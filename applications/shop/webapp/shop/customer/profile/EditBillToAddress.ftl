<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<div id="billToServerError" class="errorMessage"></div>
<form id="editBillToPostalAddress" method="post" action="">
  <fieldset>
    <input type="hidden" name="setBillingPurpose" value="Y" />
    <input type="hidden" name="contactMechId" value="${billToContactMechId!}" />
    <#assign productStoreId = Static["org.ofbiz.product.store.ProductStoreWorker"].getProductStoreId(request) />
    <input type="hidden" name="productStoreId" value="${productStoreId!}" />
    <div>
      <label for="billToAddress1">${uiLabelMap.PartyAddressLine1}*</label>
      <input type="text" class="required" name="address1" id="billToAddress1" value="${billToAddress1!}" maxlength="30" />
      <span id="advice-required-billToAddress1" style="display: none" class="errorMessage">(${uiLabelMap.CommonRequired})</span>
    </div>
    <div>
      <label for="billToAddress2">${uiLabelMap.PartyAddressLine2}</label>
      <input type="text" name="address2" id="billToAddress2" value="${billToAddress2!}" maxlength="30" />
    </div>
    <div>
      <label for="billToCity">${uiLabelMap.PartyCity}*</label>
      <input type="text" class="required" name="city" id="billToCity" value="${billToCity!}" maxlength="30" />
      <span id="advice-required-billToCity" style="display: none" class="errorMessage">(${uiLabelMap.CommonRequired})</span>
    </div>
    <div>
      <label for="billToPostalCode">${uiLabelMap.PartyZipCode}*</label>
      <input type="text" class="required" name="postalCode" id="billToPostalCode" value="${billToPostalCode!}" maxlength="10" />
      <span id="advice-required-billToPostalCode" style="display: none" class="errorMessage">(${uiLabelMap.CommonRequired})</span>
    </div>
    <div>
      <label for="billToCountryGeoId">${uiLabelMap.CommonCountry}*</label>
      <select name="countryGeoId" id="billToCountryGeoId" class="required">
        <#if billToCountryGeoId??>
          <option value="${billToCountryGeoId!}">${billToCountryProvinceGeo!(billToCountryGeoId!)}</option>
        </#if>
        <@render resource="component://common/widget/CommonScreens.xml#countries" ctxVars={"countriesPreselect":!billToCountryGeoId??}/>
      </select>
      <span id="advice-required-billToCountryGeoId" style="display: none" class="errorMessage">(${uiLabelMap.CommonRequired})</span>
    </div>
    <div id="billToStates">
      <label for="billToStateProvinceGeoId">${uiLabelMap.PartyState}*<span id="advice-required-billToStateProvinceGeoId" style="display: none" class="errorMessage">(${uiLabelMap.CommonRequired})</span></label>
      <select name="stateProvinceGeoId" id="billToStateProvinceGeoId">
        <#if billToStateProvinceGeoId?has_content>
          <option value="${billToStateProvinceGeoId!}">${billToStateProvinceGeo!(billToStateProvinceGeoId!)}</option>
        <#else>
          <option value="_NA_">${uiLabelMap.PartyNoState}</option>
        </#if>
      </select>
    </div>
    <#if billToTelecomNumber?has_content>
      <div>
        <label>${uiLabelMap.PartyPhoneNumber}*</label>
        <span id="advice-required-billToCountryCode" style="display:none" class="errorMessage"></span>
        <span id="advice-required-billToAreaCode" style="display:none" class="errorMessage"></span>
        <span id="advice-required-billToContactNumber" style="display:none" class="errorMessage"></span>
        <span id="billToPhoneRequired" style="display: none;" class="errorMessage">(${uiLabelMap.CommonRequired})</span>
        <input type="hidden" name="phoneContactMechId" value="${billToTelecomNumber.contactMechId!}" />
        <input type="text" name="countryCode" id="billToCountryCode" class="required" value="${billToTelecomNumber.countryCode!}" size="3" maxlength="3" />
        - <input type="text" name="areaCode" id="billToAreaCode" class="required" value="${billToTelecomNumber.areaCode!}" size="3" maxlength="3" />
        - <input type="text" name="contactNumber" id="billToContactNumber" class="required" value="${contactNumber?default("${billToTelecomNumber.contactNumber!}")}" size="6" maxlength="7" />
        - <input type="text" name="extension" value="${extension?default("${billToExtension!}")}" size="3" maxlength="3" />
      </div>
    </#if>
    <div class="inline">
      <label for="setShippingPurposeForBilling">${uiLabelMap.EcommerceMyDefaultShippingAddress}</label>
      <input type="checkbox" name="setShippingPurpose" id="setShippingPurposeForBilling" value="Y" <#if setShippingPurpose??>checked="checked"</#if> />
    </div>
      <#--
    <div>
      <a name="submitEditBillToPostalAddress" id="submitEditBillToPostalAddress" class="${styles.link_run_sys!} ${styles.action_update!}" onclick="updatePartyBillToPostalAddress('submitEditBillToPostalAddress')">${uiLabelMap.CommonSubmit}</a>
      <a class="popup_closebox ${styles.link_nav_cancel!}" href="javascript:void(0);">${uiLabelMap.CommonClose}</a>
    </div>
      -->
  </fieldset>
</form>