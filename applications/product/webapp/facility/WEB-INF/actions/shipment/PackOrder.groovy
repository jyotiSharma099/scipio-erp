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

import org.ofbiz.base.util.*;
import org.ofbiz.order.order.*;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.entity.util.EntityUtilProperties;
import org.ofbiz.entity.condition.EntityCondition;

final module = "PackOrder.groovy";

def facilityId = request.getAttribute("facilityId");
if (!facilityId) {
    facilityId = parameters.facilityId;
}
def facility ="";
if (facilityId) {
    facility = from("Facility").where("facilityId", facilityId).queryOne();
    context.facilityId = facilityId;
    context.facility = facility;
}

// order based packing
orderId = parameters.orderId
shipGroupSeqId = parameters.shipGroupSeqId
shipmentId = parameters.shipmentId
shipment = null
if (!shipmentId) {
    shipmentId = request.getAttribute("shipmentId");
}

// If a shipment exists, provide the IDs of any related invoices
invoiceIds = null;
if (shipmentId) {
    // Get the primaryOrderId from the shipment
    shipment = from("Shipment").where(UtilMisc.toMap("shipmentId", shipmentId)).queryOne();
    if (shipment && shipment.primaryOrderId) {
        orderItemBillingList = from("OrderItemBilling").where("orderId", shipment.primaryOrderId).orderBy("invoiceId").queryList();
        invoiceIds = EntityUtil.getFieldListFromEntityList(orderItemBillingList, "invoiceId", true);
        if (invoiceIds) {
            context.invoiceIds = invoiceIds;
        }
    }
}
if (!shipment) {
    shipment = from("Shipment").where("primaryOrderId", orderId, "statusId", "SHIPMENT_PICKED").queryFirst()
}
if (shipment) {
    context.shipment = shipment
    context.shipmentId = shipment.shipmentId;
}

// validate order information
if (orderId && !shipGroupSeqId && orderId.indexOf("/") > -1) {
    // split the orderID/shipGroupSeqID
    idSplit = orderId.split("\\/");
    orderId = idSplit[0];
    shipGroupSeqId = idSplit[1];
} else if (orderId && !shipGroupSeqId) {
    shipGroupSeqId = "00001";
}

// setup the packing session
packSession = session.getAttribute("packingSession");
clear = parameters.clear;
if (clear) {
    session.removeAttribute("packingSession");
} else {
    if (!packSession || (packSession &&
            (!packSession.getPrimaryOrderId().equals(orderId) || !packSession.getPrimaryShipGroupSeqId().equals(shipGroupSeqId)))) {
        packSession = new org.ofbiz.shipment.packing.PackingSession(dispatcher, userLogin);
        session.setAttribute("packingSession", packSession);
    }

    if (packSession.getStatus() == 0) {
        OrderReadHelper orh = new OrderReadHelper(delegator, orderId);
        shipGrp = orh.getOrderItemShipGroup(shipGroupSeqId);
        context.shippedShipGroupSeqId = shipGroupSeqId;
        context.shippedOrderId = orderId;
        context.shippedCarrier = shipGrp.carrierPartyId;

        packSession.clear();
        shipGroupSeqId = null;
        orderId = null;
    }
    packSession.clearItemInfos();
//    else if (clear) {
//        packSession.clear();
//    }


    // picklist based packing information
    picklistBinId = parameters.picklistBinId;
    // see if the bin ID is already set
    if (!picklistBinId) {
        picklistBinId = packSession.getPicklistBinId();
    }
    if (picklistBinId) {
        bin = from("PicklistBin").where("picklistBinId", picklistBinId).queryOne();
        if (bin) {
            orderId = bin.primaryOrderId;
            shipGroupSeqId = bin.primaryShipGroupSeqId;
            packSession.addItemInfo(bin.getRelated("PicklistItem", [itemStatusId: 'PICKITEM_PENDING'], null, false));
            //context.put("picklistItemInfos", bin.getRelated("PicklistItem", UtilMisc.toMap("itemStatusId", "PICKITEM_PENDING"), null, false));
        }
    } else {
        picklistBinId = null;
    }

    // make sure we always re-set the infos
    packSession.setPrimaryShipGroupSeqId(shipGroupSeqId);
    packSession.setPrimaryOrderId(orderId);
    packSession.setPicklistBinId(picklistBinId);
    packSession.setFacilityId(facilityId);

    // SCIPIO: 2.1.0: Why??? commenting out
    //if (invoiceIds) {
    //    orderId = null;
    //}

    context.packingSession = packSession;
    context.orderId = orderId;
    context.shipGroupSeqId = shipGroupSeqId;
    context.picklistBinId = picklistBinId;

    // grab the order information
    if (orderId) {
        orderHeader = from("OrderHeader").where("orderId", orderId).queryOne();
        if (orderHeader) {
            OrderReadHelper orh = new OrderReadHelper(orderHeader);
            context.orderId = orderId;
            context.orderHeader = orderHeader;
            context.orderReadHelper = orh;
            orderItemShipGroup = orh.getOrderItemShipGroup(shipGroupSeqId);
            context.orderItemShipGroup = orderItemShipGroup;
            if (orderItemShipGroup) {
                carrierPartyId = orderItemShipGroup.carrierPartyId;
                carrierShipmentBoxTypes = from("CarrierShipmentBoxType").where("partyId", carrierPartyId).queryList();
                if (carrierShipmentBoxTypes) {
                    context.carrierShipmentBoxTypes = carrierShipmentBoxTypes;
                }
            }

            if ("ORDER_APPROVED".equals(orderHeader.statusId)) {
                if (shipGroupSeqId) {
                    productStoreId = orh.getProductStoreId();
                    // SCIPIO 2.1.0: Doesn't make much sense to throw an error if shipment is not null. A verified picked shipment should be eligible for packing. No shippableItems can be retrieved from OrderItemAndShipGrpInvResAndItemSum
                    // if no shipment present or ItemIssuance if a shipment (with picked status) is present.
                    if (!picklistBinId) {
                        if (!shipment) {
                            shippableItems = from("OrderItemAndShipGrpInvResAndItemSum").where("orderId", orderId, "shipGroupSeqId", shipGroupSeqId).queryList();
                        } else {
                            //request.setAttribute("_ERROR_MESSAGE_", UtilProperties.getMessage("OrderErrorUiLabels", "OrderErrorOrderHasBeenAlreadyVerified", [orderId : orderId], locale));
                            shippableItems = from("ItemIssuance").where("orderId", orderId, "shipGroupSeqId", shipGroupSeqId).queryList();
                        }
                        packSession.addItemInfo(shippableItems);
                        //context.put("itemInfos", shippableItemInfo);
                    }
                    context.productStoreId = productStoreId;
                    // Generate the shipment cost estimate for the ship group
                    shippableItemInfo = orh.getOrderItemAndShipGroupAssoc(shipGroupSeqId);
                    shippableTotal = new Double(orh.getShippableTotal(shipGroupSeqId).doubleValue());
                    shippableWeight = new Double(orh.getShippableWeight(shipGroupSeqId).doubleValue());
                    shippableQuantity = new Double(orh.getShippableQuantity(shipGroupSeqId).doubleValue());
                    if (orderItemShipGroup && orderItemShipGroup.contactMechId && orderItemShipGroup.shipmentMethodTypeId && orderItemShipGroup.carrierPartyId && orderItemShipGroup.carrierRoleTypeId) {
                        shipmentCostEstimate = packSession.getShipmentCostEstimate(orderItemShipGroup, productStoreId, shippableItemInfo, shippableTotal, shippableWeight, shippableQuantity);
                        context.shipmentCostEstimateForShipGroup = shipmentCostEstimate;
                    }
                } else {
                    request.setAttribute("errorMessageList", ['No ship group sequence ID. Cannot process.']);
                }
            } else {
                request.setAttribute("errorMessageList", ["Order #" + orderId + " is not approved for packing."]);
            }
        } else {
            request.setAttribute("errorMessageList", ["Order #" + orderId + " cannot be found."]);
        }
    }

    // Try to get the defaultWeightUomId first from the facility, then from the shipment properties, and finally defaulting to kilos
    defaultWeightUomId = null;
    if (facility) {
        defaultWeightUomId = facility.defaultWeightUomId;
    }
    if (!defaultWeightUomId) {
        defaultWeightUomId = EntityUtilProperties.getPropertyValue("shipment", "shipment.default.weight.uom", "WT_kg", delegator);
    }
    context.defaultWeightUomId = defaultWeightUomId;

}

//packSession.complete(false);
