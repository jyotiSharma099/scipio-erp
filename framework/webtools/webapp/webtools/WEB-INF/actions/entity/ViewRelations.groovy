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
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.entity.Delegator;
import org.ofbiz.security.Security;
import org.ofbiz.entity.model.ModelReader;
import org.ofbiz.entity.model.ModelEntity;
import org.ofbiz.entity.model.ModelRelation;
import org.ofbiz.entity.model.ModelKeyMap;
import org.ofbiz.entity.GenericModelException;

entityName = parameters.entityName;
context.entityName = entityName;

reader = delegator.getModelReader();
modelEntity = null;
try { // SCIPIO: handle missing and store in context
    modelEntity = reader.getModelEntity(entityName);
} catch(GenericModelException e) {
    errorMessageList = context.errorMessageList;
    if (errorMessageList == null) errorMessageList = [];
    errorMessageList.add(UtilProperties.getMessage("WebtoolsUiLabels",
        "WebtoolsEntityNotFoundSpecified", [entityName: entityName], context.locale));
    context.errorMessageList = errorMessageList;
}
context.modelEntity = modelEntity;

// SCIPIO: refactored
plainTableName = modelEntity?.getPlainTableName();
context.plainTableName = plainTableName;

hasViewPermission = security.hasEntityPermission("ENTITY_DATA", "_VIEW", request) || security.hasEntityPermission(plainTableName, "_VIEW", request);
context.hasViewPermission = hasViewPermission;

// SCIPIO: return instead of crashing
if (!modelEntity) {
    return;
}

relations = [];
for (rit = modelEntity.getRelationsIterator(); rit.hasNext();) {
    mapRelation = [:];

    modelRelation = rit.next();
    relFields = [];
    for (kit = modelRelation.getKeyMaps().iterator(); kit.hasNext();) {
        mapFields = [:];
        keyMap = kit.next();
        mapFields.fieldName = keyMap.getFieldName();
        mapFields.relFieldName = keyMap.getRelFieldName();

        relFields.add(mapFields);
    }
    mapRelation.relFields = relFields;
    mapRelation.title = modelRelation.getTitle();
    mapRelation.relEntityName = modelRelation.getRelEntityName();
    mapRelation.type = modelRelation.getType();
    mapRelation.fkName = modelRelation.getFkName();

    relations.add(mapRelation);
}
context.relations = relations;
