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

package com.githubapimirror;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterator;

/**
 * Cache orgs, users, repos, and issues, for the lifetime of this object. In
 * order to prevent receiving stale data, this cache should have a limited
 * lifetime.
 */
public class GHRepoCache {

	private final Map<String /* user */, GHUser> userMap = new HashMap<>();

	private final Map<String /* user */, Map<String /* repo name */, GHRepository>> userReposMap = new HashMap<>();

	private final Map<String /* org */, GHOrganization> orgMap = new HashMap<>();

	private final Map<String /* org */, Map<String /* repo name */, GHRepository>> orgReposMap = new HashMap<>();

	private final Map<String /* owner/repo/issue */, GHIssue> issueMap = new HashMap<>();

	private final GitHub client;

	public GHRepoCache(GitHub client) {

		this.client = client;
	}

	public GHUser getUser(String userName) throws IOException {

		GHUser ghUser = userMap.get(userName);

		if (ghUser == null) {
			ghUser = client.getUser(userName);
			if (ghUser == null) {
				return null;
			}

			userMap.put(userName, ghUser);

			Map<String, GHRepository> repoMap = new HashMap<>();
			for (PagedIterator<GHRepository> it = ghUser.listRepositories().iterator(); it.hasNext();) {
				GHRepository repo = it.next();
				repoMap.put(repo.getName(), repo);
			}

			userReposMap.put(userName, repoMap);

		}

		return ghUser;

	}

	public GHOrganization getOrganization(String orgName) throws IOException {

		GHOrganization ghOrg = orgMap.get(orgName);

		if (ghOrg == null) {
			ghOrg = client.getOrganization(orgName);
			if (ghOrg == null) {
				return null;
			}

			orgMap.put(orgName, ghOrg);

			Map<String, GHRepository> repoMap = new HashMap<>();
			for (PagedIterator<GHRepository> it = ghOrg.listRepositories().iterator(); it.hasNext();) {
				GHRepository repo = it.next();
				repoMap.put(repo.getName(), repo);
			}

			orgReposMap.put(orgName, repoMap);

		}

		return ghOrg;

	}

	public GHRepository getRepository(boolean isOrg, String ownerName, String repoName) throws IOException {

		Map<String, GHRepository> map;

		if (isOrg) {
			GHOrganization org = getOrganization(ownerName);
			if (org == null) {
				return null;
			}
			map = orgReposMap.get(ownerName);

		} else {

			GHUser user = getUser(ownerName);
			if (user == null) {
				return null;
			}
			map = userReposMap.get(ownerName);

		}

		return map.get(repoName);
	}

	public GHIssue getIssue(GHRepository repo, int issueNumber, boolean onlyFromCache) throws IOException {
		String ownerName = repo.getOwnerName();

		String key = ownerName + "/" + repo.getName() + "/" + issueNumber;
		GHIssue ghIssue = issueMap.get(key);

		if (ghIssue == null) {

			if (onlyFromCache) {
				return null;
			}

			ghIssue = repo.getIssue(issueNumber);
			if (ghIssue == null) {
				return null;
			}

			issueMap.put(key, ghIssue);

		}

		return ghIssue;

	}
}
