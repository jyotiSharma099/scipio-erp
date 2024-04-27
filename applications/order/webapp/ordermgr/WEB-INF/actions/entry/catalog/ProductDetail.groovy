/*
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
 */

/*
 * This script is also referenced by the [Scipio: shop]'s screens and
 * should not contain order component's specific code.
 */
package com.ilscipio.scipio.ce;
import org.ofbiz.base.util.*
import org.ofbiz.base.util.cache.UtilCache
import org.ofbiz.entity.condition.EntityCondition
import org.ofbiz.entity.condition.EntityOperator
import org.ofbiz.entity.util.EntityTypeUtil
import org.ofbiz.entity.util.EntityUtil
import org.ofbiz.entity.util.EntityUtilProperties
import org.ofbiz.order.shoppingcart.ShoppingCart
import org.ofbiz.order.shoppingcart.ShoppingCartEvents
import org.ofbiz.product.catalog.CatalogWorker
import org.ofbiz.product.product.ProductContentWrapper
import org.ofbiz.product.product.ProductWorker
import org.ofbiz.product.store.ProductStoreWorker
import org.ofbiz.service.ServiceUtil
import org.ofbiz.webapp.taglib.ContentUrlTag
import org.ofbiz.webapp.website.WebSiteWorker

final module = "ProductDetail.groovy"
long lastTime = System.currentTimeMillis();
UtilCache<String, Map> productCache = UtilCache.getOrCreateUtilCache("product.productdetail.rendered", 0,0,
        UtilProperties.getPropertyAsLong("cache", "product.productdetail.rendered.expireTime",86400000),
        UtilProperties.getPropertyAsBoolean("cache", "product.productdetail.rendered.softReference",true));
Boolean useCache = UtilProperties.getPropertyAsBoolean("cache", "product.productdetail.rendered.enable", false);
cart = ShoppingCartEvents.getCartObject(request);
productId = null;
product = context.product;
priceMap = [:];

productId = (product?.productId) ?: parameters.product_id; // SCIPIO: 2.1.0: Use parameters map
context.product_id = productId;

// set currency format
currencyUomId = null;
if (cart) currencyUomId = cart.getCurrency();
if (!currencyUomId) currencyUomId = EntityUtilProperties.getPropertyValue("general", "currency.uom.id.default", "USD", delegator);
context.currencyUomId = currencyUomId; // SCIPIO: 2018-07-18: make available to ftl

// get the product store for only Sales Order not for Purchase Order.
productStore = null;
productStoreId = null;
if (cart.isSalesOrder()) {
    productStore = ProductStoreWorker.getProductStore(request);
    productStoreId = productStore.productStoreId;
    context.productStoreId = productStoreId;
    if (productStore != null) {
        context.productStore = productStore; // SCIPIO: This may be missing in orderentry
    }
}

// get the shopping lists for the user (if logged in)
if (userLogin) {
    exprList = [EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, userLogin.partyId),
                EntityCondition.makeCondition("listName", EntityOperator.NOT_EQUAL, "auto-save")];
    allShoppingLists = from("ShoppingList").where(exprList).orderBy("listName").cache(true).queryList();
    context.shoppingLists = allShoppingLists;
}

contentPathPrefix = CatalogWorker.getContentPathPrefix(request);
context.contentPathPrefix = contentPathPrefix;
product = context.product;  // SCIPIO: prevents crash if missing
categoryId = parameters.category_id ?: product.primaryProductCategoryId;
if (categoryId) context.categoryId = categoryId;
catalogId = CatalogWorker.getCurrentCatalogId(request);
currentCatalogId = catalogId;
webSiteId = WebSiteWorker.getWebSiteId(request);
autoUserLogin = request.getSession().getAttribute("autoUserLogin");
featureTypes = [:];
featureOrder = [];

boolean isAlternativePacking = ProductWorker.isAlternativePacking(delegator, productId, null);;
boolean isMarketingPackage = false;

if(product){
    isMarketingPackage = EntityTypeUtil.hasParentType(delegator, "ProductType", "productTypeId", product.productTypeId, "parentTypeId", "MARKETING_PKG");
}
context.isMarketingPackage = (isMarketingPackage? "true": "false");


// Custom Functions
String buildNext(Map map, List order, String current, String prefix, Map featureTypes) {
    def ct = 0;
    def buf = new StringBuffer();
    buf.append("function listFT" + current + prefix + "() { ");
    buf.append("document.forms[\"addform\"].elements[\"FT" + current + "\"].options.length = 1;");
    buf.append("document.forms[\"addform\"].elements[\"FT" + current + "\"].options[0] = new Option(\"" + featureTypes[current] + "\",\"\",true,true);");
    map.each { key, value ->
        def optValue = null;

        if (order.indexOf(current) == (order.size()-1)) {
            optValue = value.iterator().next();
        } else {
            optValue = prefix + "_" + ct;
        }

        buf.append("document.forms[\"addform\"].elements[\"FT" + current + "\"].options[" + (ct + 1) + "] = new Option(\"" + key + "\",\"" + optValue + "\");");
        ct++;
    }
    buf.append(" }");
    if (order.indexOf(current) < (order.size()-1)) {
        ct = 0;
        map.each { key, value ->
            def nextOrder = order.get(order.indexOf(current)+1);
            def newPrefix = prefix + "_" + ct;
            buf.append(buildNext(value, order, nextOrder, newPrefix, featureTypes));
            ct++;
        }
    }
    return buf.toString();
}

/**
 * Creates a unique product cachekey
 * */
getProductCacheKey = {
    if (userLogin) {
        return productId+"::"+webSiteId+"::"+catalogId+"::"+categoryId+"::"+productStoreId+"::"+cart.getCurrency()+"::"+userLogin.partyId+"::"+delegator.getDelegatorName();
    } else {
        return productId+"::"+webSiteId+"::"+catalogId+"::"+categoryId+"::"+productStoreId+"::"+cart.getCurrency()+"::"+"_NA_"+"::"+delegator.getDelegatorName();
    }
}

String cacheKey = getProductCacheKey();
Map cachedValue = null;
if (useCache) {
    cachedValue = productCache.get(cacheKey);
    if (cachedValue != null) {
        context.product_id = cachedValue.product_id;
        context.mainDetailImageUrl = cachedValue.mainDetailImageUrl;
        context.categoryId = cachedValue.categoryId;
        context.category = cachedValue.category;
        context.previousProductId = cachedValue.previousProductId;
        context.nextProductId = cachedValue.nextProductId;
        context.productStoreId = cachedValue.productStoreId;
        context.productStore = cachedValue.productStore;
        context.productReviews = cachedValue.productReviews;
        context.averageRating = cachedValue.averageRating;
        context.numRatings = cachedValue.numRatings;
        context.daysToShip = cachedValue.daysToShip;
        context.daysToShip = cachedValue.daysToShip;
        context.disFeatureList = cachedValue.disFeatureList;
        context.sizeProductFeatureAndAppls = cachedValue.sizeProductFeatureAndAppls;
        context.selFeatureTypes = cachedValue.selFeatureTypes;
        context.selFeatureOrder = cachedValue.selFeatureOrder;
        context.selFeatureOrderFirst = cachedValue.selFeatureOrderFirst;
        context.mainProducts = cachedValue.mainProducts;
        context.downloadProductContentAndInfoList = cachedValue.downloadProductContentAndInfoList;
        context.productImageList = cachedValue.productImageList;
        context.startDate = cachedValue.startDate;
        context.productTags = cachedValue.productTags;
        context.alsoBoughtProducts = cachedValue.alsoBoughtProducts;
        context.obsoleteProducts = cachedValue.obsoleteProducts;
        context.crossSellProducts = cachedValue.crossSellProducts;
        context.upSellProducts = cachedValue.upSellProducts;
        context.obsolenscenseProducts = cachedValue.obsolenscenseProducts;
        context.accessoryProducts = cachedValue.accessoryProducts;

        context.virtualVariant = cachedValue.virtualVariant;
        context.variantTree = cachedValue.variantTree;
        context.variantTreeSize = cachedValue.variantTreeSize;
        context.unavailableVariants = cachedValue.unavailableVariants;
        context.featureLists = cachedValue.featureLists;
        context.variantPriceList = cachedValue.variantPriceList;
        context.virtualVariants = cachedValue.virtualVariants;
        context.virtualVariantPriceList = cachedValue.virtualVariantPriceList;
        context.variantProductInfoMap = cachedValue.variantProductInfoMap;
        context.unavailableVariantIds = cachedValue.unavailableVariantIds;
        context.variantSample = cachedValue.variantSample;
        context.variantSampleKeys = cachedValue.variantSampleKeys;
        context.variantSampleSize = cachedValue.variantSampleSize;
        context.featureSet = cachedValue.featureSet;
        context.featureTypes = cachedValue.featureTypes;
        context.featureOrder = cachedValue.featureOrder;
        context.featureOrderFirst = cachedValue.featureOrderFirst;
        context.minimumQuantity = cachedValue.minimumQuantity;
        context.productSurvey = cachedValue.productSurvey
    }
}

if (product) {
    // SCIPIO: 2019-10-04: moved here because access below causes crash
    // make the productContentWrapper
    productContentWrapper = new ProductContentWrapper(product, request);
    context.productContentWrapper = productContentWrapper;

    if (cart.isSalesOrder()) {
        // sales order: run the "calculateProductPrice" service
        priceContext = [product : product, prodCatalogId : catalogId,
                        currencyUomId : cart.getCurrency(), autoUserLogin : autoUserLogin];
        priceContext.webSiteId = webSiteId;
        priceContext.productStoreId = productStoreId;
        priceContext.checkIncludeVat = "Y";
        priceContext.agreementId = cart.getAgreementId();
        priceContext.getMinimumVariantPrice = true;
        priceContext.partyId = cart.getPartyId();  // IMPORTANT: must put this in, or price will be calculated for the CSR instead of the customer
        priceMap = runService('calculateProductPrice', priceContext);
        priceMap.currencyUomId = cart.getCurrency(); // SCIPIO: 2018-07-18: put the currency in this map so it is unambiguous
        context.priceMap = priceMap;
    } else {
        // purchase order: run the "calculatePurchasePrice" service
        priceContext = [product : product, currencyUomId : cart.getCurrency(),
                        partyId : cart.getPartyId(), userLogin : userLogin];
        priceMap = runService('calculatePurchasePrice', priceContext);
        priceMap.currencyUomId = cart.getCurrency(); // SCIPIO: 2018-07-18: put the currency in this map so it is unambiguous
        context.priceMap = priceMap;
    }
}

if(!cachedValue){
    // get the product detail information
    if (product) {
        productTypeId = product.productTypeId;

        // set this as a last viewed
        LAST_VIEWED_TO_KEEP = 10; // modify this to change the number of last viewed to keep
        // SCIPIO: Thread safety: 2018-11-28: Fixes below make the session attribute immutable and safer.
        // The synchronized block locks on the _previous_ list instance, and then changes the instance.
        lastViewedProducts = session.getAttribute("lastViewedProducts");
        synchronized(lastViewedProducts != null ? lastViewedProducts : UtilHttp.getSessionSyncObject(session)) {
            lastViewedProducts = session.getAttribute("lastViewedProducts"); // SCIPIO: Re-read because other thread changed it
            if (!lastViewedProducts) {
                lastViewedProducts = [];
                //session.setAttribute("lastViewedProducts", lastViewedProducts); // SCIPIO: Moved below
            } else {
                lastViewedProducts = new ArrayList(lastViewedProducts); // SCIPIO: Make local copy
            }
            lastViewedProducts.remove(productId);
            lastViewedProducts.add(0, productId);
            while (lastViewedProducts.size() > LAST_VIEWED_TO_KEEP) {
                lastViewedProducts.remove(lastViewedProducts.size() - 1);
            }
            session.setAttribute("lastViewedProducts", lastViewedProducts); // SCIPIO: Safe publish
        }

        // get the main detail image (virtual or single product)
        mainDetailImage = productContentWrapper.get("DETAIL_IMAGE_URL", "url");
        if (mainDetailImage) {
            mainDetailImageUrl = ContentUrlTag.getContentPrefix(request) + mainDetailImage;
            context.mainDetailImageUrl = mainDetailImageUrl.toString();
        }

        catNextPreviousResult = null;
        if (categoryId) {
            prevNextMap = [categoryId : categoryId, productId : productId];
            prevNextMap.orderByFields = context.orderByFields ?: ["sequenceNum", "productId"];
            catNextPreviousResult = runService('getPreviousNextProducts', prevNextMap);
            if (ServiceUtil.isError(catNextPreviousResult)) {
                request.setAttribute("errorMessageList", [ServiceUtil.getErrorMessage(catNextPreviousResult)]);
                return;
            }
            if (catNextPreviousResult && catNextPreviousResult.category) {
                context.category = catNextPreviousResult.category;
                context.previousProductId = catNextPreviousResult.previousProductId;
                context.nextProductId = catNextPreviousResult.nextProductId;
            }
        }

        /* SCIPIO: 2019-03: This is already done by ShoppingCartEvents.addToCart and this is the wrong groovy file for this;
         * the stashParameterMap call creates a memory leak
        // get a defined survey
        productSurvey = ProductStoreWorker.getProductSurveys(delegator, productStoreId, productId, "CART_ADD");
        if (productSurvey) {
            survey = EntityUtil.getFirst(productSurvey);
            origParamMapId = UtilHttp.stashParameterMap(request);
            surveyContext = ["_ORIG_PARAM_MAP_ID_" : origParamMapId];
            surveyPartyId = userLogin?.partyId;
            wrapper = new ProductStoreSurveyWrapper(survey, surveyPartyId, surveyContext);
            context.surveyWrapper = wrapper;
        }
        */

        // get the product review(s)
        // get all product review in case of Purchase Order.
        reviewByAnd = [:];
        reviewByAnd.statusId = "PRR_APPROVED";
        if (cart.isSalesOrder()) {
            reviewByAnd.productStoreId = productStoreId;
        }
        reviews = product.getRelated("ProductReview", reviewByAnd, ["-postedDateTime"], true);
        context.productReviews = reviews;
        // get the average rating
        if (reviews) {
            ratingReviews = EntityUtil.filterByAnd(reviews, [EntityCondition.makeCondition("productRating", EntityOperator.NOT_EQUAL, null)]);
            if (ratingReviews) {
                context.averageRating = ProductWorker.getAverageProductRating(product, reviews, productStoreId);
                context.numRatings = ratingReviews.size();
            }
        }

        // get the days to ship
        // if order is purchase then don't calculate available inventory for product.
        if (cart.isSalesOrder()) {
            facilityId = productStore.inventoryFacilityId;
            /*
            productFacility = delegator.findOne("ProductFacility", [productId : productId, facilityId : facilityId, true);
            context.daysToShip = productFacility?.daysToShip
            */

            resultOutput = runService('getInventoryAvailableByFacility', [productId : productId, facilityId : facilityId, useCache : false]);
            totalAvailableToPromise = resultOutput.availableToPromiseTotal;
            if (totalAvailableToPromise) {
                productFacility = from("ProductFacility").where("productId", productId, "facilityId", facilityId).cache(true).queryOne();
                context.daysToShip = productFacility?.daysToShip
            }
        } else {
            supplierProduct = from("SupplierProduct").where("productId", productId).orderBy("-availableFromDate").cache(true).queryFirst();
            if (supplierProduct?.standardLeadTimeDays) {
                standardLeadTimeDays = supplierProduct.standardLeadTimeDays;
                daysToShip = standardLeadTimeDays + 1;
                context.daysToShip = daysToShip;
            }
        }

        // get the product distinguishing features
        disFeatureMap = runService('getProductFeatures', [productId : productId, type : "DISTINGUISHING_FEAT"]);
        disFeatureList = disFeatureMap.productFeatures;
        context.disFeatureList = disFeatureList;

        // an example of getting features of a certain type to show
        sizeProductFeatureAndAppls = from("ProductFeatureAndAppl").where("productId", productId, "productFeatureTypeId", "SIZE").orderBy("sequenceNum", "defaultSequenceNum").queryList();
        context.sizeProductFeatureAndAppls = sizeProductFeatureAndAppls;

        // SCIPIO: always get selectable features, in case need (affects nothing else)
        if (true) {
            selFeatureMap = runService('getProductFeatureSet', [productId : productId, productFeatureApplTypeId : "SELECTABLE_FEATURE"]);
            selFeatureSet = selFeatureMap.featureSet;
            selFeatureTypes = [:];
            selFeatureOrder = [];
            selFeatureOrderFirst = null;
            if (selFeatureSet) {
                selFeatureOrder = new LinkedList(selFeatureSet);
                selFeatureOrder.each { featureKey ->
                    featureValue = from("ProductFeatureType").where("productFeatureTypeId", featureKey).cache(true).queryOne();
                    fValue = featureValue.get("description") ?: featureValue.productFeatureTypeId;
                    selFeatureTypes[featureKey] = fValue;
                }
            }
            context.selFeatureTypes = selFeatureTypes;
            context.selFeatureOrder = selFeatureOrder;
            if (selFeatureOrder) {
                selFeatureOrderFirst = selFeatureOrder[0];
            }
            context.selFeatureOrderFirst = selFeatureOrderFirst;
            //org.ofbiz.base.util.Debug.logInfo("Test: " + selFeatureTypes, module);
            //org.ofbiz.base.util.Debug.logInfo("Test: " + selFeatureOrder, module);
        }

        // get product variant for Box/Case/Each
        productVariants = [];
        mainProducts = [];
        if(isAlternativePacking){
            productVirtualVariants = from("ProductAssoc").where("productIdTo", product.productId , "productAssocTypeId", "ALTERNATIVE_PACKAGE").cache(true).queryList();
            if(productVirtualVariants){
                productVirtualVariants.each { virtualVariantKey ->
                    mainProductMap = [:];
                    mainProduct = virtualVariantKey.getRelatedOne("MainProduct", true);
                    quantityUom = mainProduct.getRelatedOne("QuantityUom", true);
                    mainProductMap.productId = mainProduct.productId;
                    mainProductMap.piecesIncluded = mainProduct.piecesIncluded;
                    if (quantityUom) { // SCIPIO: This could be missing
                        mainProductMap.uomDesc = quantityUom.description;
                    }
                    mainProducts.add(mainProductMap);
                }
            }
        }
        context.mainProducts = mainProducts;

        // get the DIGITAL_DOWNLOAD related Content records to show the contentName/description
        // SCIPIO: This should order by sequenceNum
        downloadProductContentAndInfoList = from("ProductContentAndInfo").where("productId", productId, "productContentTypeId", "DIGITAL_DOWNLOAD").orderBy("sequenceNum ASC").cache(true).queryList();
        context.downloadProductContentAndInfoList = downloadProductContentAndInfoList;

        // SCIPIO: 2.0.0: invocation now controlled through properties
        if (UtilProperties.getPropertyAsBoolean("serverstats", "stats.countProduct", false)) {
            // not the best to save info in an action, but this is probably the best place to count a view; it is done async
            dispatcher.runAsync("countProductView", [productId : productId, weight : new Long(1)], false);
        }

        //get product image from image management
        productImageList = [];
        productContentAndInfoImageManamentList = from("ProductContentAndInfo").where("productId", productId, "productContentTypeId", "IMAGE", "statusId", "IM_APPROVED", "drIsPublic", "Y").orderBy("sequenceNum").queryList();
        if(productContentAndInfoImageManamentList) {
            productContentAndInfoImageManamentList.each { productContentAndInfoImageManament ->
                contentAssocThumb = from("ContentAssoc").where("contentId", productContentAndInfoImageManament.contentId, "contentAssocTypeId", "IMAGE_THUMBNAIL").queryFirst();
                if(contentAssocThumb) {
                    imageContentThumb = from("Content").where("contentId", contentAssocThumb.contentIdTo).queryOne();
                    if(imageContentThumb) {
                        productImageThumb = from("ContentDataResourceView").where("contentId", imageContentThumb.contentId, "drDataResourceId", imageContentThumb.dataResourceId).queryOne();
                        productImageMap = [:];
                        productImageMap.productImageThumb = productImageThumb.drObjectInfo;
                        productImageMap.productImage = productContentAndInfoImageManament.drObjectInfo;
                        productImageList.add(productImageMap);
                    }
                }
            }
            context.productImageList = productImageList;
        }

        // get reservation start date for rental product
        if("ASSET_USAGE".equals(productTypeId) || "ASSET_USAGE_OUT_IN".equals(productTypeId)){
            context.startDate = UtilDateTime.addDaysToTimestamp(UtilDateTime.nowTimestamp(), 1).toString().substring(0,10); // should be tomorrow.
        }

        // get product tags
        productKeywords = from("ProductKeyword").where("productId": productId, "keywordTypeId" : "KWT_TAG", "statusId" : "KW_APPROVED").queryList();
        keywordMap = [:];
        if (productKeywords) {
            for (productKeyword in productKeywords) {
                productKeyWordCount = from("ProductKeyword").where("keyword", productKeyword.keyword, "keywordTypeId", "KWT_TAG", "statusId", "KW_APPROVED").queryCount();
                keywordMap.put(productKeyword.keyword,productKeyWordCount);
            }
            context.productTags = keywordMap;
        }
    }

    // get product associations
    alsoBoughtProducts = runService('getAssociatedProducts', [productId : productId, type : "ALSO_BOUGHT", checkViewAllow : true, prodCatalogId : currentCatalogId, bidirectional : false, sortDescending : true]);
    context.alsoBoughtProducts = alsoBoughtProducts.assocProducts;

    obsoleteProducts = runService('getAssociatedProducts', [productId : productId, type : "PRODUCT_OBSOLESCENCE", checkViewAllow : true, prodCatalogId : currentCatalogId]);
    context.obsoleteProducts = obsoleteProducts.assocProducts;

    crossSellProducts = runService('getAssociatedProducts', [productId : productId, type : "PRODUCT_COMPLEMENT", checkViewAllow : true, prodCatalogId : currentCatalogId]);
    context.crossSellProducts = crossSellProducts.assocProducts;

    upSellProducts = runService('getAssociatedProducts', [productId : productId, type : "PRODUCT_UPGRADE", checkViewAllow : true, prodCatalogId : currentCatalogId]);
    context.upSellProducts = upSellProducts.assocProducts;

    obsolenscenseProducts = runService('getAssociatedProducts', [productIdTo : productId, type : "PRODUCT_OBSOLESCENCE", checkViewAllow : true, prodCatalogId : currentCatalogId]);
    context.obsolenscenseProducts = obsolenscenseProducts.assocProducts;

    accessoryProducts = runService('getAssociatedProducts', [productId : productId, type : "PRODUCT_ACCESSORY", checkViewAllow : true, prodCatalogId : currentCatalogId]);
    context.accessoryProducts = accessoryProducts.assocProducts;


    // Special Variant Code
    if ("Y".equals(product.isVirtual)) {
        if ("VV_FEATURETREE".equals(ProductWorker.getProductVirtualVariantMethod(delegator, productId))) {
            context.featureLists = ProductWorker.getProductFeatures(product, context.locale);
        } else {
            featureMap = runService('getProductFeatureSet', [productId : productId]);
            featureSet = featureMap.featureSet;
            if (featureSet) {
                //if order is purchase then don't calculate available inventory for product.
                if (cart.isPurchaseOrder()) {
                    variantTreeMap = runService('getProductVariantTree', [productId : productId, featureOrder : featureSet, checkInventory: false, unavailableInTree : context.unavailableInVariantTree]); // SCIPIO: unavailableInTree
                } else {
                    variantTreeMap = runService('getProductVariantTree', [productId : productId, featureOrder : featureSet, productStoreId : productStoreId, unavailableInTree : context.unavailableInVariantTree]); // SCIPIO: unavailableInTree
                }
                variantTree = variantTreeMap.variantTree;
                imageMap = variantTreeMap.variantSample;
                virtualVariant = variantTreeMap.virtualVariant;
                context.virtualVariant = virtualVariant;
                if (variantTree) {
                    context.variantTree = variantTree;
                    context.variantTreeSize = variantTree.size();
                }
                unavailableVariants = variantTreeMap.unavailableVariants;
                if (unavailableVariants) {
                    context.unavailableVariants = unavailableVariants;
                    // SCIPIO
                    unavailableVariantIds = [];
                    for(uvProd in unavailableVariants) {
                        unavailableVariantIds.add(uvProd.productId);
                    }
                    context.unavailableVariantIds = unavailableVariantIds;
                }
                if (imageMap) {
                    context.variantSample = imageMap;
                    context.variantSampleKeys = imageMap.keySet();
                    context.variantSampleSize = imageMap.size();
                }
                context.featureSet = featureSet;

                if (variantTree) {
                    featureOrder = new LinkedList(featureSet);
                    featureOrder.each { featureKey ->
                        featureValue = from("ProductFeatureType").where("productFeatureTypeId", featureKey).cache(true).queryOne();
                        fValue = featureValue.get("description", context.locale) ?: featureValue.productFeatureTypeId;
                        featureTypes[featureKey] = fValue;
                    }
                }
                context.featureTypes = featureTypes;
                context.featureOrder = featureOrder;
                if (featureOrder) {
                    context.featureOrderFirst = featureOrder[0];
                }

                // SCIPIO: The original OFBiz code was removed here.
                if (variantTree && imageMap) {
                    // make a list of variant sku with requireAmount
                    variantsRes = runService('getAssociatedProducts', [productId : productId, type : "PRODUCT_VARIANT", checkViewAllow : true, prodCatalogId : currentCatalogId]);
                    variants = variantsRes.assocProducts;
                    variantPriceList = [];
                    variantProductInfoMap = [:]; // SCIPIO: Maps productId to a more detailed product info map, including requireAmount flag and price
                    if (variants) {
                        if (productStore) {
                            localeString = productStore.defaultLocaleString;
                            if (localeString) {
                                locale = UtilMisc.parseLocale(localeString);
                            }
                        }
                        variants.each { variantAssoc ->
                            variant = variantAssoc.getRelatedOne("AssocProduct", false);
                            // Get the price for each variant. Reuse the priceContext already setup for virtual product above and replace the product
                            priceContext.product = variant;
                            if (cart.isSalesOrder()) {
                                // sales order: run the "calculateProductPrice" service
                                variantPriceMap = runService('calculateProductPrice', priceContext);
                                BigDecimal calculatedPrice = (BigDecimal) variantPriceMap.get("price");
                                // Get the minimum quantity for variants if MINIMUM_ORDER_PRICE is set for variants.
                                variantPriceMap.put("minimumQuantity", ShoppingCart.getMinimumOrderQuantity(delegator, calculatedPrice, variant.get("productId")));
                                Iterator treeMapIter = variantTree.entrySet().iterator();
                                while (treeMapIter.hasNext()) {
                                    Map.Entry entry = treeMapIter.next();
                                    if (entry.getValue() instanceof Map) {
                                        Iterator entryIter = entry.getValue().entrySet().iterator();
                                        while (entryIter.hasNext()) {
                                            Map.Entry innerentry = entryIter.next();
                                            if (variant.get("productId").equals(innerentry.getValue().get(0))) {
                                                variantPriceMap.put("variantName", innerentry.getKey());
                                                variantPriceMap.put("secondVariantName", entry.getKey());
                                                break;
                                            }
                                        }
                                    } else if (UtilValidate.isNotEmpty(entry.getValue())) {
                                        if (variant.get("productId").equals(entry.getValue().get(0))) {
                                            variantPriceMap.put("variantName", entry.getKey());
                                            break;
                                        }
                                    }
                                }
                                variantPriceList.add(variantPriceMap);
                            } else {
                                variantPriceMap = runService('calculatePurchasePrice', priceContext);
                            }

                            // SCIPIO: Save requireAmount flag and base price for variant
                            variantProductInfo = [:];
                            variantProductInfo.putAll(variant);
                            variantProductInfo.requireAmount = (variant.requireAmount ?: "N");
                            if (variantPriceMap) {
                                if (variantPriceMap.basePrice) {
                                    variantProductInfo.price = variantPriceMap.basePrice;
                                    variantProductInfo.priceFormatted = UtilFormatOut.formatCurrency(variantPriceMap.basePrice, variantPriceMap.currencyUsed, UtilHttp.getLocale(request));
                                }
                                if (variantPriceMap.defaultPrice) {
                                    variantProductInfo.defaultPrice = variantPriceMap.defaultPrice;
                                    variantProductInfo.defaultPriceFormatted = UtilFormatOut.formatCurrency(variantPriceMap.defaultPrice, variantPriceMap.currencyUsed, UtilHttp.getLocale(request));
                                }
                                if (variantPriceMap.listPrice) {
                                    variantProductInfo.listPrice = variantPriceMap.listPrice;
                                    variantProductInfo.listPriceFormatted = UtilFormatOut.formatCurrency(variantPriceMap.listPrice, variantPriceMap.currencyUsed, UtilHttp.getLocale(request));
                                }
                                if (UtilValidate.isNotEmpty(variantPriceMap.orderItemPriceInfos)) {
                                    for (itemPriceInfo in variantPriceMap.orderItemPriceInfos) {
                                        if (itemPriceInfo.modifyAmount) {
                                            variantProductInfo.modifyAmount = itemPriceInfo.modifyAmount;
                                            variantProductInfo.modifyAmountFormatted = UtilFormatOut.formatCurrency(itemPriceInfo.modifyAmount, variantPriceMap.currencyUsed, UtilHttp.getLocale(request));                                            
                                            
                                            if (variantPriceMap.listPrice) {
                                                variantProductInfo.oldPrice = variantPriceMap.listPrice;
                                                variantProductInfo.oldPriceFormatted = UtilFormatOut.formatCurrency(variantPriceMap.listPrice, variantPriceMap.currencyUsed, UtilHttp.getLocale(request));;
                                            } else if (variantProductInfo.defaultPrice) {
                                                variantProductInfo.oldPrice = variantPriceMap.defaultPrice;
                                                variantProductInfo.oldPriceFormatted = UtilFormatOut.formatCurrency(variantPriceMap.defaultPrice, variantPriceMap.currencyUsed, UtilHttp.getLocale(request));;;
                                            }
                                            if (variantProductInfo.oldPrice) {
                                                priceSaved = variantProductInfo.oldPrice - variantProductInfo.price;
                                                variantProductInfo.priceSaved = priceSaved;
                                                percentSaved = (priceSaved / variantProductInfo.oldPrice);
                                                variantProductInfo.percentSaved = UtilFormatOut.formatPercentage(percentSaved);
                                            }
                                        }
                                        /*for (priceInfo in itemPriceInfo.keySet()) {
                                            Debug.log("[" + priceInfo + "]: " + itemPriceInfo.get(priceInfo));
                                        }*/
                                    }
                                }
                            }
                            variantProductInfoMap[variant.productId] = variantProductInfo;

                            // make a list of virtual variants sku with requireAmount
                            virtualVariantsRes = runService('getAssociatedProducts', [productIdTo : variant.productId, type : "ALTERNATIVE_PACKAGE", checkViewAllow : true, prodCatalogId : currentCatalogId]);
                            virtualVariants = virtualVariantsRes.assocProducts;

                            if(virtualVariants){
                                virtualVariants.each { virtualAssoc ->
                                    virtual = virtualAssoc.getRelatedOne("MainProduct", false);
                                    // Get price from a virtual product
                                    priceContext.product = virtual;
                                    if (cart.isSalesOrder()) {
                                        // sales order: run the "calculateProductPrice" service
                                        virtualPriceMap = runService('calculateProductPrice', priceContext);
                                        BigDecimal calculatedPrice = (BigDecimal)virtualPriceMap.get("price");
                                        // Get the minimum quantity for variants if MINIMUM_ORDER_PRICE is set for variants.
                                        virtualPriceMap.put("minimumQuantity", ShoppingCart.getMinimumOrderQuantity(delegator, calculatedPrice, virtual.get("productId")));
                                        Iterator treeMapIter = variantTree.entrySet().iterator();
                                        while (treeMapIter.hasNext()) {
                                            Map.Entry entry = treeMapIter.next();
                                            if (entry.getValue() instanceof  Map) {
                                                Iterator entryIter = entry.getValue().entrySet().iterator();
                                                while (entryIter.hasNext()) {
                                                    Map.Entry innerentry = entryIter.next();
                                                    if (virtual.get("productId").equals(innerentry.getValue().get(0))) {
                                                        virtualPriceMap.put("variantName", innerentry.getKey());
                                                        virtualPriceMap.put("secondVariantName", entry.getKey());
                                                        break;
                                                    }
                                                }
                                            } else if (UtilValidate.isNotEmpty(entry.getValue())) {
                                                if (virtual.get("productId").equals(entry.getValue().get(0))) {
                                                    virtualPriceMap.put("variantName", entry.getKey());
                                                    break;
                                                }
                                            }
                                        }
                                        variantPriceList.add(virtualPriceMap);
                                        // SCIPIO: Save product info for virtual
                                        variantProductInfo = [:];
                                        variantProductInfo.putAll(virtual);
                                        //variantProductInfo.requireAmount = (virtual.requireAmount ?: "N"); // SCIPIO: This doesn't apply for virtuals
                                        variantProductInfo.price = variantPriceMap.basePrice;
                                        variantProductInfo.priceFormatted = UtilFormatOut.formatCurrency(variantPriceMap.basePrice, variantPriceMap.currencyUsed, UtilHttp.getLocale(request));
                                        variantProductInfoMap[virtual.productId] = variantProductInfo;
                                        Debug.log("[" + virtual.productId + "]: " + variantProductInfo);
                                    } else {
                                        virtualPriceMap = runService('calculatePurchasePrice', priceContext);
                                        // SCIPIO: Save product info for virtual
                                        variantProductInfo = [:];
                                        variantProductInfo.putAll(virtual);
                                        //variantProductInfo.requireAmount = (virtual.requireAmount ?: "N"); // SCIPIO: This doesn't apply for virtuals
                                        variantProductInfo.price = variantPriceMap.price;
                                        variantProductInfo.priceFormatted = UtilFormatOut.formatCurrency(variantPriceMap.basePrice, variantPriceMap.currencyUsed, UtilHttp.getLocale(request));
                                        variantProductInfoMap[virtual.productId] = variantProductInfo;
                                    }
                                }

                            }
                        }
                    }
                    context.variantPriceList = variantPriceList;
                    context.virtualVariants = virtualVariants;
                    context.variantProductInfoMap = variantProductInfoMap; // SCIPIO: Save map
                }
            }
        }
    } else {
        context.minimumQuantity= ShoppingCart.getMinimumOrderQuantity(delegator, priceMap.price, productId);
        if(isAlternativePacking){
            // get alternative product price when product doesn't have any feature
            // make a list of variant sku with requireAmount
            virtualVariantsRes = runService('getAssociatedProducts', [productIdTo : productId, type : "ALTERNATIVE_PACKAGE", checkViewAllow : true, prodCatalogId : categoryId]);
            virtualVariants = virtualVariantsRes.assocProducts;
            // Format to apply the currency code to the variant price in the javascript
            if (productStore) {
                localeString = productStore.defaultLocaleString;
                if (localeString) {
                    locale = UtilMisc.parseLocale(localeString);
                }
            }
            virtualVariantPriceList = [];

            if(virtualVariants){
                // Create the javascript to return the price for each variant
                virtualVariants.each { virtualAssoc ->
                    virtual = virtualAssoc.getRelatedOne("MainProduct", false);
                    // Get price from a virtual product
                    priceContext.product = virtual;
                    if (cart.isSalesOrder()) {
                        // sales order: run the "calculateProductPrice" service
                        virtualPriceMap = runService('calculateProductPrice', priceContext);
                        BigDecimal calculatedPrice = (BigDecimal)virtualPriceMap.get("price");
                        // Get the minimum quantity for variants if MINIMUM_ORDER_PRICE is set for variants.
                        virtualVariantPriceList.add(virtualPriceMap);
                    } else {
                        virtualPriceMap = runService('calculatePurchasePrice', priceContext);
                    }
                }
                context.virtualVariantPriceList = virtualVariantPriceList;
                context.virtualVariants = virtualVariants;
            }
        }
    }

    // cache
    prodMap = [:];
    prodMap.product_id = context.product_id;
    prodMap.mainDetailImageUrl = context.mainDetailImageUrl;
    prodMap.categoryId = context.categoryId;
    prodMap.category = context.category;
    prodMap.previousProductId = context.previousProductId;
    prodMap.nextProductId = context.nextProductId;
    prodMap.productStoreId = context.productStoreId;
    prodMap.productStore = context.productStore;
    prodMap.productReviews = context.productReviews;
    prodMap.averageRating = context.averageRating;
    prodMap.numRatings = context.numRatings;
    prodMap.daysToShip = context.daysToShip;
    prodMap.daysToShip = context.daysToShip;
    prodMap.disFeatureList = context.disFeatureList;
    prodMap.sizeProductFeatureAndAppls = context.sizeProductFeatureAndAppls;
    prodMap.selFeatureTypes = context.selFeatureTypes;
    prodMap.selFeatureOrder = context.selFeatureOrder;
    prodMap.selFeatureOrderFirst = context.selFeatureOrderFirst;
    prodMap.mainProducts = context.mainProducts;
    prodMap.downloadProductContentAndInfoList = context.downloadProductContentAndInfoList;
    prodMap.productImageList = context.productImageList;
    prodMap.startDate = context.startDate;
    prodMap.productTags = context.productTags;
    prodMap.alsoBoughtProducts = context.alsoBoughtProducts;
    prodMap.obsoleteProducts = context.obsoleteProducts;
    prodMap.crossSellProducts = context.crossSellProducts;
    prodMap.upSellProducts = context.upSellProducts;
    prodMap.obsolenscenseProducts = context.obsolenscenseProducts;
    prodMap.accessoryProducts = context.accessoryProducts;

    prodMap.virtualVariant = context.virtualVariant;
    prodMap.variantTree = context.variantTree;
    prodMap.variantTreeSize = context.variantTreeSize;
    prodMap.unavailableVariants = context.unavailableVariants;
    prodMap.featureLists = context.featureLists;
    prodMap.variantPriceList = context.variantPriceList;
    prodMap.virtualVariants = context.virtualVariants;
    prodMap.virtualVariantPriceList = context.virtualVariantPriceList;
    prodMap.variantProductInfoMap = context.variantProductInfoMap;
    prodMap.unavailableVariantIds = context.unavailableVariantIds;
    prodMap.variantSample = context.variantSample;
    prodMap.variantSampleKeys = context.variantSampleKeys;
    prodMap.variantSampleSize = context.variantSampleSize;
    prodMap.featureSet = context.featureSet;
    prodMap.featureTypes = context.featureTypes;
    prodMap.featureOrder = context.featureOrder;
    prodMap.featureOrderFirst = context.featureOrderFirst;
    prodMap.minimumQuantity = context.minimumQuantity;
    productCache.put(cacheKey,prodMap);
}

// non-cacheable code
if(System.currentTimeMillis() - lastTime > 30){
    Debug.logWarning("Took "+(System.currentTimeMillis() - lastTime)+"ms ","OriginalProductDetail.groovy");
}
lastTime = System.currentTimeMillis();
if(product){


    if(!context.isStockOverride){ //allows the cpu heavy stock calculation to be overridden when used from another script
        availableInventory = 0.0;

        // if the product is a MARKETING_PKG_AUTO/PICK, then also get the quantity which can be produced from components
        if (isMarketingPackage) {
            resultOutput = runService('getMktgPackagesAvailable', [productId : productId, useInventoryCache:true]); // SCIPIO: cache
            availableInventory = resultOutput.availableToPromiseTotal;
        } else {
            //get last inventory count from product facility for the product
            facilities = from("ProductFacility").where("productId", product.productId).queryList();
            if(facilities) {
                facilities.each { facility ->
                    lastInventoryCount = facility.lastInventoryCount;
                    if (lastInventoryCount != null) {
                        availableInventory += lastInventoryCount;
                    }
                }
            }
        }
        context.availableInventory = availableInventory;
    }

}
if(System.currentTimeMillis() - lastTime > 30){
    Debug.logWarning("Took "+(System.currentTimeMillis() - lastTime)+"ms to calculate the available stock","OriginalProductDetail.groovy");
}


// SCIPIO: Decide the next possible reserv start date (next day)
nextDayTimestamp = UtilDateTime.getDayStart(nowTimestamp, 1, timeZone, locale);
context.nextDayTimestamp = nextDayTimestamp;
earliestReservStartDate = nextDayTimestamp;
context.earliestReservStartDate = earliestReservStartDate;