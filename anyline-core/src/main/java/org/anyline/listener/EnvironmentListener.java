/*
 * Copyright 2006-2023 www.anyline.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.anyline.listener;

import org.anyline.util.ConfigTable;
import org.noear.solon.annotation.Component;
import org.noear.solon.core.event.AppLoadEndEvent;
import org.noear.solon.core.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

@Component("anyline.listener.EnvironmentListener")
public class EnvironmentListener implements EventListener<AppLoadEndEvent> {
    private Logger log = LoggerFactory.getLogger(EnvironmentListener.class);


    @Override
    public void onEvent(AppLoadEndEvent event) {
        Field[] fields = ConfigTable.class.getDeclaredFields();
        for(Field field:fields){
            String name = field.getName();
            //TODO 配置文件 赋值ConfigTable属性
           /* String value = BeanUtil.value("anyline", environment, "." + name);
            if(BasicUtil.isNotEmpty(value)) {
                if(Modifier.isFinal(field.getModifiers())){
                    continue;
                }
                BeanUtil.setFieldValue(null, field, value);
            }*/
        }
    }
}
