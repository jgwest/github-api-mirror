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

import java.util.Map;

/** JSON Issue event response from the GitHub mirror server. */
public class IssueEventAssignedUnassignedJson {

	String assignee;
	String assigner;

	boolean assigned;

	public IssueEventAssignedUnassignedJson() {
	}

	public IssueEventAssignedUnassignedJson(Map<Object, Object> mapParam) {

		this.assignee = (String) mapParam.getOrDefault("assignee", null);
		this.assigner = (String) mapParam.getOrDefault("assigner", null);
		this.assigned = (boolean) mapParam.get("assigned");

	}

	public void setAssigned(boolean assigned) {
		this.assigned = assigned;
	}

	public boolean isAssigned() {
		return assigned;
	}

	public String getAssignee() {
		return assignee;
	}

	public void setAssignee(String assignee) {
		this.assignee = assignee;
	}

	public String getAssigner() {
		return assigner;
	}

	public void setAssigner(String assigner) {
		this.assigner = assigner;
	}

}
