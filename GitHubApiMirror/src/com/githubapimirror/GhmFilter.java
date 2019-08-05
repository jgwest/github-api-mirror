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

import com.githubapimirror.shared.Owner;

/**
 * This interface can be used to limit which repositories and issues a
 * GHServerInstance will access. If a resource should be processed, then 'true'
 * should be returned, else 'false'.
 * 
 * This interface is primarily used in the automated tests, in order to reduce
 * the automated test execution time.
 */
public interface GhmFilter {

	public boolean processOwner(Owner owner);

	public boolean processRepo(Owner owner, String repoName);

	public boolean processIssue(Owner owner, String repoName, int issue);

	public boolean processIssueEvents(Owner owner, String repoName, int issue);

	public boolean processUser(String loginName);
}
