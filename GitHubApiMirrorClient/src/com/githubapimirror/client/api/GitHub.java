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

import com.githubapimirror.shared.json.OrganizationJson;
import com.githubapimirror.shared.json.UserRepositoriesJson;

/**
 * A GitHub repository may have either an organization as a parent
 * (github.com/golang/repo), or a user repository as a parent
 * (github.com/username/repo). This class allows you to retrieve either.
 */

public class GitHub {

	private final GHConnectInfo connectionInfo;

	public GitHub(GHConnectInfo connectionInfo) {
		this.connectionInfo = connectionInfo;
	}

	public GHOrganization getOrganization(String str) {

		OrganizationJson json = connectionInfo.getClient().getOrganization(str).orElse(null);

		if (json == null) {
			return null;
		}

		GHOrganization result = new GHOrganization(json, connectionInfo);

		return result;

	}

	public GHUserRepositories getUserRepositories(String name) {

		UserRepositoriesJson json = connectionInfo.getClient().getUserRepositories(name).orElse(null);

		if (json == null) {
			return null;
		}

		GHUserRepositories result = new GHUserRepositories(json, connectionInfo);

		return result;

	}

	public GHConnectInfo getConnectionInfo() {
		return connectionInfo;
	}

}
