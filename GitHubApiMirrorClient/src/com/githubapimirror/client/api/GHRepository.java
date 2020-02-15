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

package com.githubapimirror.client.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.githubapimirror.client.GHApiMirrorHttpClient;
import com.githubapimirror.shared.GHApiUtil;
import com.githubapimirror.shared.Owner;
import com.githubapimirror.shared.json.BulkIssuesJson;
import com.githubapimirror.shared.json.IssueJson;
import com.githubapimirror.shared.json.RepositoryJson;

/**
 * Represents a GitHub repository and allows querying the GitHub mirror
 * repository API.
 * 
 * Repository issues may be retrieved either through the standard or bulk API.
 */
public class GHRepository {

	private final String name;

	private final Owner owner;

	private final long repositoryId;

	private final GHConnectInfo connInfo;

	public GHRepository(RepositoryJson json, GHConnectInfo connection) {
		this.name = json.getName();
		this.owner = json.calculateOwner();
		this.connInfo = connection;
		this.repositoryId = json.getRepositoryId();
	}

	public String getName() {
		return name;
	}

	public String getFullName() {
		return owner.getName() + "/" + name;
	}

	public String getOwnerName() {
		return owner.getName();
	}

	/** Retrieve a single issue, or null if not found. */
	public GHIssue getIssue(int id) {
		GHApiMirrorHttpClient client = connInfo.getClient();

		IssueJson jsonIssue = client.getIssue(owner, name, id).orElse(null);

		if (jsonIssue == null) {
			return null;
		}

		GHIssue issue = new GHIssue(jsonIssue, connInfo);
		return issue;

	}

	/** Retrieve issues, one at a time, on the current thread. */
	public List<GHIssue> listIssues() {

		GHApiMirrorHttpClient client = connInfo.getClient();

		RepositoryJson json = client.getRepository(owner, name).orElse(null);

		Integer firstIssue = json.getFirstIssue();
		Integer lastIssue = json.getLastIssue();

		if (firstIssue == null || lastIssue == null) {
			return Collections.emptyList();
		}

		List<GHIssue> result = new ArrayList<>();

		for (int x = firstIssue; x <= lastIssue; x++) {

			IssueJson jsonIssue = client.getIssue(owner, name, x).orElse(null);

			if (jsonIssue == null) {
				continue;
			}

			GHIssue issue = new GHIssue(jsonIssue, connInfo);
			result.add(issue);
		}

		return result;

	}

	public long getRepositoryId() {
		return repositoryId;
	}

	/** Retrieves issues, in groups of 30, using multiple threads. */
	public List<GHIssue> bulkListIssues() {

		GHApiMirrorHttpClient client = connInfo.getClient();

		RepositoryJson json = client.getRepository(owner, name).orElse(null);

		Integer firstIssue = json.getFirstIssue();
		Integer lastIssue = json.getLastIssue();

		if (firstIssue == null || lastIssue == null) {
			return Collections.emptyList();
		}

		// Convert the range of issues to acquire into blocks of 30
		List<WorkUnit> workUnits = new ArrayList<>();
		{
			List<Integer> currUnit = new ArrayList<>();
			for (int x = firstIssue; x <= lastIssue; x++) {

				currUnit.add(x);

				if (currUnit.size() >= 30) {
					workUnits.add(convertToSimpleWorkUnit(currUnit));
					currUnit.clear();
				}

			}

			if (currUnit.size() > 0) {
				workUnits.add(convertToSimpleWorkUnit(currUnit));

			}
		}

		return executeBulkListIssuesWorkerThreads(workUnits);
	}

	/** Retrieves issues, in groups of 30, using multiple threads. */
	public List<GHIssue> bulkListIssues(List<Integer> individualIssues) {

		// Convert the range of issues to acquire into blocks of 30
		List<WorkUnit> workUnits = new ArrayList<>();
		{
			List<Integer> currUnit = new ArrayList<>();

			for (int currIssue : individualIssues) {

				currUnit.add(currIssue);

				if (currUnit.size() >= 30) {
					workUnits.add(new WorkUnit(new ArrayList<Integer>(currUnit)));
					currUnit.clear();
				}

			}

			if (currUnit.size() > 0) {
				workUnits.add(new WorkUnit(new ArrayList<Integer>(currUnit)));
			}
		}

		return executeBulkListIssuesWorkerThreads(workUnits);
	}

	private List<GHIssue> executeBulkListIssuesWorkerThreads(List<WorkUnit> workUnits) {

		Throwable throwable = null;

		List<GHIssue> results = new ArrayList<>();
		List<BulkListIssuesWorkerThread> activeThreads = new ArrayList<>();

		int count = 0;

		outer_while: while (workUnits.size() > 0 || activeThreads.size() > 0) {

			// Clear completed threads
			for (Iterator<BulkListIssuesWorkerThread> it = activeThreads.iterator(); it.hasNext();) {
				BulkListIssuesWorkerThread wt = it.next();

				if (wt.getThrowable().isPresent()) {
					// On first failing worker thread, bail out of the loops
					throwable = wt.getThrowable().get();
					break outer_while;

				} else if (wt.isComplete()) {
					wt.getResults().ifPresent(e -> {

						for (IssueJson jsonIssue : e) {
							// Ignore old issues that do not conform to the new JSON
							try {
								GHIssue issue = new GHIssue(jsonIssue, connInfo);
								results.add(issue);
							} catch (Exception ex) {
								System.err.println(ex.getClass().getName() + " for " + jsonIssue.getParentRepo() + "/"
										+ jsonIssue.getNumber());
							}
						}

					});
					it.remove();
				}
			}

			// Queue work to new threads
			while (activeThreads.size() < 10 && workUnits.size() > 0) {

				WorkUnit workUnit = workUnits.remove(0);

				BulkListIssuesWorkerThread wt = new BulkListIssuesWorkerThread(owner, name, workUnit,
						connInfo.getClient());
				wt.start();
				activeThreads.add(wt);
			}

			count++;

			if (count % 5 == 0) {
				GHApiUtil.sleep(20);
			}

		}

		if (throwable != null) {
			try {
				activeThreads.forEach(e -> e.interrupt());
			} catch (Exception e) {
				/* ignore */
			}

			GHApiUtil.throwAsUnchecked(throwable);
		}

		return results;
	}

	private static WorkUnit convertToSimpleWorkUnit(List<Integer> currUnit) {
		int unitStart = currUnit.get(0);
		int unitEnd = currUnit.get(currUnit.size() - 1);

		return new WorkUnit(unitStart, unitEnd);
	}

	/**
	 * Returns a list of repos matching the given names, or if one or more of the
	 * names could not be located then returns Optional.empty()
	 */
	public static Optional<List<GHRepository>> findByNames(List<GHRepository> repoList, String... names) {
		List<String> namesList = Arrays.asList(names);

		List<GHRepository> result = repoList.stream().filter(e -> {
			// e.getName() does not include the owner name
			return namesList.contains(e.getName());
		}).collect(Collectors.toList());

		if (result.size() != names.length) {

			List<String> reposFound = result.stream().map(e -> e.getName()).collect(Collectors.toList());

			Arrays.asList(names).stream().filter(e -> !reposFound.contains(e)).forEach(e -> {
				System.err.println("* Unable to find repo name: " + e);
			});

			return Optional.empty();
		} else {
			return Optional.of(result);
		}
	}

	private static class WorkUnit {
		private final Integer startRange;
		private final Integer endRange;

		private final List<Integer> issueList;

		public WorkUnit(int startRange, int endRange) {
			this.startRange = startRange;
			this.endRange = endRange;
			this.issueList = null;
		}

		public WorkUnit(List<Integer> issueList) {
			this.startRange = null;
			this.endRange = null;
			this.issueList = issueList;
		}

		public Integer getStartRange() {
			return startRange;
		}

		public Integer getEndRange() {
			return endRange;
		}

		public List<Integer> getIssueList() {
			return issueList;
		}

	}

	/**
	 * Used by bulkListIssues(): This worker thread issues requests to the mirror
	 * API, in groups of 30.
	 * 
	 * This class is thread safe.
	 */
	private static class BulkListIssuesWorkerThread extends Thread {

		private final WorkUnit workUnit;

		private final GHApiMirrorHttpClient client;

		private final List<IssueJson> results_synch_lock = new ArrayList<>();

		private final Object lock = new Object();

		private final Owner owner;

		private final String repoName;

		private boolean threadComplete_synch_lock = false;

		private Optional<Throwable> throwable_sync_lock = Optional.empty();

		public BulkListIssuesWorkerThread(Owner owner, String repo, WorkUnit workUnit, GHApiMirrorHttpClient client) {
			setName(BulkListIssuesWorkerThread.class.getName());
			this.workUnit = workUnit;
			this.client = client;
			this.repoName = repo;
			this.owner = owner;
		}

		@Override
		public void run() {
			try {

				GHApiUtil.waitForPass(3, false, 2000, 2, 10 * 1000, () -> {
					// Issue a bulk issue request up to 3 times, retrying on failure, otherwise
					// throw the last exception.
					BulkIssuesJson json = null;
					if (workUnit.getStartRange() != null && workUnit.getEndRange() != null) {
						json = client.getBulkIssues(owner, repoName, workUnit.getStartRange(), workUnit.getEndRange())
								.orElse(null);

					} else if (workUnit.getIssueList() != null) {

						json = client.getBulkIssues(owner, repoName, workUnit.getIssueList()).orElse(null);

					} else {
						throw new RuntimeException("Invalid worker thread request.");
					}

					if (json != null) {
						synchronized (lock) {
							results_synch_lock.addAll(json.getIssues());
						}
					}
				});

			} catch (Throwable t) {
				synchronized (lock) {
					this.throwable_sync_lock = Optional.of(t);
				}
				GHApiUtil.throwAsUnchecked(t);

			} finally {
				synchronized (lock) {
					threadComplete_synch_lock = true;
				}
			}
		}

		public Optional<Throwable> getThrowable() {
			return throwable_sync_lock;
		}

		public boolean isComplete() {
			synchronized (lock) {
				return threadComplete_synch_lock;
			}
		}

		Optional<List<IssueJson>> getResults() {
			synchronized (lock) {

				return Optional.of(results_synch_lock);

			}

		}
	}

}
