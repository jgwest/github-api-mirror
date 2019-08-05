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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubapimirror.GHLog;
import com.githubapimirror.shared.GHApiUtil;
import com.githubapimirror.shared.Owner;
import com.githubapimirror.shared.json.IssueJson;
import com.githubapimirror.shared.json.OrganizationJson;
import com.githubapimirror.shared.json.RepositoryJson;
import com.githubapimirror.shared.json.UserJson;
import com.githubapimirror.shared.json.UserRepositoriesJson;

/**
 * Persists the GH JSON resources to disk, using path and filenames to
 * distinguish resources types in the data hierarchy. The output directory is
 * specified in the constructor.
 * 
 * This class is thread safe.
 */
public class PersistJsonDb implements Database {

	private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

	private final Lock readLock = rwLock.readLock();
	private final Lock writeLock = rwLock.writeLock();

	private final File outputDirectory;

	private final AtomicBoolean initialized = new AtomicBoolean();

	private final static String KEY_GITHUB_CONTENTS_HASH = "GitHubContentsHash";

	private static final boolean DEBUG_IGNORE_OLD_DATABASE = false;

	private static final GHLog log = GHLog.getInstance();

	public PersistJsonDb(File outputDirectory) {
		this.outputDirectory = outputDirectory;

		initialized.set(outputDirectory.exists() && outputDirectory.listFiles().length > 0);
	}

	@Override
	public Optional<IssueJson> getIssue(Owner owner, String repoName, long issueNumber) {

		String key = DatabaseUtil.generateIssueKey(owner, repoName, issueNumber);

		File inputFile = new File(outputDirectory, key + ".json");
		if (!inputFile.exists()) {
			return Optional.empty();
		}

		String contents = readFromFile(inputFile).orElse(null);

		IssueJson result = readValue(contents, IssueJson.class);

		return Optional.ofNullable(result);

	}

	@Override
	public void persistIssue(Owner owner, IssueJson issue) {

		String key = DatabaseUtil.generateIssueKey(owner, issue.getParentRepo(), issue.getNumber());

		File outputFile = new File(outputDirectory, key + ".json");

		String contents = writeValueAsString(issue);
		writeToFile(contents, outputFile);

	}

	@Override
	public Optional<OrganizationJson> getOrganization(String orgName) {
		String key = DatabaseUtil.generateOrgKey(orgName);
		File inputFile = new File(outputDirectory, key + "/" + orgName + ".json");
		if (!inputFile.exists()) {
			return Optional.empty();
		}

		String contents = readFromFile(inputFile).orElse(null);

		OrganizationJson result = (OrganizationJson) readValue(contents, OrganizationJson.class);

		return Optional.ofNullable(result);
	}

	@Override
	public void persistOrganization(OrganizationJson org) {

		String key = DatabaseUtil.generateOrgKey(org.getName());
		File outputFile = new File(outputDirectory, key + "/" + org.getName() + ".json");

		String contents = writeValueAsString(org);
		writeToFile(contents, outputFile);
	}

	@Override
	public Optional<RepositoryJson> getRepository(Owner owner, String repoName) {

		String key = DatabaseUtil.generateRepoKey(owner, repoName);

		File inputFile = new File(outputDirectory, key + "/" + repoName + ".json");
		if (!inputFile.exists()) {
			return Optional.empty();
		}

		String contents = readFromFile(inputFile).orElse(null);

		RepositoryJson result = (RepositoryJson) readValue(contents, RepositoryJson.class);

		return Optional.ofNullable(result);
	}

	@Override
	public void persistRepository(RepositoryJson repo) {
		String orgName = repo.getOrgName();
		String userName = repo.getOwnerUserName();

		Owner owner = orgName != null ? Owner.org(orgName) : Owner.user(userName);

		String key = DatabaseUtil.generateRepoKey(owner, repo.getName());

		File outputFile = new File(outputDirectory, key + "/" + repo.getName() + ".json");

		String contents = writeValueAsString(repo);
		writeToFile(contents, outputFile);
	}

	@Override
	public Optional<UserJson> getUser(String loginName) {
		String key = DatabaseUtil.generateUserKey(loginName);

		File inputFile = new File(outputDirectory, key + ".json");
		if (!inputFile.exists()) {
			return Optional.empty();
		}

		String contents = readFromFile(inputFile).orElse(null);

		UserJson result = (UserJson) readValue(contents, UserJson.class);

		return Optional.ofNullable(result);

	}

	@Override
	public void persistUser(UserJson user) {

		String key = DatabaseUtil.generateUserKey(user.getLogin());

		File outputFile = new File(outputDirectory, key + ".json");

		String contents = writeValueAsString(user);
		writeToFile(contents, outputFile);

	}

	@Override
	public Optional<UserRepositoriesJson> getUserRepositories(String userName) {
		String key = DatabaseUtil.generateUserRepositoriesKey(userName);
		File inputFile = new File(outputDirectory, key + "/" + userName + ".json");
		if (!inputFile.exists()) {
			return Optional.empty();
		}

		String contents = readFromFile(inputFile).orElse(null);

		UserRepositoriesJson result = (UserRepositoriesJson) readValue(contents, UserRepositoriesJson.class);

		return Optional.ofNullable(result);
	}

	@Override
	public void persistUserRepositories(UserRepositoriesJson r) {
		String key = DatabaseUtil.generateUserRepositoriesKey(r.getUserName());
		File outputFile = new File(outputDirectory, key + "/" + r.getUserName() + ".json");

		String contents = writeValueAsString(r);
		writeToFile(contents, outputFile);

	}

	private <T> T readValue(String contents, Class<T> c) {
		if (contents == null) {
			return null;
		}

		ObjectMapper om = new ObjectMapper();
		try {
			return om.readValue(contents, c);
		} catch (Exception e) {
			GHApiUtil.throwAsUnchecked(e);
			return null;
		}
	}

	private String writeValueAsString(Object o) {
		ObjectMapper om = new ObjectMapper();
		String result = null;
		try {
			result = om.writeValueAsString(o);
		} catch (JsonProcessingException e) {
			GHApiUtil.throwAsUnchecked(e);
		}
		return result;
	}

	private Optional<String> readFromFile(File f) {
		try {
			readLock.lock();

			if (!f.exists()) {
				return Optional.empty();
			}

			StringBuilder sb = new StringBuilder();

			try {

				byte[] barr = new byte[1024 * 64];
				int c;

				FileInputStream fis = new FileInputStream(f);
				while (-1 != (c = fis.read(barr))) {

					sb.append(new String(barr, 0, c));
				}
				fis.close();

			} catch (IOException e) {
				System.err.println("Error from file: " + f.getPath());
				GHApiUtil.throwAsUnchecked(e);
			}

			return Optional.of(sb.toString());

		} finally {
			readLock.unlock();
		}
	}

	private void writeToFile(List<String> contents, File f) {
		StringBuilder sb = new StringBuilder();
		for (String str : contents) {
			sb.append(str);
			sb.append("\n");
		}

		writeToFile(sb.toString(), f);

	}

	private void writeToFile(String contents, File f) {

		try {
			writeLock.lock();

			f.getParentFile().mkdirs();

			FileWriter fw = null;
			try {
				fw = new FileWriter(f);
				fw.write(contents);
				fw.close();
			} catch (IOException e) {
				GHApiUtil.throwAsUnchecked(e);
			} finally {
				if (fw != null) {
					try {
						fw.close();
					} catch (IOException e) {
						/* ignore */ }
				}
			}

		} finally {
			writeLock.unlock();
		}

	}

	@Override
	public void addProcessedEvents(List<String> eventsToAdd) {
		try {
			writeLock.lock();

			File metadataDir = new File(outputDirectory, "metadata");
			metadataDir.mkdirs();

			File eventHashesFile = new File(metadataDir, "event-hashes.txt");

			List<String> contents = new ArrayList<>();

			if (eventHashesFile.exists()) {
				contents.addAll(GHApiUtil.readFileIntoLines(eventHashesFile));
			}

			contents.addAll(eventsToAdd);

			if (contents.size() > 500) { // With an ArrayList: Faster to reverse, remove from end, then reverse again
				Collections.reverse(contents);

				while (contents.size() > 500) {
					contents.remove(contents.size() - 1);
				}

				Collections.reverse(contents);
			}

			writeToFile(contents, eventHashesFile);

		} finally {
			writeLock.unlock();
		}

	}

	@Override
	public List<String> getProcessedEvents() {
		try {
			readLock.lock();

			File metadataDir = new File(outputDirectory, "metadata");
			metadataDir.mkdirs();

			File eventHashesFile = new File(metadataDir, "event-hashes.txt");

			if (!eventHashesFile.exists()) {
				return Collections.emptyList();
			}

			return GHApiUtil.readFileIntoLines(eventHashesFile);

		} finally {
			readLock.unlock();
		}
	}

	@Override
	public boolean isDatabaseInitialized() {

		return initialized.get();

	}

	@Override
	public void initializeDatabase() {
		initialized.set(true);
	}

	@Override
	public void uninitializeDatabaseOnContentsMismatch(List<String> orgs, List<String> userRepos,
			List<String> individualRepos) {

		if (!isDatabaseInitialized()) {
			return;
		}

		if (DEBUG_IGNORE_OLD_DATABASE) {
			for (int x = 0; x < 30; x++) {
				System.err.println("Debug: Ignoring old database!!!!!!!!!!");
			}
			return;
		}

		if (orgs == null) {
			orgs = new ArrayList<>();
		}
		if (userRepos == null) {
			userRepos = new ArrayList<>();
		}
		if (individualRepos == null) {
			individualRepos = new ArrayList<>();
		}

		// Convert to lowercase and sort
		Arrays.asList(orgs, userRepos, individualRepos).stream().forEach(e -> {

			List<String> newContents = e.stream().map(f -> f.toLowerCase()).sorted().collect(Collectors.toList());

			e.clear();
			e.addAll(newContents);

		});

		List<String> contents = new ArrayList<>();
		contents.add("orgs:");
		contents.addAll(orgs);
		contents.add("user-repos:");
		contents.addAll(userRepos);
		contents.add("individual-repos:");
		contents.addAll(individualRepos);

		String encoded;

		// Convert the array list to a hash
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(contents.stream().reduce((a, b) -> a + " " + b).get().getBytes("UTF-8"));

			encoded = Base64.getEncoder().encodeToString(bytes);

		} catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
			throw new RuntimeException(e); // Convert to unchecked
		}

		boolean uninitializeDatabase = false;

		Optional<String> gitHubContentsHash = getString(KEY_GITHUB_CONTENTS_HASH);
		if (!gitHubContentsHash.isPresent()) { // key not found
			uninitializeDatabase = true;
		} else if (!gitHubContentsHash.get().equals(encoded)) {
			uninitializeDatabase = true; // key doesn't match
		}

		if (uninitializeDatabase) {

			File oldDir = new File(outputDirectory, "old");
			if (!oldDir.exists()) {
				if (!oldDir.mkdirs()) {
					throw new RuntimeException("Unable to create: " + oldDir.getParentFile());
				}
			}

			long time = System.currentTimeMillis();

			for (File f : outputDirectory.listFiles()) {
				if (f.getPath().equals(oldDir.getPath())) {
					continue; // Don't move the old directory
				}

				try {
					Files.move(f.toPath(), new File(oldDir, f.getName() + ".old." + time).toPath());
				} catch (IOException e1) {
					throw new RuntimeException("Unable to move: " + f.getPath(), e1);
				}
			}

			log.logInfo("* Old database has been moved to " + oldDir.getPath());

			initialized.set(false);
		}

	}

	@Override
	public void persistLong(String key, long value) {

		persistString(key, Long.toString(value));
	}

	@Override
	public Optional<Long> getLong(String key) {

		Optional<String> result = getString(key);

		if (!result.isPresent()) {
			return Optional.empty();
		}

		return Optional.of(Long.parseLong(result.get()));

	}

	@Override
	public void persistString(String key, String value) {
		File outputFile = new File(outputDirectory, "keys/" + key + ".txt");

		writeToFile(value, outputFile);
	}

	@Override
	public Optional<String> getString(String key) {
		File inputFile = new File(outputDirectory, "keys/" + key + ".txt");
		if (!inputFile.exists()) {
			return Optional.empty();
		}

		String contents = readFromFile(inputFile).orElse(null);

		return Optional.ofNullable(contents);
	}

}
