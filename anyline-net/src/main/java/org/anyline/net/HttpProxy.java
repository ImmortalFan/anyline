/* 
 * Copyright 2006-2020 www.anyline.org
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
package org.anyline.net; 
 
public  
class HttpProxy{ 
	private String host; 
	private int port; 
	private String user; 
	private String password; 

	public HttpProxy(){
	}

	public HttpProxy(String host, int port, String user, String password){
	}
 
	public String getHost() { 
		return host; 
	} 
 
	public void setHost(String host) { 
		this.host = host; 
	} 
 
	public int getPort() { 
		return port; 
	} 
 
	public void setPort(int port) { 
		this.port = port; 
	} 
 
	public String getUser() { 
		return user; 
	} 
 
	public void setUser(String user) { 
		this.user = user; 
	} 
 
	public String getPassword() { 
		return password; 
	} 
 
	public void setPassword(String password) { 
		this.password = password; 
	} 
	 
}
