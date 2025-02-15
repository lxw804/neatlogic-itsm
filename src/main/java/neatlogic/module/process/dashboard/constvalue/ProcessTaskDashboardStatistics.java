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

package neatlogic.module.process.dashboard.constvalue;

import neatlogic.framework.dashboard.constvalue.IDashboardGroupField;
import neatlogic.framework.util.$;

public enum ProcessTaskDashboardStatistics implements IDashboardGroupField {
    AVG_HANDLE_COST_TIME("avgCostTime","平均耗时"),
    AVG_RESPONSE_COST_TIME("avgResponseCostTime","平均响应耗时"),
    RESPONSE_PUNCTUALITY("responsePunctuality","响应准时率"),
    HANDLE_PUNCTUALITY("handlePunctuality","处理准时率"),
    ;

    private final String value;
    private final String text;

    ProcessTaskDashboardStatistics(String _value, String _text){
        value = _value;
        text = _text;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public String getText() {
        return $.t(text);
    }

    public static String getValue(String _value) {
        for (ProcessTaskDashboardStatistics s : ProcessTaskDashboardStatistics.values()) {
            if (s.getValue().equals(_value)) {
                return s.getValue();
            }
        }
        return null;
    }
}
