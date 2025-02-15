/*
Copyright(c) 2023 NeatLogic Co., Ltd. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package neatlogic.module.process.api.task;

import neatlogic.framework.auth.core.AuthAction;
import neatlogic.framework.common.constvalue.ApiParamType;
import neatlogic.framework.process.auth.PROCESS_BASE;
import neatlogic.framework.process.dao.mapper.ProcessTaskStepTaskMapper;
import neatlogic.framework.process.dao.mapper.task.TaskMapper;
import neatlogic.framework.process.dto.TaskConfigVo;
import neatlogic.framework.process.exception.processtask.task.TaskConfigIsInvokedException;
import neatlogic.framework.process.exception.processtask.task.TaskConfigNotFoundException;
import neatlogic.framework.restful.annotation.*;
import neatlogic.framework.restful.constvalue.OperationTypeEnum;
import neatlogic.framework.restful.core.privateapi.PrivateApiComponentBase;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service

@AuthAction(action = PROCESS_BASE.class)
@OperationType(type = OperationTypeEnum.DELETE)
public class TaskDeleteApi extends PrivateApiComponentBase{
	@Resource
	TaskMapper taskMapper;
	@Resource
	ProcessTaskStepTaskMapper processTaskStepTaskMapper;
	@Override
	public String getToken() {
		return "task/delete";
	}

	@Override
	public String getName() {
		return "删除子任务";
	}

	@Override
	public String getConfig() {
		return null;
	}

	@Input({
			@Param(name = "id", type = ApiParamType.LONG, isRequired = true, desc = "任务id"),
	})
	@Output({

	})
	@Description(desc = "删除子任务接口")
	@Override
	public Object myDoService(JSONObject jsonObj) throws Exception {
		Long taskId = jsonObj.getLong("id");
		TaskConfigVo taskConfigTmp = taskMapper.getTaskConfigById(taskId);
		if (taskConfigTmp == null) {
			throw new TaskConfigNotFoundException(taskId.toString());
		}
		//判断依赖能否删除
		if(processTaskStepTaskMapper.getInvokedCountByTaskConfigId(taskId)>0){
			throw new TaskConfigIsInvokedException(taskConfigTmp.getName());
		}
		taskMapper.deleteTaskConfigById(taskId);
		return null;
	}

}
