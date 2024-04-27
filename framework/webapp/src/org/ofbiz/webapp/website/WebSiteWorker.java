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
package org.ofbiz.webapp.website;

import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpSession;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.webapp.renderer.RenderEnvType;

/**
 * WebSiteWorker - Worker class for web site related functionality
 */
public class WebSiteWorker {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    /**
     * Gets the webSiteId for the current webapp, from the web.xml configuration.
     *
     * <p>SCIPIO: 2018-07-31: This is now safe to use from early filters since session is now skipped (servlet 3.0 API).</p>
     */
    public static String getWebSiteId(ServletRequest request) {
        return request.getServletContext().getInitParameter("webSiteId");
    }

    /**
     * Gets the webSiteId for the current webapp, from the web.xml configuration.
     *
     * <p>NOTE: When request available, always use {@link #getWebSiteId(ServletRequest)} instead.</p>
     *
     * <p>SCIPIO: 3.0.0: Added for support for websocket backends where only session available.</p>
     */
    public static String getWebSiteId(HttpSession session) {
        return session.getServletContext().getInitParameter("webSiteId");
    }

    /**
     * Gets the webSiteId for the current webapp, from the web.xml configuration.
     *
     * <p>NOTE: When request available, always use {@link #getWebSiteId(ServletRequest)} instead.</p>
     *
     * <p>SCIPIO: 3.0.0: Added.</p>
     */
    public static String getWebSiteId(ServletContext servletContext) {
        return servletContext.getInitParameter("webSiteId");
    }

    /**
     * Gets the WebSite for the current webapp, from the web.xml configuration.
     *
     * <p>SCIPIO: 3.0.0: Added.</p>
     */
    public static GenericValue getWebSite(ServletRequest request) {
        String webSiteId = getWebSiteId(request);
        if (webSiteId == null) {
            return null;
        }
        return findWebSite(Delegator.from(request), webSiteId, true);
    }

    /**
     * Gets the WebSite for the current webapp, from the web.xml configuration.
     *
     * <p>NOTE: When request available, always use {@link #getWebSite(ServletRequest)} instead.</p>
     *
     * <p>SCIPIO: 3.0.0: Added for support for websocket backends where only session available.</p>
     */
    public static GenericValue getWebSite(HttpSession session) {
        String webSiteId = getWebSiteId(session);
        if (webSiteId == null) {
            return null;
        }
        return findWebSite(Delegator.from(session), webSiteId, true);
    }

    /**
     * Gets the WebSite for the current webapp, from the web.xml configuration.
     *
     * <p>NOTE: When request available, always use {@link #getWebSite(ServletRequest)} instead.</p>
     *
     * <p>SCIPIO: 3.0.0: Added.</p>
     */
    public static GenericValue getWebSite(ServletContext servletContext) {
        String webSiteId = getWebSiteId(servletContext);
        if (webSiteId == null) {
            return null;
        }
        return findWebSite(Delegator.from(servletContext), webSiteId, true);
    }

    /**
     * returns a WebSite-GenericValue (using entityCache)
     *
     * @param delegator
     * @param webSiteId
     * @return
     */
    public static GenericValue findWebSite(Delegator delegator, String webSiteId) {
        return findWebSite(delegator, webSiteId, true);
    }

    /**
     * returns a WebSite-GenericValue
     *
     * @param delegator
     * @param webSiteId
     * @param useCache
     * @return GenericValue
     */
    public static GenericValue findWebSite(Delegator delegator, String webSiteId, boolean useCache) {
        GenericValue result = null;
        try {
            result = EntityQuery.use(delegator).from("WebSite").where("webSiteId", webSiteId).cache(useCache).queryOne();
        }
        catch (GenericEntityException e) {
            Debug.logError("Error looking up website with id " + webSiteId, module);
        }
        return result;
    }

    /**
     * SCIPIO: Extracts the webSiteId from the given context according to its render environment type.
     * <p>
     * NOTE: Partly based on {@link org.ofbiz.common.email.NotificationServices#setBaseUrl}.
     * <p>
     * Added 2018-08-02.
     */
    public static String getWebSiteIdFromContext(Map<String, ?> context, RenderEnvType renderEnvType) {
        if (renderEnvType.isStatic()) { // NOTE: for now we assume email and non-email static should all be similar...
            String webSiteId = (String) context.get("webSiteId");
            if (UtilValidate.isNotEmpty(webSiteId)) {
                return webSiteId;
            }
            webSiteId = (String) context.get("baseWebSiteId");
            if (UtilValidate.isNotEmpty(webSiteId)) {
                return webSiteId;
            }
        } else if (renderEnvType.isWebapp()) {
            ServletRequest request = (ServletRequest) context.get("request");
            if (request != null) return getWebSiteId(request);
        }
        return null;
    }

    /**
     * SCIPIO: Extracts the webSiteId from the given context according to its render environment type
     * automatically determined from the context itself (best-effort).
     * <p>
     * NOTE: Partly based on {@link org.ofbiz.common.email.NotificationServices#setBaseUrl}.
     * <p>
     * Added 2018-08-17.
     */
    public static String getWebSiteIdFromContext(Map<String, ?> context) {
        return getWebSiteIdFromContext(context, RenderEnvType.fromContext(context));
    }

    /**
     * SCIPIO: Gets WebSite from context.
     */
    public static GenericValue getWebSiteFromContext(Map<String, ?> context, RenderEnvType renderEnvType) {
        String webSiteId = getWebSiteIdFromContext(context);
        if (webSiteId == null) {
            return null;
        }

        return findWebSite(Delegator.from(context), webSiteId);
    }

    /**
     * SCIPIO: Gets WebSite from context.
     */
    public static GenericValue getWebSiteFromContext(Map<String, ?> context) {
        return getWebSiteFromContext(context, RenderEnvType.fromContext(context));
    }
}
