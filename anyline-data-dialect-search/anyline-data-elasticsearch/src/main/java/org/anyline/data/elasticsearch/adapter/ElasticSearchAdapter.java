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



package org.anyline.data.elasticsearch.adapter;

import org.anyline.adapter.KeyAdapter;
import org.anyline.annotation.Component;
import org.anyline.data.adapter.DriverAdapter;
import org.anyline.data.adapter.init.AbstractDriverAdapter;
import org.anyline.data.elasticsearch.metadata.ElasticSearchBuilder;
import org.anyline.data.elasticsearch.metadata.ElasticSearchIndex;
import org.anyline.data.elasticsearch.param.ElasticSearchRequestBody;
import org.anyline.data.elasticsearch.run.ElasticSearchRun;
import org.anyline.data.param.ConfigStore;
import org.anyline.data.param.Highlight;
import org.anyline.data.param.init.DefaultConfigStore;
import org.anyline.data.prepare.Condition;
import org.anyline.data.prepare.ConditionChain;
import org.anyline.data.prepare.RunPrepare;
import org.anyline.data.prepare.auto.AutoCondition;
import org.anyline.data.run.*;
import org.anyline.data.runtime.DataRuntime;
import org.anyline.entity.*;
import org.anyline.exception.CommandQueryException;
import org.anyline.exception.NotSupportException;
import org.anyline.metadata.*;
import org.anyline.metadata.adapter.ViewMetadataAdapter;
import org.anyline.metadata.type.DatabaseType;
import org.anyline.metadata.type.TypeMetadata;
import org.anyline.net.HttpResponse;
import org.anyline.proxy.CacheProxy;
import org.anyline.proxy.EntityAdapterProxy;
import org.anyline.util.*;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Component("anyline.data.jdbc.adapter.elasticsearch")
public class ElasticSearchAdapter extends AbstractDriverAdapter implements DriverAdapter {
    private static Logger log = LoggerFactory.getLogger(ElasticSearchAdapter.class);

    @Override
    public DatabaseType type() {
        return DatabaseType.ElasticSearch;
    }

    @Override
    public boolean supportCatalog() {
        return false;
    }

    @Override
    public boolean supportSchema() {
        return false;
    }

    @Override
    public String name(Type type) {
        return null;
    }

    public ElasticSearchAdapter() {
        super();
        for (ElasticSearchTypeMetadataAlias alias : ElasticSearchTypeMetadataAlias.values()) {
            this.alias.put(alias.name(), alias.standard());
            TypeMetadata.Config config = alias.config();
            reg(alias.name(), config);
            reg(alias.standard(), config);
        }
    }

    /* *****************************************************************************************************************
     *
     * 													DML
     *
     * =================================================================================================================
     * INSERT			: 插入
     * UPDATE			: 更新
     * SAVE				: 根据情况插入或更新
     * QUERY			: 查询(RunPrepare/XML/TABLE/VIEW/PROCEDURE)
     * EXISTS			: 是否存在
     * COUNT			: 统计
     * EXECUTE			: 执行(原生SQL及存储过程)
     * DELETE			: 删除
     *
     ******************************************************************************************************************/

    /* *****************************************************************************************************************
     * 													INSERT
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * long insert(DataRuntime runtime, String random, int batch, Table dest, Object data, ConfigStore configs, List<String> columns)
     * [命令合成]
     * public Run buildInsertRun(DataRuntime runtime, int batch, Table dest, Object obj, ConfigStore configs, List<String> columns)
     * public void fillInsertContent(DataRuntime runtime, Run run, Table dest, DataSet set, ConfigStore configs, LinkedHashMap<String, Column> columns)
     * public void fillInsertContent(DataRuntime runtime, Run run, Table dest, Collection list, ConfigStore configs, LinkedHashMap<String, Column> columns)
     * public LinkedHashMap<String, Column> confirmInsertColumns(DataRuntime runtime, Table dest, Object obj, ConfigStore configs, List<String> columns, boolean batch)
     * public String batchInsertSeparator()
     * public boolean supportInsertPlaceholder()
     * protected Run createInsertRun(DataRuntime runtime, Table dest, Object obj, ConfigStore configs, List<String> columns)
     * protected Run createInsertRunFromCollection(DataRuntime runtime, int batch, Table dest, Collection list, ConfigStore configs, List<String> columns)
     * public String generatedKey()
     * [命令执行]
     * long insert(DataRuntime runtime, String random, Object data, ConfigStore configs, Run run, String[] pks);
     * long insert(DataRuntime runtime, String random, Object data, ConfigStore configs, Run run);
     ******************************************************************************************************************/

    /**
     * insert [调用入口]<br/>
     * 执行前根据主键生成器补充主键值, 执行完成后会补齐自增主键值
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param dest 表 如果不提供表名则根据data解析, 表名可以事实前缀&lt;数据源名&gt;表示切换数据源
     * @param data 需要插入入的数据
     * @param columns 需要插入的列，如果不指定则根据data或configs获取注意会受到ConfigTable中是否插入更新空值的几个配置项影响
     *                列可以加前缀<br/>
     *                +:表示必须插入<br/>
     *                -:表示必须不插入<br/>
     *                ?:根据是否有值<br/>
     *
     *        如果没有提供columns, 长度为0也算没有提供<br/>
     *        则解析obj(遍历所有的属性工Key)获取insert列<br/>
     *
     *        如果提供了columns则根据columns获取insert列<br/>
     *
     *        但是columns中出现了添加前缀列, 则解析完columns后, 继续解析obj<br/>
     *
     *        以上执行完后, 如果开启了ConfigTable.IS_AUTO_CHECK_METADATA=true<br/>
     *        则把执行结果与表结构对比, 删除表中没有的列<br/>
     * @return 影响行数
     */
    @Override
    public long insert(DataRuntime runtime, String random, int batch, Table dest, Object data, ConfigStore configs, List<String> columns) {
        return super.insert(runtime, random, batch, dest, data, configs, columns);
    }


    /**
     * insert [命令合成]<br/>
     * 填充inset命令内容(创建批量INSERT RunPrepare)
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param dest 表 如果不提供表名则根据data解析, 表名可以事实前缀&lt;数据源名&gt;表示切换数据源
     * @param obj 需要插入的数据
     * @param columns 需要插入的列，如果不指定则根据data或configs获取注意会受到ConfigTable中是否插入更新空值的几个配置项影响
     * @return Run 最终执行命令 如JDBC环境中的 SQL 与 参数值
     */
    @Override
    public Run buildInsertRun(DataRuntime runtime, int batch, Table dest, Object obj, ConfigStore configs, List<String> columns) {
        return super.buildInsertRun(runtime, batch, dest, obj, configs, columns);
    }

    /**
     * insert [命令合成-子流程]<br/>
     * 填充inset命令内容(创建批量INSERT RunPrepare)
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     * @param dest 表 如果不提供表名则根据data解析, 表名可以事实前缀&lt;数据源名&gt;表示切换数据源
     * @param set 需要插入的数据集合
     * @param columns 需要插入的列，如果不指定则根据data或configs获取注意会受到ConfigTable中是否插入更新空值的几个配置项影响
     */
    @Override
    public void fillInsertContent(DataRuntime runtime, Run run, Table dest, DataSet set, ConfigStore configs, LinkedHashMap<String, Column> columns) {
        super.fillInsertContent(runtime, run, dest, set, configs, columns);
    }

    /**
     * insert [命令合成-子流程]<br/>
     * 填充inset命令内容(创建批量INSERT RunPrepare)
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     * @param dest 表 如果不提供表名则根据data解析, 表名可以事实前缀&lt;数据源名&gt;表示切换数据源
     * @param list 需要插入的数据集合
     * @param columns 需要插入的列，如果不指定则根据data或configs获取注意会受到ConfigTable中是否插入更新空值的几个配置项影响
     */
    @Override
    public void fillInsertContent(DataRuntime runtime, Run run, Table dest, Collection list, ConfigStore configs, LinkedHashMap<String, Column> columns) {
        super.fillInsertContent(runtime, run, dest, list, configs, columns);
    }

    /**
     * insert [命令合成-子流程]<br/>
     * 确认需要插入的列
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param dest 表 如果不提供表名则根据data解析, 表名可以事实前缀&lt;数据源名&gt;表示切换数据源
     * @param obj  Entity或DataRow
     * @param batch  是否批量，批量时不检测值是否为空
     * @param columns 需要插入的列，如果不指定则根据data或configs获取注意会受到ConfigTable中是否插入更新空值的几个配置项影响
     *                列可以加前缀<br/>
     *                +:表示必须插入<br/>
     *                -:表示必须不插入<br/>
     *                ?:根据是否有值<br/>
     *
     *        如果没有提供columns, 长度为0也算没有提供<br/>
     *        则解析obj(遍历所有的属性工Key)获取insert列<br/>
     *
     *        如果提供了columns则根据columns获取insert列<br/>
     *
     *        但是columns中出现了添加前缀列, 则解析完columns后, 继续解析obj<br/>
     *
     *        以上执行完后, 如果开启了ConfigTable.IS_AUTO_CHECK_METADATA=true<br/>
     *        则把执行结果与表结构对比, 删除表中没有的列<br/>
     * @return List
     */
    @Override
    public LinkedHashMap<String, Column> confirmInsertColumns(DataRuntime runtime, Table dest, Object obj, ConfigStore configs, List<String> columns, boolean batch) {
        return super.confirmInsertColumns(runtime, dest, obj, configs, columns, batch);
    }

    /**
     * insert [命令合成-子流程]<br/>
     * 批量插入数据时, 多行数据之间分隔符
     * @return String
     */
    @Override
    public String batchInsertSeparator() {
        return ",";
    }

    /**
     * insert [命令合成-子流程]<br/>
     * 插入数据时是否支持占位符
     * @return boolean
     */
    @Override
    public boolean supportInsertPlaceholder() {
        return true;
    }
    /**
     * insert [命令合成-子流程]<br/>
     * 设置主键值
     * @param obj obj
     * @param value value
     */
    @Override
    protected void setPrimaryValue(Object obj, Object value) {
        super.setPrimaryValue(obj, value);
    }
    /**
     * insert [命令合成-子流程]<br/>
     * 根据entity创建 INSERT RunPrepare由buildInsertRun调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param dest 表 如果不提供表名则根据data解析, 表名可以事实前缀&lt;数据源名&gt;表示切换数据源
     * @param obj 数据
     * @param columns 需要插入的列，如果不指定则根据data或configs获取注意会受到ConfigTable中是否插入更新空值的几个配置项影响
     * @return Run 最终执行命令 如JDBC环境中的 SQL 与 参数值
     */
    @Override
    protected Run createInsertRun(DataRuntime runtime, Table dest, Object obj, ConfigStore configs, List<String> columns) {
        ElasticSearchRun run = new ElasticSearchRun(runtime, dest);
        run.action(ACTION.DML.INSERT);
        run.setMethod("POST");
        String endpoint = dest.getName()+"/_doc/"; // 有没有/后缀都可以
        Object id = null;
        if(obj instanceof DataRow) {
            DataRow row = (DataRow)obj;
            id = row.getPrimaryValue();
            row.remove(row.getPrimaryKey());

        }else{
            id = EntityAdapterProxy.primaryKey(obj.getClass(), true);
        }
        if(BasicUtil.isNotEmpty(id)) {
            endpoint += id;
        }
        run.setEndpoint(endpoint);
        run.getBuilder().append(BeanUtil.object2json(obj));
        return run;
    }

    /**
     * insert [命令合成-子流程]<br/>
     * 根据collection创建 INSERT RunPrepare由buildInsertRun调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param dest 表 如果不提供表名则根据data解析, 表名可以事实前缀&lt;数据源名&gt;表示切换数据源
     * @param list 对象集合
     * @param columns 需要插入的列，如果不指定则根据data或configs获取注意会受到ConfigTable中是否插入更新空值的几个配置项影响
     * @return Run 最终执行命令 如JDBC环境中的 SQL 与 参数值
     */
    @Override
    protected Run createInsertRunFromCollection(DataRuntime runtime, int batch, Table dest, Collection list, ConfigStore configs, List<String> columns) {
        ElasticSearchRun run = new ElasticSearchRun(runtime, dest);
        run.action(ACTION.DML.INSERT);
        run.setMethod("POST");
        String endpoint = null;
        String index_name = null;
        if(null != dest){
            index_name = dest.getName();
        }
        if(BasicUtil.isNotEmpty(index_name)){
            endpoint = index_name+"/_bulk";
        }else{
            endpoint = "*/_bulk";
        }
        run.setEndpoint(endpoint);
        StringBuilder builder = run.getBuilder();
        for(Object item:list){
            String item_index_name = null;
            Object pv = null;
            if(item instanceof DataRow) {
                DataRow row = (DataRow)item;
                item_index_name = row.getTableName();
                pv = row.getPrimaryValue();
            }else{
                item_index_name = EntityAdapterProxy.table(item.getClass(), true);
                pv = EntityAdapterProxy.primaryValue(item, true);
            }
            if(BasicUtil.isEmpty(item_index_name)){
                item_index_name = index_name;
            }
            Map<String, Object> head = new HashMap<>();
            Map<String, Object> head_property = new HashMap<>();
            //{"index":{"_index":"index_user", "_id":"10011"}}
            head.put("index", head_property);
            if(BasicUtil.isNotEmpty(item_index_name)){
                head_property.put("_index", item_index_name);
            }
            if(BasicUtil.isNotEmpty(pv)){
                head_property.put("_id", pv);
            }
            Map<String, Object> map = BeanUtil.object2map(item);
            map.remove("_id");
            builder.append(BeanUtil.map2json(head)).append("\n");
            builder.append(BeanUtil.map2json(map)).append("\n");
        }
        /*POST /your_index/_bulk
{ "index" : { "_id" : "1" } }
{ "field1" : "value1" }
{ "index" : { "_id" : "2" } }
{ "field1" : "value2" }


#批量添加  也可以写上索引名 PUT index_user/_bulk
PUT * /_bulk
        {"index":{"_index":"index_user", "_id":"10011"}}
        {"id":1001, "name":"a b", "age":20}
        {"index":{"_index":"index_user", "_id":"10012"}}
        {"id":1002, "name":"b c", "age":20}
        {"index":{"_index":"index_user", "_id":"10013"}}
        {"id":1003, "name":"c d", "age":30}

#如果没有_id会自动生成随机值
        PUT index_user/_bulk
        {"index":{"_index":"index_user"}}
        {"id":1001, "name":"a b", "age":20}
        {"index":{"_index":"index_user", "_id":"10012"}}
        {"id":1002, "name":"b c", "age":20}
        {"index":{"_index":"index_user"}}
        {"id":1003, "name":"c d", "age":30}


*/
        return run;
    }

    /**
     * insert [after]<br/>
     * 执行insert后返回自增主键的key
     * @return String
     */
    @Override
    public String generatedKey() {
        return super.generatedKey();
    }

    /**
     * insert [命令执行]
     * <br/>
     * 执行完成后会补齐自增主键值
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param data data
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     * @param pks 需要返回的主键
     * @return 影响行数
     */
    @Override
    public long insert(DataRuntime runtime, String random, Object data, ConfigStore configs, Run run, String[] pks) {
        return super.insert(runtime, random, data, configs, run, pks);
    }

    

    /* *****************************************************************************************************************
     * 													UPDATE
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * long update(DataRuntime runtime, String random, int batch, Table dest, Object data, ConfigStore configs, List<String> columns)
     * [命令合成]
     * Run buildUpdateRun(DataRuntime runtime, int batch, Table dest, Object obj, ConfigStore configs, List<String> columns)
     * Run buildUpdateRunFromEntity(DataRuntime runtime, Table dest, Object obj, ConfigStore configs, LinkedHashMap<String, Column> columns)
     * Run buildUpdateRunFromDataRow(DataRuntime runtime, Table dest, DataRow row, ConfigStore configs, LinkedHashMap<String,Column> columns)
     * Run buildUpdateRunFromCollection(DataRuntime runtime, int batch, Table dest, Collection list, ConfigStore configs, LinkedHashMap<String,Column> columns)
     * LinkedHashMap<String,Column> confirmUpdateColumns(DataRuntime runtime, Table dest, DataRow row, ConfigStore configs, List<String> columns)
     * LinkedHashMap<String,Column> confirmUpdateColumns(DataRuntime runtime, Table dest, Object obj, ConfigStore configs, List<String> columns)
     * [命令执行]
     * long update(DataRuntime runtime, String random, Table dest, Object data, ConfigStore configs, Run run)
     ******************************************************************************************************************/
    /**
     * UPDATE [调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param dest 表 如果不提供表名则根据data解析,表名可以事实前缀&lt;数据源名&gt;表示切换数据源
     * @param data 数据
     * @param configs 条件
     * @param columns 需要插入或更新的列，如果不指定则根据data或configs获取注意会受到ConfigTable中是否插入更新空值的几个配置项影响
     *                列可以加前缀<br/>
     *                +:表示必须更新<br/>
     *                -:表示必须不更新<br/>
     *                ?:根据是否有值<br/>
     *
     *        如果没有提供columns,长度为0也算没有提供<br/>
     *        则解析obj(遍历所有的属性工Key)获取insert列<br/>
     *
     *        如果提供了columns则根据columns获取insert列<br/>
     *
     *        但是columns中出现了添加前缀列,则解析完columns后,继续解析obj<br/>
     *
     *        以上执行完后,如果开启了ConfigTable.IS_AUTO_CHECK_METADATA=true<br/>
     *        则把执行结果与表结构对比,删除表中没有的列<br/>
     * @return 影响行数
     */
    @Override
    public long update(DataRuntime runtime, String random, int batch, Table dest, Object data, ConfigStore configs, List<String> columns) {
        return super.update(runtime, random, batch, dest, data, configs, columns);
    }
    /**
     * update [命令合成]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param dest 表 如果不提供表名则根据data解析,表名可以事实前缀&lt;数据源名&gt;表示切换数据源
     * @param obj Entity或DtaRow
     * @param configs 更新条件
     * @param columns 需要插入或更新的列，如果不指定则根据data或configs获取注意会受到ConfigTable中是否插入更新空值的几个配置项影响
     *                列可以加前缀<br/>
     *                +:表示必须更新<br/>
     *                -:表示必须不更新<br/>
     *                ?:根据是否有值<br/>
     *
     *        如果没有提供columns,长度为0也算没有提供<br/>
     *        则解析obj(遍历所有的属性工Key)获取insert列<br/>
     *
     *        如果提供了columns则根据columns获取insert列<br/>
     *
     *        但是columns中出现了添加前缀列,则解析完columns后,继续解析obj<br/>
     *
     *        以上执行完后,如果开启了ConfigTable.IS_AUTO_CHECK_METADATA=true<br/>
     *        则把执行结果与表结构对比,删除表中没有的列<br/>
     * @return Run 最终执行命令 如JDBC环境中的 SQL 与 参数值
     */
    @Override
    public Run buildUpdateRun(DataRuntime runtime, int batch, Table dest, Object obj, ConfigStore configs, List<String> columns) {
        return createInsertRun(runtime, dest, obj, configs, columns);
    }
    @Override
    public Run buildUpdateRunFromEntity(DataRuntime runtime, Table dest, Object obj, ConfigStore configs, LinkedHashMap<String, Column> columns) {
        return super.buildUpdateRunFromEntity(runtime, dest, obj, configs, columns);
    }
    @Override
    public Run buildUpdateRunFromDataRow(DataRuntime runtime, Table dest, DataRow row, ConfigStore configs, LinkedHashMap<String,Column> columns) {
        return createInsertRun(runtime, dest, row, configs, Column.names(columns));
    }
    @Override
    public Run buildUpdateRunFromCollection(DataRuntime runtime, int batch, Table dest, Collection list, ConfigStore configs, LinkedHashMap<String,Column> columns) {
        return super.buildUpdateRunFromCollection(runtime, batch, dest, list, configs, columns);
    }

    /**
     * update [命令合成-子流程]<br/>
     * 确认需要更新的列
     * @param row DataRow
     * @param configs 更新条件
     * @param columns 需要插入或更新的列，如果不指定则根据data或configs获取注意会受到ConfigTable中是否插入更新空值的几个配置项影响
     *                列可以加前缀<br/>
     *                +:表示必须更新<br/>
     *                -:表示必须不更新<br/>
     *                ?:根据是否有值<br/>
     *
     *        如果没有提供columns,长度为0也算没有提供<br/>
     *        则解析obj(遍历所有的属性工Key)获取insert列<br/>
     *
     *        如果提供了columns则根据columns获取insert列<br/>
     *
     *        但是columns中出现了添加前缀列,则解析完columns后,继续解析obj<br/>
     *
     *        以上执行完后,如果开启了ConfigTable.IS_AUTO_CHECK_METADATA=true<br/>
     *        则把执行结果与表结构对比,删除表中没有的列<br/>
     * @return List
     */
    @Override
    public LinkedHashMap<String,Column> confirmUpdateColumns(DataRuntime runtime, Table dest, DataRow row, ConfigStore configs, List<String> columns) {
        return super.confirmUpdateColumns(runtime, dest, row, configs, columns);
    }
    @Override
    public LinkedHashMap<String,Column> confirmUpdateColumns(DataRuntime runtime, Table dest, Object obj, ConfigStore configs, List<String> columns) {
        return super.confirmUpdateColumns(runtime, dest, obj, configs, columns);
    }
    /**
     * update [命令执行]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param dest 表 如果不提供表名则根据data解析,表名可以事实前缀&lt;数据源名&gt;表示切换数据源
     * @param data 数据
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     * @return 影响行数
     */
    @Override
    public long update(DataRuntime runtime, String random, Table dest, Object data, ConfigStore configs, Run run) {
        return super.update(runtime, random, dest, data, configs, run);
    }

    /**
     * save [调用入口]<br/>
     * <br/>
     * 根据是否有主键值确认insert | update<br/>
     * 执行完成后会补齐自增主键值
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param dest 表 如果不提供表名则根据data解析,表名可以事实前缀&lt;数据源名&gt;表示切换数据源
     * @param data 数据
     * @param configs 更新条件
     * @param columns 需要插入或更新的列，如果不指定则根据data或configs获取注意会受到ConfigTable中是否插入更新空值的几个配置项影响
     *                列可以加前缀<br/>
     *                +:表示必须更新<br/>
     *                -:表示必须不更新<br/>
     *                ?:根据是否有值<br/>
     *
     *        如果没有提供columns,长度为0也算没有提供<br/>
     *        则解析obj(遍历所有的属性工Key)获取insert列<br/>
     *
     *        如果提供了columns则根据columns获取insert列<br/>
     *
     *        但是columns中出现了添加前缀列,则解析完columns后,继续解析obj<br/>
     *
     *        以上执行完后,如果开启了ConfigTable.IS_AUTO_CHECK_METADATA=true<br/>
     *        则把执行结果与表结构对比,删除表中没有的列<br/>
     * @return 影响行数
     */
    @Override
    public long save(DataRuntime runtime, String random, Table dest, Object data, ConfigStore configs, List<String> columns) {
        return super.save(runtime, random, dest, data, configs, columns);
    }

    @Override
    protected long saveCollection(DataRuntime runtime, String random, Table dest, Collection<?> data, ConfigStore configs, List<String> columns) {
        return super.saveCollection(runtime, random, dest, data, configs, columns);
    }
    @Override
    protected long saveObject(DataRuntime runtime, String random, Table dest, Object data, ConfigStore configs, List<String> columns) {
        return super.saveObject(runtime, random, dest, data, configs, columns);
    }
    @Override
    protected Boolean checkOverride(Object obj) {
        return super.checkOverride(obj);
    }
    @Override
    protected Map<String,Object> checkPv(Object obj) {
        return super.checkPv(obj);
    }

    /**
     * 是否是可以接收数组类型的值
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     * @param key key
     * @return boolean
     */
    @Override
    protected boolean isMultipleValue(DataRuntime runtime, TableRun run, String key) {
        return super.isMultipleValue(runtime, run, key);
    }
    @Override
    protected boolean isMultipleValue(Column column) {
        return super.isMultipleValue(column);
    }
    /**
     * 过滤掉表结构中不存在的列
     * ES不检测
     * @param table 表
     * @param columns columns
     * @return List
     */
    @Override
    public LinkedHashMap<String, Column> checkMetadata(DataRuntime runtime, Table table, ConfigStore configs, LinkedHashMap<String, Column> columns) {
        return columns;
    }

    /* *****************************************************************************************************************
     * 													QUERY
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * DataSet querys(DataRuntime runtime, String random, RunPrepare prepare, ConfigStore configs, String ... conditions)
     * DataSet querys(DataRuntime runtime, String random, Procedure procedure, PageNavi navi)
     * <T> EntitySet<T> selects(DataRuntime runtime, String random, RunPrepare prepare, Class<T> clazz, ConfigStore configs, String... conditions)
     * List<Map<String,Object>> maps(DataRuntime runtime, String random, RunPrepare prepare, ConfigStore configs, String ... conditions)
     * [命令合成]
     * Run buildQueryRun(DataRuntime runtime, RunPrepare prepare, ConfigStore configs, String ... conditions)
     * List<Run> buildQuerySequence(DataRuntime runtime, boolean next, String ... names)
     * Run fillQueryContent(DataRuntime runtime, Run run)
     * String mergeFinalQuery(DataRuntime runtime, Run run)
     * RunValue createConditionLike(DataRuntime runtime, StringBuilder builder, Compare compare, Object value, boolean placeholder)
     * Object createConditionFindInSet(DataRuntime runtime, StringBuilder builder, String column, Compare compare, Object value, boolean placeholder)
     * StringBuilder createConditionIn(DataRuntime runtime, StringBuilder builder, Compare compare, Object value, boolean placeholder)
     * [命令执行]
     * DataSet select(DataRuntime runtime, String random, boolean system, String table, ConfigStore configs, Run run)
     * List<Map<String,Object>> maps(DataRuntime runtime, String random, ConfigStore configs, Run run)
     * Map<String,Object> map(DataRuntime runtime, String random, ConfigStore configs, Run run) 
     * DataRow sequence(DataRuntime runtime, String random, boolean next, String ... names)
     * List<Map<String,Object>> process(DataRuntime runtime, List<Map<String,Object>> list)
     ******************************************************************************************************************/

    /**
     * query [调用入口]<br/>
     * <br/>
     * 返回DataSet中包含元数据信息，如果性能有要求换成maps
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param prepare 构建最终执行命令的全部参数，包含表（或视图｜函数｜自定义SQL)查询条件 排序 分页等
     * @param configs 过滤条件及相关配置
     * @param conditions  简单过滤条件
     * @return DataSet
     */
    @Override
    public DataSet querys(DataRuntime runtime, String random, RunPrepare prepare, ConfigStore configs, String ... conditions) {
        return super.querys(runtime, random, prepare, configs, conditions);
    }

    /**
     * query procedure [调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param procedure 存储过程
     * @param navi 分页
     * @return DataSet
     */
    @Override
    public DataSet querys(DataRuntime runtime, String random, Procedure procedure, PageNavi navi) {
        return super.querys(runtime, random, procedure, navi);
    }

    /**
     * query [调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param clazz 类
     * @param prepare 构建最终执行命令的全部参数，包含表（或视图｜函数｜自定义SQL)查询条件 排序 分页等
     * @param configs 过滤条件及相关配置
     * @param conditions  简单过滤条件
     * @return EntitySet
     * @param <T> Entity
     */
    @Override
    public <T> EntitySet<T> selects(DataRuntime runtime, String random, RunPrepare prepare, Class<T> clazz, ConfigStore configs, String ... conditions) {
        return super.selects(runtime, random, prepare, clazz, configs, conditions);
    }

    /**
     * select [命令执行-子流程]<br/>
     * DataRow转换成Entity
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param clazz entity class
     * @param table table
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     * @param configs 过滤条件及相关配置
     * @return EntitySet
     * @param <T> entity.class
     *
     */
    @Override
    protected <T> EntitySet<T> select(DataRuntime runtime, String random, Class<T> clazz, Table table, ConfigStore configs, Run run) {
        return super.select(runtime, random, clazz, table, configs, run);
    }

    /**
     * query [调用入口]<br/>
     * <br/>
     * 对性能有要求的场景调用，返回java原生map集合,结果中不包含元数据信息
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param prepare 构建最终执行命令的全部参数，包含表（或视图｜函数｜自定义SQL)查询条件 排序 分页等
     * @param configs 过滤条件及相关配置
     * @param conditions  简单过滤条件
     * @return maps 返回map集合
     */
    @Override
    public List<Map<String,Object>> maps(DataRuntime runtime, String random, RunPrepare prepare, ConfigStore configs, String ... conditions) {
        return super.maps(runtime, random, prepare, configs, conditions);
    }
    /**
     * select[命令合成]<br/> 最终可执行命令<br/>
     * 创建查询SQL
     * @param prepare 构建最终执行命令的全部参数，包含表（或视图｜函数｜自定义SQL)查询条件 排序 分页等
     * @param configs 过滤条件及相关配置
     * @param conditions  简单过滤条件
     * @return Run 最终执行命令 如JDBC环境中的 SQL 与 参数值
     */
    @Override
    public Run buildQueryRun(DataRuntime runtime, RunPrepare prepare, ConfigStore configs, String ... conditions) {
        ElasticSearchRun run = new ElasticSearchRun(runtime, prepare.getTableName());
        run.setMethod("POST");
        String endpoint = null;
        String index_name = prepare.getTableName();
        if(BasicUtil.isNotEmpty(index_name)){
            endpoint = "/" + index_name + "/_search";
        }else{
            endpoint = "/*/_search";
        }
        run.setEndpoint(endpoint);
        run.setPrepare(prepare);
        run.setConfigStore(configs);
        run.addCondition(conditions);
        if(run.checkValid()) {
            //为变量赋值
            run.init();
            //构造最终的查询SQL
            fillQueryContent(runtime, run);
        }
        return run;
    }

    /**
     * 查询序列cur 或 next value
     * @param next  是否生成返回下一个序列 false:cur true:next
     * @param names 序列名
     * @return String
     */
    @Override
    public List<Run> buildQuerySequence(DataRuntime runtime, boolean next, String ... names) {
        return super.buildQuerySequence(runtime, next, names);
    }

    /**
     * select[命令合成-子流程] <br/>
     * 构造查询主体
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     */
    @Override
    public Run fillQueryContent(DataRuntime runtime, Run run) {
        return super.fillQueryContent(runtime, run);
    }
    @Override
    protected Run fillQueryContent(DataRuntime runtime, XMLRun run) {
        return super.fillQueryContent(runtime, run);
    }
    @Override
    protected Run fillQueryContent(DataRuntime runtime, TextRun run) {
        return super.fillQueryContent(runtime, run);
    }
    @Override
    protected Run fillQueryContent(DataRuntime runtime, TableRun run) {
        ElasticSearchRun r = (ElasticSearchRun)run;
        ConditionChain chain = r.getConditionChain();

        ElasticSearchRequestBody request = new ElasticSearchRequestBody();

        DataRow body = new DataRow(KeyAdapter.KEY_CASE.SRC);
        ConfigStore configs = run.getConfigs();
        if(null != configs){
            configs.autoCount(false); //不需要单独计算总行数
            PageNavi navi = configs.getPageNavi();
            if(null != navi){
                body.put("from", navi.getFirstRow());
                body.put("size", navi.getPageRows());
            }
            List<String> columns = configs.columns();
            if(null != columns && !columns.isEmpty()){
                body.put("_source", columns);
            }
            Highlight highlight = configs.getHighlight();
            if(null != highlight){
                LinkedHashMap<String, Highlight> fields = highlight.getFields();
                LinkedHashMap<String, Map> fields_map = new LinkedHashMap<>();
                Map map = BeanUtil.object2map(highlight);
                BeanUtil.clearEmpty(map, true);
                for(String field:fields.keySet()){
                    //field有空的情况也需要保留
                    Highlight field_highlight = fields.get(field);
                    Map field_map = BeanUtil.object2map(field_highlight);
                    BeanUtil.clearEmpty(field_map, true);
                    fields_map.put(field,field_map);
                }
                if(!fields_map.isEmpty()){
                    map.put("fields",fields_map);
                }
                body.put("highlight", map);
            }
        }

        DataRow query = new OriginRow();
        parseCondition(query, chain);
        if(!query.isEmpty()) {
            body.put("query", query);
        }
        OrderStore orderStore = configs.getOrders();
        if(null!= orderStore){
            List<Order> orders = orderStore.getOrders();
            if(null != orders && !orders.isEmpty()){
                DataSet sorts = new DataSet();
                body.put("sort", sorts);
                for(Order order:orders){
                    DataRow sort = new OriginRow();
                    sort.put(order.getColumn()).put("order", order.getType().getCode().toLowerCase());
                    sorts.add(sort);
                }
            }
        }
        if(!body.isEmpty()){
            request.setJson(body.json());
            r.getBuilder().append(body.json());
        }
        return r;
    }
    private DataRow parseCondition(DataRow base, Condition condition){
        DataRow row = null;
        if(condition instanceof ConditionChain) {
            ConditionChain chain = (ConditionChain)condition;
            List<Condition> conditions = chain.getConditions();
            if(null != conditions && !conditions.isEmpty()){
                if(conditions.size() ==1){//只有一个条件
                    parseCondition(base, conditions.get(0));
                }else {
                    row = base;
                    DataRow bool = base.put("bool");
                    DataSet set = null;
                    String join = chain.getJoin().trim();
                    if(BasicUtil.isEmpty(join) || "AND".equalsIgnoreCase(join)){ //默认就是AND
                        Condition second = conditions.get(1);
                        join = second.getJoin().trim();
                    }
                    if ("OR".equalsIgnoreCase(join)) {
                        join ="should";
                    } else if("AND".equalsIgnoreCase(join)){
                        join= "must";
                    }
                    set = bool.puts(join);
                    for (Condition item : conditions) {
                        set.add(parseCondition(new OriginRow(), item));
                    }
                }
            }
        }else{
            if(condition instanceof AutoCondition) {
                AutoCondition auto = (AutoCondition)condition;
                //List<RunValue> values = condition.getRunValues();
                List<Object> values = auto.getValues();
                String column = condition.getId();
                String join = condition.getJoin();
                row = filter(auto.getCompare(), column, values);
            }
        }
        return row;
    }
    private DataRow filter(Compare compare, String column, List<Object> values) {
        DataRow row = new OriginRow();
        if(null != values && !values.isEmpty()) {
            Object value = null;
            boolean multiple = compare.isMultipleValue();
            if (multiple) {
                List<Object> list = new ArrayList<>();
                list.addAll(values);
                value = list;
            } else {
                value = values.get(0);
            }
            int cc = compare.getCode();
            if(cc == 10) {                                           //  EQUAL
                row.put("term").put(column, value);
            }else if(cc == 999) {                                   //  Compare.REGEX
                row.put("regexp").put(column, value);
            }else if(cc == 50) {                                    //  LIKE
                row.put("wildcard").put(column, "*"+value+"*");
            }else if(cc == 51) {                                     //  START_WITH
                row.put("wildcard").put(column, value+"*");
            }else if(cc == 52) {                                     //  END_WITH
                row.put("wildcard").put(column, "*"+value);
            }else if(cc == 55) {                                     //  MATCH
                if(column.contains(",")){
                    String[] cols = column.split(",");
                    row.put("multi_match").set("query", value).set("fields", cols);
                }else{
                    row.put("match").put(column, value);
                }
            }else if(cc == 20) {                                     //  GREAT
            }else if(cc == 21) {                                     //  GREAT_EQUAL
            }else if(cc == 30) {                                     //  LESS
            }else if(cc == 31) {                                     //  LESS_EQUAL
            }else if(cc == 40) {                                     //  IN
                row.put("terms").put(column, value);
            }else if(cc == 80) {                                     //  BETWEEN
                if(values.size() > 1) {
                    row.put("range").put(column).set("gte", values.get(0)).set("lte",values.get(1));
                }
            }else if(cc == 110) {                                    //  NOT EQUAL
            }else if(cc == 140) {                                    //  NOT IN
            }else if(cc == 150) {                                    //  NOT LIKE
            }else if(cc == 151) {                                    //  NOT START_WITH
            }else if(cc == 152) {                                    //  NOT END_WITH
            }

        }
        return row;
    }
    /**
     * select[命令合成-子流程] <br/>
     * 合成最终 select 命令 包含分页 排序
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     * @return String
     */
    @Override
    public String mergeFinalQuery(DataRuntime runtime, Run run) {
        return super.mergeFinalQuery(runtime, run);
    }
    /**
     * select[命令合成-子流程] <br/>
     * 构造 LIKE 查询条件
     * 如果不需要占位符 返回null  否则原样返回value
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param compare 比较方式 默认 equal 多个值默认 in
     * @param value value
     * @return value 有占位符时返回占位值，没有占位符返回null
     */
    @Override
    public RunValue createConditionLike(DataRuntime runtime, StringBuilder builder, Compare compare, Object value, boolean placeholder) {
        return super.createConditionLike(runtime, builder, compare, value, placeholder);
    }
    /**
     * select[命令合成-子流程] <br/>
     * 构造 FIND_IN_SET 查询条件
     * 如果不需要占位符 返回null  否则原样返回value
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param column 列
     * @param compare 比较方式 默认 equal 多个值默认 in
     * @param value value
     * @return value
     */
    @Override
    public Object createConditionFindInSet(DataRuntime runtime, StringBuilder builder, String column, Compare compare, Object value, boolean placeholder) throws NotSupportException {
        return super.createConditionFindInSet(runtime, builder, column, compare, value, placeholder);
    }
    /**
     * select[命令合成-子流程] <br/>
     * 构造(NOT) IN 查询条件
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param compare 比较方式 默认 equal 多个值默认 in
     * @param value value
     * @return builder
     */
    @Override
    public StringBuilder createConditionIn(DataRuntime runtime, StringBuilder builder, Compare compare, Object value, boolean placeholder) {
        return super.createConditionIn(runtime, builder, compare, value, placeholder);
    }

    /**
     * select [命令执行]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param system 系统表不检测列属性
     * @param table 表
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     * @return DataSet
     */
    @Override
    public DataSet select(DataRuntime runtime, String random, boolean system, Table table, ConfigStore configs, Run run) {
        return select(runtime, random, system, table, configs, (ElasticSearchRun) run);
    }
    /**
     * select [命令执行]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     * @return maps
     */
    @Override
    public List<Map<String,Object>> maps(DataRuntime runtime, String random, ConfigStore configs, Run run) {
        return super.maps(runtime, random, configs, run);
    }
    /**
     * select [命令执行]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     * @return map
     */
    @Override
    public Map<String,Object> map(DataRuntime runtime, String random, ConfigStore configs, Run run) {
        return super.map(runtime, random, configs, run);
    }

    /**
     * select [命令执行]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param next 是否查下一个序列值
     * @param names 存储过程名称s
     * @return DataRow 保存序列查询结果 以存储过程name作为key
     */
    @Override
    public DataRow sequence(DataRuntime runtime, String random, boolean next, String ... names) {
        return super.sequence(runtime, random, next, names);
    }

    /**
     * select [命令执行-子流程]<br/>
     * JDBC执行完成后的结果处理
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param list JDBC执行返回的结果集
     * @return  maps
     */
    @Override
    public List<Map<String,Object>> process(DataRuntime runtime, List<Map<String,Object>> list) {
        return super.process(runtime, list);
    }

    /* *****************************************************************************************************************
     * 													COUNT
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * long count(DataRuntime runtime, String random, RunPrepare prepare, ConfigStore configs, String ... conditions)
     * [命令合成]
     * String mergeFinalTotal(DataRuntime runtime, Run run)
     * [命令执行]
     * long count(DataRuntime runtime, String random, Run run)
     ******************************************************************************************************************/
    /**
     * count [调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param prepare 构建最终执行命令的全部参数，包含表（或视图｜函数｜自定义SQL)查询条件 排序 分页等
     * @param configs 过滤条件及相关配置
     * @param conditions  简单过滤条件
     * @return long
     */
    @Override
    public long count(DataRuntime runtime, String random, RunPrepare prepare, ConfigStore configs, String ... conditions) {
        return super.count(runtime, random, prepare, configs, conditions);
    }
    /**
     * count [命令合成]<br/>
     * 合成最终 select count 命令
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     * @return String
     */
    @Override
    public String mergeFinalTotal(DataRuntime runtime, Run run) {
        return super.mergeFinalTotal(runtime, run);
    }

    /**
     * count [命令执行]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     * @return long
     */
    @Override
    public long count(DataRuntime runtime, String random, Run run) {
        return super.count(runtime, random, run);
    }

    /* *****************************************************************************************************************
     * 													EXISTS
     * -----------------------------------------------------------------------------------------------------------------
     * boolean exists(DataRuntime runtime, String random, RunPrepare prepare, ConfigStore configs, String ... conditions)
     * String mergeFinalExists(DataRuntime runtime, Run run)
     ******************************************************************************************************************/

    /**
     * exists [调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param prepare 构建最终执行命令的全部参数，包含表（或视图｜函数｜自定义SQL)查询条件 排序 分页等
     * @param configs 查询条件及相关设置
     * @param conditions  简单过滤条件
     * @return boolean
     */
    @Override
    public boolean exists(DataRuntime runtime, String random, RunPrepare prepare, ConfigStore configs, String ... conditions) {
        return super.exists(runtime, random, prepare, configs, conditions);
    }
    @Override
    public String mergeFinalExists(DataRuntime runtime, Run run) {
        return super.mergeFinalExists(runtime, run);
    }

    /* *****************************************************************************************************************
     * 													EXECUTE
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * long execute(DataRuntime runtime, String random, RunPrepare prepare, ConfigStore configs, String ... conditions)
     * long execute(DataRuntime runtime, String random, int batch, ConfigStore configs, RunPrepare prepare, Collection<Object> values)
     * boolean execute(DataRuntime runtime, String random, Procedure procedure)
     * [命令合成]
     * Run buildExecuteRun(DataRuntime runtime, RunPrepare prepare, ConfigStore configs, String ... conditions)
     * void fillExecuteContent(DataRuntime runtime, Run run)
     * [命令执行]
     * long execute(DataRuntime runtime, String random, ConfigStore configs, Run run) 
     ******************************************************************************************************************/

    /**
     * execute [调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param prepare 构建最终执行命令的全部参数，包含表（或视图｜函数｜自定义SQL)查询条件 排序 分页等
     * @param configs 查询条件及相关设置
     * @param conditions  简单过滤条件
     * @return 影响行数
     */
    @Override
    public long execute(DataRuntime runtime, String random, RunPrepare prepare, ConfigStore configs, String ... conditions)  {
        return super.execute(runtime, random, prepare, configs, conditions);
    }

    @Override
    public long execute(DataRuntime runtime, String random, int batch, ConfigStore configs, RunPrepare prepare, Collection<Object> values) {
        return super.execute(runtime, random, batch, configs, prepare, values);
    }
    /**
     * procedure [命令执行]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param procedure 存储过程
     * @param random  random
     * @return 影响行数
     */
    @Override
    public boolean execute(DataRuntime runtime, String random, Procedure procedure) {
        return super.execute(runtime, random, procedure);
    }
    /**
     * execute [命令合成]<br/>
     * 创建执行SQL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param prepare 构建最终执行命令的全部参数，包含表（或视图｜函数｜自定义SQL)查询条件 排序 分页等
     * @param configs 查询条件及相关设置
     * @param conditions  简单过滤条件
     * @return Run 最终执行命令 如JDBC环境中的 SQL 与 参数值
     */
    @Override
    public Run buildExecuteRun(DataRuntime runtime, RunPrepare prepare, ConfigStore configs, String ... conditions) {
        return super.buildExecuteRun(runtime, prepare, configs, conditions);
    }
    @Override
    protected void fillExecuteContent(DataRuntime runtime, XMLRun run) {
        super.fillExecuteContent(runtime, run);
    }
    @Override
    protected void fillExecuteContent(DataRuntime runtime, TextRun run) {
        super.fillExecuteContent(runtime, run);
    }
    @Override
    protected void fillExecuteContent(DataRuntime runtime, TableRun run) {
        super.fillExecuteContent(runtime, run);
    }

    /**
     * execute [命令合成-子流程]<br/>
     * 填充 execute 命令内容
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     */
    @Override
    public void fillExecuteContent(DataRuntime runtime, Run run) {
        super.fillExecuteContent(runtime, run);
    }
    /**
     * execute [命令执行]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     * @return 影响行数
     */
    @Override
    public long execute(DataRuntime runtime, String random, ConfigStore configs, Run run) {
        return super.execute(runtime, random, configs, run);
    }

    /* *****************************************************************************************************************
     * 													DELETE
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * <T> long deletes(DataRuntime runtime, String random, int batch, String table, ConfigStore configs, String column, Collection<T> values)
     * long delete(DataRuntime runtime, String random, String table, ConfigStore configs, Object obj, String... columns)
     * long delete(DataRuntime runtime, String random, String table, ConfigStore configs, String... conditions)
     * long truncate(DataRuntime runtime, String random, String table)
     * [命令合成]
     * List<Run> buildDeleteRun(DataRuntime runtime, String table, ConfigStore configs, Object obj, String ... columns)
     * List<Run> buildDeleteRun(DataRuntime runtime, int batch, String table, ConfigStore configs, String column, Object values)
     * List<Run> buildTruncateRun(DataRuntime runtime, String table)
     * List<Run> buildDeleteRunFromTable(DataRuntime runtime, int batch, String table, ConfigStore configs,String column, Object values)
     * List<Run> buildDeleteRunFromEntity(DataRuntime runtime, String table, ConfigStore configs, Object obj, String ... columns)
     * void fillDeleteRunContent(DataRuntime runtime, Run run)
     * [命令执行]
     * long delete(DataRuntime runtime, String random, ConfigStore configs, Run run)
     ******************************************************************************************************************/
    /**
     * delete [调用入口]<br/>
     * <br/>
     * 合成 where column in (values)
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param table 表 如果不提供表名则根据data解析,表名可以事实前缀&lt;数据源名&gt;表示切换数据源
     * @param values 列对应的值
     * @return 影响行数
     * @param <T> T
     */
    @Override
    public <T> long deletes(DataRuntime runtime, String random, int batch, String table, ConfigStore configs, String key, Collection<T> values) {
        return super.deletes(runtime, random, batch, table, configs, key, values);
    }

    /**
     * delete [调用入口]<br/>
     * <br/>
     * 合成 where k1 = v1 and k2 = v2
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param obj entity或DataRow
     * @param columns 删除条件的列或属性，根据columns取obj值并合成删除条件
     * @return 影响行数
     */
    @Override
    public long delete(DataRuntime runtime, String random, Table dest, ConfigStore configs, Object obj, String... columns) {
        return super.delete(runtime, random, dest, configs, obj, columns);
    }

    /**
     * delete [调用入口]<br/>
     * <br/>
     * 根据configs和conditions过滤条件
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param table 表
     * @param configs 查询条件及相关设置
     * @param conditions  简单过滤条件
     * @return 影响行数
     */
    @Override
    public long delete(DataRuntime runtime, String random, String table, ConfigStore configs, String... conditions) {
        return super.delete(runtime, random, table, configs, conditions);
    }

    /**
     * truncate [调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param table 表
     * @return 1表示成功执行
     */
    @Override
    public long truncate(DataRuntime runtime, String random, Table table) {
        return super.truncate(runtime, random, table);
    }

    /**
     * delete[命令合成]<br/>
     * 合成 where k1 = v1 and k2 = v2
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param dest 表 如果不提供表名则根据data解析,表名可以事实前缀&lt;数据源名&gt;表示切换数据源
     * @param obj entity或DataRow
     * @param columns 删除条件的列或属性，根据columns取obj值并合成删除条件
     * @return Run 最终执行命令 如JDBC环境中的 SQL 与 参数值
     */
    @Override
    public List<Run> buildDeleteRun(DataRuntime runtime, Table dest, ConfigStore configs, Object obj, String ... columns) {
        List<Run> runs = new ArrayList<>();
        ElasticSearchRun run = new ElasticSearchRun(runtime, dest);
        run.setTable(dest);
        run.action(ACTION.DML.DELETE);
        if(obj instanceof Collection){
            Collection list = (Collection) obj;
            run.setMethod("POST");
            run.setEndpoint(dest.getName() + "/_bulk");
            StringBuilder builder = run.getBuilder();
            for(Object item:list){
                Object pv = null;
                if(obj instanceof DataRow){
                    pv = ((DataRow)obj).getPrimaryValue();
                }else{
                    pv = EntityAdapterProxy.primaryValue(item, true);
                }
                builder.append("{\"delete\":{\"_id\":\"").append(pv).append("\"}}\n");
            }
        }else{
            run.setMethod("DELETE");
            Object pv = null;
            if(obj instanceof DataRow){
                pv = ((DataRow)obj).getPrimaryValue();
            }else{
                pv = EntityAdapterProxy.primaryValue(obj, true);
            }
            run.setEndpoint(dest.getName() + "/_doc/" + pv);
        }
        runs.add(run);
        return runs;
    }

    /**
     * delete[命令合成]<br/>
     * 合成 where column in (values)
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param table 表 如果不提供表名则根据data解析,表名可以事实前缀&lt;数据源名&gt;表示切换数据源
     * @param key 根据属性解析出列
     * @param values values
     * @return Run 最终执行命令 如JDBC环境中的 SQL 与 参数值
     */
    @Override
    public List<Run> buildDeleteRun(DataRuntime runtime, int batch, String table, ConfigStore configs, String key, Object values) {
        return super.buildDeleteRun(runtime, batch, table, configs, key, values);
    }

    @Override
    public List<Run> buildTruncateRun(DataRuntime runtime, String table) {
        return super.buildTruncateRun(runtime, table);
    }

    /**
     * delete[命令合成-子流程]<br/>
     * 合成 where column in (values)
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param table 表 如果不提供表名则根据data解析,表名可以事实前缀&lt;数据源名&gt;表示切换数据源
     * @param column 列
     * @param values values
     * @return Run 最终执行命令 如JDBC环境中的 SQL 与 参数值
     */
    @Override
    public List<Run> buildDeleteRunFromTable(DataRuntime runtime, int batch, Table table, ConfigStore configs, String column, Object values) {
        return super.buildDeleteRunFromTable(runtime, batch, table, configs, column, values);
    }

    /**
     * delete[命令合成-子流程]<br/>
     * 合成 where k1 = v1 and k2 = v2
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param table 表 如果不提供表名则根据data解析,表名可以事实前缀&lt;数据源名&gt;表示切换数据源 如果为空 可以根据obj解析
     * @param obj entity或DataRow
     * @param columns 删除条件的列或属性，根据columns取obj值并合成删除条件
     * @return Run 最终执行命令 如JDBC环境中的 SQL 与 参数值
     */
    @Override
    public List<Run> buildDeleteRunFromEntity(DataRuntime runtime, Table table, ConfigStore configs, Object obj, String... columns) {
        return super.buildDeleteRunFromEntity(runtime, table, configs, obj, columns);
    }

    /**
     * delete[命令合成-子流程]<br/>
     * 构造查询主体 拼接where group等(不含分页 ORDER)
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     */
    @Override
    public void fillDeleteRunContent(DataRuntime runtime, Run run) {
        super.fillDeleteRunContent(runtime, run);
    }

    /**
     * delete[命令执行]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param configs 查询条件及相关设置
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     * @return 影响行数
     */
    @Override
    public long delete(DataRuntime runtime, String random, ConfigStore configs, Run run) {
        return super.delete(runtime, random, configs, run);
    }

    @Override
    public <T extends Metadata> void checkSchema(DataRuntime runtime, T meta) {

    }

    /* *****************************************************************************************************************
     *
     * 													metadata
     *
     * =================================================================================================================
     * database			: 数据库(catalog, schema)
     * table			: 表
     * master table		: 主表
     * partition table	: 分区表
     * column			: 列
     * tag				: 标签
     * primary key      : 主键
     * foreign key		: 外键
     * index			: 索引
     * constraint		: 约束
     * trigger		    : 触发器
     * procedure        : 存储过程
     * function         : 函数
     ******************************************************************************************************************/

    /* *****************************************************************************************************************
     * 													database
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * LinkedHashMap<String, Database> databases(DataRuntime runtime, String random, String name)
     * List<Database> databases(DataRuntime runtime, String random, boolean greedy, String name)
     * Database database(DataRuntime runtime, String random, String name)
     * [命令合成]
     * List<Run> buildQueryDatabasesRun(DataRuntime runtime, boolean greedy, String name)
     * [结果集封装]<br/>
     * LinkedHashMap<String, Database> databases(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, Database> databases, Catalog catalog, Schema schema, DataSet set)
     * List<Database> databases(DataRuntime runtime, int index, boolean create, List<Database> databases, Catalog catalog, Schema schema, DataSet set)
	 * Database database(DataRuntime runtime, boolean create, Database dataase, DataSet set)
	 * Database database(DataRuntime runtime, boolean create, Database dataase)
     ******************************************************************************************************************/
    /**
     * database[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param greedy 贪婪模式 true:查询权限范围内尽可能多的数据
     * @param name 名称统配符或正则
     * @return LinkedHashMap
     */
    @Override
    public List<Database> databases(DataRuntime runtime, String random, boolean greedy, String name) {
        return super.databases(runtime, random, greedy, name);
    }
    /**
     * database[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param name 名称统配符或正则
     * @return LinkedHashMap
     */
    @Override
    public LinkedHashMap<String, Database> databases(DataRuntime runtime, String random, String name) {
        return super.databases(runtime, random, name);
    }
    /**
     * database[命令合成]<br/>
     * 查询全部数据库
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param name 名称统配符或正则
     * @param greedy 贪婪模式 true:查询权限范围内尽可能多的数据
     * @return sqls
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildQueryDatabasesRun(DataRuntime runtime, boolean greedy, String name) throws Exception {
        return super.buildQueryDatabasesRun(runtime, greedy, name);
    }
    /**
     * database[结果集封装]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照 buildQueryDatabaseRun 返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param databases 上一步查询结果
     * @param set 查询结果集
     * @return LinkedHashMap
     * @throws Exception Exception
     */
    @Override
    public LinkedHashMap<String, Database> databases(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, Database> databases, Catalog catalog, Schema schema, DataSet set) throws Exception {
        return super.databases(runtime, index, create, databases, catalog, schema, set);
    }
    @Override
    public List<Database> databases(DataRuntime runtime, int index, boolean create, List<Database> databases, Catalog catalog, Schema schema, DataSet set) throws Exception {
        return super.databases(runtime, index, create, databases, catalog, schema, set);
    }
	/**
	 * database[结果集封装]<br/>
	 * 当前database 根据查询结果集
	 * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
	 * @param index 第几条SQL 对照 buildQueryDatabaseRun 返回顺序
	 * @param create 上一步没有查到的,这一步是否需要新创建
	 * @param database 上一步查询结果
	 * @param set 查询结果集
	 * @return database
	 * @throws Exception 异常
	 */
	@Override
	public Database database(DataRuntime runtime, int index, boolean create, Database database, DataSet set) throws Exception {
		return super.database(runtime, index, create, database, set);
	}

	/**
	 * database[结果集封装]<br/>
	 * 当前database 根据驱动内置接口补充
	 * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
	 * @param create 上一步没有查到的,这一步是否需要新创建
	 * @param database 上一步查询结果
	 * @return database
	 * @throws Exception 异常
	 */
	@Override
	public Database database(DataRuntime runtime, boolean create, Database database) throws Exception {
		return super.database(runtime, create, database);
	}

	/**
	 * database[结果集封装]<br/>
	 * 根据查询结果集构造 product
	 * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
	 * @param create 上一步没有查到的,这一步是否需要新创建
	 * @param product 上一步查询结果
	 * @param set 查询结果集
	 * @return product
	 * @throws Exception 异常
	 */
	@Override
	public String product(DataRuntime runtime, int index, boolean create, String product, DataSet set) {
		return super.product(runtime, index, create, product, set);
	}

	/**
	 * database[结果集封装]<br/>
	 * 根据JDBC内置接口 product
	 * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
	 * @param create 上一步没有查到的,这一步是否需要新创建
	 * @param product 上一步查询结果
	 * @return product
	 * @throws Exception 异常
	 */
	@Override
	public String product(DataRuntime runtime, boolean create, String product) {
		return super.product(runtime, create, product);
	}

	/**
	 * database[结果集封装]<br/>
	 * 根据查询结果集构造 version
	 * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
	 * @param create 上一步没有查到的,这一步是否需要新创建
	 * @param version 上一步查询结果
	 * @param set 查询结果集
	 * @return version
	 * @throws Exception 异常
	 */
	@Override
	public String version(DataRuntime runtime, int index, boolean create, String version, DataSet set) {
		return super.version(runtime, index, create, version, set);
	}

	/**
	 * database[结果集封装]<br/>
	 * 根据JDBC内置接口 version
	 * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
	 * @param create 上一步没有查到的,这一步是否需要新创建
	 * @param version 上一步查询结果
	 * @return version
	 * @throws Exception 异常
	 */
	@Override
	public String version(DataRuntime runtime, boolean create, String version) {
		return super.version(runtime, create, version);
	}
    /* *****************************************************************************************************************
     * 													catalog
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * LinkedHashMap<String, Catalog> catalogs(DataRuntime runtime, String random, String name)
     * List<Catalog> catalogs(DataRuntime runtime, String random, boolean greedy, String name)
     * [命令合成]
     * List<Run> buildQueryCatalogsRun(DataRuntime runtime, boolean greedy, String name)
     * [结果集封装]<br/>
     * List<Catalog> catalogs(DataRuntime runtime, int index, boolean create, List<Catalog> catalogs, DataSet set)
     * LinkedHashMap<String, Catalog> catalogs(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, Catalog> catalogs, DataSet set)
     * List<Catalog> catalogs(DataRuntime runtime, boolean create, List<Catalog> catalogs, DataSet set)
     * LinkedHashMap<String, Catalog> catalogs(DataRuntime runtime, boolean create, LinkedHashMap<String, Catalog> catalogs, DataSet set)
     * Catalog catalog(DataRuntime runtime, int index, boolean create, DataSet set)
     ******************************************************************************************************************/
    /**
     * catalog[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param name 名称统配符或正则
     * @return LinkedHashMap
     */
    @Override
    public LinkedHashMap<String, Catalog> catalogs(DataRuntime runtime, String random, String name) {
        return super.catalogs(runtime, random, name);
    }
    /**
     * catalog[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param name 名称统配符或正则
     * @return LinkedHashMap
     */
    @Override
    public List<Catalog> catalogs(DataRuntime runtime, String random, boolean greedy, String name) {
        return super.catalogs(runtime, random, greedy, name);
    }

    /**
     * catalog[命令合成]<br/>
     * 查询全部数据库
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param name 名称统配符或正则
     * @param greedy 贪婪模式 true:查询权限范围内尽可能多的数据
     * @return sqls
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildQueryCatalogsRun(DataRuntime runtime, boolean greedy, String name) throws Exception {
        return super.buildQueryCatalogsRun(runtime, greedy, name);
    }
    /**
     * catalog[结果集封装]<br/>
     * 根据查询结果集构造 Database
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照 buildQueryDatabaseRun 返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param catalogs 上一步查询结果
     * @param set 查询结果集
     * @return databases
     * @throws Exception 异常
     */
    @Override
    public LinkedHashMap<String, Catalog> catalogs(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, Catalog> catalogs, Catalog catalog, Schema schema, DataSet set) throws Exception {
        return super.catalogs(runtime, index, create, catalogs, catalog, schema, set);
    }
    /**
     * catalog[结果集封装]<br/>
     * 根据查询结果集构造 Database
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照 buildQueryDatabaseRun 返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param catalogs 上一步查询结果
     * @param set 查询结果集
     * @return databases
     * @throws Exception 异常
     */
    @Override
    public List<Catalog> catalogs(DataRuntime runtime, int index, boolean create, List<Catalog> catalogs, Catalog catalog, Schema schema, DataSet set) throws Exception {
        return super.catalogs(runtime, index, create, catalogs, catalog, schema, set);
    }
	/**
     * catalog[结果集封装]<br/>
     * 根据驱动内置接口补充 catalog
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param catalogs 上一步查询结果
     * @return databases
     * @throws Exception 异常
     */
    @Override
    public LinkedHashMap<String, Catalog> catalogs(DataRuntime runtime, boolean create, LinkedHashMap<String, Catalog> catalogs) throws Exception {
        return super.catalogs(runtime, create, catalogs);
    }

    /**
     * catalog[结果集封装]<br/>
     * 根据驱动内置接口补充 catalog
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param catalogs 上一步查询结果
     * @return databases
     * @throws Exception 异常
     */
    @Override
    public List<Catalog> catalogs(DataRuntime runtime, boolean create, List<Catalog> catalogs) throws Exception {
        return super.catalogs(runtime, create, catalogs);
    }
	/**
	 * catalog[结果集封装]<br/>
	 * 当前catalog 根据查询结果集
	 * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
	 * @param index 第几条SQL 对照 buildQueryDatabaseRun 返回顺序
	 * @param create 上一步没有查到的,这一步是否需要新创建
	 * @param catalog 上一步查询结果
	 * @param set 查询结果集
	 * @return Catalog
	 * @throws Exception 异常
	 */
	@Override
	public Catalog catalog(DataRuntime runtime, int index, boolean create, Catalog catalog, DataSet set) throws Exception {
		return super.catalog(runtime, index, create, catalog, set);
	}

	/**
	 * catalog[结果集封装]<br/>
	 * 当前catalog 根据驱动内置接口补充
	 * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
	 * @param create 上一步没有查到的,这一步是否需要新创建
	 * @param catalog 上一步查询结果
	 * @return Catalog
	 * @throws Exception 异常
	 */
	@Override
	public Catalog catalog(DataRuntime runtime, boolean create, Catalog catalog) throws Exception {
		return super.catalog(runtime, create, catalog);
	}

    /* *****************************************************************************************************************
     * 													schema
     * -----------------------------------------------------------------------------------------------------------------
	 * [调用入口]
	 * LinkedHashMap<String, Database> databases(DataRuntime runtime, String random, String name)
	 * List<Database> databases(DataRuntime runtime, String random, boolean greedy, String name)
	 * Database database(DataRuntime runtime, String random, String name)
	 * Database database(DataRuntime runtime, String random)
	 * String String product(DataRuntime runtime, String random);
	 * String String version(DataRuntime runtime, String random);
	 * [命令合成]
	 * List<Run> buildQueryDatabasesRun(DataRuntime runtime, boolean greedy, String name)
	 * List<Run> buildQueryDatabaseRun(DataRuntime runtime, boolean greedy, String name)
	 * List<Run> buildQueryProductRun(DataRuntime runtime, boolean greedy, String name)
	 * List<Run> buildQueryVersionRun(DataRuntime runtime, boolean greedy, String name)
	 * [结果集封装]<br/>
	 * LinkedHashMap<String, Database> databases(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, Database> databases, Catalog catalog, Schema schema, DataSet set)
	 * List<Database> databases(DataRuntime runtime, int index, boolean create, List<Database> databases, Catalog catalog, Schema schema, DataSet set)
	 * Database database(DataRuntime runtime, boolean create, Database dataase, DataSet set)
	 * Database database(DataRuntime runtime, boolean create, Database dataase)
	 * String product(DataRuntime runtime, boolean create, Database product, DataSet set)
	 * String product(DataRuntime runtime, boolean create, String product)
	 * String version(DataRuntime runtime, int index, boolean create, String version, DataSet set)
	 * String version(DataRuntime runtime, boolean create, String version)
	 * Catalog catalog(DataRuntime runtime, boolean create, Catalog catalog, DataSet set)
	 * Catalog catalog(DataRuntime runtime, boolean create, Catalog catalog)
	 * Schema schema(DataRuntime runtime, boolean create, Schema schema, DataSet set)
	 * Schema schema(DataRuntime runtime, boolean create, Schema schema)
	 * Database database(DataRuntime runtime, boolean create, Database dataase)
	 ******************************************************************************************************************/
    /**
     * schema[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param catalog catalog
     * @param name 名称统配符或正则
     * @return LinkedHashMap
     */
    @Override
    public LinkedHashMap<String, Schema> schemas(DataRuntime runtime, String random, Catalog catalog, String name) {
        return super.schemas(runtime, random, catalog, name);
    }
    /**
     * schema[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param catalog catalog
     * @param name 名称统配符或正则
     * @return LinkedHashMap
     */
    @Override
    public List<Schema> schemas(DataRuntime runtime, String random, boolean greedy, Catalog catalog, String name) {
        return super.schemas(runtime, random, greedy, catalog, name);
    }

    /**
     * catalog[命令合成]<br/>
     * 查询全部数据库
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param name 名称统配符或正则
     * @param greedy 贪婪模式 true:查询权限范围内尽可能多的数据
     * @return sqls
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildQuerySchemasRun(DataRuntime runtime, boolean greedy, Catalog catalog, String name) throws Exception {
        return super.buildQuerySchemasRun(runtime, greedy, catalog, name);
    }
    /**
     * schema[结果集封装]<br/>
     * 根据查询结果集构造 Database
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照 buildQueryDatabaseRun 返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param schemas 上一步查询结果
     * @param set 查询结果集
     * @return databases
     * @throws Exception 异常
     */
    @Override
    public LinkedHashMap<String, Schema> schemas(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, Schema> schemas, Catalog catalog, Schema schema, DataSet set) throws Exception {
        return super.schemas(runtime, index, create, schemas, catalog, schema, set);
    }
    @Override
    public List<Schema> schemas(DataRuntime runtime, int index, boolean create, List<Schema> schemas, Catalog catalog, Schema schema, DataSet set) throws Exception {
        return super.schemas(runtime, index, create, schemas, catalog, schema, set);
    }

	/**
	 * schema[结果集封装]<br/>
	 * 当前schema 根据查询结果集
	 * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
	 * @param index 第几条SQL 对照 buildQuerySchemaRun 返回顺序
	 * @param create 上一步没有查到的,这一步是否需要新创建
	 * @param schema 上一步查询结果
	 * @param set 查询结果集
	 * @return schema
	 * @throws Exception 异常
	 */
	@Override
	public Schema schema(DataRuntime runtime, int index, boolean create, Schema schema, DataSet set) throws Exception {
		return super.schema(runtime, index, create, schema, set);
	}

	/**
	 * schema[结果集封装]<br/>
	 * 当前schema 根据驱动内置接口补充
	 * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
	 * @param create 上一步没有查到的,这一步是否需要新创建
	 * @param schema 上一步查询结果
	 * @return schema
	 * @throws Exception 异常
	 */
	@Override
	public Schema schema(DataRuntime runtime, boolean create, Schema schema) throws Exception {
		return super.schema(runtime, create, schema);
	}

    /* *****************************************************************************************************************
     * 													table
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * <T extends Table> List<T> tables(DataRuntime runtime, String random, boolean greedy, Catalog catalog, Schema schema, String pattern, int types, boolean struct)
     * <T extends Table> LinkedHashMap<String, T> tables(DataRuntime runtime, String random, Catalog catalog, Schema schema, String pattern, String types, boolean struct)
     * [命令合成]
     * List<Run> buildQueryTablesRun(DataRuntime runtime, boolean greedy, Catalog catalog, Schema schema, String pattern, int types, ConfigStore configs)
     * List<Run> buildQueryTablesCommentRun(DataRuntime runtime, Catalog catalog, Schema schema, String pattern, int types)
     * [结果集封装]<br/>
     * <T extends Table> LinkedHashMap<String, T> tables(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, T> tables, Catalog catalog, Schema schema, DataSet set)
     * <T extends Table> List<T> tables(DataRuntime runtime, int index, boolean create, List<T> tables, Catalog catalog, Schema schema, DataSet set)
     * <T extends Table> LinkedHashMap<String, T> tables(DataRuntime runtime, boolean create, LinkedHashMap<String, T> tables, Catalog catalog, Schema schema, String pattern, int types)
     * <T extends Table> List<T> tables(DataRuntime runtime, boolean create, List<T> tables, Catalog catalog, Schema schema, String pattern, int types)
     * <T extends Table> LinkedHashMap<String, T> comments(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, T> tables, Catalog catalog, Schema schema, DataSet set)
     * [调用入口]
     * List<String> ddl(DataRuntime runtime, String random, Table table, boolean init)
     * [命令合成]
     * List<Run> buildQueryDdlsRun(DataRuntime runtime, Table table)
     * [结果集封装]<br/>
     * List<String> ddl(DataRuntime runtime, int index, Table table, List<String> ddls, DataSet set)
     ******************************************************************************************************************/

    /**
     *
     * table[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param greedy 贪婪模式 true:查询权限范围内尽可能多的数据
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @param types  Metadata.TYPE.
     * @param struct 是否查询表结构
     * @return List
     * @param <T> Table
     */
    @Override
    public <T extends Table> List<T> tables(DataRuntime runtime, String random, boolean greedy, Catalog catalog, Schema schema, String pattern, int types, int struct) {
        return super.tables(runtime, random, greedy, catalog, schema, pattern, types, struct);
    }

    /**
     * table[结果集封装-子流程]<br/>
     * 查出所有key并以大写缓存 用来实现忽略大小写
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param catalog catalog
     * @param schema schema
     */
    @Override
    protected void tableMap(DataRuntime runtime, String random, boolean greedy, Catalog catalog, Schema schema, ConfigStore configs) {
        super.tableMap(runtime, random, greedy, catalog, schema, configs);
    }

    @Override
    public <T extends Table> LinkedHashMap<String, T> tables(DataRuntime runtime, String random, Catalog catalog, Schema schema, String pattern, int types, int struct, ConfigStore configs) {
        return super.tables(runtime, random, catalog, schema, pattern, types, struct, configs);
    }

    /**
     * table[命令合成]<br/>
     * 查询表,不是查表中的数据
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param greedy 贪婪模式 true:查询权限范围内尽可能多的数据
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @param types  Metadata.TYPE.
     * @return String
     */
    @Override
    public List<Run> buildQueryTablesRun(DataRuntime runtime, boolean greedy, Catalog catalog, Schema schema, String pattern, int types, ConfigStore configs) throws Exception {
        List<Run> runs = new ArrayList<>();
        ElasticSearchRun run = new ElasticSearchRun(runtime);
        run.action(ACTION.DML.SELECT);
        run.setMethod("GET");
        run.setEndpoint("_cat/indices");
        runs.add(run);
        return runs;
    }

    /**
     * table[命令合成]<br/>
     * 查询表备注
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @param types types Metadata.TYPE.
     * @return String
     */
    @Override
    public List<Run> buildQueryTablesCommentRun(DataRuntime runtime, Catalog catalog, Schema schema, String pattern, int types) throws Exception {
        return super.buildQueryTablesCommentRun(runtime, catalog, schema, pattern, types);
    }

    /**
     * table[结果集封装]<br/>
     *  根据查询结果集构造Table
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照buildQueryTablesRun返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param catalog catalog
     * @param schema schema
     * @param tables 上一步查询结果
     * @param set 查询结果集
     * @return tables
     * @throws Exception 异常
     */
    @Override
    public <T extends Table> LinkedHashMap<String, T> tables(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, T> tables, Catalog catalog, Schema schema, DataSet set) throws Exception {
        return super.tables(runtime, index, create, tables, catalog, schema, set);
    }

    /**
     * table[结果集封装]<br/>
     *  根据查询结果集构造Table
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照buildQueryTablesRun返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param catalog catalog
     * @param schema schema
     * @param tables 上一步查询结果
     * @param set 查询结果集
     * @return tables
     * @throws Exception 异常
     */
    @Override
    public <T extends Table> List<T> tables(DataRuntime runtime, int index, boolean create, List<T> tables, Catalog catalog, Schema schema, DataSet set) throws Exception {
        return super.tables(runtime, index, create, tables, catalog, schema, set);
    }

    @Override
    public <T extends Table> T init(DataRuntime runtime, int index, T table, Catalog catalog, Schema schema, DataRow row) {
        if(null == table){
            table = (T) new ElasticSearchIndex();
        }
        table.setName(row.getString("2"));
        return table;
    }

    @Override
    public <T extends Table> T detail(DataRuntime runtime, int index, T table, Catalog catalog, Schema schema, DataRow row) {
        return null;
    }

    /**
     * table[结果集封装]<br/>
     * 根据驱动内置方法补充
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param tables 上一步查询结果
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @param types types Metadata.TYPE.
     * @return tables
     * @throws Exception 异常
     */

    @Override
    public <T extends Table> LinkedHashMap<String, T> tables(DataRuntime runtime, boolean create, LinkedHashMap<String, T> tables, Catalog catalog, Schema schema, String pattern, int types) throws Exception {
        return super.tables(runtime, create, tables, catalog, schema, pattern, types);
    }

    /**
     * table[结果集封装]<br/>
     * 根据驱动内置方法补充
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param tables 上一步查询结果
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @param types types Metadata.TYPE.
     * @return tables
     * @throws Exception 异常
     */
    @Override
    public <T extends Table> List<T> tables(DataRuntime runtime, boolean create, List<T> tables, Catalog catalog, Schema schema, String pattern, int types) throws Exception {
        return super.tables(runtime, create, tables, catalog, schema, pattern, types);
    }

    /**
     * table[结果集封装]<br/>
     * 表备注
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照buildQueryTablesRun返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param catalog catalog
     * @param schema schema
     * @param tables 上一步查询结果
     * @param set 查询结果集
     * @return tables
     * @throws Exception 异常
     */
    @Override
    public <T extends Table> LinkedHashMap<String, T> comments(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, T> tables, Catalog catalog, Schema schema, DataSet set) throws Exception {
        return super.comments(runtime, index, create, tables, catalog, schema, set);
    }

    /**
     * table[结果集封装]<br/>
     * 表备注
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照buildQueryTablesRun返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param catalog catalog
     * @param schema schema
     * @param tables 上一步查询结果
     * @param set 查询结果集
     * @return tables
     * @throws Exception 异常
     */
    @Override
    public <T extends Table> List<T> comments(DataRuntime runtime, int index, boolean create, List<T> tables, Catalog catalog, Schema schema, DataSet set) throws Exception {
        return super.comments(runtime, index, create, tables, catalog, schema, set);
    }

    /**
     *
     * table[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param table 表
     * @param init 是否还原初始状态 如自增状态
     * @return List
     */
    @Override
    public List<String> ddl(DataRuntime runtime, String random, Table table, boolean init) {
        return super.ddl(runtime, random, table, init);
    }

    /**
     * table[命令合成]<br/>
     * 查询表DDL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param table 表
     * @return List
     */
    @Override
    public List<Run> buildQueryDdlsRun(DataRuntime runtime, Table table) throws Exception {
        return super.buildQueryDdlsRun(runtime, table);
    }

    /**
     * table[结果集封装]<br/>
     * 查询表DDL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照 buildQueryDdlsRun 返回顺序
     * @param table 表
     * @param ddls 上一步查询结果
     * @param set sql执行的结果集
     * @return List
     */
    @Override
    public List<String> ddl(DataRuntime runtime, int index, Table table, List<String> ddls, DataSet set) {
        return super.ddl(runtime, index, table, ddls, set);
    }

    /* *****************************************************************************************************************
     * 													view
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * <T extends View> LinkedHashMap<String, T> views(DataRuntime runtime, String random, boolean greedy, Catalog catalog, Schema schema, String pattern, int types)
     * [命令合成]
     * List<Run> buildQueryViewsRun(DataRuntime runtime, boolean greedy, Catalog catalog, Schema schema, String pattern, int types)
     * [结果集封装]<br/>
     * <T extends View> LinkedHashMap<String, T> views(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, T> views, Catalog catalog, Schema schema, DataSet set)
     * <T extends View> LinkedHashMap<String, T> views(DataRuntime runtime, boolean create, LinkedHashMap<String, T> views, Catalog catalog, Schema schema, String pattern, int types)
     * [调用入口]
     * List<String> ddl(DataRuntime runtime, String random, View view)
     * [命令合成]
     * List<Run> buildQueryDdlsRun(DataRuntime runtime, View view)
     * [结果集封装]<br/>
     * List<String> ddl(DataRuntime runtime, int index, View view, List<String> ddls, DataSet set)
     ******************************************************************************************************************/

    /**
     * view[调用入口]<br/>
     * 查询视图
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @param types  Metadata.TYPE.
     * @return List
     * @param <T> View
     */
    @Override
    public <T extends View> LinkedHashMap<String, T> views(DataRuntime runtime, String random, Catalog catalog, Schema schema, String pattern, int types, int struct, ConfigStore configs) {
		return super.views(runtime, random, catalog, schema, pattern, types, struct, configs);
	}
    /**
     * view[命令合成]<br/>
     * 查询视图
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param greedy 贪婪模式 true:查询权限范围内尽可能多的数据
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @param types types Metadata.TYPE.
     * @return List
     */
    @Override
    public List<Run> buildQueryViewsRun(DataRuntime runtime, boolean greedy, Catalog catalog, Schema schema, String pattern, int types, ConfigStore configs) throws Exception {
		return super.buildQueryViewsRun(runtime, greedy, catalog, schema, pattern, types, configs);
	}

    /**
     * view[结果集封装]<br/>
     *  根据查询结果集构造View
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照buildQueryViewsRun返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param catalog catalog
     * @param schema schema
     * @param views 上一步查询结果
     * @param set 查询结果集
     * @return views
     * @throws Exception 异常
     */
    @Override
    public <T extends View> LinkedHashMap<String, T> views(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, T> views, Catalog catalog, Schema schema, DataSet set) throws Exception {
        return super.views(runtime, index, create, views, catalog, schema, set);
    }
    /**
     * view[结果集封装]<br/>
     * 根据根据驱动内置接口补充
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param views 上一步查询结果
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @param types types Metadata.TYPE.
     * @return views
     * @throws Exception 异常
     */
    @Override
    public <T extends View> LinkedHashMap<String, T> views(DataRuntime runtime, boolean create, LinkedHashMap<String, T> views, Catalog catalog, Schema schema, String pattern, int types) throws Exception {
        return super.views(runtime, create, views, catalog, schema, pattern, types);
    }

    /**
     * view[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param view 视图
     * @return List
     */
    @Override
    public List<String> ddl(DataRuntime runtime, String random, View view, boolean init) {
		return super.ddl(runtime, random, view, init);
	}

    /**
     * view[命令合成]<br/>
     * 查询view DDL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param view view
     * @return List
     */
    @Override
    public List<Run> buildQueryDdlsRun(DataRuntime runtime, View view) throws Exception {
        return super.buildQueryDdlsRun(runtime, view);
    }

    /**
     * view[结果集封装]<br/>
     * 查询 view DDL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照 buildQueryDdlsRun 返回顺序
     * @param view view
     * @param ddls 上一步查询结果
     * @param set sql执行的结果集
     * @return List
     */
    @Override
    public List<String> ddl(DataRuntime runtime, int index, View view, List<String> ddls, DataSet set) {
        return super.ddl(runtime, index, view, ddls, set);
    }

    @Override
    public ViewMetadataAdapter viewMetadataAdapter(DataRuntime runtime) {
        return null;
    }
    /* *****************************************************************************************************************
     * 													master table
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * <T extends MasterTable> LinkedHashMap<String, T> masterTables(DataRuntime runtime, String random, Catalog catalog, Schema schema, String pattern, int types, int struct, ConfigStore configs)
     * [命令合成]
     * List<Run> buildQueryMasterTablesRun(DataRuntime runtime, Catalog catalog, Schema schema, String pattern, int types, ConfigStore configs)
     * [结果集封装]<br/>
     * <T extends MasterTable> LinkedHashMap<String, T> masterTables(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, T> tables, Catalog catalog, Schema schema, DataSet set)
     * [结果集封装]<br/>
     * <T extends MasterTable> LinkedHashMap<String, T> masterTables(DataRuntime runtime, boolean create, LinkedHashMap<String, T> tables,Catalog catalog, Schema schema, String pattern, int types)
     * [调用入口]
     * List<String> ddl(DataRuntime runtime, String random, MasterTable table)
     * [命令合成]
     * List<Run> buildQueryDdlsRun(DataRuntime runtime, MasterTable table)
     * [结果集封装]<br/>
     * List<String> ddl(DataRuntime runtime, int index, MasterTable table, List<String> ddls, DataSet set)
     ******************************************************************************************************************/

    /**
     * master table[调用入口]<br/>
     * 查询主表
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param greedy 贪婪模式 true:查询权限范围内尽可能多的数据
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @param types  Metadata.TYPE.
     * @return List
     * @param <T> MasterTable
     */
    @Override
	public <T extends MasterTable> List<T> masterTables(DataRuntime runtime, String random, boolean greedy, Catalog catalog, Schema schema, String pattern, int types, int struct, ConfigStore configs) {
		return super.masterTables(runtime, random, greedy, catalog, schema, pattern, types, struct, configs);
	}
	@Override
	public <T extends MasterTable> List<T> masterTables(DataRuntime runtime, String random, boolean greedy, Catalog catalog, Schema schema, String pattern, int types, int struct) {
		return super.masterTables(runtime, random, greedy, catalog, schema, pattern, types, struct);
	}
    /**
     * master table[命令合成]<br/>
     * 查询主表
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @param types types
     * @return String
     */
    @Override
    public List<Run> buildQueryMasterTablesRun(DataRuntime runtime, boolean greedy, Catalog catalog, Schema schema, String pattern, int types, ConfigStore configs) throws Exception {
		return super.buildQueryMasterTablesRun(runtime, greedy, catalog, schema, pattern, types, configs);
	}

    /**
     * master table[结果集封装]<br/>
     *  根据查询结果集构造Table
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照 buildQueryMasterTablesRun返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param catalog catalog
     * @param schema schema
     * @param tables 上一步查询结果
     * @param set 查询结果集
     * @return tables
     * @throws Exception 异常
     */
    @Override
    public <T extends MasterTable> LinkedHashMap<String, T> masterTables(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, T> tables, Catalog catalog, Schema schema, DataSet set) throws Exception {
        return super.masterTables(runtime, index, create, tables, catalog, schema, set);
    }
    /**
     * master table[结果集封装]<br/>
     * 根据根据驱动内置接口
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param catalog catalog
     * @param schema schema
     * @param tables 上一步查询结果
     * @return tables
     * @throws Exception 异常
     */
    @Override
    public <T extends MasterTable> LinkedHashMap<String, T> masterTables(DataRuntime runtime, boolean create, LinkedHashMap<String, T> tables,Catalog catalog, Schema schema, String pattern, int types) throws Exception {
        return super.masterTables(runtime, create, tables, catalog, schema, pattern, types);
    }

    /**
     * master table[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param meta MasterTable
     * @return List
     */
    @Override
    public List<String> ddl(DataRuntime runtime, String random, MasterTable meta, boolean init) {
		return super.ddl(runtime, random, meta, init);
	}
    /**
     * master table[命令合成]<br/>
     * 查询 MasterTable DDL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta MasterTable
     * @return List
     */
    @Override
    public List<Run> buildQueryDdlsRun(DataRuntime runtime, MasterTable meta) throws Exception {
        return super.buildQueryDdlsRun(runtime, meta);
    }
    /**
     * master table[结果集封装]<br/>
     * 查询 MasterTable DDL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照 buildQueryDdlsRun 返回顺序
     * @param table MasterTable
     * @param ddls 上一步查询结果
     * @param set sql执行的结果集
     * @return List
     */
    @Override
    public List<String> ddl(DataRuntime runtime, int index, MasterTable table, List<String> ddls, DataSet set) {
        return super.ddl(runtime, index, table, ddls, set);
    }
    /* *****************************************************************************************************************
     * 													partition table
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * <T extends PartitionTable> LinkedHashMap<String,T> partitionTables(DataRuntime runtime, String random, boolean greedy, MasterTable master, Map<String, Object> tags, String pattern)
     * [命令合成]
     * List<Run> buildQueryPartitionTablesRun(DataRuntime runtime, Catalog catalog, Schema schema, String pattern, int types)
     * List<Run> buildQueryPartitionTablesRun(DataRuntime runtime, Table master, Map<String,Object> tags, String pattern)
     * List<Run> buildQueryPartitionTablesRun(DataRuntime runtime, Table master, Map<String,Object> tags)
     * [结果集封装]<br/>
     * <T extends PartitionTable> LinkedHashMap<String, T> partitionTables(DataRuntime runtime, int total, int index, boolean create, MasterTable master, LinkedHashMap<String, T> tables, Catalog catalog, Schema schema, DataSet set)
     * <T extends PartitionTable> LinkedHashMap<String,T> partitionTables(DataRuntime runtime, boolean create, LinkedHashMap<String, T> tables, Catalog catalog, Schema schema, MasterTable master)
     * [调用入口]
     * List<String> ddl(DataRuntime runtime, String random, PartitionTable table)
     * [命令合成]
     * List<Run> buildQueryDdlsRun(DataRuntime runtime, PartitionTable table)
     * [结果集封装]<br/>
     * List<String> ddl(DataRuntime runtime, int index, PartitionTable table, List<String> ddls, DataSet set)
     ******************************************************************************************************************/
    /**
     * partition table[调用入口]<br/>
     * 查询主表
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param greedy 贪婪模式 true:查询权限范围内尽可能多的数据
     * @param master 主表
     * @param pattern 名称统配符或正则
     * @return List
     * @param <T> MasterTable
     */
    @Override
    public <T extends PartitionTable> LinkedHashMap<String,T> partitionTables(DataRuntime runtime, String random, boolean greedy, MasterTable master, Map<String, Object> tags, String pattern) {
        return super.partitionTables(runtime, random, greedy, master, tags, pattern);
    }

    /**
     * partition table[命令合成]<br/>
     * 查询分区表
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @param types types
     * @return String
     */
    @Override
    public List<Run> buildQueryPartitionTablesRun(DataRuntime runtime, Catalog catalog, Schema schema, String pattern, int types) throws Exception {
        return super.buildQueryPartitionTablesRun(runtime, catalog, schema, pattern, types);
    }
    /**
     * partition table[命令合成]<br/>
     * 根据主表查询分区表
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param master 主表
     * @param tags 标签名+标签值
     * @param name 名称统配符或正则
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildQueryPartitionTablesRun(DataRuntime runtime, Table master, Map<String,Object> tags, String name) throws Exception {
        return super.buildQueryPartitionTablesRun(runtime, master, tags, name);
    }
    /**
     * partition table[命令合成]<br/>
     * 根据主表查询分区表
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param master 主表
     * @param tags 标签名+标签值
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildQueryPartitionTablesRun(DataRuntime runtime, Table master, Map<String,Object> tags) throws Exception {
        return super.buildQueryPartitionTablesRun(runtime, master, tags);
    }
    /**
     * partition table[结果集封装]<br/>
     *  根据查询结果集构造Table
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param total 合计SQL数量
     * @param index 第几条SQL 对照 buildQueryMasterTablesRun返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param master 主表
     * @param catalog catalog
     * @param schema schema
     * @param tables 上一步查询结果
     * @param set 查询结果集
     * @return tables
     * @throws Exception 异常
     */
    @Override
    public <T extends PartitionTable> LinkedHashMap<String, T> partitionTables(DataRuntime runtime, int total, int index, boolean create, MasterTable master, LinkedHashMap<String, T> tables, Catalog catalog, Schema schema, DataSet set) throws Exception {
        return super.partitionTables(runtime, total, index, create, master, tables, catalog, schema, set);
    }
    /**
     * partition table[结果集封装]<br/>
     * 根据根据驱动内置接口
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param master 主表
     * @param catalog catalog
     * @param schema schema
     * @param tables 上一步查询结果
     * @return tables
     * @throws Exception 异常
     */
    @Override
    public <T extends PartitionTable> LinkedHashMap<String,T> partitionTables(DataRuntime runtime, boolean create, LinkedHashMap<String, T> tables, Catalog catalog, Schema schema, MasterTable master) throws Exception {
        return super.partitionTables(runtime, create, tables, catalog, schema, master);
    }
    /**
     * partition table[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param table PartitionTable
     * @return List
     */
    @Override
    public List<String> ddl(DataRuntime runtime, String random, PartitionTable table) {
        return super.ddl(runtime, random, table);
    }

    /**
     * partition table[命令合成]<br/>
     * 查询 PartitionTable DDL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param table PartitionTable
     * @return List
     */
    @Override
    public List<Run> buildQueryDdlsRun(DataRuntime runtime, PartitionTable table) throws Exception {
        return super.buildQueryDdlsRun(runtime, table);
    }

    /**
     * partition table[结果集封装]<br/>
     * 查询 MasterTable DDL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照 buildQueryDdlsRun 返回顺序
     * @param table MasterTable
     * @param ddls 上一步查询结果
     * @param set sql执行的结果集
     * @return List
     */
    @Override
    public List<String> ddl(DataRuntime runtime, int index, PartitionTable table, List<String> ddls, DataSet set) {
        return super.ddl(runtime, index, table, ddls, set);
    }
    /* *****************************************************************************************************************
     * 													column
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * <T extends Column> LinkedHashMap<String, T> columns(DataRuntime runtime, String random, boolean greedy, Table table, boolean primary);
     * <T extends Column> List<T> columns(DataRuntime runtime, String random, boolean greedy, Catalog catalog, Schema schema, String table);
     * [命令合成]
     * List<Run> buildQueryColumnsRun(DataRuntime runtime, Table table, boolean metadata) throws Exception;
     * [结果集封装]<br/>
     * <T extends Column> LinkedHashMap<String, T> columns(DataRuntime runtime, int index, boolean create, Table table, LinkedHashMap<String, T> columns, DataSet set) throws Exception;
     * <T extends Column> List<T> columns(DataRuntime runtime, int index, boolean create, Table table, List<T> columns, DataSet set) throws Exception;
     * <T extends Column> LinkedHashMap<String, T> columns(DataRuntime runtime, boolean create, LinkedHashMap<String, T> columns, Table table, String pattern) throws Exception;
     ******************************************************************************************************************/
    /**
     * column[调用入口]<br/>
     * 查询表结构
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param greedy 贪婪模式 true:如果不填写catalog或schema则查询全部 false:只在当前catalog和schema中查询
     * @param table 表
     * @param primary 是否检测主键
     * @return Column
     * @param <T>  Column
     */
    @Override
    public <T extends Column> LinkedHashMap<String, T> columns(DataRuntime runtime, String random, boolean greedy, Table table, boolean primary) {
        LinkedHashMap<String, T> columns = new LinkedHashMap<>();
        RestClient client = client(runtime);
        String method = "GET";
        String endpoint = table.getName();
        Request request = new Request(
                method,
                endpoint);
        HttpResponse response = exe(runtime, request);
        if(response.getStatus() == 200 ||  response.getStatus() == 201) {
            String txt =response.getText();
            DataRow row = DataRow.parseJson(KeyAdapter.KEY_CASE.SRC, txt);
            DataRow fields = (DataRow)row.recursion(table.getName(),"mappings","properties");
            if(null != fields) {
                List<String> keys = fields.keys();
                for(String key:keys) {
                    DataRow ps = fields.getRow(key);
                    if(null != ps) {
                        T column = (T)new Column(key);
                        column.setType(ps.getString("type"));
                        column.setStore(ps.getBoolean("store", null));
                        column.setAnalyzer(ps.getString("analyzer"));
                        column.setSearchAnalyzer(ps.getString("search_analyzer"));
                        columns.put(key.toUpperCase(), column);
                    }

                }
            }
        }
        return columns;
    }

    /**
     * column[调用入口]<br/>
     * 查询列
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param greedy 贪婪模式 true:如果不填写catalog或schema则查询全部 false:只在当前catalog和schema中查询
     * @param catalog catalog
     * @param schema schema
     * @param table 查询全部表时 输入null
     * @return List
     * @param <T> Column
     */
    @Override
    public <T extends Column> List<T> columns(DataRuntime runtime, String random, boolean greedy, Catalog catalog, Schema schema, Table table) {
        return super.columns(runtime, random, greedy, catalog, schema, table);
    }
    /**
     * column[命令合成]<br/>
     * 查询表上的列
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param table 表
     * @param metadata 是否根据metadata(true:SELECT * FROM T WHERE 1=0,false:查询系统表)
     * @return runs
     */
    @Override
    public List<Run> buildQueryColumnsRun(DataRuntime runtime, Table table, boolean metadata, ConfigStore configs) throws Exception {
        return super.buildQueryColumnsRun(runtime, table, metadata, configs);
    }

    /**
     * column[结果集封装]<br/>
     *  根据查询结果集构造Tag
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照 buildQueryColumnsRun返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param table 表
     * @param columns 上一步查询结果
     * @param set 查询结果集
     * @return tags tags
     * @throws Exception 异常
     */
    @Override
    public <T extends Column> LinkedHashMap<String, T> columns(DataRuntime runtime, int index, boolean create, Table table, LinkedHashMap<String, T> columns, DataSet set) throws Exception {
        return super.columns(runtime, index, create, table, columns, set);
    }
    @Override
    public <T extends Column> List<T> columns(DataRuntime runtime, int index, boolean create, Table table, List<T> columns, DataSet set) throws Exception {
        return super.columns(runtime, index, create, table, columns, set);
    }

    /**
     * column[结果集封装]<br/>
     * 解析JDBC get columns结果
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param table 表
     * @param columns 上一步查询结果
     * @param pattern 名称
     * @throws Exception 异常
     */
    @Override
    public <T extends Column> LinkedHashMap<String, T> columns(DataRuntime runtime, boolean create, LinkedHashMap<String, T> columns, Table table, String pattern) throws Exception {
        return super.columns(runtime, create, columns, table, pattern);
    }

    /* *****************************************************************************************************************
     * 													tag
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * <T extends Tag> LinkedHashMap<String, T> tags(DataRuntime runtime, String random, boolean greedy, Table table)
     * [命令合成]
     * List<Run> buildQueryTagsRun(DataRuntime runtime, Table table, boolean metadata)
     * [结果集封装]<br/>
     * <T extends Tag> LinkedHashMap<String, T> tags(DataRuntime runtime, int index, boolean create, Table table, LinkedHashMap<String, T> tags, DataSet set)
     * <T extends Tag> LinkedHashMap<String, T> tags(DataRuntime runtime, boolean create, LinkedHashMap<String, T> tags, Table table, String pattern)
     ******************************************************************************************************************/

    /**
     * tag[调用入口]<br/>
     * 查询表结构
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param greedy 贪婪模式 true:如果不填写catalog或schema则查询全部 false:只在当前catalog和schema中查询
     * @param table 表
     * @return Tag
     * @param <T>  Tag
     */
    @Override
    public <T extends Tag> LinkedHashMap<String, T> tags(DataRuntime runtime, String random, boolean greedy, Table table) {
        return super.tags(runtime, random, greedy, table);
    }
    /**
     * tag[命令合成]<br/>
     * 查询表上的列
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param table 表
     * @param metadata 是否需要根据metadata
     * @return sqls
     */
    @Override
    public List<Run> buildQueryTagsRun(DataRuntime runtime, Table table, boolean metadata) throws Exception {
        return super.buildQueryTagsRun(runtime, table, metadata);
    }

    /**
     * tag[结果集封装]<br/>
     *  根据查询结果集构造Tag
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条查询SQL 对照 buildQueryTagsRun返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param table 表
     * @param tags 上一步查询结果
     * @param set 查询结果集
     * @return tags
     * @throws Exception 异常
     */
    @Override
    public <T extends Tag> LinkedHashMap<String, T> tags(DataRuntime runtime, int index, boolean create, Table table, LinkedHashMap<String, T> tags, DataSet set) throws Exception {
        return super.tags(runtime, index, create, table, tags, set);
    }
    /**
     *
     * tag[结果集封装]<br/>
     * 解析JDBC get columns结果
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param table 表
     * @param tags 上一步查询结果
     * @param pattern 名称统配符或正则
     * @return tags
     * @throws Exception 异常
     */
    @Override
    public <T extends Tag> LinkedHashMap<String, T> tags(DataRuntime runtime, boolean create, LinkedHashMap<String, T> tags, Table table, String pattern) throws Exception {
        return super.tags(runtime, create, tags, table, pattern);
    }

    /* *****************************************************************************************************************
     * 													primary
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * PrimaryKey primary(DataRuntime runtime, String random, boolean greedy, Table table)
     * [命令合成]
     * List<Run> buildQueryPrimaryRun(DataRuntime runtime, Table table) throws Exception
     * [结构集封装]
     * <T extends PrimaryKey> T init(DataRuntime runtime, int index, T primary, Table table, DataSet set)
     ******************************************************************************************************************/
    /**
     * primary[调用入口]<br/>
     * 查询主键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param greedy 贪婪模式 true:如果不填写catalog或schema则查询全部 false:只在当前catalog和schema中查询
     * @param table 表
     * @return PrimaryKey
     */
    @Override
    public PrimaryKey primary(DataRuntime runtime, String random, boolean greedy, Table table) {
        return super.primary(runtime, random, greedy, table);
    }

    /**
     * primary[命令合成]<br/>
     * 查询表上的主键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param table 表
     * @return sqls
     */
    @Override
    public List<Run> buildQueryPrimaryRun(DataRuntime runtime, Table table) throws Exception {
        return super.buildQueryPrimaryRun(runtime, table);
    }

    /**
     * primary[结构集封装]<br/>
     *  根据查询结果集构造PrimaryKey
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条查询SQL 对照 buildQueryIndexesRun 返回顺序
     * @param table 表
     * @param set sql查询结果
     * @throws Exception 异常
     */
    @Override
    public <T extends PrimaryKey> T init(DataRuntime runtime, int index, T primary, Table table, DataSet set) throws Exception {
        return super.init(runtime, index, primary, table, set);
    }

    /* *****************************************************************************************************************
     * 													foreign
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * <T extends ForeignKey> LinkedHashMap<String, T> foreigns(DataRuntime runtime, String random, boolean greedy, Table table);
     * [命令合成]
     * List<Run> buildQueryForeignsRun(DataRuntime runtime, Table table) throws Exception;
     * [结构集封装]
     * <T extends ForeignKey> LinkedHashMap<String, T> foreigns(DataRuntime runtime, int index, Table table, LinkedHashMap<String, T> foreigns, DataSet set) throws Exception;
     ******************************************************************************************************************/

    /**
     * foreign[调用入口]<br/>
     * 查询外键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param greedy 贪婪模式 true:如果不填写catalog或schema则查询全部 false:只在当前catalog和schema中查询
     * @param table 表
     * @return PrimaryKey
     */
    @Override
    public <T extends ForeignKey> LinkedHashMap<String, T> foreigns(DataRuntime runtime, String random, boolean greedy, Table table) {
        return super.foreigns(runtime, random, greedy,table);
    }
    /**
     * foreign[命令合成]<br/>
     * 查询表上的外键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param table 表
     * @return sqls
     */
    @Override
    public List<Run> buildQueryForeignsRun(DataRuntime runtime, Table table) throws Exception {
        return super.buildQueryForeignsRun(runtime, table);
    }
    /**
     * foreign[结构集封装]<br/>
     *  根据查询结果集构造PrimaryKey
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条查询SQL 对照 buildQueryForeignsRun 返回顺序
     * @param table 表
     * @param foreigns 上一步查询结果
     * @param set sql查询结果
     * @throws Exception 异常
     */
    @Override
    public <T extends ForeignKey> LinkedHashMap<String, T> foreigns(DataRuntime runtime, int index, Table table, LinkedHashMap<String, T> foreigns, DataSet set) throws Exception {
        return super.foreigns(runtime, index, table, foreigns, set);
    }

    /* *****************************************************************************************************************
     * 													index
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * <T extends Index> List<T> indexes(DataRuntime runtime, String random, boolean greedy, Table table, String pattern)
     * <T extends Index> LinkedHashMap<T, Index> indexes(DataRuntime runtime, String random, Table table, String pattern)
     * [命令合成]
     * List<Run> buildQueryIndexesRun(DataRuntime runtime, Table table, String name)
     * [结果集封装]<br/>
     * <T extends Index> List<T> indexes(DataRuntime runtime, int index, boolean create, Table table, List<T> indexes, DataSet set)
     * <T extends Index> LinkedHashMap<String, T> indexes(DataRuntime runtime, int index, boolean create, Table table, LinkedHashMap<String, T> indexes, DataSet set)
     * <T extends Index> List<T> indexes(DataRuntime runtime, boolean create, List<T> indexes, Table table, boolean unique, boolean approximate)
     * <T extends Index> LinkedHashMap<String, T> indexes(DataRuntime runtime, boolean create, LinkedHashMap<String, T> indexes, Table table, boolean unique, boolean approximate)
     ******************************************************************************************************************/
    /**
     *
     * index[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param greedy 贪婪模式 true:如果不填写catalog或schema则查询全部 false:只在当前catalog和schema中查询
     * @param table 表
     * @param pattern 名称统配符或正则
     * @return  LinkedHashMap
     * @param <T> Index
     */
    @Override
    public <T extends Index> List<T> indexes(DataRuntime runtime, String random, boolean greedy, Table table, String pattern) {
        return super.indexes(runtime, random, greedy, table, pattern);
    }
    /**
     *
     * index[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param table 表
     * @param pattern 名称统配符或正则
     * @return  LinkedHashMap
     * @param <T> Index
     */
    @Override
    public <T extends Index> LinkedHashMap<String, T> indexes(DataRuntime runtime, String random, Table table, String pattern) {
        return super.indexes(runtime, random, table, pattern);
    }
    /**
     * index[命令合成]<br/>
     * 查询表上的索引
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param table 表
     * @param name 名称
     * @return sqls
     */
    @Override
    public List<Run> buildQueryIndexesRun(DataRuntime runtime, Table table, String name) {
        return super.buildQueryIndexesRun(runtime, table, name);
    }

    /**
     * index[结果集封装]<br/>
     *  根据查询结果集构造Index
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条查询SQL 对照 buildQueryIndexesRun 返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param table 表
     * @param indexes 上一步查询结果
     * @param set 查询结果集
     * @return indexes indexes
     * @throws Exception 异常
     */
    @Override
    public <T extends Index> LinkedHashMap<String, T> indexes(DataRuntime runtime, int index, boolean create, Table table, LinkedHashMap<String, T> indexes, DataSet set) throws Exception {
        return super.indexes(runtime, index, create, table, indexes, set);
    }
    /**
     * index[结果集封装]<br/>
     *  根据查询结果集构造Index
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条查询SQL 对照 buildQueryIndexesRun 返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param table 表
     * @param indexes 上一步查询结果
     * @param set 查询结果集
     * @return indexes indexes
     * @throws Exception 异常
     */
    @Override
    public <T extends Index> List<T> indexes(DataRuntime runtime, int index, boolean create, Table table, List<T> indexes, DataSet set) throws Exception {
        return super.indexes(runtime, index, create, table, indexes, set);
    }

    /**
     * index[结果集封装]<br/>
     * 根据驱动内置接口
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param table 表
     * @param unique 是否唯一
     * @param approximate 索引允许结果反映近似值
     * @return indexes indexes
     * @throws Exception 异常
     */
    @Override
    public <T extends Index> List<T> indexes(DataRuntime runtime, boolean create, List<T> indexes, Table table, boolean unique, boolean approximate) throws Exception {
        return super.indexes(runtime, create, indexes, table, unique, approximate);
    }
    /**
     * index[结果集封装]<br/>
     * 根据驱动内置接口
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param table 表
     * @param unique 是否唯一
     * @param approximate 索引允许结果反映近似值
     * @return indexes indexes
     * @throws Exception 异常
     */
    @Override
    public <T extends Index> LinkedHashMap<String, T> indexes(DataRuntime runtime, boolean create, LinkedHashMap<String, T> indexes, Table table, boolean unique, boolean approximate) throws Exception {
        return super.indexes(runtime, create, indexes, table, unique, approximate);
    }

    /* *****************************************************************************************************************
     * 													constraint
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * <T extends Constraint> List<T> constraints(DataRuntime runtime, String random, boolean greedy, Table table, String pattern);
     * <T extends Constraint> LinkedHashMap<String, T> constraints(DataRuntime runtime, String random, Table table, Column column, String pattern);
     * [命令合成]
     * List<Run> buildQueryConstraintsRun(DataRuntime runtime, Table table, Column column, String pattern) ;
     * [结果集封装]<br/>
     * <T extends Constraint> List<T> constraints(DataRuntime runtime, int index, boolean create, Table table, List<T> constraints, DataSet set) throws Exception;
     * <T extends Constraint> LinkedHashMap<String, T> constraints(DataRuntime runtime, int index, boolean create, Table table, Column column, LinkedHashMap<String, T> constraints, DataSet set) throws Exception;
     ******************************************************************************************************************/
    /**
     *
     * constraint[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param greedy 贪婪模式 true:如果不填写catalog或schema则查询全部 false:只在当前catalog和schema中查询
     * @param table 表
     * @param pattern 名称统配符或正则
     * @return  LinkedHashMap
     * @param <T> Index
     */
    @Override
    public <T extends Constraint> List<T> constraints(DataRuntime runtime, String random, boolean greedy, Table table, String pattern) {
        return super.constraints(runtime, random, greedy, table, pattern);
    }
    /**
     *
     * constraint[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param table 表
     * @param column 列
     * @param pattern 名称统配符或正则
     * @return  LinkedHashMap
     * @param <T> Index
     */
    @Override
    public <T extends Constraint> LinkedHashMap<String, T> constraints(DataRuntime runtime, String random, Table table, Column column, String pattern) {
        return super.constraints(runtime, random, table, column, pattern);
    }

    /**
     * constraint[命令合成]<br/>
     * 查询表上的约束
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param table 表
     * @param pattern 名称通配符或正则
     * @return sqls
     */
    @Override
    public List<Run> buildQueryConstraintsRun(DataRuntime runtime, Table table, Column column, String pattern) {
        return super.buildQueryConstraintsRun(runtime, table, column, pattern);
    }

    /**
     * constraint[结果集封装]<br/>
     * 根据查询结果集构造Constraint
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条查询SQL 对照 buildQueryConstraintsRun 返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param table 表
     * @param constraints 上一步查询结果
     * @param set DataSet
     * @return constraints constraints
     * @throws Exception 异常
     */
    @Override
    public <T extends Constraint> List<T> constraints(DataRuntime runtime, int index, boolean create, Table table, List<T> constraints, DataSet set) throws Exception {
        return super.constraints(runtime, index, create, table, constraints, set);
    }
    /**
     * constraint[结果集封装]<br/>
     * 根据查询结果集构造Constraint
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条查询SQL 对照 buildQueryConstraintsRun 返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param table 表
     * @param column 列
     * @param constraints 上一步查询结果
     * @param set DataSet
     * @return constraints constraints
     * @throws Exception 异常
     */
    @Override
    public <T extends Constraint> LinkedHashMap<String, T> constraints(DataRuntime runtime, int index, boolean create, Table table, Column column, LinkedHashMap<String, T> constraints, DataSet set) throws Exception {
        return super.constraints(runtime, index, create, table, column, constraints, set);
    }

    /* *****************************************************************************************************************
     * 													trigger
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * <T extends Trigger> LinkedHashMap<String, T> triggers(DataRuntime runtime, String random, boolean greedy, Table table, List<Trigger.EVENT> events)
     * [命令合成]
     * List<Run> buildQueryTriggersRun(DataRuntime runtime, Table table, List<Trigger.EVENT> events)
     * [结果集封装]<br/>
     * <T extends Trigger> LinkedHashMap<String, T> triggers(DataRuntime runtime, int index, boolean create, Table table, LinkedHashMap<String, T> triggers, DataSet set)
     ******************************************************************************************************************/

    /**
     *
     * trigger[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param greedy 贪婪模式 true:如果不填写catalog或schema则查询全部 false:只在当前catalog和schema中查询
     * @param table 表
     * @param events 事件 INSERT|UPDATE|DELETE
     * @return  LinkedHashMap
     * @param <T> Index
     */
    public <T extends Trigger> LinkedHashMap<String, T> triggers(DataRuntime runtime, String random, boolean greedy, Table table, List<Trigger.EVENT> events) {
        return super.triggers(runtime, random, greedy, table, events);
    }
    /**
     * trigger[命令合成]<br/>
     * 查询表上的 Trigger
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param table 表
     * @param events 事件 INSERT|UPDATE|DELETE
     * @return sqls
     */
    public List<Run> buildQueryTriggersRun(DataRuntime runtime, Table table, List<Trigger.EVENT> events) {
        return super.buildQueryTriggersRun(runtime, table, events);
    }
    /**
     * trigger[结果集封装]<br/>
     * 根据查询结果集构造 Trigger
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条查询SQL 对照 buildQueryConstraintsRun 返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param table 表
     * @param triggers 上一步查询结果
     * @param set 查询结果集
     * @return LinkedHashMap
     * @throws Exception 异常
     */
    public <T extends Trigger> LinkedHashMap<String, T> triggers(DataRuntime runtime, int index, boolean create, Table table, LinkedHashMap<String, T> triggers, DataSet set) throws Exception {
        return super.triggers(runtime, index, create, table, triggers, set);
    }

    /* *****************************************************************************************************************
     * 													procedure
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * <T extends Procedure> List<T> procedures(DataRuntime runtime, String random, boolean greedy, Catalog catalog, Schema schema, String pattern);
     * <T extends Procedure> LinkedHashMap<String, T> procedures(DataRuntime runtime, String random, Catalog catalog, Schema schema, String pattern);
     * [命令合成]
     * List<Run> buildQueryProceduresRun(DataRuntime runtime, Catalog catalog, Schema schema, String pattern) ;
     * [结果集封装]<br/>
     * <T extends Procedure> List<T> procedures(DataRuntime runtime, int index, boolean create, List<T> procedures, DataSet set) throws Exception;
     * <T extends Procedure> LinkedHashMap<String, T> procedures(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, T> procedures, DataSet set) throws Exception;
     * <T extends Procedure> List<T> procedures(DataRuntime runtime, boolean create, List<T> procedures, DataSet set) throws Exception;
     * <T extends Procedure> LinkedHashMap<String, T> procedures(DataRuntime runtime, boolean create, LinkedHashMap<String, T> procedures, DataSet set) throws Exception;
     * [调用入口]
     * List<String> ddl(DataRuntime runtime, String random, Procedure procedure);
     * [命令合成]
     * List<Run> buildQueryDdlsRun(DataRuntime runtime, Procedure procedure) throws Exception;
     * [结果集封装]<br/>
     * List<String> ddl(DataRuntime runtime, int index, Procedure procedure, List<String> ddls, DataSet set);
     ******************************************************************************************************************/
    /**
     *
     * procedure[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param greedy 贪婪模式 true:如果不填写catalog或schema则查询全部 false:只在当前catalog和schema中查询
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @return  LinkedHashMap
     * @param <T> Index
     */
    @Override
    public <T extends Procedure> List<T> procedures(DataRuntime runtime, String random, boolean greedy, Catalog catalog, Schema schema, String pattern) {
        return super.procedures(runtime, random, greedy, catalog, schema, pattern);
    }
    /**
     *
     * procedure[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @return  LinkedHashMap
     * @param <T> Index
     */
    @Override
    public <T extends Procedure> LinkedHashMap<String, T> procedures(DataRuntime runtime, String random, Catalog catalog, Schema schema, String pattern) {
        return super.procedures(runtime, random, catalog, schema, pattern);
    }
    /**
     * procedure[命令合成]<br/>
     * 查询表上的 Trigger
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @return sqls
     */
    @Override
    public List<Run> buildQueryProceduresRun(DataRuntime runtime, Catalog catalog, Schema schema, String pattern) {
        return super.buildQueryProceduresRun(runtime, catalog, schema, pattern);
    }
    /**
     * procedure[结果集封装]<br/>
     * 根据查询结果集构造 Trigger
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条查询SQL 对照 buildQueryConstraintsRun 返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param procedures 上一步查询结果
     * @param set 查询结果集
     * @return LinkedHashMap
     * @throws Exception 异常
     */
    @Override
    public <T extends Procedure> LinkedHashMap<String, T> procedures(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, T> procedures, DataSet set) throws Exception {
        return super.procedures(runtime, index, create, procedures, set);
    }

    /**
     * procedure[结果集封装]<br/>
     * 根据驱动内置接口补充 Procedure
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param procedures 上一步查询结果
     * @return List
     * @throws Exception 异常
     */
    @Override
    public <T extends Procedure> List<T> procedures(DataRuntime runtime, boolean create, List<T> procedures) throws Exception {
        return super.procedures(runtime, create, procedures);
    }

    /**
     * procedure[结果集封装]<br/>
     * 根据驱动内置接口补充 Procedure
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param procedures 上一步查询结果
     * @return LinkedHashMap
     * @throws Exception 异常
     */
    @Override
    public <T extends Procedure> LinkedHashMap<String, T> procedures(DataRuntime runtime, boolean create, LinkedHashMap<String, T> procedures) throws Exception {
        return super.procedures(runtime, create, procedures);
    }
    /**
     *
     * procedure[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param procedure Procedure
     * @return ddl
     */
    @Override
    public List<String> ddl(DataRuntime runtime, String random, Procedure procedure) {
        return super.ddl(runtime, random, procedure);
    }
    /**
     * procedure[命令合成]<br/>
     * 查询存储DDL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param procedure 存储过程
     * @return List
     */
    @Override
    public List<Run> buildQueryDdlsRun(DataRuntime runtime, Procedure procedure) throws Exception {
        return super.buildQueryDdlsRun(runtime, procedure);
    }

    /**
     * procedure[结果集封装]<br/>
     * 查询 Procedure DDL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照 buildQueryDdlsRun 返回顺序
     * @param procedure Procedure
     * @param ddls 上一步查询结果
     * @param set 查询结果集
     * @return List
     */
    @Override
    public List<String> ddl(DataRuntime runtime, int index, Procedure procedure, List<String> ddls, DataSet set) {
        return super.ddl(runtime, index, procedure, ddls, set);
    }

    /* *****************************************************************************************************************
     * 													function
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * <T extends Function> List<T> functions(DataRuntime runtime, String random, boolean greedy, Catalog catalog, Schema schema, String pattern);
     * <T extends Function> LinkedHashMap<String, T> functions(DataRuntime runtime, String random, Catalog catalog, Schema schema, String pattern);
     * [命令合成]
     * List<Run> buildQueryFunctionsRun(DataRuntime runtime, Catalog catalog, Schema schema, String pattern) ;
     * [结果集封装]<br/>
     * <T extends Function> List<T> functions(DataRuntime runtime, int index, boolean create, List<T> functions, Catalog catalog, Schema schema, DataSet set) throws Exception;
     * <T extends Function> LinkedHashMap<String, T> functions(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, T> functions, Catalog catalog, Schema schema, DataSet set) throws Exception;
     * <T extends Function> List<T> functions(DataRuntime runtime, boolean create, List<T> functions, DataSet set) throws Exception;
     * <T extends Function> LinkedHashMap<String, T> functions(DataRuntime runtime, boolean create, LinkedHashMap<String, T> functions, DataSet set) throws Exception;
     * [调用入口]
     * List<String> ddl(DataRuntime runtime, String random, Function function);
     * [命令合成]
     * List<Run> buildQueryDdlsRun(DataRuntime runtime, Function function) throws Exception;
     * [结果集封装]<br/>
     * List<String> ddl(DataRuntime runtime, int index, Function function, List<String> ddls, DataSet set)
     ******************************************************************************************************************/
    /**
     *
     * function[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param greedy 贪婪模式 true:如果不填写catalog或schema则查询全部 false:只在当前catalog和schema中查询
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @return  LinkedHashMap
     * @param <T> Index
     */
    @Override
    public <T extends Function> List<T> functions(DataRuntime runtime, String random, boolean greedy, Catalog catalog, Schema schema, String pattern) {
        return super.functions(runtime, random, greedy, catalog, schema, pattern);
    }
    /**
     *
     * function[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @return  LinkedHashMap
     * @param <T> Index
     */
    @Override
    public <T extends Function> LinkedHashMap<String, T> functions(DataRuntime runtime, String random, Catalog catalog, Schema schema, String pattern) {
        return super.functions(runtime, random, catalog, schema, pattern);
    }
    /**
     * function[命令合成]<br/>
     * 查询表上的 Trigger
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param catalog catalog
     * @param schema schema
     * @param name 名称统配符或正则
     * @return sqls
     */
    @Override
    public List<Run> buildQueryFunctionsRun(DataRuntime runtime, Catalog catalog, Schema schema, String name) {
        return super.buildQueryFunctionsRun(runtime, catalog, schema, name);
    }

    /**
     * function[结果集封装]<br/>
     * 根据查询结果集构造 Trigger
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条查询SQL 对照 buildQueryConstraintsRun 返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param functions 上一步查询结果
     * @param set 查询结果集
     * @return LinkedHashMap
     * @throws Exception 异常
     */
    @Override
    public <T extends Function> List<T> functions(DataRuntime runtime, int index, boolean create, List<T> functions, Catalog catalog, Schema schema, DataSet set) throws Exception {
        return super.functions(runtime, index, create, functions, catalog, schema, set);
    }
    /**
     * function[结果集封装]<br/>
     * 根据查询结果集构造 Trigger
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条查询SQL 对照 buildQueryConstraintsRun 返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param functions 上一步查询结果
     * @param set 查询结果集
     * @return LinkedHashMap
     * @throws Exception 异常
     */
    @Override
    public <T extends Function> LinkedHashMap<String, T> functions(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, T> functions, Catalog catalog, Schema schema, DataSet set) throws Exception {
        return super.functions(runtime, index, create, functions, catalog, schema, set);
    }

    /**
     * function[结果集封装]<br/>
     * 根据驱动内置接口补充 Function
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param functions 上一步查询结果
     * @return LinkedHashMap
     * @throws Exception 异常
     */
    @Override
    public <T extends Function> List<T> functions(DataRuntime runtime, boolean create, List<T> functions) throws Exception {
        return super.functions(runtime, create, functions);
    }

    /**
     *
     * function[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param meta Function
     * @return ddl
     */
    @Override
    public List<String> ddl(DataRuntime runtime, String random, Function meta) {
        return super.ddl(runtime, random, meta);
    }

    /**
     * function[命令合成]<br/>
     * 查询函数DDL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 函数
     * @return List
     */
    @Override
    public List<Run> buildQueryDdlsRun(DataRuntime runtime, Function meta) throws Exception {
        return super.buildQueryDdlsRun(runtime, meta);
    }
    /**
     * function[结果集封装]<br/>
     * 查询 Function DDL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照 buildQueryDdlsRun 返回顺序
     * @param function Function
     * @param ddls 上一步查询结果
     * @param set 查询结果集
     * @return List
     */
    @Override
    public List<String> ddl(DataRuntime runtime, int index, Function function, List<String> ddls, DataSet set) {
        return super.ddl(runtime, index, function, ddls, set);
    }

    /* *****************************************************************************************************************
     * 													sequence
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * <T extends Sequence> List<T> sequences(DataRuntime runtime, String random, boolean greedy, Catalog catalog, Schema schema, String pattern);
     * <T extends Sequence> LinkedHashMap<String, T> sequences(DataRuntime runtime, String random, Catalog catalog, Schema schema, String pattern);
     * [命令合成]
     * List<Run> buildQuerySequencesRun(DataRuntime runtime, Catalog catalog, Schema schema, String pattern) ;
     * [结果集封装]<br/>
     * <T extends Sequence> List<T> sequences(DataRuntime runtime, int index, boolean create, List<T> sequences, DataSet set) throws Exception;
     * <T extends Sequence> LinkedHashMap<String, T> sequences(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, T> sequences, DataSet set) throws Exception;
     * <T extends Sequence> List<T> sequences(DataRuntime runtime, boolean create, List<T> sequences, DataSet set) throws Exception;
     * <T extends Sequence> LinkedHashMap<String, T> sequences(DataRuntime runtime, boolean create, LinkedHashMap<String, T> sequences, DataSet set) throws Exception;
     * [调用入口]
     * List<String> ddl(DataRuntime runtime, String random, Sequence sequence);
     * [命令合成]
     * List<Run> buildQueryDdlsRun(DataRuntime runtime, Sequence sequence) throws Exception;
     * [结果集封装]<br/>
     * List<String> ddl(DataRuntime runtime, int index, Sequence sequence, List<String> ddls, DataSet set)
     ******************************************************************************************************************/
    /**
     *
     * sequence[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param greedy 贪婪模式 true:如果不填写catalog或schema则查询全部 false:只在当前catalog和schema中查询
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @return  LinkedHashMap
     * @param <T> Index
     */
    @Override
    public <T extends Sequence> List<T> sequences(DataRuntime runtime, String random, boolean greedy, Catalog catalog, Schema schema, String pattern) {
        return super.sequences(runtime, random, greedy, catalog, schema, pattern);
    }
    /**
     *
     * sequence[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param catalog catalog
     * @param schema schema
     * @param pattern 名称统配符或正则
     * @return  LinkedHashMap
     * @param <T> Index
     */
    @Override
    public <T extends Sequence> LinkedHashMap<String, T> sequences(DataRuntime runtime, String random, Catalog catalog, Schema schema, String pattern) {
        return super.sequences(runtime, random, catalog, schema, pattern);
    }
    /**
     * sequence[命令合成]<br/>
     * 查询表上的 Trigger
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param catalog catalog
     * @param schema schema
     * @param name 名称统配符或正则
     * @return sqls
     */
    @Override
    public List<Run> buildQuerySequencesRun(DataRuntime runtime, Catalog catalog, Schema schema, String name) {
        return super.buildQuerySequencesRun(runtime, catalog, schema, name);
    }

    /**
     * sequence[结果集封装]<br/>
     * 根据查询结果集构造 Trigger
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条查询SQL 对照 buildQueryConstraintsRun 返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param sequences 上一步查询结果
     * @param set 查询结果集
     * @return LinkedHashMap
     * @throws Exception 异常
     */
    @Override
    public <T extends Sequence> List<T> sequences(DataRuntime runtime, int index, boolean create, List<T> sequences, DataSet set) throws Exception {
        return super.sequences(runtime, index, create, sequences, set);
    }
    /**
     * sequence[结果集封装]<br/>
     * 根据查询结果集构造 Trigger
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条查询SQL 对照 buildQueryConstraintsRun 返回顺序
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param sequences 上一步查询结果
     * @param set 查询结果集
     * @return LinkedHashMap
     * @throws Exception 异常
     */
    @Override
    public <T extends Sequence> LinkedHashMap<String, T> sequences(DataRuntime runtime, int index, boolean create, LinkedHashMap<String, T> sequences, DataSet set) throws Exception {
        return super.sequences(runtime, index, create, sequences, set);
    }

    /**
     * sequence[结果集封装]<br/>
     * 根据驱动内置接口补充 Sequence
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param create 上一步没有查到的,这一步是否需要新创建
     * @param sequences 上一步查询结果
     * @return LinkedHashMap
     * @throws Exception 异常
     */
    @Override
    public <T extends Sequence> List<T> sequences(DataRuntime runtime, boolean create, List<T> sequences) throws Exception {
        return super.sequences(runtime, create, sequences);
    }

    /**
     *
     * sequence[调用入口]<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param meta Sequence
     * @return ddl
     */
    @Override
    public List<String> ddl(DataRuntime runtime, String random, Sequence meta) {
        return super.ddl(runtime, random, meta);
    }

    /**
     * sequence[命令合成]<br/>
     * 查询序列DDL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 序列
     * @return List
     */
    @Override
    public List<Run> buildQueryDdlsRun(DataRuntime runtime, Sequence meta) throws Exception {
        return super.buildQueryDdlsRun(runtime, meta);
    }
    /**
     * sequence[结果集封装]<br/>
     * 查询 Sequence DDL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param index 第几条SQL 对照 buildQueryDdlsRun 返回顺序
     * @param sequence Sequence
     * @param ddls 上一步查询结果
     * @param set 查询结果集
     * @return List
     */
    @Override
    public List<String> ddl(DataRuntime runtime, int index, Sequence sequence, List<String> ddls, DataSet set) {
        return super.ddl(runtime, index, sequence, ddls, set);
    }

    /* *****************************************************************************************************************
     * 													common
     * ----------------------------------------------------------------------------------------------------------------
     */
    /**
     *
     * 根据 catalog, schema, name检测tables集合中是否存在
     * @param metas metas
     * @param catalog catalog
     * @param schema schema
     * @param name name
     * @return 如果存在则返回Table 不存在则返回null
     * @param <T> Table
     */
    @Override
    public <T extends Metadata> T search(List<T> metas, Catalog catalog, Schema schema, String name) {
        return super.search(metas, catalog, schema, name);
    }

    /**
     *
     * 根据 catalog, name检测schemas集合中是否存在
     * @param schemas schemas
     * @param catalog catalog
     * @param name name
     * @return 如果存在则返回 Schema 不存在则返回null
     * @param <T> Table
     */
    @Override
    public <T extends Schema> T schema(List<T> schemas, Catalog catalog, String name) {
        return super.schema(schemas, catalog, name);
    }

    /**
     *
     * 根据 name检测catalogs集合中是否存在
     * @param catalogs catalogs
     * @param name name
     * @return 如果存在则返回 Catalog 不存在则返回null
     * @param <T> Table
     */
    @Override
    public <T extends Catalog> T catalog(List<T> catalogs, String name) {
        return super.catalog(catalogs, name);
    }
    /**
     *
     * 根据 name检测databases集合中是否存在
     * @param databases databases
     * @param name name
     * @return 如果存在则返回 Database 不存在则返回null
     * @param <T> Table
     */
    @Override
    public <T extends Database> T database(List<T> databases, String name) {
        return super.database(databases, name);
    }
    /* *****************************************************************************************************************
     *
     * 													DDL
     *
     * =================================================================================================================
     * database			: 数据库
     * table			: 表
     * master table		: 主表
     * partition table	: 分区表
     * column			: 列
     * tag				: 标签
     * primary key      : 主键
     * foreign key		: 外键
     * index			: 索引
     * constraint		: 约束
     * trigger		    : 触发器
     * procedure        : 存储过程
     * function         : 函数
     ******************************************************************************************************************/

    /**
     * ddl [执行命令]
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param random 用来标记同一组命令
     * @param meta Metadata(表,列等)
     * @param action 执行命令
     * @param run 最终待执行的命令和参数(如JDBC环境中的SQL)
     * @return boolean
     */
    @Override
    public boolean execute(DataRuntime runtime, String random, Metadata meta, ACTION.DDL action, Run run) {
        if(null == run) {
            return false;
        }
        boolean result = false;
        run.action(action);
        meta.addRun(run);
        if(meta.execute()) {
            try {
                update(runtime, random, (Table) null, null, null, run);
            }finally {
                CacheProxy.clear();
            }
        }
        result = true;
        return result;
    }
    /* *****************************************************************************************************************
     * 													table
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * boolean create(DataRuntime runtime, Table meta)
     * boolean alter(DataRuntime runtime, Table meta)
     * boolean drop(DataRuntime runtime, Table meta)
     * boolean rename(DataRuntime runtime, Table origin, String name)
     * [命令合成]
     * List<Run> buildCreateRun(DataRuntime runtime, Table meta)
     * List<Run> buildAlterRun(DataRuntime runtime, Table meta)
     * List<Run> buildAlterRun(DataRuntime runtime, Table meta, Collection<Column> columns)
     * List<Run> buildRenameRun(DataRuntime runtime, Table meta)
     * List<Run> buildDropRun(DataRuntime runtime, Table meta)
     * [命令合成-子流程]
     * List<Run> buildAppendCommentRun(DataRuntime runtime, Table table)
     * List<Run> buildChangeCommentRun(DataRuntime runtime, Table table)
     * StringBuilder checkTableExists(DataRuntime runtime, StringBuilder builder, boolean exists)
     * StringBuilder primary(DataRuntime runtime, StringBuilder builder, Table table)
     * time runtime, StringBuilder builder, Table table)
     * StringBuilder comment(DataRuntime runtime, StringBuilder builder, Table table)
     * StringBuilder partitionBy(DataRuntime runtime, StringBuilder builder, Table table)
     * StringBuilder partitionOf(DataRuntime runtime, StringBuilder builder, Table table)
     ******************************************************************************************************************/
    /**
     * table[调用入口]<br/>
     * 创建表,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */
    @Override
    public boolean create(DataRuntime runtime, Table meta) throws Exception {
        return super.create(runtime, meta);
    }

    /**
     * table[调用入口]<br/>
     * 修改表,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */

    @Override
    public boolean alter(DataRuntime runtime, Table meta) throws Exception {
        return super.alter(runtime, meta);
    }
    /**
     * table[调用入口]<br/>
     * 删除表,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */

    @Override
    public boolean drop(DataRuntime runtime, Table meta) throws Exception {
        return super.drop(runtime, meta);
    }

    /**
     * table[调用入口]<br/>
     * 重命名表,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param origin 原表
     * @param name 新名称
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */

    @Override
    public boolean rename(DataRuntime runtime, Table origin, String name) throws Exception {
        return super.rename(runtime, origin, name);
    }

    /**
     * table[命令合成-子流程]<br/>
     * 部分数据库在创建主表时用主表关键字(默认)，部分数据库普通表主表子表都用table，部分数据库用collection、timeseries等
     * @param meta 表
     * @return String
     */
    @Override
    public String keyword(Metadata meta)
{
        return meta.getKeyword();
    }

    /**
     * table[命令合成]<br/>
     * 创建表<br/>
     * 关于创建主键的几个环节<br/>
     * 1.1.定义列时 标识 primary(DataRuntime runtime, StringBuilder builder, Column column)<br/>
     * 1.2.定义表时 标识 primary(DataRuntime runtime, StringBuilder builder, Table table)<br/>
     * 1.3.定义完表DDL后，单独创建 primary(DataRuntime runtime, PrimaryKey primary)根据三选一情况调用buildCreateRun<br/>
     * 2.单独创建 buildCreateRun(DataRuntime runtime, PrimaryKey primary)<br/>
     * 其中1.x三选一 不要重复
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return runs
     * @throws Exception Exception
     */
    @Override
    public List<Run> buildCreateRun(DataRuntime runtime, Table meta) throws Exception {
        List<Run> runs = new ArrayList<>();
        ElasticSearchRun run = new ElasticSearchRun(runtime, meta);
        run.setTable(meta);
        run.action(ACTION.DDL.TABLE_CREATE);
        run.setMethod("PUT");
        run.setEndpoint("/"+meta.getName());
        run.setTable(meta);
        runs.add(run);
        LinkedHashMap<String, Column> columns = meta.getColumns();
        for(Column column:columns.values()){
            typeMetadata(runtime, column);
        }
        LinkedHashMap<String, Object> map = ElasticSearchBuilder.build(meta);
        String json = BeanUtil.map2json(map);
        run.getBuilder().append(json);
        return runs;
    }
    /**
     * table[命令合成]<br/>
     * 修改表
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildAlterRun(DataRuntime runtime, Table meta) throws Exception {
        return super.buildAlterRun(runtime, meta);
    }

    /**
     * table[命令合成]<br/>
     * 修改列
     * 有可能生成多条SQL,根据数据库类型优先合并成一条执行
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @param columns 列
     * @return List
     */
    @Override
    public List<Run> buildAlterRun(DataRuntime runtime, Table meta, Collection<Column> columns) throws Exception {
        return super.buildAlterRun(runtime, meta, columns);
    }

    /**
     * table[命令合成]<br/>
     * 重命名
     * 子类实现
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildRenameRun(DataRuntime runtime, Table meta) throws Exception {
        return super.buildRenameRun(runtime, meta);
    }
    /**
     * table[命令合成]<br/>
     * 删除表
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildDropRun(DataRuntime runtime, Table meta) throws Exception {
        List<Run> runs = new ArrayList<>();
        ElasticSearchRun run = new ElasticSearchRun(runtime, meta);
        run.setTable(meta);
        run.action(ACTION.DDL.TABLE_DROP);
        run.setMethod("DELETE");
        run.setEndpoint("/"+meta.getName());
        runs.add(run);
        return runs;
    }

    /**
     * table[命令合成-子流程]<br/>
     * 创建表完成后追加表备注,创建过程能添加备注的不需要实现与comment(DataRuntime runtime, StringBuilder builder, Table meta)二选一实现
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildAppendCommentRun(DataRuntime runtime, Table meta) throws Exception {
        return super.buildAppendCommentRun(runtime, meta);
    }

    /**
     * table[命令合成-子流程]<br/>
     * 修改备注
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildChangeCommentRun(DataRuntime runtime, Table meta) throws Exception {
        return super.buildChangeCommentRun(runtime, meta);
    }

    /**
     * table[命令合成-子流程]<br/>
     * 创建或删除表之前  检测表是否存在
     * IF NOT EXISTS
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param exists exists
     * @return StringBuilder
     */
    @Override
    public StringBuilder checkTableExists(DataRuntime runtime, StringBuilder builder, boolean exists) {
        return super.checkTableExists(runtime, builder, exists);
    }

    /**
     * table[命令合成-子流程]<br/>
     * 检测表主键(在没有显式设置主键时根据其他条件判断如自增),同时根据主键对象给相关列设置主键标识
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param table 表
     */
    @Override
    public void checkPrimary(DataRuntime runtime, Table table) {
        super.checkPrimary(runtime, table);
    }

    /**
     * table[命令合成-子流程]<br/>
     * 定义表的主键标识,在创建表的DDL结尾部分(注意不要跟列定义中的主键重复)
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param meta 表
     * @return StringBuilder
     */
    @Override
    public StringBuilder primary(DataRuntime runtime, StringBuilder builder, Table meta) {
        return super.primary(runtime, builder, meta);
    }

    /**
     * table[命令合成-子流程]<br/>
     * 编码
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param meta 表
     * @return StringBuilder
     */
    @Override
    public StringBuilder charset(DataRuntime runtime, StringBuilder builder, Table meta) {
        return super.charset(runtime, builder, meta);
    }

    /**
     * table[命令合成-子流程]<br/>
     * 备注 创建表的完整DDL拼接COMMENT部分，与buildAppendCommentRun二选一实现
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param meta 表
     * @return StringBuilder
     */
    @Override
    public StringBuilder comment(DataRuntime runtime, StringBuilder builder, Table meta) {
        return super.comment(runtime, builder, meta);
    }

    /**
     * table[命令合成-子流程]<br/>
     * 主表设置分区依据(根据哪几列分区)
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param meta 表
     * @return StringBuilder
     * @throws Exception 异常
     */
    @Override
    public StringBuilder partitionBy(DataRuntime runtime, StringBuilder builder, Table meta) throws Exception {
        return super.partitionBy(runtime, builder, meta);
    }

    /**
     * table[命令合成-子流程]<br/>
     * 子表执行分区依据(相关主表及分区值)
     * 如CREATE TABLE hr_user_fi PARTITION OF hr_user FOR VALUES IN ('FI')
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param meta 表
     * @return StringBuilder
     * @throws Exception 异常
     */
    @Override
    public StringBuilder partitionOf(DataRuntime runtime, StringBuilder builder, Table meta) throws Exception {
        return super.partitionOf(runtime, builder, meta);
    }

    /* *****************************************************************************************************************
     * 													view
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * boolean create(DataRuntime runtime, View view) throws Exception;
     * boolean alter(DataRuntime runtime, View view) throws Exception;
     * boolean drop(DataRuntime runtime, View view) throws Exception;
     * boolean rename(DataRuntime runtime, View origin, String name) throws Exception;
     * [命令合成]
     * List<Run> buildCreateRun(DataRuntime runtime, View view) throws Exception;
     * List<Run> buildAlterRun(DataRuntime runtime, View view) throws Exception;
     * List<Run> buildRenameRun(DataRuntime runtime, View view) throws Exception;
     * List<Run> buildDropRun(DataRuntime runtime, View view) throws Exception;
     * [命令合成-子流程]
     * List<Run> buildAppendCommentRun(DataRuntime runtime, View view) throws Exception;
     * List<Run> buildChangeCommentRun(DataRuntime runtime, View view) throws Exception;
     * StringBuilder checkViewExists(DataRuntime runtime, StringBuilder builder, boolean exists);
     * StringBuilder comment(DataRuntime runtime, StringBuilder builder, View view);
     ******************************************************************************************************************/
    /**
     * view[调用入口]<br/>
     * 创建视图,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 视图
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */
    @Override
    public boolean create(DataRuntime runtime, View meta) throws Exception {
        return super.create(runtime, meta);
    }

    /**
     * view[调用入口]<br/>
     * 修改视图,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 视图
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */
    @Override
    public boolean alter(DataRuntime runtime, View meta) throws Exception {
        return super.alter(runtime, meta);
    }

    /**
     * view[调用入口]<br/>
     * 删除视图,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 视图
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */
    @Override
    public boolean drop(DataRuntime runtime, View meta) throws Exception {
        return super.drop(runtime, meta);
    }

    /**
     * view[调用入口]<br/>
     * 重命名视图,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param origin 视图
     * @param name 新名称
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */
    @Override
    public boolean rename(DataRuntime runtime, View origin, String name) throws Exception {
        return super.rename(runtime, origin, name);
    }

    /**
     * view[命令合成]<br/>
     * 创建视图
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 视图
     * @return Run
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildCreateRun(DataRuntime runtime, View meta) throws Exception {
        return super.buildCreateRun(runtime, meta);
    }
    /**
     * view[命令合成]<br/>
     * 修改视图
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 视图
     * @return sql
     * @throws Exception 异常
     */

    @Override
    public List<Run> buildAlterRun(DataRuntime runtime, View meta) throws Exception {
        return super.buildAlterRun(runtime, meta);
    }
    /**
     * view[命令合成]<br/>
     * 重命名
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 视图
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildRenameRun(DataRuntime runtime, View meta) throws Exception {
        return super.buildRenameRun(runtime, meta);
    }
    /**
     * view[命令合成]<br/>
     * 删除视图
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 视图
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildDropRun(DataRuntime runtime, View meta) throws Exception {
        return super.buildDropRun(runtime, meta);
    }

    /**
     * view[命令合成-子流程]<br/>
     * 添加视图备注(视图创建完成后调用,创建过程能添加备注的不需要实现)
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 视图
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildAppendCommentRun(DataRuntime runtime, View meta) throws Exception {
        return super.buildAppendCommentRun(runtime, meta);
    }

    /**
     * view[命令合成-子流程]<br/>
     * 修改备注
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 视图
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildChangeCommentRun(DataRuntime runtime, View meta) throws Exception {
        return super.buildChangeCommentRun(runtime, meta);
    }

    /**
     * view[命令合成-子流程]<br/>
     * 创建或删除视图之前  检测视图是否存在
     * IF NOT EXISTS
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param exists exists
     * @return StringBuilder
     */
    @Override
    public StringBuilder checkViewExists(DataRuntime runtime, StringBuilder builder, boolean exists) {
        return super.checkViewExists(runtime, builder, exists);
    }

    /**
     * view[命令合成-子流程]<br/>
     * 视图备注
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param meta 视图
     * @return StringBuilder
     */
    @Override
    public StringBuilder comment(DataRuntime runtime, StringBuilder builder, View meta) {
        return super.comment(runtime, builder, meta);
    }

    /* *****************************************************************************************************************
     * 													MasterTable
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * boolean create(DataRuntime runtime, MasterTable meta)
     * boolean alter(DataRuntime runtime, MasterTable meta)
     * boolean drop(DataRuntime runtime, MasterTable meta)
     * boolean rename(DataRuntime runtime, MasterTable origin, String name)
     * [命令合成]
     * List<Run> buildCreateRun(DataRuntime runtime, MasterTable table)
     * List<Run> buildDropRun(DataRuntime runtime, MasterTable table)
     * [命令合成-子流程]
     * List<Run> buildAlterRun(DataRuntime runtime, MasterTable table)
     * List<Run> buildRenameRun(DataRuntime runtime, MasterTable table)
     * List<Run> buildAppendCommentRun(DataRuntime runtime, MasterTable table)
     * List<Run> buildChangeCommentRun(DataRuntime runtime, MasterTable table)
     ******************************************************************************************************************/

    /**
     * master table[调用入口]<br/>
     * 创建主表,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 主表
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */
    @Override
    public boolean create(DataRuntime runtime, MasterTable meta) throws Exception {
        return super.create(runtime, meta);
    }

    /**
     * master table[调用入口]<br/>
     * 修改主表,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 主表
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */
    @Override
    public boolean alter(DataRuntime runtime, MasterTable meta) throws Exception {
        return super.alter(runtime, meta);
    }

    /**
     * master table[调用入口]<br/>
     * 删除主表,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 主表
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */
    @Override
    public boolean drop(DataRuntime runtime, MasterTable meta) throws Exception {
        return super.drop(runtime, meta);
    }

    /**
     * master table[调用入口]<br/>
     * 重命名主表,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param origin 原表
     * @param name 新名称
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */
    @Override
    public boolean rename(DataRuntime runtime, MasterTable origin, String name) throws Exception {
        return super.rename(runtime, origin, name);
    }

    /**
     * master table[命令合成]<br/>
     * 创建主表
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildCreateRun(DataRuntime runtime, MasterTable meta) throws Exception {
        return super.buildCreateRun(runtime, meta);
    }

    /**
     * master table[命令合成]<br/>
     * 删除主表
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildDropRun(DataRuntime runtime, MasterTable meta) throws Exception {
        return super.buildDropRun(runtime, meta);
    }
    /**
     * master table[命令合成-子流程]<br/>
     * 修改主表
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildAlterRun(DataRuntime runtime, MasterTable meta) throws Exception {
        return super.buildAlterRun(runtime, meta);
    }
    /**
     * master table[命令合成-子流程]<br/>
     * 主表重命名
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildRenameRun(DataRuntime runtime, MasterTable meta) throws Exception {
        return super.buildRenameRun(runtime, meta);
    }

    /**
     * master table[命令合成-子流程]<br/>
     * 创建表完成后追加表备注,创建过程能添加备注的不需要实现与comment(DataRuntime runtime, StringBuilder builder, Table meta)二选一实现
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildAppendCommentRun(DataRuntime runtime, MasterTable meta) throws Exception {
        return super.buildAppendCommentRun(runtime, meta);
    }

    /**
     * master table[命令合成-子流程]<br/>
     * 修改主表备注
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildChangeCommentRun(DataRuntime runtime, MasterTable meta) throws Exception {
        return super.buildChangeCommentRun(runtime, meta);
    }

    /* *****************************************************************************************************************
     * 													partition table
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * boolean create(DataRuntime runtime, PartitionTable meta) throws Exception;
     * boolean alter(DataRuntime runtime, PartitionTable meta) throws Exception;
     * boolean drop(DataRuntime runtime, PartitionTable meta) throws Exception;
     * boolean rename(DataRuntime runtime, PartitionTable origin, String name) throws Exception;
     * [命令合成]
     * List<Run> buildCreateRun(DataRuntime runtime, PartitionTable table) throws Exception;
     * List<Run> buildAppendCommentRun(DataRuntime runtime, PartitionTable table) throws Exception;
     * List<Run> buildAlterRun(DataRuntime runtime, PartitionTable table) throws Exception;
     * List<Run> buildDropRun(DataRuntime runtime, PartitionTable table) throws Exception;
     * List<Run> buildRenameRun(DataRuntime runtime, PartitionTable table) throws Exception;
     * [命令合成-子流程]
     * List<Run> buildChangeCommentRun(DataRuntime runtime, PartitionTable table) throws Exception;
     *
     ******************************************************************************************************************/

    /**
     * partition table[调用入口]<br/>
     * 创建分区表,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */
    @Override
    public boolean create(DataRuntime runtime, PartitionTable meta) throws Exception {
        return super.create(runtime, meta);
    }

    /**
     * partition table[调用入口]<br/>
     * 修改分区表,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */
    @Override
    public boolean alter(DataRuntime runtime, PartitionTable meta) throws Exception {
        return super.alter(runtime, meta);
    }

    /**
     * partition table[调用入口]<br/>
     * 删除分区表,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */

    @Override
    public boolean drop(DataRuntime runtime, PartitionTable meta) throws Exception {
        return super.drop(runtime, meta);
    }
    /**
     * partition table[调用入口]<br/>
     * 创建分区表,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param origin 原表
     * @param name 新名称
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */
    @Override
    public boolean rename(DataRuntime runtime, PartitionTable origin, String name) throws Exception {
        return super.rename(runtime, origin, name);
    }
    /**
     * partition table[命令合成]<br/>
     * 创建分区表
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildCreateRun(DataRuntime runtime, PartitionTable meta) throws Exception {
        return super.buildCreateRun(runtime, meta);
    }

    /**
     * partition table[命令合成]<br/>
     * 创建表完成后追加表备注,创建过程能添加备注的不需要实现与comment(DataRuntime runtime, StringBuilder builder, Table meta)二选一实现
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildAppendCommentRun(DataRuntime runtime, PartitionTable meta) throws Exception {
        return super.buildAppendCommentRun(runtime, meta);
    }

    /**
     * partition table[命令合成]<br/>
     * 修改分区表
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildAlterRun(DataRuntime runtime, PartitionTable meta) throws Exception {
        return super.buildAlterRun(runtime, meta);
    }

    /**
     * partition table[命令合成-]<br/>
     * 删除分区表
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildDropRun(DataRuntime runtime, PartitionTable meta) throws Exception {
        return super.buildDropRun(runtime, meta);
    }
    /**
     * partition table[命令合成]<br/>
     * 分区表重命名
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 分区表
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildRenameRun(DataRuntime runtime, PartitionTable meta) throws Exception {
        return super.buildRenameRun(runtime, meta);
    }

    /**
     * partition table[命令合成-子流程]<br/>
     * 修改分区表备注
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 表
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildChangeCommentRun(DataRuntime runtime, PartitionTable meta) throws Exception {
        return super.buildChangeCommentRun(runtime, meta);
    }

    /* *****************************************************************************************************************
     * 													column
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * boolean add(DataRuntime runtime, Column meta)
     * boolean alter(DataRuntime runtime, Table table, Column meta, boolean trigger)
     * boolean alter(DataRuntime runtime, Column meta)
     * boolean drop(DataRuntime runtime, Column meta)
     * boolean rename(DataRuntime runtime, Column origin, String name)
     * [命令合成]
     * List<Run> buildAddRun(DataRuntime runtime, Column column, boolean slice)
     * List<Run> buildAddRun(DataRuntime runtime, Column column)
     * List<Run> buildAlterRun(DataRuntime runtime, Column column, boolean slice)
     * List<Run> buildAlterRun(DataRuntime runtime, Column column)
     * List<Run> buildDropRun(DataRuntime runtime, Column column, boolean slice)
     * List<Run> buildDropRun(DataRuntime runtime, Column column)
     * List<Run> buildRenameRun(DataRuntime runtime, Column column)
     * [命令合成-子流程]
     * List<Run> buildChangeTypeRun(DataRuntime runtime, Column column)
     * String alterColumnKeyword(DataRuntime runtime)
     * StringBuilder addColumnGuide(DataRuntime runtime, StringBuilder builder, Column column)
     * StringBuilder dropColumnGuide(DataRuntime runtime, StringBuilder builder, Column column)
     * List<Run> buildChangeDefaultRun(DataRuntime runtime, Column column)
     * List<Run> buildChangeNullableRun(DataRuntime runtime, Column column)
     * List<Run> buildChangeCommentRun(DataRuntime runtime, Column column)
     * List<Run> buildAppendCommentRun(DataRuntime runtime, Column column)
     * List<Run> buildDropAutoIncrement(DataRuntime runtime, Column column)
     * StringBuilder define(DataRuntime runtime, StringBuilder builder, Column meta, ACTION.DDL action)
     * StringBuilder type(DataRuntime runtime, StringBuilder builder, Column column)
     * StringBuilder type(DataRuntime runtime, StringBuilder builder, Column column, String type, int ignorePrecision, boolean ignoreScale)
     * int ignorePrecision(DataRuntime runtime, Column column)
     * int ignoreScale(DataRuntime runtime, Column column)
     * Boolean checkIgnorePrecision(DataRuntime runtime, String datatype)
     * int checkIgnoreScale(DataRuntime runtime, String datatype)
     * StringBuilder nullable(DataRuntime runtime, StringBuilder builder, Column meta, ACTION.DDL action)
     * StringBuilder charset(DataRuntime runtime, StringBuilder builder, Column column)
     * StringBuilder defaultValue(DataRuntime runtime, StringBuilder builder, Column column)
     * StringBuilder primary(DataRuntime runtime, StringBuilder builder, Column column)
     * StringBuilder increment(DataRuntime runtime, StringBuilder builder, Column column)
     * StringBuilder onupdate(DataRuntime runtime, StringBuilder builder, Column column)
     * StringBuilder position(DataRuntime runtime, StringBuilder builder, Column column)
     * StringBuilder comment(DataRuntime runtime, StringBuilder builder, Column column)
     * StringBuilder checkColumnExists(DataRuntime runtime, StringBuilder builder, boolean exists)
     ******************************************************************************************************************/

    /**
     * column[调用入口]<br/>
     * 添加列,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 列
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */
    @Override
    public boolean add(DataRuntime runtime, Column meta) throws Exception {
        return super.add(runtime, meta);
    }

    /**
     * column[调用入口]<br/>
     * 修改列,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 列
     * @param trigger 修改异常时，是否触发监听器
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */
    @Override
    public boolean alter(DataRuntime runtime, Table table, Column meta, boolean trigger) throws Exception {
        return super.alter(runtime, table, meta, trigger);
    }

    /**
     * column[调用入口]<br/>
     * 修改列,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 列
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */
    @Override
    public boolean alter(DataRuntime runtime, Column meta) throws Exception {
        return super.alter(runtime, meta);
    }

    /**
     * column[调用入口]<br/>
     * 删除列,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 列
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */
    @Override
    public boolean drop(DataRuntime runtime, Column meta) throws Exception {
        return super.drop(runtime, meta);
    }

    /**
     * column[调用入口]<br/>
     * 重命名列,执行的命令通过meta.ddls()返回
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param origin 列
     * @param name 新名称
     * @return boolean 是否执行成功
     * @throws Exception DDL异常
     */
    @Override
    public boolean rename(DataRuntime runtime, Column origin, String name) throws Exception {
        return super.rename(runtime, origin, name);
    }

    /**
     * column[命令合成]<br/>
     * 添加列
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 列
     * @param slice 是否只生成片段(不含alter table部分，用于DDL合并)
     * @return String
     */
    @Override
    public List<Run> buildAddRun(DataRuntime runtime, Column meta, boolean slice) throws Exception {
        return super.buildAddRun(runtime, meta, slice);
    }
    /**
     * column[命令合成]<br/>
     * 修改列
     * 有可能生成多条SQL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 列
     * @param slice 是否只生成片段(不含alter table部分，用于DDL合并)
     * @return List
     */
    @Override
    public List<Run> buildAlterRun(DataRuntime runtime, Column meta, boolean slice) throws Exception {
        return super.buildAlterRun(runtime, meta, slice);
    }
   

    /**
     * column[命令合成]<br/>
     * 删除列
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 列
     * @param slice 是否只生成片段(不含alter table部分，用于DDL合并)
     * @return String
     */
    @Override
    public List<Run> buildDropRun(DataRuntime runtime, Column meta, boolean slice) throws Exception {
        return super.buildDropRun(runtime, meta, slice);
    }

    /**
     * column[命令合成]<br/>
     * 修改列名
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 列
     * @return String
     */
    @Override
    public List<Run> buildRenameRun(DataRuntime runtime, Column meta, boolean slice) throws Exception {
        return super.buildRenameRun(runtime, meta, slice);
    }

    /**
     * column[命令合成-子流程]<br/>
     * 修改数据类型
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 列
     * @return String
     */
    @Override
    public List<Run> buildChangeTypeRun(DataRuntime runtime, Column meta, boolean slice) throws Exception {
        return super.buildChangeTypeRun(runtime, meta, slice);
    }

    /**
     * column[命令合成-子流程]<br/>
     * 修改表的关键字
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @return String
     */
    @Override
    public String alterColumnKeyword(DataRuntime runtime) {
        return super.alterColumnKeyword(runtime);
    }

    /**
     * column[命令合成-子流程]<br/>
     * 添加列引导
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder StringBuilder
     * @param meta 列
     * @return String
     */
    @Override
    public StringBuilder addColumnGuide(DataRuntime runtime, StringBuilder builder, Column meta) {
        return super.addColumnGuide(runtime, builder, meta);
    }

    /**
     * column[命令合成-子流程]<br/>
     * 删除列引导
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder StringBuilder
     * @param meta 列
     * @return String
     */
    @Override
    public StringBuilder dropColumnGuide(DataRuntime runtime, StringBuilder builder, Column meta) {
        return super.dropColumnGuide(runtime, builder, meta);
    }

    /**
     * column[命令合成-子流程]<br/>
     * 修改默认值
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 列
     * @return String
     */
    @Override
    public List<Run> buildChangeDefaultRun(DataRuntime runtime, Column meta, boolean slice) throws Exception {
        return super.buildChangeDefaultRun(runtime, meta, slice);
    }

    /**
     * column[命令合成-子流程]<br/>
     * 修改非空限制
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 列
     * @return String
     */
    @Override
    public List<Run> buildChangeNullableRun(DataRuntime runtime, Column meta, boolean slice) throws Exception {
        return super.buildChangeNullableRun(runtime, meta, slice);
    }

    /**
     * column[命令合成-子流程]<br/>
     * 修改备注
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 列
     * @return String
     */
    @Override
    public List<Run> buildChangeCommentRun(DataRuntime runtime, Column meta, boolean slice) throws Exception {
        return super.buildChangeCommentRun(runtime, meta, slice);
    }

    /**
     * column[命令合成-子流程]<br/>
     * 创建表完成后追加表备注,创建过程能添加备注的不需要实现与comment(DataRuntime runtime, StringBuilder builder, Table meta)二选一实现
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param column 列
     * @return sql
     * @throws Exception 异常
     */
    /**
     * 创建表完成后追加表备注,创建过程能添加备注的不需要实现与comment(DataRuntime runtime, StringBuilder builder, Table meta)二选一实现
     * @param meta 列
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildAppendCommentRun(DataRuntime runtime, Column meta, boolean slice) throws Exception {
        return super.buildAppendCommentRun(runtime, meta, slice);
    }

    /**
     * column[命令合成-子流程]<br/>
     * 取消自增
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 列
     * @return sql
     * @throws Exception 异常
     */
    @Override
    public List<Run> buildDropAutoIncrement(DataRuntime runtime, Column meta, boolean slice) throws Exception {
        return super.buildDropAutoIncrement(runtime, meta, slice);
    }

    /**
     * column[命令合成-子流程]<br/>
     * 定义列，依次拼接下面几个属性注意不同数据库可能顺序不一样
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param meta 列
     * @return StringBuilder
     */
    @Override
    public StringBuilder define(DataRuntime runtime, StringBuilder builder, Column meta, ACTION.DDL action) {
        return super.define(runtime, builder, meta, action);
    }

    /**
     * column[命令合成-子流程]<br/>
     * 列定义:创建或删除列之前  检测表是否存在
     * IF NOT EXISTS
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param exists exists
     * @return StringBuilder
     */
    @Override
    public StringBuilder checkColumnExists(DataRuntime runtime, StringBuilder builder, boolean exists) {
        return super.checkColumnExists(runtime, builder, exists);
    }
    /**
     * column[命令合成-子流程]<br/>
     * 列定义:数据类型
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param meta 列
     * @return StringBuilder
     */
    @Override
    public StringBuilder type(DataRuntime runtime, StringBuilder builder, Column meta) {
        return super.type(runtime, builder, meta);
    }
    /**
     * column[命令合成-子流程]<br/>
     * 列定义:数据类型定义
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param meta 列
     * @param type 数据类型(已经过转换)
     * @param ignorePrecision 是否忽略长度
     * @param ignoreScale 是否忽略小数
     * @return StringBuilder
     */
    @Override
    public StringBuilder type(DataRuntime runtime, StringBuilder builder, Column meta, String type, int ignoreLength, int ignorePrecision, int ignoreScale) {
        return super.type(runtime, builder, meta, type, ignoreLength, ignorePrecision, ignoreScale);
    }

    /**
     * column[命令合成-子流程]<br/>
     * 列定义:非空
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param meta 列
     * @return StringBuilder
     */
    @Override
    public StringBuilder nullable(DataRuntime runtime, StringBuilder builder, Column meta, ACTION.DDL action) {
        return super.nullable(runtime, builder, meta, action);
    }
    /**
     * column[命令合成-子流程]<br/>
     * 列定义:编码
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param meta 列
     * @return StringBuilder
     */
    @Override
    public StringBuilder charset(DataRuntime runtime, StringBuilder builder, Column meta) {
        return super.charset(runtime, builder, meta);
    }

    /**
     * column[命令合成-子流程]<br/>
     * 列定义:默认值
     * @param builder builder
     * @param meta 列
     * @return StringBuilder
     */
    @Override
    public StringBuilder defaultValue(DataRuntime runtime, StringBuilder builder, Column meta) {
        return super.defaultValue(runtime, builder, meta);
    }

    /**
     * column[命令合成-子流程]<br/>
     * 列定义:定义列的主键标识(注意不要跟表定义中的主键重复)
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param meta 列
     * @return StringBuilder
     */
    @Override
    public StringBuilder primary(DataRuntime runtime, StringBuilder builder, Column meta) {
        return super.primary(runtime, builder, meta);
    }

    /**
     * column[命令合成-子流程]<br/>
     * 列定义:递增列,需要通过serial实现递增的在type(DataRuntime runtime, StringBuilder builder, Column meta)中实现
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param meta 列
     * @return StringBuilder
     */
    @Override
    public StringBuilder increment(DataRuntime runtime, StringBuilder builder, Column meta) {
        return super.increment(runtime, builder, meta);
    }
    /**
     * column[命令合成-子流程]<br/>
     * 列定义:更新行事件
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param meta 列
     * @return StringBuilder
     */
    @Override
    public StringBuilder onupdate(DataRuntime runtime, StringBuilder builder, Column meta) {
        return super.onupdate(runtime, builder, meta);
    }

    /**
     * column[命令合成-子流程]<br/>
     * 列定义:位置
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param meta 列
     * @return StringBuilder
     */
    @Override
    public StringBuilder position(DataRuntime runtime, StringBuilder builder, Column meta) {
        return super.position(runtime, builder, meta);
    }

    /**
     * column[命令合成-子流程]<br/>
     * 列定义:备注
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param meta 列
     * @return StringBuilder
     */
    @Override
    public StringBuilder comment(DataRuntime runtime, StringBuilder builder, Column meta) {
        return super.comment(runtime, builder, meta);
    }

    /* *****************************************************************************************************************
     * 													tag
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * boolean add(DataRuntime runtime, Tag meta)
     * boolean alter(DataRuntime runtime, Table table, Tag meta, boolean trigger)
     * boolean alter(DataRuntime runtime, Tag meta)
     * boolean drop(DataRuntime runtime, Tag meta)
     * boolean rename(DataRuntime runtime, Tag origin, String name)
     * [命令合成]
     * List<Run> buildAddRun(DataRuntime runtime, Tag meta)
     * List<Run> buildAlterRun(DataRuntime runtime, Tag meta)
     * List<Run> buildDropRun(DataRuntime runtime, Tag meta, boolean slice)
     * List<Run> buildRenameRun(DataRuntime runtime, Tag meta, boolean slice)
     * List<Run> buildChangeDefaultRun(DataRuntime runtime, Tag meta)
     * List<Run> buildChangeNullableRun(DataRuntime runtime, Tag meta)
     * List<Run> buildChangeCommentRun(DataRuntime runtime, Tag meta)
     * List<Run> buildChangeTypeRun(DataRuntime runtime, Tag meta)
     * StringBuilder checkTagExists(DataRuntime runtime, StringBuilder builder, boolean exists)
     ******************************************************************************************************************/

    /**
     * tag[调用入口]<br/>
     * 添加标签
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 标签
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean add(DataRuntime runtime, Tag meta) throws Exception {
        return super.add(runtime, meta);
    }

    /**
     * tag[调用入口]<br/>
     * 修改标签
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 标签
     * @param trigger 修改异常时，是否触发监听器
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean alter(DataRuntime runtime, Table table, Tag meta, boolean trigger) throws Exception {
        return super.alter(runtime, table, meta, trigger);
    }

    /**
     * tag[调用入口]<br/>
     * 修改标签
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 标签
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean alter(DataRuntime runtime, Tag meta) throws Exception {
        return super.alter(runtime, meta);
    }

    /**
     * tag[调用入口]<br/>
     * 删除标签
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 标签
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean drop(DataRuntime runtime, Tag meta) throws Exception {
        return super.drop(runtime, meta);
    }

    /**
     * tag[调用入口]<br/>
     * 重命名标签
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param origin 原标签
     * @param name 新名称
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean rename(DataRuntime runtime, Tag origin, String name) throws Exception {
        return super.rename(runtime, origin, name);
    }

    /**
     * tag[命令合成]<br/>
     * 添加标签
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 标签
     * @return String
     */
    @Override
    public List<Run> buildAddRun(DataRuntime runtime, Tag meta, boolean slice) throws Exception {
        return super.buildAddRun(runtime, meta, slice);
    }
    /**
     * tag[命令合成]<br/>
     * 修改标签
     * 有可能生成多条SQL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 标签
     * @return List
     */
    @Override
    public List<Run> buildAlterRun(DataRuntime runtime, Tag meta, boolean slice) throws Exception {
        return super.buildAlterRun(runtime, meta, slice);
    }

    /**
     * tag[命令合成]<br/>
     * 删除标签
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 标签
     * @return String
     */
    @Override
    public List<Run> buildDropRun(DataRuntime runtime, Tag meta, boolean slice) throws Exception {
        return super.buildDropRun(runtime, meta, slice);
    }

    /**
     * tag[命令合成]<br/>
     * 修改标签名
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 标签
     * @return String
     */
    @Override
    public List<Run> buildRenameRun(DataRuntime runtime, Tag meta, boolean slice) throws Exception {
        return super.buildRenameRun(runtime, meta, slice);
    }
    /**
     * tag[命令合成]<br/>
     * 修改默认值
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 标签
     * @return String
     */
    @Override
    public List<Run> buildChangeDefaultRun(DataRuntime runtime, Tag meta, boolean slice) throws Exception {
        return super.buildChangeDefaultRun(runtime, meta, slice);
    }

    /**
     * tag[命令合成]<br/>
     * 修改非空限制
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 标签
     * @return String
     */
    @Override
    public List<Run> buildChangeNullableRun(DataRuntime runtime, Tag meta, boolean slice) throws Exception {
        return super.buildChangeNullableRun(runtime, meta, slice);
    }

    /**
     * tag[命令合成]<br/>
     * 修改备注
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 标签
     * @return String
     */
    @Override
    public List<Run> buildChangeCommentRun(DataRuntime runtime, Tag meta, boolean slice) throws Exception {
        return super.buildChangeCommentRun(runtime, meta, slice);
    }

    /**
     * tag[命令合成]<br/>
     * 修改数据类型
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 标签
     * @return String
     */
    @Override
    public List<Run> buildChangeTypeRun(DataRuntime runtime, Tag meta, boolean slice) throws Exception {
        return super.buildChangeTypeRun(runtime, meta, slice);
    }

    /**
     * tag[命令合成]<br/>
     * 创建或删除标签之前  检测表是否存在
     * IF NOT EXISTS
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param exists exists
     * @return StringBuilder
     */
    @Override
    public StringBuilder checkTagExists(DataRuntime runtime, StringBuilder builder, boolean exists) {
        return super.checkTagExists(runtime, builder, exists);
    }

    /* *****************************************************************************************************************
     * 													primary
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * boolean add(DataRuntime runtime, PrimaryKey meta)
     * boolean alter(DataRuntime runtime, PrimaryKey meta)
     * boolean alter(DataRuntime runtime, Table table, PrimaryKey meta)
     * boolean drop(DataRuntime runtime, PrimaryKey meta)
     * boolean rename(DataRuntime runtime, PrimaryKey origin, String name)
     * [命令合成]
     * List<Run> buildAddRun(DataRuntime runtime, PrimaryKey primary)
     * List<Run> buildAlterRun(DataRuntime runtime, PrimaryKey primary)
     * List<Run> buildDropRun(DataRuntime runtime, PrimaryKey primary)
     * List<Run> buildRenameRun(DataRuntime runtime, PrimaryKey primary)
     ******************************************************************************************************************/

    /**
     * primary[调用入口]<br/>
     * 添加主键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 主键
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean add(DataRuntime runtime, PrimaryKey meta) throws Exception {
        return super.add(runtime, meta);
    }

    /**
     * primary[调用入口]<br/>
     * 修改主键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 主键
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean alter(DataRuntime runtime, PrimaryKey meta) throws Exception {
        return super.alter(runtime, meta);
    }

    /**
     * primary[调用入口]<br/>
     * 修改主键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 主键
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean alter(DataRuntime runtime, Table table, PrimaryKey meta) throws Exception {
        return super.alter(runtime, table, meta);
    }

    /**
     * primary[调用入口]<br/>
     * 删除主键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 主键
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean drop(DataRuntime runtime, PrimaryKey meta) throws Exception {
        return super.drop(runtime, meta);
    }

    /**
     * primary[调用入口]<br/>
     * 添加主键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param origin 主键
     * @param name 新名称
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean rename(DataRuntime runtime, PrimaryKey origin, String name) throws Exception {
        return super.rename(runtime, origin, name);
    }
    /**
     * primary[命令合成]<br/>
     * 添加主键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 主键
     * @param slice 是否只生成片段(不含alter table部分，用于DDL合并)
     * @return String
     */
    @Override
    public List<Run> buildAddRun(DataRuntime runtime, PrimaryKey meta, boolean slice) throws Exception {
        return super.buildAddRun(runtime, meta, slice);
    }

    /**
     * primary[命令合成]<br/>
     * 修改主键
     * 有可能生成多条SQL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param origin 原主键
     * @param meta 新主键
     * @return List
     */
    @Override
    public List<Run> buildAlterRun(DataRuntime runtime, PrimaryKey origin, PrimaryKey meta, boolean slice) throws Exception {
        return super.buildAlterRun(runtime, origin, meta, slice);
    }
    /**
     * primary[命令合成]<br/>
     * 删除主键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 主键
     * @param slice 是否只生成片段(不含alter table部分，用于DDL合并)
     * @return String
     */
    @Override
    public List<Run> buildDropRun(DataRuntime runtime, PrimaryKey meta, boolean slice) throws Exception {
        return super.buildDropRun(runtime, meta);
    }
    /**
     * primary[命令合成]<br/>
     * 修改主键名
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 主键
     * @return String
     */
    @Override
    public List<Run> buildRenameRun(DataRuntime runtime, PrimaryKey meta) throws Exception {
        return super.buildRenameRun(runtime, meta);
    }

    /* *****************************************************************************************************************
     * 													foreign
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * boolean add(DataRuntime runtime, ForeignKey meta)
     * boolean alter(DataRuntime runtime, ForeignKey meta)
     * boolean alter(DataRuntime runtime, Table table, ForeignKey meta)
     * boolean drop(DataRuntime runtime, ForeignKey meta)
     * boolean rename(DataRuntime runtime, ForeignKey origin, String name)
     * [命令合成]
     * List<Run> buildAddRun(DataRuntime runtime, ForeignKey meta)
     * List<Run> buildAlterRun(DataRuntime runtime, ForeignKey meta)
     * List<Run> buildDropRun(DataRuntime runtime, ForeignKey meta)
     * List<Run> buildRenameRun(DataRuntime runtime, ForeignKey meta)
     ******************************************************************************************************************/

    /**
     * foreign[调用入口]<br/>
     * 添加外键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 外键
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean add(DataRuntime runtime, ForeignKey meta) throws Exception {
        return super.add(runtime, meta);
    }

    /**
     * foreign[调用入口]<br/>
     * 修改外键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 外键
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean alter(DataRuntime runtime, ForeignKey meta) throws Exception {
        return super.alter(runtime, meta);
    }

    /**
     * foreign[调用入口]<br/>
     * 修改外键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 外键
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean alter(DataRuntime runtime, Table table, ForeignKey meta) throws Exception {
        return super.alter(runtime, table, meta);
    }

    /**
     * foreign[调用入口]<br/>
     * 删除外键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 外键
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean drop(DataRuntime runtime, ForeignKey meta) throws Exception {
        return super.drop(runtime, meta);
    }

    /**
     * foreign[调用入口]<br/>
     * 重命名外键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param origin 外键
     * @param name 新名称
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean rename(DataRuntime runtime, ForeignKey origin, String name) throws Exception {
        return super.rename(runtime, origin, name);
    }

    /**
     * foreign[命令合成]<br/>
     * 添加外键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 外键
     * @return String
     */
    @Override
    public List<Run> buildAddRun(DataRuntime runtime, ForeignKey meta) throws Exception {
        return super.buildAddRun(runtime, meta);
    }
    /**
     * foreign[命令合成]<br/>
     * 修改外键
     * @param meta 外键
     * @return List
     */

    /**
     * 添加外键
     * @param meta 外键
     * @return List
     */
    @Override
    public List<Run> buildAlterRun(DataRuntime runtime, ForeignKey meta) throws Exception {
        return super.buildAlterRun(runtime, meta);
    }

    /**
     * foreign[命令合成]<br/>
     * 删除外键
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 外键
     * @return String
     */
    @Override
    public List<Run> buildDropRun(DataRuntime runtime, ForeignKey meta) throws Exception {
        return super.buildDropRun(runtime, meta);
    }

    /**
     * foreign[命令合成]<br/>
     * 修改外键名
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 外键
     * @return String
     */
    @Override
    public List<Run> buildRenameRun(DataRuntime runtime, ForeignKey meta) throws Exception {
        return super.buildRenameRun(runtime, meta);
    }
    /* *****************************************************************************************************************
     * 													index
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * boolean add(DataRuntime runtime, Index meta)
     * boolean alter(DataRuntime runtime, Index meta)
     * boolean alter(DataRuntime runtime, Table table, Index meta)
     * boolean drop(DataRuntime runtime, Index meta)
     * boolean rename(DataRuntime runtime, Index origin, String name)
     * [命令合成]
     * List<Run> buildAppendIndexRun(DataRuntime runtime, Table meta)
     * List<Run> buildAlterRun(DataRuntime runtime, Index meta)
     * List<Run> buildDropRun(DataRuntime runtime, Index meta)
     * List<Run> buildRenameRun(DataRuntime runtime, Index meta)
     * [命令合成-子流程]
     * StringBuilder type(DataRuntime runtime, StringBuilder builder, Index meta)
     * StringBuilder comment(DataRuntime runtime, StringBuilder builder, Index meta)
     ******************************************************************************************************************/

    /**
     * index[调用入口]<br/>
     * 添加索引
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 索引
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean add(DataRuntime runtime, Index meta) throws Exception {
        return super.add(runtime, meta);
    }

    /**
     * index[调用入口]<br/>
     * 修改索引
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 索引
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean alter(DataRuntime runtime, Index meta) throws Exception {
        return super.alter(runtime, meta);
    }

    /**
     * index[调用入口]<br/>
     * 修改索引
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 索引
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean alter(DataRuntime runtime, Table table, Index meta) throws Exception {
        return super.alter(runtime, table, meta);
    }

    /**
     * index[调用入口]<br/>
     * 删除索引
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 索引
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean drop(DataRuntime runtime, Index meta) throws Exception {
        return super.drop(runtime, meta);
    }

    /**
     * index[调用入口]<br/>
     * 重命名索引
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param origin 索引
     * @param name 新名称
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean rename(DataRuntime runtime, Index origin, String name) throws Exception {
        return super.rename(runtime, origin, name);
    }

    /**
     * index[命令合成]<br/>
     * 添加索引
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 索引
     * @return String
     */
    @Override
    public List<Run> buildAppendIndexRun(DataRuntime runtime, Table meta) throws Exception {
        return super.buildAppendIndexRun(runtime, meta);
    }
    /**
     * index[命令合成]<br/>
     * 修改索引
     * 有可能生成多条SQL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 索引
     * @return List
     */
    @Override
    public List<Run> buildAlterRun(DataRuntime runtime, Index meta) throws Exception {
        return super.buildAlterRun(runtime, meta);
    }
    /**
     * index[命令合成]<br/>
     * 删除索引
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 索引
     * @return String
     */
    @Override
    public List<Run> buildDropRun(DataRuntime runtime, Index meta) throws Exception {
        return super.buildDropRun(runtime, meta);
    }
    /**
     * index[命令合成]<br/>
     * 修改索引名
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 索引
     * @return String
     */
    @Override
    public List<Run> buildRenameRun(DataRuntime runtime, Index meta) throws Exception {
        return super.buildRenameRun(runtime, meta);
    }

    /**
     * index[命令合成-子流程]<br/>
     * 索引类型
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 索引
     * @param builder builder
     * @return StringBuilder
     */
    @Override
    public StringBuilder type(DataRuntime runtime, StringBuilder builder, Index meta) {
        return super.type(runtime, builder, meta);
    }
    /**
     * index[命令合成-子流程]<br/>
     * 索引备注
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 索引
     * @param builder builder
     * @return StringBuilder
     */
    @Override
    public StringBuilder comment(DataRuntime runtime, StringBuilder builder, Index meta) {
        return super.comment(runtime, builder, meta);
    }
    /* *****************************************************************************************************************
     * 													constraint
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * boolean add(DataRuntime runtime, Constraint meta)
     * boolean alter(DataRuntime runtime, Constraint meta)
     * boolean alter(DataRuntime runtime, Table table, Constraint meta)
     * boolean drop(DataRuntime runtime, Constraint meta)
     * boolean rename(DataRuntime runtime, Constraint origin, String name)
     * [命令合成]
     * List<Run> buildAddRun(DataRuntime runtime, Constraint meta)
     * List<Run> buildAlterRun(DataRuntime runtime, Constraint meta)
     * List<Run> buildDropRun(DataRuntime runtime, Constraint meta)
     * List<Run> buildRenameRun(DataRuntime runtime, Constraint meta)
     ******************************************************************************************************************/

    /**
     * constraint[调用入口]<br/>
     * 添加约束
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 约束
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean add(DataRuntime runtime, Constraint meta) throws Exception {
        return super.add(runtime, meta);
    }

    /**
     * constraint[调用入口]<br/>
     * 修改约束
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 约束
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean alter(DataRuntime runtime, Constraint meta) throws Exception {
        return super.alter(runtime, meta);
    }

    /**
     * constraint[调用入口]<br/>
     * 修改约束
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 约束
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean alter(DataRuntime runtime, Table table, Constraint meta) throws Exception {
        return super.alter(runtime, table, meta);
    }

    /**
     * constraint[调用入口]<br/>
     * 删除约束
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 约束
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean drop(DataRuntime runtime, Constraint meta) throws Exception {
        return super.drop(runtime, meta);
    }

    /**
     * constraint[调用入口]<br/>
     * 重命名约束
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param origin 约束
     * @param name 新名称
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean rename(DataRuntime runtime, Constraint origin, String name) throws Exception {
        return super.rename(runtime, origin, name);
    }

    /**
     * constraint[命令合成]<br/>
     * 添加约束
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 约束
     * @return String
     */
    @Override
    public List<Run> buildAddRun(DataRuntime runtime, Constraint meta) throws Exception {
        return super.buildAddRun(runtime, meta);
    }

    /**
     * constraint[命令合成]<br/>
     * 修改约束
     * 有可能生成多条SQL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 约束
     * @return List
     */
    @Override
    public List<Run> buildAlterRun(DataRuntime runtime, Constraint meta) throws Exception {
        return super.buildAlterRun(runtime, meta);
    }
    /**
     * constraint[命令合成]<br/>
     * 删除约束
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 约束
     * @return String
     */
    @Override
    public List<Run> buildDropRun(DataRuntime runtime, Constraint meta) throws Exception {
        return super.buildDropRun(runtime, meta);
    }

    /**
     * constraint[命令合成]<br/>
     * 修改约束名
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 约束
     * @return String
     */
    @Override
    public List<Run> buildRenameRun(DataRuntime runtime, Constraint meta) throws Exception {
        return super.buildRenameRun(runtime, meta);
    }

    /* *****************************************************************************************************************
     * 													trigger
     * -----------------------------------------------------------------------------------------------------------------
     * List<Run> buildCreateRun(DataRuntime runtime, Trigger trigger) throws Exception
     * List<Run> buildAlterRun(DataRuntime runtime, Trigger trigger) throws Exception;
     * List<Run> buildDropRun(DataRuntime runtime, Trigger trigger) throws Exception;
     * List<Run> buildRenameRun(DataRuntime runtime, Trigger trigger) throws Exception;
     ******************************************************************************************************************/

    /**
     * trigger[调用入口]<br/>
     * 添加触发器
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 触发器
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean add(DataRuntime runtime, Trigger meta) throws Exception {
        return super.add(runtime, meta);
    }

    /**
     * trigger[调用入口]<br/>
     * 修改触发器
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 触发器
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean alter(DataRuntime runtime, Trigger meta) throws Exception {
        return super.alter(runtime, meta);
    }

    /**
     * trigger[调用入口]<br/>
     * 删除触发器
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 触发器
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean drop(DataRuntime runtime, Trigger meta) throws Exception {
        return super.drop(runtime, meta);
    }

    /**
     * trigger[调用入口]<br/>
     * 重命名触发器
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param origin 触发器
     * @param name 新名称
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean rename(DataRuntime runtime, Trigger origin, String name) throws Exception {
        return super.rename(runtime, origin, name);
    }

    /**
     * trigger[命令合成]<br/>
     * 添加触发器
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 触发器
     * @return List
     */
    @Override
    public List<Run> buildCreateRun(DataRuntime runtime, Trigger meta) throws Exception {
        return super.buildCreateRun(runtime, meta);
    }
    /**
     * trigger[命令合成]<br/>
     * 修改触发器
     * 有可能生成多条SQL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 触发器
     * @return List
     */
    @Override
    public List<Run> buildAlterRun(DataRuntime runtime, Trigger meta) throws Exception {
        return super.buildAlterRun(runtime, meta);
    }

    /**
     * trigger[命令合成]<br/>
     * 删除触发器
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 触发器
     * @return List
     */
    @Override
    public List<Run> buildDropRun(DataRuntime runtime, Trigger meta) throws Exception {
        return super.buildDropRun(runtime, meta);
    }

    /**
     * trigger[命令合成]<br/>
     * 修改触发器名
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 触发器
     * @return List
     */
    @Override
    public List<Run> buildRenameRun(DataRuntime runtime, Trigger meta) throws Exception {
        return super.buildRenameRun(runtime, meta);
    }
    /**
     * trigger[命令合成-子流程]<br/>
     * 触发级别(行或整个命令)
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 触发器
     * @param builder builder
     * @return StringBuilder
     */
    @Override
    public StringBuilder each(DataRuntime runtime, StringBuilder builder, Trigger meta) {
        return super.each(runtime, builder, meta);
    }

    /* *****************************************************************************************************************
     * 													procedure
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * boolean create(DataRuntime runtime, Procedure meta)
     * boolean alter(DataRuntime runtime, Procedure meta)
     * boolean drop(DataRuntime runtime, Procedure meta)
     * boolean rename(DataRuntime runtime, Procedure origin, String name)
     * [命令合成]
     * List<Run> buildCreateRun(DataRuntime runtime, Procedure meta)
     * List<Run> buildAlterRun(DataRuntime runtime, Procedure meta)
     * List<Run> buildDropRun(DataRuntime runtime, Procedure meta)
     * List<Run> buildRenameRun(DataRuntime runtime, Procedure meta)
     * [命令合成-子流程]
     * StringBuilder parameter(DataRuntime runtime, StringBuilder builder, Parameter parameter)
     ******************************************************************************************************************/

    /**
     * procedure[调用入口]<br/>
     * 添加存储过程
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 存储过程
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean create(DataRuntime runtime, Procedure meta) throws Exception {
        return super.create(runtime, meta);
    }

    /**
     * procedure[调用入口]<br/>
     * 修改存储过程
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 存储过程
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean alter(DataRuntime runtime, Procedure meta) throws Exception {
        return super.alter(runtime, meta);
    }

    /**
     * procedure[调用入口]<br/>
     * 删除存储过程
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 存储过程
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean drop(DataRuntime runtime, Procedure meta) throws Exception {
        return super.drop(runtime, meta);
    }

    /**
     * procedure[调用入口]<br/>
     * 重命名存储过程
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param origin 存储过程
     * @param name 新名称
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean rename(DataRuntime runtime, Procedure origin, String name) throws Exception {
        return super.rename(runtime, origin, name);
    }

    /**
     * procedure[命令合成]<br/>
     * 添加存储过程
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 存储过程
     * @return String
     */
    @Override
    public List<Run> buildCreateRun(DataRuntime runtime, Procedure meta) throws Exception {
        return super.buildCreateRun(runtime, meta);
    }
    /**
     * procedure[命令合成]<br/>
     * 修改存储过程
     * 有可能生成多条SQL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 存储过程
     * @return List
     */
    @Override
    public List<Run> buildAlterRun(DataRuntime runtime, Procedure meta) throws Exception {
        return super.buildAlterRun(runtime, meta);
    }

    /**
     * procedure[命令合成]<br/>
     * 删除存储过程
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 存储过程
     * @return String
     */
    @Override
    public List<Run> buildDropRun(DataRuntime runtime, Procedure meta) throws Exception {
        return super.buildDropRun(runtime, meta);
    }

    /**
     * procedure[命令合成]<br/>
     * 修改存储过程名<br/>
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 存储过程
     * @return String
     */
    @Override
    public List<Run> buildRenameRun(DataRuntime runtime, Procedure meta) throws Exception {
        return super.buildRenameRun(runtime, meta);
    }

    /**
     * procedure[命令合成-子流程]<br/>
     * 生在输入输出参数
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param builder builder
     * @param parameter parameter
     */
    @Override
    public StringBuilder parameter(DataRuntime runtime, StringBuilder builder, Parameter parameter) {
        return super.parameter(runtime, builder, parameter);
    }

    /* *****************************************************************************************************************
     * 													function
     * -----------------------------------------------------------------------------------------------------------------
     * [调用入口]
     * boolean create(DataRuntime runtime, Function meta)
     * boolean alter(DataRuntime runtime, Function meta)
     * boolean drop(DataRuntime runtime, Function meta)
     * boolean rename(DataRuntime runtime, Function origin, String name)
     * [命令合成]
     * List<Run> buildCreateRun(DataRuntime runtime, Function function)
     * List<Run> buildAlterRun(DataRuntime runtime, Function function)
     * List<Run> buildDropRun(DataRuntime runtime, Function function)
     * List<Run> buildRenameRun(DataRuntime runtime, Function function)
     ******************************************************************************************************************/

    /**
     * function[调用入口]<br/>
     * 添加函数
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 函数
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean create(DataRuntime runtime, Function meta) throws Exception {
        return super.create(runtime, meta);
    }

    /**
     * function[调用入口]<br/>
     * 修改函数
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 函数
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean alter(DataRuntime runtime, Function meta) throws Exception {
        return super.alter(runtime, meta);
    }

    /**
     * function[调用入口]<br/>
     * 删除函数
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 函数
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean drop(DataRuntime runtime, Function meta) throws Exception {
        return super.drop(runtime, meta);
    }

    /**
     * function[调用入口]<br/>
     * 重命名函数
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param origin 函数
     * @param name 新名称
     * @return 是否执行成功
     * @throws Exception 异常
     */
    @Override
    public boolean rename(DataRuntime runtime, Function origin, String name) throws Exception {
        return super.rename(runtime, origin, name);
    }

    /**
     * function[命令合成]<br/>
     * 添加函数
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 函数
     * @return String
     */
    @Override
    public List<Run> buildCreateRun(DataRuntime runtime, Function meta) throws Exception {
        return super.buildCreateRun(runtime, meta);
    }

    /**
     * function[命令合成]<br/>
     * 修改函数
     * 有可能生成多条SQL
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 函数
     * @return List
     */
    @Override
    public List<Run> buildAlterRun(DataRuntime runtime, Function meta) throws Exception {
        return super.buildAlterRun(runtime, meta);
    }

    /**
     * function[命令合成]<br/>
     * 删除函数
     * @param meta 函数
     * @return String
     */
    @Override
    public List<Run> buildDropRun(DataRuntime runtime, Function meta) throws Exception {
        return super.buildDropRun(runtime, meta);
    }

    /**
     * function[命令合成]<br/>
     * 修改函数名
     * 一般不直接调用,如果需要由buildAlterRun内部统一调用
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param meta 函数
     * @return String
     */
    @Override
    public List<Run> buildRenameRun(DataRuntime runtime, Function meta) throws Exception {
        return super.buildRenameRun(runtime, meta);
    }

    @Override
    public LinkedHashMap<String, Column> metadata(DataRuntime runtime, RunPrepare prepare, boolean comment) {
        return null;
    }

    @Override
    public String concat(DataRuntime runtime, String... args) {
        return null;
    }

    protected RestClient client(DataRuntime runtime) {
        Object processor = runtime.getProcessor();
        return (RestClient) processor;
    }
    /**
     *
     PUT index_user/_bulk<br/>
     {"index":{"_index":"index_user","_id":"10011"}}<br/>
     {"id":1001, "name":"a b","age":20}<br/>
     {"index":{"_index":"index_user","_id":"10012"}}<br/>
     {"id":1002, "name":"b c","age":20}<br/>
     {"index":{"_index":"index_user","_id":"10013"}}<br/>
     {"id":1003, "name":"c d","age":30}
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param table 表(索引)
     * @param list list
     * @return boolean
     */
    public boolean inserts(DataRuntime runtime, String table, Collection list) {
        boolean result = false;
        String pk = "_id";
        String method = "PUT";
        String endpoint = table+"/_bulk";
        String body = null;
        StringBuilder builder = new StringBuilder();
        for(Object entity:list) {
            Object _id = BeanUtil.getFieldValue(entity, pk);
            if (null == _id) {
                pk = "id";
                _id = BeanUtil.getFieldValue(entity, pk);
            }
            builder.append("{\"index\":{\"_index\":\"").append(table).append("\", \"_id\":\"").append(_id).append("\"}}\n");
            builder.append(BeanUtil.object2json(entity)).append("\n");
        }
        Request request = new Request(
                method,
                endpoint);
        body = BeanUtil.object2json(builder.toString());
        request.setJsonEntity(body);
        HttpResponse response = exe(runtime, request);
        if(response.getStatus() == 200 ||  response.getStatus() == 201) {
            result = true;
        }
        return result;
    }

    /**
     *PUT index_user/_bulk<br/>
     * {"index":{"_index":"index_user","_id":"10011"}}<br/>
     * {"id":1001, "name":"a b","age":20}<br/>
     * {"index":{"_index":"index_user","_id":"10012"}}<br/>
     * {"id":1002, "name":"b c","age":20}<br/>
     * {"index":{"_index":"index_user","_id":"10013"}}<br/>
     * {"id":1003, "name":"c d","age":30}<br/>
     * @param runtime 运行环境主要包含驱动适配器 数据源或客户端
     * @param table 表(索引)
     * @param set 数据集合
     * @return boolean
     */
    public boolean insert(DataRuntime runtime, String table, DataSet set) {
        boolean result = false;
        String method = "PUT";
        String endpoint = "*/_bulk";
        String body = null;
        StringBuilder builder = new StringBuilder();
        for(DataRow row:set) {
            String pk = "_id";
            Object _id = BeanUtil.getFieldValue(row, pk);
            if (null == _id) {
                pk = "id";
                _id = BeanUtil.getFieldValue(row, pk);
            }
            row.remove("_id");
            builder.append("{\"index\":{\"_index\":\"").append(table).append("\", \"_id\":\"").append(_id).append("\"}}\n");
            builder.append(row.toJSON()).append("\n");
        }
        Request request = new Request(
                method,
                endpoint);
        body = builder.toString();
        request.setJsonEntity(body);
        HttpResponse response = exe(runtime, request);
        return result;
    }
    public boolean insert(DataRuntime runtime, String table, DataRow entity) {
        boolean result = false;
        String pk = "_id";
        String method = "POST";
        String endpoint = null;
        String body = null;
        //一般需要设置用于索引的主键 如法规id = l100 问答id = q100
        Object _id = BeanUtil.getFieldValue(entity, pk);
        if (null == _id) {
            pk = "id";
            _id = BeanUtil.getFieldValue(entity, pk);
        }
        endpoint = table + "/_doc/";
        if (BasicUtil.isNotEmpty(_id)) {
            method = "PUT";
            endpoint += _id;
        }
        entity.remove("_id");
        Request request = new Request(
                method,
                endpoint);
        body = BeanUtil.object2json(entity);
        request.setJsonEntity(body);
        HttpResponse response = exe(runtime, request);
        if(BasicUtil.isEmpty(_id)) {
            DataRow row = DataRow.parse(response.getText());
            _id = row.getString(pk);
            if(BasicUtil.isNotEmpty(_id)) {
                BeanUtil.setFieldValue(entity, pk, _id);
            }
        }
        return result;
    }
    private HttpResponse exe(DataRuntime runtime, Request request) {
        HttpResponse result = new HttpResponse();
        RestClient client = client(runtime);
        try {
            Response response = client.performRequest(request);
            int status = response.getStatusLine().getStatusCode();
            result.setStatus(status);
            //{"_index":"index_user","_id":"102","_version":3,"result":"updated","_shards":{"total":2,"successful":2,"failed":0},"_seq_no":9,"_primary_term":1}
            String content = FileUtil.read(response.getEntity().getContent()).toString();
            result.setText(content);
            log.info("[status:{}]", status);
        }catch (Exception e) {
            log.error("执行异常:", e);
        }
        return result;
    }
    public DataRow get(DataRuntime runtime, String table, String id) {
        DataRow result = null;
        String method = "GET";
        String endpoint = "/"+table+"/_doc/"+id;
        Request request = new Request(
                method,
                endpoint);
        HttpResponse response = exe(runtime, request);
        if(response.getStatus() == 200) {
            String txt = response.getText();
            DataRow row = DataRow.parseJson(txt);
            result = row.getRow("_source");
        }
        return result;
    }
    public DataSet search(DataRuntime runtime, String table, DataRow body, PageNavi page) {
        DataSet set = null;
        if(null != page) {
            body.put("from", page.getFirstRow());
            body.put("size", page.getPageRows());
        }
        String method = "POST";
        String endpoint = table+"/_search";
        Request request = new Request(
                method,
                endpoint);
        String json = body.toLowerKey(true).toJSON();
        log.warn("[search][body:{}]", body);
        request.setJsonEntity(json);
        HttpResponse response = exe(runtime, request);
        if(response.getStatus() == 200) {
            String txt = response.getText();
            DataRow row = DataRow.parseJson(txt);
            Object total = row.recursion("hits","total","value");
            if(null == page) {
                page = new DefaultPageNavi();
            }
            page.setTotalRow(BasicUtil.parseInt(total,0));
            set = new DataSet();
            set.setNavi(page);
            DataSet hits = row.getRow("hits").getSet("hits");
            for(DataRow hit:hits) {
                DataRow item = hit.getRow("_source");
                item.put("_id", hit.get("_id"));
                DataRow highlight = hit.getRow("highlight");
                if(null != highlight) {
                    for(String key:highlight.keySet()) {
                        List vals = highlight.getList(key);
                        if(null != vals && vals.size()>0) {
                            item.put(key, vals.get(0));
                        }
                    }
                }
                set.add(item);
            }

        }
        return set;
    }
    public LinkedHashMap<String,DataRow> analyze(DataRuntime runtime, String key) {
        return analyze(runtime, key, null);
    }
    public LinkedHashMap<String,DataRow> analyze(DataRuntime runtime, String key, String mode) {
        LinkedHashMap<String,DataRow> maps = new LinkedHashMap<>();
        DataRow body = new DataRow(KeyAdapter.KEY_CASE.SRC);
        if(BasicUtil.isEmpty(mode)) {
            mode = "ik_smart";
        }
        body.put("analyzer", mode);
        body.put("text", new String[]{key});

        Request request = new Request(
                "GET",
                "_analyze");
        request.setJsonEntity(BeanUtil.object2json(body));
        HttpResponse response = exe(runtime, request);
        if(response.getStatus() == 200) {
            DataRow row = DataRow.parseJson(response.getText());
            DataSet tokens = row.getSet("tokens");
            for(DataRow token:tokens) {
                String k = token.getString("token");
                if(k.length() > 1) {
                    maps.put(k, token);
                }
            }
        }
        return maps;
    }

    public DataSet select(DataRuntime runtime, String random, boolean system, Table table, ConfigStore configs, ElasticSearchRun run) {
        if(null == configs) {
            configs = new DefaultConfigStore();
        }
        configs.add(run);
        if(null == random) {
            random = random(runtime);
        }
        if(log.isInfoEnabled() &&ConfigStore.IS_LOG_SQL(configs)) {
            String body = run.getFinalQuery();
            log.info("{}[action:select][method:{}][endpoint:{}][body:{}]", random, run.getMethod(), run.getEndpoint(), body);
        }
        DataSet set = new DataSet();
        set.setTable(table);
        boolean exe = configs.execute();
        if(!exe) {
            return set;
        }
        LinkedHashMap<String,Column> columns = new LinkedHashMap<>();
        if(!system &&ConfigStore.IS_AUTO_CHECK_METADATA(configs) && null != table) {
            columns = columns(runtime, random, false, table, false);
        }
        try{
            set = actuator.select(this, runtime, random, system, ACTION.DML.SELECT, table, configs, run, null, null, columns);

            LinkedHashMap<String,Column> metadatas = set.getMetadatas();
            if(!system && (null == metadatas || metadatas.isEmpty())&&ConfigStore.IS_CHECK_EMPTY_SET_METADATA(configs)) {

            }
            boolean slow = false;
            long SLOW_SQL_MILLIS = ConfigStore.SLOW_SQL_MILLIS(configs);
            long times = configs.getLastExecuteTime();
            if(SLOW_SQL_MILLIS > 0 && ConfigStore.IS_LOG_SLOW_SQL(configs) && times > SLOW_SQL_MILLIS) {
                slow = true;
                log.warn("{}[slow cmd][action:select][执行耗时:{}]{}", random, DateUtil.format(times), run.log(ACTION.DML.SELECT, ConfigStore.IS_SQL_LOG_PLACEHOLDER(configs)));
                if(null != dmListener) {
                    dmListener.slow(runtime, random, ACTION.DML.SELECT, null, null, null, null, true, set,times);
                }

            }
            if(!slow && log.isInfoEnabled() && ConfigStore.IS_LOG_SQL_TIME(configs)) {
                log.info("{}[action:select][执行耗时:{}]", random, DateUtil.format(times));
                log.info("{}[action:select][封装耗时:{}][封装行数:{}]", random, DateUtil.format(configs.getLastPackageTime()), set.size());
            }
            set.setDatalink(runtime.datasource());
        }catch(Exception e) {
            if(ConfigStore.IS_PRINT_EXCEPTION_STACK_TRACE(configs)) {
                e.printStackTrace();
            }
            if(ConfigStore.IS_LOG_SQL_WHEN_ERROR(configs)) {
                log.error("{}[{}][action:select]{}", random, LogUtil.format("查询异常:", 33) + e, run.log(ACTION.DML.SELECT, ConfigStore.IS_SQL_LOG_PLACEHOLDER(configs)));
            }
            if(ConfigStore.IS_THROW_SQL_QUERY_EXCEPTION(configs)) {
                CommandQueryException ex = new CommandQueryException("query异常:"+e,e);
                throw ex;
            }

        }
        return set;
    }
}
