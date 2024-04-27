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
package org.ofbiz.service.eca;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.ofbiz.base.GeneralConfig;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.ObjectType;
import org.ofbiz.base.util.StringUtil;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.UtilXml;
import org.ofbiz.base.util.collections.FlexibleMapAccessor;
import org.ofbiz.base.util.string.FlexibleStringExpander;
import org.ofbiz.entity.util.EntityUtilProperties;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;
import org.w3c.dom.Element;

/**
 * ServiceEcaCondition
 * SCIPIO: Major refactor to allow better conditions.
 */
@SuppressWarnings("serial")
public abstract class ServiceEcaCondition implements java.io.Serializable {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    public static ServiceEcaCondition getCondition(Element element) { // SCIPIO: moved from EntityEcaRule construction
        if ("condition".equals(element.getNodeName())) {
            return new SingleServiceEcaCondition(element, true, false);
        } else if ("condition-field".equals(element.getNodeName())) {
            return new SingleServiceEcaCondition(element, false, false);
        } else if ("condition-service".equals(element.getNodeName())) {
            return new SingleServiceEcaCondition(element, false, true);
        } else if ("condition-property".equals(element.getNodeName())) {
            return new SingleServiceEcaCondition(element, true, false, true);
        } else if ("condition-property-field".equals(element.getNodeName())) {
            return new SingleServiceEcaCondition(element, false, false, true);
        } else if ("and".equals(element.getNodeName())) {
            return new AndServiceEcaCondition(element);
        } else if ("xor".equals(element.getNodeName())) {
            return new XorServiceEcaCondition(element);
        } else if ("or".equals(element.getNodeName())) {
            return new OrServiceEcaCondition(element);
        } else if ("not".equals(element.getNodeName())) {
            return new NotServiceEcaCondition(element);
        }
        return null;
    }

    public abstract String getShortDisplayDescription(boolean moreDetail);

    public abstract Boolean eval(String serviceName, DispatchContext dctx, Map<String, Object> context, String scope) throws GenericServiceException; // SCIPIO: scope

    public Boolean eval(String serviceName, DispatchContext dctx, Map<String, Object> context) throws GenericServiceException { // SCIPIO: scope
        return eval(serviceName, dctx, context, null);
    }

    public static abstract class GroupServiceEcaCondition extends ServiceEcaCondition {
        protected final List<ServiceEcaCondition> conditions;

        protected GroupServiceEcaCondition(Element element) {
            List<? extends Element> childElementList = UtilXml.childElementList(element);
            List<ServiceEcaCondition> conditions = new ArrayList<>(childElementList.size());
            for (Element childElement : UtilXml.childElementList(element)) {
                conditions.add(getCondition(childElement));
            }
            if (UtilValidate.isEmpty(conditions)) {
                throw new IllegalArgumentException("Missing condition for service condition group operator: " + element.getNodeName());
            }
            this.conditions = conditions;
        }

        protected abstract String getOperator();

        public String getShortDisplayDescription(boolean moreDetail) {
            List<String> descriptions = new ArrayList<>();
            for(ServiceEcaCondition condition : conditions) {
                descriptions.add(condition.getShortDisplayDescription(moreDetail));
            }
            return "[" + getOperator() + ":" + descriptions + "]";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GroupServiceEcaCondition that = (GroupServiceEcaCondition) o;
            return conditions.equals(that.conditions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(conditions);
        }

        @Override
        public String toString() {
            return "[" + getOperator() + ":" + conditions + "]";
        }
    }

    public static class AndServiceEcaCondition extends GroupServiceEcaCondition {
        protected AndServiceEcaCondition(Element element) {
            super(element);
        }

        @Override
        protected String getOperator() {
            return "and";
        }

        @Override
        public Boolean eval(String serviceName, DispatchContext dctx, Map<String, Object> context, String scope) throws GenericServiceException {
            Boolean result = null; // SCIPIO: if all null, returns null; otherwise AND non-nulls
            for (ServiceEcaCondition ec: conditions) {
                Boolean subResult = ec.eval(serviceName, dctx, context, scope);
                if (subResult != null) {
                    if (!subResult) {
                        return false;
                    } else {
                        result = true;
                    }
                }
            }
            return result;
        }
    }

    public static class XorServiceEcaCondition extends GroupServiceEcaCondition {
        protected XorServiceEcaCondition(Element element) {
            super(element);
        }

        @Override
        protected String getOperator() {
            return "xor";
        }

        @Override
        public Boolean eval(String serviceName, DispatchContext dctx, Map<String, Object> context, String scope) throws GenericServiceException {
            Boolean foundOneTrue = null; // SCIPIO: if all null, returns null; otherwise XOR non-nulls
            for (ServiceEcaCondition ec : conditions) {
                Boolean subResult = ec.eval(serviceName, dctx, context, scope);
                if (subResult != null) {
                    if (subResult) {
                        if (foundOneTrue != null && foundOneTrue) {
                            // now found two true, so return false
                            return false;
                        }
                        foundOneTrue = true;
                    } else if (foundOneTrue == null) {
                        foundOneTrue = false;
                    }
                }
            }
            return foundOneTrue;
        }
    }

    public static class OrServiceEcaCondition extends GroupServiceEcaCondition {
        protected OrServiceEcaCondition(Element element) {
            super(element);
        }

        @Override
        protected String getOperator() {
            return "or";
        }

        @Override
        public Boolean eval(String serviceName, DispatchContext dctx, Map<String, Object> context, String scope) throws GenericServiceException {
            Boolean result = null; // SCIPIO: if all null, returns null; otherwise OR non-nulls
            for (ServiceEcaCondition ec: conditions) {
                Boolean subResult = ec.eval(serviceName, dctx, context, scope);
                if (subResult != null) {
                    if (subResult) {
                        return true;
                    } else {
                        result = false;
                    }
                }
            }
            return result;
        }
    }

    public static class NotServiceEcaCondition extends GroupServiceEcaCondition {
        protected final ServiceEcaCondition condition;
        protected NotServiceEcaCondition(Element element) {
            super(element);
            this.condition = conditions.get(0);
        }

        @Override
        protected String getOperator() {
            return "not";
        }

        @Override
        public Boolean eval(String serviceName, DispatchContext dctx, Map<String, Object> context, String scope) throws GenericServiceException {
            Boolean subResult = condition.eval(serviceName, dctx, context, scope); // SCIPIO: null
            return (subResult != null) ? !subResult : null;
        }
    }
    
    public static class SingleServiceEcaCondition extends ServiceEcaCondition {
        protected String conditionService = null;
        protected String lhsValueName = null;
        protected FlexibleMapAccessor<Object> lhsValueNameExdr = null; // SCIPIO: 3.0.0: Added
        protected String rhsValue = null;
        protected FlexibleMapAccessor<Object> rhsValueNameExdr = null; // SCIPIO: 3.0.0: Added
        protected FlexibleStringExpander rhsValueExdr = null; // SCIPIO: 3.0.0: Added
        protected String lhsMapName = null;
        protected String rhsMapName = null;
        protected String operator = null;
        protected String compareType = null;
        protected String format = null;
        protected boolean isValue = false;
        protected boolean isService = false;
        protected boolean property = false; // SCIPIO
        protected String propertyResource = null;
        protected Set<String> scopes = null; // SCIPIO
        protected String scopeString = null; // SCIPIO

        public SingleServiceEcaCondition(Element condition, boolean isValue, boolean isService, boolean property) { // SCIPIO: added property
            if (isService) {
                this.isService = isService;
                this.conditionService = condition.getAttribute("service-name");
            } else {
                String lhsValueName = condition.getAttribute("field"); // SCIPIO: 3.0.0: Added better alias
                if (UtilValidate.isEmpty(lhsValueName)) {
                    lhsValueName = condition.getAttribute("field-name");
                }
                this.lhsValueName = lhsValueName;
                if (lhsValueName.contains(FlexibleStringExpander.openBracket)) {
                    this.lhsValueNameExdr = FlexibleMapAccessor.getInstance(lhsValueName);
                }
                this.lhsMapName = condition.getAttribute("map-name");

                // SCIPIO: 3.0.0: This seems to be pointless; just use present of value vs to-field-name to determine
                //this.isValue = isValue;
                //if (isValue) {
                //    this.rhsValueName = condition.getAttribute("value");
                //    this.rhsMapName = null;
                //} else {
                //    this.rhsValueName = condition.getAttribute("to-field-name");
                //    this.rhsMapName = condition.getAttribute("to-map-name");
                //}

                String rhsValue = condition.getAttribute("to-field-name");
                if (UtilValidate.isNotEmpty(rhsValue)) {
                    isValue = false;
                    if (rhsValue.contains(FlexibleStringExpander.openBracket)) {
                        this.rhsValueNameExdr = FlexibleMapAccessor.getInstance(this.rhsValue);
                    }
                } else {
                    isValue = true;
                    rhsValue = condition.getAttribute("value");
                    if (rhsValue.contains(FlexibleStringExpander.openBracket)) {
                        this.rhsValueExdr = FlexibleStringExpander.getInstance(this.rhsValue);
                    }
                }
                this.isValue = isValue;
                this.rhsValue = rhsValue;
                this.rhsMapName = condition.getAttribute("to-map-name");

                this.operator = condition.getAttribute("operator");
                this.compareType = condition.getAttribute("type");
                this.format = condition.getAttribute("format");
                this.property = property; // SCIPIO
                this.propertyResource = condition.getAttribute("resource");
                if (property) {
                    this.lhsValueName = condition.getAttribute("property-name");
                    int splitIndex = this.lhsValueName.indexOf('#');
                    if (this.propertyResource.isEmpty() && splitIndex >= 1) {
                        this.propertyResource = this.lhsValueName.substring(0, splitIndex);
                        this.lhsValueName = this.lhsValueName.substring(splitIndex + 1);
                    }
                    if (this.propertyResource.isEmpty()) {
                        this.propertyResource = null;
                    }
                }
            }
            this.scopeString = UtilValidate.nullIfEmpty(condition.getAttribute("scope"));
            if (this.scopeString != null) {
                scopes = Collections.unmodifiableSet(StringUtil.splitNames(new HashSet<>(), this.scopeString));
            }
        }

        public SingleServiceEcaCondition(Element condition, boolean isValue, boolean isService) { // SCIPIO: added property
            this(condition, isValue, isService, false);
        }

        @Override
        public String getShortDisplayDescription(boolean moreDetail) {
            StringBuilder buf = new StringBuilder();
            if (isService) {
                buf.append("[").append(conditionService).append("]");
            } else {
                buf.append("[");
                if (UtilValidate.isNotEmpty(lhsMapName)) buf.append(lhsMapName).append(".");
                buf.append(lhsValueName);
                buf.append(":").append(operator).append(":");
                if (UtilValidate.isNotEmpty(rhsMapName)) buf.append(rhsMapName).append(".");
                buf.append(rhsValue);

                if (moreDetail) {
                    if (UtilValidate.isNotEmpty(compareType)) {
                        buf.append("-");
                        buf.append(compareType);
                    }
                    if (UtilValidate.isNotEmpty(format)) {
                        buf.append(";");
                        buf.append(format);
                    }
                }

                buf.append("]");
            }
            return buf.toString();
        }

        @Override
        public Boolean eval(String serviceName, DispatchContext dctx, Map<String, Object> context, String scope) throws GenericServiceException {
            if (scope == null || "run".equals(scope)) { // SCIPIO
                if (scopes != null && !scopes.contains("run")) {
                    return null; // null means not applicable - skip this condition
                }
            } else {
                if (scopes == null || !scopes.contains(scope)) {
                    return null;
                }
            }

            if (serviceName == null || dctx == null || context == null || dctx.getClassLoader() == null) {
                throw new GenericServiceException("Cannot have null Service, Context or DispatchContext!");
            }

            if (Debug.verboseOn()) Debug.logVerbose(this.toString() + ", In the context: " + context, module);

            // condition-service; run the service and return the reply result
            if (isService) {
                LocalDispatcher dispatcher = dctx.getDispatcher();
                Map<String, Object> conditionServiceResult = dispatcher.runSync(conditionService,
                        UtilMisc.<String, Object>toMap("serviceContext", context, "serviceName", serviceName,
                                "userLogin", context.get("userLogin")));

                Boolean conditionReply = Boolean.FALSE;
                if (ServiceUtil.isError(conditionServiceResult)) {
                    Debug.logError("Error in condition-service : " +
                            ServiceUtil.getErrorMessage(conditionServiceResult), module);
                } else {
                    conditionReply = (Boolean) conditionServiceResult.get("conditionReply");
                }
                return conditionReply;
            }

            Object lhsValue = null;
            Object rhsValue = null;

            if (property) {
                if (propertyResource != null) {
                    lhsValue = EntityUtilProperties.getPropertyValue(propertyResource, lhsValueName, dctx.getDelegator());
                } else {
                    lhsValue = GeneralConfig.getCommonPropertiesMap().get(lhsValueName);
                    if (lhsValue == null) {
                        Debug.logWarning("Could not find property named '" + lhsValueName
                                + "' in GeneralConfig.getCommonPropertiesMap for eca (invalid common name or missing resource); returning false", module);
                        return false;
                    }
                }
            } else {
                if (UtilValidate.isNotEmpty(lhsMapName)) {
                    try {
                        if (context.containsKey(lhsMapName)) {
                            Map<String, ? extends Object> envMap = UtilGenerics.checkMap(context.get(lhsMapName));
                            lhsValue = envMap.get(lhsValueName);
                        } else {
                            Debug.logInfo("From Map (" + lhsMapName + ") not found in context, defaulting to null.", module);
                        }
                    } catch (ClassCastException e) {
                        throw new GenericServiceException("From Map field [" + lhsMapName + "] is not a Map.", e);
                    }
                } else {
                    if (context.containsKey(lhsValueName)) {
                        lhsValue = context.get(lhsValueName);
                    } else {
                        Debug.logInfo("From Field (" + lhsValueName + ") is not found in context for " + serviceName + ", defaulting to null.", module);
                    }
                }
            }

            if (isValue) {
                if (this.rhsValueExdr != null) {
                    rhsValue = this.rhsValueExdr.expand(context);
                } else {
                    rhsValue = this.rhsValue;
                }
            } else if (UtilValidate.isNotEmpty(rhsMapName)) {
                try {
                    if (context.containsKey(rhsMapName)) {
                        Map<String, ? extends Object> envMap = UtilGenerics.checkMap(context.get(rhsMapName));
                        if (this.rhsValueNameExdr != null) {
                            rhsValue = this.rhsValueNameExdr.get(envMap);
                        } else {
                            rhsValue = envMap.get(this.rhsValue);
                        }
                    } else {
                        Debug.logInfo("To Map (" + rhsMapName + ") not found in context for " + serviceName + ", defaulting to null.", module);
                    }
                } catch (ClassCastException e) {
                    throw new GenericServiceException("To Map field [" + rhsMapName + "] is not a Map.", e);
                }
            } else {
                if (this.rhsValueNameExdr != null) {
                    rhsValue = this.rhsValueNameExdr.get(context);
                } else {
                    if (context.containsKey(this.rhsValue)) {
                        rhsValue = context.get(this.rhsValue);
                    } else {
                        Debug.logInfo("To Field (" + this.rhsValue + ") is not found in context for " + serviceName + ", defaulting to null.", module);
                    }
                }
            }

            if (Debug.verboseOn())
                Debug.logVerbose("Comparing : " + lhsValue + " " + operator + " " + rhsValue, module);

            // evaluate the condition & invoke the action(s)
            List<Object> messages = new ArrayList<>(); // SCIPIO: switched to ArrayList
            Boolean cond = ObjectType.doRealCompare(lhsValue, rhsValue, operator, compareType, format, messages, null, dctx.getClassLoader(), isValue);

            // if any messages were returned send them out
            if (messages.size() > 0 && Debug.warningOn()) {
                for (Object message : messages) {
                    Debug.logWarning(message.toString(), module);
                }
            }
            if (cond != null) {
                return cond;
            } else {
                Debug.logWarning("doRealCompare returned null, returning false", module);
                return false;
            }
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();

            if (UtilValidate.isNotEmpty(conditionService)) buf.append("[").append(conditionService).append("]");
            if (UtilValidate.isNotEmpty(propertyResource)) buf.append("[").append(propertyResource).append("]");
            if (UtilValidate.isNotEmpty(lhsMapName)) buf.append("[").append(lhsMapName).append("]");
            if (UtilValidate.isNotEmpty(lhsValueName)) buf.append("[").append(lhsValueName).append("]");
            if (UtilValidate.isNotEmpty(operator)) buf.append("[").append(operator).append("]");
            if (UtilValidate.isNotEmpty(rhsMapName)) buf.append("[").append(rhsMapName).append("]");
            if (UtilValidate.isNotEmpty(rhsValue)) buf.append("[").append(rhsValue).append("]");
            if (UtilValidate.isNotEmpty(isValue)) buf.append("[").append(isValue).append("]");
            if (UtilValidate.isNotEmpty(compareType)) buf.append("[").append(compareType).append("]");
            if (UtilValidate.isNotEmpty(format)) buf.append("[").append(format).append("]");
            if (UtilValidate.isNotEmpty(scopeString)) buf.append("[").append(scopeString).append("]");

            return buf.toString();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((compareType == null) ? 0 : compareType.hashCode());
            result = prime * result + ((conditionService == null) ? 0 : conditionService.hashCode());
            result = prime * result + ((format == null) ? 0 : format.hashCode());
            result = prime * result + (isValue ? 1231 : 1237);
            result = prime * result + (isService ? 1231 : 1237);
            result = prime * result + ((lhsMapName == null) ? 0 : lhsMapName.hashCode());
            result = prime * result + ((lhsValueName == null) ? 0 : lhsValueName.hashCode());
            result = prime * result + ((operator == null) ? 0 : operator.hashCode());
            result = prime * result + ((rhsMapName == null) ? 0 : rhsMapName.hashCode());
            result = prime * result + ((rhsValue == null) ? 0 : rhsValue.hashCode());
            result = prime * result + ((scopeString == null) ? 0 : scopeString.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SingleServiceEcaCondition) {
                SingleServiceEcaCondition other = (SingleServiceEcaCondition) obj;

                if (!UtilValidate.areEqual(this.conditionService, other.conditionService)) return false;
                if (!UtilValidate.areEqual(this.lhsValueName, other.lhsValueName)) return false;
                if (!UtilValidate.areEqual(this.rhsValue, other.rhsValue)) return false;
                if (!UtilValidate.areEqual(this.lhsMapName, other.lhsMapName)) return false;
                if (!UtilValidate.areEqual(this.rhsMapName, other.rhsMapName)) return false;
                if (!UtilValidate.areEqual(this.operator, other.operator)) return false;
                if (!UtilValidate.areEqual(this.compareType, other.compareType)) return false;
                if (!UtilValidate.areEqual(this.format, other.format)) return false;

                if (this.isValue != other.isValue) return false;
                if (this.isService != other.isService) return false;

                if (!UtilValidate.areEqual(this.scopeString, other.scopeString)) return false; // SCIPIO: FIXME: inaccurate

                return true;
            } else {
                return false;
            }
        }
    }
}
