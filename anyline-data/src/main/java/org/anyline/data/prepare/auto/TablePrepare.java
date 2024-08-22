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

package org.anyline.data.prepare.auto;


import org.anyline.data.prepare.RunPrepare;

public interface TablePrepare extends AutoPrepare{

    /**
     * 设置数据源
     * @param table 表
     * @return Run 最终执行命令 如JDBC环境中的 SQL 与 参数值
     */
    RunPrepare setTable(String table);

}
