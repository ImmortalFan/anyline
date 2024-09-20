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

package org.anyline.data.jdbc.adapter.init.alias;

import org.anyline.data.metadata.PropertyAlias;
import org.anyline.metadata.*;

public enum MySQLGenusPropertyAlias implements PropertyAlias {
	MergeTree   (Table.class  ,"ENGINE" ,"MergeTree" ,"InnoDB" );

	private Class<? extends Metadata> metadata  ; // 适用类型 如Table.class
	private String property                     ; // 属性分组 如 ENGINE
	private String alias                        ; // 输入属性值
	private String value                        ; // 兼容当前数据库的属性值

	MySQLGenusPropertyAlias(Class<? extends Metadata> metadata, String group, String alias, String value) {
		this.metadata = metadata;
		this.property = property;
		this.alias = alias;
		this.value = value;
	}

	@Override
	public Class<? extends Metadata> metadata() {
		return metadata;
	}

	@Override
	public String property() {
		return property;
	}

	@Override
	public String alias() {
		return alias;
	}

	@Override
	public String value() {
		return value;
	}

}
