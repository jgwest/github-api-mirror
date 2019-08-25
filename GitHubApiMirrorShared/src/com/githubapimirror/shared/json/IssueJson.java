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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** JSON Issue response from the GitHub mirror server. */
public class IssueJson {

	private String parentRepo;

	private Date createdAt;
	private Date closedAt;

	private List<String> labels = new ArrayList<>();

	private List<String> assignees = new ArrayList<>();

	private String title;

	private String htmlUrl;

	private Integer number;

	private String body;

	private boolean isPullRequest;

	private boolean isClosed;

	private List<IssueCommentJson> comments = new ArrayList<>();

	private String reporter;

	private List<IssueEventJson> issueEvents = new ArrayList<>();

	public IssueJson() {
	}

	public Integer getNumber() {
		return number;
	}

	public void setNumber(Integer number) {
		this.number = number;
	}

	public String getParentRepo() {
		return parentRepo;
	}

	public void setParentRepo(String parentRepo) {
		this.parentRepo = parentRepo;
	}

	public Date getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
	}

	public Date getClosedAt() {
		return closedAt;
	}

	public void setClosedAt(Date closedAt) {
		this.closedAt = closedAt;
	}

	public List<String> getLabels() {
		return labels;
	}

	public void setLabels(List<String> labels) {
		this.labels = labels;
	}

	public List<String> getAssignees() {
		return assignees;
	}

	public void setAssignees(List<String> assignees) {
		this.assignees = assignees;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getHtmlUrl() {
		return htmlUrl;
	}

	public void setHtmlUrl(String htmlUrl) {
		this.htmlUrl = htmlUrl;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public List<IssueCommentJson> getComments() {
		return comments;
	}

	public void setComments(List<IssueCommentJson> comments) {
		this.comments = comments;
	}

	public boolean isPullRequest() {
		return isPullRequest;
	}

	public void setPullRequest(boolean isPullRequest) {
		this.isPullRequest = isPullRequest;
	}

	public void setReporter(String reporter) {
		this.reporter = reporter;
	}

	public String getReporter() {
		return reporter;
	}

	public List<IssueEventJson> getIssueEvents() {
		return issueEvents;
	}

	public void setIssueEvents(List<IssueEventJson> issueEvents) {
		this.issueEvents = issueEvents;
	}

	public boolean isClosed() {
		return isClosed;
	}

	public void setClosed(boolean isClosed) {
		this.isClosed = isClosed;
	}

}
