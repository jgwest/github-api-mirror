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

package com.githubapimirror;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.eclipse.egit.github.core.client.GitHubClient;
import org.kohsuke.github.AbuseLimitHandler;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.RateLimitHandler;

import com.githubapimirror.EventScan.EventScanData;
import com.githubapimirror.EventScan.ProcessIteratorReturnValue;
import com.githubapimirror.WorkQueue.OwnerContainer;
import com.githubapimirror.WorkQueue.RepositoryContainer;
import com.githubapimirror.db.Database;
import com.githubapimirror.db.InMemoryCacheDb;
import com.githubapimirror.db.PersistJsonDb;
import com.githubapimirror.shared.GHApiUtil;
import com.githubapimirror.shared.NewFileLogger;
import com.githubapimirror.shared.Owner;

/**
 * An instance of this class will mirror GitHub resources using the given
 * parameters. This includes initializing required internal resources
 * (databases, background threads), maintaining references to the internal state
 * of the process, and managing the lifecycle of that process.
 * 
 * To construct an instance of this class, call ZHServerInstance.builder().
 */
public class ServerInstance {

	private final WorkQueue queue;

	private final List<OwnerContainer> ghOwners;

	private final Database db;

	private final BackgroundSchedulerThread backgroundSchedulerThread;

	private final GitHubClient egitClient;

	private final GHLog log = GHLog.getInstance();

	/**
	 * Whether or not the GitHub service returns a valid rate limit value: returns
	 * false if GHE, true otherwise.
	 */
	private final boolean supportsRateLimit;

	private final GitHub githubClientInstance;

	private long rateLimitResetTime_synch_rateLimitLock = 0;

	private Integer lastRateLimitSeen_synch_rateLimitLock = null;

	private final Object rateLimitLock = new Object();

	private final NewFileLogger fileLogger;

	private ServerInstance(String username, String password, String serverName, List<String> orgNames,
			List<String> userRepos, List<RepoConstructorEntry> individualRepos, long pauseBetweenRequestsInMsecs,
			File dbDir, long timeBetweenEventScansInSeconds, GhmFilter filter, int numRequestsPerHour,
			File fileLogPath) {

		if (filter == null) {
			filter = new PermissiveFilter();
		}

		this.fileLogger = new NewFileLogger(fileLogPath != null ? fileLogPath.toPath() : null);

		List<String> individualRepoNames = individualRepos.stream().map(e -> e.getRepo()).collect(Collectors.toList());

		// Verify that we don't have both an org, and an individual repo under that org.
		// eg: asking the server to index both the eclipse org, and the
		// eclipse/che individual repo.
		List<String> ownersOfIndividualRepos = individualRepoNames.stream().map(e -> e.substring(0, e.indexOf("/")))
				.distinct().collect(Collectors.toList());
		if (ownersOfIndividualRepos.stream().anyMatch(e -> orgNames.contains(e))
				|| ownersOfIndividualRepos.stream().anyMatch(e -> userRepos.contains(e))) {
			throw new RuntimeException(
					"You cannot include an individual repo if you have also included the organization of that repo.");
		}

		db = new InMemoryCacheDb(new PersistJsonDb(dbDir));

		db.uninitializeDatabaseOnContentsMismatch(orgNames, userRepos, individualRepoNames);

		queue = new WorkQueue(this, db, numRequestsPerHour, pauseBetweenRequestsInMsecs);

		GitHub githubClient;
		boolean weSupportRateLimit = false;

		NextEventScanInNanos nextEventScanSettings = new NextEventScanInNanos(timeBetweenEventScansInSeconds);

		try {

			GitHubBuilder builder = GitHubBuilder.fromEnvironment();

			if (serverName.toLowerCase().endsWith("github.com")) {
				builder = builder.withPassword(username, password);
				egitClient = new GitHubClient();
			} else {
				builder = builder.withEndpoint("https://" + serverName + "/api/v3").withPassword(username, password);
				egitClient = new GitHubClient(serverName);
			}

			githubClient = builder.withRateLimitHandler(RateLimitHandler.FAIL)
					.withAbuseLimitHandler(AbuseLimitHandler.FAIL).build();
			this.githubClientInstance = githubClient;

			ghOwners = new ArrayList<>();

			egitClient.setCredentials(username, password);

			// If we have hit the API rate limit, keep trying to get the org/user until we
			// succeed.
			boolean success = false;
			do {

				try {

					if (githubClient.getRateLimit().remaining == 1000000) {
						// The Java GitHub we use returns 1000000 if Rate Limit is not enabled
						weSupportRateLimit = false;
					} else {
						weSupportRateLimit = true;
					}

					ghOwners.clear();

					if (orgNames != null) { // Resolve the orgs
						for (String orgName : orgNames) {
							GHOrganization org = githubClient.getOrganization(orgName);
							if (org == null) {
								throw new RuntimeException("GitHub org not found: " + orgName);
							}
							ghOwners.add(new OwnerContainer(org));
						}
					}

					if (userRepos != null) { // Resolve the user repos
						for (String userName : userRepos) {
							GHUser user = githubClient.getUser(userName);
							if (user == null) {
								throw new RuntimeException("GitHub user not found: " + userName);
							}
							ghOwners.add(new OwnerContainer(user));
						}
					}

					if (individualRepos != null) { // Resolve the individual repos

						Map<String /* org/user name */, List<RepositoryContainer>> irOwners = new HashMap<>();

						// Determine if the repo string refers to an org or a user repo, resolve it,
						// then add it to the individual repo list.
						for (RepoConstructorEntry indivRepoFor : individualRepos) {

							String fullRepoName = indivRepoFor.getRepo();
							int slashIndex = fullRepoName.indexOf("/");
							if (slashIndex == -1) {
								throw new RuntimeException("Invalid repository format: " + fullRepoName);
							}

							String ownerName = fullRepoName.substring(0, slashIndex);
							String repoName = fullRepoName.substring(slashIndex + 1);

							GHOrganization org = null;
							GHUser user = null;
							try {
								org = githubClient.getOrganization(ownerName);
							} catch (Exception e) {
								// Ignore; will try retrieving it as a user, next.
							}
							if (org == null) {
								user = githubClient.getUser(ownerName);
								if (user == null) {
									throw new RuntimeException("Unable to find user repo or org for: " + fullRepoName);
								}
							}

							Owner owner = Owner.org(ownerName);

							GHRepository repo;

							if (org != null) {
								repo = org.getRepository(repoName);
							} else {
								repo = user.getRepository(repoName);
							}

							if (repo == null) {
								throw new RuntimeException(
										"Unable to find user repo or org, after request to GitHub API: "
												+ fullRepoName);
							}

							nextEventScanSettings.setTimeBetweenEventScanInSeconds(
									indivRepoFor.getTimeBetweenEventScansInSeconds().orElse(null), repo);

							RepositoryContainer rc = new RepositoryContainer(owner, repo);

							List<RepositoryContainer> listOfReposPerOwner = irOwners.computeIfAbsent(ownerName,
									e -> new ArrayList<RepositoryContainer>());
							listOfReposPerOwner.add(rc);

						}

						irOwners.forEach((ownerName, ownerRepos) -> {
							if (ownerRepos.size() == 0) {
								return;
							}

							Owner owner = ownerRepos.get(0).getOwner();

							List<GHRepository> repositories = ownerRepos.stream().map(e -> e.getRepo())
									.collect(Collectors.toList());

							ghOwners.add(new OwnerContainer(repositories, owner));

						});

					} // end individual repos if

					success = true;
				} catch (Exception e) { // Blanket catch-all, so we can retry
					if (e.getMessage().contains("API rate limit reached")) {
						System.err.println("Rate limited reached: " + e.getClass().getName());
					} else {
						e.printStackTrace();
					}

					success = false;
					System.err.println("Failed in constructor, retrying in 60 seconds.");
					GHApiUtil.sleep(60 * 1000);
				}

			} while (!success);

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		this.supportsRateLimit = weSupportRateLimit;

		for (int x = 0; x < 5; x++) {
			WorkerThread wt = new WorkerThread(queue, filter);
			wt.start();
		}

		backgroundSchedulerThread = new BackgroundSchedulerThread(nextEventScanSettings);
		backgroundSchedulerThread.start();

	}

	public GitHubClient getEgitClient() {
		return egitClient;
	}

	public Database getDb() {
		return db;
	}

	public static ServerInstanceBuilder builder() {
		return new ServerInstanceBuilder();
	}

	public NewFileLogger getFileLogger() {
		return fileLogger;
	}

	/**
	 * Returns a Long[requests remaining, time remaining] if the GitHub service
	 * supports returning the # of requests remaining in quota, otherwise returns an
	 * empty optional (for example, this does not work with GHE)
	 */
	public Optional<Long[]> getRemainingRequestsPerSecond() {

		if (!this.supportsRateLimit) {
			return Optional.empty();
		}

		GHRateLimit rateLimit;

		while (true) {
			try {
				rateLimit = this.githubClientInstance.lastRateLimit();

				if (rateLimit == null) {
					log.logInfo("Do a (potentially) full get rate limit request in ServerInstance.");
					rateLimit = this.githubClientInstance.getRateLimit();
				}
				break;
			} catch (Exception e) {
				e.printStackTrace();
				log.logError("Unable to retrieve rate limit, sleeping 1 second, then trying again.");
				GHApiUtil.sleep(1000);
			}

		}

		long resetDate;

		synchronized (rateLimitLock) {

			// If reset time has elapsed, or if we detect the limit increase (implying a
			// reset).
			if (System.currentTimeMillis() > rateLimitResetTime_synch_rateLimitLock
					|| (lastRateLimitSeen_synch_rateLimitLock != null
							&& lastRateLimitSeen_synch_rateLimitLock < rateLimit.remaining)) {
				this.rateLimitResetTime_synch_rateLimitLock = rateLimit.getResetDate().getTime();

				log.logInfo("Updating rate limit reset time, now: "
						+ new Date(this.rateLimitResetTime_synch_rateLimitLock));
			}

			lastRateLimitSeen_synch_rateLimitLock = rateLimit.remaining;

			resetDate = this.rateLimitResetTime_synch_rateLimitLock;
		}

		long remainingTimeInSecs = (resetDate - System.currentTimeMillis()) / 1000;

		return Optional.of(new Long[] { (long) rateLimit.remaining, remainingTimeInSecs, (long) rateLimit.limit });

	}

	public void requestFullScan() {
		backgroundSchedulerThread.requestFullScan();
	}

	/**
	 * A single background thread is running at all times, for a single server
	 * instance. This thread is responsible for retrieving an updated copy of the
	 * most recent resources every X seconds, and also doing a full resource scan
	 * every day.
	 */
	private class BackgroundSchedulerThread extends Thread {

		private boolean fullScanInProgress = false;

		private final EventScanData data;

		private final AtomicBoolean fullScanRequested_synch = new AtomicBoolean(false);

		private final NextEventScanInNanos nextEventScanSettings;

		public BackgroundSchedulerThread(NextEventScanInNanos nextEventScanSettings) {
			setName(BackgroundSchedulerThread.class.getName());
			setDaemon(true);

			this.nextEventScanSettings = nextEventScanSettings;
			data = new EventScanData(db.getProcessedEvents());
		}

		/**
		 * This method is run every 60 seconds, but event scan is actually
		 * 'timeBetweenEventScanInNanos'
		 */
		private void innerRun(Map<Long /* year * 1000 + day_of_year */, Boolean> hasDailyScanRunToday)
				throws IOException {

			Calendar c = Calendar.getInstance();
			int hour = c.get(Calendar.HOUR_OF_DAY);
			int dayOfYear = c.get(Calendar.DAY_OF_YEAR);
			int year = c.get(Calendar.YEAR);

			Long lastFullScanStart = db.getLong(Database.LAST_FULL_SCAN_START).orElse(null);

			if (queue.availableWork() == 0 && queue.activeResources() == 0 && this.fullScanInProgress) {
				// A full scan is considered completed if it starts, and then the available work
				// dropped to 0.

				this.fullScanInProgress = false;

				log.logInfo("Full scan was detected as complete.");

			}

			boolean fullScanRequired = (hour == 3 || !getDb().isDatabaseInitialized() || lastFullScanStart == null);

			if (!fullScanRequired && queue.availableWork() + queue.activeResources() <= 10) {

				if (!this.fullScanInProgress && lastFullScanStart != null) {

					long finalLastFullScan = lastFullScanStart;

					// Perform an event scan on each of the owner containers, if scheduled.
					for (OwnerContainer oc : ghOwners) {

						HeartbeatThreadRunner<ProcessIteratorReturnValue> htr = new HeartbeatThreadRunner<ProcessIteratorReturnValue>(
								(threadRunnerRef) -> {
									return EventScan.doEventScan(oc, data, queue, finalLastFullScan,
											githubClientInstance, egitClient, nextEventScanSettings, threadRunnerRef);
								});

						ProcessIteratorReturnValue retVal;
						try {
							retVal = htr.run().orElse(null);
						} catch (Exception e) {
							if (e instanceof IOException) {
								throw (IOException) e;
							} else if (e instanceof RuntimeException) {
								throw (RuntimeException) e;
							} else {
								throw new RuntimeException(e);
							}
						}

						// Null is returned if a scan wasn't yet scheduled, OR an error occurred.
						if (retVal == null) {
							continue;
						}

						// Event scan can detect that a full scan is required
						if (retVal.isFullScanRequired() && !fullScanRequired) {
							log.logInfo("Setting 'runFullScan' to true based on event scan");
							fullScanRequired = true;
						}

						// For events that we processed during the scan, persist them to the DB
						List<String> newEventHashes = retVal.getNewEventHashes();
						db.addProcessedEvents(newEventHashes);
					}

				}
			}

			synchronized (fullScanRequested_synch) {
				if (!fullScanRequired && !fullScanInProgress && fullScanRequested_synch.get()) {
					fullScanRequired = true;
					fullScanRequested_synch.set(false);
				}
			}

			// Start a full scan if one is needed, and one hasn't already run today
			if (fullScanRequired && !fullScanInProgress) {

				if (!getDb().isDatabaseInitialized()) {
					getDb().initializeDatabase();
				}

				long value = year * 1000 + dayOfYear;
				if (!hasDailyScanRunToday.containsKey(value)) {
					hasDailyScanRunToday.put(value, true);

					log.logInfo("Beginning full scan.");

					this.fullScanInProgress = true;

					db.persistLong(Database.LAST_FULL_SCAN_START, System.currentTimeMillis());
					db.clearProcessedEvents();
					data.clear();

					ghOwners.forEach(e -> {
						queue.addOwner(e);
					});

				}

			}
		}

		@Override
		public void run() {

			// Whether the daily scan has run today
			Map<Long /* (year * 1000) + day_of_year */, Boolean /* not used */> hasDailyScanRunToday = new HashMap<>();

			while (true) {

				log.logDebug("Background thread wake up.");

				try {
					innerRun(hasDailyScanRunToday);
				} catch (Exception e) {
					// Log and ignore
					log.logError("Exception occured in " + this.getClass().getSimpleName() + ",", e);
				}

				GHApiUtil.sleep(20 * 1000);

			}

		}

		public void requestFullScan() {
			synchronized (fullScanRequested_synch) {
				fullScanRequested_synch.set(true);
			}
		}

	}

	/**
	 * Call ServerInstance.builder() to get an instance of this class; this class is
	 * used to construct an instance of ServerInstance using a fluent builder API.
	 */
	public static class ServerInstanceBuilder {

		private String username;
		private String password;
		private String serverName;
		private List<String> orgNames = new ArrayList<>();
		private List<String> userRepos = new ArrayList<>();
		private List<RepoConstructorEntry> individualRepos = new ArrayList<>();
		private File dbDir;
		private GhmFilter filter;

		private File fileLoggingPath;

		private long timeBetweenEventScansInSeconds = TimeUnit.SECONDS.convert(10, TimeUnit.MINUTES);

		private long pauseBetweenRequestsInMsecs = 500;

		private int numRequestsPerHour = 5000;

		/** default to minimum */

		private ServerInstanceBuilder() {
		}

		public ServerInstanceBuilder dbDir(File dbDir) {
			this.dbDir = dbDir;
			return this;
		}

		public ServerInstanceBuilder dbDir(String dbDirStr) {
			this.dbDir = new File(dbDirStr);
			return this;
		}

		public ServerInstanceBuilder filter(GhmFilter filter) {
			this.filter = filter;
			return this;

		}

		public ServerInstanceBuilder serverName(String serverName) {
			this.serverName = serverName;
			return this;
		}

		public ServerInstanceBuilder org(String str) {
			this.orgNames = Arrays.asList(new String[] { str });
			return this;
		}

		public ServerInstanceBuilder orgs(List<String> strList) {
			this.orgNames = strList;
			return this;
		}

		public ServerInstanceBuilder orgs(String... strList) {
			this.orgNames = Arrays.asList(strList);
			return this;
		}

		// The user equivalent to organization, eg https://github.com/jgwest
		public ServerInstanceBuilder userRepos(String... userList) {
			this.userRepos = Arrays.asList(userList);
			return this;
		}
		
		// The user equivalent to organization, eg https://github.com/jgwest
		public ServerInstanceBuilder userRepos(List<String> userReposList) {
			this.userRepos = userReposList;
			return this;
		}

		// 'repo' should be in the form of '(owner name)/repository name'
		public ServerInstanceBuilder individualRepos(String repo, Long timeBetweenEventScanInSecondsParam) {
			this.individualRepos.add(new RepoConstructorEntry(repo, timeBetweenEventScanInSecondsParam));
			return this;

		}

		public ServerInstanceBuilder credentials(String username, String password) {
			this.username = username;
			this.password = password;
			return this;
		}

		public ServerInstanceBuilder numRequestsPerHour(int numReqsPerHour) {
			this.numRequestsPerHour = numReqsPerHour;
			return this;
		}

		public ServerInstanceBuilder timeBetweenEventScansInSeconds(long timeBetweenEventScansInSeconds) {
			this.timeBetweenEventScansInSeconds = timeBetweenEventScansInSeconds;
			return this;
		}

		public ServerInstanceBuilder pauseBetweenRequestsInMsecs(long pauseBetweenRequestsInMsecs) {
			this.pauseBetweenRequestsInMsecs = pauseBetweenRequestsInMsecs;
			return this;
		}

		public ServerInstanceBuilder fileLoggingPath(File fileLoggingPath) {
			this.fileLoggingPath = fileLoggingPath;
			return this;
		}

		public ServerInstance build() {
			return new ServerInstance(username, password, serverName, orgNames, userRepos, individualRepos,
					pauseBetweenRequestsInMsecs, dbDir, timeBetweenEventScansInSeconds, filter, numRequestsPerHour,
					fileLoggingPath);
		}

	}

	/** If no filter is specified, we use a filter that accepts all resources. */
	private static class PermissiveFilter implements GhmFilter {

		@Override
		public boolean processOwner(Owner owner) {
			return true;
		}

		@Override
		public boolean processRepo(Owner owner, String repoName) {
			return true;
		}

		@Override
		public boolean processIssue(Owner owner, String repoName, int issue) {
			return true;
		}

		@Override
		public boolean processIssueEvents(Owner owner, String repoName, int issue) {
			return true;
		}

		@Override
		public boolean processUser(String loginName) {
			return true;
		}

	}

	public static class RepoConstructorEntry {
		String repo;
		Long timeBetweenEventScansInSeconds = null;

		public RepoConstructorEntry(String repo, Long timeBetweenEventScansInSeconds) {
			this.repo = repo;
			this.timeBetweenEventScansInSeconds = timeBetweenEventScansInSeconds;
		}

		public String getRepo() {
			return repo;
		}

		public Optional<Long> getTimeBetweenEventScansInSeconds() {
			return Optional.ofNullable(timeBetweenEventScansInSeconds);
		}

	}

	public static class NextEventScanInNanos {

		private final HashMap<String /* org/user name ( + repo name) */, Long> timeBetweenEventScanInNanos_synch = new HashMap<>();

		private final HashMap<String /* org/user name ( + repo name) */, Long> nextEventScanInNanos_synch = new HashMap<>();

		private final long globalTimeBetweenEventScansInNanos;

		public NextEventScanInNanos(long timeBetweenEventScansInSeconds) {
			this.globalTimeBetweenEventScansInNanos = TimeUnit.NANOSECONDS.convert(timeBetweenEventScansInSeconds,
					TimeUnit.SECONDS);
		}

		public void setTimeBetweenEventScanInSeconds(Long secondsParam, GHRepository repo) {
			String key = repo.getOwnerName() + "/" + repo.getName();

			// TODO: Remove me once debugging is done.
//			System.out.println("setTimeBetweenEventScanInSeconds -> " + secondsParam + " " + repo.getName());

			long inNanoSeconds;

			if (secondsParam != null) {
				inNanoSeconds = TimeUnit.NANOSECONDS.convert(secondsParam, TimeUnit.SECONDS);
			} else {
				inNanoSeconds = globalTimeBetweenEventScansInNanos;
			}

			synchronized (timeBetweenEventScanInNanos_synch) {

				timeBetweenEventScanInNanos_synch.put(key, inNanoSeconds);

			}
		}

		private long timeBetweenEventScanInNanos(GHRepository repo) {

			String key = repo.getOwnerName() + "/" + repo.getName();

			Long timeBetweenEventScanInNanos;

			synchronized (timeBetweenEventScanInNanos_synch) {
				timeBetweenEventScanInNanos = timeBetweenEventScanInNanos_synch.get(key);
			}

			if (timeBetweenEventScanInNanos == null) {
				timeBetweenEventScanInNanos = globalTimeBetweenEventScansInNanos;
			}

			return timeBetweenEventScanInNanos;

		}

		public boolean shouldDoNextEventScan(GHRepository repo) {
			return shouldDoNextEventScan(repo.getOwnerName() + "/" + repo.getName());
		}

		public void updateNextEventScan(GHRepository repo) {
			if (!shouldDoNextEventScan(repo)) {
				return;
			}

			String key = repo.getOwnerName() + "/" + repo.getName();

			synchronized (nextEventScanInNanos_synch) {

				nextEventScanInNanos_synch.put(key, timeBetweenEventScanInNanos(repo) + System.nanoTime());
			}

		}

		public boolean shouldDoNextEventScan(GHOrganization org) {
			return shouldDoNextEventScan(org.getLogin());
		}

		public boolean shouldDoNextEventScan(GHUser user) {
			return shouldDoNextEventScan(user.getLogin());
		}

		private boolean shouldDoNextEventScan(String key) {

			Long nextScan;
			synchronized (nextEventScanInNanos_synch) {
				nextScan = nextEventScanInNanos_synch.get(key);
			}

			boolean result;

			if (nextScan == null || nextScan == 0 || System.nanoTime() > nextScan) {
				result = true;
			} else {
				result = false;
			}

			// TODO: Remove me once debugging is done.
//			String debugStr = "null";
//			if (nextScan != null) {
//				debugStr = "" + TimeUnit.SECONDS.convert(nextScan - System.nanoTime(), TimeUnit.NANOSECONDS);
//			}
//
//			System.out.println(key + " " + result + " (" + debugStr + ")");

			return result;

		}

		public void updateNextEventScan(GHUser user) {

			if (!shouldDoNextEventScan(user)) {
				return;
			}

			String key = user.getLogin();

			synchronized (nextEventScanInNanos_synch) {

				nextEventScanInNanos_synch.put(key, globalTimeBetweenEventScansInNanos + System.nanoTime());
			}

		}

		public void updateNextEventScan(GHOrganization org) {
			if (!shouldDoNextEventScan(org)) {
				return;
			}

			String key = org.getLogin();

			synchronized (nextEventScanInNanos_synch) {

				nextEventScanInNanos_synch.put(key, globalTimeBetweenEventScansInNanos + System.nanoTime());
			}

		}

	}
}
