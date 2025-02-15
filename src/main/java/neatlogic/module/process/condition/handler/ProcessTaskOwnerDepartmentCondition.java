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

package neatlogic.module.process.condition.handler;

import neatlogic.framework.common.constvalue.FormHandlerType;
import neatlogic.framework.common.constvalue.ParamType;
import neatlogic.framework.common.constvalue.TeamLevel;
import neatlogic.framework.dao.mapper.TeamMapper;
import neatlogic.framework.dto.TeamVo;
import neatlogic.framework.dto.condition.ConditionVo;
import neatlogic.framework.form.constvalue.FormConditionModel;
import neatlogic.framework.process.condition.core.IProcessTaskCondition;
import neatlogic.framework.process.condition.core.ProcessTaskConditionBase;
import neatlogic.framework.process.constvalue.ConditionConfigType;
import neatlogic.framework.process.constvalue.ProcessFieldType;
import neatlogic.framework.process.dto.ProcessTaskStepVo;
import neatlogic.framework.process.dto.ProcessTaskVo;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ProcessTaskOwnerDepartmentCondition extends ProcessTaskConditionBase implements IProcessTaskCondition {

    @Autowired
    private TeamMapper teamMapper;

    @Override
    public String getName() {
        return "ownerdepartment";
    }

    @Override
    public String getDisplayName() {
        return "上报人部门";
    }

	@Override
	public String getHandler(FormConditionModel processWorkcenterConditionType) {
		return FormHandlerType.SELECT.toString();
	}

    @Override
    public String getType() {
        return ProcessFieldType.COMMON.getValue();
    }

    @Override
    public JSONObject getConfig(ConditionConfigType type) {
        JSONObject config = new JSONObject();
        config.put("type", FormHandlerType.SELECT.toString());
        config.put("search", true);
        config.put("dynamicUrl", "/api/rest/team/list/forselect?level=department");
        config.put("rootName", "tbodyList");
        config.put("valueName", "uuid");
        config.put("textName", "name");
        config.put("multiple", true);
        config.put("value", "");
        config.put("defaultValue", "");
        /** 以下代码是为了兼容旧数据结构，前端有些地方还在用 **/
        config.put("isMultiple", true);
        JSONObject mappingObj = new JSONObject();
        mappingObj.put("value", "uuid");
        mappingObj.put("text", "name");
        config.put("mapping", mappingObj);
        return config;
    }

    @Override
    public Integer getSort() {
        return 11;
    }

    @Override
    public ParamType getParamType() {
        return ParamType.ARRAY;
    }

    @Override
    public Object valueConversionText(Object value, JSONObject config) {
        if (value != null) {
            if (value instanceof String) {
                TeamVo teamVo = teamMapper.getTeamByUuid((String) value);
                if (teamVo != null) {
                    return teamVo.getName();
                }
            } else if (value instanceof List) {
                List<String> valueList = JSON.parseArray(JSON.toJSONString(value), String.class);
                List<String> textList = new ArrayList<>();
                for (String valueStr : valueList) {
                    TeamVo teamVo = teamMapper.getTeamByUuid(valueStr);
                    if (teamVo != null) {
                        textList.add(teamVo.getName());
                    } else {
                        textList.add(valueStr);
                    }
                }
                return String.join("、", textList);
            }
        }
        return value;
    }

    @Override
    public void getSqlConditionWhere(List<ConditionVo> conditionList, Integer index, StringBuilder sqlSb) {

    }

    @Override
    public Object getConditionParamData(ProcessTaskStepVo processTaskStepVo) {
        ProcessTaskVo processTaskVo = processTaskMapper.getProcessTaskById(processTaskStepVo.getProcessTaskId());
        if (processTaskVo == null) {
            return null;
        }
        List<String> teamUuidList = teamMapper.getTeamUuidListByUserUuid(processTaskVo.getOwner());
        if (CollectionUtils.isNotEmpty(teamUuidList)) {
            Set<String> upwardUuidSet = new HashSet<>();
            List<TeamVo> teamList = teamMapper.getTeamByUuidList(teamUuidList);
            for (TeamVo teamVo : teamList) {
                String upwardUuidPath = teamVo.getUpwardUuidPath();
                if (StringUtils.isNotBlank(upwardUuidPath)) {
                    String[] upwardUuidArray = upwardUuidPath.split(",");
                    for (String upwardUuid : upwardUuidArray) {
                        upwardUuidSet.add(upwardUuid);
                    }
                }
            }
            if (CollectionUtils.isNotEmpty(upwardUuidSet)) {
                List<TeamVo> upwardTeamList = teamMapper.getTeamByUuidList(new ArrayList<>(upwardUuidSet));
                List<String> departmentUuidList = new ArrayList<>();
                for (TeamVo teamVo : upwardTeamList) {
                    if (TeamLevel.DEPARTMENT.getValue().equals(teamVo.getLevel())) {
                        departmentUuidList.add(teamVo.getUuid());
                    }
                }
                return departmentUuidList;
            }
        }
        return null;
    }
}
