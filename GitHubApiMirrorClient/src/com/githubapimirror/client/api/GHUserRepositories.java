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

import java.util.ArrayList;
import java.util.List;

import com.githubapimirror.client.GHApiMirrorHttpClient;
import com.githubapimirror.shared.Owner;
import com.githubapimirror.shared.json.RepositoryJson;
import com.githubapimirror.shared.json.UserRepositoriesJson;

/**
 * A GitHub repository may have either an organization as a parent
 * (github.com/golang/repo), or a user repository as a parent
 * (github.com/username/repo). This class represents the latter.
 */
public class GHUserRepositories {

	private final GHConnectInfo connection;

	private final String name;

	public GHUserRepositories(UserRepositoriesJson json, GHConnectInfo connection) {
		this.connection = connection;
		this.name = json.getUserName();
	}

	public List<GHRepository> getRepositories() {

		GHApiMirrorHttpClient client = connection.getClient();

		UserRepositoriesJson urj = client.getUserRepositories(name).orElse(null);
		if (urj == null) {
			return null;
		}

		List<GHRepository> result = new ArrayList<>();

		Owner owner = Owner.user(name);

		for (String repository : urj.getRepoNames()) {
			RepositoryJson json = client.getRepository(owner, repository).orElse(null);
			if (json == null) {
				System.err.println("Could not find: " + repository);
			} else {
				GHRepository repo = new GHRepository(json, connection);
				result.add(repo);
			}
		}

		return result;
	}

	public GHConnectInfo getConnection() {
		return connection;
	}

}
