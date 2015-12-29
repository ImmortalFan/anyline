
package org.anyline.config.db.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

import org.anyline.config.db.Condition;
import org.anyline.config.db.ConditionChain;
import org.anyline.config.db.Order;
import org.anyline.config.db.OrderStore;
import org.anyline.config.db.PageNavi;
import org.anyline.config.db.SQL;
import org.anyline.config.db.SQLVariable;
import org.anyline.util.BasicUtil;
import org.anyline.util.ConfigTable;
import org.anyline.util.Constant;
import org.apache.log4j.Logger;

public abstract class BasicSQL implements SQL{

	protected Logger log = Logger.getLogger(this.getClass());


	protected ConditionChain chain;			//查询条件
	protected OrderStore orders;
	protected List<String> groups;			//分组条件
	protected PageNavi navi;				//分页
	//运行时参数值
	protected Vector<Object> runValues;
	public int getVersion(){
		return ConfigTable.getInt("DATABASE_VERSION",Constant.DATABASE_VERSION);
	}

	public BasicSQL(){
	}
	protected void initRunValues(){
		if(null == runValues){
			runValues = new Vector<Object>();
		}else{
			runValues.clear();
		}
	}
	

	/**
	 * 添加排序条件,在之前的基础上添加新排序条件,有重复条件则覆盖
	 * @param order
	 * @return
	 */
	public SQL order(Order order){
		if(null == orders){
			orders = new OrderStoreImpl();
		}
		orders.order(order);
		return this;
	}
	
	public SQL order(String order){
		if(null == orders){
			orders = new OrderStoreImpl();
		}
		orders.order(order);
		return this;
	}
	public SQL order(String col, String type){
		if(null == orders){
			orders = new OrderStoreImpl();
		}
		orders.order(col, type);
		return this;
	}

	protected String getOrderText(String disKey){
		if(null != orders){
			return orders.getRunText(disKey);
		}
		return "";
	}
	/**
	 * 添加分组条件,在之前的基础上添加新分组条件,有重复条件则覆盖
	 * @param group
	 * @return
	 */
	public SQL group(String group){
		/*避免添加空条件*/
		if(BasicUtil.isEmpty(group)){
			return this;
		}
		
		if(null == groups){
			groups = new ArrayList<String>();
		}

		group = group.trim().toUpperCase();

		
		/*添加新分组条件*/
		if(!groups.contains(group)){
			groups.add(group);
		}
		
		return this;
	}
	/**
	 * 添加运行时参数值
	 * @param runValue
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected SQL addRunValue(Object runValue){
		if(null == runValues){
			runValues = new Vector<Object>();
		}
		if(runValue != null && runValue instanceof Collection){
			Collection collection = (Collection)runValue;
			runValues.addAll(collection);
		}else{
			runValues.add(runValue);
		}
		return this;
	}
	/**
	 * 运行时参数值
	 */
	public List<Object> getRunValues() {
		return runValues;
	}
	
	public SQL setConditionChain(ConditionChain chain){
		this.chain = chain;
		return this;
	}
	/**
	 * 添加标准查询条件
	 */
	public SQL addCondition(Condition condition) {
		chain.addCondition(condition);
		return this;
	}

	
	/**
	 * 设置查询条件变量值(XML定义)
	 * @param	condition		
	 * 			条件ID
	 * @param	variable		
	 * 			变量key
	 * @param	values
	 * 			值
	 */
	public SQL setConditionValue(String condition, String variable, Object value) {
		return this;
	}
	/**
	 * 添加查询条件(自动生成)
	 * @param	column
	 * 			列名
	 * @param	value
	 * 			值
	 * @param	compare
	 * 			比较方式
	 */
	public SQL addCondition(String column, Object value, int compare) {
		return this;
	}




	@Override
	public SQL setText(String text) {
		return this;
	}
	
	public void setPageNavi(PageNavi navi){
		this.navi = navi;
	}
	public PageNavi getPageNavi(){
		return navi;
	}

	public OrderStore getOrders() {
		return orders;
	}

	public void setOrders(OrderStore orders) {
		if(null != orders){
			List<Order> list = orders.getOrders();
			for(Order order:list){
				this.order(order);
			}
			for(Order order:orders.getOrders()){				
				orders.order(order);
			}
		}else{
			orders = this.orders;
		}
	}
	
	public ConditionChain getConditionChain(){
		return this.chain;
	}
	
}
