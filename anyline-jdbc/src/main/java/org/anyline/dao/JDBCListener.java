/*
 * Copyright 2006-2022 www.anyline.org
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
 *
 *
 */
package org.anyline.dao;

import org.anyline.entity.DataSet;
import org.anyline.jdbc.config.db.Procedure;
import org.anyline.jdbc.config.db.run.RunSQL;

import java.util.List;
import java.util.Map;

public interface JDBCListener {

    /**
     * 统计总记录数之前调用
     * @param dao dao
     * @param run sql
     */
    public void beforeTotal(AnylineDao dao, RunSQL run);
    /**
     * 统计总记录数之后调用
     * @param dao dao
     * @param run sql
     * @param total total
     */
    public void afterTotal(AnylineDao dao, RunSQL run, int total);
    /**
     * 查询之前调用
     * @param dao dao
     * @param run sql
     */
    public void beforeQuery(AnylineDao dao, RunSQL run);
    /**
     * 查询之后调用(调用service.map或service.maps)
     * @param dao dao
     * @param run sql
     * @param maps 查询结果
     */
    public void afterQuery(AnylineDao dao, RunSQL run, List<Map<String,Object>>  maps);
    /**
     * 查询之后调用(调用service.query或service.querys)
     * @param dao dao
     * @param run sql
     * @param set 查询结果
     */
    public void afterQuery(AnylineDao dao, RunSQL run, DataSet set );
    /**
     * count之前调用
     * @param dao dao
     * @param run sql
     */
    public void beforeCount(AnylineDao dao, RunSQL run);
    /**
     * count之后调用
     * @param dao dao
     * @param run sql
     * @param count count
     */
    public void afterCount(AnylineDao dao, RunSQL run, int count);

    /**
     * 判断是否存在之前调用
     * @param dao dao
     * @param run sql
     */
    public void beforeExists(AnylineDao dao, RunSQL run);
    /**
     * 判断是否存在之后调用
     * @param dao dao
     * @param run sql
     * @param exists 是否存在
     */
    public void afterExists(AnylineDao dao, RunSQL run, boolean exists);

    /**
     * 更新之前调用
     * @param dao dao
     * @param run run
     * @param dest 需要更新的表
     * @param obj 更新内容
     * @param columns 需要更新的列
     * @return 是否执行  如果返回false 将不执行更新
     */
    public boolean beforeUpdate(AnylineDao dao, RunSQL run, String dest, Object obj, String ... columns);
    /**
     * 更新之前调用
     * @param dao dao
     * @param run run
     * @param count 影响行数
     * @param dest 需要更新的表
     * @param obj 更新内容
     * @param columns 需要更新的列
     * @return 是否执行  如果返回false 将不执行更新
     */
    public void afterUpdate(AnylineDao dao, RunSQL run,int count, String dest, Object obj, String ... columns);

    /**
     * 插入之前调用
     * @param dao dao
     * @param run sql
     * @param dest 需要插入的表
     * @param obj 接入内容
     * @param checkParimary 是否需要检测主键
     * @param columns 需要插入的列
     * @return 是否执行  如果返回false 将不执行插入
     */
    public boolean beforeInsert(AnylineDao dao, RunSQL run, String dest, Object obj, boolean checkParimary, String ... columns);

    /**
     * 插入之后调用
     * @param dao dao
     * @param run sql
     * @param count 影响行数
     * @param dest 需要插入的表
     * @param obj 接入内容
     * @param checkParimary 是否需要检测主键
     * @param columns 需要插入的列
     */
    public void afterInsert(AnylineDao dao, RunSQL run,int count, String dest, Object obj, boolean checkParimary, String ... columns);

    /**
     * 批量插入前调用
     * @param dao dao
     * @param dest 需要插入的表
     * @param obj 插入内容
     * @param checkParimary 是否需要检测主键
     * @param columns 需要插入的列
     * @return 是否执行  如果返回false 将不执行插入
     */
    public boolean beforeBatchInsert(AnylineDao dao, String dest, Object obj, boolean checkParimary, String ... columns);
    /**
     * 批量插入之后调用
     * @param dao dao
     * @param count 影响行数
     * @param dest 需要插入的表
     * @param obj 接入内容
     * @param checkParimary 是否需要检测主键
     * @param columns 需要插入的列
     */
    public void afterBatchInsert(AnylineDao dao, int count, String dest, Object obj, boolean checkParimary, String ... columns);

    /**
     * 执行SQL之前调用
     * @param dao dao
     * @param run sql
     * @return 是否执行 如果返回false装不执行sql
     */
    public boolean beforeExecute(AnylineDao dao, RunSQL run);

    /**
     * 执行SQL之后调用
     * @param dao dao
     * @param run sql
     * @param count 影响行数
     */
    public void afterExecute(AnylineDao dao, RunSQL run, int count);

    /**
     * 执行存储过程之前调用
     * @param dao dao
     * @param procedure 存储过程
     * @return 是否执行 如果返回false装不执行存储过程
     */
    public boolean beforeExecute(AnylineDao dao, Procedure procedure);

    /**
     * 执行存储过程之后调用
     * @param dao dao
     * @param procedure 存储过程
     * @param result 执行是否成功 如果需要返回值需要从procedure中获取
     */
    public void afterExecute(AnylineDao dao, Procedure procedure, boolean result);

    /**
     * 查询存过程之前调用
     * @param dao dao
     * @param procedure 存储过程
     */
    public void beforeQuery(AnylineDao dao, Procedure procedure);

    /**
     * 查询存储过程之后调用
     * @param dao dao
     * @param procedure 存储过程
     * @param set 返回结果集
     */
    public void afterQuery(AnylineDao dao, Procedure procedure, DataSet set);

    /**
     * 执行删除前调用
     * @param dao dao
     * @param run sql
     * @return 是否执行 如果返回false装不执行删除
     */
    public boolean beforeDelete(AnylineDao dao, RunSQL run);

    /**
     * 执行删除后调用
     * @param dao dao
     * @param run sql
     * @param count 影响行数
     */
    public void afterDelete(AnylineDao dao, RunSQL run, int count);
}
