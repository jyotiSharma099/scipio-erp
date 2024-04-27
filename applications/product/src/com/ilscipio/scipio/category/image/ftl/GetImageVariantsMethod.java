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
package com.ilscipio.scipio.category.image.ftl;

import com.ilscipio.scipio.category.image.CategoryImageVariants;
import com.ilscipio.scipio.ce.webapp.ftl.CommonFtlUtil;
import com.ilscipio.scipio.ce.webapp.ftl.context.ContextFtlUtil;
import com.ilscipio.scipio.ce.webapp.ftl.context.TransformUtil;
import com.ilscipio.scipio.ce.webapp.ftl.lang.LangFtlUtil;
import com.ilscipio.scipio.content.image.ContentImageVariants;
import freemarker.core.Environment;
import freemarker.template.TemplateHashModelEx;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateScalarModel;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.service.LocalDispatcher;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SCIPIO: GetImageVariantsMethod.
 */
public class GetImageVariantsMethod implements TemplateMethodModelEx {

    //private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    /*
     * @see freemarker.template.TemplateMethodModel#exec(java.util.List)
     */
    @Override
    public Object exec(@SuppressWarnings("rawtypes") List args) throws TemplateModelException {
        return exec(UtilGenerics.cast(args), CommonFtlUtil.getCurrentEnvironment());
    }

    protected Object exec(@SuppressWarnings("rawtypes") List<TemplateModel> args, Environment env) throws TemplateModelException {
        if (args == null || args.size() < 1 || args.size() > 4) {
            throw new TemplateModelException("Invalid number of arguments (expected: 1-4)");
        }
        TemplateModel firstArg = args.get(0);
        Map<String, TemplateModel> mapArgs = Collections.emptyMap();
        Map<String, Object> unwrappedMapArgs = Collections.emptyMap();

        String type = null;
        String contentId = null;
        String productCategoryId = null;
        String productCatContentTypeId = null;
        boolean useCache = true;

        if (firstArg instanceof TemplateScalarModel) {
            type = TransformUtil.getStringArg(firstArg);
            if ("content".equals(type)) {
                contentId = TransformUtil.getStringArg(args, 1);
                useCache = TransformUtil.getBooleanArg(args, 2, true);
            } else if ("category".equals(type)) {
                productCategoryId = TransformUtil.getStringArg(args, 1);
                productCatContentTypeId = TransformUtil.getStringArg(args, 2);
                useCache = TransformUtil.getBooleanArg(args, 3, true);
            } else {
                throw new TemplateModelException("Invalid image variants type. Supported: content, product");
            }
        } else if (firstArg instanceof TemplateHashModelEx) {
            mapArgs = LangFtlUtil.adaptAsMap((TemplateHashModelEx) args.get(0));
            unwrappedMapArgs = UtilGenerics.cast(LangFtlUtil.unwrapPermissive((TemplateHashModelEx) args.get(0)));
            //type = TransformUtil.getStringArg(mapArgs, "type");
            contentId = TransformUtil.getStringArg(mapArgs, "contentId");
            productCategoryId = TransformUtil.getStringArg(mapArgs, "productCategoryId");
            productCatContentTypeId = TransformUtil.getStringArg(mapArgs, "prodCatContentTypeId");
            useCache = TransformUtil.getBooleanArg(mapArgs, "useCache", true);
        } else {
            throw new TemplateModelException("First arg not instance of string or hash");
        }

        Delegator delegator = ContextFtlUtil.getDelegator(env);
        LocalDispatcher dispatcher = ContextFtlUtil.getDispatcher(env);
        Locale locale = TransformUtil.getOfbizLocaleArgOrCurrent(mapArgs, "locale", env);

        if (UtilValidate.isNotEmpty(productCategoryId)) {
            if (UtilValidate.isEmpty(productCatContentTypeId)) { // SCIPIO: 3.0.0: Default to ORIGINAL_IMAGE_URL
                productCatContentTypeId = "ORIGINAL_IMAGE_URL";
            }
            return CategoryImageVariants.from(productCategoryId, productCatContentTypeId, true, delegator, dispatcher, locale, useCache, unwrappedMapArgs);
        } else if (UtilValidate.isNotEmpty(contentId)) {
            boolean useUtilCache = TransformUtil.getBooleanArg(args, "useUtilCache", 2, true);
            return ContentImageVariants.from(contentId, delegator, dispatcher, locale, useUtilCache, unwrappedMapArgs);
        } else {
            // Behave nicely to simplify usage
            //throw new TemplateModelException("Invalid parameters: missing contentId or (productId + productContentTypeId)");
            return null;
        }
    }
}
