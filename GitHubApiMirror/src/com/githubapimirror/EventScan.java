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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventInfo;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHEventPayload.Issue;
import org.kohsuke.github.GHEventPayload.IssueComment;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.PagedIterator;

import com.githubapimirror.WorkQueue.OwnerContainer;
import com.githubapimirror.shared.Owner;

/**
 * In order to ensure that our GitHub mirror database is up-to-date, we
 * frequently scan the list of org/user events that are returned by the
 * listEvents() API. This allows us to determine which issues have changed
 * recently, without needing to a full scan of all the issues in the org/user
 * repository.
 * 
 * This class scans through the list of recent events for an org/repo, and
 * filters out event types we are not specifically interested in.
 * 
 * Next, since we don't want to process events that we have already previously
 * processed, we hash the event contents and check the database for a matching
 * hash. If a match is not found, we process the event and add it to the
 * database.
 */
public class EventScan {

	private static final boolean DEBUG = false;

	private static final GHLog log = GHLog.getInstance();

	public static List<String> doEventScan(OwnerContainer ownerContainer, EventScanData data, WorkQueue workQueue)
			throws IOException {

		String ownerName;

		PagedIterator<GHEventInfo> it;
		if (ownerContainer.getType() == OwnerContainer.Type.ORG) {
			GHOrganization org = ownerContainer.getOrg();
			ownerName = org.getLogin();
			it = org.listEvents().iterator();
			return processIterator(it, Owner.org(ownerName), data, workQueue);

		} else if (ownerContainer.getType() == OwnerContainer.Type.USER) {
			GHUser user = ownerContainer.getUser();
			ownerName = user.getLogin();
			it = user.listEvents().iterator();
			return processIterator(it, Owner.user(ownerName), data, workQueue);
			// TODO: Not actually sure this will work for other users.

		} else if (ownerContainer.getType() == OwnerContainer.Type.REPO_LIST) {

			List<String> eventHashResult = new ArrayList<>();

			for (GHRepository repo : ownerContainer.getIndividualRepos()) {
				List<String> currRepoHash = processIterator(repo.listEvents().iterator(), ownerContainer.getOwner(),
						data, workQueue);
				eventHashResult.addAll(currRepoHash);
			}

			return eventHashResult;

		} else {
			throw new RuntimeException("Unrecognized owner type: " + ownerContainer);
		}

	}

	/**
	 * @return Returns the hashes of the new events that we have seen, for storage
	 *         in the DB
	 */
	@SuppressWarnings("unused")
	private static List<String> processIterator(PagedIterator<GHEventInfo> it, final Owner owner2, EventScanData data,
			WorkQueue workQueue) throws IOException {

		List<EventScanEntry> toScan = new ArrayList<>();

		event_loop: for (; it.hasNext();) {

			GHEventInfo g = it.next();

			GHEvent eventType = g.getType();

			GHRepository ghRepo;

			try {
				ghRepo = g.getRepository();
			} catch (Exception e) {
				/*
				 * repos will be returned by the API that are unaccessible, and return 404 on
				 * API access
				 */
				continue;
			}

			String repoName = null;
			if (ghRepo != null) {
				repoName = ghRepo.getName();
			}

			Date createdAt = g.getCreatedAt();

			String actorLogin = g.getActorLogin();

//			log.out("Received event: [" + eventType.name() + "] " + g.getActorLogin() + " " + repoName + " " + g.getType().name() + " "
//					+ g.getCreatedAt() + " ");

			if (eventType == GHEvent.CREATE) {

				GHEventPayload.Create eventDetails = g.getPayload(GHEventPayload.Create.class);
				String refType = eventDetails.getRefType();
				if (DEBUG && refType != null && !refType.equals("branch") && !refType.equals("tag")
						&& !refType.equals("repository")) {
					System.out.println("New type: " + eventDetails.getRef() + " " + eventDetails.getRefType());
				}

			} else if (eventType == GHEvent.ISSUE_COMMENT) {

				IssueComment eventDetails = g.getPayload(GHEventPayload.IssueComment.class);

				GHIssue issue = eventDetails.getIssue();

				if (!issue.isPullRequest()) {

					String hash = createEventHash(eventType, owner2, repoName, issue.getNumber(), createdAt,
							actorLogin);

					boolean added = data.addEventIfNotPresent(hash);
					if (!added) {
						break event_loop;
					}
					log.logDebug("Received event: [" + eventType.name() + "] " + g.getActorLogin() + " " + repoName
							+ " " + g.getType().name() + " " + g.getCreatedAt() + " " + hash);

					toScan.add(new EventScanEntry(issue, ghRepo, hash, g));
				}

			} else if (eventType == GHEvent.ISSUES) {

				Issue eventDetails = g.getPayload(GHEventPayload.Issue.class);

				GHIssue issue = eventDetails.getIssue();

				if (!issue.isPullRequest()) {
					String hash = createEventHash(eventType, owner2, repoName, issue.getNumber(), createdAt,
							actorLogin);

					boolean added = data.addEventIfNotPresent(hash);
					if (!added) {
						break event_loop;
					}

					log.logDebug("Received event: [" + eventType.name() + "] " + g.getActorLogin() + " " + repoName
							+ " " + g.getType().name() + " " + g.getCreatedAt() + " " + hash);

					toScan.add(new EventScanEntry(issue, ghRepo, hash, g));

				}

			} else if (eventType == GHEvent.PULL_REQUEST || eventType == GHEvent.PUSH || eventType == GHEvent.DELETE
					|| eventType == GHEvent.PULL_REQUEST_REVIEW_COMMENT || eventType == GHEvent.FORK
					|| eventType == GHEvent.GOLLUM) {
				/* ignore */

			} else {

				if (DEBUG) {
					System.out.println("[" + eventType.name() + "] Ignoring: " + g.getActorLogin() + " "
							+ g.getRepository().getName() + " " + g.getType().name() + " " + g.getCreatedAt() + " ");
				}
			}
		} // end event_loop

		List<String> newEventHashes = new ArrayList<>();

		Map<String, Boolean> seen = new HashMap<String, Boolean>();

		for (EventScanEntry e : toScan) {

			newEventHashes.add(e.getHash());

			// Prevent duplicates
			{
				String key = e.calculateKey();
				if (seen.containsKey(key)) {
					continue;
				}
				seen.put(key, true);
			}

			GHRepository repo = e.getRepository();
			GHIssue issue = repo.getIssue(e.getIssue().getNumber());

			// If the issue doesn't match, it means the issue was moved to another repo.
			if (issue.getId() != e.getIssue().getId()) {

				// It appears that the only way to get the destination repo and issue # is by
				// parsing the URL, so lets do that...

				// https://api.github.com/repos/eclipse/codewind-vscode/issues/84 to:
				// [https:][][api.github.com][repos][eclipse][codewind-vscode][issues][84]

				String[] splitByForwardSlashes = issue.getUrl().toString().split(java.util.regex.Pattern.quote("/"));
				int len = splitByForwardSlashes.length;
				if (DEBUG) {
					System.out.println("!!!! Move detected: " + issue + " and " + e.getIssue());
					System.out.println("     - " + issue.getUrl() + " and " + e.getIssue().getUrl());
					System.out.println("        o " + Arrays.asList(splitByForwardSlashes).stream()
							.map(f -> "[" + f + "] ").reduce((a, b) -> a + b).get());
				}

				int newIssueNumber = Integer.parseInt(splitByForwardSlashes[len - 1]);

				String repoName = splitByForwardSlashes[len - 3];

				repo = repo.getOwner().getRepository(repoName);

				issue = repo.getIssue(newIssueNumber);

			}

			if (!repo.getOwnerName().equals(e.getRepository().getOwnerName())) {
				throw new RuntimeException("GitHub does not support moving between organizations at thie time: " + repo
						+ " " + e.getRepository());
			}
			workQueue.addIssue(issue, repo, owner2);

		}

		return newEventHashes;

	}

	private static String createEventHash(GHEvent event, Owner owner, String repoName, Integer issue, Date createdAt,
			String actorLogin) {

		StringBuilder sb = new StringBuilder();
		sb.append(event.ordinal());
		sb.append("-");
		sb.append(owner.getOrgNameOrNull());
		sb.append("-");
		sb.append(owner.getUserNameOrNull());
		sb.append("-");
		sb.append(repoName);
		sb.append("-");
		sb.append(issue);
		sb.append("-");
		sb.append(createdAt.getTime());
		sb.append("-");
		sb.append(actorLogin);

		return DigestUtils.sha256Hex(sb.toString());
	}

	/** Representation of an event, before it is turned into a work entry. */
	private static class EventScanEntry {
		private final GHIssue issue;
		private final GHRepository repository;
		private final String hash;
		private final GHEventInfo event;

		public EventScanEntry(GHIssue issue, GHRepository repository, String hash, GHEventInfo event) {
			this.issue = issue;
			this.repository = repository;
			this.hash = hash;
			this.event = event;
		}

		public String getHash() {
			return hash;
		}

		public GHIssue getIssue() {
			return issue;
		}

		public GHRepository getRepository() {
			return repository;
		}

		@SuppressWarnings("unused")
		public GHEventInfo getEventInfo() {
			return event;
		}

		public String calculateKey() {
			return repository.getName() + "-" + issue.getNumber();

		}
	}

	/**
	 * This class maintains an in memory list of the last X processed events, for
	 * use by EventScan.
	 * 
	 * A single instance of this class will exist per server instance. This class is
	 * thread safe.
	 */
	public static class EventScanData {

		private final Object lock = new Object();

		private final Map<String /* hash of event contents */, Boolean> processedEvents_synch_lock = new HashMap<>();
		private final List<String /* hash of event contents */> eventsAdded_synch_lock = new ArrayList<String>();

		public EventScanData(List<String> seedContents) {

			seedContents.forEach(eventHash -> {
				processedEvents_synch_lock.put(eventHash, true);
				eventsAdded_synch_lock.add(eventHash);
			});

		}

		/** Return true if added, false otherwise */
		public boolean addEventIfNotPresent(String eventHash) {
			synchronized (lock) {
				if (isEventProcessed(eventHash)) {
					return false;
				}

				processedEvents_synch_lock.put(eventHash, true);
				eventsAdded_synch_lock.add(eventHash);

				cleanupOld();

				return true;

			}
		}

		private void cleanupOld() {
			synchronized (lock) {
				while (eventsAdded_synch_lock.size() > 1000) {
					String atZero = eventsAdded_synch_lock.remove(0);
					processedEvents_synch_lock.remove(atZero);
				}
			}
		}

		private boolean isEventProcessed(String eventHash) {
			synchronized (lock) {
				return processedEvents_synch_lock.containsKey(eventHash);
			}
		}
	}

}
