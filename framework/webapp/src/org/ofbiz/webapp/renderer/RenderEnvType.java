package org.ofbiz.webapp.renderer;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.ilscipio.scipio.ce.webapp.ftl.context.ContextFtlUtil;

import freemarker.core.Environment;
import freemarker.template.TemplateModelException;
import org.ofbiz.base.util.UtilGenerics;

/**
 * RenderEnvType, also known as renderContextType (SCIPIO).
 */
public enum RenderEnvType {

    /**
     * Webapp request.
     * <p>
     * Currently (2018-08) equivalent to presence of HttpServletRequest
     * (but this could change in future).
     */
    WEBAPP(false, "web"),

    /**
     * Email render context.
     * <p>
     * Currently (2018-08) this counts toward {@link #isStatic()},
     * but this could change in future.
     */
    EMAIL(true, "email"),

    /**
     * General static render context.
     * <p>
     * Generally implies absence of HttpServletRequest in context.
     * <p>
     * Currently (2018-08) this means any non-EMAIL static context.
     */
    STATIC(true, "general");

    private final boolean staticEnv;
    private final String apiName;

    private RenderEnvType(boolean staticEnv, String apiName) {
        this.staticEnv = staticEnv;
        this.apiName = apiName;
    }

    public boolean isWebapp() {
        return !staticEnv;
    }

    public boolean isStatic() {
        return staticEnv;
    }

    public String getApiName() {
        return apiName;
    }

    /**
     * Tries to determine the render context from the request using heuristic.
     */
    public static RenderEnvType fromRequest(HttpServletRequest request) {
        // trivial case
        return WEBAPP;
    }

    /**
     * Tries to determine the render context from the context map using heuristic.
     */
    public static RenderEnvType fromContext(Map<String, ?> context) {
        return fromRequestOrContext((HttpServletRequest) context.get("request"), context);
    }

    /**
     * Tries to determine the render context from the request or context map using heuristic.
     * <p>
     * NOTE: This overload does not check if the context map contains a request field;
     * use {@link #fromContext(Map)} for this.
     * This is overload is a very common optimization.
     * <p>
     * NOTE: In some cases, despite having a request, the {@link #fromContext(Map)} should be
     * used instead, if the request is only meant for logging purposes while the intent
     * is to render using the contents of the context.
     */
    public static RenderEnvType fromRequestOrContext(HttpServletRequest request, Map<String, ?> context) {
        if (request != null) {
            return WEBAPP;
        }
        return (context.get("baseUrl") != null) ? RenderEnvType.EMAIL : RenderEnvType.STATIC;
    }

    public static RenderEnvType fromFtlEnv(Environment env) throws TemplateModelException {
        return fromRequestOrFtlEnv(ContextFtlUtil.getRequest(env), env);
    }

    public static RenderEnvType fromRequestOrFtlEnv(HttpServletRequest request, Environment env) throws TemplateModelException {
        if (request != null) {
            return WEBAPP;
        }
        return (env.getVariable("baseUrl") != null) ? RenderEnvType.EMAIL : RenderEnvType.STATIC;
    }
}
