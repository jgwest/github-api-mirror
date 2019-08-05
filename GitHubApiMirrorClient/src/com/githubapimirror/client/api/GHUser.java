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

package com.githubapimirror.client.api;

import com.githubapimirror.shared.json.UserJson;

/** Represents a GitHub user. */
public class GHUser {

	private final String login;
	private final String name;
	private final String email;

	public GHUser(UserJson userJson, GHConnectInfo connInfo) {
		this.login = userJson.getLogin();
		this.email = userJson.getEmail();
		this.name = userJson.getName();
	}

	public String getLogin() {
		return login;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

}
