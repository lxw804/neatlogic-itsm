package neatlogic.module.process.groupsearch;

import neatlogic.framework.common.dto.ValueTextVo;
import neatlogic.framework.process.constvalue.ProcessTaskGroupSearch;
import neatlogic.framework.process.constvalue.ProcessUserType;
import neatlogic.framework.process.dao.mapper.task.TaskMapper;
import neatlogic.framework.process.dto.TaskConfigVo;
import neatlogic.framework.restful.groupsearch.core.IGroupSearchHandler;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ProcessUserTypeGroupHandler implements IGroupSearchHandler {
    @Override
    public String getName() {
        return ProcessTaskGroupSearch.PROCESSUSERTYPE.getValue();
    }

    @Override
    public String getHeader() {
        return getName() + "#";
    }

    @Resource
    TaskMapper taskMapper;

    @SuppressWarnings("unchecked")
    @Override
    public <T> List<T> search(JSONObject jsonObj) {
        List<Object> includeList = jsonObj.getJSONArray("includeList");
        List<Object> excludeList = jsonObj.getJSONArray("excludeList");
        if (CollectionUtils.isEmpty(includeList)) {
            includeList = new ArrayList<Object>();
        }
        List<String> includeStrList = includeList.stream().map(Object::toString).collect(Collectors.toList());
        List<String> valuelist = new ArrayList<>();
        List<ValueTextVo> userTypeList = new ArrayList<>();
        for (ProcessUserType s : ProcessUserType.values()) {
            if (s.getIsShow() && s.getText().contains(jsonObj.getString("keyword"))) {
                String value = getHeader() + s.getValue();
                if (!valuelist.contains(value)) {
                    valuelist.add(value);
                    userTypeList.add(new ValueTextVo(value, s.getText()));
                }
            }
            if (includeStrList.contains(getHeader() + s.getValue())) {
                if (userTypeList.stream().noneMatch(o -> Objects.equals(o.getValue(), s.getValue()))) {
                    String value = getHeader() + s.getValue();
                    if (!valuelist.contains(value)) {
                        valuelist.add(value);
                        userTypeList.add(new ValueTextVo(value, s.getText()));
                    }
                }
            }
        }
        //任务
        if (CollectionUtils.isNotEmpty(excludeList) && !excludeList.contains("processUserType#" + ProcessUserType.MINOR.getValue())) {
            TaskConfigVo configParam = new TaskConfigVo();
            configParam.setKeyword(jsonObj.getString("keyword"));
            List<TaskConfigVo> taskConfigVoList = taskMapper.searchTaskConfig(configParam);
            if (CollectionUtils.isNotEmpty(taskConfigVoList)) {
                for (TaskConfigVo configVo : taskConfigVoList) {
                    String value = getHeader() + configVo.getId().toString();
                    if (!valuelist.contains(value)) {
                        valuelist.add(value);
                        userTypeList.add(new ValueTextVo(value, configVo.getName() + "处理人"));
                    }
                }
            }
        }
        return (List<T>) userTypeList;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> List<T> reload(JSONObject jsonObj) {
        List<ValueTextVo> userTypeList = new ArrayList<>();
        List<String> valueList = jsonObj.getJSONArray("valueList").toJavaList(String.class);
        if (CollectionUtils.isNotEmpty(valueList)) {
            for (String value : valueList) {
                if (value.startsWith(getHeader())) {
                    String realValue = value.replace(getHeader(), "");
                    String text = ProcessUserType.getText(realValue);
                    if (StringUtils.isNotBlank(text)) {
                        userTypeList.add(new ValueTextVo(value, text));
                    }
                }
            }
            List<TaskConfigVo> configVoList = taskMapper.getTaskConfigByIdList(JSONArray.parseArray(JSONArray.toJSONString(valueList.stream().map(v -> v.replace(getHeader(), "")).collect(Collectors.toList()))));
            if (CollectionUtils.isNotEmpty(configVoList)) {
                configVoList.forEach(o -> {
                    userTypeList.add(new ValueTextVo(getHeader() + o.getId().toString(), o.getName() + "处理人"));
                });
            }
        }
        return (List<T>) userTypeList;
    }

    @Override
    public <T> JSONObject repack(List<T> userTypeList) {
        JSONObject userTypeObj = new JSONObject();
        userTypeObj.put("value", "processUserType");
        userTypeObj.put("text", "工单干系人");
        userTypeObj.put("sort", getSort());
        userTypeObj.put("dataList", userTypeList);
        return userTypeObj;
    }

    @Override
    public int getSort() {
        return 1;
    }

    @Override
    public Boolean isLimit() {
        return false;
    }
}
