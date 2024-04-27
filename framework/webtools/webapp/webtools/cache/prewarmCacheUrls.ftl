<#include "component://cms/webapp/cms/common/common.ftl">
<@script>
    function openSection(v){
        var divsToHide = document.getElementsByClassName("wb_entry");
        for(var i = 0; i < divsToHide.length; i++){
            divsToHide[i].style.display = "none";
        }
        document.getElementById('edit_'+v).style.display = "block";
        var codemirrorRefresh = document.getElementsByClassName("CodeMirror");
         for(var i = 0; i < codemirrorRefresh.length; i++){
            codemirrorRefresh[i].CodeMirror.refresh();
        }
    }

    window.addEventListener("load", function(){
        openSection(document.getElementById('scriptWebsiteId').value);
        var prtextEls = document.getElementsByClassName("wb_prtext");
        for(var i = 0; i < prtextEls.length; i++){
            var cwwp_code_editor = CodeMirror.fromTextArea(prtextEls[i], {
                lineNumbers: true,
                matchBrackets: true,
                mode: "text/plain",
                indentUnit: 4,
                placeholder: 'https://www.mydomain.com/...\n...\n',
                indentWithTabs: ${indentWithTabs?string},
                foldGutter: true,
                gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter"],
                extraKeys: {"Ctrl-Space": "autocomplete"}
            });
        }
    });

</@script>
<#macro menuContent menuArgs={}>
    <@menu args=menuArgs>
        <@menuitem type="link" href=makePageUrl("PrewarmCache") class="+${styles.action_nav!} ${styles.action_run!}" text="Prewarm Cache"/>
    </@menu>
</#macro>
<#if websites?has_content>
    <@section menuContent=menuContent>
        <@row>
            <@cell columns=6>
                <@form>
                    <@field type="select" label="WebSite" size="30" name="scriptWebsiteId" id="scriptWebsiteId" required=true events={"change":"openSection(this.value);"}>
                        <#list websites as website>
                            <option value="${(website.webSiteId)!}"<#if parameters.webSiteId?has_content && parameters.webSiteId==website.webSiteId> selected="selected"</#if>>${(website.siteName)!(website.webSiteId)!}</option>
                        </#list>
                    </@field>
                </@form>
            </@cell>
            <@cell columns=6>
                <@alert type="info">${getLabel("WebtoolsPrewarmCacheUrlDescription")}.</@alert>
            </@cell>
        </@row>
    </@section>
    <#list websites as website>
        <div id="edit_${website.webSiteId}" class="wb_entry" <#if !parameters.webSiteId?has_content || parameters.webSiteId != website.webSiteId>style="display:none"</#if>>
            <@form method="post" action=makePageUrl("UpdatePrewarmCacheUrls")+"?websiteId="+website.webSiteId>
                <@section title=website.siteName!website.webSiteId!"">
                    <input type="hidden" name="webSiteId" value="${website.webSiteId}" >
                    <@field type="textarea" class="wb_prtext" name="prewarmcache"
                    value=(parameters.description!) label="URLs" required=false>${website.prewarmcache!""}</@field>
                    <@field type="submit" text=uiLabelMap.CommonSave class="+${styles.link_run_sys!} ${styles.action_add!}"/>
                </@section>
            </@form>
        </div>
    </#list>
<#else>
    <@alert type="warming">No website entity found.</@alert>
</#if>
