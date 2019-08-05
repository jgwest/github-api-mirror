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
import java.util.Date;
import java.util.List;

import com.githubapimirror.shared.json.IssueCommentJson;
import com.githubapimirror.shared.json.IssueJson;

/** This class represents the result of querying the GitHub mirror Issue API. */
public class GHIssue {

	private final GHConnectInfo connInfo;

	private final IssueJson json;

	public GHIssue(IssueJson jsonIssue, GHConnectInfo connInfo) {
		this.connInfo = connInfo;
		this.json = jsonIssue;
	}

	public List<GHUser> getAssignees() {

		List<GHUser> result = new ArrayList<>();

		for (String assignee : json.getAssignees()) {

			GHUser user = connInfo.getUserByLogin(assignee);
			if (user != null) {
				result.add(user);
			}

		}

		return result;
	}

	public Date getCreatedAt() {
		return json.getCreatedAt();
	}

	public List<String> getLabels() {
		return json.getLabels();
	}

	public Date getClosedAt() {
		return json.getClosedAt();
	}

	public int getNumber() {
		return json.getNumber();
	}

	public String getTitle() {
		return json.getTitle();
	}

	public String getHtmlUrl() {
		return json.getHtmlUrl();
	}

	public String getBody() {
		return json.getBody();
	}

	public List<GHIssueComment> getComments() {

		List<GHIssueComment> result = new ArrayList<>();

		for (IssueCommentJson json : json.getComments()) {

			result.add(new GHIssueComment(json, connInfo));
		}

		return result;
	}

	public boolean isPullRequest() {
		return json.isPullRequest();
	}

	public GHUser getReporter() {
		return connInfo.getUserByLogin(json.getReporter());
	}

}
