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

package neatlogic.module.process.sql.core.structure;

import neatlogic.framework.auth.core.AuthActionChecker;
import neatlogic.framework.condition.core.ConditionHandlerFactory;
import neatlogic.framework.fulltextindex.dto.fulltextindex.FullTextIndexWordOffsetVo;
import neatlogic.framework.fulltextindex.utils.FullTextIndexUtil;
import neatlogic.framework.process.auth.PROCESSTASK_MODIFY;
import neatlogic.framework.process.column.core.IProcessTaskColumn;
import neatlogic.framework.process.column.core.ProcessTaskColumnFactory;
import neatlogic.framework.process.condition.core.IProcessTaskCondition;
import neatlogic.framework.process.constvalue.ProcessWorkcenterInitType;
import neatlogic.framework.process.workcenter.dto.JoinTableColumnVo;
import neatlogic.framework.process.workcenter.dto.WorkcenterTheadVo;
import neatlogic.framework.process.workcenter.dto.WorkcenterVo;
import neatlogic.framework.process.workcenter.table.ProcessTaskSqlTable;
import neatlogic.framework.process.workcenter.table.constvalue.ProcessSqlTypeEnum;
import neatlogic.framework.process.workcenter.table.util.SqlTableUtil;
import neatlogic.module.process.condition.handler.ProcessTaskStartTimeCondition;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static neatlogic.framework.common.util.CommonUtil.distinctByKey;

public abstract class WorkcenterProcessSqlBase extends ProcessSqlBase<WorkcenterVo> {
    Logger logger = LoggerFactory.getLogger(WorkcenterProcessSqlBase.class);

    /**
     * @Description: 根据条件获取需要的表
     * @Author: 89770
     * @Date: 2021/1/20 16:36
     * @Params: []
     * @Returns: void
     **/
    protected void buildJoinTableOfConditionSql(StringBuilder sb, WorkcenterVo workcenterVo) {
        List<JoinTableColumnVo> joinTableColumnList = getJoinTableOfCondition(sb, workcenterVo);
        //我的待办 条件
        if (workcenterVo.getConditionConfig().getIntValue("isProcessingOfMine") == 1) {
            List<JoinTableColumnVo> handlerJoinTableColumnList = SqlTableUtil.getProcessingOfMineJoinTableSql();
            joinTableColumnList.addAll(handlerJoinTableColumnList);
        }
        buildFromJoinSql(sb, workcenterVo, joinTableColumnList);
    }

    /**
     * 补充主体sql
     *
     * @param sqlSb        sql
     * @param workcenterVo 工单中心参数
     */
    protected void buildFromJoinSql(StringBuilder sqlSb, WorkcenterVo workcenterVo, List<JoinTableColumnVo> joinTableColumnList) {
        //补充排序需要的表
        joinTableColumnList.addAll(getJoinTableOfOrder(workcenterVo, joinTableColumnList));
        sqlSb.append(" from  processtask pt ");
        joinTableColumnList = joinTableColumnList.stream().filter(distinctByKey(JoinTableColumnVo::getHash)).collect(Collectors.toList());
        for (JoinTableColumnVo joinTableColumn : joinTableColumnList) {
            sqlSb.append(joinTableColumn.toSqlString());
        }
    }

    /**
     * @Description: 补充排序需要 join 的表
     * @Author: 89770
     * @Date: 2021/1/26 20:36
     * @Params: [sqlDecoratorVo, joinTableKeyList]
     * @Returns: java.util.List<neatlogic.framework.process.workcenter.dto.JoinTableColumnVo>
     **/
    protected List<JoinTableColumnVo> getJoinTableOfOrder(WorkcenterVo workcenterVo, List<JoinTableColumnVo> joinTableColumnList) {
        JSONObject sortConfig = workcenterVo.getSortConfig();
        if (MapUtils.isNotEmpty(sortConfig)) {
            for (Map.Entry<String, Object> entry : sortConfig.entrySet()) {
                String handler = entry.getKey();
                IProcessTaskColumn column = ProcessTaskColumnFactory.getHandler(handler);
                if (column != null && column.getIsSort()) {
                    List<JoinTableColumnVo> handlerJoinTableColumnList = column.getJoinTableColumnList();
                    for (JoinTableColumnVo handlerJoinTableColumn : handlerJoinTableColumnList) {
                        handlerJoinTableColumn.getHash();
                        joinTableColumnList.add(handlerJoinTableColumn);
                    }
                }
            }
        }
        return joinTableColumnList;
    }

    /**
     * 根据工单中心字段 补充join table
     *
     * @param sb           sql
     * @param workcenterVo 工单中心参数
     */
    protected void getJoinTableOfColumn(StringBuilder sb, WorkcenterVo workcenterVo) {
        List<JoinTableColumnVo> joinTableColumnList = new ArrayList<>();
        //根据接口入参的返回需要的columnList,然后获取需要关联的tableList
        Map<String, IProcessTaskColumn> columnComponentMap = ProcessTaskColumnFactory.columnComponentMap;
        //循环所有需要展示的字段
        if (CollectionUtils.isNotEmpty(workcenterVo.getTheadVoList())) {
            for (WorkcenterTheadVo theadVo : workcenterVo.getTheadVoList()) {
                //去掉沒有勾选的thead
                if (theadVo.getIsShow() != 1) {
                    continue;
                }
                if (columnComponentMap.containsKey(theadVo.getName())) {
                    joinTableColumnList.addAll(getJoinTableColumnList(columnComponentMap, theadVo.getName()));
                }
            }
        }
        buildFromJoinSql(sb, workcenterVo, joinTableColumnList);
    }

    /**
     * 固定条件
     *
     * @param sqlSb        sql builder
     * @param workcenterVo 工单入参
     */
    protected void buildCommonConditionWhereSql(StringBuilder sqlSb, WorkcenterVo workcenterVo) {
        //上报时间
        ProcessTaskStartTimeCondition startTimeSqlCondition = (ProcessTaskStartTimeCondition) ConditionHandlerFactory.getHandler("starttime");
        JSONObject startTimeCondition = workcenterVo.getConditionConfig().getJSONObject("startTimeCondition");
        if (startTimeCondition == null) {
            startTimeCondition = JSONObject.parseObject("{\"timeRange\":\"1\",\"timeUnit\":\"year\"}");//默认展示一年
        }
        startTimeSqlCondition.getDateSqlWhere(startTimeCondition, sqlSb, new ProcessTaskSqlTable().getShortName(), ProcessTaskSqlTable.FieldEnum.START_TIME.getValue());
        //我的待办
        if (workcenterVo.getConditionConfig().getIntValue("isProcessingOfMine") == 1) {
            sqlSb.append(" and ");
            IProcessTaskCondition sqlCondition = (IProcessTaskCondition) ConditionHandlerFactory.getHandler("processingofmine");
            sqlCondition.getSqlConditionWhere(null, 0, sqlSb);
        }
        //keyword搜索框搜索 idList 过滤
        if (CollectionUtils.isNotEmpty(workcenterVo.getKeywordConditionList())) {
            for (Object obj : workcenterVo.getKeywordConditionList()) {
                JSONObject condition = JSONObject.parseObject(obj.toString());
                //title serialNumber 全词匹配
                if (ProcessTaskSqlTable.FieldEnum.TITLE.getValue().equals(condition.getString("name"))) {
                    sqlSb.append(String.format(" AND pt.title in ('%s') ", condition.getJSONArray("valueList").stream().map(Object::toString).collect(Collectors.joining("','"))));
                } else if (ProcessTaskSqlTable.FieldEnum.SERIAL_NUMBER.getValue().equals(condition.getString("name"))) {
                    sqlSb.append(String.format(" AND pt.serial_number in ('%s') ", condition.getJSONArray("valueList").stream().map(Object::toString).collect(Collectors.joining("','"))));
                } else if (ProcessTaskSqlTable.FieldEnum.ID.getValue().equals(condition.getString("name"))) {
                    sqlSb.append(String.format(" AND pt.id in ('%s') ", condition.getJSONArray("valueList").stream().map(Object::toString).collect(Collectors.joining("','"))));
                }else {
                    try {
                        List<FullTextIndexWordOffsetVo> wordOffsetVoList = FullTextIndexUtil.sliceWord(condition.getJSONArray("valueList").stream().map(Object::toString).collect(Collectors.joining("")));
                        String contentWord = wordOffsetVoList.stream().map(FullTextIndexWordOffsetVo::getWord).collect(Collectors.joining("','"));
                        sqlSb.append(String.format("  AND EXISTS (SELECT 1 FROM `fulltextindex_word` fw JOIN fulltextindex_field_process ff ON fw.id = ff.`word_id` JOIN `fulltextindex_target_process` ft ON ff.`target_id` = ft.`target_id` WHERE ff.`target_id` = pt.id  AND ft.`target_type` = 'processtask' AND ff.`target_field` = '%s' AND fw.word IN ('%s') )", condition.getString("name"), contentWord));
                    } catch (IOException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        }
        //隐藏工单 过滤
        Boolean isHasProcessTaskAuth = AuthActionChecker.check(PROCESSTASK_MODIFY.class.getSimpleName());
        if (!isHasProcessTaskAuth) {
            sqlSb.append(" and pt.is_show = 1 ");
        }
        // 过滤掉已删除的工单
        sqlSb.append(" and (pt.is_deleted is null or pt.is_deleted != 1)");
    }

    /**
     * 只有”我的草稿“分类才显示工单状态”未提交“的工单
     * 不是出厂"我的草稿"&&sql 条件不含有 'draft'（因为只有我的草稿分类 工单状态条件才含有"未提交"状态）&& 需是 "DISTINCT_ID"、"TOTAL_COUNT"和"LIMIT_COUNT" 类型
     *
     * @param sqlSb        sql builder
     * @param workcenterVo 工单入参
     */
    protected void draftCondition(StringBuilder sqlSb, WorkcenterVo workcenterVo) {
        if (!Objects.equals(ProcessWorkcenterInitType.DRAFT_PROCESSTASK.getValue(), workcenterVo.getUuid())
                && !sqlSb.toString().contains("draft")
                && Arrays.asList(ProcessSqlTypeEnum.DISTINCT_ID.getValue(), ProcessSqlTypeEnum.TOTAL_COUNT.getValue(), ProcessSqlTypeEnum.LIMIT_COUNT.getValue()).contains(workcenterVo.getSqlFieldType())) {
            sqlSb.append(" and pt.status != 'draft' ");
        }
    }

    /**
     * @Description: 构造column sql
     * @Author: 89770
     * @Date: 2021/1/19 16:32
     * @Params: [sqlSb, workcenterVo]
     * @Returns: void
     **/
    protected void buildField(StringBuilder sqlSb, WorkcenterVo workcenterVo) {
        Map<String, IProcessTaskColumn> columnComponentMap = ProcessTaskColumnFactory.columnComponentMap;
        List<String> columnList = new ArrayList<>();
        for (WorkcenterTheadVo theadVo : workcenterVo.getTheadVoList()) {
            //去掉沒有勾选的thead
            if (theadVo.getIsShow() != 1) {
                continue;
            }
            getColumnSqlList(columnComponentMap, columnList, theadVo.getName(), false);
        }
        //查询是否隐藏
        columnList.add(String.format(" %s.%s as %s ", new ProcessTaskSqlTable().getShortName(), ProcessTaskSqlTable.FieldEnum.IS_SHOW.getValue(), ProcessTaskSqlTable.FieldEnum.IS_SHOW.getValue()));
        columnList.add(String.format(" %s.%s as %s ", new ProcessTaskSqlTable().getShortName(), ProcessTaskSqlTable.FieldEnum.ID.getValue(), ProcessTaskSqlTable.FieldEnum.ID.getValue()));
        sqlSb.append(String.join(",", columnList));
    }
}
