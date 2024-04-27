package com.ilscipio.scipio.shop.shoppingcart;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.UtilFormatOut;
import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilNumber;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.order.shoppingcart.CartUpdate;
import org.ofbiz.order.shoppingcart.ShoppingCart;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * SCIPIO: Shop-specific checkout events.
 */
public class CheckOutEvents extends org.ofbiz.order.shoppingcart.CheckOutEvents {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());


    /**
     * NOT IMPLEMENTED - CURRENTLY HANDLED BY CustomerEvents.xml#checkCreateUpdateAnonUser.
     * <p>
     * Creates/updates an anon user for checkoutoptions if a form was submitted containing a request
     * to create one (createNewUser "Y").
     * <p>
     * Currently it will always create a new anon user if the screen requested it.
     * This at least may prevent (or cause!) some issues with session.
     * <p>
     * It will use a minimal set of info (first and last name) to create the Person;
     * the rest should be handled by other events.
     * <p>
     * It stores the fake userLogin (userLoginId "anonymous", partyId to a new party) in session.
     * <p>
     * Based on CustomerEvents.xml#processCustomerSettings.
     */
    public static String checkCreateUpdateAnonUser(HttpServletRequest request, HttpServletResponse response) {
        /*
        String newUser = request.getParameter("createUpdateAnonUser");
        if ("Y".equals(newUser)) {
            String firstName = request.getParameter("firstName");
            String lastName = request.getParameter("lastName");
        }
        return "success";
        */
        throw new UnsupportedOperationException("Not implemented");
    }

    public static String checkGiftCardBalance(HttpServletRequest request, HttpServletResponse response) {
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        Map<String, Object> params = UtilHttp.getParameterMap(request);

        GenericValue userLogin = UtilHttp.getUserLogin(request);
        Locale locale = UtilHttp.getLocale(request);
        TimeZone timeZone = UtilHttp.getTimeZone(request);

        BigDecimal specificCardAmount = BigDecimal.ZERO;
        if (params.containsKey("giftCardAmount") && UtilValidate.isNotEmpty(params.get("giftCardAmount"))) {
            try {
                specificCardAmount = UtilNumber.toBigDecimal(params.get("cardAmount"));
                if (specificCardAmount.signum() != 1) {
                    specificCardAmount = BigDecimal.ZERO;
                }
            } catch (NumberFormatException e) {
                request.setAttribute("errorMessage", UtilProperties.getMessage("AccountingErrorUiLabels", "AccountingInvalidGiftCardAmount", locale));
            }
        }

        String currency = (String) params.getOrDefault("currency", UtilProperties.getPropertyValue("general", "currency.uom.id.default"));
        String cardNumber = (String) params.get("giftCardNumber");
        String pinNumber = (String) params.get("giftCardPin");
        try {
            Map<String, Object> checkGiftCertificateBalanceCtx = UtilMisc.toMap("cardNumber", cardNumber, "pinNumber", pinNumber,
                    "currency", currency, "userLogin", userLogin);
            Map<String, Object> checkGiftCertificateBalanceResult = dispatcher.runSync("checkGiftCertificateBalance",
                    checkGiftCertificateBalanceCtx);
            if (ServiceUtil.isSuccess(checkGiftCertificateBalanceResult)) {
                BigDecimal currentBalance = (BigDecimal) checkGiftCertificateBalanceResult.get("balance");
                if (UtilValidate.isEmpty(currentBalance)) {
                    currentBalance = BigDecimal.ZERO;
                }

                try (CartUpdate cartUpdate = CartUpdate.updateSection(request)) {
                    ShoppingCart cart = cartUpdate.getCartForUpdate();
                    BigDecimal cartGrandTotal = cart.getGrandTotal();
                    // If specific card amount is greater or equal as balance, use the whole balance
                    BigDecimal pendingCartAmount = cartGrandTotal.subtract(currentBalance);
                    BigDecimal pendingBalance = BigDecimal.ZERO;
                    if (pendingCartAmount.compareTo(BigDecimal.ZERO) < 0) {
                        pendingCartAmount = BigDecimal.ZERO;
                        pendingBalance = currentBalance.subtract(cartGrandTotal);
                    }

                    if (specificCardAmount.compareTo(BigDecimal.ZERO) > 0 && specificCardAmount.compareTo(currentBalance) < 0) {
                        pendingBalance = currentBalance.subtract(specificCardAmount);
                        pendingCartAmount = cartGrandTotal.subtract(specificCardAmount);
                    }
                    boolean balanceCoversTotal = (pendingCartAmount.compareTo(BigDecimal.ZERO) <= 0);

                    BigDecimal balanceUsed = currentBalance.subtract(pendingBalance);

                    String currentBalanceFormatted = UtilFormatOut.formatCurrency(currentBalance, currency, locale);
                    String pendingBalanceFormatted = UtilFormatOut.formatCurrency(pendingBalance, currency, locale);
                    String pendingCartAmountFormatted = UtilFormatOut.formatCurrency(pendingCartAmount, currency, locale);

                    request.setAttribute("currentBalanceMessage", UtilProperties.getMessage("AccountingUiLabels", "AccountingCurrentBalance",
                            UtilMisc.toMap("balance", currentBalanceFormatted), locale));
                    request.setAttribute("pendingBalanceMessage", UtilProperties.getMessage("AccountingUiLabels", "AccountingPendingBalance",
                            UtilMisc.toMap("pendingBalance", pendingBalanceFormatted), locale));
                    request.setAttribute("cartAmountPendingMessage", UtilProperties.getMessage("AccountingUiLabels", "AccountingCartAmountPending",
                            UtilMisc.toMap("pendingCartAmount", pendingCartAmountFormatted), locale));
                    request.setAttribute("currentBalance", currentBalance);
                    request.setAttribute("balanceCoversTotal", balanceCoversTotal);
                    request.setAttribute("balancePendingAmount", pendingBalance);
                    request.setAttribute("balancePendingAmountFormatted", pendingBalanceFormatted);
                    request.setAttribute("pendingCartAmount", pendingCartAmount);
                    request.setAttribute("pendingCartAmountFormatted", pendingCartAmountFormatted);
                    request.setAttribute("balanceUsed", balanceUsed);
                }
            } else {
                request.setAttribute("errorMessage", ServiceUtil.getErrorMessage(checkGiftCertificateBalanceResult));
            }
        } catch (GeneralException e) {
            return "error";
        }
        return "success";
    }

}
