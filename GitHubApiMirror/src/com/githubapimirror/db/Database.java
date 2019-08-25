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

import java.util.List;
import java.util.Optional;

import com.githubapimirror.shared.Owner;
import com.githubapimirror.shared.json.IssueJson;
import com.githubapimirror.shared.json.OrganizationJson;
import com.githubapimirror.shared.json.RepositoryJson;
import com.githubapimirror.shared.json.ResourceChangeEventJson;
import com.githubapimirror.shared.json.UserJson;
import com.githubapimirror.shared.json.UserRepositoriesJson;

/**
 * This interface abstracts the persistence of GitHub JSON resources, allowing
 * the underlying database technology to vary independently of the calling
 * class.
 * 
 * Implementing classes include InMemoryCacheDB and PersistJsonDb.
 */
public interface Database {

	public static final String LAST_FULL_SCAN = "lastFullScan";

	public Optional<IssueJson> getIssue(Owner owner, String repoName, long issueNumber);

	public void persistIssue(Owner owner, IssueJson issue);

	public Optional<OrganizationJson> getOrganization(String orgName);

	public void persistOrganization(OrganizationJson org);

	public Optional<RepositoryJson> getRepository(Owner owner, String repoName);

	public void persistRepository(RepositoryJson repo);

	public Optional<UserJson> getUser(String loginName);

	public void persistUser(UserJson user);

	public void addProcessedEvents(List<String> eventHashes);

	public List<String> getProcessedEvents();

	public void clearProcessedEvents();

	public Optional<UserRepositoriesJson> getUserRepositories(String user);

	public void persistUserRepositories(UserRepositoriesJson r);

	public boolean isDatabaseInitialized();

	public void initializeDatabase();

	/**
	 * This is called at startup, to ensure the database contents match the repos we
	 * are asked to mirror in the configuration file. If they don't match, the
	 * database should be destroyed and rebuilt.
	 */
	public void uninitializeDatabaseOnContentsMismatch(List<String> orgs, List<String> userRepos,
			List<String> individualRepos);

	public void persistLong(String key, long value);

	public Optional<Long> getLong(String key);

	public void persistString(String key, String value);

	public Optional<String> getString(String key);

	public void persistResourceChangeEvents(List<ResourceChangeEventJson> newEvents);

	public List<ResourceChangeEventJson> getRecentResourceChangeEvents(long timestampEqualOrGreater);

}