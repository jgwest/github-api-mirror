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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;

import com.githubapimirror.db.Database;
import com.githubapimirror.shared.GHApiUtil;
import com.githubapimirror.shared.Owner;

/**
 * Maintains a list of all of the orgs/repositories/issues/users that are
 * currently waiting to be processed by a worker thread.
 * 
 * The WorkerThread thread may call an instance of this class, in order to: add
 * additional work, query if work is available, and poll for new work by type.
 * 
 * This class is thread safe.
 */
public class WorkQueue {

	private final Object lock = new Object();

	private final List<OwnerContainer> ownersToProcess_synch_lock = new ArrayList<>();
	private final List<RepositoryContainer> repositoriesToProcess_synch_lock = new ArrayList<>();
	private final List<IssueContainer> issuesToProcess_synch_lock = new ArrayList<>();
	private final List<GHUser> usersToProcess_synch_lock = new ArrayList<>();

	/**
	 * Users are mostly immutable, so we only acquire them once. We use this to
	 * avoid reacquiring them.
	 */
	private final Map<String /* user login */, Boolean> acquiredUsers_synch_lock = new HashMap<>();

	private final Database db;

	private final ServerInstance serverInstance;

	private long minimumElapsedTimeBetweenWorkInNanos_synch_lock;

	private long nextWorkAvailableInNanos_synch_lock = Long.MIN_VALUE;

	private static final GHLog log = GHLog.getInstance();

	public WorkQueue(ServerInstance serverInstance, Database db, int numRequestsPerHour) {
		this.db = db;
		this.serverInstance = serverInstance;

		this.minimumElapsedTimeBetweenWorkInNanos_synch_lock = TimeUnit.NANOSECONDS.convert(1, TimeUnit.HOURS)
				/ numRequestsPerHour;
	}

	public void addOwner(OwnerContainer oc) {
		synchronized (lock) {
			if (!ownersToProcess_synch_lock.contains(oc)) {
				ownersToProcess_synch_lock.add(oc);
				lock.notify();
			}
		}
	}

	public void addRepository(Owner owner, GHRepository repository) {

		RepositoryContainer rc = new RepositoryContainer(owner, repository);

		synchronized (lock) {
			if (!repositoriesToProcess_synch_lock.contains(rc)) {
				repositoriesToProcess_synch_lock.add(rc);
				lock.notify();
			}
		}
	}

	public void addIssue(GHIssue issue, Owner owner) {

		IssueContainer container = new IssueContainer(issue, null, owner);

		synchronized (lock) {

			if (!issuesToProcess_synch_lock.contains(container)) {
				issuesToProcess_synch_lock.add(container);
				lock.notify();
			}
		}
	}

	public void addIssue(GHIssue issue, GHRepository repository, Owner owner) {

		IssueContainer container = new IssueContainer(issue, repository, owner);

		synchronized (lock) {
			if (!issuesToProcess_synch_lock.contains(container)) {
				issuesToProcess_synch_lock.add(container);
				lock.notify();
			}
		}
	}

	public void addUser(GHUser user) {
		synchronized (lock) {
			if (!usersToProcess_synch_lock.contains(user) && !acquiredUsers_synch_lock.containsKey(user.getLogin())) {
				usersToProcess_synch_lock.add(user);
				acquiredUsers_synch_lock.put(user.getLogin(), true);
				lock.notify();
			}
		}
	}

	public void addUserRetry(GHUser user) {
		// Unlike addUser(), this method ignores whether the user is in acquiredUsers
		synchronized (lock) {
			if (!usersToProcess_synch_lock.contains(user)) {
				usersToProcess_synch_lock.add(user);
				acquiredUsers_synch_lock.put(user.getLogin(), true);
				lock.notify();
			}
		}
	}

	public long availableWork() {
		long workAvailable = 0;

		synchronized (lock) {
			workAvailable += ownersToProcess_synch_lock.size();
			workAvailable += repositoriesToProcess_synch_lock.size();
			workAvailable += issuesToProcess_synch_lock.size();
			workAvailable += usersToProcess_synch_lock.size();
		}

		return workAvailable;

	}

	private boolean checkNextWorkAvailableTime() {
		synchronized (lock) {
			return nextWorkAvailableInNanos_synch_lock < System.nanoTime();
		}
	}

	private void updateNextWorkAvailableTime(int estimatedRequests) {
		Optional<Long[]> remainingRequestsPerSecond = serverInstance.getRemainingRequestsPerSecond();

		synchronized (lock) {

			// Dynamically adjust 'minimumElapsedTimeBetweenWorkInNanos' based on the
			// current quota from the server, if the server supports it (for example, GHE
			// doesn't support rate limiting, though server admins may use rate limiting
			// middleware).
			if (remainingRequestsPerSecond.isPresent()) {

				long requestsRemaining = remainingRequestsPerSecond.get()[0];
				long secondsRemaining = remainingRequestsPerSecond.get()[1];

				if (secondsRemaining < 0) {
					secondsRemaining = 0;
				}
				
				if(requestsRemaining <= 0) {
					requestsRemaining = 1;
				}

				log.logDebug("Remaining: " + requestsRemaining + " " + secondsRemaining + " -> "
						+ ((secondsRemaining * 1000) / requestsRemaining));

				this.minimumElapsedTimeBetweenWorkInNanos_synch_lock = TimeUnit.NANOSECONDS.convert(secondsRemaining,
						TimeUnit.SECONDS) / requestsRemaining;

				nextWorkAvailableInNanos_synch_lock = System.nanoTime()
						+ minimumElapsedTimeBetweenWorkInNanos_synch_lock;

			} else {
				// We use 'estimatedRequests', which is a guess at the number of requests used
				// by the last server API call.
				nextWorkAvailableInNanos_synch_lock = System.nanoTime()
						+ estimatedRequests * minimumElapsedTimeBetweenWorkInNanos_synch_lock;
			}

		}
	}

	public void waitForAvailableWork() {
		synchronized (lock) {
			while (true) {

				if (!checkNextWorkAvailableTime()) {
					try {
						lock.wait(20);
					} catch (InterruptedException e) {
						GHApiUtil.throwAsUnchecked(e);
					}
				} else {
					if (availableWork() > 0) {
						return;
					}
					try {
						lock.wait(20);
					} catch (InterruptedException e) {
						GHApiUtil.throwAsUnchecked(e);
					}
				}

			}
		}

	}

	public Optional<OwnerContainer> pollOwner() {
		synchronized (lock) {
			if (!checkNextWorkAvailableTime()) {
				return Optional.empty();
			}

			if (ownersToProcess_synch_lock.size() == 0) {
				return Optional.empty();
			}

			updateNextWorkAvailableTime(5);

			return Optional.of(ownersToProcess_synch_lock.remove(0));
		}
	}

	public Optional<RepositoryContainer> pollRepository() {
		synchronized (lock) {
			if (!checkNextWorkAvailableTime()) {
				return Optional.empty();
			}

			if (repositoriesToProcess_synch_lock.size() == 0) {
				return Optional.empty();
			}

			updateNextWorkAvailableTime(20);

			return Optional.of(repositoriesToProcess_synch_lock.remove(0));
		}

	}

	public Optional<IssueContainer> pollIssue() {
		synchronized (lock) {
			if (!checkNextWorkAvailableTime()) {
				return Optional.empty();
			}

			if (issuesToProcess_synch_lock.size() == 0) {
				return Optional.empty();
			}

			updateNextWorkAvailableTime(3);

			return Optional.of(issuesToProcess_synch_lock.remove(0));
		}
	}

	public Optional<GHUser> pollUser() {
		synchronized (lock) {
			if (!checkNextWorkAvailableTime()) {
				return Optional.empty();
			}

			if (usersToProcess_synch_lock.size() == 0) {
				return Optional.empty();
			}

			updateNextWorkAvailableTime(1);

			return Optional.of(usersToProcess_synch_lock.remove(0));
		}
	}

	public void waitForComplete(long howLongToWaitForNoNewWorkInMsecs) {

		long howLongToWaitInNanos = TimeUnit.NANOSECONDS.convert(howLongToWaitForNoNewWorkInMsecs,
				TimeUnit.MILLISECONDS);

		long timeSinceLastWorkSeenInNanos = System.nanoTime();

		while (true) {
			long workAvailable = availableWork();

			if (workAvailable > 0) {
				timeSinceLastWorkSeenInNanos = System.nanoTime();
			}

			if ((System.nanoTime() - timeSinceLastWorkSeenInNanos) > howLongToWaitInNanos) {
				return;
			}

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				GHApiUtil.throwAsUnchecked(e);
			}

		}
	}

	Database getDb() {
		return db;
	}

	ServerInstance getServerInstance() {
		return serverInstance;
	}

	/**
	 * A piece of a work in the work queue, specifically either an Org/User, plus
	 * additional required fields.
	 */
	public static class OwnerContainer {

		enum Type {
			ORG, USER, REPO_LIST
		};

		private final GHOrganization org;
		private final GHUser user;
		private final List<GHRepository> individualRepos;

		private final Owner owner;

		private final Type type;

		private final String equalsKey;

		public OwnerContainer(GHOrganization org) {
			this.org = org;
			this.user = null;
			this.individualRepos = null;
			this.owner = Owner.org(org.getLogin());
			this.type = Type.ORG;

			this.equalsKey = calculateKey();
		}

		public OwnerContainer(GHUser user) {
			this.user = user;
			this.org = null;
			this.individualRepos = null;
			this.owner = Owner.user(user.getLogin());
			this.type = Type.USER;
			this.equalsKey = calculateKey();
		}

		public OwnerContainer(List<GHRepository> individualRepos, Owner owner) {
			this.user = null;
			this.org = null;
			this.individualRepos = Collections.unmodifiableList(new ArrayList<GHRepository>(individualRepos));
			this.owner = owner;
			this.type = Type.REPO_LIST;
			this.equalsKey = calculateKey();
		}

		public GHOrganization getOrg() {
			return org;
		}

		public GHUser getUser() {
			return user;
		}

		public List<GHRepository> getIndividualRepos() {
			return individualRepos;
		}

		public Owner getOwner() {
			return owner;
		}

		public Type getType() {
			return type;
		}

		private String calculateKey() {

			StringBuilder sb = new StringBuilder();
			sb.append(org != null ? org.getLogin() : "null");
			sb.append("-");
			sb.append(user != null ? user.getLogin() : "null");
			sb.append("-");
			if (individualRepos != null) {
				individualRepos.forEach(e -> {
					sb.append(e.getFullName() + "/");
				});

			} else {
				sb.append("null");
			}

			return sb.toString();
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof OwnerContainer)) {
				return false;
			}
			OwnerContainer other = (OwnerContainer) obj;

			return other.equalsKey.equals(this.equalsKey);

		}

	}

	/**
	 * A piece of a work in the work queue, specifically a Repository, plus
	 * additional required fields.
	 */
	public static class RepositoryContainer {
		private final Owner owner;

		private final GHRepository repo;

		public RepositoryContainer(Owner owner, GHRepository repo) {
			this.owner = owner;
			this.repo = repo;
		}

		public Owner getOwner() {
			return owner;
		}

		public GHRepository getRepo() {
			return repo;
		}

		private String calculateKey() {
			StringBuilder sb = new StringBuilder();

			sb.append(owner.getOrgNameOrNull());
			sb.append("-");
			sb.append(owner.getUserNameOrNull());
			sb.append("-");

			GHRepository localRepo = getRepo();
			sb.append(localRepo.getName());

			return sb.toString();
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof RepositoryContainer)) {
				return false;
			}
			RepositoryContainer other = (RepositoryContainer) obj;

			return other.calculateKey().contentEquals(calculateKey());

		}
	}

	/**
	 * A piece of a work in the work queue, specifically an Issue, plus additional
	 * required fields.
	 */
	public static class IssueContainer {
		private final GHIssue issue;
		private final GHRepository repo;

		private final Owner owner;

		public IssueContainer(GHIssue issue, GHRepository repo, Owner owner) {
			if (owner == null) {
				throw new IllegalArgumentException();
			}

			this.issue = issue;
			this.repo = repo;
			this.owner = owner;
		}

		public GHIssue getIssue() {
			return issue;
		}

		public Optional<GHRepository> getRepo() {
			// If this is empty, you can get the repository by calling
			// issue.getRepository();
			return Optional.ofNullable(repo);
		}

		public Owner getOwner() {
			return owner;
		}

		private String calculateKey() {
			StringBuilder sb = new StringBuilder();

			String repoName = null;
			GHRepository localRepo = getRepo().orElse(null);
			if (localRepo != null) {
				repoName = localRepo.getName();
			} else {
				repoName = issue.getRepository().getName();
			}

			sb.append(owner.getOrgNameOrNull());
			sb.append("-");
			sb.append(owner.getUserNameOrNull());
			sb.append("-");
			sb.append(repoName);
			sb.append("-");
			sb.append(issue.getNumber());

			return sb.toString();
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof IssueContainer)) {
				return false;
			}
			IssueContainer other = (IssueContainer) obj;

			// Fast path
			if (other.getIssue().getNumber() != this.getIssue().getNumber()) {
				return false;
			}

			return other.calculateKey().equals(this.calculateKey());

		}

	}
}
