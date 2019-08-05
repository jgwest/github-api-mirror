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

import com.githubapimirror.shared.Owner;

/** JSON repository response from the GitHub mirror server. */
public class RepositoryJson {

	private String orgName;

	private String ownerUserName;

	private String name;

	private Integer firstIssue = null;

	private Integer lastIssue = null;

	private long repositoryId;

	public RepositoryJson() {
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getFirstIssue() {
		return firstIssue;
	}

	public void setFirstIssue(Integer firstIssue) {
		this.firstIssue = firstIssue;
	}

	public Integer getLastIssue() {
		return lastIssue;
	}

	public void setLastIssue(Integer lastIssue) {
		this.lastIssue = lastIssue;
	}

	public String getOrgName() {
		return orgName;
	}

	public void setOrgName(String orgName) {
		this.orgName = orgName;
	}

	public String getOwnerUserName() {
		return ownerUserName;
	}

	public void setOwnerUserName(String ownerUserName) {
		this.ownerUserName = ownerUserName;
	}

	public long getRepositoryId() {
		return repositoryId;
	}

	public void setRepositoryId(long l) {
		this.repositoryId = l;
	}

	public Owner calculateOwner() {
		if (this.orgName != null) {
			return Owner.org(this.orgName);
		} else {
			return Owner.user(this.ownerUserName);
		}
	}
}
