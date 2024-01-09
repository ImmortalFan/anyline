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


package org.anyline.data.jdbc.loader;

import org.anyline.data.datasource.DatasourceHolder;
import org.anyline.data.jdbc.datasource.JDBCDatasourceHolder;
import org.anyline.data.jdbc.runtime.JDBCRuntimeHolder;
import org.anyline.data.listener.DatasourceLoader;
import org.noear.solon.annotation.Component;
import org.noear.solon.core.AppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

@Component("anyline.data.datasource.loader.jdbc")
public class JDBCDatasourceLoader implements DatasourceLoader {
    public static Logger log = LoggerFactory.getLogger(JDBCDatasourceLoader.class);

    public List<String> load(AppContext context){
        List<String> list = new ArrayList<>();
        JDBCRuntimeHolder.init(context);
        JDBCDatasourceHolder.loadCache();
        boolean loadDefault = true; //是否需要加载default
        if(!DatasourceHolder.contains("default")){
            //如果还没有注册默认数据源
            // 项目中可以提前注册好默认数据源 如通过@Configuration注解先执行注册 也可以在spring启动完成后覆盖默认数据源
            JdbcTemplate jdbc = null;
            try{
                jdbc = SpringContextUtil.getBean(JdbcTemplate.class);
            }catch (Exception e){}

            if(null != jdbc){
                JDBCRuntimeHolder.reg("default", jdbc, null);
                loadDefault = false;
            }else{
                DataSource datasource = null;
                try{
                    datasource = SpringContextUtil.getBean(DataSource.class);
                }catch (Exception e){}
                if(null != datasource){
                    try {
                        JDBCDatasourceHolder.reg("default", datasource, false);
                        loadDefault = false;
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }else{
            loadDefault = false;
        }
        list.addAll(load(context, "spring.datasource", loadDefault));
        list.addAll(load(context, "anyline.datasource", loadDefault));
        //TODO 项目指定一个前缀
        return list;
    }

    //加载配置文件

    /**
     *
     * @param env 配置文件
     * @param head 前缀
     * @param loadDefault 是否加载默认数据源
     * @return keys
     */
    private List<String> load(AppContext env, String head, boolean loadDefault){
        //加载成功的前缀 crm, sso
        List<String> list = new ArrayList<>();
        if(loadDefault) {
            String def = JDBCDatasourceHolder.reg("default", head, env);
            if (null != def) {
                list.add(def);
            }
        }
        //默认数据源
        //多数据源
        // 读取配置文件获取更多数据源 anyline.datasource.list
        String prefixs = env.cfg().getProperty(head + ".list");
        if(null == prefixs){
            //anyline.datasource-list
            prefixs = env.cfg().getProperty(head + "-list");
        }
        if(null != prefixs){
            for (String prefix : prefixs.split(",")) {
                // 多个数据源
                try {
                    //返回 datasource的bean id
                    // sso, anyline.datasource.sso, env
                    String ds = JDBCDatasourceHolder.reg(prefix, head + "." + prefix, env);
                    if(null != ds) {
                        list.add(ds);
                    }
                }catch (Exception e){
                    log.error("[注入数据源失败][type:JDBC][key:{}][msg:{}]", prefix, e.toString());
                }
            }
        }
        return list;
    }
}
