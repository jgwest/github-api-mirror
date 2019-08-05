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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.githubapimirror.ServerInstance;
import com.githubapimirror.ServerInstance.ServerInstanceBuilder;
import com.githubapimirror.db.Database;
import com.githubapimirror.service.yaml.ConfigFileYaml;

/**
 * Only a single instance of a number of objects are maintained in the
 * application, include the ServerInstance and Database. This class maintains
 * references to those.
 * 
 * This class also handles initial configuration of the above classes.
 */
public class ApiMirrorInstance {

	private static final ApiMirrorInstance instance = new ApiMirrorInstance();

	private ApiMirrorInstance() {

		String configPath = lookupString("github-api-mirror/config-path").get();

		try {
			ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

			List<String> userReposList = new ArrayList<>();
			List<String> orgList = new ArrayList<>();
			List<String> individualReposList = new ArrayList<>();

			ConfigFileYaml sf = mapper.readValue(new FileInputStream(new File(configPath)), ConfigFileYaml.class);

			this.presharedKey = sf.getPresharedKey();

			if (sf.getUserRepoList() != null) {
				userReposList.addAll(sf.getUserRepoList());
			}

			if (sf.getIndividualRepoList() != null) {
				individualReposList.addAll(sf.getIndividualRepoList());
			}

			if (sf.getOrgList() != null) {
				orgList.addAll(sf.getOrgList());
			}

			String dbPath = sf.getDbPath();

			// The database path of the YAML can be override by this JNDI value in the
			// server.xml. This is used when running within a container.
			Optional<String> jndiOverride = lookupString("github-api-mirror/db-path");
			if (jndiOverride.isPresent()) {
				dbPath = jndiOverride.get();
			}

			ServerInstanceBuilder builder = ServerInstance.builder()
					.credentials(sf.getGithubUsername(), sf.getGithubPassword()).dbDir(new File(dbPath))
					.serverName(sf.getGithubServer());

			if (!orgList.isEmpty()) {
				builder = builder.orgs(orgList.stream().filter(e -> !e.isEmpty()).collect(Collectors.toList()));
			}
			if (!userReposList.isEmpty()) {
				builder = builder
						.userRepos(userReposList.stream().filter(e -> !e.isEmpty()).collect(Collectors.toList()));
			}

			if (!individualReposList.isEmpty()) {
				builder = builder.individualRepos(
						individualReposList.stream().filter(e -> !e.isEmpty()).collect(Collectors.toList()));
			}

			if (sf.getGithubRateLimit() != null) {
				builder = builder.numRequestsPerHour(sf.getGithubRateLimit());
			}

			this.serverInstance = builder.build();

			db = serverInstance.getDb();

		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private static Optional<String> lookupString(String key) {

		try {
			InitialContext context = new InitialContext();
			Object value = context.lookup(key);
			if (value == null) {
				return Optional.empty();
			}
			return Optional.of(value.toString());
		} catch (NamingException e) {
			return Optional.empty();
		}
	}

	public static ApiMirrorInstance getInstance() {
		return instance;
	}

	// -----------------------------------------

	private final ServerInstance serverInstance;

	private final Database db;

	private final String presharedKey;

	public Database getDb() {
		return db;
	}

	public String getPresharedKey() {
		return presharedKey;
	}

}
