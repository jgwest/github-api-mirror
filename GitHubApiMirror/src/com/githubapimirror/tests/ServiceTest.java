/*
 * Copyright 2019, 2021 Jonathan West
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.Test;

import com.githubapimirror.ServerInstance;
import com.githubapimirror.ServerInstance.ServerInstanceBuilder;
import com.githubapimirror.db.Database;
import com.githubapimirror.shared.Owner;
import com.githubapimirror.shared.json.IssueCommentJson;
import com.githubapimirror.shared.json.IssueJson;
import com.githubapimirror.shared.json.OrganizationJson;
import com.githubapimirror.shared.json.RepositoryJson;
import com.githubapimirror.shared.json.UserRepositoriesJson;

/**
 * These tests will start the server instance/database, ask the server to
 * process a specific repository, then wait for the database to contain the
 * expected data.
 */
public class ServiceTest extends AbstractTest {

	@Test
	public void testGitHubOrganization() throws IOException {

		File dirDb = Files.createTempDirectory("gham").toFile();

		String orgName = "microclimate-dev2ops";
		String repoName = "microclimate-vscode-tools";

		TestFilter tf = new TestFilter();
		tf.addTestPair(new TestFilter.TFTestPair(Owner.org(orgName), repoName, 26, false));

		ServerInstanceBuilder builder = getClientBuilder();

		ServerInstance instance = builder.serverName("github.com").owner("microclimate-dev2ops").dbDir(dirDb).filter(tf)
				.numRequestsPerHour(100000).build();

		waitForPass(60, () -> {

			Owner owner = Owner.org(orgName);

			Database db = instance.getDb();
			OrganizationJson oj = db.getOrganization(orgName).get();

			assertTrue(oj.getRepositories().contains(repoName));

			RepositoryJson rj = db.getRepository(Owner.org(orgName), repoName).get();
			assertNotNull(rj);

			IssueJson ij = db.getIssue(owner, repoName, 26).get();
			assertTrue(ij.getBody().contains("Document it in the troubleshooting"));

			List<IssueCommentJson> comments = ij.getComments();
			assertTrue(comments.size() == 1);

			assertTrue(comments.get(0).getBody().contains("doc'd"));
			System.out.println(comments);
		});

	}

	@Test
	public void testGitHubUserRepository() throws IOException {

		File dirDb = Files.createTempDirectory("gham").toFile();

		String userName = "jgwest";
		String repoName = "rogue-cloud";

		TestFilter tf = new TestFilter();
		tf.addTestPair(new TestFilter.TFTestPair(Owner.user(userName), repoName, 1, 1000, false));

		ServerInstanceBuilder builder = getClientBuilder();

		ServerInstance instance = builder.serverName("github.com").owner("jgwest").dbDir(dirDb).filter(tf)
				.numRequestsPerHour(100000).build();

		waitForPass(60, false, () -> {

			Database db = instance.getDb();

			UserRepositoriesJson urj = db.getUserRepositories(userName).get();

			assertTrue(urj.getRepoNames().contains(repoName));

			Owner owner = Owner.user(userName);

			RepositoryJson rj = db.getRepository(owner, repoName).get();
			assertNotNull(rj);

		});

	}

	@Test
	public void testIssueEvents() throws IOException {

		File dirDb = Files.createTempDirectory("gham").toFile();

		String orgName = "argoproj-labs";
		String repoName = "applicationset";

		TestFilter tf = new TestFilter();
		tf.addTestPair(new TestFilter.TFTestPair(Owner.org(orgName), repoName, 222, true));

		ServerInstanceBuilder builder = getClientBuilder();

		ServerInstance instance = builder.serverName("github.com").individualRepos(orgName + "/" + repoName, 3600l)
				.dbDir(dirDb).filter(tf).numRequestsPerHour(100_000).build();

		waitForPass(60, false, () -> {

			Database db = instance.getDb();

			Owner owner = Owner.org(orgName);

			OrganizationJson oj = db.getOrganization(orgName).get();
			assertTrue(oj.getRepositories().contains(repoName));

			RepositoryJson rj = db.getRepository(owner, repoName).get();
			assertNotNull(rj);

			IssueJson ij = db.getIssue(owner, repoName, 222).get();

			assertTrue(ij.getIssueEvents().size() > 0);

			assertTrue(ij.getIssueEvents().stream()
					.anyMatch(ie -> ie.getActorUserLogin().equals("jgwest") && ie.getType().equals("labeled")));

			assertTrue(ij.getIssueEvents().stream()
					.anyMatch(ie -> ie.getActorUserLogin().equals("chetan-rns") && ie.getType().equals("assigned")));
			
		});

	}

}
