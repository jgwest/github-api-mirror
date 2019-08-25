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

package com.githubapimirror.db;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.githubapimirror.shared.Owner;
import com.githubapimirror.shared.json.IssueJson;
import com.githubapimirror.shared.json.OrganizationJson;
import com.githubapimirror.shared.json.RepositoryJson;
import com.githubapimirror.shared.json.ResourceChangeEventJson;
import com.githubapimirror.shared.json.UserJson;
import com.githubapimirror.shared.json.UserRepositoriesJson;

/**
 * This class wraps an 'inner' database, and speeds up retrieval operations from
 * that database, by caching the result of persistent operations to that
 * database. The cache of the inner database is stored in memory, and is only
 * limited by the amount of JVM heap.
 * 
 * All objects in the cache are maintained via soft references, to ensure that
 * these objects are GC-ed when not otherwise used (preventing memory leaks).
 */
public class InMemoryCacheDb implements Database {

	private static final boolean DEBUG = false;

	private final Database inner;

	private final Map<String, SoftReference<Object>> cache_synch = new HashMap<>();

	private Long debug_cacheAttempts_synch_lock = 0l;

	private Long debug_cacheHits_synch_lock = 0l;

	public InMemoryCacheDb(Database inner) {
		this.inner = inner;
	}

	private Object getByKey(String key) {
		SoftReference<Object> entry;
		synchronized (cache_synch) {
			entry = cache_synch.get(key);

			if (DEBUG) {
				debug_cacheAttempts_synch_lock++;
				if (entry != null) {
					debug_cacheHits_synch_lock++;
				}
				if (debug_cacheAttempts_synch_lock % 1000 == 0) {
					System.out.println(
							"cache %: " + ((100 * debug_cacheHits_synch_lock) / debug_cacheAttempts_synch_lock));
				}
			}

		}
		if (entry == null) {
			return null;
		}

		return entry.get();

	}

	private void putByKeyOptional(String key, Optional<?> value) {
		if (value.isPresent()) {
			putByKey(key, value.get());
		}
	}

	private void putByKey(String key, Object value) {
		synchronized (cache_synch) {
			cache_synch.put(key, new SoftReference<Object>(value));
		}
	}

	@Override
	public Optional<IssueJson> getIssue(Owner owner, String repoName, long issueNumber) {
		String key = DatabaseUtil.generateIssueKey(owner, repoName, issueNumber);

		IssueJson cachedResult = (IssueJson) getByKey(key);
		if (cachedResult != null) {
			return Optional.of(cachedResult);
		}

		Optional<IssueJson> result = inner.getIssue(owner, repoName, issueNumber);
		putByKeyOptional(key, result);

		return result;

	}

	@Override
	public void persistIssue(Owner owner, IssueJson issue) {
		String key = DatabaseUtil.generateIssueKey(owner, issue.getParentRepo(), issue.getNumber());

		inner.persistIssue(owner, issue);

		putByKey(key, issue);
	}

	@Override
	public Optional<OrganizationJson> getOrganization(String orgName) {
		String key = DatabaseUtil.generateOrgKey(orgName);

		OrganizationJson cachedResult = (OrganizationJson) getByKey(key);
		if (cachedResult != null) {
			return Optional.of(cachedResult);
		}

		Optional<OrganizationJson> result = inner.getOrganization(orgName);

		putByKeyOptional(key, result);

		return result;
	}

	@Override
	public void persistOrganization(OrganizationJson org) {
		String key = DatabaseUtil.generateOrgKey(org.getName());

		inner.persistOrganization(org);

		putByKey(key, org);

	}

	@Override
	public Optional<RepositoryJson> getRepository(Owner owner, String repoName) {

		String key = DatabaseUtil.generateRepoKey(owner, repoName);

		RepositoryJson cachedResult = (RepositoryJson) getByKey(key);
		if (cachedResult != null) {
			return Optional.of(cachedResult);
		}

		Optional<RepositoryJson> result = inner.getRepository(owner, repoName);
		putByKeyOptional(key, result);

		return result;
	}

	@Override
	public void persistRepository(RepositoryJson repo) {
		String orgName = repo.getOrgName();
		String userName = repo.getOwnerUserName();

		Owner owner = orgName != null ? Owner.org(orgName) : Owner.user(userName);

		String key = DatabaseUtil.generateRepoKey(owner, repo.getName());

		inner.persistRepository(repo);

		putByKey(key, repo);
	}

	@Override
	public Optional<UserJson> getUser(String loginName) {
		String key = DatabaseUtil.generateUserKey(loginName);

		UserJson cachedResult = (UserJson) getByKey(key);
		if (cachedResult != null) {
			return Optional.of(cachedResult);
		}

		Optional<UserJson> result = inner.getUser(loginName);
		putByKeyOptional(key, result);

		return result;
	}

	@Override
	public void persistUser(UserJson user) {
		String key = DatabaseUtil.generateUserKey(user.getLogin());

		inner.persistUser(user);

		putByKey(key, user);

	}

	@Override
	public Optional<UserRepositoriesJson> getUserRepositories(String userName) {
		String key = DatabaseUtil.generateUserRepositoriesKey(userName);

		UserRepositoriesJson cachedResult = (UserRepositoriesJson) getByKey(key);
		if (cachedResult != null) {
			return Optional.of(cachedResult);
		}

		Optional<UserRepositoriesJson> result = inner.getUserRepositories(userName);

		putByKeyOptional(key, result);

		return result;
	}

	@Override
	public void persistUserRepositories(UserRepositoriesJson r) {
		String key = DatabaseUtil.generateUserRepositoriesKey(r.getUserName());

		inner.persistUserRepositories(r);

		putByKey(key, r);

	}

	@Override
	public void addProcessedEvents(List<String> eventHashes) {

		inner.addProcessedEvents(eventHashes);

	}

	@Override
	public List<String> getProcessedEvents() {
		return inner.getProcessedEvents();
	}

	@Override
	public void clearProcessedEvents() {
		inner.clearProcessedEvents();
	}

	@Override
	public boolean isDatabaseInitialized() {
		return inner.isDatabaseInitialized();
	}

	@Override
	public void initializeDatabase() {
		inner.initializeDatabase();
	}

	@Override
	public void persistLong(String keyParam, long value) {
		String key = "long-" + keyParam;

		inner.persistLong(keyParam, value);
		putByKey(key, value);
	}

	@Override
	public Optional<Long> getLong(final String keyParam) {
		String key = "long-" + keyParam;

		Long cachedResult = (Long) getByKey(key);
		if (cachedResult != null) {
			return Optional.of(cachedResult);
		}

		Optional<Long> result = inner.getLong(keyParam);
		putByKeyOptional(key, result);

		return result;
	}

	@Override
	public void uninitializeDatabaseOnContentsMismatch(List<String> orgs, List<String> userRepos,
			List<String> individualRepos) {

		inner.uninitializeDatabaseOnContentsMismatch(orgs, userRepos, individualRepos);
	}

	@Override
	public void persistString(String key, String value) {
		key = "string-" + key;

		inner.persistString(key, value);
		putByKey(key, value);
	}

	@Override
	public Optional<String> getString(String keyParam) {
		String key = "string-" + keyParam;

		String cachedResult = (String) getByKey(key);
		if (cachedResult != null) {
			return Optional.of(cachedResult);
		}

		Optional<String> result = inner.getString(keyParam);
		putByKeyOptional(key, result);

		return result;
	}

	@Override
	public void persistResourceChangeEvents(List<ResourceChangeEventJson> newEvents) {
		inner.persistResourceChangeEvents(newEvents);
	}

	@Override
	public List<ResourceChangeEventJson> getRecentResourceChangeEvents(long timestampEqualOrGreater) {
		return inner.getRecentResourceChangeEvents(timestampEqualOrGreater);
	}

}
