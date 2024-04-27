<style>
.ws-ORDER_APPROVED{}
.ws-ORDER_PICKED{
    color: #999;
}
.ws-ORDER_PACKED{
    color: #eee;
    text-decoration:line-through;
}


.wsnew {
  animation: wsadded 0.8s ease forwards;
  opacity: 0;
  filter: blur(4px);
}

@keyframes wsadded {
   to {
     filter: blur(0);
     opacity:1;
   }
}

.wsupdate {
  animation: wsupdated 5s linear forwards;
}

@keyframes wsupdated {
    0%   {color: #cae0c4; }
    100%   {color: inherit; }
}

.wsremove {
  animation: wsremove 3s linear forwards;
}

@keyframes wsremove {
    to {
    opacity: 0;
    filter: blur(4px);
    display:none;
    }
}
</style>
<@script>
      var unlistedOrderIds = ['ORDER_REJECTED','ORDER_CANCELLED','ORDER_COMPLETED','ORDER_PACKED'];
      $(function(){
            var webSocket = new WebSocket('wss://' + window.location.host + '<@appUrl fullPath="false">/ws/orderdata/subscribe</@appUrl>');
            webSocket.onopen = function(event){
                var msg = {
                };
              webSocket.send(JSON.stringify(msg));
              let dt = $("#wsOrderDataTable").DataTable();
      };


            webSocket.onmessage = function(event){
                 var jsonObject, message;
                 var text = event.data;
                 message = text;
                  try {
                      jsonObject =  JSON.parse(text);
                      fillDataTable(jsonObject);
                    } catch (error) {
                        console.log(error);
                    }
            };
      });

   function fillDataTable(dataSet){
      if(document.readyState === "complete") {
       let dt = $("#wsOrderDataTable").DataTable();
       if(dataSet){
           let orderId = dataSet["orderId"];
            dt.rows().nodes().to$().removeClass('wsnew').removeClass('wsupdate');
            if(orderId && dt.row('#'+orderId).length > 0){
                if($.inArray(dataSet["statusId"],unlistedOrderIds)>=0){
                    dt.row('#'+orderId).scrollTo();
                    dt.row('#'+orderId)
                    .nodes()
                    .to$().addClass('wsremove');
                    setTimeout(function(){
                        dt.row('#'+orderId).remove();
                        $("#wsOrderDataTable").find('.ws-undefined').remove();
                        dt.draw();
                    },3000);
                }else{
                    dt.row('#'+orderId).data([
                        dataSet["status"],
                        orderId,
                        dataSet["orderDate"],
                        dataSet["customerPartyId"]
                        <#--,
                        dataSet["totalAmount"],
                        dataSet["totalQuantity"]-->
                    ])
                    .draw().nodes()
                    .to$()
                    .attr('id', orderId)
                    .addClass( 'wsupdate' )
                    .addClass("ws-"+dataSet["statusId"]);
                    dt.row('#'+orderId).scrollTo();
                }

            }else{
                if($.inArray(dataSet["statusId"],unlistedOrderIds)==-1){
                    dt.row.add([
                            dataSet["status"],
                            orderId,
                            dataSet["orderDate"],
                            dataSet["customerPartyId"]
                            <#--,
                            dataSet["totalQuantity"],
                            dataSet["totalAmount"]-->
                        ])
                        .draw().nodes()
                        .to$()
                        .attr('id', orderId)
                        .addClass( 'wsnew' )
                        .addClass("ws-"+dataSet["statusId"]);
                        dt.row('#'+orderId).scrollTo();
                }

            }
          }
      }
   }
</@script>

<#assign responsiveOptions = {
"fixedHeader" : true,
"info" : false,
"paging" : false,
"order" : [[3, "asc" ],[1, "desc" ]],
"scrollY": '30vh',
"scrollCollapse":  true,
"scroller": true
}/>


<@section title=title!"">
    <@table type="data-list" id="wsOrderDataTable" responsiveOptions=responsiveOptions autoAltRows=true scrollable=true responsive=true >
        <@thead>
            <@tr>
                <@th>${getLabel("CommonStatus",'CommonUiLabels')}</@th>
                <@th>${getLabel("ProductOrderId",'ProductUiLabels')}</@th>
                <@th>${getLabel('OrderDate','OrderUiLabels')}</@th>
                <@th>${getLabel("PartyPartyId",'PartyUiLabels')}</@th>
                <#--
                <@th>${getLabel("OrderGrandTotal",'OrderUiLabels')}</@th>
                <@th>${getLabel("OrderQuantity",'OrderUiLabels')}</@th>-->
            </@tr>
        </@thead>
        <@tbody>
            <#if orderList?has_content>
                <#list orderList as order>
                    <#assign orh = Static["org.ofbiz.order.order.OrderReadHelper"].getHelper(order)>
                    <#assign statusItem = order.getRelatedOneCache("StatusItem")>
                    <#assign displayParty = orh.getPlacingParty()?if_exists>
                    <#assign partyId = displayParty.partyId?default("_NA_")>
                    <@tr id=order.orderId!"">
                        <@td>${statusItem.get("description",locale)?default(statusItem.statusId?default("N/A"))}</@td>
                        <@td>${order.orderId!""}</@td>
                        <@td>${order.orderDate!""}</@td>
                        <@td>${partyId!""}</@td>
                    </@tr>
                </#list>
            </#if>
        </@tbody>
    </@table>
</@section>