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


package org.anyline.data.mongodb.loader;

import org.anyline.data.listener.DatasourceLoader;
import org.anyline.data.mongodb.datasource.MongoDatasourceHolder;
import org.anyline.data.mongodb.runtime.MongoRuntimeHolder;
import org.noear.solon.annotation.Component;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Component("anyline.data.datasource.loader.mongo")
public class MongoDatasourceLoader implements DatasourceLoader {
    public static Logger log = LoggerFactory.getLogger(MongoDatasourceLoader.class);

    public List<String> load(AppContext context){
        List<String> list = new ArrayList<>();
        MongoRuntimeHolder.init(context);
        boolean loadDefault = true;
        list.addAll(load(context.cfg(), "spring.datasource", loadDefault));
        list.addAll(load(context.cfg(), "anyline.datasource", loadDefault));
        //TODO 项目指定一个前缀
        return list;
    }

    //加载配置文件
    private List<String> load(Props env, String head, boolean loadDefault){
        //加载成功的前缀 crm, sso
        List<String> list = new ArrayList<>();
        if(loadDefault) {
            String def = MongoDatasourceHolder.reg("mongodb", head, env);
            if (null != def) {
                list.add(def);
            }
        }
        //默认数据源
        //多数据源
        // 读取配置文件获取更多数据源 anyline.datasource.list
        String prefixs = env.getProperty(head + ".list");
        if(null == prefixs){
            //anyline.datasource-list
            prefixs = env.getProperty(head + "-list");
        }
        if(null != prefixs){
            for (String prefix : prefixs.split(",")) {
                // 多个数据源
                try {
                    //返回 datasource的bean id
                    // sso, anyline.datasource.sso, env
                    String ds = MongoDatasourceHolder.reg(prefix, head + "." + prefix, env);
                    if(null != ds) {
                        list.add(ds);
                    }
                }catch (Exception e){
                    log.error("[注入数据源失败][type:MongoDB][key:{}][msg:{}]", prefix, e.toString());
                }
            }
        }
        return list;
    }
}
