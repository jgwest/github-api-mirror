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
import com.githubapimirror.service.yaml.IndividualRepoListYaml;

/**
 * Only a single instance of a number of objects are maintained in the
 * application, include the ServerInstance and Database. This class maintains
 * references to those.
 * 
 * This class also handles initial configuration of the above classes.
 */
public final class ApiMirrorInstance {

	private static final ApiMirrorInstance instance = new ApiMirrorInstance();

	private final Object lock = new Object();

	private ApiMirrorInstance() {
	}

	public void initializeServerInstance(String configPath, String dbPath) {

		try {

			if (configPath == null) {
				return;
			}

//			String configPath = configPathProperty.orElse(null);
//			if (configPath == null || configPath.isBlank()) {
//				System.err.println("Warning: Config path is empty.");
//				this.db = null;
//				this.presharedKey = null;
//				this.serverInstance = null;
//				return;
//			}

//			String configPath = lookupString("github-api-mirror/config-path").orElse(null);
//			if (configPath == null || configPath.isBlank()) {
//				System.err.println("Warning: Config path is empty.");
//				this.db = null;
//				this.presharedKey = null;
//				this.serverInstance = null;
//				return;
//			}

			ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

			List<String> owners = new ArrayList<>();

//			List<String> userReposList = new ArrayList<>();
//			List<String> orgList = new ArrayList<>();

			ConfigFileYaml configYaml = mapper.readValue(new FileInputStream(new File(configPath)),
					ConfigFileYaml.class);

			if (configYaml.getOwners() != null) {
				owners.addAll(configYaml.getOwners().stream().filter(e -> !e.isBlank()).collect(Collectors.toList()));
			}

//			if (configYaml.getUserRepoList() != null) {
//				userReposList.addAll(configYaml.getUserRepoList());
//			}
//
//			if (configYaml.getOrgList() != null) {
//				orgList.addAll(configYaml.getOrgList());
//			}

			// If the user has not overriden the configuration, then use the config YAML
			// value
			if (dbPath == null) {
				dbPath = configYaml.getDbPath();
			}

//
//			if (dbPathProperty.isPresent() && !dbPathProperty.get().isBlank()) {
//				dbPath = dbPathProperty.get();
//			}

//			String lookupString = lookupString("db.path").orElse(null);
//
//			// Allow the configuration file value to be overriden by config
//			if (lookupString != null && !lookupString.isEmpty()) {
//				dbPath = lookupString;
//			}

			File dbPathFile = new File(dbPath);
			if (dbPathFile == null || !dbPathFile.exists()) {
				throw new RuntimeException("Database path does not exist: " + dbPathFile);
			}

			ServerInstanceBuilder builder = ServerInstance.builder()
					.credentials(configYaml.getGithubUsername(), configYaml.getGithubPassword()).dbDir(dbPathFile)
					.serverName(configYaml.getGithubServer());

			if (!owners.isEmpty()) {
				builder = builder.owners(owners);
			}

//			if (!orgList.isEmpty()) {
//				builder = builder.orgs(orgList.stream().filter(e -> !e.isEmpty()).collect(Collectors.toList()));
//			}
//			if (!userReposList.isEmpty()) {
//				builder = builder
//						.userRepos(userReposList.stream().filter(e -> !e.isEmpty()).collect(Collectors.toList()));
//			}

			if (configYaml.getIndividualRepoList() != null) {

				for (IndividualRepoListYaml irly : configYaml.getIndividualRepoList()) {
					builder = builder.individualRepos(irly.getRepo(), irly.getTimeBetweenEventScansInSeconds());
				}

			}

			if (configYaml.getGithubRateLimit() != null) {
				builder = builder.numRequestsPerHour(configYaml.getGithubRateLimit());
			}

			if (configYaml.getTimeBetweenEventScansInSeconds() != null) {
				builder = builder.timeBetweenEventScansInSeconds(configYaml.getTimeBetweenEventScansInSeconds());
			}

			if (configYaml.getPauseBetweenRequestsInMsecs() != null) {
				builder = builder.pauseBetweenRequestsInMsecs(configYaml.getPauseBetweenRequestsInMsecs());
			}

			if (configYaml.getFileLoggerPath() != null) {
				builder = builder.fileLoggingPath(new File(configYaml.getFileLoggerPath()));
			}

			synchronized (lock) {
				if (this.serverInstance_synch_lock == null) {
					this.presharedKey_synch_lock = configYaml.getPresharedKey();
					this.serverInstance_synch_lock = builder.build();
					this.db_sync_lock = serverInstance_synch_lock.getDb();
				} else {
					throw new RuntimeException(this.getClass().getName() + " is already initialized");
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

//	private static Optional<String> lookupString(String key) {
//
//		try {
//			InitialContext context = new InitialContext();
//			Object value = context.lookup(key);
//			if (value == null) {
//				return Optional.empty();
//			}
//			return Optional.of(value.toString());
//		} catch (NamingException e) {
//			return Optional.empty();
//		}
//	}

	public static ApiMirrorInstance getInstance() {
		return instance;
	}

	// -----------------------------------------

	private ServerInstance serverInstance_synch_lock;

	private Database db_sync_lock;

	private String presharedKey_synch_lock;

	// -----------------------------------------

	public Database getDb() {
		synchronized (lock) {
			if (db_sync_lock == null) {
				throw new RuntimeException(this.getClass().getName() + " not initialized.");
			}
			return db_sync_lock;
		}
	}

	public String getPresharedKey() {
		synchronized (lock) {
			if (serverInstance_synch_lock == null) {
				throw new RuntimeException(this.getClass().getName() + " not initialized.");
			}
			return presharedKey_synch_lock;
		}
	}

	public ServerInstance getServerInstance() {
		synchronized (lock) {
			if (serverInstance_synch_lock == null) {
				throw new RuntimeException(this.getClass().getName() + " not initialized.");
			}
			return serverInstance_synch_lock;
		}
	}

}
