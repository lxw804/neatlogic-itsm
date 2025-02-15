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

package neatlogic.module.process.sql.decorator;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SqlDecoratorChain implements ApplicationContextAware, InitializingBean {

    private ApplicationContext applicationContext;

    public static ISqlDecorator firstSqlDecorator;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Map<String, ISqlDecorator> beansOfTypeMap = applicationContext.getBeansOfType(ISqlDecorator.class);
        if (beansOfTypeMap.size() == 0) {
            return;
        }
        List<ISqlDecorator> decoratorList = beansOfTypeMap.values().stream().sorted((e1, e2) -> e1.getSort() - e2.getSort()).collect(Collectors.toList());
        for (int i = 0; i < decoratorList.size(); i++) {
            ISqlDecorator decoratorHandler = decoratorList.get(i);
            if (i != decoratorList.size() - 1) {
                decoratorHandler.setNextSqlDecorator(decoratorList.get(i + 1));
            }
        }
        firstSqlDecorator = decoratorList.get(0);
    }
}
