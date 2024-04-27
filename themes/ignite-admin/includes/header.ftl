<#--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->
<@virtualSection name="Global-Header-Ignite" contains="!$Global-Column-Left, *">
<#-- Applications -->
<#if (requestAttributes.externalLoginKey)??><#assign externalKeyParam = "?externalLoginKey=" + (requestAttributes.externalLoginKey!)></#if>
<#if (externalLoginKey)??><#assign externalKeyParam = "?externalLoginKey=" + (requestAttributes.externalLoginKey!)></#if>
<#assign ofbizServerName = application.getAttribute("_serverId")!"default-server">
<#assign contextPath = request.getContextPath()>
<#if userLogin?has_content>
    <#assign displayApps = Static["org.ofbiz.webapp.control.LoginWorker"].getAppBarWebInfos(security, userLogin, ofbizServerName, "main")>
    <#assign displaySecondaryApps = Static["org.ofbiz.webapp.control.LoginWorker"].getAppBarWebInfos(security, userLogin, ofbizServerName, "secondary")>
    <#assign appModelMenu = Static["org.ofbiz.widget.model.MenuFactory"].getMenuFromLocation(applicationMenuLocation,applicationMenuName)>
</#if>
<#if person?has_content>
  <#assign userName = person.firstName!"" + " " + person.middleName!"" + " " + person.lastName!"">
<#elseif partyGroup?has_content>
  <#assign userName = partyGroup.groupName!>
<#elseif userLogin??>
  <#assign userName = userLogin.userLoginId>
<#else>
  <#assign userName = "">
</#if>
<#if defaultOrganizationPartyGroupName?has_content>
  <#assign orgName = " - " + defaultOrganizationPartyGroupName!>
<#else>
  <#assign orgName = "">
</#if>
<#macro generalMenu>
    <#if userLogin??>
        <#--
        <#if layoutSettings.topLines?has_content>
          <#list layoutSettings.topLines as topLine>
            <#if topLine.text??>
              <li class="nav-item">${topLine.text}<a href="${raw(topLine.url!)}${raw(externalKeyParam)}">${topLine.urlText!}</a></li>
            <#elseif topLine.dropDownList??>
              <li class="nav-item"><#include "component://common/webcommon/includes/insertDropDown.ftl"/></li>
            <#else>
              <li class="nav-item">${topLine!}</li>
            </#if>
          </#list>
        <#else>
          <li class="nav-item">${userLogin.userLoginId}</li>
        </#if>
        -->
        <li><a href="<@ofbizUrl>ListVisualThemes</@ofbizUrl>" class="dropdown-item"><i class="${styles.icon!} fa-photo"></i> ${uiLabelMap.CommonVisualThemes}</a></li>
    </#if>
    <li><a href="<@ofbizUrl>ListLocales</@ofbizUrl>" class="dropdown-item"><i class="${styles.icon!} fa-language"></i> ${uiLabelMap.CommonLanguageTitle}</a></li>
    <#-- Disabled Help function for the time being
    <#if parameters.componentName?? && requestAttributes._CURRENT_VIEW_?? && helpTopic??>
        <#include "component://common/webcommon/includes/helplink.ftl" />
        <#assign portalPageParamStr><#if parameters.portalPageId?has_content>&portalPageId=${raw(parameters.portalPageId!)}</#if></#assign>
        <li class="has-form"><@modal label=uiLabelMap.CommonHelp id="help" linkClass="dropdown-item"
            href=makeOfbizUrl("showHelp?helpTopic=${raw(helpTopic!)}${portalPageParamStr}") icon="${styles.icon!} fa-info"></@modal></li>
    </#if>-->
    <#if userLogin??>
        <li class="active"><a href="<@ofbizUrl>logout?t=${.now?long}</@ofbizUrl>" class="dropdown-item active"><i class="${styles.icon!} fa-power-off"></i> ${uiLabelMap.CommonLogout}</a></li>
    </#if>
</#macro>

<#macro primaryAppsMenu>
  <#assign appCount = 0>
  <#assign firstApp = true>
  <#--  <li class="nav-item"><label>${uiLabelMap["CommonPrimaryApps"]}</label></li>-->
  <#list displayApps as display>
        <#assign thisApp = display.getContextRoot()>
        <#assign selected = false>
        <#if thisApp == contextPath || contextPath + "/" == thisApp>
          <#assign selected = true>
        </#if>
        <#assign servletPath = Static["org.ofbiz.webapp.WebAppUtil"].getControlServletPathSafeSlash(display)!"">
        <#assign thisURL = raw(servletPath)>
        <#if thisApp != "/">
          <#if servletPath?has_content>
            <#assign thisURL = thisURL + "main">
          <#else>
            <#assign thisURL = thisApp>
          </#if>
        </#if>
        <#if layoutSettings.suppressTab?? && display.name == layoutSettings.suppressTab>
          <#-- do not display this component-->
        <#else>
            <li class="<#if selected> active</#if>">
                <a href="${thisURL}${raw(externalKeyParam)}" class="dropdown-item<#if selected> active</#if>"
                <#if uiLabelMap??> title="${uiLabelMap[display.description]}">
                    <#if styles.app_icon[display.name]?has_content><i class="${styles.icon!} ${styles.app_icon[display.name]}"></i> </#if>${uiLabelMap[display.title]}
                <#else> title="${display.description}">
                    ${display.title}
                </#if>
                </a>
            </li>
            <#assign appCount = appCount + 1>
        </#if>
  </#list>
</#macro>

<#macro secondaryAppsMenu>
    <#--<li class="nav-item"><label>${uiLabelMap["CommonSecondaryApps"]}</label></li>-->
    <#list displaySecondaryApps as display>
        <#assign thisApp = display.getContextRoot()>
        <#assign selected = false>
        <#if thisApp == contextPath || contextPath + "/" == thisApp>
          <#assign selected = true>
        </#if>
          <#assign servletPath = Static["org.ofbiz.webapp.WebAppUtil"].getControlServletPathSafeSlash(display)!"">
          <#assign thisURL = raw(servletPath)>
          <#if thisApp != "/">
            <#if servletPath?has_content>
              <#assign thisURL = thisURL + "main">
            <#else>
              <#assign thisURL = thisApp>
            </#if>
          </#if>
          <li class="<#if selected> active</#if>">      
            <a href="${thisURL}${raw(externalKeyParam)}" class="dropdown-item <#if selected> active</#if>"
                <#if uiLabelMap??> title="${uiLabelMap[display.description]}">
                    <#if styles.app_icon[display.name]?has_content><i class="${styles.icon!} ${styles.app_icon[display.name]}"></i> </#if>${uiLabelMap[display.title]}
                <#else> title="${display.description}">
                    ${display.title}
                </#if>
            </a>
            <#assign appCount = appCount + 1>
          </li>
    </#list>
</#macro>

<#macro notificationsMenu>
<ul class="dropdown-menu dropdown-menu-right message-dropdown">
        <li><div class="dropdown-header">${uiLabelMap["CommonLastSytemNotes"]}</div></li>
        <#list systemNotifications as notification>
            <li class="media-preview">
                <#if notification.url?has_content><#assign notificationUrl=addParamsToUrl(notification.url,{"scipioSysMsgId":notification.messageId})></#if>
                <a href="${notificationUrl!"#"}"  class="dropdown-item">
                    <div class="message_wrap <#if notification.isRead?has_content && notification.isRead=='Y'>message_isread</#if>">
                        <#--<div class="message_status">
                            <#if notification.fromPartyId?has_content> <span class="message_user"><small>${notification.fromPartyId!""}</small></span></#if>
                        </div>-->
                        <div class="message_header">
                            ${notification.title!"No Title"} <span class="message_time float-right">${notification.createdStamp?string.short}</span>
                        </div>
                        <div class="message_body">${notification.description!""}</div>
                    </div>
                </a>
            </li>
        </#list>
        <#-- 
        <li class="message-footer">
            <a href="#">
                <div>View All</div>
            </a>
        </li>-->
</ul>
</#macro>

<#-- in theory there is a transform that converts the selected menu to a proper list on these screens. It is never used by any of the other ofbiz screens, however and poorly documented
so for now we have to split the screens in half and rely on the menu widget renderer to get the same effect
<#macro currentAppMenu>
    <#if appModelMenu?has_content>
        <li class="has-dropdown not-click active"><a href="#">${title!"TEST"}</a>
            <ul class="dropdown">
                
            </ul>
        </li>
    </#if>
</#macro>-->

<#macro logoMenu hasLink=true isSmall=false>
    <#if layoutSettings.headerImageUrl??>
        <#assign headerImageUrl = layoutSettings.headerImageUrl>
    <#elseif layoutSettings.commonHeaderImageUrl??>
        <#assign headerImageUrl = layoutSettings.commonHeaderImageUrl>
    <#elseif layoutSettings.VT_HDR_IMAGE_URL??>
        <#assign headerImageUrl = layoutSettings.VT_HDR_IMAGE_URL.get(0)>
    </#if>
    <#if headerImageUrl??>
        <#if organizationLogoLinkURL?has_content>
            <#if hasLink><a href="<@ofbizUrl>${logoLinkURL}</@ofbizUrl>" class="navbar-brand"></#if><#if hasLink></a></#if>
            <#else><#if hasLink><a href="<@ofbizUrl>${logoLinkURL}</@ofbizUrl>" class="navbar-brand"></#if><#if hasLink></a></#if>
        </#if>
        <#else>
        <a href="<@ofbizUrl>${logoLinkURL!""}</@ofbizUrl>" class="navbar-brand"></a>
    </#if>
</#macro>

  <@scripts output=true> <#-- ensure @script elems here will always output -->

    <title>${layoutSettings.companyName}<#if title?has_content>: ${title}<#elseif titleProperty?has_content>: ${uiLabelMap[titleProperty]}</#if></title>
    
    <#if layoutSettings.shortcutIcon?has_content>
      <#assign shortcutIcon = layoutSettings.shortcutIcon/>
    <#elseif layoutSettings.VT_SHORTCUT_ICON?has_content>
      <#assign shortcutIcon = layoutSettings.VT_SHORTCUT_ICON.get(0)/>
    </#if>
    <#if shortcutIcon?has_content>
      <link rel="shortcut icon" href="<@ofbizContentUrl>${raw(shortcutIcon)}</@ofbizContentUrl>" />
    </#if>
    
    <#if layoutSettings.styleSheets?has_content>
        <#--layoutSettings.styleSheets is a list of style sheets. So, you can have a user-specified "main" style sheet, AND a component style sheet.-->
        <#list layoutSettings.styleSheets as styleSheet>
            <link rel="stylesheet" href="<@ofbizContentUrl>${raw(styleSheet)}</@ofbizContentUrl>" type="text/css"/>
        </#list>
    </#if>
    <#if layoutSettings.VT_STYLESHEET?has_content>
        <#list layoutSettings.VT_STYLESHEET as styleSheet>
            <link rel="stylesheet" href="<@ofbizContentUrl>${raw(styleSheet)}</@ofbizContentUrl>" type="text/css"/>
        </#list>
    </#if>
    <#if layoutSettings.rtlStyleSheets?has_content && langDir == "rtl">
        <#--layoutSettings.rtlStyleSheets is a list of rtl style sheets.-->
        <#list layoutSettings.rtlStyleSheets as styleSheet>
            <link rel="stylesheet" href="<@ofbizContentUrl>${raw(styleSheet)}</@ofbizContentUrl>" type="text/css"/>
        </#list>
    </#if>
    <#if layoutSettings.VT_RTL_STYLESHEET?has_content && langDir == "rtl">
        <#list layoutSettings.VT_RTL_STYLESHEET as styleSheet>
            <link rel="stylesheet" href="<@ofbizContentUrl>${raw(styleSheet)}</@ofbizContentUrl>" type="text/css" />
        </#list>
    </#if>
    
    <#-- VT_TOP_JAVASCRIPT must always come before all others and at top of document -->
    <#if layoutSettings.VT_TOP_JAVASCRIPT?has_content>
        <#assign javaScriptsSet = toSet(layoutSettings.VT_TOP_JAVASCRIPT)/>
        <#list layoutSettings.VT_TOP_JAVASCRIPT as javaScript>
            <#if javaScriptsSet.contains(javaScript)>
                <#assign nothing = javaScriptsSet.remove(javaScript)/>
                <@script src=makeOfbizContentUrl(javaScript) />
            </#if>
        </#list>
    </#if>

    <#-- VT_PRIO_JAVASCRIPT should come right before javaScripts (always move together with javaScripts) -->
    <#if layoutSettings.VT_PRIO_JAVASCRIPT?has_content>
        <#assign javaScriptsSet = toSet(layoutSettings.VT_PRIO_JAVASCRIPT)/>
        <#list layoutSettings.VT_PRIO_JAVASCRIPT as javaScript>
            <#if javaScriptsSet.contains(javaScript)>
                <#assign nothing = javaScriptsSet.remove(javaScript)/>
                <@script src=makeOfbizContentUrl(javaScript) />
            </#if>
        </#list>
    </#if>
    <#if layoutSettings.javaScripts?has_content>
        <#--layoutSettings.javaScripts is a list of java scripts. -->
        <#-- use a Set to make sure each javascript is declared only once, but iterate the list to maintain the correct order -->
        <#assign javaScriptsSet = toSet(layoutSettings.javaScripts)/>
        <#list layoutSettings.javaScripts as javaScript>
            <#if javaScriptsSet.contains(javaScript)>
                <#assign nothing = javaScriptsSet.remove(javaScript)/>
                <@script src=makeOfbizContentUrl(javaScript) />
            </#if>
        </#list>
    </#if>
    <#if layoutSettings.VT_HDR_JAVASCRIPT?has_content>
        <#assign javaScriptsSet = toSet(layoutSettings.VT_HDR_JAVASCRIPT)/>
        <#list layoutSettings.VT_HDR_JAVASCRIPT as javaScript>
            <#if javaScriptsSet.contains(javaScript)>
                <#assign nothing = javaScriptsSet.remove(javaScript)/>
                <@script src=makeOfbizContentUrl(javaScript) />
            </#if>
        </#list>
    </#if>
    <#if layoutSettings.VT_EXTRA_HEAD?has_content>
        <#list layoutSettings.VT_EXTRA_HEAD as extraHead>
            ${extraHead}
        </#list>
    </#if>
    <#if lastParameters??><#assign parametersURL = "&amp;" + lastParameters></#if>
    <#if layoutSettings.WEB_ANALYTICS?has_content>
      <@script>
        <#list layoutSettings.WEB_ANALYTICS as webAnalyticsConfig>
          ${raw(webAnalyticsConfig.webAnalyticsCode!)}
        </#list>
      </@script>
    </#if>

  </@scripts>
</head>
<#if layoutSettings.headerImageLinkUrl??>
  <#assign logoLinkURL = "${layoutSettings.headerImageLinkUrl}">
<#else>
  <#assign logoLinkURL = "${layoutSettings.commonHeaderImageLinkUrl}">
</#if>
<#assign organizationLogoLinkURL = "${layoutSettings.organizationLogoLinkUrl!}">
<body class="app header-fixed aside-menu-fixed sidebar-lg-show <#if activeApp?has_content>app-${activeApp}</#if><#if parameters._CURRENT_VIEW_?has_content> page-${parameters._CURRENT_VIEW_!}</#if> <#if userLogin??>sidebar-fixed page-auth<#else>page-noauth</#if>">
    <!-- Navigation -->
    <header class="app-header navbar">

        <#if userLogin?has_content>
            <!-- Brand and toggle get grouped for better mobile display -->
            <button class="navbar-toggler sidebar-toggler d-lg-none mr-auto" type="button" data-toggle="sidebar-show">
                <span class="navbar-toggler-icon"></span>
            </button>
        </#if>
        <@logoMenu isSmall=true/>

        <!-- Top Menu Items -->
        <ul class="nav navbar-nav navbar-left top-nav d-md-down-none">
            <#if userLogin??>
            <li class="nav-item">
                <button class="navbar-toggler sidebar-toggler d-md-down-none" type="button" data-toggle="sidebar-lg-show">
                    <span class="navbar-toggler-icon"></span>
                </button>
            </li>
            <li class="nav-item active">
               ${applicationTitle!}
            </li>
            <li class="nav-item p-x-1 dropdown ml-auto">
                <a href="javascript:;" class="nav-link dropdown-toggle nav-link" data-toggle="dropdown"><#--<i class="${styles.icon!} fa-dashboard"></i>-->${uiLabelMap["CommonPrimaryApps"]}</a>
                <ul id="menuPrimary" class="dropdown-menu">
                    <@primaryAppsMenu/>
                </ul>
            </li>
            <li class="nav-item p-x-1 dropdown ml-auto">
                <a href="javascript:;" class="nav-link dropdown-toggle nav-link" data-toggle="dropdown"><#--<i class="${styles.icon!} fa-desktop"></i>-->${uiLabelMap["CommonSecondaryApps"]}</a>
                <ul id="menuSecondary" class="dropdown-menu">
                    <@secondaryAppsMenu/>
                </ul>
            </li>
            </#if>
         </ul>

         <ul class="nav navbar-nav navbar-right ml-auto">
            <#if systemNotifications?has_content>
                  <li class="nav-item dropdown"><a href="#" class="nav-link dropdown-toggle" data-toggle="dropdown"><i class="${styles.icon!} ${styles.icon_prefix!}bell"></i><#if systemNotificationsCount?has_content> <span class="label label-primary">${systemNotificationsCount}</span> <b class="caret"></b> </#if></a>
                    <@notificationsMenu />
                  </li>
            </#if>
            <#-- Alerts
            <li class="dropdown">
                <a href="#" class="nav-link dropdown-toggle" data-toggle="dropdown"><i class="fa fa-bell"></i> <b class="caret"></b></a>
                <ul class="dropdown-menu alert-dropdown">
                    <li class="nav-item">
                        <a href="#">Alert Name <span class="label label-default">Alert Badge</span></a>
                    </li>
                    <li class="nav-item">
                        <a href="#">Alert Name <span class="label label-primary">Alert Badge</span></a>
                    </li>
                    <li class="nav-item">
                        <a href="#">Alert Name <span class="label label-success">Alert Badge</span></a>
                    </li>
                    <li class="nav-item">
                        <a href="#">Alert Name <span class="label label-info">Alert Badge</span></a>
                    </li>
                    <li class="nav-item">
                        <a href="#">Alert Name <span class="label label-warning">Alert Badge</span></a>
                    </li>
                    <li class="nav-item">
                        <a href="#">Alert Name <span class="label label-danger">Alert Badge</span></a>
                    </li>
                    <li class="divider"></li>
                    <li class="nav-item">
                        <a href="#">View All</a>
                    </li>
                </ul>
            </li>
            -->
            <li class="nav-item dropdown">
                <#if userLogin??><a href="#" class="nav-link dropdown-toggle" data-toggle="dropdown"><i class="fa fa-user"></i> ${userLogin.userLoginId} </a><#else><a href="<@ofbizUrl>${checkLoginUrl}</@ofbizUrl>" class="nav-link dropdown-toggle" data-toggle="dropdown">${uiLabelMap.CommonLogin}</a></#if>
                <ul class="dropdown-menu dropdown-menu-right">
                    <@generalMenu />
                </ul>
            </li>
        </ul>
        <#if userLogin?has_content>
            <button class="navbar-toggler aside-menu-toggler d-lg-none" type="button" data-toggle="aside-menu-show">
                <span class="navbar-toggler-icon"></span>
            </button>
        </#if>
     </header>
</@virtualSection>
