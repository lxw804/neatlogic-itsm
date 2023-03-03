/*
 * Copyright(c) 2023 NeatLogic Co., Ltd. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package neatlogic.module.process.thread;

import neatlogic.framework.asynchronization.thread.NeatLogicThread;
import neatlogic.framework.file.dao.mapper.FileMapper;
import neatlogic.framework.file.dto.FileVo;
import neatlogic.framework.notify.core.INotifyTriggerType;
import neatlogic.framework.notify.dao.mapper.NotifyMapper;
import neatlogic.framework.notify.dto.NotifyPolicyConfigVo;
import neatlogic.framework.notify.dto.NotifyPolicyVo;
import neatlogic.framework.notify.dto.NotifyReceiverVo;
import neatlogic.framework.notify.dto.ParamMappingVo;
import neatlogic.framework.process.condition.core.ProcessTaskConditionFactory;
import neatlogic.framework.process.constvalue.ConditionProcessTaskOptions;
import neatlogic.framework.process.dao.mapper.ProcessStepHandlerMapper;
import neatlogic.framework.process.dao.mapper.ProcessTaskMapper;
import neatlogic.framework.process.dao.mapper.SelectContentByHashMapper;
import neatlogic.framework.process.dto.ProcessTaskStepVo;
import neatlogic.framework.process.dto.ProcessTaskVo;
import neatlogic.framework.process.exception.process.ProcessStepUtilHandlerNotFoundException;
import neatlogic.framework.process.notify.constvalue.ProcessTaskNotifyTriggerType;
import neatlogic.module.process.service.ProcessTaskService;
import neatlogic.framework.process.stephandler.core.IProcessStepInternalHandler;
import neatlogic.framework.process.stephandler.core.ProcessStepInternalHandlerFactory;
import neatlogic.framework.util.NotifyPolicyUtil;
import neatlogic.module.process.message.handler.ProcessTaskMessageHandler;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProcessTaskNotifyThread extends NeatLogicThread {
    private static final Logger logger = LoggerFactory.getLogger(ProcessTaskActionThread.class);
    private static ProcessTaskMapper processTaskMapper;
    private static SelectContentByHashMapper selectContentByHashMapper;
    private static ProcessStepHandlerMapper processStepHandlerMapper;
    private static NotifyMapper notifyMapper;
    private static ProcessTaskService processTaskService;

    @Autowired
    public void setProcessTaskService(ProcessTaskService _processTaskService) {
        processTaskService = _processTaskService;
    }

    @Autowired
    public void setProcessTaskMapper(ProcessTaskMapper _processTaskMapper) {
        processTaskMapper = _processTaskMapper;
    }

    @Autowired
    public void setSelectContentByHashMapper(SelectContentByHashMapper _selectContentByHashMapper) {
        selectContentByHashMapper = _selectContentByHashMapper;
    }

    @Autowired
    public void setProcessStepHandlerMapper(ProcessStepHandlerMapper _processStepHandlerMapper) {
        processStepHandlerMapper = _processStepHandlerMapper;
    }

    @Autowired
    public void setNotifyMapper(NotifyMapper _notifyMapper) {
        notifyMapper = _notifyMapper;
    }

    private ProcessTaskStepVo currentProcessTaskStepVo;
    private INotifyTriggerType notifyTriggerType;

    public ProcessTaskNotifyThread() {
        super("PROCESSTASK-NOTIFY");
    }

    public ProcessTaskNotifyThread(ProcessTaskStepVo _currentProcessTaskStepVo, INotifyTriggerType _trigger) {
        super("PROCESSTASK-NOTIFY" + (_currentProcessTaskStepVo != null ? "-" + _currentProcessTaskStepVo.getId() : ""));
        currentProcessTaskStepVo = _currentProcessTaskStepVo;
        notifyTriggerType = _trigger;
    }

    @Override
    protected void execute() {
        try {
            StringBuilder notifyAuditMessageStringBuilder = new StringBuilder();
            JSONObject notifyPolicyConfig = null;
            Long policyId = null;
            if (notifyTriggerType instanceof ProcessTaskNotifyTriggerType) {
                /** 获取工单配置信息 **/
                ProcessTaskVo processTaskVo = processTaskMapper.getProcessTaskBaseInfoByIdIncludeIsDeleted(currentProcessTaskStepVo.getProcessTaskId());
                String config = selectContentByHashMapper.getProcessTaskConfigStringByHash(processTaskVo.getConfigHash());
                notifyPolicyConfig = (JSONObject) JSONPath.read(config, "process.processConfig.notifyPolicyConfig");
                if (MapUtils.isNotEmpty(notifyPolicyConfig)) {
                    policyId = notifyPolicyConfig.getLong("policyId");
                }
                notifyAuditMessageStringBuilder.append(currentProcessTaskStepVo.getProcessTaskId());
            } else {
                /* 获取步骤配置信息 **/
                ProcessTaskStepVo stepVo = processTaskMapper.getProcessTaskStepBaseInfoById(currentProcessTaskStepVo.getId());
                IProcessStepInternalHandler processStepUtilHandler = ProcessStepInternalHandlerFactory.getHandler(stepVo.getHandler());
                if (processStepUtilHandler == null) {
                    throw new ProcessStepUtilHandlerNotFoundException(stepVo.getHandler());
                }
                String stepConfig = selectContentByHashMapper.getProcessTaskStepConfigByHash(stepVo.getConfigHash());
                notifyPolicyConfig = (JSONObject) JSONPath.read(stepConfig, "notifyPolicyConfig");
                if (MapUtils.isNotEmpty(notifyPolicyConfig)) {
                    policyId = notifyPolicyConfig.getLong("policyId");
                }
                if (policyId == null) {
                    String processStepHandlerConfig = processStepHandlerMapper.getProcessStepHandlerConfigByHandler(stepVo.getHandler());
                    JSONObject globalConfig = null;
                    if (StringUtils.isNotBlank(processStepHandlerConfig)) {
                        globalConfig = JSONObject.parseObject(processStepHandlerConfig);
                    }
                    globalConfig = processStepUtilHandler.makeupConfig(globalConfig);
                    notifyPolicyConfig = globalConfig.getJSONObject("notifyPolicyConfig");
                    if (MapUtils.isNotEmpty(notifyPolicyConfig)) {
                        policyId = notifyPolicyConfig.getLong("policyId");
                    }
                }
                notifyAuditMessageStringBuilder.append(stepVo.getProcessTaskId());
                notifyAuditMessageStringBuilder.append("-");
                notifyAuditMessageStringBuilder.append(stepVo.getName());
                notifyAuditMessageStringBuilder.append("(");
                notifyAuditMessageStringBuilder.append(stepVo.getId());
                notifyAuditMessageStringBuilder.append(")");
            }

            /* 从步骤配置信息中获取通知策略信息 **/
            if (policyId != null) {
                NotifyPolicyVo notifyPolicyVo = notifyMapper.getNotifyPolicyById(policyId);
                if (notifyPolicyVo != null) {
                    NotifyPolicyConfigVo policyConfig = notifyPolicyVo.getConfig();
                    if (policyConfig != null) {
                        JSONObject conditionParamData = ProcessTaskConditionFactory.getConditionParamData(Arrays.stream(ConditionProcessTaskOptions.values()).map(ConditionProcessTaskOptions::getValue).collect(Collectors.toList()), currentProcessTaskStepVo);
//                        ProcessTaskVo processTaskVo = processTaskService.getProcessTaskDetailById(currentProcessTaskStepVo.getProcessTaskId());
//                        processTaskVo.setStartProcessTaskStep(processTaskService.getStartProcessTaskStepByProcessTaskId(processTaskVo.getId()));
//                        processTaskVo.setCurrentProcessTaskStep(processTaskService.getCurrentProcessTaskStepDetail(currentProcessTaskStepVo));
//                        JSONObject conditionParamData = ProcessTaskUtil.getProcessFieldData(processTaskVo, true);
//                        JSONObject templateParamData = ProcessTaskUtil.getProcessTaskParamData(processTaskVo);
                        Map<String, List<NotifyReceiverVo>> receiverMap = new HashMap<>();
                        processTaskService.getReceiverMap(currentProcessTaskStepVo, receiverMap, notifyTriggerType);
                        /* 参数映射列表 **/
                        List<ParamMappingVo> paramMappingList = new ArrayList<>();
                        JSONArray paramMappingArray = notifyPolicyConfig.getJSONArray("paramMappingList");
                        if (CollectionUtils.isNotEmpty(paramMappingArray)) {
                            paramMappingList = paramMappingArray.toJavaList(ParamMappingVo.class);
                        }
                        List<FileVo> fileList = processTaskMapper.getFileListByProcessTaskId(currentProcessTaskStepVo.getProcessTaskId());
                        if (CollectionUtils.isNotEmpty(fileList)) {
                            fileList = fileList.stream().filter(o -> o.getSize() <= 10 * 1024 * 1024).collect(Collectors.toList());
                        }
                        String notifyPolicyHandler = notifyPolicyVo.getHandler();
                        NotifyPolicyUtil.execute(notifyPolicyHandler, notifyTriggerType, ProcessTaskMessageHandler.class, notifyPolicyVo, paramMappingList, conditionParamData, receiverMap, currentProcessTaskStepVo, fileList, notifyAuditMessageStringBuilder.toString());
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("通知失败：" + ex.getMessage(), ex);
        }
    }

}
