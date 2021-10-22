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

package com.githubapimirror.tests;

import java.util.ArrayList;
import java.util.List;

import com.githubapimirror.GhmFilter;
import com.githubapimirror.shared.Owner;

/** Only process GitHub resources that are contained within a test pair */
public class TestFilter implements GhmFilter {

	private final List<TFTestPair> pairs = new ArrayList<>();

	public TestFilter() {
	}

	public void addTestPair(TFTestPair pair) {
		pairs.add(pair);
	}

	@Override
	public boolean processOwner(Owner owner) {		
		return pairs.stream().anyMatch(e -> e.owner.equals(owner));
	}

	@Override
	public boolean processRepo(Owner owner, String repoName) {
		return pairs.stream().anyMatch(e -> e.owner.equals(owner) && e.repo.contentEquals(repoName));

	}

	@Override
	public boolean processIssue(Owner owner, String repoName, int issue) {
		return pairs.stream().anyMatch(e -> e.owner.equals(owner) && e.repo.contentEquals(repoName)
				&& e.issueStart >= issue && e.issueEnd <= issue);
	}

	@Override
	public boolean processIssueEvents(Owner owner, String repoName, int issue) {
		TFTestPair pair = pairs.stream().filter(e -> e.owner.equals(owner) && e.repo.contentEquals(repoName)
				&& e.issueStart >= issue && e.issueEnd <= issue).findFirst().orElse(null);

		if (pair == null) {
			return false;
		}

		return pair.processEvents;

	}

	@Override
	public boolean processUser(String loginName) {
		return true;
	}

	/** Defines an inclusive range on a GitHub resource. */
	public static class TFTestPair {
		private final Owner owner;
		private final String repo;
		private final int issueStart;
		private final int issueEnd;
		private final boolean processEvents;

		public TFTestPair(Owner owner, String repo, int issue, boolean processEvents) {
			this(owner, repo, issue, issue, processEvents);
		}

		public TFTestPair(Owner owner, String repo, int issueStart, int issueEnd, boolean processEvents) {
			this.owner = owner;
			this.repo = repo;
			this.issueStart = issueStart;
			this.issueEnd = issueEnd;
			this.processEvents = processEvents;
		}

	}

}
