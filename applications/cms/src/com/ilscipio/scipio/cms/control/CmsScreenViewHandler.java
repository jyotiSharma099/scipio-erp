package com.ilscipio.scipio.cms.control;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.PropertyMessage;
import org.ofbiz.base.util.UtilRender;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.transaction.GenericTransactionException;
import org.ofbiz.entity.transaction.TransactionUtil;
import org.ofbiz.webapp.control.RequestUtil;
import org.ofbiz.webapp.view.ViewHandler;
import org.ofbiz.webapp.view.ViewHandlerException;
import org.ofbiz.webapp.website.WebSiteWorker;
import org.ofbiz.widget.renderer.macro.MacroScreenViewHandler;
import org.xml.sax.SAXException;

import com.ilscipio.scipio.cms.CmsUtil;
import com.ilscipio.scipio.cms.content.CmsPage;
import com.ilscipio.scipio.cms.content.CmsPageInfo;
import com.ilscipio.scipio.cms.control.cmscall.CmsCallType;
import com.ilscipio.scipio.cms.control.cmscall.render.RenderInvoker;
import com.ilscipio.scipio.cms.data.CmsDataException;
import com.ilscipio.scipio.cms.template.CmsRenderUtil;

import freemarker.template.TemplateException;

/**
 * Cms screen view handler - invokes CMS rendering.
 * <p>
 * NOTE: 2016: client webapps should include this in controller.xml using
 * through <code>component://cms/webcommon/WEB-INF/cms-client-controller.xml</code>.
 * <p>
 * All CMS render invocations are done through this class, both process and view mappings.
 * It finds the page to render by matching the current view to a (process) view mapping, which
 * contains the address of a CMS page to invoke.
 * <p>
 * FIXME: 2016: This should be changed to not extend MacroScreenViewHandler and instead only
 * implement the basic interface and delegate to an instance of MacroScreenViewHandler instead, which
 * can then be configurable.
 */
public class CmsScreenViewHandler extends MacroScreenViewHandler implements ViewHandler {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    public static final Set<Integer> passOnHttpStatusesFromCms = Collections.unmodifiableSet(new HashSet<Integer>(Arrays.asList(
            new Integer[] { HttpServletResponse.SC_NOT_FOUND })));

    protected RenderInvoker renderInvoker = null;

    protected CmsWebSiteConfig webSiteConfig = CmsWebSiteConfig.getDefault();

    protected CmsPageInfo defaultCmsPage = new CmsPageInfo(webSiteConfig.getDefaultCmsPageId());

    @Override
    public void init(ServletContext context) throws ViewHandlerException {
        super.init(context);
        this.servletContext = context;

        this.defaultCmsPage = new CmsPageInfo(this.webSiteConfig.getDefaultCmsPageId());

        this.renderInvoker = RenderInvoker.getRenderInvoker(context);

        // hasControllerHint true because if there's a view handler there has to be a controller...
        CmsWebSiteInfo webSiteInfo = CmsWebSiteInfo.registerCmsWebSite(context, true);
        this.webSiteConfig = CmsWebSiteInfo.getWebSiteConfigOrDefaults(webSiteInfo, servletContext);
    }

    @Override
    public void render(ViewRenderContext vrctx) throws ViewHandlerException {
        String name = vrctx.name();
        String page = vrctx.page();
        String info = vrctx.info();
        String contentType = vrctx.contentType();
        String encoding = vrctx.encoding();
        HttpServletRequest request = vrctx.request();
        HttpServletResponse response = vrctx.response();
        Writer writer = vrctx.renderWriter();
        Delegator delegator = (Delegator) request.getAttribute("delegator");

        String path = request.getPathInfo(); // DEV NOTE: do not rely on this

        String webSiteId = WebSiteWorker.getWebSiteId(request);
        CmsPage cmsPage = null;
        CmsCallType renderMode;
        CmsView cmsView = CmsView.findByName(delegator, name, webSiteId, true);
        CmsControlState controlState = CmsControlState.fromRequest(request);

        // 2017-04-03: due to redirection issues, we have to use saved path first, so use the same
        // info as the original request. re-lookup is only for pure view handler and for fallback.
        String requestServletPath = controlState.getRequestServletPath();
        String requestPath = controlState.getRequestPath();
        if (requestServletPath == null || requestPath == null) { // should be set together, and null makes NPE elsewhere
            requestServletPath = CmsControlUtil.normalizeServletPathNoNull(request.getServletPath());
            requestPath = CmsControlUtil.normalizeServletRootRequestPathNoNull(request.getPathInfo());
        }

        // This will only run if filter didn't already do it
        if (webSiteConfig.isSetResponseBrowserNoCache()) {
            if (CmsUtil.verboseOn()) {
                Debug.logInfo("Cms: Setting browser no-proxy no-cache response" + CmsControlUtil.getReqLogIdDelimStr(request), module);
            }
            CmsControlUtil.checkSetNoCacheResponse(request, response);
        }

        // 2016: check preview flag
        renderMode = controlState.getPageRenderMode();

        // 2016: check preview mode parameter
        // NOTE: this MAY have been done in process filter, but it possible to run without it, so do it again here
        if (renderMode == null) {
            renderMode = CmsControlUtil.getRenderModeParam(request, webSiteConfig);
        }

        // 2016: MUST NOT CACHE PREVIEWS!
        boolean useDataObjectCache = (renderMode != CmsCallType.OFBIZ_PREVIEW);

        /* 2018-12-18: We have no need of this anymore; old CmsControlServlet is gone
         * DEV NOTE: If you need this again in future, this logic must be added to CmsControlState and follow pattern of:
         *   getProcessMapping(Delegator, boolean)
        // 2016: NEW MODE: use cmsPage if already set (by old CmsControlServlet or other)
        // NOTE: reliance on this is likely TEMPORARY as the CmsControlServlet itself will probably disappear or change significantly,
        // but this code can remain here anyway.
        cmsPage = controlState.getPage();
        if (cmsPage == null) {
            // in case we run into serialization issues, allow re-lookup by ID
            String cmsPageId = controlState.getPageId();
            if (cmsPageId != null) {
                cmsPage = CmsPage.getWorker().findById(delegator, cmsPageId, useDataObjectCache);
                if (cmsPage == null) {
                    Debug.logWarning("Cms: Could not find page by ID: " + cmsPageId + "; ignoring", module);
                } else {
                    controlState.setPage(cmsPage);
                }
            }
        }
        */

        // Check for process mapping
        if (cmsPage == null) {
            CmsProcessMapping procMapping = controlState.getProcessMapping(delegator, useDataObjectCache);
            if (procMapping != null) {
                // CMS: 2016: not needed without wildcard renders (DEV NOTE: If ever needed, use CmsControlState)
                //String procExtraPathInfo = controlState.getProcessExtraPathInfo();
                // CMS: 2016: wildcard renders not applicable to local renders
                //boolean pageFromPathWildcard = false;

                try {
                    if (CmsUtil.verboseOn()) {
                        Debug.logInfo("Cms: Looking for process mapping view matching process mapping (" +
                                procMapping.getLogIdRepr() + "), view (" + name + "), request servlet path (" +
                                requestServletPath + ") and request path (" + requestPath + ")" + CmsControlUtil.getReqLogIdDelimStr(request), module);
                    }

                    CmsProcessViewMapping procViewMapping = procMapping.getProcessViewMapping(requestServletPath,
                            requestPath, name, webSiteConfig.getDefaultTargetServletPath(), CmsProcessMapping.defaultMatchAnyTargetPath);
                    if (procViewMapping != null) {
                        if (procViewMapping.isActiveLogical()) {
                            if (CmsUtil.verboseOn()) {
                                Debug.logInfo("Cms: Found active process view mapping: " + procViewMapping.getLogIdRepr() + CmsControlUtil.getReqLogIdDelimStr(request), module);
                            }

                            cmsPage = procViewMapping.getPage();
                            if (CmsUtil.verboseOn()) {
                                if (cmsPage != null) {
                                    Debug.logInfo("Cms: Found page " + cmsPage.getLogIdRepr() + " for process view mapping" + CmsControlUtil.getReqLogIdDelimStr(request), module);
                                } else {
                                    Debug.logInfo("Cms: No page associated to process view mapping (or parent process mapping)" + CmsControlUtil.getReqLogIdDelimStr(request), module);
                                }
                            }

                            // (technically should check this even if page was null; same result)
                            // CMS: 2016: not applicable to local renders
    //                        Boolean mappingPageFromPathWildcard = procViewMapping.getPageFromPathWildcard();
    //                        if (mappingPageFromPathWildcard == null) {
    //                            mappingPageFromPathWildcard = procMapping.getPageFromPathWildcard();
    //                            if (mappingPageFromPathWildcard == null) {
    //                                mappingPageFromPathWildcard = CmsProcessMapping.defaultPageFromPathWildcard;
    //                            }
    //                        }
    //                        pageFromPathWildcard = mappingPageFromPathWildcard;
                        } else {
                            if (CmsUtil.verboseOn()) {
                                Debug.logInfo("Cms: Found process view mapping, but is inactive: " + procViewMapping.getLogIdRepr() + CmsControlUtil.getReqLogIdDelimStr(request), module);
                            }
                        }
                    } else {
                        if (CmsUtil.verboseOn()) {
                            Debug.logInfo("Cms: No process view mapping found for request/view" + CmsControlUtil.getReqLogIdDelimStr(request), module);
                        }
                    }

                } catch (Exception e) {
                    // an exception is thrown, return a 500 error
                    Debug.logError(e, "Cms: Error retrieving process mapping or page from database. URI: " + path + CmsControlUtil.getReqLogIdDelimStr(request), module);
                    handleException(request, response, e, renderMode);
                    return; // Nothing can be sent after this
                }
            }
        }

        // Check for simple view mapping
        if (cmsPage == null && UtilValidate.isNotEmpty(name)) {
            try {
                if (CmsUtil.verboseOn()) {
                    Debug.logInfo("Cms: Looking for simple view mapping matching view (" + name + "), request servlet path (" +
                            requestServletPath + ") and web site (" + webSiteId + ")" + CmsControlUtil.getReqLogIdDelimStr(request), module);
                }

                CmsViewMapping viewMapping = CmsViewMapping.findByView(delegator, webSiteId, requestServletPath,
                        name, webSiteConfig.getDefaultTargetServletPath(), useDataObjectCache, request);
                if (viewMapping != null) {
                    if (viewMapping.isActiveLogical()) {
                        cmsPage = viewMapping.getPage();
                        if (CmsUtil.verboseOn()) {
                            Debug.logInfo("Cms: Active view mapping found: " + viewMapping.getLogIdRepr() + "; "
                                    + (cmsPage != null ? "maps to page " + cmsPage.getLogIdRepr() : "has no valid page assigned; ignoring"
                                    + CmsControlUtil.getReqLogIdDelimStr(request)), module);
                        }
                    } else {
                        if (CmsUtil.verboseOn()) {
                            Debug.logInfo("Cms: View mapping found, but is inactive: " +
                                    viewMapping.getLogIdRepr() + "; ignoring" + CmsControlUtil.getReqLogIdDelimStr(request), module);
                        }
                    }
                } else {
                    if (CmsUtil.verboseOn()) {
                        Debug.logInfo("Cms: No view mapping found for: " + CmsViewMapping.makeLogIdRepr(name, webSiteId)
                            + CmsControlUtil.getReqLogIdDelimStr(request), module);
                    }
                }
            } catch (Exception e) {
                // an exception is thrown, return a 500 error
                Debug.logError(e, "Cms: Error retrieving view mapping or page from database. URI: "
                        + path + CmsControlUtil.getReqLogIdDelimStr(request), module);
                handleException(request, response, e, renderMode);
                return; // Nothing can be sent after this
            }
        }

        // check cmsAccessToken (NOTE: we must do this in both CmsProcessFilter and CmsScreenViewHandler)
        boolean validAccessToken = CmsControlUtil.verifyValidAccessToken(request, delegator, webSiteConfig, renderMode);
        if (!validAccessToken) {
            Debug.logWarning("Cms: Invalid access token; denying request" + CmsControlUtil.getReqLogIdDelimStr(request), module); // SCIPIO: Changed to warning
            try {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
            } catch (IOException e) {
                Debug.logWarning(e, "Cms: Error sending server error response" + CmsControlUtil.getReqLogIdDelimStr(request), module); // SCIPIO: Changed to warning
            }
            return;
        }

        boolean renderDefault;

        if (cmsPage != null) {
            //CmsPageInfo cmsPageInfo = new CmsPageInfo(cmsPage);

            // 2016: not applicable for local cms
//            if (pageFromPathWildcard) {
//                // Special case; here cmsPageInfo points to a page representing a base directory of pages
//
//                String resolvedPagePath;
//                CmsPageInfo resolvedPageInfo;
//                if (UtilValidate.isNotEmpty(procExtraPathInfo)) {
//                    resolvedPagePath = PathUtil.concatPaths(cmsPageInfo.getCmsPageReqPath(), procExtraPathInfo);
//                    resolvedPageInfo = new CmsPageInfo("_NA_", resolvedPagePath);
//                }
//                else {
//                    // If there's no extra path info, just invoke the base page (this can be blocked at process forwarding level)
//                    resolvedPagePath = cmsPageInfo.getCmsPageReqPath();
//                    resolvedPageInfo = cmsPageInfo;
//                }
//
//                Debug.logInfo("Cms: CMS wildcard page mapping found; processing view through CMS: request: " + path + "; view name: " + name +
//                        "; CMS page mapping: " + resolvedPageInfo.getLogIdRepr() + ControlUtil.getReqLogIdDelimStr(request), module);
//                renderDefault = false;
//                boolean continueOk = renderCmsPage(request, response, path, resolvedPageInfo, cmsView, webSiteId);
//                if (!continueOk) {
//                    return;
//                }
//            }
//            else {
            Debug.logInfo("Cms: " + (renderMode == CmsCallType.OFBIZ_PREVIEW ? "PREVIEW: " : "") + "CMS page mapping found; processing view through CMS: request: " + path + "; view name: " + name +
                    "; CMS page mapping: " + cmsPage.getLogIdRepr() + CmsControlUtil.getReqLogIdDelimStr(request), module);
            renderDefault = false;
            boolean continueOk = renderCmsPage(vrctx, path, cmsPage, cmsView, webSiteId, renderMode);
            if (!continueOk) {
                return;
            }
//            }
        } else {
            renderDefault = true;
        }

        if (renderDefault) {
            if (webSiteConfig.isUseDefaultCmsPage() && UtilValidate.isNotEmpty(webSiteConfig.getDefaultCmsPageId())) {
                try {
                    cmsPage = CmsPage.getWorker().findById(delegator, webSiteConfig.getDefaultCmsPageId(), true, request);
                    if (cmsPage == null) {
                        throw new CmsDataException("Unable to find CMS page with pageId: " + webSiteConfig.getDefaultCmsPageId());
                    }
                } catch(Exception e) {
                    Debug.logError(e, "Cms: Error retrieving default page from database. defaultCmsPageId: " + webSiteConfig.getDefaultCmsPageId()
                            + CmsControlUtil.getReqLogIdDelimStr(request), module);
                    handleException(request, response, e, renderMode);
                    return; // Nothing can be sent after this
                }

                Debug.logInfo("Cms: " + (renderMode == CmsCallType.OFBIZ_PREVIEW ? "PREVIEW: " : "") + "No existing or active CMS page mapping found for view '" + name + "'; rendering default CMS page (" + defaultCmsPage.getLogIdReprTargetPage() + ")"
                        + CmsControlUtil.getReqLogIdDelimStr(request), module);
                boolean continueOk = renderCmsPage(vrctx, path, cmsPage, cmsView, webSiteId, renderMode);
                if (!continueOk) {
                    return;
                }
            } else {
                Debug.logInfo("Cms: No existing/active CMS page mapping found for view '" + name + "'; continuing with screen '" + page + "'"
                        + CmsControlUtil.getReqLogIdDelimStr(request), module);
                renderScreen(vrctx);
            }
        }
    }

    @Override
    public void render(String name, String page, String info, String contentType, String encoding,
                       HttpServletRequest request, HttpServletResponse response) throws ViewHandlerException {
        Writer writer;
        try {
            writer = CmsControlUtil.getResponseWriter(request, response);
        } catch (IOException e) {
            Debug.logError(e, "Cms: Error getting response writer: " + e.getMessage() + CmsControlUtil.getReqLogIdDelimStr(request), module);
            handleException(request, response, e, null);
            return;
        }
        try {
            super.render(name, page, info, contentType, encoding, request, response, writer);
        } catch (ViewHandlerException e) {
            Debug.logError(e, "Cms: View rendering error: " + e.getMessage() + CmsControlUtil.getReqLogIdDelimStr(request), module);
            handleException(request, response, e, null);
            return;
        }
    }

    @Override
    public void render(String name, String page, String info, String contentType, String encoding,
                       HttpServletRequest request, HttpServletResponse response, Writer writer) throws ViewHandlerException {
        try {
            super.render(name, page, info, contentType, encoding, request, response, writer);
        } catch (ViewHandlerException e) {
            Debug.logError(e, "Cms: View rendering error: " + e.getMessage() + CmsControlUtil.getReqLogIdDelimStr(request), module);
            handleException(request, response, e, null);
            return;
        }
    }

    protected void renderScreen(ViewRenderContext vrctx) throws ViewHandlerException {
        if (CmsUtil.verboseOn()) {
            Debug.logInfo("Cms: Starting legacy screen widget render process" + CmsControlUtil.getReqLogIdDelimStr(vrctx.request()), module);
        }

        if (webSiteConfig.isSetResponseBrowserNoCacheScreen()) {
            if (CmsUtil.verboseOn()) {
                Debug.logInfo("Cms: Setting browser no-proxy no-cache response" + CmsControlUtil.getReqLogIdDelimStr(vrctx.request()), module);
            }
            CmsControlUtil.checkSetNoCacheResponse(vrctx.request(), vrctx.response());
        }

        super.render(vrctx);
    }

    /**
     * Renders page.
     * <p>
     * DEV NOTE: do not use the "path" parameter.
     *
     * @return false if must prevent any further web response
     */
    protected boolean renderCmsPage(ViewRenderContext vrctx, String path, CmsPage cmsPage, CmsView cmsView, String webSiteId, CmsCallType renderMode) throws ViewHandlerException {
        HttpServletRequest request = vrctx.request();
        HttpServletResponse response = vrctx.response();

        // We must make sure that if for whatever reason a transaction is in place, maybe
        // some runaway transaction, we kill it now, because the CMS rendering happens in a
        // different thread and this could end in a total lockup if read/writing the same entity
        // value if keep a transaction here.
        // Note: I wasn't sure if should do commit/rollback or suspend/resume here/after,
        // but I'm not sure implications of suspend w.r.t. multithread here so doing commit/rollback for now
        endTransactionAlways(request, response);

        if (CmsUtil.verboseOn()) {
            Debug.logInfo("Cms: Starting CMS page render process" + CmsControlUtil.getReqLogIdDelimStr(request), module);
        }

        if (webSiteConfig.isSetResponseBrowserNoCacheCmsPage()) {
            if (CmsUtil.verboseOn()) {
                Debug.logInfo("Cms: Setting browser no-proxy no-cache response" + CmsControlUtil.getReqLogIdDelimStr(request), module);
            }
            CmsControlUtil.checkSetNoCacheResponse(request, response);
        }

        try {

            // Main render invocation
            renderInvoker.invokeCmsRendering(vrctx, cmsPage, cmsView, webSiteId, renderMode);

        // NOTE: 2016: this is never thrown from local cms renders, but leaving here for future use
//        } catch (CmsCallHttpException e) {
//
//            int httpStatus = e.getHttpStatus();
//            int returnCode = passOnHttpStatusesFromCms.contains(httpStatus) ? httpStatus : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
//
//            Exception exToPrint = e;
//            if (returnCode == HttpServletResponse.SC_NOT_FOUND) {
//                // too verbose
//                exToPrint = null;
//            }
//
//            Debug.logError(exToPrint, "Cms: Error rendering page " + cmsPage.getLogIdRepr() +
//                    " for view " + cmsView.getLogIdRepr() + " via CMS invocation: " + e.getMessage() + CmsControlUtil.getReqLogIdDelimStr(request), module);
//            try {
//
//                response.sendError(returnCode);
//            } catch (IOException e1) {
//                Debug.logError(e1, "Cms: Error sending server error response" + CmsControlUtil.getReqLogIdDelimStr(request), module);
//            }
//            return false; // Nothing can be sent after this
        } catch (Exception e) {
            Debug.logError(e, "Cms: Error rendering page " + cmsPage.getLogIdRepr() +
                    " for view " + cmsView.getLogIdRepr() + " via CMS invocation: " + e.getMessage() + CmsControlUtil.getReqLogIdDelimStr(request), module);
            handleException(request, response, e, renderMode);
            return false; // Nothing can be sent after this
        }

        return true;
    }

    /**
     * Handles exceptions by rethrowing as {@link ViewHandlerException}.
     *
     * <p>SCIPIO: 3.0.0: When non-preview or debug, now uses GeneralException propertyMessageList to set safe public
     *  message so we can still propagate the private detailed main exception message internally and not lose debugging info;
     *  note that ControlServlet also applies the same message if secure mode enabled but we do it independently here
     *  to honor CMS-specific settings (TODO: REVIEW: need?).</p>
     */
    protected void handleException(HttpServletRequest request, HttpServletResponse response, Exception ex, CmsCallType renderMode) throws ViewHandlerException {
        // EMULATION of MacroScreenViewHandler behavior - these throws would in stock be handled by ControlServlet and printed
        List<PropertyMessage> publicMsg = null;
        if (renderMode != CmsCallType.OFBIZ_PREVIEW && !UtilRender.RenderExceptionMode.isDebug(CmsRenderUtil.getLiveExceptionMode(request.getServletContext()))) {
            publicMsg = List.of(RequestUtil.getGenericErrorPropertyMessage(request));
        }
        try {
            throw ex;
        } catch (TemplateException e) {
            Debug.logError(e, "Error initializing screen renderer", module);
            throw new ViewHandlerException(e.getMessage(), publicMsg);
        } catch (IOException e) {
            throw new ViewHandlerException("Error in the response writer/output stream: " + e, publicMsg, e);
        } catch (SAXException | ParserConfigurationException e) {
            throw new ViewHandlerException("XML Error rendering page: " + e, publicMsg, e);
        } catch (GeneralException e) {
            throw new ViewHandlerException("Lower level error rendering page: " + e, publicMsg, e);
        } catch (Exception e) {
            throw new ViewHandlerException("General error rendering page: " + e, publicMsg, e);
        }
    }

    /**
     * This kills any transaction running.
     * <p>
     * It currently does it by rolling back rather than
     * committing because there should never be a runaway transaction! If there is one it means
     * there was a code or system error, has to be fixed and the data could be bad, so don't save it.
     * Also, this is what Ofbiz's ControlServlet does with runaway transactions.
     */
    protected void endTransactionAlways(HttpServletRequest request, HttpServletResponse response) {
        if (CmsUtil.verboseOn()) {
            Debug.logInfo("Cms: Stopping any open Ofbiz transactions" + CmsControlUtil.getReqLogIdDelimStr(request), module);
        }

        boolean transInPlace = false;
        try {
            transInPlace = TransactionUtil.isTransactionInPlace();
        } catch (GenericTransactionException e) {
            Debug.logError(e, "Cms: Unable to verify if transaction is in place at " +
                    "time of CMS invocation; invocation may be dangerous!" + CmsControlUtil.getReqLogIdDelimStr(request), module);
        }
        if (transInPlace) {

            // Note: I think commit() or rollback() do the majority of the work here.
            Debug.logWarning("Cms: A transaction was in place at time of CMS invocation (runaway?); " +
                    "performing rollback (to prevent lockups)" + CmsControlUtil.getReqLogIdDelimStr(request), module);
            try {
                TransactionUtil.rollback();
            } catch (GenericTransactionException e) {
                Debug.logError(e, "Cms: Could not rollback transaction at time of CMS invocation" + CmsControlUtil.getReqLogIdDelimStr(request), module);
            }

            // I think there's no point to this... commit() and rollback() take care of most of the work,
            // and if there's a scenario where somehow the transaction wasn't ended, it will 99% likely be
            // shown in the exception catch above.
            // Also note:
//            // Sanity check
//            transInPlace = false;
//            try {
//                transInPlace = TransactionUtil.isTransactionInPlace();
//            } catch (GenericTransactionException e) {
//                Debug.logError(e, "Cms: Unable to verify that no transaction is still in place at time of " +
//                        "CMS invocation following rollback; invocation may be dangerous!" + CmsControlUtil.getReqLogIdDelimStr(request), module);
//            }
//            if (transInPlace) {
//                Debug.logWarning("Cms: A transaction was still in place at time of CMS invocation following " +
//                        "commit and rollback attempts; invocation may be dangerous!" + CmsControlUtil.getReqLogIdDelimStr(request), module);
//            }
        }
    }
}
