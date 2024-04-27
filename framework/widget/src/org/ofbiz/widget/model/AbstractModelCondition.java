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
package org.ofbiz.widget.model;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.Perl5Matcher;
import org.ofbiz.base.component.ComponentConfig;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.ObjectType;
import org.ofbiz.base.util.PatternFactory;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.UtilXml;
import org.ofbiz.base.util.collections.FlexibleMapAccessor;
import org.ofbiz.base.util.collections.ValueAccessor;
import org.ofbiz.base.util.string.FlexibleStringExpander;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entityext.permission.EntityPermissionChecker;
import org.ofbiz.minilang.operation.BaseCompare;
import org.ofbiz.security.Security;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ModelService;
import org.ofbiz.service.ServiceUtil;
import org.w3c.dom.Element;

/**
 * Abstract base class for the condition models.
 */
@SuppressWarnings("serial")
public abstract class AbstractModelCondition implements Serializable, ModelCondition {

    /*
     * ----------------------------------------------------------------------- *
     *                     DEVELOPERS PLEASE READ
     * ----------------------------------------------------------------------- *
     *
     * This model is intended to be a read-only data structure that represents
     * an XML element. Outside of object construction, the class should not
     * have any behaviors.
     *
     * Instances of this class will be shared by multiple threads - therefore
     * it is immutable. DO NOT CHANGE THE OBJECT'S STATE AT RUN TIME!
     *
     */

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    public static final ModelConditionFactory DEFAULT_CONDITION_FACTORY = new DefaultConditionFactory();

    /**
     * SCIPIO: ModelCondition that always evaluates to false.
     */
    public static final ModelCondition FALSE_CONDITION = new FalseCondition();

    /**
     * SCIPIO: ModelCondition that always evaluates to true.
     */
    public static final ModelCondition TRUE_CONDITION = new TrueCondition();

    public static List<ModelCondition> readSubConditions(ModelConditionFactory factory, ModelWidget modelWidget,
            Element conditionElement) {
        List<? extends Element> subElementList = UtilXml.childElementList(conditionElement);
        List<ModelCondition> condList = new ArrayList<>(subElementList.size());
        for (Element subElement : subElementList) {
            condList.add(factory.newInstance(modelWidget, subElement));
        }
        return Collections.unmodifiableList(condList);
    }

    private final ModelWidget modelWidget;

    protected AbstractModelCondition(ModelConditionFactory factory, ModelWidget modelWidget, Element conditionElement) {
        this.modelWidget = modelWidget;
    }

    /**
     * SCIPIO: Non-XML constructor.
     */
    protected AbstractModelCondition(ModelConditionFactory factory, ModelWidget modelWidget) {
        this.modelWidget = modelWidget;
    }

    /**
     * SCIPIO: Non-XML constructor.
     */
    protected AbstractModelCondition(ModelWidget modelWidget) {
        this.modelWidget = modelWidget;
    }

    public ModelWidget getModelWidget() {
        return modelWidget;
    }

    /**
     * SCIPIO: Returns suffix log message with location/id of directive (best-effort).
     */
    public String getLogDirectiveLocationString() {
        return modelWidget != null ? modelWidget.getLogWidgetLocationString() : " (untracked widget)";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        ModelConditionVisitor visitor = new XmlWidgetConditionVisitor(sb);
        try {
            accept(visitor);
        } catch (Exception e) {
            Debug.logWarning(e, "Exception thrown in XmlWidgetConditionVisitor", module);
        }
        return sb.toString();
    }

    /**
     * Models the &lt;and&gt; element.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class And extends AbstractModelCondition {
        private final List<ModelCondition> subConditions;

        private And(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
            this.subConditions = readSubConditions(factory, modelWidget, condElement);
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            visitor.visit(this);
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            // return false for the first one in the list that is false, basic and algo
            for (ModelCondition subCondition : this.subConditions) {
                if (!subCondition.eval(context)) {
                    return false;
                }
            }
            return true;
        }

        public List<ModelCondition> getSubConditions() {
            return subConditions;
        }
    }

    /**
     * A <code>ModelCondition</code> factory. This factory handles elements
     * common to all widgets that support conditions. Widgets that have
     * specialized conditions can extend this class.
     *
     */
    public static class DefaultConditionFactory implements ModelConditionFactory {
        public static final ModelCondition TRUE = new ModelCondition() {
            @Override
            public boolean eval(Map<String, Object> context) {
                return true;
            }

            @Override
            public void accept(ModelConditionVisitor visitor) throws Exception {
            }
        };

        public static final ModelCondition FALSE = new ModelCondition() {
            @Override
            public boolean eval(Map<String, Object> context) {
                return false;
            }

            @Override
            public void accept(ModelConditionVisitor visitor) throws Exception {
            }
        };

        public ModelCondition newInstance(ModelWidget modelWidget, Element conditionElement) {
            return newInstance(this, modelWidget, conditionElement);
        }

        // TODO: Test extended factory
        protected ModelCondition newInstance(ModelConditionFactory factory, ModelWidget modelWidget, Element conditionElement) {
            if (conditionElement == null) {
                return TRUE;
            }
            String nodeName = conditionElement.getNodeName();
            if ("and".equals(nodeName)) {
                return new And(factory, modelWidget, conditionElement);
            } else if ("xor".equals(nodeName)) {
                return new Xor(factory, modelWidget, conditionElement);
            } else if ("or".equals(nodeName)) {
                return new Or(factory, modelWidget, conditionElement);
            } else if ("not".equals(nodeName)) {
                return new Not(factory, modelWidget, conditionElement);
            } else if ("if-service-permission".equals(nodeName)) {
                return new IfServicePermission(factory, modelWidget, conditionElement);
            } else if ("if-has-permission".equals(nodeName)) {
                return new IfHasPermission(factory, modelWidget, conditionElement);
            } else if ("if-validate-method".equals(nodeName)) {
                return new IfValidateMethod(factory, modelWidget, conditionElement);
            } else if ("if-compare".equals(nodeName)) {
                return new IfCompare(factory, modelWidget, conditionElement);
            } else if ("if-compare-field".equals(nodeName)) {
                return new IfCompareField(factory, modelWidget, conditionElement);
            } else if ("if-regexp".equals(nodeName)) {
                return new IfRegexp(factory, modelWidget, conditionElement);
            } else if ("if-empty".equals(nodeName)) {
                return new IfEmpty(factory, modelWidget, conditionElement);
            } else if ("if-entity-permission".equals(nodeName)) {
                return new IfEntityPermission(factory, modelWidget, conditionElement);
            } else if ("if-true".equals(nodeName)) { // SCIPIO: new
                return new IfTrue(factory, modelWidget, conditionElement);
            } else if ("if-false".equals(nodeName)) { // SCIPIO: new
                return new IfFalse(factory, modelWidget, conditionElement);
            } else if ("if-widget".equals(nodeName)) { // SCIPIO: new
                return new IfWidget(factory, modelWidget, conditionElement);
            } else if ("if-component".equals(nodeName)) { // SCIPIO: new
                return new IfComponent(factory, modelWidget, conditionElement);
            } else if ("if-entity".equals(nodeName)) { // SCIPIO: new
                return new IfEntity(factory, modelWidget, conditionElement);
            } else if ("if-service".equals(nodeName)) { // SCIPIO: new
                return new IfService(factory, modelWidget, conditionElement);
            } else {
                throw new IllegalArgumentException("Condition element not supported with name: " + conditionElement.getNodeName());
            }
        }
    }

    /**
     * Models the &lt;if-compare&gt; element.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class IfCompare extends AbstractModelCondition {
        private final FlexibleMapAccessor<Object> fieldAcsr;
        private final FlexibleStringExpander formatExdr;
        private final String operator;
        private final String type;
        private final FlexibleStringExpander valueExdr;

        private IfCompare(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
            String fieldAcsr = condElement.getAttribute("field");
            if (fieldAcsr.isEmpty()) {
                fieldAcsr = condElement.getAttribute("field-name");
            }
            this.fieldAcsr = FlexibleMapAccessor.getInstance(fieldAcsr);
            this.valueExdr = FlexibleStringExpander.getInstance(condElement.getAttribute("value"));
            this.operator = condElement.getAttribute("operator");
            this.type = condElement.getAttribute("type");
            this.formatExdr = FlexibleStringExpander.getInstance(condElement.getAttribute("format"));
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            visitor.visit(this);
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            String value = this.valueExdr.expandString(context);
            String format = this.formatExdr.expandString(context);
            Object fieldVal = this.fieldAcsr.get(context);
            // always use an empty string by default
            if (fieldVal == null) {
                fieldVal = "";
            }
            List<Object> messages = new LinkedList<>();
            Boolean resultBool = BaseCompare.doRealCompare(fieldVal, value, operator, type, format, messages, null, null, true);
            if (messages.size() > 0) {
                messages.add(0, "Error with comparison in if-compare between field [" + fieldAcsr.toString() + "] with value ["
                        + fieldVal + "] and value [" + value + "] with operator [" + operator + "] and type [" + type + "]: ");

                StringBuilder fullString = new StringBuilder();
                for (Object item : messages) {
                    fullString.append(item.toString());
                }
                fullString.append(getLogDirectiveLocationString()); // SCIPIO: new
                Debug.logWarning(fullString.toString(), module);
                throw new IllegalArgumentException(fullString.toString());
            }
            return resultBool;
        }

        public FlexibleMapAccessor<Object> getFieldAcsr() {
            return fieldAcsr;
        }

        public FlexibleStringExpander getFormatExdr() {
            return formatExdr;
        }

        public String getOperator() {
            return operator;
        }

        public String getType() {
            return type;
        }

        public FlexibleStringExpander getValueExdr() {
            return valueExdr;
        }
    }

    /**
     * Models the &lt;if-compare-field&gt; element.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class IfCompareField extends AbstractModelCondition {
        private final FlexibleMapAccessor<Object> fieldAcsr;
        private final FlexibleStringExpander formatExdr;
        private final String operator;
        private final FlexibleMapAccessor<Object> toFieldAcsr;
        private final String type;

        private IfCompareField(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
            String fieldAcsr = condElement.getAttribute("field");
            if (fieldAcsr.isEmpty()) {
                fieldAcsr = condElement.getAttribute("field-name");
            }
            this.fieldAcsr = FlexibleMapAccessor.getInstance(fieldAcsr);
            String toFieldAcsr = condElement.getAttribute("to-field");
            if (toFieldAcsr.isEmpty()) {
                toFieldAcsr = condElement.getAttribute("to-field-name");
            }
            this.toFieldAcsr = FlexibleMapAccessor.getInstance(toFieldAcsr);
            this.operator = condElement.getAttribute("operator");
            this.type = condElement.getAttribute("type");
            this.formatExdr = FlexibleStringExpander.getInstance(condElement.getAttribute("format"));
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            visitor.visit(this);
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            String format = this.formatExdr.expandString(context);
            Object fieldVal = this.fieldAcsr.get(context);
            Object toFieldVal = this.toFieldAcsr.get(context);
            // always use an empty string by default
            if (fieldVal == null) {
                fieldVal = "";
            }
            List<Object> messages = new LinkedList<>();
            Boolean resultBool = BaseCompare.doRealCompare(fieldVal, toFieldVal, operator, type, format, messages, null, null,
                    false);
            if (messages.size() > 0) {
                messages.add(0, "Error with comparison in if-compare-field between field [" + fieldAcsr.toString()
                        + "] with value [" + fieldVal + "] and to-field [" + toFieldAcsr.toString() + "] with value ["
                        + toFieldVal + "] with operator [" + operator + "] and type [" + type + "]: ");

                StringBuilder fullString = new StringBuilder();
                for (Object item : messages) {
                    fullString.append(item.toString());
                }
                fullString.append(getLogDirectiveLocationString()); // SCIPIO: new
                Debug.logWarning(fullString.toString(), module);
                throw new IllegalArgumentException(fullString.toString());
            }
            return resultBool;
        }

        public FlexibleMapAccessor<Object> getFieldAcsr() {
            return fieldAcsr;
        }

        public FlexibleStringExpander getFormatExdr() {
            return formatExdr;
        }

        public String getOperator() {
            return operator;
        }

        public FlexibleMapAccessor<Object> getToFieldAcsr() {
            return toFieldAcsr;
        }

        public String getType() {
            return type;
        }
    }

    /**
     * Models the &lt;if-empty&gt; element.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class IfEmpty extends AbstractModelCondition {
        private final FlexibleMapAccessor<Object> fieldAcsr;

        private IfEmpty(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
            String fieldAcsr = condElement.getAttribute("field");
            if (fieldAcsr.isEmpty()) {
                fieldAcsr = condElement.getAttribute("field-name");
            }
            this.fieldAcsr = FlexibleMapAccessor.getInstance(fieldAcsr);
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            visitor.visit(this);
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            Object fieldVal = this.fieldAcsr.get(context);
            return ObjectType.isEmpty(fieldVal);
        }

        public FlexibleMapAccessor<Object> getFieldAcsr() {
            return fieldAcsr;
        }

    }

    /**
     * SCIPIO: Models the &lt;if-true&gt; and &lt;if-false&gt; elements.
     * 2016-11-09: New element, added for 1.14.3.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static abstract class IfTrueFalse extends AbstractModelCondition {
        protected final ValueAccessor accessor;
        protected final boolean verify;
        protected final boolean allowEmpty;

        protected IfTrueFalse(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
            ValueAccessor accessor;
            try {
                accessor = ValueAccessor.getFieldOrExpanderAccessor(condElement, "field", "value");
            } catch(Exception e) {
                Debug.logError("if-true/if-false condition: error in specified field or value: " +
                        e.getMessage() + getLogDirectiveLocationString(), module);
                accessor = ValueAccessor.NULL_ACCESSOR;
            }
            this.accessor = accessor;
            this.verify = !"false".equals(condElement.getAttribute("verify"));
            this.allowEmpty = !"false".equals(condElement.getAttribute("allow-empty"));
        }

        protected IfTrueFalse(ModelConditionFactory factory, ModelWidget modelWidget,
                ValueAccessor accessor, boolean verify, boolean allowEmpty) {
            super(factory, modelWidget);
            this.accessor = accessor;
            this.verify = verify;
            this.allowEmpty = allowEmpty;
        }

        protected IfTrueFalse(ModelWidget modelWidget,
                ValueAccessor accessor, boolean verify, boolean allowEmpty) {
            super(modelWidget);
            this.accessor = accessor;
            this.verify = verify;
            this.allowEmpty = allowEmpty;
        }

        public ValueAccessor getAccessor() {
            return accessor;
        }

        public boolean isVerify() {
            return verify;
        }

        public boolean isAllowEmpty() {
            return allowEmpty;
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            // TODO
        }
    }

    /**
     * SCIPIO: Models the &lt;if-true&gt; element.
     * 2016-11-09: New element, added for 1.14.3.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class IfTrue extends IfTrueFalse {
        private IfTrue(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
        }

        public IfTrue(ModelConditionFactory factory, ModelWidget modelWidget,
                ValueAccessor accessor, boolean verify, boolean allowEmpty) {
            super(factory, modelWidget, accessor, verify, allowEmpty);
        }

        public IfTrue(ModelWidget modelWidget, ValueAccessor accessor, boolean verify, boolean allowEmpty) {
            super(modelWidget, accessor, verify, allowEmpty);
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            Object fieldVal = this.accessor.getValue(context);
            if (Boolean.TRUE.equals(fieldVal) || "true".equals(fieldVal)) {
                return true;
            } else {
                if (verify) {
                    if (!((Boolean.FALSE.equals(fieldVal) || "false".equals(fieldVal))
                            || (allowEmpty && fieldVal == null || "".equals(fieldVal)))) {
                        Debug.logError("if-true: " + accessor.getLogRepr() +
                                " produced invalid boolean value: [" + fieldVal +
                                "]" + getLogDirectiveLocationString(), module);
                    }
                }
                return false;
            }
        }
    }

    /**
     * SCIPIO: Models the &lt;if-false&gt; element.
     * 2016-11-09: New element, added for 1.14.3.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class IfFalse extends IfTrueFalse {
        private IfFalse(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
        }

        public IfFalse(ModelConditionFactory factory, ModelWidget modelWidget,
                ValueAccessor accessor, boolean verify, boolean allowEmpty) {
            super(factory, modelWidget, accessor, verify, allowEmpty);
        }

        public IfFalse(ModelWidget modelWidget, ValueAccessor accessor, boolean verify, boolean allowEmpty) {
            super(modelWidget, accessor, verify, allowEmpty);
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            Object fieldVal = this.accessor.getValue(context);
            if (Boolean.FALSE.equals(fieldVal) || "false".equals(fieldVal)) {
                return true;
            } else {
                if (verify) {
                    if (!((Boolean.TRUE.equals(fieldVal) || "true".equals(fieldVal))
                            || (allowEmpty && fieldVal == null || "".equals(fieldVal)))) {
                        Debug.logError("if-false: " + accessor.getLogRepr() +
                                " produced invalid boolean value: [" + fieldVal +
                                "]" + getLogDirectiveLocationString(), module);
                    }
                }
                return false;
            }
        }
    }

    /**
     * Models the &lt;if-entity-permission&gt; element.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class IfEntityPermission extends AbstractModelCondition {
        private final EntityPermissionChecker permissionChecker;

        private IfEntityPermission(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
            this.permissionChecker = new EntityPermissionChecker(condElement);
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            visitor.visit(this);
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            return permissionChecker.runPermissionCheck(context);
        }

        public EntityPermissionChecker getPermissionChecker() {
            return permissionChecker;
        }
    }

    /**
     * Models the &lt;if-has-permission&gt; element.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class IfHasPermission extends AbstractModelCondition {
        private final FlexibleStringExpander actionExdr;
        private final FlexibleStringExpander permissionExdr;

        private IfHasPermission(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
            this.permissionExdr = FlexibleStringExpander.getInstance(condElement.getAttribute("permission"));
            this.actionExdr = FlexibleStringExpander.getInstance(condElement.getAttribute("action"));
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            visitor.visit(this);
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            // if no user is logged in, treat as if the user does not have permission
            GenericValue userLogin = (GenericValue) context.get("userLogin");
            if (userLogin != null) {
                String permission = permissionExdr.expandString(context);
                String action = actionExdr.expandString(context);
                Security security = (Security) context.get("security");
                if (UtilValidate.isNotEmpty(action)) {
                    // run hasEntityPermission
                    if (security.hasEntityPermission(permission, action, userLogin)) {
                        return true;
                    }
                } else {
                    // run hasPermission
                    if (security.hasPermission(permission, userLogin)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public FlexibleStringExpander getActionExdr() {
            return actionExdr;
        }

        public FlexibleStringExpander getPermissionExdr() {
            return permissionExdr;
        }
    }

    /**
     * Models the &lt;if-regexp&gt; element.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class IfRegexp extends AbstractModelCondition {
        private final FlexibleStringExpander exprExdr;
        private final FlexibleMapAccessor<Object> fieldAcsr;

        private IfRegexp(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
            String fieldAcsr = condElement.getAttribute("field");
            if (fieldAcsr.isEmpty()) {
                fieldAcsr = condElement.getAttribute("field-name");
            }
            this.fieldAcsr = FlexibleMapAccessor.getInstance(fieldAcsr);
            this.exprExdr = FlexibleStringExpander.getInstance(condElement.getAttribute("expr"));
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            visitor.visit(this);
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            Object fieldVal = this.fieldAcsr.get(context);
            String expr = this.exprExdr.expandString(context);
            Pattern pattern;
            try {
                pattern = PatternFactory.createOrGetPerl5CompiledPattern(expr, true);
            } catch (MalformedPatternException e) {
                String errMsg = "Error in evaluation in if-regexp in screen: " + e.toString() + getLogDirectiveLocationString(); // SCIPIO: getLogDirectiveLocationString
                Debug.logError(e, errMsg, module);
                throw new IllegalArgumentException(errMsg);
            }
            String fieldString = null;
            try {
                fieldString = (String) ObjectType.simpleTypeConvert(fieldVal, "String", null, (TimeZone) context.get("timeZone"),
                        (Locale) context.get("locale"), true);
            } catch (GeneralException e) {
                Debug.logError(e, "Could not convert object to String, using empty String" + getLogDirectiveLocationString(), module); // SCIPIO: getLogDirectiveLocationString
            }
            // always use an empty string by default
            if (fieldString == null) {
                fieldString = "";
            }
            PatternMatcher matcher = new Perl5Matcher();
            return matcher.matches(fieldString, pattern);
        }

        public FlexibleStringExpander getExprExdr() {
            return exprExdr;
        }

        public FlexibleMapAccessor<Object> getFieldAcsr() {
            return fieldAcsr;
        }
    }

    /**
     * SCIPIO: Models the &lt;if-component&gt; element.
     * 2018-12-11: New element.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class IfComponent extends AbstractModelCondition {
        private final FlexibleStringExpander componentExdr;

        private IfComponent(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
            this.componentExdr = FlexibleStringExpander.getInstance(condElement.getAttribute("component-name"));
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            visitor.visit(this);
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            String componentName = componentExdr.expandString(context);
            if (UtilValidate.isNotEmpty(componentName)) {
                return ComponentConfig.isComponentEnabled(componentName);
            }
            return false;
        }

        public FlexibleStringExpander getComponentExdr() {
            return componentExdr;
        }
    }

    /**
     * SCIPIO: Models the &lt;if-service&gt; element.
     * 2018-12-11: New element.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class IfEntity extends AbstractModelCondition {
        private final FlexibleStringExpander entityExdr;

        private IfEntity(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
            this.entityExdr = FlexibleStringExpander.getInstance(condElement.getAttribute("entity-name"));
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            visitor.visit(this);
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            String entityName = entityExdr.expandString(context);
            if (UtilValidate.isNotEmpty(entityName)) {
                Delegator delegator = (Delegator) context.get("delegator");
                if (delegator == null) {
                    Debug.logWarning("Cannot test if entity '" + entityName
                            + "' exists; missing delegator in context", module);
                    return false;
                }
                return delegator.isEntity(entityName);
            }
            return false;
        }

        public FlexibleStringExpander getEntityExdr() {
            return entityExdr;
        }
    }

    /**
     * SCIPIO: Models the &lt;if-service&gt; element.
     * 2018-12-11: New element.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class IfService extends AbstractModelCondition {
        private final FlexibleStringExpander serviceExdr;

        private IfService(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
            this.serviceExdr = FlexibleStringExpander.getInstance(condElement.getAttribute("service-name"));
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            visitor.visit(this);
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            String serviceName = serviceExdr.expandString(context);
            if (UtilValidate.isNotEmpty(serviceName)) {
                LocalDispatcher dispatcher = (LocalDispatcher) context.get("dispatcher");
                if (dispatcher == null) {
                    Debug.logWarning("Cannot test if service '" + serviceName
                            + "' exists; missing dispatcher in context", module);
                    return false;
                }
                return dispatcher.getDispatchContext().isService(serviceName);
            }
            return false;
        }

        public FlexibleStringExpander getServiceExdr() {
            return serviceExdr;
        }
    }
    
    /**
     * Models the &lt;if-service-permission&gt; element.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class IfServicePermission extends AbstractModelCondition {
        private final FlexibleStringExpander actionExdr;
        private final FlexibleStringExpander ctxMapExdr;
        private final FlexibleStringExpander resExdr;
        private final FlexibleStringExpander serviceExdr;

        private IfServicePermission(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
            this.serviceExdr = FlexibleStringExpander.getInstance(condElement.getAttribute("service-name"));
            this.actionExdr = FlexibleStringExpander.getInstance(condElement.getAttribute("main-action"));
            this.ctxMapExdr = FlexibleStringExpander.getInstance(condElement.getAttribute("context-map"));
            this.resExdr = FlexibleStringExpander.getInstance(condElement.getAttribute("resource-description"));
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            visitor.visit(this);
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            // if no user is logged in, treat as if the user does not have permission
            GenericValue userLogin = (GenericValue) context.get("userLogin");
            if (userLogin != null) {
                String serviceName = serviceExdr.expandString(context);
                String mainAction = actionExdr.expandString(context);
                String contextMap = ctxMapExdr.expandString(context);
                String resource = resExdr.expandString(context);
                if (UtilValidate.isEmpty(resource)) {
                    resource = serviceName;
                }
                if (UtilValidate.isEmpty(serviceName)) {
                    Debug.logWarning("No permission service-name specified!" + getLogDirectiveLocationString(), module); // SCIPIO: getLogDirectiveLocationString
                    return false;
                }
                // SCIPIO: refactored the main code
                Map<String, Object> serviceContext = UtilGenerics.toMap(context.get(contextMap));
                return checkServicePermission(context, serviceName, mainAction, serviceContext, resource, userLogin);
            }
            return false;
        }

        /**
         * SCIPIO: Factored out from the eval method, for reuse in code that needs to emulate minilang.
         */
        public static boolean checkServicePermission(Map<String, Object> context, String serviceName,
                String mainAction, Map<String, Object> serviceContext, String resource, GenericValue userLogin) {
            // SCIPIO: extra userLogin check so safe from external calls
            if (userLogin == null) {
                return false;
            }

            if (serviceContext != null) {
                // copy the required internal fields
                serviceContext.put("userLogin", context.get("userLogin"));
                serviceContext.put("locale", context.get("locale"));
            } else {
                serviceContext = context;
            }
            // get the service engine objects
            LocalDispatcher dispatcher = (LocalDispatcher) context.get("dispatcher");
            DispatchContext dctx = dispatcher.getDispatchContext();
            // get the service
            ModelService permService;
            try {
                permService = dctx.getModelService(serviceName);
            } catch (GenericServiceException e) {
                Debug.logError(e, module);
                return false;
            }
            // build the context
            Map<String, Object> svcCtx = permService.makeValid(serviceContext, ModelService.IN_PARAM);
            svcCtx.put("resourceDescription", resource);
            if (UtilValidate.isNotEmpty(mainAction)) {
                svcCtx.put("mainAction", mainAction);
            }
            // invoke the service
            Map<String, Object> resp;
            try {
                resp = dispatcher.runSync(permService.name, svcCtx, 300, true);
            } catch (GenericServiceException e) {
                Debug.logError(e, module);
                return false;
            }
            if (ServiceUtil.isError(resp) || ServiceUtil.isFailure(resp)) {
                Debug.logError(ServiceUtil.getErrorMessage(resp), module);
                return false;
            }
            Boolean hasPermission = (Boolean) resp.get("hasPermission");
            if (hasPermission != null) {
                return hasPermission;
            }
            return false;
        }

        public FlexibleStringExpander getActionExdr() {
            return actionExdr;
        }

        public FlexibleStringExpander getCtxMapExdr() {
            return ctxMapExdr;
        }

        public FlexibleStringExpander getResExdr() {
            return resExdr;
        }

        public FlexibleStringExpander getServiceExdr() {
            return serviceExdr;
        }
    }

    /**
     * Models the &lt;if-validate-method&gt; element.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class IfValidateMethod extends AbstractModelCondition {
        private final FlexibleStringExpander classExdr;
        private final FlexibleMapAccessor<Object> fieldAcsr;
        private final FlexibleStringExpander methodExdr;

        private IfValidateMethod(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
            String fieldAcsr = condElement.getAttribute("field");
            if (fieldAcsr.isEmpty()) {
                fieldAcsr = condElement.getAttribute("field-name");
            }
            this.fieldAcsr = FlexibleMapAccessor.getInstance(fieldAcsr);
            this.methodExdr = FlexibleStringExpander.getInstance(condElement.getAttribute("method"));
            this.classExdr = FlexibleStringExpander.getInstance(condElement.getAttribute("class"));
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            visitor.visit(this);
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            String methodName = this.methodExdr.expandString(context);
            String className = this.classExdr.expandString(context);
            Object fieldVal = this.fieldAcsr.get(context);
            String fieldString = null;
            if (fieldVal != null) {
                try {
                    fieldString = (String) ObjectType.simpleTypeConvert(fieldVal, "String", null,
                            (TimeZone) context.get("timeZone"), (Locale) context.get("locale"), true);
                } catch (GeneralException e) {
                    Debug.logError(e, "Could not convert object to String, using empty String" + getLogDirectiveLocationString(), module); // SCIPIO: getLogDirectiveLocationString
                }
            }
            // always use an empty string by default
            if (fieldString == null) {
                fieldString = "";
            }
            Class<?>[] paramTypes = new Class<?>[] { String.class };
            Object[] params = new Object[] { fieldString };
            Class<?> valClass;
            try {
                valClass = ObjectType.loadClass(className);
            } catch (ClassNotFoundException cnfe) {
                Debug.logError("Could not find validation class: " + className + getLogDirectiveLocationString(), module); // SCIPIO: getLogDirectiveLocationString
                return false;
            }
            Method valMethod;
            try {
                valMethod = valClass.getMethod(methodName, paramTypes);
            } catch (NoSuchMethodException cnfe) {
                Debug.logError("Could not find validation method: " + methodName + " of class " + className + getLogDirectiveLocationString(), module); // SCIPIO: getLogDirectiveLocationString
                return false;
            }
            Boolean resultBool = Boolean.FALSE;
            try {
                resultBool = (Boolean) valMethod.invoke(null, params);
            } catch (Exception e) {
                Debug.logError(e, "Error in IfValidationMethod " + methodName + " of class " + className
                        + ", defaulting to false " + getLogDirectiveLocationString(), module); // SCIPIO: getLogDirectiveLocationString
            }
            return resultBool;
        }

        public FlexibleStringExpander getClassExdr() {
            return classExdr;
        }

        public FlexibleMapAccessor<Object> getFieldAcsr() {
            return fieldAcsr;
        }

        public FlexibleStringExpander getMethodExdr() {
            return methodExdr;
        }

    }

    /**
     * Models the &lt;not&gt; element.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class Not extends AbstractModelCondition {
        private final ModelCondition subCondition;

        private Not(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
            Element firstChildElement = UtilXml.firstChildElement(condElement);
            this.subCondition = factory.newInstance(modelWidget, firstChildElement);
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            visitor.visit(this);
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            return !this.subCondition.eval(context);
        }

        public ModelCondition getSubCondition() {
            return subCondition;
        }
    }

    /**
     * Models the &lt;or&gt; element.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class Or extends AbstractModelCondition {
        private final List<ModelCondition> subConditions;

        private Or(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
            this.subConditions = readSubConditions(factory, modelWidget, condElement);
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            visitor.visit(this);
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            // return true for the first one in the list that is true, basic or algo
            for (ModelCondition subCondition : this.subConditions) {
                if (subCondition.eval(context)) {
                    return true;
                }
            }
            return false;
        }

        public List<ModelCondition> getSubConditions() {
            return subConditions;
        }
    }

    /**
     * Models the &lt;xor&gt; element.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class Xor extends AbstractModelCondition {
        private final List<ModelCondition> subConditions;

        private Xor(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
            this.subConditions = readSubConditions(factory, modelWidget, condElement);
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            visitor.visit(this);
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            // if more than one is true stop immediately and return false; if all are false return false; if only one is true return true
            boolean foundOneTrue = false;
            for (ModelCondition subCondition : this.subConditions) {
                if (subCondition.eval(context)) {
                    if (foundOneTrue) {
                        // now found two true, so return false
                        return false;
                    }
                    foundOneTrue = true;
                }
            }
            return foundOneTrue;
        }

        public List<ModelCondition> getSubConditions() {
            return subConditions;
        }
    }

    /**
     * SCIPIO: Models the &lt;if-widget&gt; element.
     * 2016-11-11: New element, added for 1.14.3.
     *
     * @see <code>widget-common.xsd</code>
     */
    public static class IfWidget extends AbstractModelCondition {
        private final FlexibleStringExpander name;
        private final FlexibleStringExpander location;
        private final WidgetFactory widgetFactory;
        private final String operator;
        private final boolean definedOperator;

        private IfWidget(ModelConditionFactory factory, ModelWidget modelWidget, Element condElement) {
            super(factory, modelWidget, condElement);
            this.name = FlexibleStringExpander.getInstance(condElement.getAttribute("name"));
            this.location = FlexibleStringExpander.getInstance(condElement.getAttribute("location"));
            this.widgetFactory = WidgetFactory.getFactory(condElement.getAttribute("type"));
            this.operator = condElement.getAttribute("operator");
            this.definedOperator = "defined".equals(operator);
            if (!this.definedOperator) {
                throw new IllegalArgumentException("Unrecognized widget operator: " + operator);
            }
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            // TODO
        }

        @Override
        public boolean eval(Map<String, Object> context) {
            if (definedOperator) {
                return widgetFactory.isWidgetDefinedAtLocation(
                        ModelLocation.fromResAndName(this.location.expandString(context),
                                this.name.expandString(context)));
            } else {
                throw new IllegalArgumentException("Unrecognized widget operator: " + operator);
            }
        }

        public FlexibleStringExpander getNameExdr() {
            return name;
        }

        public FlexibleStringExpander getLocationExdr() {
            return location;
        }
    }


    /**
     * SCIPIO: always false condition.
     */
    public static final class FalseCondition implements ModelCondition {
        private FalseCondition() {}

        @Override
        public boolean eval(Map<String, Object> context) {
            return false;
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            // TODO Auto-generated method stub
        }
    }

    /**
     * SCIPIO: always true condition.
     */
    public static final class TrueCondition implements ModelCondition {
        private TrueCondition() {}

        @Override
        public boolean eval(Map<String, Object> context) {
            return true;
        }

        @Override
        public void accept(ModelConditionVisitor visitor) throws Exception {
            // TODO Auto-generated method stub
        }
    }

}
