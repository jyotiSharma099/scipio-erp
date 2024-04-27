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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ilscipio.scipio.service.def.Service;
import com.ilscipio.scipio.service.def.ServiceDefUtil;
import com.ilscipio.scipio.service.def.seca.Seca;
import com.ilscipio.scipio.service.def.seca.SecaAction;
import com.ilscipio.scipio.service.def.seca.SecaSet;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.UtilXml;
import org.ofbiz.base.util.string.FlexibleStringExpander;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.GenericServiceException;
import org.w3c.dom.Element;

/**
 * ServiceEcaRule
 */
@SuppressWarnings("serial")
public final class ServiceEcaRule implements java.io.Serializable {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    public final String serviceName;
    public final String eventName;
    public final boolean runOnFailure;
    public final boolean runOnError;
    public final List<ServiceEcaCondition> conditions;
    public final FlexibleStringExpander conditionExpr; // SCIPIO: 3.0.0: Added for annotations support
    public final List<Object> actionsAndSets;
    public final boolean enabled; // SCIPIO: 2018-09-06: made final for thread-safety
    public final String definitionLocation;
    protected transient Boolean initEnabled = null;

    public ServiceEcaRule(Element eca, String definitionLocation) {
        this.definitionLocation = definitionLocation;
        this.serviceName = eca.getAttribute("service");
        this.eventName = eca.getAttribute("event");
        this.runOnFailure = "true".equals(eca.getAttribute("run-on-failure"));
        this.runOnError = "true".equals(eca.getAttribute("run-on-error"));
        this.enabled = !"false".equals(eca.getAttribute("enabled"));

        /* SCIPIO: refactored, this was not great
        for (Element element: UtilXml.childElementList(eca, "condition")) {
            conditions.add(new ServiceEcaCondition(element, true, false));
        }

        for (Element element: UtilXml.childElementList(eca, "condition-field")) {
            conditions.add(new ServiceEcaCondition(element, false, false));
        }

        for (Element element: UtilXml.childElementList(eca, "condition-service")) {
            conditions.add(new ServiceEcaCondition(element, false, true));
        }

        for (Element element: UtilXml.childElementList(eca, "condition-property")) { // SCIPIO
            conditions.add(new ServiceEcaCondition(element, true, false, true));
        }

        for (Element element: UtilXml.childElementList(eca, "condition-property-field")) { // SCIPIO
            conditions.add(new ServiceEcaCondition(element, false, false, true));
        }
         */
        ArrayList<ServiceEcaCondition> conditions = new ArrayList<ServiceEcaCondition>(); // SCIPIO: fixed final synch issue
        for (Element element: UtilXml.childElementList(eca)) {
            ServiceEcaCondition condition = ServiceEcaCondition.getCondition(element);
            if (condition != null) {
                conditions.add(condition);
            }
        }
        if (Debug.verboseOn()) {
            Debug.logVerbose("Conditions: " + conditions, module);
        }
        conditions.trimToSize();
        this.conditions = Collections.unmodifiableList(conditions);
        this.conditionExpr = null; // SCIPIO: 3.0.0: Added for annotations support

        Set<String> nameSet = UtilMisc.toSet("set", "action");
        ArrayList<Object> actionsAndSets = new ArrayList<Object>(); // SCIPIO: fixed final synch issue
        for (Element actionOrSetElement: UtilXml.childElementList(eca, nameSet)) {
            if ("action".equals(actionOrSetElement.getNodeName())) {
                actionsAndSets.add(new ServiceEcaAction(actionOrSetElement, this.eventName));
            } else {
                actionsAndSets.add(new ServiceEcaSetField(actionOrSetElement));
            }
        }
        actionsAndSets.trimToSize();
        this.actionsAndSets = Collections.unmodifiableList(actionsAndSets);

        if (Debug.verboseOn()) {
            Debug.logVerbose("actions and sets (intermixed): " + actionsAndSets, module);
        }
    }

    /**
     * Annotations constructor.
     *
     * <p>NOTE: serviceClass null when serviceMethod set and vice-versa.</p>
     *
     * <p>SCIPIO: 3.0.0: Added for annotations support.</p>
     */
    public ServiceEcaRule(Seca secaDef, Service serviceDef, Class<?> serviceClass, Method serviceMethod) {
        this.definitionLocation = (serviceMethod != null) ? serviceMethod.getDeclaringClass().getName() :
                (serviceClass.getEnclosingClass() != null ? serviceClass.getEnclosingClass().getName() : serviceClass.getName());
        this.serviceName = (!secaDef.service().isEmpty()) ? secaDef.service() : ServiceDefUtil.getServiceName(serviceDef, serviceClass, serviceMethod);
        if (UtilValidate.isEmpty(serviceName)) {
            if (serviceClass != null) {
                throw new IllegalArgumentException("Missing service ECA source service name on " + Seca.class.getSimpleName() +
                        " annotation for service class " + serviceClass.getName());
            } else {
                throw new IllegalArgumentException("Missing service ECA source service name on " + Seca.class.getSimpleName() +
                        " annotation for service method " + serviceMethod.getDeclaringClass().getName() + "." + serviceMethod.getName());
            }
        }
        this.eventName = secaDef.event();
        this.runOnFailure = "true".equals(secaDef.runOnFailure());
        this.runOnError = "true".equals(secaDef.runOnError());
        this.enabled = !"false".equals(secaDef.enabled());
        this.conditions = List.of();
        this.conditionExpr = FlexibleStringExpander.getInstance(secaDef.condition());

        ArrayList<Object> actionsAndSets = new ArrayList<Object>(); // SCIPIO: fixed final synch issue

        // Global assignments
        List<SecaSet> assignments = new ArrayList<>(Arrays.asList(secaDef.assignments()));
        for (SecaSet assignmentDef : assignments) {
            actionsAndSets.add(new ServiceEcaSetField(assignmentDef, secaDef, serviceDef, serviceClass, serviceMethod));
        }

        // Actions and local assignments
        List<SecaAction> actions = new ArrayList<>(Arrays.asList(secaDef.actions()));
        if (actions.isEmpty()) {
            actions.add(SecaAction.DefaultType.class.getAnnotation(SecaAction.class));
        }
        for (SecaAction action : actions) {
            for (SecaSet assignmentDef : action.assignments()) {
                actionsAndSets.add(new ServiceEcaSetField(assignmentDef, secaDef, serviceDef, serviceClass, serviceMethod));
            }
            actionsAndSets.add(new ServiceEcaAction(action, secaDef, serviceDef, serviceClass, serviceMethod));
        }

        actionsAndSets.trimToSize();
        this.actionsAndSets = Collections.unmodifiableList(actionsAndSets);

        if (Debug.verboseOn()) {
            Debug.logVerbose("actions and sets (intermixed): " + actionsAndSets, module);
        }
    }

    public String getShortDisplayName() {
        return this.serviceName + ":" + this.eventName;
    }

    public String getServiceName() {
        return this.serviceName;
    }

    public String getEventName() {
        return this.eventName;
    }

    public String getDefinitionLocation() {
        return this.definitionLocation;
    }

    public List<ServiceEcaAction> getEcaActionList() {
        List<ServiceEcaAction> actionList = new ArrayList<>(this.actionsAndSets.size()); // SCIPIO: switched to ArrayList
        for (Object actionOrSet: this.actionsAndSets) {
            if (actionOrSet instanceof ServiceEcaAction) {
                actionList.add((ServiceEcaAction) actionOrSet);
            }
        }
        return actionList;
    }

    public List<ServiceEcaCondition> getEcaConditionList() {
        List<ServiceEcaCondition> condList = new ArrayList<>(this.conditions); // SCIPIO: switched to ArrayList
        //condList.addAll(this.conditions);
        return condList;
    }

    public void eval(String serviceName, DispatchContext dctx, Map<String, Object> context, Map<String, Object> result, boolean isError, boolean isFailure, Set<String> actionsRun) throws GenericServiceException {
        // SCIPIO: Now incorporated into initEnabled for speed
        //if (!enabled) {
        //    if (Debug.verboseOn()) {
        //        Debug.logVerbose("Service ECA [" + this.serviceName + "] on [" + this.eventName + "] is disabled; not running.", module);
        //    }
        //    return;
        //}
        if (!isEnabled(dctx, context)) { // SCIPIO
            return;
        }
        if (isFailure && !this.runOnFailure) {
            return;
        }
        if (isError && !this.runOnError) {
            return;
        }

        boolean allCondTrue = true;
        for (ServiceEcaCondition ec: conditions) {
            Boolean subResult = ec.eval(serviceName, dctx, context, null); // SCIPIO: null
            if (subResult != null) {
                if (!subResult) {
                    if (Debug.infoOn()) {
                        Debug.logInfo("For Service ECA [" + this.serviceName + "] on [" + this.eventName + "] got false for condition: " + ec, module);
                    }
                    allCondTrue = false;
                    break;
                } else {
                    if (Debug.verboseOn()) {
                        Debug.logVerbose("For Service ECA [" + this.serviceName + "] on [" + this.eventName + "] got true for condition: " + ec, module);
                    }
                }
            }
        }

        // if all conditions are true
        if (allCondTrue) {
            for (Object setOrAction: actionsAndSets) {
                if (setOrAction instanceof ServiceEcaAction) {
                    ServiceEcaAction ea = (ServiceEcaAction) setOrAction;
                    // in order to enable OR logic without multiple calls to the given service,
                    // only execute a given service name once per service call phase
                    if (!actionsRun.contains(ea.serviceName)) {
                        if (Debug.infoOn()) {
                            Debug.logInfo("Running Service ECA Service: " + ea.serviceName + ", triggered by rule on Service: " + serviceName, module);
                        }
                        if (ea.runAction(serviceName, dctx, context, result)) {
                            actionsRun.add(ea.serviceName);
                        }
                    }
                } else {
                    ServiceEcaSetField sf = (ServiceEcaSetField) setOrAction;
                    sf.eval(context);
                }
            }
        }
    }

    /* SCIPIO: 2018-09-06: REMOVED for thread safety (NOTE: this was only lately merged from upstream, so no harm done)
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    */

    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((actionsAndSets == null) ? 0 : actionsAndSets.hashCode());
        result = prime * result + ((conditions == null) ? 0 : conditions.hashCode());
        // SCIPIO: 2018-10-09: TODO: REVIEW: this is not in equals, can't be here...
        //result = prime * result + ((definitionLocation == null) ? 0 : definitionLocation.hashCode());
        // SCIPIO: 2018-10-09: TODO: REVIEW: this is not in equals, can't be here...
        //result = prime * result + (enabled ? 1231 : 1237);
        result = prime * result + ((eventName == null) ? 0 : eventName.hashCode());
        result = prime * result + (runOnError ? 1231 : 1237);
        result = prime * result + (runOnFailure ? 1231 : 1237);
        result = prime * result + ((serviceName == null) ? 0 : serviceName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ServiceEcaRule) {
            ServiceEcaRule other = (ServiceEcaRule) obj;
            if (!UtilValidate.areEqual(this.serviceName, other.serviceName)) {
                return false;
            }
            if (!UtilValidate.areEqual(this.eventName, other.eventName)) {
                return false;
            }
            if (!this.conditions.equals(other.conditions)) {
                return false;
            }
            if (!this.actionsAndSets.equals(other.actionsAndSets)) {
                return false;
            }

            if (this.runOnFailure != other.runOnFailure) {
                return false;
            }
            if (this.runOnError != other.runOnError) {
                return false;
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "ServiceEcaRule:" + this.serviceName + ":" + this.eventName + ":runOnError=" + this.runOnError + ":runOnFailure=" + this.runOnFailure + ":enabled=" + this.enabled + ":conditions=" + this.conditions + ":actionsAndSets=" + this.actionsAndSets;
    }

    protected final boolean isEnabled(DispatchContext dctx, Map<String, Object> context) { // SCIPIO
        Boolean initEnabled = this.initEnabled;
        if (initEnabled == null) {
            synchronized (this) {
                initEnabled = this.initEnabled;
                if (initEnabled == null) {
                    if (!enabled) {
                        initEnabled = false;
                        if (Debug.infoOn()) {
                            Debug.logInfo("Service ECA [" + this.serviceName + "] on [" + this.eventName + "] is disabled globally.", module);
                        }
                    } else {
                        initEnabled = checkInitConditions(dctx, context, true);
                    }
                    this.initEnabled = initEnabled;
                }
            }
        }
        return initEnabled;
    }

    protected boolean checkInitConditions(DispatchContext dctx, Map<String, Object> context, boolean log) { // SCIPIO
        for(ServiceEcaCondition cond : conditions) {
            try {
                Boolean subResult = cond.eval(serviceName, dctx, context, "init");
                if (Boolean.FALSE.equals(subResult)) {
                    if (log && Debug.infoOn()) {
                        Debug.logInfo("Service ECA [" + this.serviceName + "] on [" + this.eventName + "] is disabled by init condition: " + cond, module);
                    }
                    return false;
                }
            } catch (GenericServiceException e) {
                Debug.logError(e, "Could not check seca condition for service [" + serviceName + "] at init scope: " + cond, module);
            }
        }
        return true;
    }
}
