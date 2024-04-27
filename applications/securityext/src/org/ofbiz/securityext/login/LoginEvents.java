/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/

package org.ofbiz.securityext.login;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.RandomStringUtils;
import org.ofbiz.base.crypto.HashCrypt;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilFormatOut;
import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.string.FlexibleStringExpander;
import org.ofbiz.common.login.LoginServices;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.entity.util.EntityUtilProperties;
import org.ofbiz.party.contact.ContactHelper;
import org.ofbiz.product.product.ProductEvents;
import org.ofbiz.product.store.ProductStoreWorker;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;
import org.ofbiz.webapp.control.LoginWorker;
import org.ofbiz.webapp.website.WebSiteWorker;

/**
 * LoginEvents - Events for UserLogin and Security handling.
 */
public class LoginEvents {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    public static final String resource = "SecurityextUiLabels";

    public static final String PARTY_ATTR_PWD_RECOVERY_HASH = "PWD_RECOVERY_HASH";

    /**
     * @deprecated SCIPIO: no longer hardcoded, see security.properties
     */
    @Deprecated
    public static final String usernameCookieName = "Scipio.Username"; // SCIPIO: renamed cookie

    /**
     * Save USERNAME and PASSWORD for use by auth pages even if we start in non-auth pages.
     *
     * @param request The HTTP request object for the current JSP or Servlet request.
     * @param response The HTTP response object for the current JSP or Servlet request.
     * @return String
     */
    public static String saveEntryParams(HttpServletRequest request, HttpServletResponse response) {
        GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
        HttpSession session = request.getSession();
        Delegator delegator = (Delegator) request.getAttribute("delegator");

        // save entry login parameters if we don't have a valid login object
        if (userLogin == null) {

            String username = request.getParameter("USERNAME");
            String password = request.getParameter("PASSWORD");

            if ((username != null) && ("true".equalsIgnoreCase(EntityUtilProperties.getPropertyValue("security", "username.lowercase", delegator)))) {
                username = username.toLowerCase(Locale.getDefault());
            }
            if ((password != null) && ("true".equalsIgnoreCase(EntityUtilProperties.getPropertyValue("security", "password.lowercase", delegator)))) {
                password = password.toLowerCase(Locale.getDefault());
            }

            // save parameters into the session - so they can be used later, if needed
            if (username != null) {
                session.setAttribute("USERNAME", username);
            }
            if (password != null) {
                session.setAttribute("PASSWORD", password);
            }

        } else {
            // if the login object is valid, remove attributes
            session.removeAttribute("USERNAME");
            session.removeAttribute("PASSWORD");
        }

        return "success";
    }

    /**
     * The user forgot his/her password.  This will call showPasswordHint, emailPassword or simply returns "success" in case
     * no operation has been specified.
     *
     * @param request The HTTPRequest object for the current request
     * @param response The HTTPResponse object for the current request
     * @return String specifying the exit status of this event
     */
    public static String forgotPassword(HttpServletRequest request, HttpServletResponse response) {
        if ((UtilValidate.isNotEmpty(request.getParameter("GET_PASSWORD_HINT"))) || (UtilValidate.isNotEmpty(request.getParameter("GET_PASSWORD_HINT.x")))) {
            return showPasswordHint(request, response);
        } else if ((UtilValidate.isNotEmpty(request.getParameter("EMAIL_PASSWORD"))) || (UtilValidate.isNotEmpty(request.getParameter("EMAIL_PASSWORD.x")))) {
            return emailPassword(request, response);
        } else {
            return "success";
        }
    }

    /** Show the password hint for the userLoginId specified in the request object.
     *@param request The HTTPRequest object for the current request
     *@param response The HTTPResponse object for the current request
     *@return String specifying the exit status of this event
     */
    public static String showPasswordHint(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");

        String userLoginId = request.getParameter("USERNAME");
        String errMsg = null;

        if ((userLoginId != null) && ("true".equals(EntityUtilProperties.getPropertyValue("security", "username.lowercase", delegator)))) {
            userLoginId = userLoginId.toLowerCase(Locale.getDefault());
        }

        if (UtilValidate.isEmpty(userLoginId)) {
            // the password was incomplete
            errMsg = UtilProperties.getMessage(resource, "loginevents.username_was_empty_reenter", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }

        GenericValue supposedUserLogin = null;

        try {
            supposedUserLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", userLoginId).queryOne();
        } catch (GenericEntityException gee) {
            Debug.logWarning(gee, "", module);
        }
        if (supposedUserLogin == null) {
            // the Username was not found
            errMsg = UtilProperties.getMessage(resource, "loginevents.username_not_found_reenter", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }

        String passwordHint = supposedUserLogin.getString("passwordHint");

        if (UtilValidate.isEmpty(passwordHint)) {
            // the Username was not found
            errMsg = UtilProperties.getMessage(resource, "loginevents.no_password_hint_specified_try_password_emailed", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }

        Map<String, String> messageMap = UtilMisc.toMap("passwordHint", passwordHint);
        errMsg = UtilProperties.getMessage(resource, "loginevents.password_hint_is", messageMap, UtilHttp.getLocale(request));
        request.setAttribute("_EVENT_MESSAGE_", errMsg);
        return "success";
    }

    /**
     *  Email the password for the userLoginId specified in the request object.
     *
     * @param request The HTTPRequest object for the current request
     * @param response The HTTPResponse object for the current request
     * @return String specifying the exit status of this event
     */
    public static String emailPassword(HttpServletRequest request, HttpServletResponse response) {
        String defaultScreenLocation = "component://securityext/widget/EmailSecurityScreens.xml#PasswordEmail";

        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        String productStoreId = ProductStoreWorker.getProductStoreId(request);

        String errMsg = null;

        boolean useEncryption = "true".equals(EntityUtilProperties.getPropertyValue("security", "password.encrypt", delegator));

        String userLoginId = request.getParameter("USERNAME");

        if ((userLoginId != null) && ("true".equals(EntityUtilProperties.getPropertyValue("security", "username.lowercase", delegator)))) {
            userLoginId = userLoginId.toLowerCase(Locale.getDefault());
        }

        if (UtilValidate.isEmpty(userLoginId)) {
            // the password was incomplete
            errMsg = UtilProperties.getMessage(resource, "loginevents.username_was_empty_reenter", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }

        GenericValue supposedUserLogin = null;
        String passwordToSend = null;
        String verifyHash = null;

        try {
            supposedUserLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", userLoginId).queryOne();
            if (supposedUserLogin == null) {
                // the Username was not found
                errMsg = UtilProperties.getMessage(resource, "loginevents.username_not_found_reenter", UtilHttp.getLocale(request));
                request.setAttribute("_ERROR_MESSAGE_", errMsg);
                return "error";
            }
        } catch (GenericEntityException e) {
            Debug.logWarning("emailPassword: Error accessing password for userLoginId: " + (supposedUserLogin != null ? supposedUserLogin.getString("userLoginId") : "n/a") + ": " + e.toString(), module);
            // SCIPIO: 2019-12-17: This is not appropriate to show to users by default, for security reasons, and already logged (just shove the friendlier one in)
            //Map<String, String> messageMap = UtilMisc.toMap("errorMessage", e.toString());
            Map<String, String> messageMap = UtilMisc.toMap("errorMessage", UtilProperties.getMessage(resource, "loginevents.error_unable_email_password_contact_customer_service", UtilHttp.getLocale(request)));
            errMsg = UtilProperties.getMessage(resource, "loginevents.error_accessing_password", messageMap, UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }

        StringBuilder emails = new StringBuilder();
        GenericValue party = null;

        try {
            party = supposedUserLogin.getRelatedOne("Party", false);
        } catch (GenericEntityException e) {
            Debug.logWarning(e, "", module);
        }
        String firstEmail = null;
        if (party != null) {
            Iterator<GenericValue> emailIter = UtilMisc.toIterator(ContactHelper.getContactMechByPurpose(party, "PRIMARY_EMAIL", false));
            while (emailIter != null && emailIter.hasNext()) {
                GenericValue email = emailIter.next();
                emails.append(emails.length() > 0 ? "," : "").append(email.getString("infoString"));
                if (UtilValidate.isEmpty(firstEmail)) {
                    firstEmail = email.getString("infoString");
                }
            }
        }

        if (UtilValidate.isEmpty(emails.toString())) {
            // the Username was not found
            errMsg = UtilProperties.getMessage(resource, "loginevents.no_primary_email_address_set_contact_customer_service", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }

        String emailRecoveryMode = EntityUtilProperties.getPropertyValue("security", "password.email.recovery.mode", "password", delegator);
        if (emailRecoveryMode.equals("password")) {
            try {
                if (useEncryption) {
                    // password encrypted, can't send, generate new password and email to user
                    passwordToSend = RandomStringUtils.randomAlphanumeric(EntityUtilProperties.getPropertyAsInteger("security", "password.length.min", 5));
                    if ("true".equals(EntityUtilProperties.getPropertyValue("security", "password.lowercase", delegator))) {
                        passwordToSend = passwordToSend.toLowerCase(Locale.getDefault());
                    }
                    supposedUserLogin.set("currentPassword", HashCrypt.cryptUTF8(LoginServices.getHashType(), null, passwordToSend));
                    supposedUserLogin.set("passwordHint", "Auto-Generated Password");
                    if ("true".equals(EntityUtilProperties.getPropertyValue("security", "password.email_password.require_password_change", delegator))) {
                        supposedUserLogin.set("requirePasswordChange", "Y");
                    }
                } else {
                    passwordToSend = supposedUserLogin.getString("currentPassword");
                }
            /* Its a Base64 string, it can contain + and this + will be converted to space after decoding the url.
               For example: passwordToSend "DGb1s2wgUQmwOBK9FK+fvQ==" will be converted to "DGb1s2wgUQmwOBK9FK fvQ=="
               So to fix it, done Url encoding of passwordToSend.
            */
                passwordToSend = URLEncoder.encode(passwordToSend, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Debug.logWarning("emailPassword: Error accessing password for userLoginId: " + (supposedUserLogin != null ? supposedUserLogin.getString("userLoginId") : "n/a") + ": " + e.toString(), module);
                // SCIPIO: 2019-12-17: This is not appropriate to show to users by default, for security reasons, and already logged (just shove the friendlier one in)
                //Map<String, String> messageMap = UtilMisc.toMap("errorMessage", e.toString());
                Map<String, String> messageMap = UtilMisc.toMap("errorMessage", UtilProperties.getMessage(resource, "loginevents.error_unable_email_password_contact_customer_service", UtilHttp.getLocale(request)));
                errMsg = UtilProperties.getMessage(resource, "loginevents.error_accessing_password", messageMap, UtilHttp.getLocale(request));
                request.setAttribute("_ERROR_MESSAGE_", errMsg);
                return "error";
            }
        } else if (emailRecoveryMode.equals("verifyHash")) {
            Map<String, Object> createEmailAddressVerificationCtx = ServiceUtil.makeContext();
            // FIXME: Service expects a String, which doesn't make sense. Besides the service doesn't care about passed expired.
//        Calendar calendar = Calendar.getInstance();
//        calendar.add(Calendar.HOUR, 24);
//        createEmailAddressVerificationCtx.put("expireDate", UtilDateTime.toTimestamp(calendar.getTime()));
            Map<String, Object> createEmailAddressVerificationResult = UtilMisc.newMap();
            createEmailAddressVerificationCtx.put("sendVerificationEmail", false);
            createEmailAddressVerificationCtx.put("emailAddress", firstEmail);
            try {
                GenericValue pwdHashPartyAttribute = delegator.findOne("PartyAttribute",
                        UtilMisc.toMap("partyId", party.getString("partyId"), "attrName", PARTY_ATTR_PWD_RECOVERY_HASH), false);
                if (UtilValidate.isNotEmpty(pwdHashPartyAttribute)) {
                    pwdHashPartyAttribute.remove();
                }
                GenericValue emailAddressVerification = delegator.findOne("EmailAddressVerification",
                        UtilMisc.toMap("emailAddress", firstEmail), false);
                if (UtilValidate.isNotEmpty(emailAddressVerification)) {
                    emailAddressVerification.remove();
                }

                createEmailAddressVerificationResult = dispatcher.runSync("createEmailAddressVerification", createEmailAddressVerificationCtx);
                if (!ServiceUtil.isSuccess(createEmailAddressVerificationResult)) {
                    throw new GenericServiceException("Problem creating verification hash");
                }
                if (createEmailAddressVerificationResult.containsKey("verifyHash")) {
                    verifyHash = (String) createEmailAddressVerificationResult.get("verifyHash");
                    Map<String, Object> partyAttribute = UtilMisc.toMap(
                            "partyId", party.getString("partyId"),
                            "attrName", PARTY_ATTR_PWD_RECOVERY_HASH,
                            "attrValue", verifyHash);
                    pwdHashPartyAttribute = delegator.makeValue("PartyAttribute", partyAttribute).create();
                    if (UtilValidate.isEmpty(pwdHashPartyAttribute)) {
                        throw new GenericServiceException("Problem creating party attribute containing the verification hash");
                    }
                } else {
                    throw new GenericServiceException("Problem creating verification hash");
                }
            } catch (GenericServiceException | GenericEntityException e) {
                Debug.logError(e, e.getMessage(), module);
                errMsg = UtilProperties.getMessage(resource, "loginevents.error_unable_generate_hash_customer_service", UtilHttp.getLocale(request));

            }
            // FIXME: Make hash type configurable
            int prefixLength = 0;
            if (verifyHash.startsWith("{MD5}")) {
                prefixLength = "{MD5}".length();
            }
        } else {
            errMsg = UtilProperties.getMessage(resource, "loginevents.error_unsupported_email_recovery_mode: " + emailRecoveryMode, UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }

        // get the ProductStore email settings
        GenericValue productStoreEmail = null;
        try {
            productStoreEmail = EntityQuery.use(delegator).from("ProductStoreEmailSetting").where("productStoreId", productStoreId, "emailType", "PRDS_PWD_RETRIEVE").queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, "Problem getting ProductStoreEmailSetting", module);
        }

        String bodyScreenLocation = null;
        if (productStoreEmail != null) {
            bodyScreenLocation = productStoreEmail.getString("bodyScreenLocation");
        }
        if (UtilValidate.isEmpty(bodyScreenLocation)) {
            bodyScreenLocation = defaultScreenLocation;
        }

        // set the needed variables in new context
        Map<String, Object> bodyParameters = new HashMap<>();
        if (emailRecoveryMode.equals("verifyHash")) {
            // FIXME: Make hash type configurable
            int prefixLength = 0;
            if (verifyHash.startsWith("{MD5}")) {
                prefixLength = "{MD5}".length();
            }
            bodyParameters.put("verifyHash", verifyHash.substring(prefixLength));
        } else if (emailRecoveryMode.equals("password")) {
            bodyParameters.put("useEncryption", useEncryption);
            bodyParameters.put("password", UtilFormatOut.checkNull(passwordToSend));
        }
        bodyParameters.put("locale", UtilHttp.getLocale(request));
        bodyParameters.put("userLogin", supposedUserLogin);
        bodyParameters.put("productStoreId", productStoreId);

        Map<String, Object> serviceContext = new HashMap<>();
        serviceContext.put("bodyScreenUri", bodyScreenLocation);
        serviceContext.put("bodyParameters", bodyParameters);
        if (productStoreEmail != null) {
            serviceContext.put("subject", productStoreEmail.getString("subject"));
            serviceContext.put("sendFrom", productStoreEmail.get("fromAddress"));
            serviceContext.put("sendCc", productStoreEmail.get("ccAddress"));
            serviceContext.put("sendBcc", productStoreEmail.get("bccAddress"));
            serviceContext.put("contentType", productStoreEmail.get("contentType"));
        } else {
            GenericValue emailTemplateSetting = null;
            try {
                emailTemplateSetting = EntityQuery.use(delegator).from("EmailTemplateSetting").where("emailTemplateSettingId", "EMAIL_PASSWORD").cache().queryOne();
            } catch (GenericEntityException e) {
                Debug.logError(e, module);
            }
            if (emailTemplateSetting != null) {
                String subject = emailTemplateSetting.getString("subject");
                subject = FlexibleStringExpander.expandString(subject, UtilMisc.toMap("userLoginId", userLoginId));
                serviceContext.put("subject", subject);
                serviceContext.put("sendFrom", emailTemplateSetting.get("fromAddress"));
            } else {
                serviceContext.put("subject", UtilProperties.getMessage(resource, "loginservices.password_reminder_subject", UtilMisc.toMap("userLoginId", userLoginId), UtilHttp.getLocale(request)));
                serviceContext.put("sendFrom", EntityUtilProperties.getPropertyValue("general", "defaultFromEmailAddress", delegator));
            }
        }
        serviceContext.put("sendTo", emails.toString());
        serviceContext.put("partyId", party.getString("partyId"));
        // SCIPIO: Set webSiteId, if available - otherwise will be inferred from productStoreId by scipio extensions
        String webSiteId = WebSiteWorker.getWebSiteId(request);
        if (UtilValidate.isNotEmpty(webSiteId)) {
            serviceContext.put("webSiteId", webSiteId);
        }

        try {
            Map<String, Object> result = dispatcher.runSync("sendMailHiddenInLogFromScreen", serviceContext);

            if (ServiceUtil.isError(result)) { // SCIPIO: 2018-10-04: Corrected error check
                // SCIPIO: 2019-12-17: This is not appropriate to show to users by default, for security reasons, and service engine should have logged it
                //Map<String, Object> messageMap = UtilMisc.toMap("errorMessage", ServiceUtil.getErrorMessage(result));
                //errMsg = UtilProperties.getMessage(resource, "loginevents.error_unable_email_password_contact_customer_service_errorwas", messageMap, UtilHttp.getLocale(request));
                errMsg = UtilProperties.getMessage(resource, "loginevents.error_unable_email_password_contact_customer_service", UtilHttp.getLocale(request));
                request.setAttribute("_ERROR_MESSAGE_", errMsg);
                return "error";
            }
        } catch (GenericServiceException e) {
            Map<String, Object> sanitizedCtx = new HashMap<>(serviceContext);
            sanitizedCtx.put("password", "[HIDDEN]");
            Debug.logWarning("emailPassword: Error sending email: " + e.toString() + "; context: " + sanitizedCtx, module);
            errMsg = UtilProperties.getMessage(resource, "loginevents.error_unable_email_password_contact_customer_service", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }

        if (emailRecoveryMode.equals("password")) {
            // don't save password until after it has been sent
            if (useEncryption) {
                try {
                    supposedUserLogin.store();
                } catch (GenericEntityException e) {
                    Debug.logWarning("emailPassword: Could not store userLoginId: " + (supposedUserLogin != null ? supposedUserLogin.getString("userLoginId") : "n/a") + ": " + e.toString(), module);
                    // SCIPIO: 2019-12-17: This is not appropriate to show to users by default, for security reasons, and already logged (NOTE: label was changed to omit message)
                    //Map<String, String> messageMap = UtilMisc.toMap("errorMessage", e.toString());
                    //errMsg = UtilProperties.getMessage(resource, "loginevents.error_saving_new_password_email_not_correct_password", messageMap, UtilHttp.getLocale(request));
                    errMsg = UtilProperties.getMessage(resource, "loginevents.error_saving_new_password_email_not_correct_password", UtilHttp.getLocale(request));
                    request.setAttribute("_ERROR_MESSAGE_", errMsg);
                    return "error";
                }
            }

            if (useEncryption) {
                errMsg = UtilProperties.getMessage(resource, "loginevents.new_password_createdandsent_check_email", UtilHttp.getLocale(request));
                request.setAttribute("_EVENT_MESSAGE_", errMsg);
            } else {
                errMsg = UtilProperties.getMessage(resource, "loginevents.new_password_sent_check_email", UtilHttp.getLocale(request));
                request.setAttribute("_EVENT_MESSAGE_", errMsg);
            }
        } else {
            errMsg = UtilProperties.getMessage(resource, "loginevents.password_reset_sent_check_email", UtilHttp.getLocale(request));
            request.setAttribute("_EVENT_MESSAGE_", errMsg);
        }
        return "success";
    }

    public static String storeCheckLogin(HttpServletRequest request, HttpServletResponse response) {
        String responseString = LoginWorker.checkLogin(request, response);
        if ("error".equals(responseString)) {
            return responseString;
        }
        // if we are logged in okay, do the check store customer role
        return ProductEvents.checkStoreCustomerRole(request, response);
    }

    public static String storeLogin(HttpServletRequest request, HttpServletResponse response) {
        String responseString = LoginWorker.login(request, response);
        if (!"success".equals(responseString)) {
            return responseString;
        }
        // TODO: REVIEW: Not in stock; appropriate?
        //String errMsg = UtilProperties.getMessage(resource, "loginevents.login_successful", UtilHttp.getLocale(request));
        //request.setAttribute("_EVENT_MESSAGE_", errMsg);
        if ("Y".equals(request.getParameter("rememberMe"))) {
            setUsername(request, response);
        }
        // if we logged in okay, do the check store customer role
        return ProductEvents.checkStoreCustomerRole(request, response);
    }

    public static String getUsername(HttpServletRequest request) {
        String cookieUsername = null;
        Cookie[] cookies = request.getCookies();
        if (Debug.verboseOn()) {
            Debug.logVerbose("Cookies: " + Arrays.toString(cookies), module);
        }
        String usernameCookieName = LoginWorker.getUserNameCookieName(request); // SCIPIO
        if (cookies != null) {
            for (Cookie cookie: cookies) {
                if (cookie.getName().equals(usernameCookieName)) {
                    cookieUsername = cookie.getValue();
                    break;
                }
            }
        }
        // SCIPIO: 2018-11-05: Decode the username (encoded in setUsername)
        try {
            cookieUsername = URLDecoder.decode(cookieUsername, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Debug.logWarning("getUsername: Cannot decode username [" + cookieUsername + "] from \"" 
                    + usernameCookieName + "\" cookie; returning as-is; cause: " + e.toString(), module);
        }
        return cookieUsername;
    }

    public static void setUsername(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession();
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        String domain = EntityUtilProperties.getPropertyValue("url", "cookie.domain", delegator);
        // first try to get the username from the cookie
        // SCIPIO: 2018-12-03: This is not supported by servlet API and will not work with session facades
        //synchronized (session) {
        synchronized (UtilHttp.getSessionSyncObject(session)) {
            // SCIPIO: 2018-11-05: This condition makes little sense, it means this could never save a new user until old cookie expires
            // or session is cleared/logout, but user can trigger this call outside those cases with a form
            //if (UtilValidate.isEmpty(getUsername(request))) {
            if (UtilValidate.isNotEmpty(request.getParameter("USERNAME"))) {
                // create the cookie and send it back
                String usernameParam;
                try {
                    usernameParam = URLEncoder.encode(request.getParameter("USERNAME"), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    // SCIPIO: 2018-11-05: Added this try/catch, but it basically impossible for it to cause error here
                    Debug.logError("setUsername: Error encoding username from parameter [" + request.getParameter("USERNAME") 
                        + "] for cookie; cannot set username cookie: " + e.toString(), module);
                    return;
                }
                // SCIPIO
                //Cookie cookie = new Cookie(usernameCookieName, usernameParam);
                //cookie.setMaxAge(60 * 60 * 24 * 365);
                Cookie cookie = new Cookie(LoginWorker.getUserNameCookieName(request), usernameParam);
                cookie.setMaxAge(LoginWorker.getUserNameCookieMaxAge(request));
                cookie.setPath("/");
                cookie.setDomain(domain);
                cookie.setSecure(true);
                cookie.setHttpOnly(true);
                response.addCookie(cookie);
            }
        }
    }

    /**
     * Shows the change password form if the h (hash) param is passed and it is valid. If not, shows login page. No current password need to be reset password.
     * no operation has been specified.
     *
     * @param request  The HTTPRequest object for the current request
     * @param response The HTTPResponse object for the current request
     * @return String specifying the exit status of this event
     */
    public static String changePassword(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        Locale locale = UtilHttp.getLocale(request);

        String hash = request.getParameter("h");
        if (UtilValidate.isEmpty(hash)) {
            request.setAttribute("_ERROR_MESSAGE_", UtilProperties.getMessage(resource, "loginevents.change_password_missing_hash", locale));
            return "error";
        }

        // FIXME: Make hash type configurable
        final String finalHash = "{MD5}" + hash;
        try {
            GenericValue pwdHashPartyAttribute = EntityUtil.getFirst(delegator.findByAnd("PartyAttribute",
                    UtilMisc.toMap("attrValue", finalHash, "attrName", PARTY_ATTR_PWD_RECOVERY_HASH), null, false));
            if (UtilValidate.isEmpty(pwdHashPartyAttribute)) {
                throw new GenericEntityException();
            }
            GenericValue party = pwdHashPartyAttribute.getRelatedOne("Party");

            Timestamp now = UtilDateTime.nowTimestamp();
            EntityCondition emailVerificationCond = EntityCondition.makeCondition(UtilMisc.toList(
                    EntityCondition.makeCondition("expireDate", EntityOperator.GREATER_THAN_EQUAL_TO, now),
                    EntityCondition.makeCondition("verifyHash", EntityOperator.EQUALS, finalHash)
            ), EntityOperator.AND);
            GenericValue emailAddressVerification = EntityQuery.use(delegator).from("EmailAddressVerification").where(emailVerificationCond).queryFirst();
            if (UtilValidate.isEmpty(emailAddressVerification)) {
                throw new GenericEntityException();
            }
            request.setAttribute("pwdRecoveryPartyId", party.getString("partyId"));
            request.setAttribute("hash", hash);
        } catch (GenericEntityException e) {
            request.setAttribute("_ERROR_MESSAGE_", UtilProperties.getMessage(resource, "loginevents.change_password_invalid_expired_hash", locale));
            return "error";
        }
        return "success";
    }


    /**
     * Updates password using a valid combination of party and hash
     *
     * @param request  The HTTPRequest object for the current request
     * @param response The HTTPResponse object for the current request
     * @return String specifying the exit status of this event
     */
    public static String updatePassword(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        Locale locale = UtilHttp.getLocale(request);
        Map<String, Object> parameterMap = UtilHttp.getCombinedMap(request);

        String partyId = (String) parameterMap.get("pwdRecoveryPartyId");
        String hash = (String) parameterMap.get("hash");
        if (UtilValidate.isEmpty(partyId) || UtilValidate.isEmpty(hash)) {
            request.setAttribute("_ERROR_MESSAGE_", UtilProperties.getMessage(resource, "loginevents.change_password_missing_hash", locale));
            return "error";
        }

        try {
            final String finalHash = "{MD5}" + hash;
            Timestamp now = UtilDateTime.nowTimestamp();
            EntityCondition emailVerificationCond = EntityCondition.makeCondition(UtilMisc.toList(
                    EntityCondition.makeCondition("expireDate", EntityOperator.GREATER_THAN_EQUAL_TO, now),
                    EntityCondition.makeCondition("verifyHash", EntityOperator.EQUALS, finalHash)
            ), EntityOperator.AND);
            List<GenericValue> emailAddressVerifications = EntityQuery.use(delegator).from("EmailAddressVerification").where(emailVerificationCond).queryList();
            if (UtilValidate.isEmpty(emailAddressVerifications)) {
                throw new GenericEntityException();
            }

            GenericValue pwdHashPartyAttribute = EntityQuery.use(delegator).from("PartyAttribute").where(
                    UtilMisc.toMap("partyId", partyId, "attrName", PARTY_ATTR_PWD_RECOVERY_HASH, "attrValue", finalHash)).queryFirst();
            if (UtilValidate.isEmpty(pwdHashPartyAttribute)) {
                throw new GenericEntityException();
            }
            GenericValue party = pwdHashPartyAttribute.getRelatedOne("Party");
            GenericValue userLogin = EntityUtil.getFirst(party.getRelated("UserLogin", UtilMisc.toMap("enabled", "Y"), null, false));
            GenericValue system = delegator.findOne("UserLogin", UtilMisc.toMap("userLoginId", "system"), false);

            Map<String, Object> updatePasswordCtx = ServiceUtil.makeContext();
            updatePasswordCtx.put("userLogin", system);
            updatePasswordCtx.put("userLoginId", userLogin.get("userLoginId"));
            updatePasswordCtx.put("currentPassword", userLogin.getString("currentPassword"));
            updatePasswordCtx.put("newPassword", request.getParameter("newPassword"));
            updatePasswordCtx.put("newPasswordVerify", request.getParameter("newPasswordVerify"));
            Map<String, Object> updatePasswordResult = null;
            try {
                updatePasswordResult = dispatcher.runSync("updatePassword", updatePasswordCtx);
                if (!ServiceUtil.isSuccess(updatePasswordResult)) {
                    throw new GenericServiceException((String) updatePasswordResult.get("errorMessage"));
                }
            } catch (GenericServiceException e) {
                request.setAttribute("_ERROR_MESSAGE_", e.getMessage());
                request.setAttribute("pwdRecoveryPartyId", partyId);
                request.setAttribute("hash", hash);
                return "error";
            }

            pwdHashPartyAttribute.remove();
            for (GenericValue emailAddressVerification : emailAddressVerifications) {
                emailAddressVerification.remove();
            }

            request.setAttribute("_EVENT_MESSAGE_", updatePasswordResult.get("successMessage"));
        } catch (GenericEntityException e) {
            request.setAttribute("_ERROR_MESSAGE_", UtilProperties.getMessage(resource, "loginevents.change_password_invalid_expired_hash", locale));
            request.setAttribute("pwdRecoveryPartyId", partyId);
            request.setAttribute("hash", hash);
            return "error";
        }
        return "success";
    }

}
