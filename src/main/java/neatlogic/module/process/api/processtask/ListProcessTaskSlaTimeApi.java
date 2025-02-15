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

package neatlogic.module.process.api.processtask;

import neatlogic.framework.auth.core.AuthAction;
import neatlogic.framework.common.constvalue.ApiParamType;
import neatlogic.framework.process.auth.PROCESS_BASE;
import neatlogic.framework.process.dto.ProcessTaskSlaTimeVo;
import neatlogic.framework.restful.annotation.Input;
import neatlogic.framework.restful.annotation.OperationType;
import neatlogic.framework.restful.annotation.Output;
import neatlogic.framework.restful.annotation.Param;
import neatlogic.framework.restful.constvalue.OperationTypeEnum;
import neatlogic.framework.restful.core.privateapi.PrivateApiComponentBase;
import neatlogic.framework.util.TableResultUtil;
import neatlogic.module.process.service.ProcessTaskService;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
@AuthAction(action = PROCESS_BASE.class)
@OperationType(type = OperationTypeEnum.SEARCH)
public class ListProcessTaskSlaTimeApi extends PrivateApiComponentBase {

    @Resource
    private ProcessTaskService processTaskService;

    @Override
    public String getToken() {
        return "processtask/slatime/list";
    }

    @Override
    public String getName() {
        return "工单时效列表";
    }

    @Override
    public String getConfig() {
        return null;
    }

    @Input({
            @Param(name = "slaIdList", type = ApiParamType.JSONARRAY, isRequired = true, minSize = 1, desc = "时效ID列表")
    })
    @Output({
            @Param(name = "tbodyList", explode = ProcessTaskSlaTimeVo[].class, desc = "时效列表")
    })
    @Override
    public Object myDoService(JSONObject paramObj) throws Exception {
        JSONArray slaIdArray = paramObj.getJSONArray("slaIdList");
        List<Long> slaIdList = slaIdArray.toJavaList(Long.class);
        List<ProcessTaskSlaTimeVo> processTaskSlaTimeList = processTaskService.getSlaTimeListBySlaIdList(slaIdList);
        return TableResultUtil.getResult(processTaskSlaTimeList);
    }
}
