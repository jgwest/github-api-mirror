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

package com.githubapimirror.client.api.events;

import java.util.Map;

import com.githubapimirror.shared.json.IssueEventJson;
import com.githubapimirror.shared.json.IssueEventLabeledUnlabeledJson;

public class GHIssueEventLabeledUnlabeled extends GHIssueEvent {

	private final IssueEventLabeledUnlabeledJson labelData;

	@SuppressWarnings("unchecked")
	public GHIssueEventLabeledUnlabeled(IssueEventJson json) {
		super(json);
		labelData = new IssueEventLabeledUnlabeledJson((Map<Object, Object>) json.getData());
	}

	public String getLabel() {
		return labelData.getLabel();
	}

	public boolean isLabeled() {
		return labelData.isLabeled();
	}

}
