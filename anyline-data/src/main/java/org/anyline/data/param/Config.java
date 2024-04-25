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


package org.anyline.data.param;

import org.anyline.entity.Compare;
import org.anyline.data.prepare.Condition;
import org.anyline.data.prepare.ConditionChain;
import org.anyline.entity.Compare.EMPTY_VALUE_SWITCH;

import java.util.List;
import java.util.Map;
 
public interface Config {
	// 从request 取值方式
	static int FETCH_REQUEST_VALUE_TYPE_NONE 	= 0;	// 没有参数
	static int FETCH_REQUEST_VALUE_TYPE_SINGLE 	= 1;	// 单值
	static int FETCH_REQUEST_VALUE_TYPE_MULTIPLE = 2;	// 数组
	void setValue(Map<String,Object> values); 
	List<Object> getValues() ; 
	List<Object> getOrValues() ; 
	void addValue(Object value);
	void setValue(Object value);

	void setOrValue(Object value);
	void addOrValue(Object value);
	/** 
	 *  
	 * @param chain 容器 
	 * @return Condition
	 */ 
	Condition createAutoCondition(ConditionChain chain);

	String getPrefix() ; 	// XML condition.id 或表名/表别名
 
	void setPrefix(String prefix) ;

	String getVariable() ;//XML condition中的key 或列名

	void setVariable(String variable) ;

	String getKey() ;//参数key

	void setKey(String key) ;


	Compare getCompare() ; 
	void setCompare(Compare compare) ; 
	
	Compare getOrCompare() ; 
	void setOrCompare(Compare compare) ;

	/**
	 * 是否空条件
	 * @return boolean
	 */
	boolean isEmpty() ; 
 
	void setEmpty(boolean empty) ; 


	String getJoin() ; 
 
	void setJoin(String join) ; 
 
	boolean isKeyEncrypt() ; 
 
	boolean isValueEncrypt();
	
	Object clone();
	String toString();
	String cacheKey();

	void setText(String text);
	String getText();

	void setOverCondition(boolean over);
	void setOverValue(boolean over);
	boolean isOverCondition();
	boolean isOverValue();
	void setSwitch(EMPTY_VALUE_SWITCH swt);
	EMPTY_VALUE_SWITCH getSwitch();

	/**
	 * 是否需要跟前面的条件 隔离，前面所有条件加到()中
	 * @return boolean
	 */
/*	boolean apart();
	void apart(boolean apart);*/
	/**
	 * 是否作为一个整体，不可分割，与其他条件合并时以()包围
	 * @return boolean
	 */
	boolean integrality();
	void integrality(boolean integrality);
}
