package org.anyline.aliyun.sms.util; 
 
public class SMSResult { 
	private boolean result; 
	private String code; 
	private String msg;
	private String biz;	//发送回执ID,即发送流水号,查询送达状态时用到

	public boolean isResult() { 
		return result; 
	} 
	public void setResult(boolean result) { 
		this.result = result; 
	} 
	public String getMsg() { 
		return msg; 
	} 
	public void setMsg(String msg) { 
		this.msg = msg; 
	} 
	public String getCode() { 
		return code; 
	} 
	public void setCode(String code) { 
		this.code = code; 
	}

	public String getBiz() {
		return biz;
	}

	public void setBiz(String biz) {
		this.biz = biz;
	}
}
