package com.ilscipio.scipio.solr;

import org.ofbiz.base.util.ContinueException;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.ProcessSignals;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.GenericPK;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.util.EntityListIterator;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.ServiceUtil;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * Special Product-focused EntityIndexer that produces Solr documents that entity indexing consumers can convert and
 * commit to their data sources. Invoked by scheduleProductIndexing service.
 * Instances are global; {@link SolrDocBuilder} is a local worker that holds dispatch context.
 */
public class ProductIndexer extends EntityIndexer {
    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    protected ProductIndexer(String name, Map<String, ?> properties, Queue<Entry> queue) {
        super(name, properties, queue);
    }

    public static class Factory extends EntityIndexer.Factory {
        protected static final Factory DEFAULT = new Factory() {};

        @Override
        public ProductIndexer makeIndexer(String name, Map<String, ?> properties) {
            return new ProductIndexer(name, properties, makeQueue(properties));
        }
    }

    public static ProductIndexer getDefault() {
        return EntityIndexer.getIndexer("Product");
    }

    @Override
    public IndexingStatus readDocsAndCommit(DispatchContext dctx, Map<String, Object> context, Iterable<Entry> entries) throws GeneralException, InterruptedException, IOException {
        return (IndexingStatus) super.readDocsAndCommit(dctx, context, entries);
    }

    @Override
    public IndexingStatus readDocs(DispatchContext dctx, Map<String, Object> context, Iterable<Entry> entries, List<DocEntry> docs,
                                   Set<Entry> docsToRemove) throws GeneralException, InterruptedException, IOException {
        SolrDocBuilder docBuilder = SolrDocBuilder.getInstance(dctx, context);

        // Eliminate duplicates and read only the last Entry (action) for each product
        Map<String, ProductEntry> products = new LinkedHashMap<>();
        for(Entry entry : entries) {
            String id = entry.getShortPk();
            ProductEntry prevEntry = products.get(id);
            products.put(id, ((ProductEntry) entry).merge(prevEntry));
        }

        // Expand virtual/variants
        Map<String, ProductEntry> expandedProducts = new LinkedHashMap<>();
        SolrDocBuilder.ExpandProductResult expandResult = docBuilder.expandProductsForIndexing(UtilGenerics.cast(products.values()), expandedProducts);

        IndexingHookHandler.HookType hookType = IndexingHookHandler.HookType.ECA;
        List<? extends IndexingHookHandler> hookHandlers = IndexingHookHandler.Handlers.getHookHandlers(
                IndexingHookHandler.Handlers.getHookHandlerFactories(hookType));
        String logPrefix = "readDocs: ";
        IndexingStatus.Standard status = new IndexingStatus.Standard(dctx, hookType, docBuilder, expandedProducts.size(), getBufSize(), logPrefix);
        if (expandResult.isError()) { // for expandProductsForIndexing above - need to return status
            status.registerGeneralFailure("Error expanding products for indexing: " + ServiceUtil.getErrorMessage(expandResult.getErrorResult()), null);
            return status;
        }

        for(IndexingHookHandler hookHandler : hookHandlers) {
            try {
                hookHandler.begin(status);
            } catch (ContinueException e) {
                status.registerHookFailure(null, e, hookHandler, "begin");
            }
        }
        int docsConsumed = 0;
        Iterator<Map.Entry<String, ProductEntry>> prodIt = expandedProducts.entrySet().iterator();
        // Build (solr) documents and ProductDocBuilder instances, collect results
        while (prodIt.hasNext()) {
            status.updateStartEndIndex(docsConsumed);
            docsConsumed = 0;
            for(IndexingHookHandler hookHandler : hookHandlers) {
                try {
                    hookHandler.beginBatch(status);
                } catch (ContinueException e) {
                    status.registerHookFailure(null, e, hookHandler, "beginBatch");
                }
            }
            /* caller gives better for now
            if (isDebug()) {
                String progressStr = status.getIndexProgressString();
                if (progressStr != null) {
                    Debug.logInfo(logPrefix + "Reading products " + status.getIndexProgressString() + " for indexing", module);
                }
            }
             */
            int numLeft = status.getBufSize();
            while ((status.getBufSize() <= 0 || numLeft > 0) && prodIt.hasNext()) {
                docsConsumed++;
                Map.Entry<String, ProductEntry> mapEntry = prodIt.next();
                ProductEntry entry = mapEntry.getValue();
                String productId = mapEntry.getKey();
                if (entry.isExplicitRemove()) {
                    docsToRemove.add(entry);
                    for(IndexingHookHandler hookHandler : hookHandlers) {
                        try {
                            hookHandler.processDocRemove(status, entry);
                        } catch (ContinueException e) {
                            status.registerHookFailure(null, e, hookHandler, "processDocRemove");
                        }
                    }
                } else {
                    // IMPORTANT: 2020-05-13: Contrary to older code here we'll always force a new Product lookup due to risks of not doing and many cases doing that anyway
                    //Map<String, Object> product = UtilGenerics.cast(props.get("instance"));
                    GenericValue product = docBuilder.getProductData().getProduct(dctx, productId, false);
                    if (product != null) {
                        Map<String, Object> doc;
                        SolrDocBuilder.ProductDocBuilder data;
                        try {
                            Timestamp moment = UtilDateTime.nowTimestamp();
                            data = docBuilder.makeProductDocBuilder(product, moment);
                            doc = docBuilder.makeProductMapDoc(data, entry, null);
                            if (doc != null) {
                                status.increaseNumDocs(1);
                                numLeft--;
                                ProductDocEntry docEntry = makeDocEntry(entry, doc, data);
                                docs.add(docEntry);
                                for (IndexingHookHandler hookHandler : hookHandlers) {
                                    try {
                                        hookHandler.processDocAdd(status, docEntry);
                                    } catch (ContinueException e) {
                                        status.registerHookFailure(null, e, hookHandler, "processDocAdd");
                                    }
                                }
                            } else {
                                status.increaseNumFiltered(1);
                            }
                        } catch (ContinueException e) {
                            status.registerGeneralFailure("Error reading product '" + productId + "'", e);
                        }
                    } else {
                        if (entry.isExplicitAdd()) {
                            status.registerGeneralFailure("Error reading product '" + productId + "' for indexing: invalid id or has been removed", null);
                        } else {
                            docsToRemove.add(entry);
                            for (IndexingHookHandler hookHandler : hookHandlers) {
                                try {
                                    hookHandler.processDocRemove(status, entry);
                                } catch (ContinueException e) {
                                    status.registerHookFailure(null, e, hookHandler, "processDocRemove");
                                }
                            }
                        }
                    }
                }
            }
            for(IndexingHookHandler hookHandler : hookHandlers) {
                try {
                    hookHandler.endBatch(status);
                } catch (ContinueException e) {
                    status.registerHookFailure(null, e, hookHandler, "endBatch");
                }
            }
            if (docsConsumed == 0) {
                break;
            }
        }
        for(IndexingHookHandler hookHandler : hookHandlers) {
            try {
                hookHandler.end(status);
            } catch (ContinueException e) {
                status.registerHookFailure(null, e, hookHandler, "end");
            }
        }
        if (status.getGeneralFailures() > 0) {
            Debug.logError("Problems occurred processing product data: failures: " + status.getGeneralFailures() + "; success: " + status.getNumDocs() + "; filtered: " + status.getNumFiltered(), module);
        } else {
            Debug.logInfo("Processing product data: success: " + status.getNumDocs() + "; fail: " + status.getGeneralFailures() + "; filtered: " + status.getNumFiltered(), module);
        }
        return status;
    }

    @Override
    public ProductEntry makeEntry(GenericPK pk, Object entityRef, Action action, long entryTime, Collection<String> topics, String flush, Map<String, Object> context, Object properties) {
        return new ProductEntry(pk, entityRef, action, entryTime, topics, flush, context);
    }

    public ProductEntry makeEntry(GenericPK pk, Action action, Collection<String> topics) {
        return (ProductEntry) super.makeEntry(pk, action, topics);
    }

    @Override
    public ProductEntry makeEntry(Entry other, Object pk, Long entryTime) {
        return new ProductEntry((ProductEntry) other, toPk(pk), entryTime);
    }

    /**
     * Product queue entry.
     */
    public static class ProductEntry extends Entry {
        protected final Boolean updateVariants;
        protected final Boolean updateVariantsDeep;
        protected final Boolean updateVirtual;
        protected final Boolean updateVirtualDeep;

        protected ProductEntry(GenericPK pk, Object entityRef, Action action, long entryTime, Collection<String> topics, String flush, Map<String, Object> context) {
            super(pk, entityRef, action, entryTime, topics, flush, context);
            this.updateVariants = (context != null) ? (Boolean) context.get("updateVariants") : null;
            this.updateVariantsDeep = (context != null) ? (Boolean) context.get("updateVariantsDeep") : null;
            this.updateVirtual = (context != null) ? (Boolean) context.get("updateVirtual") : null;
            this.updateVirtualDeep = (context != null) ? (Boolean) context.get("updateVirtualDeep") : null;
        }

        protected ProductEntry(GenericPK pk, Object entityRef, Action action, long entryTime, Collection<String> topics, String flush, Map<String, Object> context,
                               Boolean updateVariants, Boolean updateVariantsDeep, Boolean updateVirtual, Boolean updateVirtualDeep) {
            super(pk, entityRef, action, entryTime, topics, flush, context);
            this.updateVariants = updateVariants;
            this.updateVariantsDeep = updateVariantsDeep;
            this.updateVirtual = updateVirtual;
            this.updateVirtualDeep = updateVirtualDeep;
        }

        public ProductEntry(ProductEntry other, GenericPK pk, Long entryTime) {
            super(other, pk, entryTime);
            this.updateVariants = other.updateVariants;
            this.updateVariantsDeep = other.updateVariantsDeep;
            this.updateVirtual = other.updateVirtual;
            this.updateVirtualDeep = other.updateVirtualDeep;
        }

        public Boolean getUpdateVariants() { return updateVariants; }
        public Boolean getUpdateVariantsDeep() { return updateVariantsDeep; }
        public Boolean getUpdateVirtual() { return updateVirtual; }
        public Boolean getUpdateVirtualDeep() { return updateVirtualDeep; }

        public boolean isUpdateRelatedProducts() { return isUpdateVariants() || isUpdateVirtual(); }
        public boolean isUpdateVariants() { return Boolean.TRUE.equals(getUpdateVariants()) || isUpdateVariantsDeep(); }
        public boolean isUpdateVariantsDeep() { return Boolean.TRUE.equals(getUpdateVariantsDeep()); }
        public boolean isUpdateVirtual() { return Boolean.TRUE.equals(getUpdateVirtual()) || isUpdateVirtualDeep(); }
        public boolean isUpdateVirtualDeep() { return Boolean.TRUE.equals(getUpdateVirtualDeep()); }

        public GenericValue getProductValue() { // NOTE: no lookup
            if (entityRef instanceof GenericValue && "Product".equals(((GenericValue) entityRef).getEntityName())) {
                return (GenericValue) entityRef;
            }
            return null;
        }

        public ProductEntry merge(ProductEntry other) { // assumes same product
            if (other == null) {
                return this;
            }
            if (Objects.equals(this.action, other.action) && Objects.equals(this.updateVariants, other.updateVariants) &&
                Objects.equals(this.updateVariantsDeep, other.updateVariantsDeep) && Objects.equals(this.updateVirtual, other.updateVirtual) &&
                Objects.equals(this.updateVirtualDeep, other.updateVirtualDeep)) {
                return this;
            }
            Action action = null;
            if (this.isExplicitRemove() && other.isExplicitRemove()) {
                action = Action.REMOVE;
            } else if (this.isExplicitAdd() || other.isExplicitAdd()) {
                action = Action.ADD;
            }
            Object entityRef = this.entityRef != null ? this.entityRef : other.entityRef;
            long entryTime = Math.max(this.entryTime, other.entryTime);
            Boolean updateVariants = maxBool(this.updateVariants, other.updateVariants);
            Boolean updateVariantsDeep = maxBool(this.updateVariantsDeep, other.updateVariantsDeep);
            Boolean updateVirtual = maxBool(this.updateVirtual, other.updateVirtual);
            Boolean updateVirtualDeep = maxBool(this.updateVirtualDeep, other.updateVirtualDeep);
            Collection<String> topics;
            if (UtilValidate.isNotEmpty(this.topics)) {
                if (UtilValidate.isNotEmpty(other.topics)) {
                    topics = new LinkedHashSet<>(this.topics);
                    topics.addAll(other.topics);
                } else {
                    topics = this.topics;
                }
            } else {
                topics = other.topics;
            }
            String flush;
            if (UtilValidate.isNotEmpty(this.flush)) {
                if (UtilValidate.isNotEmpty(other.flush)) {
                    if ("all".equals(this.flush) || "all".equals(other.flush)) {
                        flush = "all";
                    } else {
                        flush = this.flush;
                    }
                } else {
                    flush = this.flush;
                }
            } else {
                flush = other.flush;
            }
            return new ProductEntry(pk, entityRef, action, entryTime, topics, flush, null,
                    updateVariants, updateVariantsDeep, updateVirtual, updateVirtualDeep);
        }

        private static Boolean maxBool(Boolean first, Boolean second) {
            if (first != null) {
                if (second != null) {
                    return first || second;
                } else {
                    return first ? first : null;
                }
            } else {
                if (second != null) {
                    return second ? second : null;
                } else {
                    return null;
                }
            }
        }
    }

    public ProductDocEntry makeDocEntry(Entry entry, Map<String, Object> doc, Object data) {
        return new ProductDocEntry((ProductEntry) entry, doc, (SolrDocBuilder.ProductDocBuilder) data);
    }

    public ProductDocEntry makeDocEntry(GenericPK pk, Map<String, Object> doc, Object data) {
        return new ProductDocEntry(pk, doc, (SolrDocBuilder.ProductDocBuilder) data);
    }

    /**
     * Processed document entry (commit).
     */
    public class ProductDocEntry extends DocEntry { // TODO: extend DocEntry in EntityIndexer
        protected ProductDocEntry(ProductEntry entry, Map<String, Object> doc, SolrDocBuilder.ProductDocBuilder data) {
            super(entry, doc, data);
        }

        public ProductDocEntry(GenericPK pk, Map<String, Object> doc, SolrDocBuilder.ProductDocBuilder data) {
            super(pk, doc, data);
        }

        @Override
        public Map<String, Object> getDoc() {
            return super.getDoc();
        }

        @Override
        public ProductEntry getEntry() {
            return (ProductEntry) super.getEntry();
        }

        @Override
        public ProductEntry makeEntry(Action action, long entryTime, Collection<String> topics, String flush, Map<String, Object> context, Object properties) {
            return ProductIndexer.this.makeEntry(getPk(), (getEntry() != null) ? getEntry().getEntityRef() : null, action, entryTime, topics, flush, context, properties);
        }

        @Override
        public SolrDocBuilder.ProductDocBuilder getData() {
            return (SolrDocBuilder.ProductDocBuilder) super.getData();
        }
    }

    /**
     * Processes an iterator of products, producing intermediate solr maps if necessary, for consumption by hooks
     * implementing {@link IndexingHookHandler}.
     * This is a general-purpose method that allows to reuse hooks anywhere.
     */
    public IndexingStatus runProductHooks(DispatchContext dctx, Map<String, ?> context,
                                          Object products, SolrDocBuilder docBuilder,
                                          IndexingHookHandler.HookType hookType, List<? extends IndexingHookHandler> hookHandlers,
                                          List<SolrDocBuilder.ProductFilter> productFilters, ProcessSignals processSignals,
                                          int bufSize, String logPrefix, Object logger) throws GeneralException, InterruptedException, IOException {
        Iterator<? extends Map<String, Object>> productsIt = UtilMisc.asIterator(products);
        try {
            int numDocs = 0;
            IndexingStatus.Standard status = new IndexingStatus.Standard(dctx, hookType,
                    docBuilder, numDocs, bufSize, logPrefix);
            for(IndexingHookHandler hookHandler : hookHandlers) {
                try {
                    hookHandler.begin(status);
                } catch (ContinueException e) {
                    status.registerHookFailure(null, e, hookHandler, "begin");
                }
            }

            int docsConsumed = 0;
            boolean lastReached = false;
            while (!lastReached) {
                if (processSignals != null && processSignals.isSet("stop")) {
                    status.setAborted(true);
                    return status;
                }
                status.updateStartEndIndex(docsConsumed);
                docsConsumed = 0;

                for(IndexingHookHandler hookHandler : hookHandlers) {
                    try {
                        hookHandler.beginBatch(status);
                    } catch (ContinueException e) {
                        status.registerHookFailure(null, e, hookHandler, "beginBatch");
                    }
                }

                // TODO: logging callback
                //Debug.logInfo(logPrefix+"Reading products " + status.getIndexProgressString() + " for indexing", module);
                int numLeft = bufSize;
                while ((bufSize <= 0 || numLeft > 0) && !lastReached) {
                    ProductDocEntry docEntry = null;
                    Object productObj = (productsIt instanceof EntityListIterator) ? productsIt.next() : (productsIt.hasNext() ? productsIt.next() : null);
                    if (productObj != null) {
                        docsConsumed++;

                        Timestamp moment = UtilDateTime.nowTimestamp();
                        try {
                            docEntry = docBuilder.asDocEntry(productObj, productFilters, moment);
                            if (docEntry != null) {
                                status.increaseNumDocs(1);
                                numLeft--;

                                for (IndexingHookHandler hookHandler : hookHandlers) {
                                    try {
                                        hookHandler.processDocAdd(status, docEntry);
                                    } catch (ContinueException e) {
                                        status.registerHookFailure(null, e, hookHandler, "processDocAdd");
                                    }
                                }
                            } else {
                                status.increaseNumFiltered(1);
                            }
                        } catch (ContinueException e) {
                            if (docEntry != null && docEntry.getShortPk() != null) {
                                status.registerGeneralFailure("Error reading product [" + docEntry.getShortPk() + "]", e);
                            } else {
                                status.registerGeneralFailure("Error reading product [" +
                                        (productObj instanceof GenericValue ? ((GenericValue) productObj).getPrimaryKey() : productObj) + "]", e);
                            }
                        }
                    } else {
                        lastReached = true;
                    }
                }

                for(IndexingHookHandler hookHandler : hookHandlers) {
                    try {
                        hookHandler.endBatch(status);
                    } catch (ContinueException e) {
                        status.registerHookFailure(null, e, hookHandler, "endBatch");
                    }
                }
                if (docsConsumed == 0) {
                    break;
                }
            }

            for(IndexingHookHandler hookHandler : hookHandlers) {
                try {
                    hookHandler.end(status);
                } catch (ContinueException e) {
                    status.registerHookFailure(null, e, hookHandler, "end");
                }
            }

            // better to let callers
            //String cacheStats = indexer.getLogStatsShort();
            //cacheStats = (cacheStats != null) ? " (caches: " + cacheStats + ")" : "";
            //String statusMsg = "Processed " + status.getNumDocs() + " documents; failures: " + (status.getNumFailures() + status.getHookFailures());
            //return UtilMisc.put((status.getNumFailures() > 0) ? ServiceUtil.returnFailure(statusMsg) : ServiceUtil.returnSuccess(statusMsg),
            //        "numDocs", numDocs);
            return status;
        } finally {
            if (productsIt instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) productsIt).close();
                } catch(Exception e) {
                }
            }
        }
    }

    public IndexingStatus extractEntriesToDocs(DispatchContext dctx, Map<String, Object> context, long entryTime, Object properties,
                                               List<DocEntry> docs, Set<Entry> docsToRemove) throws GeneralException, InterruptedException, IOException {
        return (IndexingStatus) super.extractEntriesToDocs(dctx, context, entryTime, properties, docs, docsToRemove);
    }

    public IndexingStatus extractEntriesToDocs(DispatchContext dctx, Map<String, Object> context, List<DocEntry> docs,
                                               Set<Entry> docsToRemove) throws GeneralException, InterruptedException, IOException {
        return extractEntriesToDocs(dctx, context, System.currentTimeMillis(), null, docs, docsToRemove);
    }
}
