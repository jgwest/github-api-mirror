/*
 * Copyright 2019 Jonathan West
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
*/

package com.githubapimirror.shared.json;

import java.util.Date;

/** JSON Issue event response from the GitHub mirror server. */
public class IssueEventJson {

	String type;

	Date createdAt;

	String actorUserLogin;

	Object data;

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Date getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
	}

	public String getActorUserLogin() {
		return actorUserLogin;
	}

	public void setActorUserLogin(String actorUserLogin) {
		this.actorUserLogin = actorUserLogin;
	}

	public Object getData() {
		return data;
	}

	public void setData(Object data) {
		this.data = data;
	}

	@Override
	public String toString() {
		return "IssueEventJson [type=" + type + ", createdAt=" + createdAt + ", actorUserLogin=" + actorUserLogin
				+ ", data=" + data + "]";
	}

}
