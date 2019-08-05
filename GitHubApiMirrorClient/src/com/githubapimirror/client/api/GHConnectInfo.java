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

import java.util.HashMap;
import java.util.Map;

import com.githubapimirror.client.GHApiMirrorHttpClient;
import com.githubapimirror.shared.json.UserJson;

/**
 * This class maintains the connection info for a GitHub mirror server, and also
 * maintains a cache of users by login from that server.
 */
public class GHConnectInfo {

	private final String resourceUrl;
	private final String authId;

	private final GHApiMirrorHttpClient rsClient;

	private final Map<String, GHUser> userCache_synch = new HashMap<String, GHUser>();

	public GHConnectInfo(String resourceUrl, String authId) {
		this.resourceUrl = resourceUrl;
		this.authId = authId;
		this.rsClient = new GHApiMirrorHttpClient(resourceUrl, authId);
	}

	public GHUser getUserByLogin(String login) {

		synchronized (userCache_synch) {
			GHUser result = userCache_synch.get(login);
			if (result != null) {
				return result;
			}
		}

		GHUser result = null;
		UserJson user = rsClient.getUser(login).orElse(null);
		if (user != null) {

			result = new GHUser(user, this);

			synchronized (userCache_synch) {
				userCache_synch.put(login, result);
			}

		}

		return result;

	}

	public String getResourceUrl() {
		return resourceUrl;
	}

	public String getAuthId() {
		return authId;
	}

	protected GHApiMirrorHttpClient getClient() {
		return rsClient;
	}
}
