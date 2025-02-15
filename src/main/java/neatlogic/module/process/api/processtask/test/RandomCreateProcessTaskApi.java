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

package neatlogic.module.process.api.processtask.test;

import neatlogic.framework.asynchronization.thread.NeatLogicThread;
import neatlogic.framework.asynchronization.threadpool.CachedThreadPool;
import neatlogic.framework.auth.core.AuthAction;
import neatlogic.framework.common.constvalue.ApiParamType;
import neatlogic.framework.common.dto.ValueTextVo;
import neatlogic.framework.dao.mapper.UserMapper;
import neatlogic.framework.dto.UserVo;
import neatlogic.framework.process.auth.PROCESS_BASE;
import neatlogic.framework.process.constvalue.ProcessTaskStatus;
import neatlogic.framework.process.constvalue.ProcessTaskStepUserStatus;
import neatlogic.framework.process.dao.mapper.ChannelMapper;
import neatlogic.framework.process.dao.mapper.PriorityMapper;
import neatlogic.framework.process.dao.mapper.ProcessTaskMapper;
import neatlogic.framework.process.dto.*;
import neatlogic.framework.restful.annotation.Input;
import neatlogic.framework.restful.annotation.OperationType;
import neatlogic.framework.restful.annotation.Param;
import neatlogic.framework.restful.constvalue.OperationTypeEnum;
import neatlogic.framework.restful.core.privateapi.PrivateApiComponentBase;
import neatlogic.framework.restful.core.privateapi.PrivateApiComponentFactory;
import neatlogic.module.process.api.processtask.ProcessTaskCompleteApi;
import neatlogic.module.process.api.processtask.ProcessTaskDraftSaveApi;
import neatlogic.module.process.api.processtask.ProcessTaskStartProcessApi;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/**
 * @Title: RandomCreateProcessTaskApi
 * @Package processtask
 * @Description: 1、create:随机获取服务、用户、优先级创建工单
 * 2、execute:随机执行工单步骤(因为异步原因，在create后需延迟50s后再执行工单)
 * @Author: 89770
 * @Date: 2020/12/28 10:49
 **/
@Service
@OperationType(type = OperationTypeEnum.OPERATE)
@AuthAction(action = PROCESS_BASE.class)
class RandomCreateProcessTaskApi extends PrivateApiComponentBase {
    private final Map<String, Action<JSONObject>> actionMap = new HashMap<>();
    CountDownLatch latch = null;
    @Autowired
    ProcessTaskMapper processtaskMapper;

    @Autowired
    ChannelMapper channelMapper;

    @Autowired
    UserMapper userMapper;

    @Autowired
    PriorityMapper priorityMapper;

    @PostConstruct
    public void actionDispatcherInit() {
        /*
         * @Description: 随机获取服务、用户、优先级创建工单
         * @Author: 89770
         * @Date: 2020/12/28 11:22
         */
        actionMap.put("create", (jsonParam) -> {
            int unitCount = 100000;
            JSONObject paramJson = new JSONObject();
            Integer count = jsonParam.getInteger("count");
            int latchCount = count / unitCount;
            if (count % unitCount > 0) {
                latchCount++;
            }
            latch = new CountDownLatch(latchCount);
            int startIndex = 0;
            int endIndex = startIndex + unitCount;
            for (int i = 0; i < latchCount; i++) {
                if (latchCount > 1) {
                    startIndex = i * unitCount + 1;
                }
                endIndex = startIndex + unitCount;
                if (latchCount == 1 || startIndex + unitCount > count) {
                    endIndex = count;
                }
                MyCreateThread thread = new MyCreateThread(startIndex, endIndex);
                CachedThreadPool.execute(thread);
            }
            try {
                latch.await(); // 主线程等待
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        /*
         * @Description: 随机执行工单步骤(因为异步原因，在create后需延迟50s后再执行工单)
         * @Author: 89770
         * @Date: 2020/12/31 11:22
         */
        actionMap.put("execute", (jsonParam) -> {
            Integer count = jsonParam.getInteger("count");
            List<ProcessTaskVo> processTaskVoList = processtaskMapper.getProcessTaskByStatusList(Collections.singletonList(ProcessTaskStatus.RUNNING.getValue()), count);
            List<Long> taskIdList = processTaskVoList.stream().map(ProcessTaskVo::getId).collect(Collectors.toList());
            List<ProcessTaskStepUserVo> stepUserVoList = processtaskMapper.getProcessTaskStepUserListByProcessTaskIdListAndStatusList(taskIdList, Collections.singletonList(ProcessTaskStepUserStatus.DOING.getValue()));
            List<Long> stepIdList = stepUserVoList.stream().map(ProcessTaskStepUserVo::getProcessTaskStepId).collect(Collectors.toList());
            ProcessTaskCompleteApi completeProcessApi = (ProcessTaskCompleteApi) PrivateApiComponentFactory.getInstance(ProcessTaskCompleteApi.class.getName());
            Map<String, Long> processTaskNextStepMap = new HashMap<>();
            List<ProcessTaskStepRelVo> stepRelVoList = processtaskMapper.getProcessTaskStepRelListByFromIdList(stepIdList);
            for (ProcessTaskStepRelVo stepRelVo : stepRelVoList) {
                processTaskNextStepMap.put(String.format("%s_%s", stepRelVo.getProcessTaskId(), stepRelVo.getFromProcessTaskStepId()), stepRelVo.getToProcessTaskStepId());
            }
            for (ProcessTaskStepUserVo stepUserVo : stepUserVoList) {
                JSONObject completeJson = new JSONObject();
                completeJson.put("processTaskId", stepUserVo.getProcessTaskId());
                completeJson.put("processTaskStepId", stepUserVo.getProcessTaskStepId());
                completeJson.put("action", "complete");
                completeJson.put("nextStepId", processTaskNextStepMap.get(String.format("%s_%s", stepUserVo.getProcessTaskId(), stepUserVo.getProcessTaskStepId())));
                completeProcessApi.doService(PrivateApiComponentFactory.getApiByToken(completeProcessApi.getToken()), completeJson, null);
            }
        });
    }

    @Override
    public String getName() {
        return "随机创建|执行工单";
    }

    @Override
    public String getConfig() {
        return null;
    }

    @Override
    @Input({
            @Param(name = "count", type = ApiParamType.INTEGER, desc = "需要随机创建|执行工单数", isRequired = true),
            @Param(name = "type", type = ApiParamType.STRING, desc = "create:创建工单，execute:执行工单(因为异步原因，在create后需延迟50s后再执行工单)", isRequired = true)
    })
    public Object myDoService(JSONObject paramJson) throws Exception {
        String type = paramJson.getString("type");
        actionMap.get(type).execute(paramJson);
        return null;
    }

    @Override
    public String getToken() {
        return "processtask/randomCreateProcessTask";
    }

    @FunctionalInterface
    public interface Action<T> {
        void execute(T t) throws Exception;
    }

    class MyCreateThread extends NeatLogicThread {
        private final Integer startIndex;
        private final Integer endIndex;

        public MyCreateThread(Integer startIndex, Integer endIndex) {
            super("RANDOM-CREATE-PROCESSTASK");
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        @SuppressWarnings("unchecked")
        public void execute() {
            JSONObject paramJson = new JSONObject();
            ChannelVo channelVo = new ChannelVo();
            channelVo.setNeedPage(false);
            List<ValueTextVo> channelKeyValueList = channelMapper.searchChannelListForSelect(channelVo);
            UserVo user = new UserVo();
            user.setNeedPage(true);
            user.setStartNum(0);
            user.setPageSize(1000);
            List<UserVo> userList = userMapper.searchUserForSelect(new UserVo());
            List<ValueTextVo> priorityKeyValueList = priorityMapper.searchPriorityListForSelect(new PriorityVo());
            for (int i = startIndex; i < endIndex; i++) {

                try {
                    int randomChannelIndex = (int) Math.round(Math.random() * (channelKeyValueList.size() - 1));
                    int randomUserIndex = (int) Math.round(Math.random() * (userList.size() - 1));
                    int randomPriorityIndex = (int) Math.round(Math.random() * (priorityKeyValueList.size() - 1));
                    ValueTextVo channelValueText = channelKeyValueList.get(randomChannelIndex);
                    UserVo ownerVo = userList.get(randomUserIndex);
                    ValueTextVo priorityValueText = priorityKeyValueList.get(randomPriorityIndex);
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    paramJson.put("channelUuid", channelValueText.getValue());
                    paramJson.put("title", String.format("%s 上报了 服务为 '%s' ,优先级为 '%s' 的工单", ownerVo.getUserName(), channelValueText.getText(), priorityValueText.getText()));
                    paramJson.put("owner", ownerVo.getUuid());
                    paramJson.put("priorityUuid", priorityValueText.getValue());
                    int startContentIndex = (int) Math.round(Math.random() * (Text.text.length() - 1));
                    int endContentIndex = (int) Math.round(Math.random() * (Text.text.length() - 1));
                    if (startContentIndex > endContentIndex) {
                        int tmpIndex = startContentIndex;
                        startContentIndex = endContentIndex;
                        endContentIndex = tmpIndex;
                    }
                    paramJson.put("content", Text.text.substring(startContentIndex, endContentIndex));
                    //暂存
                    paramJson.put("isNeedValid", 1);
                    paramJson.put("hidecomponentList", new JSONArray());
                    paramJson.put("readcomponentList", new JSONArray());

                    ProcessTaskDraftSaveApi draftSaveApi = (ProcessTaskDraftSaveApi) PrivateApiComponentFactory.getInstance(ProcessTaskDraftSaveApi.class.getName());
                    JSONObject saveResultObj = JSONObject.parseObject(draftSaveApi.doService(PrivateApiComponentFactory.getApiByToken(draftSaveApi.getToken()), paramJson, null).toString());
                    saveResultObj.put("action", "start");
                    //查询可执行下一步骤
                    List<Long> nextStepIdList = processtaskMapper.getToProcessTaskStepIdListByFromIdAndType(saveResultObj.getLong("processTaskStepId"), null);
                    saveResultObj.put("nextStepId", nextStepIdList.get((int) Math.round(Math.random() * (nextStepIdList.size() - 1))));
                    //流转
                    ProcessTaskStartProcessApi startProcessApi = (ProcessTaskStartProcessApi) PrivateApiComponentFactory.getInstance(ProcessTaskStartProcessApi.class.getName());
                    startProcessApi.doService(PrivateApiComponentFactory.getApiByToken(startProcessApi.getToken()), saveResultObj, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            latch.countDown(); // 执行完毕，计数器减1

        }
    }
}
