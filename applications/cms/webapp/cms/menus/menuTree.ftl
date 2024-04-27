<#include "component://cms/webapp/cms/common/common.ftl">

<#-- 
DEV NOTE: MOST OF OUR CODE CURRENTLY ASSUMES primaryPathFromContextRoot(Default)=Y
    This means by default we display /control prefix everywhere so ALL paths
    are relative to webapp context root, consistently.
    This is the simplest/best default because it allows the greatest flexibility with
    the simplest syntax (only a single path needed to express, no matter if page or request,
    always from webapp context root).
-->

<#-- Pre-configured Modals -->
<@modal id="delete-dialog">
    <@heading>${uiLabelMap.CommonWarning}</@heading>
    ${uiLabelMap.CmsConfirmDeleteAction}
    <div class="modal-footer ${styles.text_right}">
       <a id="delete-button" class="${styles.button} btn-ok">${uiLabelMap.CommonContinue}</a>
    </div>
</@modal>
<@modal id="edit-menuitem-dialog">
    <@heading>${uiLabelMap.CommonEdit}</@heading>
    <@form id="edit_menu_item">
        <@field type="select" name="type" id="type" label=uiLabelMap.CommonType onChange="toggle_editor_fields();">
               <option value="link_internal">${uiLabelMap.CmsLinkInternal}</option>
               <option value="link_external">${uiLabelMap.CmsLinkExternal}</option>
               <option value="content">${uiLabelMap.CmsContent}</option>
               <option value="link_catalog">${uiLabelMap.CmsLinkCatalog}</option>
        </@field>
        <div id="c_path_wrap" class="cms-menu-urlarg-wrap"><@field type="input" id="path" name="path" value="" label=uiLabelMap.CmsMenuItemLink placeholder="/main" required=false/></div>
        <div id="c_content_wrap" class="cms-menu-urlarg-wrap" style="display:none;"><@field type="textarea" id="content" class="+editor" name="content" value="" label=uiLabelMap.CmsContent placeholder="..." required=false/></div>
        <div id="c_caturlfmt_wrap" class="cms-menu-urlarg-wrap" style="display:none;">
            <@field type="select" id="catUrlFmt" name="catUrlFmt" label=uiLabelMap.CommonFormat>
                <@field type="option" value="">${getLabel('CommonDefault')}</@field>
                <@field type="option" value="alt">${getLabel('ProductContentType.description.ALTERNATIVE_URL', 'ProductEntityLabels')}</@field>
            </@field>
        </div>
        <div id="c_categoryid_wrap" class="cms-menu-urlarg-wrap" style="display:none;"><@field type="input" id="productCategoryId" name="productCategoryId" value="" label=rawLabel('ProductCategoryId', 'ProductUiLabels') required=false/></div>
        <div id="c_productid_wrap" class="cms-menu-urlarg-wrap" style="display:none;"><@field type="input" id="productId" name="productId" value="" label=rawLabel('ProductProductId', 'ProductUiLabels') required=false/></div>
        <#-- TODO?: more macro arguments for @catalogUrl/@catalogAltUrl -->
        <div class="modal-footer ${styles.text_right}">
            <@field type="submit" text=uiLabelMap.CommonUpdate class="${styles.link_run_sys!} ${styles.action_update!}" />
        </div>
    </@form>
</@modal>

<#-- Javascript functions -->
<#assign typesConfig={
            "link_internal": {
                "icon": "${styles.text_color_primary} ${styles.icon!} ${styles.icon_prefix!}page ${styles.icon_prefix!}file-o"
            },
            "link_external": {
                "icon": "${styles.text_color_primary} ${styles.icon!} ${styles.icon_prefix!}link"
            },
            "content": {
                "icon": "${styles.text_color_primary} ${styles.icon!} ${styles.icon_prefix!}file-text-o"
            },
            "link_catalog": {
                "icon": "${styles.text_color_primary} ${styles.icon!} ${styles.icon_prefix!}folder"
            }
}/>
<@script>
    function toggle_editor_fields(){
        $(".cms-menu-urlarg-wrap").hide();
        var linkType = $('#type').val();
        if (linkType == 'link_catalog') {
            $("#c_caturlfmt_wrap").show();
            $("#c_categoryid_wrap").show();
            $("#c_productid_wrap").show();
        } else if (linkType == 'content'){
            $("#c_content_wrap").show();
        }else{
            $("#c_path_wrap").show();
        }
    }

    function simple_encode_html(str){
        str = str.replace(/</g,"[lt];");
        str = str.replace(/>/g,"[gt];");
        return str;
    }

    function simple_decode_html(str){
        str = str.replace(/[lt];/g,"<");
        str = str.replace(/[gt];/g,">");
        return str;
    }

    var nodeIcons = {
        "link_internal" : "${styles.text_color_primary} ${styles.icon!} ${styles.icon_prefix!}page ${styles.icon_prefix!}file-o",
        "link_external" : "${styles.text_color_primary} ${styles.icon!} ${styles.icon_prefix!}link",
         "content" : "${styles.text_color_primary} ${styles.icon!} ${styles.icon_prefix!}file-text-o",
        "link_catalog" : "${styles.text_color_primary} ${styles.icon!} ${styles.icon_prefix!}folder"
    };
    var editorBaseUrl = '<@pageUrl escapeAs='js'>menus</@pageUrl>';
    var menuId='';
    var websiteId = 'cmsSite';
    var elData= {
                  text        : "New Link",
                  li_attr     : {},
                  a_attr      : {},
                  type        : "link_internal",
                  data        : {}
                };
    
    
    function open_dialog(dialogName){
        try {
                let modalElem = $('#'+dialogName);
                ${modalControl('modalElem','open')}
                toggle_editor_fields();
            } catch(err) {
            }
        return false;
    }
    
    function close_dialog(dialogName){
        try {
                let modalElem = $('#'+dialogName);
                ${modalControl('modalElem','close')}
            } catch(err) {
            }
        return false;
    }
    
    <#-- Functions to manage menu items -->
    function node_create(data,isParent) {
        var menuTree = $('#cms-menu-tree').jstree(true);
        var sel;
        if(isParent){
            sel = menuTree.create_node('#', data);
        }else{
            sel = menuTree.get_selected();
            if(!sel.length) { return false; }
            sel = sel[0];
            sel = menuTree.create_node(sel, data);
        }
        
        if(sel) {
            menuTree.edit(sel,null, function(){
               saveMenu();
               menuTree.deselect_all();
               menuTree.select_node(sel);
               node_edit();
            });
        }
    };


    
    function node_edit() {
        var menuTree = $('#cms-menu-tree').jstree(true);
        var sel = menuTree.get_selected();
        if(!sel.length) { return false; }
        sel = sel[0];
        var node = menuTree.get_node(sel);
        $('form#edit_menu_item')[0].reset();
        if(node){
            if(node["type"]){
                $('form#edit_menu_item #type').val(node["type"]).prop('selected', true);
            }
            if(node["data"] && node["data"]["path"]){
                $('form#edit_menu_item #path').val(node["data"]["path"]);
            }
            if(node["data"] && node["data"]["content"]){
                console.log(node["data"]["content"]);
                $('form#edit_menu_item #content').trumbowyg('html',simple_decode_html(node["data"]["content"]));
            }
            if(node["data"] && node["data"]["catUrlFmt"]){
                $('form#edit_menu_item #catUrlFmt').val(node["data"]["catUrlFmt"]);
            }
            if(node["data"] && node["data"]["productCategoryId"]){
                $('form#edit_menu_item #productCategoryId').val(node["data"]["productCategoryId"]);
            }
            if(node["data"] && node["data"]["productId"]){
                $('form#edit_menu_item #productId').val(node["data"]["productId"]);
            }
        }
        open_dialog('modal_edit-menuitem-dialog');
    };
    
    function node_update(){
        var menuTree = $('#cms-menu-tree').jstree(true);
        var path = $('form#edit_menu_item #path').val();
        var type = $('form#edit_menu_item #type').val();
        var content = $('form#edit_menu_item #content').trumbowyg('html');
        var catUrlFmt = $('form#edit_menu_item #catUrlFmt').val();
        var productCategoryId = $('form#edit_menu_item #productCategoryId').val();
        var productId = $('form#edit_menu_item #productId').val();
        var sel = menuTree.get_selected();
            if(!sel.length) { return false; }
            sel = sel[0];
        var node = menuTree.get_node(sel);
        if(node) {
            node["type"] = type;
            node["data"]["path"] = path;
            node["data"]["content"] = simple_encode_html(content);
            node["data"]["catUrlFmt"] = catUrlFmt;
            node["data"]["productCategoryId"] = productCategoryId;
            node["data"]["productId"] = productId;
            node["icon"] = nodeIcons[type];
            close_dialog('modal_edit-menuitem-dialog');
            saveMenu();
            menuTree.redraw(true);
        }
        return false;
    }
    
    function node_rename() {
        var menuTree = $('#cms-menu-tree').jstree(true);
        var sel = menuTree.get_selected();
        if(!sel.length) { return false; }
        sel = sel[0];
        menuTree.edit(sel,null, function(){
            saveMenu();
        });
    };
    function node_delete() {
        var menuTree = $('#cms-menu-tree').jstree(true);
        var sel = menuTree.get_selected();
        if(!sel.length) { return false; }
        menuTree.delete_node(sel);
        saveMenu();
    };
    
    function refreshMenuData(newData){
        var menuTree = $('#cms-menu-tree').jstree(true);
        menuTree.settings.core.data = newData;
        menuTree.refresh();
        return false;
    }
    
    <#-- Function to manage menus -->
    function addMenu(){
        var menuName = $('#menuname').val();
        var data = {menuName:menuName,menuJson:"[]"};
        $.ajax({
                  type: "POST",
                  url: "<@pageUrl escapeAs='js'>saveMenu</@pageUrl>",
                  data: data,
                  cache:false,
                  async:true,
                  success: function(data) { 
                        menuId = data.menuId;
                        $('#activeMenu').append($('<option>', { 
                            value: menuId,
                            text : menuName
                        }));
                        $("#activeMenu").val(menuId).prop('selected', true);
                        refreshMenuData(JSON.parse(data.menuJson));
                      }
            });
            close_dialog('modal_create-menu-dialog');
        return false;
    }
    function loadMenu(){
        menuId = $('#activeMenu').val();
        var data = {menuId:menuId};
        $.ajax({
                  type: "POST",
                  url: "<@pageUrl escapeAs='js'>getMenu</@pageUrl>",
                  data: data,
                  cache:false,
                  async:true,
                  success: function(data) { 
                        refreshMenuData(JSON.parse(data.menuJson));
                      }
            });
        return false;
    
    }
    
    function saveMenu() {
        var v =  $('#cms-menu-tree').jstree(true).get_json('',{flat:false});
        var menuJson = JSON.stringify(v);
        var data = {websiteId:websiteId,menuJson:menuJson};
        if(menuId){
            data.menuId=menuId;
        }
        $.ajax({
                  type: "POST",
                  url: "<@pageUrl escapeAs='js'>saveMenu</@pageUrl>",
                  data: data,
                  cache:false,
                  async:true,
                  success: function(data) { 
                        menuId = data.menuId;
                      }
            });
        return false;
    };
    
    function deleteMenu() {
            menuId = $('#activeMenu').val();
            var data = {menuId:menuId};
            $.ajax({
                  type: "POST",
                  url: "<@pageUrl escapeAs='js'>deleteMenu</@pageUrl>",
                  data: data,
                  cache:false,
                  async:true,
                  success: function(data) { 
                        $("#activeMenu option[value='"+menuId+"']").remove();
                        loadMenu();
                      }
            });
            close_dialog('modal_delete-dialog');
        }
    
    function makeLink($node,action) {
        var retStr="javascript:";
        switch(action) {
            case "create":
                retStr=retStr.concat("node_create(elData,false);");
                break;
            case "open":
                break;
            case "edit":
                 retStr=retStr.concat("node_rename();");
                break;
            case "remove":
                 retStr=retStr.concat("node_delete();");
                break; 
            case "update":
                 retStr=retStr.concat("node_edit();");
                break;    
            case "createroot":
                retStr=retStr.concat("node_create(elData,true);");
                break;       
            default:
        }
        retStr.concat("return false;");
        return retStr;
    }
    
    <#-- Function to update the action menu. Will generate new menu items based on selected option -->
    function updateMenu($node){
        var $el = $("#action_menu");

        var newOptions = {
          "${escapeVal(uiLabelMap.CommonCreate, 'js')}": makeLink($node,'create'),
          "${escapeVal(uiLabelMap.CommonEdit, 'js')}": makeLink($node,'update'),
          "${escapeVal(uiLabelMap.CommonRename, 'js')}": makeLink($node,'edit'),
          "${escapeVal(uiLabelMap.CommonRemove, 'js')}": makeLink($node,'remove')
        }; 
            
        $el.empty(); // remove old options
        $.each(newOptions, function(key,value) {
          var newEl = $('<@compress_single_line><@menuitem type="link" href="" text=""/></@compress_single_line>');
          var menuAnchor = $(newEl).find('a:last-child');
          menuAnchor.attr("href",value).text(key);
          $el.append(newEl);
        });
    }
    <@commonCmsScripts />
    
    <#-- Load after the site has been initialized -->
    $(function() {
        loadMenu();
        setupCmsDeleteActionHooks();
        
        $("#create_menu_form").submit(function(e){
            addMenu();
            return false;
        });
        
        $("#edit_menu_item").submit(function(e){
            node_update();
            return false;
        });
        
        
        <#-- Auto-save when menu positions are changed -->                                                        
        $('#cms-menu-tree').on('move_node.jstree', function (e, data) {
            saveMenu();
        });

        $('.editor').trumbowyg({
            autogrow: true,
            semantic: false,
            btnsDef: {
                // Customizables dropdowns
                image: {
                    dropdown: ['insertImage','scipio_media_image','upload','scipio_media_video','scipio_media_audio','scipio_media_file', 'base64', 'noEmbed'],
                    ico: 'insertImage'
                },
                link: {
                    dropdown: [
                        'createLink',
                        'unlink',
                        <#-- DEV NOTE: autourl tries to identify the macro in the given link and
                            open the right dialog; it falls back on createLink. but the ones below are
                            always needed also, in order to create new links.
                            also, autourl doesn't completely replace the stock 'createLink',
                            because some users may need/want to edit a cmsPageUrl or ofbizUrl using
                            the stock form instead of the helpers. -->
                        'scipio_links_autourl',
                        'scipio_links_cmspageurl',
                        'scipio_links_ofbizurl',
                        'scipio_links_ofbizcontenturl'
                    ]
                }
            },
            btns: [
                ['viewHTML'],
                ['formatting'],
                'btnGrp-semantic',
                ['superscript', 'subscript'],
                'btnGrp-justify',
                'btnGrp-lists',
                ['link'],
                ['image'],
                ['scipio_assets'],
                ['table'],
                ['horizontalRule'],
                ['removeformat'],
                ['fullscreen']
            ],
            plugins: {
                // Add imagur parameters to upload plugin
                scipio_media: {
                    serverPath: '<@pageUrl escapeAs='js'>getMediaFiles</@pageUrl>',
                    mediaUrl: '<@contentUrl escapeAs='js'>/cms/media</@contentUrl>'
                },
                scipio_links: {
                    getPagesServerPath: '<@pageUrl escapeAs='js'>getPages</@pageUrl>',
                    getWebSitesServerPath: '<@pageUrl escapeAs='js'>getCmsWebSites</@pageUrl>',
                    <#-- WARN: currentWebSiteId may become problem in future; see js source -->
                    currentWebSiteId: '${escapeVal(webSiteId!, 'js')}'
                },
                scipio_assets: {
                    getAssetTypesServerPath: '<@pageUrl escapeAs='js'>getAssetTypes</@pageUrl>',
                    getAssetsServerPath: '<@pageUrl escapeAs='js'>getAssets</@pageUrl>',
                    getAssetAttributesServerPath: '<@pageUrl escapeAs='js'>getAssetAttributes</@pageUrl>',
                    getWebSitesServerPath: '<@pageUrl escapeAs='js'>getCmsWebSites</@pageUrl>',
                    <#-- WARN: currentWebSiteId may become problem in future; see js source -->
                    currentWebSiteId: '${escapeVal(webSiteId!, 'js')}'
                }
            }
        });

    });    
   
</@script>
<#assign treeEvent={'select_node.jstree':'updateMenu(data.node);'}/>
<#assign menuEventScript>
function($node) {
        var labelCreate = "${escapeVal(uiLabelMap.CommonCreate, 'js')}";
        var labelOpen = "${escapeVal(uiLabelMap.CommonOpen, 'js')}";
        var labelOverride = "${escapeVal(uiLabelMap.CmsOverride, 'js')}";
        var createDef = {
            "separator_before": false,
            "separator_after": false,
            "label": "${escapeVal(uiLabelMap.CommonCreate, 'js')}",
            "action": function (obj) { 
                node_create(elData,false);
            }
        };
        var openDef = {
            "separator_before": false,
            "separator_after": false,
            "label": "${escapeVal(uiLabelMap.CommonOpen, 'js')}",
            "action":function (obj) {
                
            }
        };
        
        var renameDef = {
            "separator_before": false,
            "separator_after": false,
            "label": "${escapeVal(uiLabelMap.CommonRename, 'js')}",
            "action": function (obj) { 
               node_rename();
            }
        };  
        
       var updateDef = {
            "separator_before": true,
            "separator_after": true,
            "label": "${escapeVal(uiLabelMap.CommonEdit, 'js')}",
            "action": function (obj) { 
               node_edit();
            }
        };  
                               
        var removeDef = {
            "separator_before": false,
            "separator_after": false,
            "label": "${escapeVal(uiLabelMap.CommonRemove, 'js')}",
            "action": function (obj) { 
               node_delete();
            }
        };
        

        return {
            "Update": updateDef,
            "Open": openDef,
            "Create": createDef,
            "Rename": renameDef,
            "Remove": removeDef
        };

    }
</#assign>
<#assign pluginSettings={"items": wrapRawScript(menuEventScript)}/>
<#assign treePlugin =[{"name":"contextmenu", "settings":pluginSettings},{"name":"massload"},{"name":"dnd"},{"name":"types","settings": typesConfig}]/>

<#-- Content -->
<#macro menuContent menuArgs={}>
    <@menu args=menuArgs>
        <@field type="select" name="activeMenu" id="activeMenu" onChange="loadMenu();" label=uiLabelMap.CmsCurrentMenu>
            <#list cmsMenus as cmsMenu>
               <option value="${cmsMenu.menuId!""}">${cmsMenu.menuName!cmsMenu.menuId!""} (menuId: ${cmsMenu.menuId!""})</option>
            </#list>
        </@field>
        <@menuitem type="generic">
            <@modal id="create-menu-dialog" label=uiLabelMap.CmsWebSiteAddMenu linkClass="+${styles.menu_button_item_link!} ${styles.action_nav!} ${styles.action_add!}">
                <@heading>${uiLabelMap.CmsWebSiteAddMenu}</@heading>
                    <@form id="create_menu_form">
                        <@field type="input" id="menuname" name="menuName" value="" label=uiLabelMap.CmsMenuName placeholder="menu name" required=true/>
                        <div class="modal-footer ${styles.text_right}">
                            <@field type="submit" text=uiLabelMap.CommonAdd class="${styles.link_run_sys!} ${styles.action_add!}" />
                        </div>
                    </@form>
            </@modal>
        </@menuitem>
        <@menuitem type="link" href="javascript:deleteMenu(); void(0);" class="+${styles.action_run_sys!} ${styles.action_remove!} action_delete" text=uiLabelMap.CommonDelete/>
    </@menu>  
</#macro>

<@section menuContent=menuContent title=uiLabelMap.CmsMenus>
    <@row>
           <#-- JSTree displaying all content nodes -->
            <@cell columns=9>
                <@section title=uiLabelMap.CmsEditMenu>
                   <@treemenu type="lib-basic" events=treeEvent plugins=treePlugin id="cms-menu-tree">
                   </@treemenu>
                </@section>
            </@cell>
            
            <#-- empty side menu -->
            <@cell columns=3>
                <@section title=uiLabelMap.CmsMenu id="action_offset">
                        <ul class="side-nav" id="action_menu">
                            <@menuitem type="link" href="javascript:node_create(elData,true);" text=uiLabelMap.CommonCreate/>
                        </ul>
                </@section>
                <#--  Page list to enable drag & drop in between menus -->
                <@section title=uiLabelMap.CommonPages>
                   <#assign pageTreePlugin =[{"name":"massload"},{"name":"dnd"}]/>
                   <#assign pageTreeCallback>
                    function (operation, node, node_parent, node_position, more) {
                        if (preventDelete && operation=="delete_node") { 
                            preventDelete = false; 
                            return false; 
                        }
                        
                        return true;
                    }
                   </#assign>
                   <#assign pageTreeSettings = {
                            "multiple": false, <#-- TODO: in future could implement partial multiple operations (remove/move/copy) -->
                            "check_callback": wrapRawScript(pageTreeCallback)}/>
                   <@treemenu type="lib-basic" id="cms-content-tree" plugins=pageTreePlugin settings=pageTreeSettings> 
                        <#list requestMaps.keySet() as key>
                            <#if key?has_content && key!="noWebSiteId">
                                <#assign currWebsite= requestMaps[key]/>
                                <#assign treeWebsiteDisabled = false/>
                                <#if currWebsite.enabled?has_content && currWebsite.enabled=="false">
                                    <#assign treeWebsiteDisabled = true/>
                                </#if>
                                <#-- Add website -->
                                <@treeitem text=key state={"disabled": treeWebsiteDisabled} id=(currWebsite.id!key) parent=(currWebsite.parent!"#") 
                                    attribs={"data":{"type":"website","websiteid":currWebsite.webSiteId!"", 
                                        "editorRequestPathPrefix":currWebsite.editorRequestPathPrefix!"", "primaryPathFromContextRootDefault":currWebsite.primaryPathFromContextRootDefault!""}} 
                                    icon="${styles.text_color_secondary} ${styles.icon!} ${styles.icon_prefix!}folder"/>
                                <#if currWebsite.pages?has_content>
                                    <#-- Add requests and pages -->
                                    <#list currWebsite.pages?sort_by("text") as item>
                                        <#if ((item.data.type)!"")=="page">                                            
                                            <@treeitem attribs=item a_attr=itemPath icon="${styles.text_color_primary} ${styles.icon!} ${styles.icon_prefix!}page ${styles.icon_prefix!}file-o"/>
                                        <#elseif ((item.data.type)!"")=="request">
                                            <@treeitem attribs=item a_attr=itemPath   icon="${styles.text_color_info} ${styles.icon!} ${styles.icon_prefix!}link"/>
                                        <#else>
                                            <@treeitem attribs=item a_attr=itemPath/>
                                        </#if>
                                    </#list>
                                </#if>
                            </#if>
                       </#list>
                    </@treemenu>
                    <@script>                    
                        <#-- Function to stop removal of original node if copied over to second tree -->
                        var preventDelete = false;     
                        $(function() {   
                            $('#cms-menu-tree, #cms-content-tree').on('copy_node.jstree', function (e, data) {
                              if (data.is_multi) {
                                  preventDelete = true;  
                                  data.original.data.type="link_internal";
                                  data.node.type="link_internal";
                                  data.node.data = data.original.data;
                                  saveMenu();
                              }
                              
                            });
                        });    
                    </@script>
                    
                </@section>
                <@alert type="info">${uiLabelMap.CmsMenuDragndropInfo}</@alert>
                
                <#-- special message for root-aliasing of controller URIs - loaded by JS -->
                <div id="cms-ctrlrootalias-msgarea" class="cms-ctrlrootalias-msgarea" style="display:none;">
                    <@alert type="info"><span class="cms-ctrlrootalias-msg"></span></@alert>
                </div>
            </@cell>
            
            <@script>
                $(function() {
                    var $sidebar   = $("#action_offset"), 
                        $window    = $(window),
                        offset     = $sidebar.offset(),
                        topPadding = 50;
                    
                    $window.scroll(function() {
                        if ( $(window).width() > 1024) {
                            if ($window.scrollTop() > offset.top) {
                                $sidebar.stop().animate({
                                    marginTop: $window.scrollTop() - $sidebar.position().top + topPadding
                                });
                            } else {
                                $sidebar.stop().animate({
                                    marginTop: 0
                                });
                            }
                        }
                    });
            });
            </@script>
    </@row>
</@section>