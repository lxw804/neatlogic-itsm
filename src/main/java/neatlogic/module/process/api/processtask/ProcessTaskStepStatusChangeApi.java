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


package neatlogic.module.process.api.processtask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import neatlogic.framework.process.constvalue.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSONObject;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import neatlogic.framework.auth.core.AuthAction;
import neatlogic.framework.common.constvalue.ApiParamType;
import neatlogic.framework.common.constvalue.GroupSearch;
import neatlogic.framework.dao.mapper.UserMapper;
import neatlogic.framework.dto.UserVo;
import neatlogic.framework.exception.type.ParamNotExistsException;
import neatlogic.framework.exception.user.UserNotFoundException;
import neatlogic.framework.process.auth.PROCESS_BASE;
import neatlogic.framework.process.dao.mapper.ProcessTaskMapper;
import neatlogic.framework.process.dto.ProcessTaskStepRelVo;
import neatlogic.framework.process.dto.ProcessTaskStepUserVo;
import neatlogic.framework.process.dto.ProcessTaskStepVo;
import neatlogic.framework.process.dto.ProcessTaskStepWorkerVo;
import neatlogic.framework.process.dto.ProcessTaskVo;
import neatlogic.framework.process.exception.processtask.ProcessTaskNextStepIllegalException;
import neatlogic.framework.process.exception.processtask.ProcessTaskNextStepNameOrIdUnAssignException;
import neatlogic.framework.process.exception.processtask.ProcessTaskStepFoundMultipleException;
import neatlogic.framework.process.exception.processtask.ProcessTaskStepNotFoundException;
import neatlogic.framework.process.exception.processtask.ProcessTaskStepUserUnAssignException;
import neatlogic.framework.restful.annotation.Description;
import neatlogic.framework.restful.annotation.Input;
import neatlogic.framework.restful.annotation.OperationType;
import neatlogic.framework.restful.annotation.Param;
import neatlogic.framework.restful.constvalue.OperationTypeEnum;
import neatlogic.framework.restful.core.privateapi.PrivateApiComponentBase;

@Service
@AuthAction(action = PROCESS_BASE.class)
@Transactional
@OperationType(type = OperationTypeEnum.OPERATE)
public class ProcessTaskStepStatusChangeApi extends PrivateApiComponentBase {

    @Resource
    private ProcessTaskMapper processTaskMapper;

    @Resource
    private UserMapper userMapper;

    @Override
    public String getToken() {
        return "processtask/step/status/change";
    }

    @Override
    public String getName() {
        return "手动更改工单步骤状态";
    }

    @Override
    public String getConfig() {
        return null;
    }

    @Input({
            @Param(name = "processTaskId", type = ApiParamType.LONG, desc = "工单Id"),
            @Param(name = "processTaskStepName", type = ApiParamType.STRING, desc = "工单步骤名称"),
            @Param(name = "processTaskNextStepName", type = ApiParamType.STRING, desc = "需要激活的下一步骤名称(更改步骤状态为succeed时需要填此参数)"),
            @Param(name = "processTaskStepId", type = ApiParamType.LONG, desc = "工单步骤Id(待更改状态的步骤名称重复时需要填此参数。此参数存在时，无需填processTaskId与processTaskStepName)"),
            @Param(name = "processTaskNextStepId", type = ApiParamType.LONG, desc = "下一步工单步骤Id(待激活的下一步骤名称重复时需要填此参数。此参数存在时，无需填processTaskNextStepName)"),
            @Param(name = "status", type = ApiParamType.ENUM, rule = "pending,running,succeed,hang", isRequired = true, desc = "工单步骤状态"),
            @Param(name = "userId", type = ApiParamType.STRING, desc = "处理人userId"),
    })
    @Description(desc = "手动更改工单步骤状态")
    @Override
    public Object myDoService(JSONObject jsonObj) throws Exception {
        Long processTaskId = jsonObj.getLong("processTaskId");
        String processTaskStepName = jsonObj.getString("processTaskStepName");
        String processTaskNextStepName = jsonObj.getString("processTaskNextStepName");
        Long processTaskStepId = jsonObj.getLong("processTaskStepId");
        Long processTaskNextStepId = jsonObj.getLong("processTaskNextStepId");
        String status = jsonObj.getString("status");
        String userId = jsonObj.getString("userId");
        if (processTaskId == null && processTaskStepId == null) {
            throw new ParamNotExistsException("processTaskId", "processTaskStepId");
        }
        ProcessTaskStepVo processTaskStep;
        if (processTaskId != null) {
            if (StringUtils.isBlank(processTaskStepName)) {
                throw new ParamNotExistsException("processTaskStepName");
            }
            List<ProcessTaskStepVo> stepList = processTaskMapper.getProcessTaskStepByProcessTaskIdAndStepName(new ProcessTaskStepVo(processTaskId, processTaskStepName));
            if (stepList.isEmpty()) {
                throw new ProcessTaskStepNotFoundException(processTaskStepName);
            }
            if (stepList.size() > 1) {
                throw new ProcessTaskStepFoundMultipleException(processTaskStepName);
            }
            processTaskStep = stepList.get(0);
        } else {
            processTaskStep = processTaskMapper.getProcessTaskStepBaseInfoById(processTaskStepId);
            if (processTaskStep == null) {
                throw new ProcessTaskStepNotFoundException(processTaskStepId);
            }
        }
        if (StringUtils.isNotBlank(userId)) {
            UserVo user = userMapper.getUserByUserId(userId);
            if (user == null) {
                throw new UserNotFoundException(userId);
            }
            processTaskStep.setOriginalUserVo(user);
        }
        processTaskStep.setNextStepName(processTaskNextStepName);
        processTaskStep.setNextStepId(processTaskNextStepId);
        processTaskMapper.getProcessTaskLockById(processTaskStep.getProcessTaskId());
        map.get(status).accept(processTaskStep);
        return null;
    }

    static Map<String, Consumer<ProcessTaskStepVo>> map = new HashMap<>();

    @PostConstruct
    private void init() {
        map.put(ProcessTaskStepStatus.PENDING.getValue(), processTaskStepVo -> {
            if ("process".equals(processTaskStepVo.getType()) && processTaskStepVo.getOriginalUserVo() == null) {
                throw new ProcessTaskStepUserUnAssignException();
            }
            changeProcessTaskStepStatusToPending(processTaskStepVo);
        });
        map.put(ProcessTaskStepStatus.RUNNING.getValue(), processTaskStepVo -> {
            if ("process".equals(processTaskStepVo.getType())) {
                if (processTaskStepVo.getOriginalUserVo() == null) {
                    List<ProcessTaskStepUserVo> processTaskStepUserList = processTaskMapper.getProcessTaskStepUserByStepId(processTaskStepVo.getId(), ProcessUserType.MAJOR.getValue());
                    // 需要处理人的步骤，不指定处理人时，旧处理人必须存在
                    if (processTaskStepUserList.isEmpty()) {
                        throw new ProcessTaskStepUserUnAssignException();
                    }
                    changeProcessTaskStepStatusToRunning(processTaskStepVo, new UserVo(processTaskStepUserList.get(0).getUserUuid(), processTaskStepUserList.get(0).getUserName()));
                } else {
                    processTaskMapper.deleteProcessTaskStepUser(new ProcessTaskStepUserVo(processTaskStepVo.getId(), ProcessUserType.MAJOR.getValue()));
                    processTaskMapper.insertProcessTaskStepUser(new ProcessTaskStepUserVo(
                            processTaskStepVo.getProcessTaskId(),
                            processTaskStepVo.getId(),
                            processTaskStepVo.getOriginalUserVo().getUuid(),
                            ProcessUserType.MAJOR.getValue()
                    ));
                    changeProcessTaskStepStatusToRunning(processTaskStepVo, processTaskStepVo.getOriginalUserVo());
                }
            } else {
                changeProcessTaskStepStatusToRunning(processTaskStepVo);
            }
        });
        map.put(ProcessTaskStepStatus.SUCCEED.getValue(), processTaskStepVo -> {
            if (!ProcessStepHandlerType.END.getHandler().equals(processTaskStepVo.getHandler()) && StringUtils.isBlank(processTaskStepVo.getNextStepName())
                    && processTaskStepVo.getNextStepId() == null) {
                throw new ProcessTaskNextStepNameOrIdUnAssignException();
            }
            ProcessTaskStepVo nextStep = null;
            // 检查下一步骤是否合法
            if (StringUtils.isNotBlank(processTaskStepVo.getNextStepName())) {
                List<ProcessTaskStepVo> nextStepList = processTaskMapper.getProcessTaskStepByProcessTaskIdAndStepName(new ProcessTaskStepVo(processTaskStepVo.getProcessTaskId(), processTaskStepVo.getNextStepName()));
                if (nextStepList.isEmpty()) {
                    throw new ProcessTaskStepNotFoundException(processTaskStepVo.getNextStepName());
                }
                if (nextStepList.size() > 1) {
                    throw new ProcessTaskStepFoundMultipleException(processTaskStepVo.getNextStepName());
                }
                nextStep = nextStepList.get(0);
            } else if (processTaskStepVo.getNextStepId() != null) {
                nextStep = processTaskMapper.getProcessTaskStepBaseInfoById(processTaskStepVo.getNextStepId());
                if (nextStep == null) {
                    throw new ProcessTaskStepNotFoundException(processTaskStepVo.getNextStepId());
                }
            }
            if (nextStep != null) {
                List<ProcessTaskStepRelVo> stepRelVoList = processTaskMapper.getProcessTaskStepRelByFromId(processTaskStepVo.getId());
                ProcessTaskStepVo finalNextStep = nextStep;
                if (stepRelVoList.stream().noneMatch(o -> Objects.equals(o.getToProcessTaskStepId(), finalNextStep.getId()))) {
                    throw new ProcessTaskNextStepIllegalException(processTaskStepVo.getName(), nextStep.getName());
                }
            }
            // 清空当前步骤worker
            processTaskMapper.deleteProcessTaskStepWorker(new ProcessTaskStepWorkerVo(processTaskStepVo.getId(), ProcessUserType.MAJOR.getValue()));
            // 更改当前步骤处理人状态为DONE
            if (processTaskStepVo.getOriginalUserVo() == null) {
                ProcessTaskStepUserVo processTaskStepUserVo = new ProcessTaskStepUserVo(processTaskStepVo.getId(), ProcessUserType.MAJOR.getValue());
                processTaskStepUserVo.setStatus(ProcessTaskStepUserStatus.DONE.getValue());
                processTaskMapper.updateProcessTaskStepUserStatus(processTaskStepUserVo);
            } else {
                processTaskMapper.updateProcessTaskStepMajorUserAndStatus(new ProcessTaskStepUserVo(processTaskStepVo.getId()
                        , processTaskStepVo.getOriginalUserVo().getUuid()
                        , processTaskStepVo.getOriginalUserVo().getUserName()
                        , ProcessTaskStepUserStatus.DONE.getValue())
                );
            }
            // 更改当前步骤状态为SUCCEED
            processTaskStepVo.setIsActive(2);
            processTaskStepVo.setStatus(ProcessTaskStepStatus.SUCCEED.getValue());
            processTaskStepVo.setUpdateEndTime(1);
            processTaskMapper.updateProcessTaskStepStatus(processTaskStepVo);
            // 激活与下个节点之间的路径、更改工单状态
            if (ProcessStepHandlerType.END.getHandler().equals(processTaskStepVo.getHandler())) {
                processTaskMapper.updateProcessTaskStatus(new ProcessTaskVo(processTaskStepVo.getProcessTaskId(), ProcessTaskStatus.SUCCEED));
            } else if (nextStep != null) {
                processTaskMapper.updateProcessTaskStepRelIsHit(new ProcessTaskStepRelVo(processTaskStepVo.getId(), nextStep.getId(), 1));
            }
        });
        map.put(ProcessTaskStepStatus.HANG.getValue(), processTaskStepVo -> {
            ProcessTaskStepUserVo processTaskStepUserVo = new ProcessTaskStepUserVo(processTaskStepVo.getId(), ProcessUserType.MAJOR.getValue());
            processTaskStepUserVo.setStatus(ProcessTaskStepUserStatus.DONE.getValue());
            processTaskMapper.updateProcessTaskStepUserStatus(processTaskStepUserVo);
            processTaskStepVo.setIsActive(0);
            processTaskStepVo.setStatus(ProcessTaskStatus.HANG.getValue());
            processTaskStepVo.setUpdateEndTime(1);
            processTaskMapper.updateProcessTaskStepStatus(processTaskStepVo);
            processTaskMapper.updateProcessTaskStatus(new ProcessTaskVo(processTaskStepVo.getProcessTaskId(), ProcessTaskStatus.HANG));
        });
    }

    /**
     * 更改步骤状态为待处理
     *
     * @param processTaskStepVo
     */
    private void changeProcessTaskStepStatusToPending(ProcessTaskStepVo processTaskStepVo) {
        if ("process".equals(processTaskStepVo.getType())) {
            processTaskMapper.deleteProcessTaskStepUser(new ProcessTaskStepUserVo(processTaskStepVo.getId(), ProcessUserType.MAJOR.getValue()));
            processTaskMapper.deleteProcessTaskStepWorker(new ProcessTaskStepWorkerVo(processTaskStepVo.getId(), ProcessUserType.MAJOR.getValue()));
            processTaskMapper.insertIgnoreProcessTaskStepWorker(new ProcessTaskStepWorkerVo(processTaskStepVo.getProcessTaskId(), processTaskStepVo.getId()
                    , GroupSearch.USER.getValue(), processTaskStepVo.getOriginalUserVo().getUuid(), ProcessUserType.MAJOR.getValue()));
        }
        processTaskMapper.updateProcessTaskStepStatusByStepId(new ProcessTaskStepVo(processTaskStepVo.getId(), ProcessTaskStepStatus.PENDING, 1));
        processTaskMapper.updateProcessTaskStatus(new ProcessTaskVo(processTaskStepVo.getProcessTaskId(), ProcessTaskStatus.RUNNING));
    }

    /**
     * 更改无需处理人的步骤状态为处理中
     *
     * @param processTaskStep 步骤
     */
    private void changeProcessTaskStepStatusToRunning(ProcessTaskStepVo processTaskStep) {
        processTaskMapper.updateProcessTaskStepStatusByStepId(new ProcessTaskStepVo(processTaskStep.getId(), ProcessTaskStepStatus.RUNNING, 1));
        processTaskMapper.updateProcessTaskStatus(new ProcessTaskVo(processTaskStep.getProcessTaskId(), ProcessTaskStatus.RUNNING));
    }

    /**
     * 更改需要处理人的步骤状态为处理中
     *
     * @param processTaskStep 步骤
     * @param majorUser       处理人
     */
    private void changeProcessTaskStepStatusToRunning(ProcessTaskStepVo processTaskStep, UserVo majorUser) {
        processTaskMapper.deleteProcessTaskStepWorker(new ProcessTaskStepWorkerVo(processTaskStep.getId(), ProcessUserType.MAJOR.getValue()));
        processTaskMapper.insertIgnoreProcessTaskStepWorker(new ProcessTaskStepWorkerVo(processTaskStep.getProcessTaskId(), processTaskStep.getId(), GroupSearch.USER.getValue(), majorUser.getUuid(), ProcessUserType.MAJOR.getValue()));
        processTaskMapper.updateProcessTaskStepMajorUserAndStatus(new ProcessTaskStepUserVo(processTaskStep.getId(), majorUser.getUuid(), majorUser.getUserName(), ProcessTaskStepUserStatus.DOING.getValue()));
        changeProcessTaskStepStatusToRunning(processTaskStep);

    }

}
