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

package neatlogic.module.process.stephandler.component;

import neatlogic.framework.asynchronization.threadlocal.ConditionParamContext;
import neatlogic.framework.asynchronization.threadlocal.UserContext;
import neatlogic.framework.common.constvalue.SystemUser;
import neatlogic.framework.dto.condition.ConditionConfigVo;
import neatlogic.framework.process.condition.core.ProcessTaskConditionFactory;
import neatlogic.framework.process.constvalue.*;
import neatlogic.framework.process.dto.ProcessTaskStepRelVo;
import neatlogic.framework.process.dto.ProcessTaskStepVo;
import neatlogic.framework.process.dto.ProcessTaskStepWorkerVo;
import neatlogic.framework.process.dto.RelExpressionVo;
import neatlogic.framework.process.exception.processtask.ProcessTaskException;
import neatlogic.framework.process.stephandler.core.ProcessStepHandlerBase;
import neatlogic.framework.util.RunScriptUtil;
import neatlogic.framework.util.javascript.JavascriptUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ConditionProcessComponent extends ProcessStepHandlerBase {
    static Logger logger = LoggerFactory.getLogger(ConditionProcessComponent.class);

    @Override
    public String getName() {
        return ProcessStepHandlerType.CONDITION.getName();
    }

    @Override
    public String getType() {
        return ProcessStepHandlerType.CONDITION.getType();
    }

    @Override
    public JSONObject getChartConfig() {
        return new JSONObject() {
            {
                this.put("icon", "tsfont-question");
                this.put("shape", "L-triangle:R-triangle");
                this.put("width", 68);
                this.put("height", 68);
                this.put("rdy", 68 / 4);
            }
        };
    }

    @Override
    public ProcessStepMode getMode() {
        return ProcessStepMode.AT;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public String getHandler() {
        return ProcessStepHandlerType.CONDITION.getHandler();
    }

    @Override
    protected int myActive(ProcessTaskStepVo currentProcessTaskStepVo) {
        return 0;
    }

    @Override
    protected int myHandle(ProcessTaskStepVo currentProcessTaskStepVo) {
        /* 设置已完成标记位 **/
        currentProcessTaskStepVo.setIsAllDone(true);
        return 0;
    }

    @Override
    protected Set<Long> myGetNext(ProcessTaskStepVo currentProcessTaskStepVo,
                                  List<Long> nextStepIdList, Long nextStepId) throws ProcessTaskException {
        try {
            UserContext.init(SystemUser.SYSTEM);
            Set<Long> nextStepIdSet = new HashSet<>();
            if (CollectionUtils.isNotEmpty(nextStepIdList)) {
                List<ProcessTaskStepVo> nextStepList = processTaskMapper.getProcessTaskStepListByIdList(nextStepIdList);
                Map<String, ProcessTaskStepVo> processTaskStepMap = nextStepList.stream().collect(Collectors.toMap(ProcessTaskStepVo::getProcessStepUuid, e -> e));
                Map<String, String> processStepNameMap = nextStepList.stream().collect(Collectors.toMap(ProcessTaskStepVo::getProcessStepUuid, ProcessTaskStepVo::getName));
                ProcessTaskStepVo processTaskStepVo = processTaskMapper.getProcessTaskStepBaseInfoById(currentProcessTaskStepVo.getId());
                String stepConfig = selectContentByHashMapper.getProcessTaskStepConfigByHash(processTaskStepVo.getConfigHash());
                if (StringUtils.isNotBlank(stepConfig)) {
                    JSONArray moveonConfigList = (JSONArray) JSONPath.read(stepConfig, "moveonConfigList");
                    if (CollectionUtils.isNotEmpty(moveonConfigList)) {
                        JSONArray ruleList = new JSONArray();
                        List<String> conditionProcessTaskOptions = Arrays.stream(ConditionProcessTaskOptions.values()).map(ConditionProcessTaskOptions::getValue).collect(Collectors.toList());
                        for (int i = 0; i < moveonConfigList.size(); i++) {
                            JSONObject moveonConfig = moveonConfigList.getJSONObject(i);
                            JSONArray targetStepList = moveonConfig.getJSONArray("targetStepList");
                            if (CollectionUtils.isNotEmpty(targetStepList)) {
                                JSONObject ruleObj = new JSONObject();
                                String type = moveonConfig.getString("type");
                                boolean canRun = false;
                                if ("always".equals(type)) {// 直接流转
                                    canRun = true;
                                    ruleObj.putAll(moveonConfig);
                                    ruleObj.put("result", true);
                                } else if ("optional".equals(type)) {// 自定义
                                    JSONArray conditionGroupList = moveonConfig.getJSONArray("conditionGroupList");
                                    if (CollectionUtils.isNotEmpty(conditionGroupList)) {
                                        JSONObject conditionParamData = ProcessTaskConditionFactory.getConditionParamData(conditionProcessTaskOptions, currentProcessTaskStepVo);
//                                    ProcessTaskVo processTaskVo = processTaskService.getProcessTaskDetailById(currentProcessTaskStepVo.getProcessTaskId());
//                                    processTaskVo.setStartProcessTaskStep(processTaskService.getStartProcessTaskStepByProcessTaskId(processTaskVo.getId()));
//                                    processTaskVo.setCurrentProcessTaskStep(currentProcessTaskStepVo);
//                                    JSONObject conditionParamData = ProcessTaskUtil.getProcessFieldData(processTaskVo, true);
                                        try {
//                                        ConditionParamContext.init(conditionParamData).setFormConfig(processTaskVo.getFormConfig()).setTranslate(true);
                                            ConditionParamContext.init(conditionParamData).setTranslate(true);
                                            ConditionConfigVo conditionConfigVo = new ConditionConfigVo(moveonConfig);
                                            String script = conditionConfigVo.buildScript();
                                            // ((false || true) || (true && false) || (true || false))
                                            // System.out.println(JSON.toJSONString(conditionConfigVo));
                                            canRun = RunScriptUtil.runScript(script);
                                            ruleObj.putAll(JSON.parseObject(JSON.toJSONString(conditionConfigVo)));
                                            ruleObj.put("result", canRun);
                                        } catch (Exception e) {
                                            logger.error(e.getMessage(), e);
                                        } finally {
                                            ConditionParamContext.get().release();
                                        }
                                    }
                                } else {
                                    ruleObj.putAll(moveonConfig);
                                    ruleObj.put("result", true);
                                }
                                List<String> targetStepNameList = new ArrayList<>();
                                for (int j = 0; j < targetStepList.size(); j++) {
                                    String targetStepUuid = targetStepList.getString(j);
                                    String stepName = processStepNameMap.get(targetStepUuid);
                                    if (StringUtils.isNotBlank(stepName)) {
                                        targetStepNameList.add(stepName);
                                    }
                                    // 符合条件
                                    if (canRun) {
                                        ProcessTaskStepVo stepVo = processTaskStepMap.get(targetStepUuid);
                                        if (stepVo != null) {
                                            nextStepIdSet.add(stepVo.getId());
                                        }
                                    }
                                }
                                ruleObj.put("type", type);
                                ruleObj.put("targetStepList", targetStepNameList);
                                ruleList.add(ruleObj);
                            }
                        }
                        currentProcessTaskStepVo.getParamObj().put(ProcessTaskAuditDetailType.RULE.getParamName(), ruleList);
                    }
                }
            }

            return nextStepIdSet;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new ProcessTaskException(e.getMessage());
        }
    }

    @Override
    protected int myRedo(ProcessTaskStepVo currentProcessTaskStepVo) {
        return 0;
    }

    @Override
    protected int myComplete(ProcessTaskStepVo currentProcessTaskStepVo) {
        return 1;
    }

    @Override
    protected int myCompleteAudit(ProcessTaskStepVo currentProcessTaskStepVo) {
        if (StringUtils.isNotBlank(currentProcessTaskStepVo.getError())) {
            currentProcessTaskStepVo.getParamObj().put(ProcessTaskAuditDetailType.CAUSE.getParamName(),
                    currentProcessTaskStepVo.getError());
        }
        /* 处理历史记录 **/
        // String action = currentProcessTaskStepVo.getParamObj().getString("action");
        // AuditHandler.audit(currentProcessTaskStepVo, ProcessTaskAuditType.getProcessTaskAuditType(action));
        IProcessStepHandlerUtil.audit(currentProcessTaskStepVo, ProcessTaskAuditType.CONDITION);
        return 1;
    }

    @Override
    protected int myReapproval(ProcessTaskStepVo currentProcessTaskStepVo) throws ProcessTaskException {
        return 0;
    }

    @Override
    protected int myReapprovalAudit(ProcessTaskStepVo currentProcessTaskStepVo) {
        return 0;
    }

    public void makeupFlowJobStepVo(ProcessTaskStepVo flowJobStepVo) {
        if (flowJobStepVo.getRelList() != null && flowJobStepVo.getRelList().size() > 0) {
            for (ProcessTaskStepRelVo relVo : flowJobStepVo.getRelList()) {
                if (!StringUtils.isBlank(relVo.getCondition())) {
                    Pattern pattern;
                    Matcher matcher;
                    StringBuffer temp = new StringBuffer();
                    String regex = "\\$\\{([^}]+)}";
                    pattern = Pattern.compile(regex, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
                    matcher = pattern.matcher(relVo.getCondition());
                    List<String> stepAndKeyList = new ArrayList<>();
                    while (matcher.find()) {
                        matcher.appendReplacement(temp, "map[\"" + matcher.group(1) + "\"]");
                        if (!stepAndKeyList.contains(matcher.group(1))) {
                            stepAndKeyList.add(matcher.group(1));
                        }
                    }
                    matcher.appendTail(temp);

                    StringBuilder script = new StringBuilder();
                    script.append("function run(){");
                    script.append("var map = new Object();");

                    if (stepAndKeyList.size() > 0) {
                        List<RelExpressionVo> relExpressionList = new ArrayList<>();
                        for (String stepAndKey : stepAndKeyList) {
                            if (stepAndKey.contains(".") && stepAndKey.split("\\.").length == 2) {
                                String stepUid = stepAndKey.split("\\.")[0];
                                String key = stepAndKey.split("\\.")[1];
                                RelExpressionVo relExpressionVo = new RelExpressionVo();
                                relExpressionVo.setExpression("${" + stepUid + "." + key + "}");
                                List<String> valueList = new ArrayList<>(); // flowJobMapper.getFlowJobStepNodeParamValueByFlowJobIdUidKey(flowJobStepVo.getFlowJobId(),
                                // stepUid,
                                // key);
                                if (valueList.size() > 0) {
                                    if (valueList.size() > 1) {
                                        script.append("map[\"").append(stepUid).append(".").append(key).append("\"] = [");
                                        StringBuilder v = new StringBuilder("[");
                                        for (int i = 0; i < valueList.size(); i++) {
                                            String value = valueList.get(i);
                                            script.append("\"").append(value).append("\"");
                                            v.append("\"").append(value).append("\"");
                                            if (i < valueList.size() - 1) {
                                                script.append(",");
                                                v.append(",");
                                            }
                                        }
                                        v.append("]");
                                        script.append("];");
                                        relExpressionVo.setValue(v.toString());
                                    } else {
                                        script.append("map[\"").append(stepUid).append(".").append(key).append("\"] = \"").append(valueList.get(0)).append("\";");
                                        relExpressionVo.setValue("\"" + valueList.get(0) + "\"");
                                    }
                                }
                                relExpressionList.add(relExpressionVo);
                            }
                        }
                        relVo.setRelExpressionList(relExpressionList);
                    }
                    script.append("return ").append(temp).append(";");
                    script.append("}");
                    ScriptEngine se = JavascriptUtil.getEngine();
                    try {
                        se.eval(script.toString());
                        Invocable invocableEngine = (Invocable) se;
                        Object callbackvalue = invocableEngine.invokeFunction("run");
                        relVo.setScriptResult(callbackvalue.toString());
                    } catch (Exception ex) {
                        logger.error(ex.getMessage(), ex);
                        relVo.setError(ex.getMessage());
                    }
                }
            }
        }
    }

    @Override
    public int getSort() {
        return 3;
    }

    @Override
    public Boolean isAllowStart() {
        return null;
    }

    @Override
    protected int myAssign(ProcessTaskStepVo currentProcessTaskStepVo, Set<ProcessTaskStepWorkerVo> workerSet) {
        return 0;
    }

    @Override
    protected int myHang(ProcessTaskStepVo currentProcessTaskStepVo) {
        return 0;
    }

    @Override
    protected int myStart(ProcessTaskStepVo currentProcessTaskStepVo) {
        return 0;
    }

    @Override
    protected int myRetreat(ProcessTaskStepVo currentProcessTaskStepVo) {
        return 0;
    }

    @Override
    protected int myAbort(ProcessTaskStepVo currentProcessTaskStepVo) {
        return 0;
    }

    @Override
    protected int myBack(ProcessTaskStepVo currentProcessTaskStepVo) {
        return 0;
    }

    @Override
    protected int myStartProcess(ProcessTaskStepVo processTaskStepVo) {
        return 0;
    }

    @Override
    protected int myRecover(ProcessTaskStepVo currentProcessTaskStepVo) {
        return 0;
    }

    @Override
    protected int myTransfer(ProcessTaskStepVo currentProcessTaskStepVo, List<ProcessTaskStepWorkerVo> workerList) {
        return 0;
    }

    @Override
    protected int mySaveDraft(ProcessTaskStepVo processTaskStepVo) {
        return 0;
    }

    @Override
    protected int myPause(ProcessTaskStepVo currentProcessTaskStepVo) {
        return 0;
    }

    /**
     * 回退输出路径数量
     * -1代表不限制
     *
     * @return
     */
    @Override
    public int getBackwardOutputQuantity() {
        return 0;
    }

}
