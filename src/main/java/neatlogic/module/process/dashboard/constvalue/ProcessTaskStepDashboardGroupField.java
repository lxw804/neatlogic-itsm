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
import neatlogic.framework.util.I18n;

public enum ProcessTaskStepDashboardGroupField implements IDashboardGroupField {
    EVERY_DAY("stepEveryday", new I18n("每天")),
    EVERY_MONTH("stepEveryMonth", new I18n("每月")),
    EVERY_QUARTER("stepEveryQuarter", new I18n("每季度"));
    private final String value;
    private final I18n text;

    ProcessTaskStepDashboardGroupField(String _value, I18n _text) {
        value = _value;
        text = _text;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public String getText() {
        return text.toString();
    }

    public static String getValue(String _value) {
        for (ProcessTaskStepDashboardGroupField s : ProcessTaskStepDashboardGroupField.values()) {
            if (s.getValue().equals(_value)) {
                return s.getValue();
            }
        }
        return null;
    }
}
