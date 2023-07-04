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

import com.githubapimirror.GHLog;
import com.githubapimirror.GhmFilter;
import com.githubapimirror.shared.Owner;

/** Only process GitHub resources that are contained within a test pair */
public class TestFilter implements GhmFilter {

	private static final GHLog log = GHLog.getInstance();

	private static final boolean DEBUG = false;

	private final List<TFTestPair> pairs = new ArrayList<>();

	public TestFilter() {
	}

	public void addTestPair(TFTestPair pair) {
		pairs.add(pair);
	}

	private static void logDebug(String type, Owner owner, String text, boolean result) {
		if (DEBUG) {
			log.logDebug(TestFilter.class.getName() + ": [" + type + "] " + owner + (" " + text).trim() + " " + result);
		}
	}

	@Override
	public boolean processOwner(Owner owner) {
		boolean result = pairs.stream().anyMatch(e -> e.owner.equals(owner));

		logDebug("owner", owner, "", result);
		return result;
	}

	@Override
	public boolean processRepo(Owner owner, String repoName) {
		boolean result = pairs.stream().anyMatch(e -> e.owner.equals(owner) && e.repo.contentEquals(repoName));

		logDebug("repo", owner, repoName, result);
		return result;

	}

	@Override
	public boolean processIssue(Owner owner, String repoName, int issue) {
		boolean result = pairs.stream().anyMatch(e -> e.owner.equals(owner) && e.repo.contentEquals(repoName)
				&& e.issueStart >= issue && e.issueEnd <= issue);
		logDebug("issue", owner, repoName + "->" + issue, result);
		return result;
	}

	@Override
	public boolean processIssueEvents(Owner owner, String repoName, int issue) {
		boolean result = false;

		TFTestPair pair = pairs.stream().filter(e -> e.owner.equals(owner) && e.repo.contentEquals(repoName)
				&& e.issueStart >= issue && e.issueEnd <= issue).findFirst().orElse(null);

		if (pair != null) {
			result = pair.processEvents;
		}

		logDebug("issueEvents", owner, repoName + "->" + issue, result);

		return result;

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
