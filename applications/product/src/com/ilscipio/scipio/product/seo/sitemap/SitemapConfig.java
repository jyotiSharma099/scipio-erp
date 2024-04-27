package com.ilscipio.scipio.product.seo.sitemap;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;

import com.ilscipio.scipio.product.category.CatalogFilter;
import com.ilscipio.scipio.product.category.CatalogFilters;
import org.ofbiz.base.component.ComponentConfig.WebappInfo;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.cache.UtilCache;
import org.ofbiz.base.util.string.FlexibleStringExpander;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.webapp.OfbizUrlBuilder;
import org.ofbiz.webapp.WebAppUtil;
import org.ofbiz.webapp.control.WebAppConfigurationException;
import org.xml.sax.SAXException;

import com.ilscipio.scipio.ce.util.PathUtil;
import com.redfin.sitemapgenerator.W3CDateFormat;

@SuppressWarnings("serial")
public class SitemapConfig implements Serializable {
    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    public static final String SITEMAPCONFIGS_RESOURCE = "sitemaps"; // .properties
    public static final String SITEMAPCOMMON_RESOURCE = "sitemapcommon"; // .properties

    public static final int DEFAULT_SITEMAP_SIZE = UtilProperties.getPropertyAsInteger(SITEMAPCOMMON_RESOURCE, "sitemap.default.sitemapsize", 50000);
    public static final int DEFAULT_INDEX_SIZE = UtilProperties.getPropertyAsInteger(SITEMAPCOMMON_RESOURCE, "sitemap.default.indexsize", 50000);

    public static final FlexibleStringExpander DEFAULT_CMS_PAGE_URL_ATTR = FlexibleStringExpander.getInstance("currentUrl_${localeVar}");

    private static final int DEFAULT_LOG_LEVEL = Debug.getLevelFromString(UtilProperties.getPropertyValue(SITEMAPCOMMON_RESOURCE,
            "sitemap.default.logLevel"), Debug.INFO);

    private static final List<CatalogFilter> DEFAULT_CATALOG_FILTERS = Collections.unmodifiableList(UtilMisc.toList(
            CatalogFilters.ViewAllowCategoryProductFilter.getInstance()
    ));

    // NOTE: This is a special cache that contains only one map as entry: "_all_"- may be re-adapted in the future
    private static final UtilCache<String, Object> websiteCache = UtilCache.createUtilCache("scipio.seo.sitemap.config.website");

    private static final String logPrefix = SitemapGenerator.logPrefix;

    private static final Map<String, W3CDateFormat.Pattern> dateFormatTypeMap;
    static {
        Map<String, W3CDateFormat.Pattern> map = new HashMap<>();
        for(W3CDateFormat.Pattern pattern : W3CDateFormat.Pattern.values()) {
            map.put(pattern.toString(), pattern);
        }
        dateFormatTypeMap = Collections.unmodifiableMap(map);
    }

    private final String webSiteId;
    private final String urlConfPath;
    private final String baseUrl;
    private final boolean baseUrlSecure;
    private final String sitemapWebappPathPrefix;
    private final String sitemapContextPath;
    private final String sitemapDirPath;
    private final String webappPathPrefix;
    private final String contextPath;
    private final String sitemapDir;
    private final String sitemapExtension;
    private final String sitemapIndexFile;
    private final String productFilePrefix;
    private final String categoryFilePrefix;
    private final String contentFilePrefix;
    private final Integer sizemapSize;
    private final Integer indexSize;
    private final boolean doProduct;
    private final boolean doCategory;
    private final boolean doCmsPage;
    private final boolean preProcessTrail;
    // TODO?: REVIEW: I don't see a reason to implement this for sitemaps yet...
    // see SitemapWorker#buildSitemapProduct
    private final boolean doChildProduct = false;

    private final boolean useProductLastModDate;
    // TODO: REVIEW: did not see guarantee that this class is thread-safe
    private final W3CDateFormat dateFormat;

    private final String compress;

    private final List<Locale> locales;
    private final Boolean defaultAltLink;
    private final Map<Locale, LocaleConfig> localeConfigs;

    private final Set<String> prodCatalogIds;
    private final Set<String> prodCatalogCategoryTypeIds;
    private final Set<String> excludeSpecificCategoryIds;
    private final Set<String> excludeSpecificProductIds;

    private final boolean includeVariant;

    private final SitemapGenerator.SitemapGeneratorFactory generatorFactory;
    private final List<CatalogFilter> catalogFilters;
    private final boolean useDefaultCatalogFilters;
    private final boolean useAutoCatalogFilters;

    private final String categoryTraversalMode;
    private final String productTraversalMode;

    private final Map<String, Object> settingsMap; // copy of the settings map, for print/reference/other

    private final FlexibleStringExpander cmsPageUrlAttr;

    private final int logLevel;

    public SitemapConfig(Map<String, Object> map, String webSiteId) {
        this.settingsMap = Collections.unmodifiableMap(new HashMap<>(map));
        this.webSiteId = webSiteId;
        this.urlConfPath = asNormString(map.get("urlConfPath"));
        String baseUrl = asNormString(map.get("baseUrl"));
        if ("none".equalsIgnoreCase(baseUrl)) baseUrl = "";
        this.baseUrl = baseUrl;
        this.baseUrlSecure = asBoolean(map.get("baseUrlSecure"), true);
        this.sitemapWebappPathPrefix = asNormString(map.get("sitemapWebappPathPrefix"));
        this.sitemapContextPath = asNormString(map.get("sitemapContextPath"));
        this.sitemapDirPath = asNormString(map.get("sitemapDirPath"));
        this.webappPathPrefix = asNormString(map.get("webappPathPrefix"));
        this.contextPath = asNormString(map.get("contextPath"));
        this.sitemapDir = asNormString(map.get("sitemapDir"));
        this.sitemapExtension = asNormString(map.get("sitemapExtension"));
        this.sitemapIndexFile = asNormString(map.get("sitemapIndexFile"));
        this.productFilePrefix = asNormString(map.get("productFilePrefix"));
        this.categoryFilePrefix = asNormString(map.get("categoryFilePrefix"));
        this.contentFilePrefix = asNormString(map.get("contentFilePrefix"));
        Integer sizemapSize = asInteger(map.get("sizemapSize"), DEFAULT_SITEMAP_SIZE);
        if (sizemapSize <= 0) sizemapSize = null; // explicit -1 means don't limit
        this.sizemapSize = sizemapSize;
        Integer indexSize = asInteger(map.get("indexSize"), DEFAULT_INDEX_SIZE);
        if (indexSize <= 0) indexSize = null; // explicit -1 means don't limit
        this.indexSize = indexSize;
        this.doProduct = asBoolean(map.get("doProduct"), true);
        this.doCategory = asBoolean(map.get("doCategory"), true);
        this.doCmsPage = asBoolean(map.get("doCmsPage"), true);
        this.preProcessTrail = asBoolean(map.get("preProcessTrail"), false);

        this.useProductLastModDate = asBoolean(map.get("useProductLastModDate"), false);
        String dateFormatStr = asNormString(map.get("dateFormat"));
        W3CDateFormat.Pattern pattern = null;
        if (UtilValidate.isNotEmpty(dateFormatStr)) {
            try {
                pattern = W3CDateFormat.Pattern.valueOf(dateFormatStr);
            } catch(Exception e) {
                Debug.logError(logPrefix+"website '" + webSiteId + "' sitemaps.properties configuration error: invalid dateFormat value (" + dateFormatStr + "): "
                        + e.getMessage() + " (supported values: " + dateFormatTypeMap.keySet().toString() + ")", module);
            }
        }
        W3CDateFormat dateFormat = new W3CDateFormat(pattern != null ? pattern : W3CDateFormat.Pattern.AUTO);
        String timeZoneStr = asNormString(map.get("timeZone"));
        if (timeZoneStr != null) {
            try {
                dateFormat.setTimeZone(TimeZone.getTimeZone(timeZoneStr));
            } catch(Exception e) {
                Debug.logError(logPrefix+"website '" + webSiteId + "' sitemaps.properties configuration error: invalid timeZone value (" + timeZoneStr + "): " + e.getMessage(), module);
            }
        }
        this.dateFormat = dateFormat;

        this.compress = asNormString(map.get("compress"), "gzip");

        this.locales = Collections.unmodifiableList(parseLocales(asNormString(map.get("locales"))));

        this.defaultAltLink = asBoolean(map.get("defaultAltLink"), null);

        Map<Locale, LocaleConfig> localeConfigs = new LinkedHashMap<>();
        for (Locale locale : this.locales) {
            localeConfigs.put(locale, new LocaleConfig(locale, map, "locales." + locale.toString() + "."));
        }
        this.localeConfigs = Collections.unmodifiableMap(localeConfigs);

        this.prodCatalogIds = splitTokensToUnmodifiableSetOrNull(asNormString(map.get("prodCatalogIds")));
        this.prodCatalogCategoryTypeIds = splitTokensToUnmodifiableSetOrNull(asNormString(map.get("prodCatalogCategoryTypeIds")));
        this.excludeSpecificCategoryIds = splitTokensToUnmodifiableSetOrNull(asNormString(map.get("excludeSpecificCategoryIds")));
        this.excludeSpecificProductIds = splitTokensToUnmodifiableSetOrNull(asNormString(map.get("excludeSpecificProductIds")));

        this.includeVariant = asBoolean(map.get("includeVariant"), false);

        String generatorFactoryClsStr = asNormString(map.get("generatorFactory"));
        SitemapGenerator.SitemapGeneratorFactory generatorFactory = null;
        if (UtilValidate.isNotEmpty(generatorFactoryClsStr)) {
            try {
                Class<? extends SitemapGenerator.SitemapGeneratorFactory> generatorFactoryCls = UtilGenerics.cast(
                        Thread.currentThread().getContextClassLoader().loadClass(generatorFactoryClsStr));
                generatorFactory = generatorFactoryCls.getConstructor().newInstance();
            } catch(Exception e) {
                throw new IllegalArgumentException("Error loading generatorFactory " + generatorFactoryClsStr, e);
            }
        }
        this.generatorFactory = generatorFactory;

        this.catalogFilters = Collections.unmodifiableList(readCatalogFilters(map.get("catalogFilters")));
        this.useDefaultCatalogFilters = asBoolean(map.get("useDefaultCatalogFilters"), true);
        this.useAutoCatalogFilters = asBoolean(map.get("useAutoCatalogFilters"), true);

        this.categoryTraversalMode = asNormString(map.get("categoryTraversalMode"), "depth-first");
        this.productTraversalMode = asNormString(map.get("productTraversalMode"), "depth-first");

        this.cmsPageUrlAttr = FlexibleStringExpander.getInstance(asNormString(map.get("cmsPageUrlAttr")));

        Object logLevelObj = map.get("logLevel");
        if (logLevelObj instanceof Integer) {
            this.logLevel = (Integer) logLevelObj;
        } else {
            this.logLevel = Debug.getLevelFromString((String) map.get("logLevel"), DEFAULT_LOG_LEVEL);
        }
    }

    private static List<CatalogFilter> readCatalogFilters(Object catalogFiltersObj) {
        List<CatalogFilter> catalogFilters = null;
        if (catalogFiltersObj instanceof Collection) {
            Collection<?> catalogFiltersColl = (Collection<?>) catalogFiltersObj;
            catalogFilters = new ArrayList<>(catalogFiltersColl.size());
            for(Object catalogFilterObj : catalogFiltersColl) {
                if (catalogFilterObj instanceof CatalogFilter) {
                    catalogFilters.add((CatalogFilter) catalogFilterObj);
                } else if (catalogFilterObj instanceof Class) {
                    Class<? extends CatalogFilter> filterCls = UtilGenerics.cast(catalogFilterObj);
                    try {
                        catalogFilters.add(filterCls.getConstructor().newInstance());
                    } catch(Exception e) {
                        throw new IllegalArgumentException("Error loading catalogFilter " + filterCls.getName(), e);
                    }
                } else if (catalogFilterObj instanceof String) {
                    try {
                        Class<? extends CatalogFilter> filterCls = UtilGenerics.cast(Thread.currentThread().getContextClassLoader().loadClass((String) catalogFilterObj));
                        catalogFilters.add(filterCls.getConstructor().newInstance());
                    } catch(Exception e) {
                        throw new IllegalArgumentException("Error loading catalogFilter " + catalogFilterObj, e);
                    }
                } else {
                    throw new IllegalArgumentException("catalogFilter unrecognized argument type: " + catalogFilterObj.getClass().getName());
                }
            }
        } else if (catalogFiltersObj instanceof String) {
            String catalogFiltersStr = asNormString(catalogFiltersObj);
            if (UtilValidate.isNotEmpty(catalogFiltersStr)) {
                String[] catalogFilterClsStrFull = catalogFiltersStr.split(";");
                catalogFilters = new ArrayList<>(catalogFilterClsStrFull.length);
                for(String catalogFilterClsStr : catalogFilterClsStrFull) {
                    try {
                        Class<? extends CatalogFilter> filterCls = UtilGenerics.cast(Thread.currentThread().getContextClassLoader().loadClass(catalogFilterClsStr));
                        catalogFilters.add(filterCls.getConstructor().newInstance());
                    } catch(Exception e) {
                        throw new IllegalArgumentException("Error loading catalogFilter " + catalogFilterClsStr, e);
                    }
                }
            }
        } else if (catalogFiltersObj != null) {
            throw new IllegalArgumentException("catalogFilter unrecognized argument type: " + catalogFiltersObj.getClass().getName());
        }
        return catalogFilters != null ? catalogFilters : new ArrayList<>();
    }

    public static SitemapConfig getSitemapConfigForWebsite(Delegator delegator, LocalDispatcher dispatcher, String webSiteId) {
        return getWebSiteMapCache().get(webSiteId);
    }

    public static Map<String, SitemapConfig> getAllSitemapConfigs(Delegator delegator, LocalDispatcher dispatcher) {
        return getWebSiteMapCache();
    }

    /*
    private static UtilCache<String, Object> loadWebsiteCache() {
        websiteCache.clear();
        Map<String, SitemapConfig> allConfigs = readStaticConfigsFromProperties();
        // TODO: REVIEW: don't do this because each cache entry has its own expiry
        //for(Map.Entry<String, SitemapConfig> entry : allConfigs.entrySet()) {
        //    websiteCache.put(entry.getKey(), entry.getValue());
        //}
        websiteCache.put("_all_", Collections.unmodifiableMap(allConfigs));
        return websiteCache;
    }
     */

    private static Map<String, SitemapConfig> getWebSiteMapCache() {
        Map<String, SitemapConfig> cache = UtilGenerics.cast(websiteCache.get("_all_"));
        if (cache == null) {
            synchronized(SitemapConfig.class) {
                cache = UtilGenerics.cast(websiteCache.get("_all_"));
                if (cache == null) {
                    cache = Collections.unmodifiableMap(readStaticConfigsFromProperties());
                    websiteCache.put("_all_", cache);
                }
            }
        }
        return cache;
    }

    protected static Map<String, SitemapConfig> readStaticConfigsFromProperties() {
        Map<String, SitemapConfig> configs = new HashMap<>();
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = loader.getResources(SITEMAPCONFIGS_RESOURCE + ".properties");
            while (resources.hasMoreElements()) {
                URL propertyURL = resources.nextElement();
                Debug.logInfo(logPrefix+"loading properties: " + propertyURL, module);
                Properties props = UtilProperties.getProperties(propertyURL);
                if (UtilValidate.isEmpty(props)) {
                    Debug.logError(logPrefix+"Unable to locate properties file " + propertyURL, module);
                } else {
                    Map<String, Map<String, Object>> webSiteConfigs = new HashMap<>();
                    UtilProperties.extractPropertiesWithPrefixAndId(webSiteConfigs, props, "sitemap.");
                    for(Map.Entry<String, Map<String, Object>> entry : webSiteConfigs.entrySet()) {
                        Debug.logInfo(logPrefix+"Read config for website '" + entry.getKey() + "': " + entry.getValue().toString(), module);
                        try {
                            configs.put(entry.getKey(), new SitemapConfig(entry.getValue(), entry.getKey()));
                        } catch(Exception e) {
                            Debug.logError(e, logPrefix+"Unable to read sitemap config for website '" + entry.getKey() + "': " + e.getMessage(), module);
                        }
                    }
                }
            }
            for(Map.Entry<String, SitemapConfig> entry : configs.entrySet()) {
                Debug.logInfo(logPrefix+"Found sitemap config for website: " + entry.getKey(), module);
            }
        } catch (Exception e) {
            Debug.logError(e, logPrefix+"Could not load list of " + SITEMAPCONFIGS_RESOURCE + ".properties", module);
        }
        return configs;
    }

    protected static String asNormString(Object obj, String defaultValue) {
        if (obj == null) return defaultValue;
        String str = obj.toString().trim();
        return str.isEmpty() ? defaultValue : str;
    }

    protected static String asNormString(Object obj) {
        return asNormString(obj, null);
    }

    protected static Boolean asBoolean(Object obj, Boolean defaultValue) {
        Boolean value = UtilMisc.booleanValueVersatile(obj);
        return (value != null) ? value : defaultValue;
    }

    protected static Integer asInteger(Object obj, Integer defaultValue) {
        if (obj == null) return defaultValue;
        else if (obj instanceof Integer) return (Integer) obj;
        else if (obj instanceof Long) return ((Long) obj).intValue();
        else if (obj instanceof String) {
            String str = (String) obj;
            if (str.isEmpty()) return defaultValue;
            try {
                return Integer.parseInt(str);
            } catch(Exception e) {
                Debug.logError("Invalid integer value from sitemap config: " + obj + "; using default: " + defaultValue, module);

            }
        } else {
            Debug.logError("Invalid integer value from sitemap config: " + obj + "; using default: " + defaultValue, module);
        }
        return defaultValue;
    }

    // SIMPLE GETTERS

    /** Returns the original settings map used to build the instance (raw). */
    public Map<String, Object> getSettingsMap() { return settingsMap; }

    public String getWebSiteId() {
        return webSiteId;
    }

    public String getUrlConfPath() {
        return urlConfPath;
    }

    /**
     * NOTE: If null, means was not specified and should use default for website.
     * If empty, means don't append any.
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isBaseUrlSecure() {
        return baseUrlSecure;
    }

    public String getSitemapWebappPathPrefix() {
        return sitemapWebappPathPrefix;
    }

    public String getSitemapContextPath() {
        return sitemapContextPath;
    }

    public String getSitemapDirPath() {
        return sitemapDirPath;
    }

    public String getWebappPathPrefix() {
        return webappPathPrefix;
    }

    public String getContextPath() {
        return contextPath;
    }

    public String getSitemapDir() {
        return sitemapDir;
    }

    public String getSitemapExtension() {
        return sitemapExtension;
    }

    public String getSitemapIndexFile() {
        return sitemapIndexFile;
    }

    public String getProductFilePrefix() {
        return productFilePrefix;
    }

    public String getCategoryFilePrefix() {
        return categoryFilePrefix;
    }

    public String getContentFilePrefix() {
        return contentFilePrefix;
    }

    public Integer getSizemapSize() {
        return sizemapSize;
    }

    public Integer getIndexSize() {
        return indexSize;
    }

    public boolean isDoProduct() {
        return doProduct;
    }

    public boolean isDoCategory() {
        return doCategory;
    }

    public boolean isDoContent() {
        return isDoCmsPage(); // only one for now
    }

    public boolean isDoCmsPage() {
        return doCmsPage;
    }

    public boolean isPreProcessTrail() {
        return preProcessTrail;
    }

    public boolean isUseProductLastModDate() {
        return useProductLastModDate;
    }

    public W3CDateFormat getDateFormat() {
        return dateFormat;
    }

    public boolean isDoChildProduct() {
        return doChildProduct;
    }

    public String getCompress() {
        return compress;
    }

    public boolean isGzip() {
        return "gzip".equals(getCompress());
    }

    public List<Locale> getLocales() {
        return locales;
    }

    public List<Locale> getLocalesOrDefault(GenericValue webSite, GenericValue productStore) {
        return locales.isEmpty() ? UtilMisc.toList(getDefaultLocale(webSite, productStore)) : locales;
    }

    public Boolean getDefaultAltLink() {
        return defaultAltLink;
    }

    public Map<Locale, LocaleConfig> getLocaleConfigs() {
        return localeConfigs;
    }

    public LocaleConfig getLocaleConfig(Locale locale) {
        return getLocaleConfigs().get(locale);
    }

    public LocaleConfig getLocaleConfigOrDefault(Locale locale) {
        LocaleConfig localeConfig = getLocaleConfig(locale);
        return (localeConfig != null) ? localeConfig : new LocaleConfig(locale, Map.of(), "locales." + locale.toString() + ".");
    }

    public Locale getDefaultLocale(GenericValue webSite, GenericValue productStore) {
        if (locales.size() > 0) {
            return locales.get(0);
        } else if (productStore != null) {
            Locale locale = UtilMisc.parseLocale(productStore.getString("defaultLocaleString"));
            return locale != null ? locale : Locale.getDefault();
        } else {
            return Locale.getDefault();
        }
    }

    /**
     * Allowed prodCatalogIds or null if no filter.
     */
    public Set<String> getProdCatalogIds() {
        return prodCatalogIds;
    }

    /**
     * Allowed prodCatalogCategoryTypeIds or null if no filter.
     */
    public Set<String> getProdCatalogCategoryTypeIds() {
        return prodCatalogCategoryTypeIds;
    }

    public Set<String> getExcludeSpecificCategoryIds() {
        return excludeSpecificCategoryIds;
    }

    public Set<String> getExcludeSpecificProductIds() {
        return excludeSpecificProductIds;
    }

    /**
     * NOTE: setting true does not automatically include variants that are not directly linked to a category (via ProductCategoryMember).
     * (TODO: REVIEW?)
     */
    public boolean isIncludeVariant() {
        return includeVariant;
    }

    public SitemapGenerator.SitemapGeneratorFactory getGeneratorFactory() { return generatorFactory; }

    public List<CatalogFilter> getCatalogFilters() {
        return catalogFilters;
    }

    public boolean isUseDefaultCatalogFilters() {
        return useDefaultCatalogFilters;
    }

    public boolean isUseAutoCatalogFilters() {
        return useAutoCatalogFilters;
}

    public List<CatalogFilter> getDefaultCatalogFilters() { return DEFAULT_CATALOG_FILTERS; }

    public String getCategoryTraversalMode() { return categoryTraversalMode; }

    public String getProductTraversalMode() { return productTraversalMode; }

    // ADVANCED GETTERS

    public String getSitemapDirUrlLocation(String webappDir) {
        if (sitemapDir != null && sitemapDir.contains("://")) {
            return sitemapDir;
        } else {
            String result = concatPaths(webappDir, sitemapDir);
            if (result.contains("://")) return result;
            else return Paths.get(result).toUri().toString();
        }
    }

    /**
     * Abstraction method, for Sitemap use only.
     */
    public String getDefaultBaseUrl(OfbizUrlBuilder urlBuilder, boolean secure) throws GenericEntityException, WebAppConfigurationException, IOException, SAXException {
        StringBuilder sb = new StringBuilder();
        urlBuilder.buildHostPart(sb, secure);
        return sb.toString();
    }

    /**
     * Abstraction method, for Sitemap use only.
     */
    public String getDefaultWebappPathPrefix(OfbizUrlBuilder urlBuilder) throws GenericEntityException, WebAppConfigurationException, IOException, SAXException {
        StringBuilder sb = new StringBuilder();
        urlBuilder.buildPathPartWithWebappPathPrefix(sb, "");
        PathUtil.removeTrailDelim(sb);
        return sb.toString();
    }

    /**
     * Abstraction method, for Sitemap use only.
     */
    public String getDefaultContextPath(Delegator delegator) throws GenericEntityException, IOException, SAXException {
        WebappInfo webAppInfo = WebAppUtil.getWebappInfoFromWebsiteId(webSiteId);
        return webAppInfo.getContextRoot();
    }

    public FlexibleStringExpander getCmsPageUrlAttr() {
        return cmsPageUrlAttr;
    }

    public int getLogLevel() {
        return logLevel;
    }

    /**
     * Concats paths while handling bad input (to an extent).
     * TODO: central util and remove this (WARN: special null/empty check).
     */
    static String concatPaths(String... parts) {
        StringBuilder sb = new StringBuilder();
        for(String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            } else if (sb.length() == 0) {
                sb.append(part);
            } else {
                if (sb.charAt(sb.length() - 1) == '/') {
                    if (part.startsWith("/")) {
                        sb.append(part.substring(1));
                    } else {
                        sb.append(part);
                    }
                } else {
                    if (part.startsWith("/")) {
                        sb.append(part);
                    } else {
                        sb.append('/');
                        sb.append(part);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * TODO: util instead
     */
    static List<Locale> parseLocales(String localesStr) {
        ArrayList<Locale> locales = new ArrayList<>();
        if (UtilValidate.isEmpty(localesStr)) {
            locales.trimToSize();
            return locales;
        }
        Set<String> dupSet = new HashSet<>();
        for(String locStr : localesStr.split("\\s*,\\s*")) {
            Locale locale = UtilMisc.parseLocale(locStr.trim());
            if (locale != null && !dupSet.contains(locale.toString())) {
                locales.add(locale);
                dupSet.add(locale.toString());
            }
        }
        locales.trimToSize();
        return locales;
    }

    static Set<String> splitTokensToSet(String str) {
        Set<String> set = new LinkedHashSet<>();
        if (str != null) {
            for(String token : str.split("\\s*,\\s*")) {
                set.add(token.trim());
            }
        }
        return set;
    }

    static Set<String> splitTokensToUnmodifiableSetOrNull(String str) {
        Set<String> set = splitTokensToSet(str);
        return set.isEmpty() ? null : Collections.unmodifiableSet(set);
    }

    public static class LocaleConfig implements Serializable {
        private final Locale locale;
        private final String urlConfPath;
        private final String urlConfMode;
        private final String webSiteId;
        private final String baseUrl;
        private final String webappPathPrefix;
        private final String contextPath;
        private final String sitemapWebappPathPrefix;
        private final String sitemapContextPath;
        private final FlexibleStringExpander cmsPageUrlAttr;

        protected LocaleConfig(Locale locale, Map<String, Object> map, String prefix) {
            this.locale = locale;
            this.urlConfPath = asNormString(map.get(prefix + "urlConfPath"));
            this.urlConfMode = asNormString(map.get(prefix + "urlConfMode"));
            this.webSiteId = asNormString(map.get(prefix + "webSiteId"));
            this.baseUrl = asNormString(map.get(prefix + "baseUrl"));
            this.webappPathPrefix = asNormString(map.get(prefix + "webappPathPrefix"));
            this.contextPath = asNormString(map.get(prefix + "contextPath"));
            this.sitemapWebappPathPrefix = asNormString(map.get(prefix + "sitemapWebappPathPrefix"));
            this.sitemapContextPath = asNormString(map.get(prefix + "sitemapContextPath"));
            this.cmsPageUrlAttr = FlexibleStringExpander.getInstance(asNormString(map.get(prefix + "cmsPageUrlAttr")));
        }

        public Locale getLocale() {
            return locale;
        }

        public String getUrlConfPath() {
            return urlConfPath;
        }

        public String getUrlConfMode() {
            return urlConfMode;
        }

        public String getWebSiteId() {
            return webSiteId;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getWebappPathPrefix() {
            return webappPathPrefix;
        }

        public String getContextPath() {
            return contextPath;
        }

        public String getSitemapWebappPathPrefix() {
            return sitemapWebappPathPrefix;
        }

        public String getSitemapContextPath() {
            return sitemapContextPath;
        }

        public FlexibleStringExpander getCmsPageUrlAttr() {
            return cmsPageUrlAttr;
        }
    }
}