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


package org.anyline.jdbc.prepare.auto.init;

import org.anyline.jdbc.adapter.JDBCAdapter;
import org.anyline.jdbc.param.Config;
import org.anyline.jdbc.param.ConfigChain;
import org.anyline.jdbc.prepare.Condition;
import org.anyline.jdbc.prepare.ConditionChain;
import org.anyline.jdbc.run.RunValue;
import org.anyline.jdbc.prepare.init.SimpleConditionChain;
import org.anyline.util.BasicUtil;

import java.util.ArrayList;
import java.util.List;
 
public class SimpleAutoConditionChain extends SimpleConditionChain implements ConditionChain{
	public SimpleAutoConditionChain(){}
	public SimpleAutoConditionChain(ConfigChain chain){
		if(null == chain){
			return;
		}
		for(Config config:chain.getConfigs()){
			if(config instanceof ConfigChain){
				conditions.add(new SimpleAutoConditionChain((ConfigChain)config));
			}else{
				conditions.add(new SimpleAutoCondition(config));
			}
		}
	}
	@Override
	public String getRunText(String prefix, JDBCAdapter adapter){
		runValues = new ArrayList<>();
		int size = conditions.size(); 
		if(size == 0){ 
			return ""; 
		}
		StringBuilder subBuilder = new StringBuilder();
		String txt = "";
		for(int i=0; i<size; i++){
			Condition condition = conditions.get(i);
//			if(condition.isContainer()){
//				txt = ((ConditionChain) condition).getRunText(adapter);
//			}else{
//				txt = condition.getRunText(adapter);
//			}
			if(null == condition){
				continue;
			}
			txt = condition.getRunText(prefix, adapter);
			if(BasicUtil.isEmpty(txt)){
				continue;
			}
			List<RunValue> values = condition.getRunValues();
			if(condition.getVariableType() == Condition.VARIABLE_FLAG_TYPE_NONE 
					|| !BasicUtil.isEmpty(true, values) 
					|| condition.isActive()
					|| condition.isRequired()){
				//condition instanceof ConditionChain
				//if(i>0 /*&& !condition.isContainer()*/){
				if(joinSize>0){
					String chk = txt.toLowerCase().trim();
					if(!chk.startsWith("and ") && !chk.startsWith("or ") && !chk.startsWith("and(") && !chk.startsWith("or(")){
						subBuilder.append(condition.getJoin());
					}
				}
				subBuilder.append(" ").append(txt);
				addRunValue(values);
				joinSize ++;
			}
		}
 
		if(joinSize > 0){
			StringBuilder builder = new StringBuilder();
			if(!hasContainer() || getContainerJoinSize() > 0){
				builder.append("\nAND");
			}else{
				builder.append("\n\t");
			}
			builder.append("(");
			builder.append(subBuilder.toString());
			builder.append(")\n\t");
			return builder.toString(); 
		}else{
			return "";
		} 
	} 
	private int getContainerJoinSize(){ 
		if(hasContainer()){ 
			return getContainer().getJoinSize(); 
		}else{ 
			return 0; 
		} 
	}

	public String toString(){
		int size = conditions.size();
		String txt = "[";
		for(int i=0;i<size; i++){
			if(i==0){
				txt += conditions.get(i).toString();
			}else{
				txt += ","+conditions.get(i).toString();
			}
		}
		txt += "]";
		return txt;
	}
} 
