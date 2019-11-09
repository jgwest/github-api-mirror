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

package com.githubapimirror.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.githubapimirror.shared.Owner;
import com.githubapimirror.shared.Owner.Type;
import com.githubapimirror.shared.json.BulkIssuesJson;
import com.githubapimirror.shared.json.IssueJson;
import com.githubapimirror.shared.json.OrganizationJson;
import com.githubapimirror.shared.json.RepositoryJson;
import com.githubapimirror.shared.json.ResourceChangeEventJson;
import com.githubapimirror.shared.json.UserJson;
import com.githubapimirror.shared.json.UserRepositoriesJson;

/**
 * Internal class that queries the mirror server at the appropriate URL, and
 * marshals the JSON result.
 */
public class GHApiMirrorHttpClient {

	private final JavaNetHttpClient client;

	public static final String OWNER_TYPE_USER = "user";
	public static final String OWNER_TYPE_ORG = "org";

	public GHApiMirrorHttpClient(String resourceUrl, String authId) {

		client = new JavaNetHttpClient(resourceUrl, authId);
	}

	public Optional<UserRepositoriesJson> getUserRepositories(String name) {
		try {
			ApiResponse<UserRepositoriesJson> response = client.get("/user-repositories/" + name,
					UserRepositoriesJson.class);
			return Optional.of(response.getResponse());
		} catch (GHApiMirrorClientException e) {
			return Optional.empty();
		}
	}

	public Optional<OrganizationJson> getOrganization(String name) {

		try {
			ApiResponse<OrganizationJson> response = client.get("/organization/" + name, OrganizationJson.class);
			return Optional.of(response.getResponse());
		} catch (GHApiMirrorClientException e) {
			return Optional.empty();
		}
	}

	public Optional<RepositoryJson> getRepository(Owner owner, String repoName) {

		String ownerType = owner.getType() == Type.ORG ? OWNER_TYPE_ORG : OWNER_TYPE_USER;
		String ownerName = owner.getName();

		try {
			ApiResponse<RepositoryJson> response = client
					.get("/repository/" + ownerType + "/" + ownerName + "/" + repoName, RepositoryJson.class);
			return Optional.of(response.getResponse());
		} catch (GHApiMirrorClientException e) {
			return Optional.empty();
		}

	}

	public Optional<IssueJson> getIssue(Owner owner, String repoName, long issueNumber) {

		String ownerType = owner.getType() == Type.ORG ? OWNER_TYPE_ORG : OWNER_TYPE_USER;
		String ownerName = owner.getName();

		try {
			ApiResponse<IssueJson> response = client
					.get("/issue/" + ownerType + "/" + ownerName + "/" + repoName + "/" + issueNumber, IssueJson.class);
			return Optional.of(response.getResponse());
		} catch (GHApiMirrorClientException e) {
			return Optional.empty();
		}
	}

	public Optional<UserJson> getUser(String loginName) {

		try {
			ApiResponse<UserJson> response = client.get("/user/" + loginName, UserJson.class);
			return Optional.of(response.getResponse());
		} catch (GHApiMirrorClientException e) {
			return Optional.empty();
		}

	}

	public Optional<BulkIssuesJson> getBulkIssues(Owner owner, String repoName, int start, int end) {

		String ownerType = owner.getType() == Type.ORG ? OWNER_TYPE_ORG : OWNER_TYPE_USER;
		String ownerName = owner.getName();

		try {
			ApiResponse<BulkIssuesJson> response = client.get(
					"/bulk/issue/" + ownerType + "/" + ownerName + "/" + repoName + "?start=" + start + "&end=" + end,
					BulkIssuesJson.class);
			return Optional.of(response.getResponse());
		} catch (GHApiMirrorClientException e) {
			return Optional.empty();
		}

	}

	public Optional<BulkIssuesJson> getBulkIssues(Owner owner, String repoName, List<Integer> individualIssues) {

		String ownerType = owner.getType() == Type.ORG ? OWNER_TYPE_ORG : OWNER_TYPE_USER;
		String ownerName = owner.getName();

		try {

			String issueList = individualIssues.stream().sorted().map(e -> e + ",").reduce((a, b) -> a + b).get();
			while (issueList.endsWith(",")) {
				issueList = issueList.substring(0, issueList.length() - 1);
			}

			ApiResponse<BulkIssuesJson> response = client.get(
					"/bulk/issue/" + ownerType + "/" + ownerName + "/" + repoName + "?issueList=" + issueList,
					BulkIssuesJson.class);
			return Optional.of(response.getResponse());
		} catch (GHApiMirrorClientException e) {
			return Optional.empty();
		}

	}

	public List<ResourceChangeEventJson> getResourceChangeEvents(long timestampEqualOrGreater) {

		try {
			ApiResponse<ResourceChangeEventJson[]> response = client
					.get("/resourceChangeEvent?since=" + timestampEqualOrGreater, ResourceChangeEventJson[].class);

			List<ResourceChangeEventJson> result = new ArrayList<>();

			result.addAll(Arrays.asList(response.getResponse()));

			return result;

		} catch (GHApiMirrorClientException e) {
			return Collections.emptyList();
		}

	}

	public void adminTriggerFullScan() {
		try {
			@SuppressWarnings("unused")
			ApiResponse<EmptyBody> response = client.post("/admin/request/fullscan", EmptyBody.class);

		} catch (GHApiMirrorClientException e) {
			throw e;
		}

	}

	/** HTTP API expects to deserialize an object, so we give it an empty class. */
	private static class EmptyBody {
	}
}
