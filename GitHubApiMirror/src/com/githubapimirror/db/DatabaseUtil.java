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

import com.githubapimirror.shared.Owner;
import com.githubapimirror.shared.Owner.Type;

/**
 * Utility functions that may be used by implementers of the Database interface.
 */
public class DatabaseUtil {

	public static String generateIssueKey(Owner owner, String repoName, long issueNumber) {
		String key = owner.getName() + "/" + repoName + "/" + issueNumber;
		return key;
	}

	public static String generateOrgKey(String orgName) {
		String key = orgName;
		return key;
	}

	public static String generateRepoKey(Owner owner, String repoName) {
		String key;

		if (owner.getType() == Type.ORG) {
			key = owner.getOrgNameOrNull();
		} else {
			key = owner.getUserNameOrNull();
		}

		key = key + "/" + repoName;
		return key;
	}

	public static String generateUserKey(String loginName) {
		String key = "users/" + loginName;
		return key;
	}

	public static String generateUserRepositoriesKey(String userName) {
		String key = userName;
		return key;
	}

}
