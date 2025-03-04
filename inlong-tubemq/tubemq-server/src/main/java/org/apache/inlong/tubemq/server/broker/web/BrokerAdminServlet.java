/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.tubemq.server.broker.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import org.apache.inlong.tubemq.corebase.TokenConstants;
import org.apache.inlong.tubemq.corebase.rv.ProcessResult;
import org.apache.inlong.tubemq.corebase.utils.MixedUtils;
import org.apache.inlong.tubemq.corebase.utils.TStringUtils;
import org.apache.inlong.tubemq.corebase.utils.Tuple2;
import org.apache.inlong.tubemq.corebase.utils.Tuple3;
import org.apache.inlong.tubemq.server.broker.TubeBroker;
import org.apache.inlong.tubemq.server.broker.metadata.TopicMetadata;
import org.apache.inlong.tubemq.server.broker.msgstore.MessageStore;
import org.apache.inlong.tubemq.server.broker.msgstore.MessageStoreManager;
import org.apache.inlong.tubemq.server.broker.nodeinfo.ConsumerNodeInfo;
import org.apache.inlong.tubemq.server.broker.offset.OffsetService;
import org.apache.inlong.tubemq.server.broker.utils.GroupOffsetInfo;
import org.apache.inlong.tubemq.server.broker.utils.TopicPubStoreInfo;
import org.apache.inlong.tubemq.server.common.fielddef.WebFieldDef;
import org.apache.inlong.tubemq.server.common.utils.WebParameterUtils;

/***
 * Broker's web servlet. Used for admin operation, like query consumer's status etc.
 */
public class BrokerAdminServlet extends AbstractWebHandler {

    public BrokerAdminServlet(TubeBroker broker) {
        super(broker);
        registerWebApiMethod();
    }

    @Override
    public void registerWebApiMethod() {
        // query consumer group's offset
        innRegisterWebMethod("admin_query_group_offset",
                "adminQueryCurrentGroupOffSet", false);
        // query snapshot message
        innRegisterWebMethod("admin_snapshot_message",
                "adminQuerySnapshotMessageSet", false);
        // query broker's all consumer info
        innRegisterWebMethod("admin_query_broker_all_consumer_info",
                "adminQueryBrokerAllConsumerInfo", false);
        // get memory store status info
        innRegisterWebMethod("admin_query_broker_memstore_info",
                "adminGetMemStoreStatisInfo", false);
        // query broker's all message store info
        innRegisterWebMethod("admin_query_broker_all_store_info",
                "adminQueryBrokerAllMessageStoreInfo", false);
        // query consumer register info
        innRegisterWebMethod("admin_query_consumer_regmap",
                "adminQueryConsumerRegisterInfo", false);
        // manual set offset
        innRegisterWebMethod("admin_manual_set_current_offset",
                "adminManualSetCurrentOffSet", false);
        // get all registered methods
        innRegisterWebMethod("admin_get_methods",
                "adminQueryAllMethods", false);
        // query topic's publish info
        innRegisterWebMethod("admin_query_pubinfo",
                "adminQueryPubInfo", false);
        // Query all consumer groups booked on the Broker.
        innRegisterWebMethod("admin_query_group",
                "adminQueryBookedGroup", false);
        // query consumer group's offset
        innRegisterWebMethod("admin_query_offset",
                "adminQueryGroupOffSet", false);
        // clone consumer group's offset from source to target
        innRegisterWebMethod("admin_clone_offset",
                "adminCloneGroupOffSet", false);
        // set or update group's offset info
        innRegisterWebMethod("admin_set_offset",
                "adminSetGroupOffSet", false);
        // remove group's offset info
        innRegisterWebMethod("admin_rmv_offset",
                "adminRemoveGroupOffSet", false);
    }

    public void adminQueryAllMethods(HttpServletRequest req,
                                     StringBuilder sBuilder) {
        int index = 0;
        Set<String> methods = getSupportedMethod();
        sBuilder.append("{\"result\":true,\"errCode\":0,\"errMsg\":\"Success!\",\"dataSet\":[");
        for (String method : methods) {
            if (index++ > 0) {
                sBuilder.append(",");
            }
            sBuilder.append("{\"id\":").append(index)
                    .append(",\"method\":\"").append(method).append("\"}");
        }
        sBuilder.append("],\"totalCnt\":").append(index).append("}");
    }

    /***
     * Query broker's all consumer info.
     *
     * @param req
     * @param sBuilder process result
     * @throws Exception
     */
    public void adminQueryBrokerAllConsumerInfo(HttpServletRequest req,
                                                StringBuilder sBuilder) {
        int index = 0;
        ProcessResult result = new ProcessResult();
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.COMPSGROUPNAME, false, null, sBuilder, result)) {
            WebParameterUtils.buildFailResult(sBuilder, result.getErrMsg());
            return;
        }
        Set<String> groupNameSet = (Set<String>) result.getRetData();
        sBuilder.append("{\"result\":true,\"errCode\":0,\"errMsg\":\"Success!\",\"dataSet\":[");
        Map<String, ConsumerNodeInfo> map =
                broker.getBrokerServiceServer().getConsumerRegisterMap();
        for (Entry<String, ConsumerNodeInfo> entry : map.entrySet()) {
            if (TStringUtils.isBlank(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            String[] partitionIdArr =
                    entry.getKey().split(TokenConstants.ATTR_SEP);
            String groupName = partitionIdArr[0];
            if (!groupNameSet.isEmpty() && !groupNameSet.contains(groupName)) {
                continue;
            }
            String topicName = partitionIdArr[1];
            int partitionId = Integer.parseInt(partitionIdArr[2]);
            String consumerId = entry.getValue().getConsumerId();
            boolean ifFilterConsume = entry.getValue().isFilterConsume();
            if (index++ > 0) {
                sBuilder.append(",");
            }
            sBuilder.append("{\"index\":").append(index).append(",\"groupName\":\"")
                    .append(groupName).append("\",\"topicName\":\"").append(topicName)
                    .append("\",\"partitionId\":").append(partitionId);
            Long regTime =
                    broker.getBrokerServiceServer().getConsumerRegisterTime(consumerId, entry.getKey());
            if (regTime == null || regTime <= 0) {
                sBuilder.append(",\"consumerId\":\"").append(consumerId)
                        .append("\",\"isRegOk\":false")
                        .append(",\"isFilterConsume\":")
                        .append(ifFilterConsume);
            } else {
                sBuilder.append(",\"consumerId\":\"").append(consumerId)
                        .append("\",\"isRegOk\":true,\"lastRegTime\":")
                        .append(regTime).append(",\"isFilterConsume\":")
                        .append(ifFilterConsume);
            }
            sBuilder.append(",\"qryPriorityId\":").append(entry.getValue().getQryPriorityId())
                    .append(",\"curDataLimitInM\":").append(entry.getValue().getCurFlowCtrlLimitSize())
                    .append(",\"curFreqLimit\":").append(entry.getValue().getCurFlowCtrlFreqLimit())
                    .append(",\"totalSentSec\":").append(entry.getValue().getSentMsgSize())
                    .append(",\"isSupportLimit\":").append(entry.getValue().isSupportLimit())
                    .append(",\"sentUnitSec\":").append(entry.getValue().getTotalUnitSec())
                    .append(",\"totalSentMin\":").append(entry.getValue().getTotalUnitMin())
                    .append(",\"sentUnit\":").append(entry.getValue().getSentUnit());
            MessageStoreManager storeManager = broker.getStoreManager();
            OffsetService offsetService = broker.getOffsetManager();
            MessageStore store = null;
            try {
                store = storeManager.getOrCreateMessageStore(topicName, partitionId);
            } catch (Throwable e) {
                //
            }
            if (store == null) {
                sBuilder.append(",\"isMessageStoreOk\":false}");
            } else {
                long tmpOffset = offsetService.getTmpOffset(groupName, topicName, partitionId);
                long minDataOffset = store.getDataMinOffset();
                long maxDataOffset = store.getDataMaxOffset();
                long minPartOffset = store.getIndexMinOffset();
                long maxPartOffset = store.getIndexMaxOffset();
                long zkOffset = offsetService.getOffset(groupName, topicName, partitionId);
                sBuilder.append(",\"isMessageStoreOk\":true,\"tmpOffset\":").append(tmpOffset)
                        .append(",\"minOffset\":").append(minPartOffset)
                        .append(",\"maxOffset\":").append(maxPartOffset)
                        .append(",\"zkOffset\":").append(zkOffset)
                        .append(",\"minDataOffset\":").append(minDataOffset)
                        .append(",\"maxDataOffset\":").append(maxDataOffset).append("}");
            }
        }
        sBuilder.append("],\"totalCnt\":").append(index).append("}");
    }

    /***
     * Query broker's all message store info.
     *
     * @param req
     * @param sBuilder process result
     * @throws Exception
     */
    public void adminQueryBrokerAllMessageStoreInfo(HttpServletRequest req,
                                                    StringBuilder sBuilder) {
        ProcessResult result = new ProcessResult();
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.COMPSTOPICNAME, false, null, sBuilder, result)) {
            WebParameterUtils.buildFailResult(sBuilder, result.getErrMsg());
            return;
        }
        Set<String> topicNameSet = (Set<String>) result.getRetData();
        sBuilder.append("{\"result\":true,\"errCode\":0,\"errMsg\":\"Success!\",\"dataSet\":[");
        Map<String, ConcurrentHashMap<Integer, MessageStore>> messageTopicStores =
                broker.getStoreManager().getMessageStores();
        int index = 0;
        int recordId = 0;
        for (Map.Entry<String, ConcurrentHashMap<Integer, MessageStore>> entry : messageTopicStores.entrySet()) {
            if (TStringUtils.isBlank(entry.getKey())
                    || (!topicNameSet.isEmpty() && !topicNameSet.contains(entry.getKey()))) {
                continue;
            }
            if (recordId++ > 0) {
                sBuilder.append(",");
            }
            index = 0;
            sBuilder.append("{\"index\":").append(recordId).append(",\"topicName\":\"")
                    .append(entry.getKey()).append("\",\"storeInfo\":[");
            ConcurrentHashMap<Integer, MessageStore> partStoreMap = entry.getValue();
            if (partStoreMap != null) {
                for (Entry<Integer, MessageStore> subEntry : partStoreMap.entrySet()) {
                    MessageStore msgStore = subEntry.getValue();
                    if (msgStore == null) {
                        continue;
                    }
                    if (index++ > 0) {
                        sBuilder.append(",");
                    }
                    int numPartId = msgStore.getPartitionNum();
                    sBuilder.append("{\"storeId\":").append(subEntry.getKey())
                            .append(",\"numPartition\":").append(numPartId)
                            .append(",\"minDataOffset\":").append(msgStore.getDataMinOffset())
                            .append(",\"maxDataOffset\":").append(msgStore.getDataMaxOffset())
                            .append(",\"sizeInBytes\":").append(msgStore.getDataStoreSize())
                            .append(",\"partitionInfo\":[");
                    for (int partitionId = 0; partitionId < numPartId; partitionId++) {
                        if (partitionId > 0) {
                            sBuilder.append(",");
                        }
                        sBuilder.append("{\"partitionId\":").append(partitionId)
                                .append(",\"minOffset\":").append(msgStore.getIndexMinOffset())
                                .append(",\"maxOffset\":").append(msgStore.getIndexMaxOffset())
                                .append(",\"sizeInBytes\":").append(msgStore.getIndexStoreSize())
                                .append("}");
                    }
                    sBuilder.append("]}");
                }
            }
            sBuilder.append("]}");
        }
        sBuilder.append("],\"totalCnt\":").append(recordId).append("}");
    }

    /***
     * Get memory store status info.
     *
     * @param req
     * @param sBuffer process result
     * @throws Exception
     */
    public void adminGetMemStoreStatisInfo(HttpServletRequest req,
                                           StringBuilder sBuffer) {
        ProcessResult result = new ProcessResult();
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.COMPSTOPICNAME, false, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        Set<String> topicNameSet = (Set<String>) result.getRetData();
        if (!WebParameterUtils.getBooleanParamValue(req,
                WebFieldDef.NEEDREFRESH, false, false, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        boolean requireRefresh = (boolean) result.getRetData();
        sBuffer.append("{\"result\":true,\"errCode\":0,\"errMsg\":\"Success!\",\"detail\":[");
        Map<String, ConcurrentHashMap<Integer, MessageStore>> messageTopicStores =
                broker.getStoreManager().getMessageStores();
        int index = 0;
        int recordId = 0;
        for (Map.Entry<String, ConcurrentHashMap<Integer, MessageStore>> entry : messageTopicStores.entrySet()) {
            if (TStringUtils.isBlank(entry.getKey())
                    || (!topicNameSet.isEmpty() && !topicNameSet.contains(entry.getKey()))) {
                continue;
            }
            String topicName = entry.getKey();
            if (recordId++ > 0) {
                sBuffer.append(",");
            }
            index = 0;
            sBuffer.append("{\"topicName\":\"").append(topicName).append("\",\"storeStatisInfo\":[");
            ConcurrentHashMap<Integer, MessageStore> partStoreMap = entry.getValue();
            if (partStoreMap != null) {
                for (Entry<Integer, MessageStore> subEntry : partStoreMap.entrySet()) {
                    MessageStore msgStore = subEntry.getValue();
                    if (msgStore == null) {
                        continue;
                    }
                    if (index++ > 0) {
                        sBuffer.append(",");
                    }
                    sBuffer.append("{\"storeId\":").append(subEntry.getKey())
                            .append(",\"memStatis\":").append(msgStore.getCurMemMsgSizeStatisInfo(requireRefresh))
                            .append(",\"fileStatis\":")
                            .append(msgStore.getCurFileMsgSizeStatisInfo(requireRefresh)).append("}");
                }
            }
            sBuffer.append("]}");
        }
        sBuffer.append("],\"totalCount\":").append(recordId).append("}");
    }

    /***
     * Manual set offset.
     *
     * @param req
     * @param sBuffer process result
     * @throws Exception
     */
    public void adminManualSetCurrentOffSet(HttpServletRequest req,
                                            StringBuilder sBuffer) {
        ProcessResult result = new ProcessResult();
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.TOPICNAME, true, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        final String topicName = (String) result.getRetData();
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.GROUPNAME, true, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        final String groupName = (String) result.getRetData();
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.MODIFYUSER, true, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        final String modifyUser = (String) result.getRetData();
        if (!WebParameterUtils.getIntParamValue(req,
                WebFieldDef.PARTITIONID, true, -1, 0, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        int partitionId = (Integer) result.getRetData();
        if (!WebParameterUtils.getLongParamValue(req,
                WebFieldDef.MANUALOFFSET, true, -1, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        final long manualOffset = (Long) result.getRetData();
        List<String> topicList = broker.getMetadataManager().getTopics();
        if (!topicList.contains(topicName)) {
            sBuffer.append("{\"result\":false,\"errCode\":400,\"errMsg\":\"")
                    .append("Invalid parameter: not found the topicName configure!")
                    .append("\"}");
            return;
        }
        MessageStoreManager storeManager = broker.getStoreManager();
        MessageStore store = null;
        try {
            store = storeManager.getOrCreateMessageStore(topicName, partitionId);
        } catch (Throwable e) {
            //
        }
        if (store == null) {
            sBuffer.append("{\"result\":false,\"errCode\":400,\"errMsg\":\"")
                    .append("Invalid parameter: not found the store by topicName!")
                    .append("\"}");
            return;
        }
        if (manualOffset < store.getIndexMinOffset()) {
            sBuffer.append("{\"result\":false,\"errCode\":400,\"errMsg\":\"")
                    .append("Invalid parameter: manualOffset lower than Current MinOffset:(")
                    .append(manualOffset).append("<").append(store.getIndexMinOffset())
                    .append(")\"}");
            return;
        }
        if (manualOffset > store.getIndexMaxOffset()) {
            sBuffer.append("{\"result\":false,\"errCode\":400,\"errMsg\":\"")
                    .append("Invalid parameter: manualOffset bigger than Current MaxOffset:(")
                    .append(manualOffset).append(">").append(store.getIndexMaxOffset())
                    .append(")\"}");
            return;
        }
        OffsetService offsetService = broker.getOffsetManager();
        long oldOffset =
                offsetService.resetOffset(store, groupName,
                        topicName, partitionId, manualOffset, modifyUser);
        if (oldOffset < 0) {
            sBuffer.append("{\"result\":false,\"errCode\":401,\"errMsg\":\"")
                    .append("Manual update current Offset failure!")
                    .append("\"}");
        } else {
            sBuffer.append("{\"result\":true,\"errCode\":0,\"errMsg\":\"")
                    .append("Manual update current Offset success!")
                    .append("\",\"oldOffset\":").append(oldOffset).append("}");
        }
    }

    /***
     * Query snapshot message set.
     *
     * @param req
     * @param sBuffer process result
     * @throws Exception
     */
    public void adminQuerySnapshotMessageSet(HttpServletRequest req,
                                             StringBuilder sBuffer) throws Exception {
        ProcessResult result = new ProcessResult();
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.TOPICNAME, true, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        final String topicName = (String) result.getRetData();
        if (!WebParameterUtils.getIntParamValue(req,
                WebFieldDef.PARTITIONID, false, -1, 0, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        final int partitionId = (Integer) result.getRetData();
        if (!WebParameterUtils.getIntParamValue(req,
                WebFieldDef.MSGCOUNT, false, 3, 3, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        int msgCount = (Integer) result.getRetData();
        msgCount = Math.max(msgCount, 1);
        if (msgCount > 50) {
            sBuffer.append("{\"result\":false,\"errCode\":400,\"errMsg\":\"")
                    .append("Over max allowed msgCount value, allowed count is 50!")
                    .append("\"}");
            return;
        }
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.FILTERCONDS, false, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        Set<String> filterCondStrSet = (Set<String>) result.getRetData();
        broker.getBrokerServiceServer()
                .getMessageSnapshot(topicName, partitionId, msgCount, filterCondStrSet, sBuffer);
    }

    /***
     * Query consumer group offset.
     *
     * @param req
     * @param sBuffer process result
     * @throws Exception
     */
    public void adminQueryCurrentGroupOffSet(HttpServletRequest req,
                                             StringBuilder sBuffer) {
        ProcessResult result = new ProcessResult();
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.TOPICNAME, true, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        final String topicName = (String) result.getRetData();
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.GROUPNAME, true, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        final String groupName = (String) result.getRetData();
        if (!WebParameterUtils.getIntParamValue(req,
                WebFieldDef.PARTITIONID, true, -1, 0, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        int partitionId = (Integer) result.getRetData();
        if (!WebParameterUtils.getBooleanParamValue(req,
                WebFieldDef.REQUIREREALOFFSET, false, false, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        final boolean requireRealOffset = (Boolean) result.getRetData();
        List<String> topicList = broker.getMetadataManager().getTopics();
        if (!topicList.contains(topicName)) {
            sBuffer.append("{\"result\":false,\"errCode\":400,\"errMsg\":\"")
                    .append("Invalid parameter: not found the topicName configure!")
                    .append("\"}");
            return;
        }
        MessageStoreManager storeManager = broker.getStoreManager();
        OffsetService offsetService = broker.getOffsetManager();
        MessageStore store = null;
        try {
            store = storeManager.getOrCreateMessageStore(topicName, partitionId);
        } catch (Throwable e) {
            //
        }
        if (store == null) {
            sBuffer.append("{\"result\":false,\"errCode\":400,\"errMsg\":\"")
                    .append("Invalid parameter: not found the store by topicName!")
                    .append("\"}");
            return;
        }
        long tmpOffset = offsetService.getTmpOffset(groupName, topicName, partitionId);
        long minDataOffset = store.getDataMinOffset();
        long maxDataOffset = store.getDataMaxOffset();
        long minPartOffset = store.getIndexMinOffset();
        long maxPartOffset = store.getIndexMaxOffset();
        sBuffer.append("{\"result\":true,\"errCode\":0,\"errMsg\":\"")
                .append("OK!")
                .append("\",\"tmpOffset\":").append(tmpOffset)
                .append(",\"minOffset\":").append(minPartOffset)
                .append(",\"maxOffset\":").append(maxPartOffset)
                .append(",\"minDataOffset\":").append(minDataOffset)
                .append(",\"maxDataOffset\":").append(maxDataOffset);
        if (requireRealOffset) {
            long curReadDataOffset = -2;
            long curRdDltDataOffset = -2;
            long zkOffset = offsetService.getOffset(groupName, topicName, partitionId);
            String queryKey =
                    groupName + TokenConstants.ATTR_SEP + topicName + TokenConstants.ATTR_SEP + partitionId;
            ConsumerNodeInfo consumerNodeInfo = broker.getConsumerNodeInfo(queryKey);
            if (consumerNodeInfo != null) {
                curReadDataOffset = consumerNodeInfo.getLastDataRdOffset();
                curRdDltDataOffset = curReadDataOffset < 0 ? -2 : maxDataOffset - curReadDataOffset;
            }
            if (curReadDataOffset < 0) {
                sBuffer.append(",\"zkOffset\":").append(zkOffset)
                        .append(",\"curReadDataOffset\":-1,\"curRdDltDataOffset\":-1");
            } else {
                sBuffer.append(",\"zkOffset\":").append(zkOffset)
                        .append(",\"curReadDataOffset\":").append(curReadDataOffset)
                        .append(",\"curRdDltDataOffset\":").append(curRdDltDataOffset);
            }
        }
        sBuffer.append("}");
    }

    public void adminQueryConsumerRegisterInfo(HttpServletRequest req,
                                               StringBuilder sBuffer) {
        Map<String, ConsumerNodeInfo> map =
                broker.getBrokerServiceServer().getConsumerRegisterMap();
        int totalCnt = 0;
        sBuffer.append("{\"result\":true,\"errCode\":0,\"errMsg\":\"Success!\",\"dataSet\":[");
        for (Entry<String, ConsumerNodeInfo> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (totalCnt++ > 0) {
                sBuffer.append(",");
            }
            sBuffer.append("{\"Partition\":\"").append(entry.getKey())
                    .append("\",\"Consumer\":\"")
                    .append(entry.getValue().getConsumerId())
                    .append("\",\"index\":").append(totalCnt).append("}");
        }
        sBuffer.append("],\"totalCnt\":").append(totalCnt).append("}");
    }

    /***
     * Query topic's publish info on the Broker.
     *
     * @param req
     * @param sBuffer process result
     */
    public void adminQueryPubInfo(HttpServletRequest req,
                                  StringBuilder sBuffer) {
        ProcessResult result = new ProcessResult();
        // get the topic set to be queried
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.COMPSTOPICNAME, false, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        // get target consume group name
        Set<String> topicSet = (Set<String>) result.getRetData();
        // get topic's publish info
        Map<String, Map<Integer, TopicPubStoreInfo>> topicStorePubInfoMap =
                broker.getStoreManager().getTopicPublishInfos(topicSet);
        // builder result
        int totalCnt = 0;
        sBuffer.append("{\"result\":true,\"errCode\":0,\"errMsg\":\"Success!\",\"dataSet\":[");
        for (Map.Entry<String, Map<Integer, TopicPubStoreInfo>> entry
                : topicStorePubInfoMap.entrySet()) {
            if (totalCnt++ > 0) {
                sBuffer.append(",");
            }
            sBuffer.append("{\"topicName\":\"").append(entry.getKey())
                    .append("\",\"offsetInfo\":[");
            Map<Integer, TopicPubStoreInfo> storeInfoMap = entry.getValue();
            int itemCnt = 0;
            for (Map.Entry<Integer, TopicPubStoreInfo> entry1 : storeInfoMap.entrySet()) {
                if (itemCnt++ > 0) {
                    sBuffer.append(",");
                }
                TopicPubStoreInfo pubStoreInfo = entry1.getValue();
                pubStoreInfo.buildPubStoreInfo(sBuffer);
            }
            sBuffer.append("],\"itemCount\":").append(itemCnt).append("}");
        }
        sBuffer.append("],\"dataCount\":").append(totalCnt).append("}");
    }

    /***
     * Query all consumer groups booked on the Broker.
     *
     * @param req
     * @param sBuffer process result
     */
    public void adminQueryBookedGroup(HttpServletRequest req,
                                      StringBuilder sBuffer) {
        // get divide info
        ProcessResult result = new ProcessResult();
        if (!WebParameterUtils.getBooleanParamValue(req,
                WebFieldDef.WITHDIVIDE, false, false, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        boolean withDivide = (boolean) result.getRetData();
        // get offset service
        int itemCnt = 0;
        int totalCnt = 0;
        OffsetService offsetService = broker.getOffsetManager();
        sBuffer.append("{\"result\":true,\"errCode\":0,\"errMsg\":\"Success!\",\"dataSet\":[");
        if (withDivide) {
            // query in-memory group name set
            Set<String> onlineGroups = offsetService.getInMemoryGroups();
            sBuffer.append("{\"type\":\"in-cache\",\"groupName\":[");
            for (String group : onlineGroups) {
                if (itemCnt++ > 0) {
                    sBuffer.append(",");
                }
                sBuffer.append("\"").append(group).append("\"");
            }
            sBuffer.append("],\"groupCount\":").append(itemCnt).append("}");
            totalCnt++;
            sBuffer.append(",");
            // query in-zk group name set
            itemCnt = 0;
            Set<String> onZKGroup = offsetService.getUnusedGroupInfo();
            sBuffer.append("{\"type\":\"in-zk\",\"groupName\":[");
            for (String group : onZKGroup) {
                if (itemCnt++ > 0) {
                    sBuffer.append(",");
                }
                sBuffer.append("\"").append(group).append("\"");
            }
            sBuffer.append("],\"groupCount\":").append(itemCnt).append("}");
            totalCnt++;
        } else {
            Set<String> allGroups = offsetService.getBookedGroups();
            sBuffer.append("{\"type\":\"all\",\"groupName\":[");
            for (String group : allGroups) {
                if (itemCnt++ > 0) {
                    sBuffer.append(",");
                }
                sBuffer.append("\"").append(group).append("\"");
            }
            sBuffer.append("],\"groupCount\":").append(itemCnt).append("}");
            totalCnt++;
        }
        sBuffer.append("],\"dataCount\":").append(totalCnt).append("}");
    }

    /***
     * Query consumer group offset.
     *
     * @param req
     * @param sBuffer process result
     */
    public void adminQueryGroupOffSet(HttpServletRequest req,
                                      StringBuilder sBuffer) {
        ProcessResult result = new ProcessResult();
        // get group list
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.COMPSGROUPNAME, false, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        Set<String> inGroupNameSet = (Set<String>) result.getRetData();
        // get the topic set to be queried
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.COMPSTOPICNAME, false, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        // get target consume group name
        Set<String> topicSet = (Set<String>) result.getRetData();
        // filter invalid groups
        Set<String> qryGroupNameSet = new HashSet<>();
        Set<String> bookedGroupSet = broker.getOffsetManager().getBookedGroups();
        if (inGroupNameSet.isEmpty()) {
            qryGroupNameSet = bookedGroupSet;
        } else {
            for (String group : inGroupNameSet) {
                if (bookedGroupSet.contains(group)) {
                    qryGroupNameSet.add(group);
                }
            }
        }
        // verify the acquired Topic set and
        //   query the corresponding offset information
        Map<String, Map<String, Map<Integer, GroupOffsetInfo>>> groupOffsetMaps =
                getGroupOffsetInfo(WebFieldDef.COMPSGROUPNAME, qryGroupNameSet, topicSet);
        // builder result
        int totalCnt = 0;
        sBuffer.append("{\"result\":true,\"errCode\":0,\"errMsg\":\"Success!\",\"dataSet\":[");
        for (Map.Entry<String, Map<String, Map<Integer, GroupOffsetInfo>>> entry
                : groupOffsetMaps.entrySet()) {
            if (totalCnt++ > 0) {
                sBuffer.append(",");
            }
            Map<String, Map<Integer, GroupOffsetInfo>> topicPartMap = entry.getValue();
            sBuffer.append("{\"groupName\":\"").append(entry.getKey())
                    .append("\",\"subInfo\":[");
            int topicCnt = 0;
            for (Map.Entry<String, Map<Integer, GroupOffsetInfo>> entry1 : topicPartMap.entrySet()) {
                if (topicCnt++ > 0) {
                    sBuffer.append(",");
                }
                Map<Integer, GroupOffsetInfo> partOffMap = entry1.getValue();
                sBuffer.append("{\"topicName\":\"").append(entry1.getKey())
                        .append("\",\"offsets\":[");
                int partCnt = 0;
                for (Map.Entry<Integer, GroupOffsetInfo> entry2 : partOffMap.entrySet()) {
                    if (partCnt++ > 0) {
                        sBuffer.append(",");
                    }
                    GroupOffsetInfo offsetInfo = entry2.getValue();
                    offsetInfo.buildOffsetInfo(sBuffer);
                }
                sBuffer.append("],\"partCount\":").append(partCnt).append("}");
            }
            sBuffer.append("],\"topicCount\":").append(topicCnt).append("}");
        }
        sBuffer.append("],\"totalCnt\":").append(totalCnt).append("}");
    }

    /***
     * Add or Modify consumer group offset.
     *
     * @param req
     * @param sBuffer process result
     */
    public void adminSetGroupOffSet(HttpServletRequest req,
                                    StringBuilder sBuffer) {
        ProcessResult result = new ProcessResult();
        // get group list
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.COMPSGROUPNAME, true, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        final Set<String> groupNameSet = (Set<String>) result.getRetData();
        // get set mode
        if (!WebParameterUtils.getBooleanParamValue(req,
                WebFieldDef.MANUALSET, true, false, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        boolean manualSet = (Boolean) result.getRetData();
        // get modify user
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.MODIFYUSER, true, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        List<Tuple3<String, Integer, Long>> resetOffsets;
        final String modifier = (String) result.getRetData();
        if (manualSet) {
            // get offset json info
            if (!WebParameterUtils.getJsonDictParamValue(req,
                    WebFieldDef.OFFSETJSON, true, null, result)) {
                WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
                return;
            }
            Map<String, Long> manOffsets =
                    (Map<String, Long>) result.getRetData();
            // valid and transfer offset format
            if (!validManOffsetResetInfo(WebFieldDef.OFFSETJSON, manOffsets, result)) {
                WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
                return;
            }
            resetOffsets =
                    (List<Tuple3<String, Integer, Long>>) result.getRetData();
        } else {
            // get the topic set to be set
            if (!WebParameterUtils.getStringParamValue(req,
                    WebFieldDef.COMPSTOPICNAME, true, null, sBuffer, result)) {
                WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
                return;
            }
            Set<String> topicSet = (Set<String>) result.getRetData();
            // transfer offset format
            resetOffsets = buildOffsetResetInfo(topicSet);
        }
        broker.getOffsetManager().modifyGroupOffset(groupNameSet, resetOffsets, modifier);
        sBuffer.append("{\"result\":true,\"errCode\":0,\"errMsg\":\"OK\"}");
    }

    /***
     * Clone consume group offset, clone A group's offset to other group.
     *
     * @param req
     * @param sBuffer process result
     */
    public void adminCloneGroupOffSet(HttpServletRequest req,
                                      StringBuilder sBuffer) {
        ProcessResult result = new ProcessResult();
        // get source consume group name
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.SRCGROUPNAME, true, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        final String srcGroupName = (String) result.getRetData();
        // get source consume group's topic set cloned to target group
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.COMPSTOPICNAME, false, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        Set<String> srcTopicNameSet = (Set<String>) result.getRetData();
        // valid topic and get topic's partitionIds
        if (!validAndGetTopicPartInfo(srcGroupName,
                WebFieldDef.SRCGROUPNAME, srcTopicNameSet, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        final Map<String, Set<Integer>> topicPartMap =
                (Map<String, Set<Integer>>) result.getRetData();
        // get target consume group name
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.TGTCOMPSGROUPNAME, true, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        Set<String> tgtGroupNameSet = (Set<String>) result.getRetData();
        // get modify user
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.MODIFYUSER, true, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        final String modifier = (String) result.getRetData();
        // check sourceGroup if existed
        Set<String> bookedGroups = broker.getOffsetManager().getBookedGroups();
        if (!bookedGroups.contains(srcGroupName)) {
            WebParameterUtils.buildFailResult(sBuffer,
                    new StringBuilder(512).append("Parameter ")
                            .append(WebFieldDef.SRCGROUPNAME.name).append(": ")
                            .append(srcGroupName)
                            .append(" has not been registered on this Broker!").toString());
            return;
        }
        // query offset from source group
        Map<String, Map<Integer, Tuple2<Long, Long>>> srcGroupOffsets =
                broker.getOffsetManager().queryGroupOffset(srcGroupName, topicPartMap);
        // transfer offset format
        List<Tuple3<String, Integer, Long>> resetOffsets = buildOffsetResetInfo(srcGroupOffsets);
        broker.getOffsetManager().modifyGroupOffset(tgtGroupNameSet, resetOffsets, modifier);
        // builder return result
        sBuffer.append("{\"result\":true,\"errCode\":0,\"errMsg\":\"OK\"}");
    }

    /***
     * Remove consume group offset.
     *
     * @param req
     * @param sBuffer process result
     */
    public void adminRemoveGroupOffSet(HttpServletRequest req,
                                       StringBuilder sBuffer) {
        ProcessResult result = new ProcessResult();
        // get consume group name
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.COMPSGROUPNAME, true, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        final Set<String> groupNameSet = (Set<String>) result.getRetData();
        // get modify user
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.MODIFYUSER, true, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        final String modifier = (String) result.getRetData();
        // get need removed offset's topic
        if (!WebParameterUtils.getStringParamValue(req,
                WebFieldDef.COMPSTOPICNAME, false, null, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        // get target consume group name
        Set<String> topicNameSet = (Set<String>) result.getRetData();
        // get set mode
        if (!WebParameterUtils.getBooleanParamValue(req,
                WebFieldDef.ONLYMEM, false, false, sBuffer, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        boolean onlyMemory = (Boolean) result.getRetData();
        if (!validAndGetGroupTopicInfo(groupNameSet, topicNameSet, result)) {
            WebParameterUtils.buildFailResult(sBuffer, result.getErrMsg());
            return;
        }
        Map<String, Map<String, Set<Integer>>> groupTopicPartMap =
                (Map<String, Map<String, Set<Integer>>>) result.getRetData();
        broker.getOffsetManager().deleteGroupOffset(
                onlyMemory, groupTopicPartMap, modifier);
        // builder return result
        sBuffer.append("{\"result\":true,\"errCode\":0,\"errMsg\":\"OK\"}");
    }

    // build reset offset info
    private List<Tuple3<String, Integer, Long>> buildOffsetResetInfo(
            Map<String, Map<Integer, Tuple2<Long, Long>>> topicPartOffsetMap) {
        long adjOffset = -1;
        MessageStore store = null;
        List<Tuple3<String, Integer, Long>> result = new ArrayList<>();
        MessageStoreManager storeManager = broker.getStoreManager();
        for (Map.Entry<String, Map<Integer, Tuple2<Long, Long>>> entry
                : topicPartOffsetMap.entrySet()) {
            Map<Integer, Tuple2<Long, Long>> partOffsetMap = entry.getValue();
            if (partOffsetMap  == null) {
                continue;
            }
            // process offset value
            for (Map.Entry<Integer, Tuple2<Long, Long>> entry1 : partOffsetMap.entrySet()) {
                if (entry1.getValue() == null) {
                    continue;
                }
                Tuple2<Long, Long> offsetTuple = entry1.getValue();
                // get topic store
                try {
                    store = storeManager.getOrCreateMessageStore(
                            entry.getKey(), entry1.getKey());
                } catch (Throwable e) {
                    //
                }
                if (store == null) {
                    continue;
                }
                // adjust reset offset value
                adjOffset = MixedUtils.mid(offsetTuple.getF0(),
                        store.getIndexMinOffset(), store.getIndexMaxOffset());
                result.add(new Tuple3<>(entry.getKey(), entry1.getKey(), adjOffset));
            }
        }
        return result;
    }

    // build reset offset info
    private List<Tuple3<String, Integer, Long>> buildOffsetResetInfo(Set<String> topicSet) {
        MessageStore store = null;
        List<Tuple3<String, Integer, Long>> result = new ArrayList<>();
        MessageStoreManager storeManager = broker.getStoreManager();
        // get topic's partition set
        Map<String, Set<Integer>> topicPartMap = getTopicPartitions(topicSet);
        // fill current topic's max offset value
        for (Map.Entry<String, Set<Integer>> entry : topicPartMap.entrySet()) {
            if (entry.getKey() == null
                    || entry.getValue() == null
                    || entry.getValue().isEmpty()) {
                continue;
            }
            Set<Integer> partitionSet = entry.getValue();
            for (Integer partId : partitionSet) {
                // get topic store
                try {
                    store = storeManager.getOrCreateMessageStore(
                            entry.getKey(), partId);
                } catch (Throwable e) {
                    //
                }
                if (store == null) {
                    continue;
                }
                result.add(new Tuple3<>(entry.getKey(),
                        partId, store.getIndexMaxOffset()));
            }
        }
        return result;
    }

    // build reset offset info
    private boolean validManOffsetResetInfo(WebFieldDef fieldDef,
                                            Map<String, Long> manOffsetInfoMap,
                                            ProcessResult result) {
        String brokerId;
        String topicName;
        String strPartId;
        int partitionId;
        long adjOffset;
        MessageStore store = null;
        MessageStoreManager storeManager = broker.getStoreManager();
        List<Tuple3<String, Integer, Long>> offsetVals = new ArrayList<>();
        String localBrokerId = String.valueOf(broker.getTubeConfig().getBrokerId());
        // get topic configure infos
        Map<String, TopicMetadata> topicConfigMap =
                broker.getMetadataManager().getTopicConfigMap();
        for (Map.Entry<String, Long> entry : manOffsetInfoMap.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            // parse and check partitionKey value
            String[] keyItems = entry.getKey().split(TokenConstants.ATTR_SEP);
            if (keyItems.length != 3) {
                result.setFailResult(fieldDef.id,
                        new StringBuilder(512).append("Parameter ")
                                .append(fieldDef.name).append("'s key invalid:")
                                .append(entry.getKey())
                                .append(" must be brokerId:topicName:partitionId !").toString());
                return result.isSuccess();
            }
            brokerId = keyItems[0].trim();
            topicName = keyItems[1].trim();
            strPartId = keyItems[2].trim();
            if (!localBrokerId.equals(brokerId)
                    || !topicConfigMap.containsKey(topicName)) {
                continue;
            }
            try {
                partitionId = Integer.parseInt(strPartId);
            } catch (NumberFormatException e) {
                result.setFailResult(fieldDef.id,
                        new StringBuilder(512).append("Parameter ")
                                .append(fieldDef.name).append("'s key invalid:")
                                .append(entry.getKey())
                                .append("'s partitionId value not number!").toString());
                return result.isSuccess();
            }
            // check and adjust offset value
            try {
                store = storeManager.getOrCreateMessageStore(topicName, partitionId);
            } catch (Throwable e) {
                //
            }
            if (store == null) {
                continue;
            }
            adjOffset = MixedUtils.mid(entry.getValue(),
                    store.getIndexMinOffset(), store.getIndexMaxOffset());
            offsetVals.add(new Tuple3<>(topicName, partitionId, adjOffset));
        }
        if (offsetVals.isEmpty()) {
            result.setFailResult(fieldDef.id,
                    new StringBuilder(512).append("Parameter ")
                            .append(fieldDef.name).append("'s value is invalid!").toString());
        } else {
            result.setSuccResult(offsetVals);
        }
        return result.isSuccess();
    }

    // builder group's offset info
    private Map<String, Map<String, Map<Integer, GroupOffsetInfo>>> getGroupOffsetInfo(
            WebFieldDef groupFldDef, Set<String> groupSet, Set<String> topicSet) {
        ProcessResult result = new ProcessResult();
        Map<String, Map<String, Map<Integer, GroupOffsetInfo>>> groupOffsetMaps = new HashMap<>();
        for (String group : groupSet) {
            Map<String, Map<Integer, GroupOffsetInfo>> topicOffsetRet = new HashMap<>();
            // valid and get topic's partitionIds
            if (validAndGetTopicPartInfo(group, groupFldDef, topicSet, result)) {
                Map<String, Set<Integer>> topicPartMap =
                        (Map<String, Set<Integer>>) result.getRetData();
                // get topic's publish info
                Map<String, Map<Integer, TopicPubStoreInfo>> topicStorePubInfoMap =
                        broker.getStoreManager().getTopicPublishInfos(topicPartMap.keySet());
                // get group's booked offset info
                Map<String, Map<Integer, Tuple2<Long, Long>>> groupOffsetMap =
                        broker.getOffsetManager().queryGroupOffset(group, topicPartMap);
                // get offset info array
                for (Map.Entry<String, Set<Integer>> entry : topicPartMap.entrySet()) {
                    String topic = entry.getKey();
                    Map<Integer, GroupOffsetInfo> partOffsetRet = new HashMap<>();
                    Map<Integer, TopicPubStoreInfo> storeInfoMap = topicStorePubInfoMap.get(topic);
                    Map<Integer, Tuple2<Long, Long>> partBookedMap = groupOffsetMap.get(topic);
                    for (Integer partitionId : entry.getValue()) {
                        GroupOffsetInfo offsetInfo = new GroupOffsetInfo(partitionId);
                        offsetInfo.setPartPubStoreInfo(
                                storeInfoMap == null ? null : storeInfoMap.get(partitionId));
                        offsetInfo.setConsumeOffsetInfo(
                                partBookedMap == null ? null : partBookedMap.get(partitionId));
                        String queryKey = buildQueryID(group, topic, partitionId);
                        ConsumerNodeInfo nodeInfo = broker.getConsumerNodeInfo(queryKey);
                        if (nodeInfo != null) {
                            offsetInfo.setConsumeDataOffsetInfo(nodeInfo.getLastDataRdOffset());
                        }
                        offsetInfo.calculateLag();
                        partOffsetRet.put(partitionId, offsetInfo);
                    }
                    topicOffsetRet.put(topic, partOffsetRet);
                }
            }
            groupOffsetMaps.put(group, topicOffsetRet);
        }
        return groupOffsetMaps;
    }

    // valid and get need removed group-topic info
    private boolean validAndGetGroupTopicInfo(Set<String> groupSet,
                                              Set<String> topicSet,
                                              ProcessResult result) {
        Map<String, Map<String, Set<Integer>>> groupTopicPartMap = new HashMap<>();
        // filter group
        Set<String> targetGroupSet = new HashSet<>();
        Set<String> bookedGroups = broker.getOffsetManager().getBookedGroups();
        for (String orgGroup : groupSet) {
            if (bookedGroups.contains(orgGroup)) {
                targetGroupSet.add(orgGroup);
            }
        }
        // valid specified topic set
        for (String group : targetGroupSet) {
            if (validAndGetTopicPartInfo(group, WebFieldDef.GROUPNAME, topicSet, result)) {
                Map<String, Set<Integer>> topicPartMap =
                        (Map<String, Set<Integer>>) result.getRetData();
                groupTopicPartMap.put(group, topicPartMap);
            }
        }
        result.setSuccResult(groupTopicPartMap);
        return true;
    }

    private boolean validAndGetTopicPartInfo(String groupName,
                                             WebFieldDef groupFldDef,
                                             Set<String> topicSet,
                                             ProcessResult result) {
        Set<String> subTopicSet =
                broker.getOffsetManager().getGroupSubInfo(groupName);
        if (subTopicSet == null || subTopicSet.isEmpty()) {
            result.setFailResult(400, new StringBuilder(512)
                    .append("Parameter ").append(groupFldDef.name)
                    .append(": subscribed topic set of ").append(groupName)
                    .append(" query result is null!").toString());
            return result.isSuccess();
        }
        // filter valid topic set
        Set<String> tgtTopicSet = new HashSet<>();
        if (topicSet.isEmpty()) {
            tgtTopicSet = subTopicSet;
        } else {
            for (String topic : topicSet) {
                if (subTopicSet.contains(topic)) {
                    tgtTopicSet.add(topic);
                }
            }
            if (tgtTopicSet.isEmpty()) {
                result.setFailResult(400, new StringBuilder(512)
                        .append("Parameter ").append(groupFldDef.name)
                        .append(": ").append(groupName)
                        .append(" unsubscribed to the specified topic set!").toString());
                return result.isSuccess();
            }
        }
        Map<String, Set<Integer>> topicPartMap = getTopicPartitions(tgtTopicSet);
        if (topicPartMap.isEmpty()) {
            result.setFailResult(400, new StringBuilder(512)
                    .append("Parameter ").append(groupFldDef.name)
                    .append(": all topics subscribed by the group have been deleted!").toString());
            return result.isSuccess();
        }
        result.setSuccResult(topicPartMap);
        return result.isSuccess();
    }

    private Map<String, Set<Integer>> getTopicPartitions(Set<String> topicSet) {
        Map<String, Set<Integer>> topicPartMap = new HashMap<>();
        if (topicSet != null) {
            Map<String, TopicMetadata> topicConfigMap =
                    broker.getMetadataManager().getTopicConfigMap();
            if (topicConfigMap != null) {
                for (String topic : topicSet) {
                    TopicMetadata topicMetadata = topicConfigMap.get(topic);
                    if (topicMetadata != null) {
                        topicPartMap.put(topic, topicMetadata.getAllPartitionIds());
                    }
                }
            }
        }
        return topicPartMap;
    }

    private String buildQueryID(String group, String topic, int partitionId) {
        return new StringBuilder(512).append(group)
                .append(TokenConstants.ATTR_SEP).append(topic)
                .append(TokenConstants.ATTR_SEP).append(partitionId).toString();
    }

}
