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

package com.githubapimirror.service;

import java.util.Arrays;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.githubapimirror.db.Database;
import com.githubapimirror.shared.GHApiUtil;
import com.githubapimirror.shared.JsonUtil;
import com.githubapimirror.shared.Owner;
import com.githubapimirror.shared.json.BulkIssuesJson;
import com.githubapimirror.shared.json.IssueJson;
import com.githubapimirror.shared.json.OrganizationJson;
import com.githubapimirror.shared.json.RepositoryJson;
import com.githubapimirror.shared.json.ResourceChangeEventJson;
import com.githubapimirror.shared.json.UserJson;
import com.githubapimirror.shared.json.UserRepositoriesJson;

/**
 * A JAX-RS resource class that listens on resource requests to
 * Orgs/Repositores/Issues/Users, queries the database, then returns the result.
 * 
 * Before processing a request, the pre-shared key is verified, here.
 */
@Path("/")
public class ApiMirrorService {

	public static final String OWNER_TYPE_USER = "user";
	public static final String OWNER_TYPE_ORG = "org";

	@Context
	HttpHeaders headers;

	@GET
	@Path("/organization/{name}")
	public Response getOrganization(@PathParam("name") String orgName) {
		verifyHeaderAuth();

		Database db = getDb();

		OrganizationJson org = db.getOrganization(orgName).orElse(null);
		if (org != null) {
			return Response.ok(JsonUtil.toString(org)).type(MediaType.APPLICATION_JSON_TYPE).build();
		} else {
			return Response.status(Status.NOT_FOUND).build();
		}
	}

	@GET
	@Path("/user-repositories/{name}")
	public Response getUserRepositories(@PathParam("name") String userRepoName) {
		verifyHeaderAuth();

		Database db = getDb();

		UserRepositoriesJson urj = db.getUserRepositories(userRepoName).orElse(null);

		if (urj != null) {
			return Response.ok(JsonUtil.toString(urj)).type(MediaType.APPLICATION_JSON_TYPE).build();
		} else {
			return Response.status(Status.NOT_FOUND).build();
		}
	}

	@GET
	@Path("/repository/{ownerType}/{ownerName}/{repoName}")
	public Response getRepository(@PathParam("ownerType") String ownerType, @PathParam("ownerName") String ownerName,
			@PathParam("repoName") String repoName) {
		verifyHeaderAuth();

		Owner owner = getOwner(ownerType, ownerName);
		Database db = getDb();

		RepositoryJson repo = db.getRepository(owner, repoName).orElse(null);
		if (repo != null) {
			return Response.ok(JsonUtil.toString(repo)).type(MediaType.APPLICATION_JSON_TYPE).build();
		} else {
			return Response.status(Status.NOT_FOUND).build();
		}

	}

	@GET
	@Path("/issue/{ownerType}/{ownerName}/{repoName}/{issueNumber}")
	public Response getIssue(@PathParam("ownerType") String ownerType, @PathParam("ownerName") String ownerName,
			@PathParam("repoName") String repoName, @PathParam("issueNumber") long issueNum) {
		verifyHeaderAuth();

		Owner owner = getOwner(ownerType, ownerName);

		Database db = getDb();

		IssueJson issue = db.getIssue(owner, repoName, issueNum).orElse(null);
		if (issue != null) {
			return Response.ok(JsonUtil.toString(issue)).type(MediaType.APPLICATION_JSON_TYPE).build();
		} else {
			return Response.status(Status.NOT_FOUND).build();
		}

	}

	@GET
	@Path("/bulk/issue/{ownerType}/{ownerName}/{repoName}")
	public Response getBulkIssues(@PathParam("ownerType") String ownerType, @PathParam("ownerName") String ownerName,
			@PathParam("repoName") String repoName, @QueryParam("start") Integer startIssue,
			@QueryParam("end") Integer endIssue, @QueryParam("issueList") String issueList) {
		verifyHeaderAuth();

		Owner owner = getOwner(ownerType, ownerName);

		Database db = getDb();

		BulkIssuesJson result = new BulkIssuesJson();

		List<IssueJson> results = result.getIssues();

		if (startIssue != null && endIssue != null) {

			for (int x = startIssue; x <= endIssue; x++) {
				db.getIssue(owner, repoName, x).ifPresent(e -> {
					results.add(e);
				});
			}

		} else if (issueList != null) {
			// Comma-separated issue list

			Arrays.asList(issueList.split(",")).stream().map(e -> e.trim()).filter(e -> e.length() > 0)
					.map(e -> Integer.parseInt(e)).forEach(issue -> {
						db.getIssue(owner, repoName, issue).ifPresent(e -> {
							results.add(e);
						});

					});

		} else {
			return Response.status(Status.BAD_REQUEST).build();
		}

		return Response.ok(JsonUtil.toString(result)).type(MediaType.APPLICATION_JSON_TYPE).build();

	}

	private static Owner getOwner(String ownerType, String ownerName) {
		if (ownerType != null && ownerType.equals(OWNER_TYPE_ORG)) {
			return Owner.org(ownerName);

		} else if (ownerType != null && ownerType.equals(OWNER_TYPE_USER)) {
			return Owner.user(ownerName);
		} else {
			throw new IllegalArgumentException("Invalid owner type");
		}
	}

	@GET
	@Path("/user/{loginName}")
	public Response getUser(@PathParam("loginName") String loginName) {
		verifyHeaderAuth();

		Database db = getDb();
		UserJson user = db.getUser(loginName).orElse(null);
		if (user != null) {
			return Response.ok(JsonUtil.toString(user)).type(MediaType.APPLICATION_JSON_TYPE).build();
		} else {
			return Response.status(Status.NOT_FOUND).build();
		}
	}

	@GET
	@Path("/resourceChangeEvent")
	public Response getUser(@QueryParam("since") long sinceGreaterOrEqualTime) {
		verifyHeaderAuth();

		Database db = getDb();
		List<ResourceChangeEventJson> changes = db.getRecentResourceChangeEvents(sinceGreaterOrEqualTime);
		return Response.ok(JsonUtil.toString(changes)).type(MediaType.APPLICATION_JSON_TYPE).build();
	}

	private void verifyHeaderAuth() {
		String key = ApiMirrorInstance.getInstance().getPresharedKey();

		String authHeader = headers.getHeaderString("Authorization");

		if (authHeader != null && key != null && key.equalsIgnoreCase(authHeader)) {
			return;
		}

		GHApiUtil.sleep(1000);

		throw new IllegalArgumentException("No authorization header was found in the client request.");

	}

	private Database getDb() {
		return ApiMirrorInstance.getInstance().getDb();
	}
}
