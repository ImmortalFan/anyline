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



package org.anyline.data.datasource.init;

import org.anyline.data.datasource.DataSourceLoader;
import org.anyline.data.runtime.DataRuntime;
import org.anyline.util.ConfigTable;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractDataSourceLoader implements DataSourceLoader {

    /**
     *
     * @param head 前缀
     * @param loadDefault 是否加载默认数据源
     * @return keys
     */
    protected List<String> load(String head, boolean loadDefault) {
        //加载成功的前缀 crm, sso
        List<String> list = new ArrayList<>();
        if(loadDefault) {
            //上下文初始化前后会调用 两次第二次就不执行加载了
            if(!ConfigTable.worker.containsBean(DataRuntime.ANYLINE_SERVICE_BEAN_PREFIX+"default")) {
                String def = holder().create("default", head);
                if (null != def) {
                    list.add(def);
                }
            }
        }
        //默认数据源
        //多数据源
        // 读取配置文件获取更多数据源 anyline.datasource.list
        String prefixs = ConfigTable.environment().string(null, head + ".list");
        if(null == prefixs) {
            //anyline.datasource-list
            prefixs = ConfigTable.environment().string(null,head + "-list");
        }
        if(null != prefixs) {
            for (String prefix : prefixs.split(",")) {
                // 多个数据源
                try {
                    //返回 datasource的bean id
                    // sso, anyline.datasource.sso, env
                    //上下文初始化前后会调用 两次第二次就不执行加载了
                    if(!ConfigTable.worker.containsBean(DataRuntime.ANYLINE_SERVICE_BEAN_PREFIX+prefix)) {
                        String datasource =  holder().create(prefix, head + "." + prefix);
                        if(null != datasource) {
                            list.add(datasource);
                        }
                    }
                }catch (Exception e) {
                    log.error("[注入数据源失败][type:Nebula][key:{}][msg:{}]", prefix, e.toString());
                }
            }
        }
        return list;
    }
}
