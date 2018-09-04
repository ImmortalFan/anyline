/* 
 * Copyright 2006-2015 www.anyline.org
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
 *          AnyLine以及一切衍生库 不得用于任何与网游相关的系统
 */


package org.anyline.tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;

import org.apache.log4j.Logger;
import org.anyline.util.BasicUtil;
import org.anyline.util.BeanUtil;
import org.anyline.util.ConfigTable;
import org.anyline.util.WebUtil;
import org.anyline.util.regular.Regular;
import org.anyline.util.regular.RegularUtil;


public class Radio extends BaseBodyTag{
	private static final long serialVersionUID = 1L;
	private static Logger log = Logger.getLogger(Radio.class);
	private String scope;
	private Object data;
	private String valueKey = ConfigTable.getString("DEFAULT_PRIMARY_KEY","CD");
	private String textKey = "NM";
	private String head;
	private String headValue;
	private String border = "true";

	public int doEndTag() throws JspException {
		HttpServletRequest request = (HttpServletRequest)pageContext.getRequest();
		String html = "";
		try{
			if(BasicUtil.isEmpty(name)){
				name = BasicUtil.getRandomLowerString(10);
			}
			if(null != data){
				if(data instanceof String){
					if(data.toString().endsWith("}")){
						data = data.toString().replace("{", "").replace("}", "");
					}else{
						if("servelt".equals(scope) || "application".equalsIgnoreCase(scope)){
							data = request.getSession().getServletContext().getAttribute(data.toString());
						}else if("session".equals(scope)){
							data = request.getSession().getAttribute(data.toString());
						}else{
							data = request.getAttribute(data.toString());
						}
					}
				}
				if(data instanceof String){
					String items[] = data.toString().split(",");
					List list = new ArrayList();
					for(String item:items){
						Map map = new HashMap();
						String tmp[] = item.split(":");
						map.put(valueKey, tmp[0]);
						map.put(textKey, tmp[1]);
						list.add(map);
					}
					data = list;
				}
				//选中值
				if (null != this.value) {
					if(!(this.value instanceof String || this.value instanceof Collection)){
						this.value = this.value.toString();
					}
					if (this.value instanceof String) {
						if (this.value.toString().endsWith("}")) {
							this.value = this.value.toString().replace("{", "").replace("}", "");
						}
					}
					if (this.value instanceof String) {
						String items[] = this.value.toString().split(",");
						List list = new ArrayList();
						for (String item : items) {
							list.add(item);
						}
						this.value = list;
					}
				}

				if(null == headValue){
					headValue = "";
				}

				if(null == headValue){
					headValue = "";
				}
				if(null != head){
					String id = this.id;
					if(BasicUtil.isEmpty(id)){
						id = name +"_"+ headValue; 
					}
					if("true".equalsIgnoreCase(border)){
						html += "<div class=\"al-radio-item-border\">";
					}
					html += "<input type=\"radio\"";
					if((null != headValue && headValue.equals(value))){
						html += " checked = \"checked\"";
					}
					Map<String,String> map = new HashMap<String,String>();
					map.put(valueKey, headValue);
					html += tag() + crateExtraData(map) + "/>";
					html += "<label for=\""+id+ "\">" + head + "</label>\n";
					if("true".equalsIgnoreCase(border)){
						html += "</div>";
					}
				}
				
				
				
				
				Collection<Map> items = (Collection<Map>)data;
				if(null != items)
				for(Map item:items){
					Object srcValue = BeanUtil.getFieldValue(item, valueKey);
					Object value = srcValue;
					if(this.encrypt){
						value = WebUtil.encryptValue(value+"");
					}
					
					String id = name +"_"+ value;
					if("true".equalsIgnoreCase(border)){
						html += "<div class=\"al-radio-item-border\">";
					}
					html += "<input type=\"radio\" value=\"" + value + "\" id=\"" + id + "\"";
					if(null != srcValue && null != this.value && srcValue.toString().equals(this.value.toString())){
						html += " checked=\"checked\"";
					}
					html += tag() + crateExtraData(item) + "/>";
					String label = "<label for=\""+id+ "\">";
					String text = "";
					if (textKey.contains("{")) {
						text = textKey;
						List<String> keys = RegularUtil.fetch(textKey, "\\{\\w+\\}", Regular.MATCH_MODE.CONTAIN, 0);
						for (String key : keys) {
							Object v = item.get(key.replace("{", "").replace("}", ""));
							if (null != v) {
								text = text.replace(key, v.toString());
							}
						}
					} else {
						Object v = item.get(textKey);
						if (null != v) {
							text = v.toString();
						}
					}
					label += text +"</label>\n";
					html += label;
					if("true".equalsIgnoreCase(border)){
						html += "</div>";
					}
				}
			}
			JspWriter out = pageContext.getOut();
			out.print(html);
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			release();
		}
        return EVAL_PAGE;   
	}


	public Object getData() {
		return data;
	}


	public String getBorder() {
		return border;
	}


	public void setBorder(String border) {
		this.border = border;
	}


	public void setData(Object data) {
		this.data = data;
	}


	public String getValueKey() {
		return valueKey;
	}


	public void setValueKey(String valueKey) {
		this.valueKey = valueKey;
	}


	public String getTextKey() {
		return textKey;
	}


	public void setTextKey(String textKey) {
		this.textKey = textKey;
	}


	@Override
	public void release() {
		super.release();
		scope = null;
		data = null;
		value = null;
		body = null;
		head = null;
		headValue = null;
		valueKey = ConfigTable.getString("DEFAULT_PRIMARY_KEY","CD");
		textKey = "NM";
		border = "true";
	}

	public String getScope() {
		return scope;
	}

	public String getHead() {
		return head;
	}


	public void setHead(String head) {
		this.head = head;
	}


	public String getHeadValue() {
		return headValue;
	}


	public void setHeadValue(String headValue) {
		this.headValue = headValue;
	}


	public void setScope(String scope) {
		this.scope = scope;
	}
}