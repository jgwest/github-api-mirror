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

import java.util.Date;

import com.githubapimirror.shared.json.IssueCommentJson;

/**
 * This class represents a comment on an issue, received from the GitHub mirror
 * API.
 */
public class GHIssueComment {

	private final GHUser user;

	private final IssueCommentJson json;

	public GHIssueComment(IssueCommentJson json, GHConnectInfo connInfo) {

		this.json = json;

		user = connInfo.getUserByLogin(json.getUserLogin());

	}

	public GHUser getUser() {
		return user;
	}

	public String getUserLogin() {
		return json.getUserLogin();
	}

	public String getBody() {
		return json.getBody();
	}

	public Date getCreatedAt() {
		return json.getCreatedAt();
	}

	public Date getUpdatedAt() {
		return json.getUpdatedAt();
	}

}
