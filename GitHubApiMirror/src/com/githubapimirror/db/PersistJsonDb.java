/*
 * Copyright 2019, 2020 Jonathan West
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
import com.githubapimirror.shared.json.ResourceChangeEventJson;
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

			HashSet<String> hs = new HashSet<>(eventsToAdd);

			writeLock.lock();

			File metadataDir = new File(outputDirectory, "metadata");
			metadataDir.mkdirs();

			File eventHashesFile = new File(metadataDir, "event-hashes.txt");

			if (eventHashesFile.exists()) {

				GHApiUtil.readFileIntoLines(eventHashesFile).stream().filter(e -> !hs.contains(e)).forEach(e -> {
					hs.add(e);
				});
			}

			writeToFile(new ArrayList<>(hs), eventHashesFile);

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
	public void clearProcessedEvents() {
		try {
			writeLock.lock();

			File metadataDir = new File(outputDirectory, "metadata");
			metadataDir.mkdirs();

			File eventHashesFile = new File(metadataDir, "event-hashes.txt");

			List<String> contents = new ArrayList<>();

			writeToFile(contents, eventHashesFile);

		} finally {
			writeLock.unlock();
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

		if (DEBUG_IGNORE_OLD_DATABASE) {
			System.err.println("Debug: skipping check in uninitializeDatabaseOnContentsMismatch.");
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

		if (!isDatabaseInitialized()) {
			// If the database has not yet been initialized, then just set the value and
			// return.
			persistString(KEY_GITHUB_CONTENTS_HASH, encoded);
			return;
		}

		Optional<String> gitHubContentsHash = getString(KEY_GITHUB_CONTENTS_HASH);
		if (!gitHubContentsHash.isPresent()) { // key not found
			uninitializeDatabase = true;
			log.logInfo("GitHub contents key not found, so uninitializing database.");
		} else if (!gitHubContentsHash.get().equals(encoded)) {
			uninitializeDatabase = true; // key doesn't match
			log.logInfo("GitHub contents key did not match, so uninitializing database.");
		}

		if (uninitializeDatabase) {
			// If we want to "un-initialize" the database, move it to 'old/'

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

			persistString(KEY_GITHUB_CONTENTS_HASH, encoded);

			initialized.set(false);
		} else {
			// The database on the filesystem matches the same GitHub repos/orgs/users as
			// the current server instance, so no further action is required.
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

	@Override
	public void persistResourceChangeEvents(List<ResourceChangeEventJson> newEvents) {

		if (newEvents.size() == 0) {
			return;
		}

		File directory = new File(outputDirectory, "events");

		newEvents.stream().filter(e -> e.getTime() <= 0).findAny().ifPresent(e -> {
			throw new RuntimeException("One or more JSON files was missing a time.");
		});

		Long timeOfFirstEvent = newEvents.stream().map(e -> e.getTime()).sorted().findFirst().orElse(null);

		if (timeOfFirstEvent == null) {
			throw new RuntimeException("Time of first event was null: " + newEvents);
		}

		try {
			writeLock.lock();

			ObjectMapper om = new ObjectMapper();

			if (!directory.exists() && !directory.mkdirs()) {
				throw new RuntimeException("Unable to create directory: " + directory);
			}

			// Prevent collisions when different events occur together within the same
			// millisecond.
			long currTime = timeOfFirstEvent;
			File file;
			while (true) {

				file = new File(directory, "issue-" + currTime + ".json");
				if (file.exists()) {
					currTime++;
				} else {
					break;
				}

			}

			try {
				writeToFile(om.writeValueAsString(newEvents), file);
			} catch (JsonProcessingException e) {
				throw new RuntimeException(e);
			}

		} finally {
			writeLock.unlock();
		}

	}

	@Override
	public List<ResourceChangeEventJson> getRecentResourceChangeEvents(long timestampEqualOrGreater) {
		File directory = new File(outputDirectory, "events");

		// Keep events in database for only 8 days
		long expireTimestamp = System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(8, TimeUnit.DAYS);

		List<File> filesToDelete = new ArrayList<>();

		List<ResourceChangeEventJson> result = new ArrayList<>();
		try {
			readLock.lock();

			ObjectMapper om = new ObjectMapper();

			if (!directory.exists()) {
				return Collections.emptyList();
			}

			List<File> files = Arrays.asList(directory.listFiles()).stream()
					.filter(e -> e.getName().startsWith("issue-") && e.getName().endsWith(".json"))
					.collect(Collectors.toList());

			for (File f : files) {
				String name = f.getName();
				int index = name.indexOf("-");
				int endIndex = name.indexOf(".json");

				long timestamp = Long.parseLong(name.substring(index + 1, endIndex));

				if (timestamp >= timestampEqualOrGreater) {
					try {

						// It is possible for the filename timestamp to be larger than the actual
						// timestamp in the file, so we check that both are >= timestampEqualOrGreater.

						for (ResourceChangeEventJson rcej : om.readValue(readFromFile(f).get(),
								ResourceChangeEventJson[].class)) {

							if (rcej.getTime() >= timestampEqualOrGreater) {
								result.add(rcej);
							}

						}
					} catch (IOException ex) {
						throw new RuntimeException(ex);
					}
				}

				if (timestamp < expireTimestamp) {
					filesToDelete.add(f);
				}

			}

		} finally {
			readLock.unlock();
		}

		// Delete expired files.
		try {
			writeLock.lock();

			filesToDelete.forEach(e -> {
				if (e.exists() && !e.delete()) {
					System.err.println("* Unable to delete: " + e.getPath());
				}
			});

		} finally {
			writeLock.unlock();
		}

		// Sort ascending by timestamp;
		Collections.sort(result, (a, b) -> {
			return (int) (a.getTime() - b.getTime());
		});

		return result;

	}

}
