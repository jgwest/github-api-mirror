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
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.githubapimirror.client.api.events.GHIssueEvent;
import com.githubapimirror.client.api.events.GHIssueEventAssignedUnassigned;
import com.githubapimirror.client.api.events.GHIssueEventClosed;
import com.githubapimirror.client.api.events.GHIssueEventLabeledUnlabeled;
import com.githubapimirror.client.api.events.GHIssueEventMerged;
import com.githubapimirror.client.api.events.GHIssueEventRenamed;
import com.githubapimirror.client.api.events.GHIssueEventReopened;
import com.githubapimirror.shared.json.IssueCommentJson;
import com.githubapimirror.shared.json.IssueEventJson;
import com.githubapimirror.shared.json.IssueJson;

/** This class represents the result of querying the GitHub mirror Issue API. */
public class GHIssue {

	private final GHConnectInfo connInfo;

	private final IssueJson json;

	private final List<GHIssueEvent> issueEvents;

	public GHIssue(IssueJson jsonIssue, GHConnectInfo connInfo) {
		this.connInfo = connInfo;
		this.json = jsonIssue;

		List<GHIssueEvent> events = new ArrayList<>();

		for (IssueEventJson iej : json.getIssueEvents()) {

			String type = iej.getType();

			if (type.equals("labeled") || type.equals("unlabeled")) {

				GHIssueEventLabeledUnlabeled ld = new GHIssueEventLabeledUnlabeled(iej);
				events.add(ld);

			} else if (type.equals("assigned") || type.equals("unassigned")) {

				GHIssueEventAssignedUnassigned au = new GHIssueEventAssignedUnassigned(iej);
				events.add(au);

			} else if (type.equals("renamed")) {

				GHIssueEventRenamed r = new GHIssueEventRenamed(iej);
				events.add(r);

			} else if (type.equals("reopened")) {

				GHIssueEventReopened r = new GHIssueEventReopened(iej);
				events.add(r);

			} else if (type.equals("merged")) {

				GHIssueEventMerged m = new GHIssueEventMerged(iej);
				events.add(m);

			} else if (type.equals("closed")) {

				GHIssueEventClosed c = new GHIssueEventClosed(iej);
				events.add(c);

			} else {
				System.err.println("Unrecognized event type " + iej.getType());
			}

		}

		this.issueEvents = Collections.unmodifiableList(events);

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

	public boolean isClosed() {
		return json.isClosed();
	}

	public List<GHIssueEvent> getIssueEvents() {
		return issueEvents;
	}
}
