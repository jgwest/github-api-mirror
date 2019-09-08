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
import java.util.regex.Pattern;

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

import com.githubapimirror.WorkQueue.IssueContainer;
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
 * database. This allows us to reduce the number of GitHub requests required per
 * scan.
 */
public class EventScan {

	private static final boolean DEBUG = false;

	private static final GHLog log = GHLog.getInstance();

	public static ProcessIteratorReturnValue doEventScan(OwnerContainer ownerContainer, EventScanData data,
			WorkQueue workQueue, long lastFullScan) throws IOException {

		String ownerName;

		PagedIterator<GHEventInfo> it;
		if (ownerContainer.getType() == OwnerContainer.Type.ORG) {
			GHOrganization org = ownerContainer.getOrg();
			ownerName = org.getLogin();
			it = org.listEvents().iterator();
			return processIterator(it, Owner.org(ownerName), "*", data, workQueue, lastFullScan);

		} else if (ownerContainer.getType() == OwnerContainer.Type.USER) {
			GHUser user = ownerContainer.getUser();
			ownerName = user.getLogin();
			it = user.listEvents().iterator();
			return processIterator(it, Owner.user(ownerName), "*", data, workQueue, lastFullScan);
			// TODO: Not actually sure this will work for other users.

		} else if (ownerContainer.getType() == OwnerContainer.Type.REPO_LIST) {

			List<String> eventHashResult = new ArrayList<>();

			boolean fullScanRequired = false;

			for (GHRepository repo : ownerContainer.getIndividualRepos()) {
				ProcessIteratorReturnValue pirv = processIterator(repo.listEvents().iterator(),
						ownerContainer.getOwner(), repo.getName(), data, workQueue, lastFullScan);

				List<String> currRepoHash = pirv.getNewEventHashes();
				eventHashResult.addAll(currRepoHash);

				if (pirv.isFullScanRequired()) {
					fullScanRequired = true;
				}
			}

			return new ProcessIteratorReturnValue(eventHashResult, fullScanRequired);

		} else {
			throw new RuntimeException("Unrecognized owner type: " + ownerContainer);
		}

	}

	/**
	 * @return Returns the hashes of the new events that we have seen, for storage
	 *         in the DB
	 */
	@SuppressWarnings("unused")
	private static ProcessIteratorReturnValue processIterator(PagedIterator<GHEventInfo> it, final Owner owner,
			String iteratorTarget, EventScanData data, WorkQueue workQueue, long lastFullScan) throws IOException {

		List<EventScanEntry> toScan = new ArrayList<>();

		long lastEventInfoCreatedAt = Long.MAX_VALUE;

		// If we see X events in-a-row (eg 20) that match events we have already
		// seen, then we assume there will be no more new (previously unseen)
		// events in the event list.
		int eventMatchesInARowFromEsd = 0;

		boolean fullScanRequired = true;

		int count = 0;
		event_loop: for (; it.hasNext();) {

			count++;
			if (count % 20 == 0) {
				workQueue.waitIfNeeded(1); // I'm guessing 1 request per 20 events?
			}

			GHEventInfo g = it.next();

			GHEvent eventType = g.getType();

			String actorLogin = g.getActorLogin();

			GHRepository ghRepo;

			try {
				ghRepo = g.getRepository();
			} catch (Exception e) {
				// Repos will be returned by the API that are unaccessible, and return 404 on
				// API access.
				continue;
			}

			String repoName = null;
			if (ghRepo != null) {
				repoName = ghRepo.getName();
			}

			Date createdAt = g.getCreatedAt();

			if (createdAt.getTime() < lastFullScan) {

				if (fullScanRequired) {

					log.logDebug("Event of " + repoName
							+ " has a 'created at' time before full scan time, so no full scan required: "
							+ new Date(createdAt.getTime()) + ",  lastFullScan: " + new Date(lastFullScan));

					// If there is repository event data available before the last full scan, that
					// means our event scan data algorithm will necessarily be able to update us to
					// the latest state of the repo.
					fullScanRequired = false;
				}

				// There is no point in processing events after the last full scan, so break out
				// of the loop.
				break event_loop;
			}

//			log.out("Received event: [" + eventType.name() + "] " + g.getActorLogin() + " " + repoName + " " + g.getType().name() + " "
//					+ g.getCreatedAt() + " ");

			// Detect when we are not receiving events in timestamp descending order.
			// - we know that GH _will_ sometimes give us events out of order
			if (createdAt != null && createdAt.getTime() > lastEventInfoCreatedAt) {

				int issueNum = -1;
				if (eventType == GHEvent.ISSUE_COMMENT) {
					IssueComment eventDetails = g.getPayload(GHEventPayload.IssueComment.class);
					issueNum = eventDetails.getIssue().getNumber();

				} else if (eventType == GHEvent.ISSUES) {
					Issue eventDetails = g.getPayload(GHEventPayload.Issue.class);
					issueNum = eventDetails.getIssue().getNumber();
				}

				log.logInfo("Received out-of-order event: [" + eventType.name() + "] " + g.getActorLogin() + " "
						+ repoName + "/" + issueNum + " " + g.getType().name() + " " + createdAt);

				lastEventInfoCreatedAt = createdAt.getTime();
			} else {
				if (createdAt != null) {
					lastEventInfoCreatedAt = createdAt.getTime();
				}
			}

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

					String hash = createEventHash(eventType, owner, repoName, issue.getNumber(), createdAt, actorLogin);

					boolean eventProcessed = data.isEventProcessed(hash);

					if (eventProcessed) {
						eventMatchesInARowFromEsd++;
					} else {
						eventMatchesInARowFromEsd = 0;
						toScan.add(new EventScanEntry(issue, ghRepo, hash, g));
					}

					log.logDebug("Received event: [" + eventType.name() + "] " + g.getActorLogin() + " " + repoName
							+ "/" + issue.getNumber() + " " + g.getType().name() + " " + g.getCreatedAt() + " " + hash
							+ (eventProcessed ? "*" : ""));

				}

			} else if (eventType == GHEvent.ISSUES) {

				Issue eventDetails = g.getPayload(GHEventPayload.Issue.class);

				GHIssue issue = eventDetails.getIssue();

				if (!issue.isPullRequest()) {
					String hash = createEventHash(eventType, owner, repoName, issue.getNumber(), createdAt, actorLogin);

					boolean eventProcessed = data.isEventProcessed(hash);

					if (eventProcessed) {
						eventMatchesInARowFromEsd++;
					} else {
						eventMatchesInARowFromEsd = 0;
						toScan.add(new EventScanEntry(issue, ghRepo, hash, g));
					}

					log.logDebug("Received event: [" + eventType.name() + "] " + g.getActorLogin() + " " + repoName
							+ "/" + issue.getNumber() + " " + g.getType().name() + " " + g.getCreatedAt() + " " + hash
							+ (eventProcessed ? "*" : ""));

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

			if (eventMatchesInARowFromEsd == 20) {
				// If we match a bunch of events in our cache, this means these events have
				// already been processed and the database is up-to-date with these changes.

				// Therefore we will necessarily be able to update our database without a full
				// scan, since we can be assured there is no missing repository event data.
				fullScanRequired = false;

				// We likewise no longer need to continue processing repository events, because
				// we can be confident then any additional events will ALSO have already been
				// processed.
				break event_loop;
			}

		} // end event_loop

		workQueue.waitIfNeeded((int) (count / 20));

		List<String> newEventHashes = new ArrayList<>();

		Map<String /* (repo name)-(issue #) */, Boolean /* unused */> seen = new HashMap<String, Boolean>();

		List<IssueContainer> itemsToQueue = new ArrayList<>();

		for (EventScanEntry e : toScan) {

			// Add _all_ the event hashes we saw....
			newEventHashes.add(e.getHash());

			// ... but prevent duplicate issues from being processed after this block.
			{
				String key = e.calculateKey();
				if (seen.containsKey(key)) {
					continue;
				}
				seen.put(key, true);
			}

			// The rest of this block add the issue to the work queue, and attempts to
			// detect a move.

			workQueue.waitIfNeeded(1);

			GHRepository repo = e.getRepository();
			GHIssue issue = repo.getIssue(e.getIssue().getNumber());

			// If the issue doesn't match, it means the issue was moved to another repo.
			if (issue.getId() != e.getIssue().getId()) {

				// It appears that the only way to get the destination repo and issue # is by
				// parsing the URL, so lets do that...

				// https://api.github.com/repos/eclipse/codewind-vscode/issues/84 to:
				// [https:][][api.github.com][repos][eclipse][codewind-vscode][issues][84]

				String[] splitByForwardSlashes = issue.getUrl().toString().split(Pattern.quote("/"));
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
				throw new RuntimeException("GitHub does not support moving between organizations at this time: " + repo
						+ " " + e.getRepository());
			}

			itemsToQueue.add(new IssueContainer(issue, repo, owner));
		}

		if (!fullScanRequired) {
			itemsToQueue.forEach(e -> {
				workQueue.addIssue(e.getIssue(), e.getRepo().get(), e.getOwner());

			});
		} else {
			// A full scan of all the repos is required, so there is no need to queue
			// individual items here.

			log.logInfo("Full scan required for " + owner.getName() + "/" + iteratorTarget);

		}

		newEventHashes.forEach(e -> {
			data.addEventIfNotPresent(e);
		});

		return new ProcessIteratorReturnValue(newEventHashes, fullScanRequired);

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

	/** Container class for return values of event scan */
	static class ProcessIteratorReturnValue {
		private final List<String> newEventHashes;
		private final boolean fullScanRequired;

		public ProcessIteratorReturnValue(List<String> newEventHashes, boolean fullScanRequired) {
			this.newEventHashes = newEventHashes;
			this.fullScanRequired = fullScanRequired;
		}

		public List<String> getNewEventHashes() {
			return newEventHashes;
		}

		public boolean isFullScanRequired() {
			return fullScanRequired;
		}

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
	 * This class maintains an in-memory list of the repository events that have
	 * occurred since the last full scan.
	 * 
	 * A single instance of this class will exist per server instance. This class is
	 * thread safe.
	 */
	public static class EventScanData {

		private final Object lock = new Object();

		/**
		 * Whether or not the event has been processed; presence as a key means it has.
		 */
		private final Map<String /* hash of event contents */, Boolean /* not used */> processedEvents_synch_lock = new HashMap<>();

		/**
		 * This is initially seeded with the processed events list from the database.
		 */
		public EventScanData(List<String> seedContents) {

			seedContents.forEach(eventHash -> {
				processedEvents_synch_lock.put(eventHash, true);
			});

		}

		/** Return true if added, false otherwise */
		public boolean addEventIfNotPresent(String eventHash) {
			synchronized (lock) {
				if (isEventProcessed(eventHash)) {
					return false;
				}

				processedEvents_synch_lock.put(eventHash, true);
				return true;

			}
		}

		public void clear() {
			synchronized (lock) {
				processedEvents_synch_lock.clear();
			}
		}

		public boolean isEventProcessed(String eventHash) {
			synchronized (lock) {
				return processedEvents_synch_lock.containsKey(eventHash);
			}
		}
	}

}
