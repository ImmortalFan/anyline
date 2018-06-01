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


package org.anyline.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JsonConfig;

import org.anyline.util.BasicUtil;
import org.anyline.util.ConfigTable;
import org.anyline.util.DateUtil;
import org.anyline.util.EscapeUtil;
import org.anyline.util.JSONDateFormatProcessor;
import org.anyline.util.regular.RegularUtil;
import org.apache.log4j.Logger;

public class DataSet implements Collection<DataRow>, Serializable {
	private static final long serialVersionUID = 6443551515441660101L;
	protected static Logger log = Logger.getLogger(DataSet.class);
	private boolean result 			= true		; // 执行结果
	private Exception exception		= null		; // 异常
	private String message			= null		; // 提示信息
	private PageNavi navi			= null		; // 分页
	private List<String> head		= null		; // 表头
	private List<DataRow> rows		= null		; // 数据
	private List<String> primaryKeys= null		; // 主键
	private String dataSource		= null		; // 数据源(表|视图|XML定义SQL)
	private String schema			= null		;
	private String table			= null		;
	private long createTime 		= 0			; //创建时间
	private long expires 			= -1		; //过期时间(毫秒) 从创建时刻计时expires毫秒后过期
	private boolean isFromCache		= false		; //是否来自缓存
	private boolean isAsc			= false		;
	private boolean isDesc			= false		;
	
	/**
	 * 创建索引
	 * @param key
	 * @return
	 * crateIndex("ID");
	 * crateIndex("ID:ASC");
	 */
	public DataSet creatIndex(String key){
		return this;
	}
	public DataSet() {
		rows = new ArrayList<DataRow>();
		createTime = System.currentTimeMillis();
	}

	public static DataSet parseJson(String json){
		if(null != json){
			try{
				return parseJson(JSONArray.fromObject(json));
			}catch(Exception e){
				
			}
		}
		return null;
	}
	public static DataSet parseJson(JSONArray json){
		DataSet set = new DataSet();
		if(null != json){
			int size = json.size();
			for(int i=0; i<size; i++){
				Object val = json.get(i);
				if(null != val){
					if(val instanceof JSONObject){
						DataRow row = DataRow.parseJson((JSONObject) val);
						set.add(row);
					}
				}
			}
		}
		return set;
	}
	/**
	 * 添加主键
	 * 
	 * @param parmary
	 */
	public void addPrimary(String ... pks){
		if(null != pks){
			List<String> list = new ArrayList<String>();
			for(String pk:pks){
				list.add(pk);
			}
			addPrimary(list);
		}
	}
	public void addPrimary(Collection<String> pks) {
		if (null == this.primaryKeys) {
			this.primaryKeys = new ArrayList<String>();
		}
		if (null == pks) {
			return;
		}
		for (String item : pks) {
			if (BasicUtil.isEmpty(item)) {
				continue;
			}
			item = key(item);
			if (!this.primaryKeys.contains(item)) {
				this.primaryKeys.add(item);
			}
		}
	}
	public void set(int index, DataRow item){
		rows.set(index, item);
	}
	/**
	 * 设置主键
	 * 
	 * @param primary
	 */
	public void setPrimary(String ... pks){
		if(null != pks){
			List<String> list = new ArrayList<String>();
			for(String pk:pks){
				list.add(pk);
			}
			setPrimary(list);
		}
	}
	public void setPrimary(Collection<String> pks) {
		if (null == pks) {
			return;
		}
		this.primaryKeys = new ArrayList<String>();
		addPrimary(pks);
	}

//	public DataSet toLowerKey() {
//		for (DataRow row : rows) {
//			row.toLowerKey();
//		}
//		return this;
//	}
//
//	public DataSet toUpperKey() {
//		for (DataRow row : rows) {
//			row.toUpperKey();
//		}
//		return this;
//	}

	/**
	 * 是否有主键
	 * 
	 * @return
	 */
	public boolean hasPrimaryKeys() {
		if (null != primaryKeys && primaryKeys.size() > 0) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 提取主键
	 * 
	 * @return
	 */
	public List<String> getPrimaryKeys() {
		if (null == primaryKeys) {
			primaryKeys = new ArrayList<String>();
		}
		return primaryKeys;
	}

	/**
	 * 添加表头
	 * 
	 * @param col
	 */
	public void addHead(String col) {
		if (null == head) {
			head = new ArrayList<String>();
		}
		if ("ROW_NUMBER".equals(col)) {
			return;
		}
		if (head.contains(col)) {
			return;
		}
		head.add(col);
	}

	/**
	 * 表头
	 * 
	 * @return
	 */
	public List<String> getHead() {
		return head;
	}
	public int indexOf(Object obj){
		return rows.indexOf(obj);
	}
	public DataSet cut(int begin){
		return cut(begin, rows.size()-1);
	}
	public DataSet cut(int begin, int end){
		if(begin < 0){
			begin = 0;
		}
		if(end >= rows.size()){
			end = rows.size() - 1;
		}
		rows = rows.subList(begin, end);
		return this;
	}

	public DataSet(List<Map<String, Object>> list) {
		rows = new ArrayList<DataRow>();
		if (null == list)
			return;
		for (Map<String, Object> map : list) {
			DataRow row = new DataRow(map);
			rows.add(row);
		}
	}

	/**
	 * 记录数量
	 * 
	 * @return
	 */
	public int size() {
		int result = 0;
		if (null != rows)
			result = rows.size();
		return result;
	}

	public int getSize() {
		return size();
	}

	/**
	 * 是否出现异常
	 * 
	 * @return
	 */
	public boolean isException() {
		return null != exception;
	}

	public boolean isFromCache(){
		return isFromCache;
	}
	public void setIsFromCache(boolean bol){
		this.isFromCache = bol;
	}

	/**
	 * 返回数据是否为空
	 * 
	 * @return
	 */
	public boolean isEmpty() {
		boolean result = true;
		if (null == rows) {
			result = true;
		} else if (rows instanceof Collection) {
			result = ((Collection<?>) rows).isEmpty();
		}
		return result;
	}

	
	/**
	 * 读取一行数据
	 * 
	 * @param index
	 * @return
	 */
	public DataRow getRow(int index) {
		DataRow row = null;
		if (null != rows && index < rows.size()) {
			row = rows.get(index);
		}
		if (null != row) {
			row.setContainer(this);
		}
		return row;
	}

	/**
	 * 根据单个属性值读取一行
	 * 
	 * @param key
	 *            属性
	 * @param value
	 *            值
	 * @return
	 */
	public DataRow getRow(String... params) {
		return getRow(0, params);
	}
	public DataRow getRow(int begin, String... params) {
		DataSet set = getRows(begin,1, params);
		if (set.size() > 0) {
			return set.getRow(0);
		}
		return null;
	}
	/**
	 * distinct
	 * @param keys
	 * @return
	 */
	public DataSet distinct(String... keys) {
		DataSet result = new DataSet();
		if (null != rows) {
			int size = rows.size();
			for (int i = 0; i < size; i++) {
				DataRow row = rows.get(i);
				//查看result中是否已存在
				String[] params = packParam(row, keys);
				if(result.getRows(params).size() == 0){
					DataRow tmp = new DataRow();
					for(String key:keys){
						tmp.put(key, row.get(key));
					}
					result.addRow(tmp);
				}
			}
		}
		result.cloneProperty(this);
		return result;
	}
	public Object clone(){
		DataSet set = new DataSet();
		List<DataRow> rows = new ArrayList<DataRow>();
		for(DataRow row:this.rows){
			rows.add((DataRow)row.clone());
		}
		set.setRows(rows);
		set.cloneProperty(this);
		return set;
	}
	private DataSet cloneProperty(DataSet from){
		return cloneProperty(from, this);
	}
	public static DataSet cloneProperty(DataSet from, DataSet to){
		if(null != from && null != to){
			to.exception = from.exception;
			to.message = from.message;
			to.navi = from.navi;
			to.head = from.head;
			to.primaryKeys = from.primaryKeys;
			to.dataSource = from.dataSource;
			to.schema = from.schema;
			to.table = from.table;
		}
		return to;
	}
	/**
	 * 筛选符合条件的集合
	 * @param params key1,value1,key2:value2,key3,value3
	 * @param qty:最多筛选多少个 0表示不限制
	 * @return
	 */
	public DataSet getRows(int begin, int qty, String... params) {
		DataSet set = new DataSet();
		Map<String,String> kvs = new HashMap<String,String>();
		int len = params.length;
		int i = 0;
		while(i<len){
			String p1 = params[i];
			if(BasicUtil.isEmpty(p1)){
				i++;
				continue;
			}else if(p1.contains(":")){
				String tmp[] = p1.split(":");
				kvs.put(tmp[0], tmp[1]);
				i++;
				continue;
			}else{
				if(i+1<len){
					String p2 = params[i+1];
					if(BasicUtil.isEmpty(p2) || !p2.contains(":")){
						kvs.put(p1, p2); 
						i+=2;
						continue;
					}else{
						String tmp[] = p2.split(":");
						kvs.put(tmp[0], tmp[1]);
						i+=2;
						continue;
					}
				}

			}
			i++;
		}
		int size = size();
		for(i=0; i<size; i++){
			DataRow row = getRow(i);
			boolean chk = true;
			for(String k : kvs.keySet()){
				String v = kvs.get(k);
				String value = row.getString(k);
				if(null == v && null == value){
					continue;
				}
				if(!v.equals(value)){
					chk = false;
					break;
				}
			}
			if(chk){
				set.add(row);
				if(qty > 0 && set.size() >= qty){
					break;
				}
			}
		}
		set.cloneProperty(this);
		return set;
	}

	public DataSet getRows(int begin, String... params) {
		return getRows(begin, 0, params);
	}
	public DataSet getRows(String... params) {
		return getRows(0, params);
	}

	/**
	 * 数字格式化
	 * @param format
	 * @param cols
	 * @return
	 */
	public DataSet formatNumber(String format, String ... cols){
		if(null == cols || BasicUtil.isEmpty(format)){
			return this;
		}
		int size = size();
		for(int i=0; i<size; i++){
			DataRow row = getRow(i);
			row.formatNumber(format, cols);
		}
		return this;
	}
	/**
	 * 日期格式化
	 * @param format
	 * @param cols
	 * @return
	 */
	public DataSet formatDate(String format, String ... cols){
		if(null == cols || BasicUtil.isEmpty(format)){
			return this;
		}
		int size = size();
		for(int i=0; i<size; i++){
			DataRow row = getRow(i);
			row.formatDate(format, cols);
		}
		return this;
	}
	/**
	 * 提取符合指定属性值的集合
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	private DataSet filter(int begin, DataSet src, String key, String value) {
		DataSet set = new DataSet();
		String tmpValue;
		int size = src.size();
		if(begin < 0){
			begin = 0;
		}
		for (int i = begin; i < size; i++) {
			tmpValue = src.getString(i, key);
			if ((null == value && null == tmpValue)
					|| (null != value && value.equals(tmpValue))) {
				set.add(src.getRow(i));
			}
		}
		set.cloneProperty(this);
		return set;
	}

	public DataSet getRows(int fr, int to) {
		DataSet set = new DataSet();
		for (int i = fr; i < this.size() && i <= to; i++) {
			set.addRow(this.getRow(i));
		}
		return set;
	}
	
	/**
	 * 合计
	 * @param top 多少行
	 * @param key
	 * @return
	 */
	public BigDecimal sum(int top, String key){
		BigDecimal result = BigDecimal.ZERO;
		int size = rows.size();
		if(size>top){
			size = top;
		}
		for (int i = 0; i < size; i++) {
			BigDecimal tmp = getDecimal(i, key);
			if(null != tmp){
				result = result.add(getDecimal(i, key));
			}
		}
		return result;
	}
	public BigDecimal sum(String key) {
		BigDecimal result = BigDecimal.ZERO;
		result = sum(size(), key);
		return result;
	}
	/**
	 * 最大值
	 * @param top 多少行
	 * @param key
	 * @return
	 */
	public BigDecimal maxDecimal(int top, String key){
		BigDecimal result = new BigDecimal(0);
		int size = rows.size();
		if(size>top){
			size = top;
		}
		for (int i = 0; i < size; i++) {
			BigDecimal tmp = getDecimal(i, key);
			if(null != tmp && tmp.compareTo(result) > 0){
				result = tmp;
			}
		}
		return result;
	}
	public BigDecimal maxDecimal(String key){
		return maxDecimal(size(),key);
	}
	public int maxInt(int top, String key){
		BigDecimal result = maxDecimal(top, key);
		return result.intValue();
	}
	public int maxInt(String key){
		return maxInt(size(), key);
	}

	public double maxDouble(int top, String key){
		BigDecimal result = maxDecimal(top, key);
		return result.doubleValue();
	}
	public double maxDouble(String key){
		return maxDouble(size(), key);
	}

	public BigDecimal max(int top, String key){
		BigDecimal result = maxDecimal(top, key);
		return result;
	}
	public BigDecimal max(String key){
		return maxDecimal(size(), key);
	}
	
	
	
	/**
	 * 最小值
	 * @param top 多少行
	 * @param key
	 * @return
	 */
	public BigDecimal minDecimal(int top, String key){
		BigDecimal result = new BigDecimal(0);
		int size = rows.size();
		if(size>top){
			size = top;
		}
		for (int i = 0; i < size; i++) {
			BigDecimal tmp = getDecimal(i, key);
			if(null != tmp && tmp.compareTo(result) < 0){
				result = tmp;
			}
		}
		return result;
	}
	public BigDecimal minDecimal(String key){
		return minDecimal(size(),key);
	}

	public int minInt(int top, String key){
		BigDecimal result = minDecimal(top, key);
		return result.intValue();
	}
	public int minInt(String key){
		return minInt(size(), key);
	}

	public double minDouble(int top, String key){
		BigDecimal result = minDecimal(top, key);
		return result.doubleValue();
	}
	public double minDouble(String key){
		return minDouble(size(), key);
	}

	public BigDecimal min(int top, String key){
		BigDecimal result = minDecimal(top, key);
		return result;
	}
	public BigDecimal min(String key){
		return minDecimal(size(), key);
	}
	
	/**
	 * key对应的value最大的一行
	 * max与 maxRow区别:max只对number类型计算 其他类型异常
	 * @param key
	 * @return
	 */
	public DataRow maxRow(String key){
		int size = size();
		if(size ==0){
			return null;
		}
		DataRow row = null;
		if(isAsc){
			row = getRow(size-1);
		}else if(isDesc){
			row = getRow(0);
		}else{
			asc();
			row = getRow(size-1);
		}
		return row;
	}
	public DataRow minRow(String key){
		int size = size();
		if(size ==0){
			return null;
		}
		DataRow row = null;
		if(isAsc){
			row = getRow(0);
		}else if(isDesc){
			row = getRow(size-1);
		}else{
			asc();
			row = getRow(0);
		}
		return row;
	}
	/**
	 * 平均值 空数据不参与加法但参与除法
	 * @param top 多少行
	 * @param key
	 * @return
	 */
	public BigDecimal avg(int top, String key){
		BigDecimal result = BigDecimal.ZERO;
		int size = rows.size();
		if(size>top){
			size = top;
		}
		int count = 0;
		for (int i = 0; i < size; i++) {
			BigDecimal tmp = getDecimal(i, key);
			if(null != tmp){
				result = result.add(tmp);
			}
			count ++;
		}
		if(count >0){
			result = result.divide(new BigDecimal(count));
		}
		return result;
	}
	public BigDecimal avg(String key){
		BigDecimal result = avg(size(),key);
		return result;
	}
	
	public void addRow(DataRow row) {
		if (null != row) {
			rows.add(row);
		}
	}

	public void addRow(int idx, DataRow row) {
		if (null != row) {
			rows.add(idx, row);
		}
	}
	
	/**
	 * 合并key值 以connector连接
	 * @param key
	 * @param connector
	 * @return
	 */
	public String concat(String key, String connector){
		return BasicUtil.concat(getStrings(key), connector);
	}
	public String concatNvl(String key, String connector){
		return BasicUtil.concat(getNvlStrings(key), connector);
	}
	public String concatWithoutNull(String key, String connector){
		return BasicUtil.concat(getStringsWithoutNull(key), connector);
	}
	public String concatWithoutEmpty(String key, String connector){
		return BasicUtil.concat(getStringsWithoutEmpty(key), connector);
	}
	public String concatNvl(String key){
		return BasicUtil.concat(getNvlStrings(key), ",");
	}
	public String concatWithoutNull(String key){
		return BasicUtil.concat(getStringsWithoutNull(key), ",");
	}
	public String concatWithoutEmpty(String key){
		return BasicUtil.concat(getStringsWithoutEmpty(key), ",");
	}
	public String concat(String key){
		return BasicUtil.concat(getStrings(key), ",");
	}
	/**
	 * 提取单列值
	 * 
	 * @param key
	 * @return
	 */
	public List<Object> fetchValues(String key) {
		List<Object> result = new ArrayList<Object>();
		for (int i = 0; i < size(); i++) {
			result.add(this.getString(i, key));
		}
		return result;
	}
	
	/**
	 * 取单列不重复的值
	 * @param key
	 * @return
	 */
	public List<String> fetchDistinctValue(String key) {
		List<String> result = new ArrayList<String>();
		for (int i = 0; i < size(); i++) {
			String value = getString(i, key);
			if (result.contains(value)) {
				continue;
			}
			result.add(value);
		}
		return result;
	}

	/**
	 * 分页
	 * 
	 * @return
	 */
	public String displayNavi(String link) {
		String result = "";
		if (null != navi) {
			result = navi.toString();
		}
		return result;
	}

	public String navi(String link) {
		return displayNavi(link);
	}

	public String displayNavi() {
		return displayNavi(null);
	}

	public String navi() {
		return displayNavi(null);
	}
	public DataSet put(int idx, String key, Object value){
		DataRow row = getRow(idx);
		if(null != row){
			row.put(key, value);
		}
		return this;
	}
	/**
	 * String
	 * 
	 * @param index
	 * @param key
	 * @return
	 */
	public String getString(int index, String key) {
		String result = null;
		DataRow row = getRow(index);
		if (null != row)
			result = row.getString(key);
		return result;
	}
	public Object get(int index, String key){
		DataRow row = getRow(index);
		if(null != row){
			return row.get(key);
		}
		return null;
	}

	public String getString(String key) {
		return getString(0, key);
	}
	public List<String> getStrings(String key){
		List<String> result = new ArrayList<String>();
		List<Object> list = fetchValues(key);
		for(Object val:list){
			if(null != val){
				result.add(val.toString());
			}else{
				result.add(null);
			}
		}
		return result;
		
	}
	public List<String> getDistinctStrings(String key){
		return fetchDistinctValue(key);
	}
	public List<String> getNvlStrings(String key){
		List<String> result = new ArrayList<String>();
		List<Object> list = fetchValues(key);
		for(Object val:list){
			if(null != val){
				result.add(val.toString());
			}else{
				result.add("");
			}
		}
		return result;
	}
	public List<String> getStringsWithoutEmpty(String key){
		List<String> result = new ArrayList<String>();
		List<Object> list = fetchValues(key);
		for(Object val:list){
			if(BasicUtil.isNotEmpty(val)){
				result.add(val.toString());
			}
		}
		return result;
	}
	public List<String> getStringsWithoutNull(String key){
		List<String> result = new ArrayList<String>();
		List<Object> list = fetchValues(key);
		for(Object val:list){
			if(null != val){
				result.add(val.toString());
			}
		}
		return result;
	}
	public BigDecimal getDecimal(int idx, String key){
		BigDecimal result = null;
		DataRow row = getRow(idx);
		if (null != row)
			result = row.getDecimal(key);
		return result;
	}
	public BigDecimal getDecimal(int idx, String key, double def){
		return getDecimal(idx, key, new BigDecimal(def));
	}
	public BigDecimal getDecimal(int idx, String key, BigDecimal def){
		BigDecimal result =getDecimal(idx, key);
		if(null ==result){
			result = def;
		}
		return result;
	}

	/**
	 * html格式(未实现)
	 * 
	 * @param index
	 * @param key
	 * @return
	 */
	public String getHtmlString(int index, String key) {
		String result = getString(index, key);
		return result;
	}

	public String getHtmlString(String key) {
		return getHtmlString(0, key);
	}


	/**
	 * escape String
	 * 
	 * @param index
	 * @param key
	 * @return
	 */
	public String getEscapeString(int index, String key) {
		String result = getString(index, key);
		result = EscapeUtil.escape(result).toString();
		return result;
	}

	public String getDoubleEscapeString(int index, String key) {
		String result = getString(index, key);
		result = EscapeUtil.doubleEscape(result);
		return result;
	}

	public String getEscapeString(String key) {
		return getEscapeString(0, key);
	}

	public String getDoubleEscapeString(String key) {
		return getDoubleEscapeString(0, key);
	}

	/**
	 * int
	 * 
	 * @param index
	 * @param key
	 * @return
	 */
	public int getInt(int index, String key) {
		int result = 0;
		DataRow row = getRow(index);
		if (null != row)
			result = row.getInt(key);
		return result;
	}

	public int getInt(String key) {
		return getInt(0, key);
	}

	/**
	 * double
	 * 
	 * @param index
	 * @param key
	 * @return
	 */
	public double getDouble(int index, String key) {
		double result = 0;
		DataRow row = getRow(index);
		if (null != row)
			result = row.getDouble(key);
		return result;
	}

	public double getDouble(String key) {
		return getDouble(0, key);
	}
	/**
	 * rows 列表中的数据格式化成json格式   不同与toJSON
	 * map.put("type", "list");
    	map.put("result", result);
    	map.put("message", message);
    	map.put("rows", rows);
    	map.put("success", result);
    	map.put("navi", navi);
	 */
	public String toString() {
		Map<String,Object> map = new HashMap<String,Object>();
    	map.put("type", "list");
    	map.put("result", result);
    	map.put("message", message);
    	map.put("rows", rows);
    	map.put("success", result);
    	map.put("navi", navi);
    	JSON json = JSONObject.fromObject(map);
		return json.toString();
	}
	/**
	 * rows 列表中的数据格式化成json格式   不同与toString
	 * @return
	 */
	public String toJSON(){
		JsonConfig config = new JsonConfig();  
        config.registerJsonValueProcessor(Date.class, new JSONDateFormatProcessor());
        config.registerJsonValueProcessor(Timestamp.class, new JSONDateFormatProcessor());
		JSONArray json = JSONArray.fromObject(rows, config);
		return json.toString();
	}

	/**
	 * 子类
	 * 
	 * @return
	 */
	public Object getChildren(int idx) {
		DataRow row = getRow(idx);
		if (null != row) {
			return row.getChildren();
		}
		return null;
	}

	public Object getChildren() {
		return getChildren(0);
	}

	public void setChildren(int idx, Object children) {
		DataRow row = getRow(idx);
		if (null != row) {
			row.setChildren(children);
		}
	}

	public void setChildren(Object children) {
		setChildren(0, children);
	}

	/**
	 * 父类
	 * 
	 * @return
	 */
	public Object getParent(int idx) {
		DataRow row = getRow(idx);
		if (null != row) {
			return row.getParent();
		}
		return null;
	}

	public Object getParent() {
		return getParent(0);
	}

	public void setParent(int idx, Object parent) {
		DataRow row = getRow(idx);
		if (null != row) {
			row.setParent(parent);
		}
	}

	public void setParent(Object parent) {
		setParent(0, parent);
	}

	/**
	 * 转换成对象
	 * 
	 * @param clazz
	 * @return
	 */
	public <T> T entity(int index, Class<T> clazz) {
		DataRow row = getRow(index);
		if (null != row) {
			return row.entity(clazz);
		}
		return null;
	}

	/**
	 * 转换成对象集合
	 * 
	 * @param <T>
	 * @param clazz
	 * @return
	 */
	public <T> List<T> entity(Class<T> clazz) {
		List<T> list = new ArrayList<T>();
		if (null != rows) {
			for (DataRow row : rows) {
				list.add(row.entity(clazz));
			}
		}
		return list;
	}

	public <T> T entity(Class<T> clazz, int idx) {
		DataRow row = getRow(idx);
		if (null != row) {
			return row.entity(clazz);
		}
		return null;
	}

	public void setDataSource(String dataSource) {
		if (null == dataSource) {
			return;
		}
		this.dataSource = dataSource;
		if (dataSource.contains(".") && !dataSource.contains(":")) {
			schema = dataSource.substring(0, dataSource.indexOf("."));
			table = dataSource.substring(dataSource.indexOf(".") + 1);
		}
	}

	public DataSet union(DataSet set, String chkCol) {
		DataSet result = new DataSet();
		if (null != rows) {
			int size = rows.size();
			for (int i = 0; i < size; i++) {
				result.add(rows.get(i));
			}
		}
		if (null == chkCol) {
			chkCol = ConfigTable.getString("DEFAULT_PRIMARY_KEY");
		}
		int size = set.size();
		for (int i = 0; i < size; i++) {
			DataRow item = set.getRow(i);
			if (!result.contains(item, chkCol)) {
				result.add(item);
			}
		}
		return result;
	}

	public DataSet union(DataSet set) {
		return union(set, ConfigTable.getString("DEFAULT_PRIMARY_KEY"));
	}
	/**
	 * 合并
	 * @param set
	 * @return
	 */
	public DataSet unionAll(DataSet set) {
		DataSet result = new DataSet();
		if (null != rows) {
			int size = rows.size();
			for (int i = 0; i < size; i++) {
				result.add(rows.get(i));
			}
		}
		int size = set.size();
		for (int i = 0; i < size; i++) {
			DataRow item = set.getRow(i);
			result.add(item);
		}
		return result;
	}
	/**
	 * 是否包含这一行
	 * @param row
	 * @param keys
	 * @return
	 */
	public boolean contains(DataRow row, String ... keys) {
		if (null == rows || rows.size() == 0 || null == row) {
			return false;
		}
		if (null == keys) {
			keys = new String[1];
			keys[0] = ConfigTable.getString("DEFAULT_PRIMARY_KEY","ID");
		}
		String params[] = packParam(row, keys);
		return getRows(params).size() > 0;
	}
	
	/**
	 * 从items中按相应的key提取数据 存入
	 * @param field:默认"ITEMS"
	 * @param items
	 * @param keys
	 * @return
	 * dispatchItems("children",items, "DEPAT_CD")
	 * dispatchItems("children",items, "CD:BASE_CD")
	 */
	public DataSet dispatchItems(String field,DataSet items, String ... keys){
		if(null == items || null == keys || keys.length == 0){
			return this;
		}
		for(DataRow row : rows){
			String[] params = new String[keys.length*2];
			int idx = 0;
			for(String key:keys){

				String key1 = "";
				String key2 = "";
				if(key.contains(":")){
					String ks[] = key.split(":");
					key1 = ks[0];
					key2 = ks[1];
				}else{
					key1 = key;
					key2 = key;
				}
				params[idx++] = key2;
				params[idx++] = row.getString(key1);
			}
			if(BasicUtil.isEmpty(field)){
				field = "ITEMS";
			}
			row.put(field,items.getRows(params));
		}
		return this;
	}
	public DataSet dispatchItems(DataSet items, String ... keys){
		return dispatchItems("ITEMS",items, keys);
	}
	/**
	 * 按keys分组
	 * @param keys
	 * @return
	 */
	public DataSet group(String ... keys){
		DataSet result = distinct(keys);
		result.dispatchItems(this, keys);
		return result;
	}
	public DataSet or(DataSet set){
		return this.union(set);
	}
	/**
	 * 交集
	 * @param set
	 * @param keys
	 * @return
	 */
	public DataSet intersection(DataSet set, String ... keys){
		DataSet result = new DataSet();
		if(null == set){
			return result;
		}
		for(DataRow row:rows){
			if(set.contains(row, keys)){
				result.add((DataRow)row.clone());
			}
		}
		return result;
	}
	public DataSet and(DataSet set, String ... keys){
		return intersection(set, keys);
	}
	/**
	 * 差集
	 * 从当前集合中删除set中存在的row
	 * @param set
	 * @param keys CD,"CD:WORK_CD"
	 * @return
	 */
	public DataSet difference(DataSet set, String ... keys){
		DataSet result = new DataSet();
		for(DataRow row:rows){
			if(null == set || !set.contains(row, keys)){
				result.add((DataRow)row.clone());
			}
		}
		return result;
	}
	public String[] packParam(DataRow row, String ... keys){
		if(null == keys || null == row){
			return null;
		}
		String params[] = new String[keys.length*2];
		int idx = 0;
		for(String key:keys){
			if(null == key){
				continue;
			}
			String k1 = key;
			String k2 = key;
			if(key.contains(":")){
				String tmp[] = key.split(":");
				k1 = tmp[0];
				k2 = tmp[1];
			}
			params[idx++] = k1;
			params[idx++] = row.getString(k2);
		}
		return params;
	}
	/*********************************************** 实现接口 ************************************************************/
	public boolean add(DataRow e) {
		return rows.add((DataRow) e);
	}

	public boolean addAll(Collection c) {
		return rows.addAll(c);
	}

	public void clear() {
		rows.clear();
	}

	public boolean contains(Object o) {
		return rows.contains(o);
	}

	public boolean containsAll(Collection<?> c) {
		return rows.containsAll(c);
	}

	public Iterator iterator() {
		return rows.iterator();
	}

	public boolean remove(Object o) {
		return rows.remove(o);
	}

	public boolean removeAll(Collection<?> c) {
		return rows.removeAll(c);
	}

	public boolean retainAll(Collection<?> c) {
		return rows.retainAll(c);
	}

	public Object[] toArray() {
		return rows.toArray();
	}

	public Object[] toArray(Object[] a) {
		return rows.toArray(a);
	}

	public String getSchema() {
		return schema;
	}

	public void setSchema(String schema) {
		this.schema = schema;
	}

	public String getTable() {
		return table;
	}

	public void setTable(String table) {
		if(null != table && table.contains(".")){
			String[] tbs = table.split("\\.");
			this.table = tbs[1];
			this.schema = tbs[0];
		}else{
			this.table = table;
		}
	}
	/**
	 * 验证是否过期
	 * 根据当前时间与创建时间对比
	 * 过期返回 true
	 * @param millisecond	过期时间(毫秒)
	 * @return
	 */
	public boolean isExpire(int millisecond){
		if(System.currentTimeMillis() - createTime > millisecond){
			return true;
		}
		return false;
	}
	public boolean isExpire(long millisecond){
		if(System.currentTimeMillis() - createTime > millisecond){
			return true;
		}
		return false;
	}

	public boolean isExpire(){
		if(getExpires() == -1){
			return false;
		}
		if(System.currentTimeMillis() - createTime > getExpires()){
			return true;
		}
		return false;
	}
	public long getCreateTime() {
		return createTime;
	}
	public List<DataRow> getRows(){
		return rows;
	}

	/************************** getter setter ***************************************/

	/**
	 * 过期时间(毫秒)
	 * @return
	 */
	public long getExpires() {
		return expires;
	}
	public void setExpires(long millisecond) {
		this.expires = millisecond;
	}
	public void setExpires(int millisecond) {
		this.expires = millisecond;
	}
	public boolean isResult() {
		return result;
	}

	public boolean isSuccess() {
		return result;
	}

	public void setResult(boolean result) {
		this.result = result;
	}

	public Exception getException() {
		return exception;
	}

	public void setException(Exception exception) {
		this.exception = exception;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public PageNavi getNavi() {
		return navi;
	}

	public void setNavi(PageNavi navi) {
		this.navi = navi;
	}


	public void setRows(List<DataRow> rows) {
		this.rows = rows;
	}

	public String getDataSource() {
		String ds = table;
		if(BasicUtil.isNotEmpty(ds) && BasicUtil.isNotEmpty(schema)){
			ds = schema + "." + ds;
		}
		if(BasicUtil.isEmpty(ds)){
			ds = dataSource;
		}
		return ds;
	}
	public DataSet order(final String ... keys){
		return asc(keys);
	}
	public Object put(String key, Object value, boolean pk, boolean override){
		for(DataRow row:rows){
			row.put(key, value, pk, override);
		}
		return this;
	}
	public Object put(String key, Object value, boolean pk){
		for(DataRow row:rows){
			row.put(key, value, pk);
		}
		return this;
	}
	public Object put(String key, Object value){
		for(DataRow row:rows){
			row.put(key, value);
		}
		return this;
	}
	/**
	 * 排序
	 * @param keys
	 * @return
	 */
	public DataSet asc(final String ... keys){
		Collections.sort(rows, new Comparator<DataRow>() {  
            public int compare(DataRow r1, DataRow r2) {
            	int result = 0;
            	for(String key:keys){
            		Object v1 = r1.get(key);
            		Object v2 = r2.get(key);
            		if(null == v1){
            			if(null == v2){
            				continue;
            			}
            			return -1;
            		}else{
            			if(null == v2){
            				return 1;
            			}
            		}
            		if(BasicUtil.isNumber(v1) && BasicUtil.isNumber(v2)){
            			BigDecimal val1 = new BigDecimal(v1.toString());
            			BigDecimal val2 = new BigDecimal(v2.toString());
            			result = val1.compareTo(val2);
            		}else if((BasicUtil.isDate(v1) && BasicUtil.isDate(v2))
            				||(BasicUtil.isDateTime(v1) && BasicUtil.isDateTime(v2))
            				){
            			Date date1 = DateUtil.parse(v1.toString());
            			Date date2 = DateUtil.parse(v2.toString());
            			result = date1.compareTo(date2);
            		}else{
            			result = v1.toString().compareTo(v2.toString());
            		}
            		if(result != 0){
            			return result;
            		}
            	}
            	return 0;
            }  
        }); 
		isAsc = true;
		isDesc = false;
		return this;
	}
	public DataSet desc(final String ... keys){
		Collections.sort(rows, new Comparator<DataRow>() {  
			public int compare(DataRow r1, DataRow r2) {
            	int result = 0;
            	for(String key:keys){
            		Object v1 = r1.get(key);
            		Object v2 = r2.get(key);
            		if(null == v1){
            			if(null == v2){
            				continue;
            			}
            			return 1;
            		}else{
            			if(null == v2){
            				return -1;
            			}
            		}
            		if(BasicUtil.isNumber(v1) && BasicUtil.isNumber(v2)){
            			BigDecimal val1 = new BigDecimal(v1.toString());
            			BigDecimal val2 = new BigDecimal(v2.toString());
            			result = val2.compareTo(val1);
            		}else if((BasicUtil.isDate(v1) && BasicUtil.isDate(v2))
            				||(BasicUtil.isDateTime(v1) && BasicUtil.isDateTime(v2))
            				){
            			Date date1 = DateUtil.parse(v1.toString());
            			Date date2 = DateUtil.parse(v2.toString());
            			result = date2.compareTo(date1);
            		}else{
            			result = v2.toString().compareTo(v1.toString());
            		}
            		if(result != 0){
            			return result;
            		}
            	}
            	return 0;
            } 
        }); 
		isAsc = false;
		isDesc = true;
		return this;
	}

	private static String key(String key){
		if(null != key && ConfigTable.IS_UPPER_KEY){
			key = key.toUpperCase();
		}
		return key;
	}
	/************************** 类sql操作 ***************************************/
	/**
	 * @param key
	 * @param value
	 * @return
	 */
	public DataSet equals(String key, String value){
		DataSet set = new DataSet();
		String tmpValue;
		for(DataRow row:this){
			tmpValue = row.getString(key);
			if (null != tmpValue &&  tmpValue.equals(value)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet equalsIgnoreCase(String key, String value){
		DataSet set = new DataSet();
		String tmpValue;
		for(DataRow row:this){
			tmpValue = row.getString(key);
			if (null != tmpValue &&  tmpValue.equalsIgnoreCase(value)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet notEquals(String key, String value){
		DataSet set = new DataSet();
		String tmpValue;
		for(DataRow row:this){
			tmpValue = row.getString(key);
			if (null != tmpValue &&  !tmpValue.equals(value)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet notEqualsIgnoreCase(String key, String value){
		DataSet set = new DataSet();
		String tmpValue;
		for(DataRow row:this){
			tmpValue = row.getString(key);
			if (null != tmpValue &&  !tmpValue.equalsIgnoreCase(value)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	/**
	 * key列的值是否包含value
	 * @param key
	 * @param value
	 * @return
	 */
	public DataSet contains(String key, String value){
		DataSet set = new DataSet();
		String tmpValue;
		for(DataRow row:this){
			tmpValue = row.getString(key);
			if (null != tmpValue && tmpValue.contains(value)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	/**
	 * @param key
	 * @param value
	 * @return
	 */
	public DataSet like(String key, String pattern){
		DataSet set = new DataSet();
		if(null == pattern){
			return set;
		}
		pattern = pattern.replace("!", "^").replace("_", "\\s|\\S").replace("%", "(\\s|\\S)*");
		String tmpValue;
		for(DataRow row:this){
			tmpValue = row.getString(key);
			if (null != tmpValue && RegularUtil.match(tmpValue, pattern, RegularUtil.MATCH_MODE_MATCH)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet startWith(String key, String prefix){
		DataSet set = new DataSet();
		String tmpValue;
		for(DataRow row:this){
			tmpValue = row.getString(key);
			if (null != tmpValue && tmpValue.startsWith(prefix)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet endWith(String key, String suffix){
		DataSet set = new DataSet();
		String tmpValue;
		for(DataRow row:this){
			tmpValue = row.getString(key);
			if (null != tmpValue && tmpValue.endsWith(suffix)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet in(String key, Object ... values){
		DataSet set = new DataSet();
		Object tmpValue;
		for(DataRow row:this){
			tmpValue = row.get(key);
			if (null != tmpValue && BasicUtil.contains(values, tmpValue)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet in(String key, Collection<Object> values){
		DataSet set = new DataSet();
		Object tmpValue;
		for(DataRow row:this){
			tmpValue = row.get(key);
			if (null != tmpValue && BasicUtil.contains(values, tmpValue)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet inIgnoreCase(String key, Object ... values){
		DataSet set = new DataSet();
		Object tmpValue;
		for(DataRow row:this){
			tmpValue = row.get(key);
			if (null != tmpValue && BasicUtil.containsIgnoreCase(values, tmpValue)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet inIgnoreCase(String key, Collection<Object> values){
		DataSet set = new DataSet();
		Object tmpValue;
		for(DataRow row:this){
			tmpValue = row.get(key);
			if (null != tmpValue && BasicUtil.containsIgnoreCase(values, tmpValue)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet notIn(String key, Object ... values){
		DataSet set = new DataSet();
		Object tmpValue;
		for(DataRow row:this){
			tmpValue = row.get(key);
			if (null != tmpValue && !BasicUtil.contains(values, tmpValue)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet notIn(String key, Collection<Object> values){
		DataSet set = new DataSet();
		Object tmpValue;
		for(DataRow row:this){
			tmpValue = row.get(key);
			if (null != tmpValue && !BasicUtil.contains(values, tmpValue)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet notInIgnoreCase(String key, Object ... values){
		DataSet set = new DataSet();
		Object tmpValue;
		for(DataRow row:this){
			tmpValue = row.get(key);
			if (null != tmpValue && !BasicUtil.containsIgnoreCase(values, tmpValue)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet notInIgnoreCase(String key, Collection<Object> values){
		DataSet set = new DataSet();
		Object tmpValue;
		for(DataRow row:this){
			tmpValue = row.get(key);
			if (null != tmpValue && !BasicUtil.containsIgnoreCase(values, tmpValue)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet isNull(String key){
		DataSet set = new DataSet();
		for(DataRow row:this){
			if (null == row.get(key)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet isNull(String ... keys){
		DataSet set = this;
		if(null != keys){
			for(String key:keys){
				set = isNull(key);
			}
		}
		return set;
	}
	public DataSet isNotNull(String key){
		DataSet set = new DataSet();
		for(DataRow row:this){
			if (null != row.get(key)) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	
	public DataSet notNull(String key){
		return isNotNull(key);
	}
	public DataSet isNotNull(String ... keys){
		DataSet set = this;
		if(null != keys){
			for(String key:keys){
				set = isNotNull(key);
			}
		}
		return set;
	}
	public DataSet notNull(String ... keys){
		return isNotNull(keys);
	}
	public DataSet isEmpty(String key){
		DataSet set = new DataSet();
		for(DataRow row:this){
			if (BasicUtil.isEmpty(row.get(key))) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet empty(String keys){
		return isEmpty(keys);
	}
	public DataSet isEmpty(String ... keys){
		DataSet set = this;
		if(null != keys){
			for(String key:keys){
				set = isEmpty(key);
			}
		}
		return set;
	}
	public DataSet empty(String ... keys){
		return isEmpty(keys);
	}
	public DataSet isNotEmpty(String key){
		DataSet set = new DataSet();
		for(DataRow row:this){
			if (BasicUtil.isNotEmpty(row.get(key))) {
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet notEmpty(String key){
		return isNotEmpty(key);
	}
	public DataSet isNotEmpty(String ... keys){
		DataSet set = this;
		if(null != keys){
			for(String key:keys){
				set = isNotEmpty(key);
			}
		}
		return set;
	}
	public DataSet notEmpty(String ... keys){
		return isNotEmpty(keys);
	}
	public DataSet less(String key, Object value){
		DataSet set = new DataSet();
		if(null == value){
			return set;
		}
		if(BasicUtil.isNumber(value)){
			BigDecimal number = new BigDecimal(value.toString());
			for(DataRow row:this){
				if(row.getDecimal(key).compareTo(number) >= 0){
					set.add(row);
				}
			}
		}else if(BasicUtil.isDate(value) || BasicUtil.isDateTime(value)){
			Date date = DateUtil.parse(value.toString());
			for(DataRow row:this){
				if(row.isNotEmpty(key) && 
						DateUtil.diff(DateUtil.DATE_PART_MILLISECOND, date, row.getDate(key,new Date())) < 0){
					set.add(row);
				}
			}
		}else{
			for(DataRow row:this){
				if(row.getString(key).compareTo(value.toString()) >= 0){
					set.add(row);
				}
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet lessEqual(String key, Object value){
		DataSet set = new DataSet();
		if(null == value){
			return set;
		}
		if(BasicUtil.isNumber(value)){
			BigDecimal number = new BigDecimal(value.toString());
			for(DataRow row:this){
				if(row.getDecimal(key).compareTo(number) >= 0){
					set.add(row);
				}
			}
		}else if(BasicUtil.isDate(value) || BasicUtil.isDateTime(value)){
			Date date = DateUtil.parse(value.toString());
			for(DataRow row:this){
				if(row.isNotEmpty(key) && 
						DateUtil.diff(DateUtil.DATE_PART_MILLISECOND, date, row.getDate(key,new Date())) <= 0){
					set.add(row);
				}
			}
		}else{
			for(DataRow row:this){
				if(row.getString(key).compareTo(value.toString()) >= 0){
					set.add(row);
				}
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet greater(String key, Object value){
		DataSet set = new DataSet();
		if(null == value){
			return set;
		}
		if(BasicUtil.isNumber(value)){
			BigDecimal number = new BigDecimal(value.toString());
			for(DataRow row:this){
				if(row.getDecimal(key).compareTo(number) >= 0){
					set.add(row);
				}
			}
		}else if(BasicUtil.isDate(value) || BasicUtil.isDateTime(value)){
			Date date = DateUtil.parse(value.toString());
			for(DataRow row:this){
				if(row.isNotEmpty(key) && 
						DateUtil.diff(DateUtil.DATE_PART_MILLISECOND, date, row.getDate(key,new Date())) > 0){
					set.add(row);
				}
			}
		}else{
			for(DataRow row:this){
				if(row.getString(key).compareTo(value.toString()) >= 0){
					set.add(row);
				}
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet greaterEqual(String key, Object value){
		DataSet set = new DataSet();
		if(null == value){
			return set;
		}
		if(BasicUtil.isNumber(value)){
			BigDecimal number = new BigDecimal(value.toString());
			for(DataRow row:this){
				if(row.getDecimal(key).compareTo(number) >= 0){
					set.add(row);
				}
			}
		}else if(BasicUtil.isDate(value) || BasicUtil.isDateTime(value)){
			Date date = DateUtil.parse(value.toString());
			for(DataRow row:this){
				if(row.isNotEmpty(key) && 
						DateUtil.diff(DateUtil.DATE_PART_MILLISECOND, date, row.getDate(key,new Date())) >= 0){
					set.add(row);
				}
			}
		}else{
			for(DataRow row:this){
				if(row.getString(key).compareTo(value.toString()) >= 0){
					set.add(row);
				}
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet between(String key, Object min, Object max){
		return greaterEqual(key, min).lessEqual(key, max);
		
	}
	public DataRow random(){
		DataRow row = null;
		int size = size();
		if(size > 0){
			row = getRow(BasicUtil.getRandomNumber(0, size-1));
		}
		return row;
	}
	public DataSet randoms(int qty){
		DataSet set = new DataSet();
		int size = size();
		if(qty <0){
			qty = 0;
		}
		if(qty > size){
			qty = size;
		}
		for(int i=0; i<qty; i++){
			while(true){
				int idx = BasicUtil.getRandomNumber(0, size-1);
				DataRow row = set.getRow(idx);
				if(!set.contains(row)){
					set.add(row);
					break;
				}
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet randoms(int min, int max){
		int qty = BasicUtil.getRandomNumber(min, max);
		return randoms(qty);
	}
	public DataSet unique(String ... keys){
		return distinct(keys);
	}
	public DataSet regex(String key, String regex, int mode){
		DataSet set = new DataSet();
		String tmpValue;
		for(DataRow row:this){
			tmpValue = row.getString(key);
			if(RegularUtil.match(tmpValue, regex, mode)){
				set.add(row);
			}
		}
		set.cloneProperty(this);
		return set;
	}
	public DataSet regex(String key, String regex){
		return regex(key, regex, RegularUtil.MATCH_MODE_MATCH);
	}
}

