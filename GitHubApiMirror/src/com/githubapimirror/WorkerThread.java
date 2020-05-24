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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.egit.github.core.IssueEvent;
import org.eclipse.egit.github.core.Rename;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.client.PageIterator;
import org.eclipse.egit.github.core.service.IssueService;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubapimirror.WorkQueue.IssueContainer;
import com.githubapimirror.WorkQueue.OwnerContainer;
import com.githubapimirror.WorkQueue.RepositoryContainer;
import com.githubapimirror.db.Database;
import com.githubapimirror.shared.GHApiUtil;
import com.githubapimirror.shared.JsonUtil;
import com.githubapimirror.shared.NewFileLogger;
import com.githubapimirror.shared.Owner;
import com.githubapimirror.shared.Owner.Type;
import com.githubapimirror.shared.json.IssueCommentJson;
import com.githubapimirror.shared.json.IssueEventAssignedUnassignedJson;
import com.githubapimirror.shared.json.IssueEventJson;
import com.githubapimirror.shared.json.IssueEventLabeledUnlabeledJson;
import com.githubapimirror.shared.json.IssueEventRenamedJson;
import com.githubapimirror.shared.json.IssueJson;
import com.githubapimirror.shared.json.OrganizationJson;
import com.githubapimirror.shared.json.RepositoryJson;
import com.githubapimirror.shared.json.ResourceChangeEventJson;
import com.githubapimirror.shared.json.UserJson;
import com.githubapimirror.shared.json.UserRepositoriesJson;

/**
 * This class is responsible for requesting work from the WorkQueue, acting on
 * that work (querying the specified resources in the GitHub API) and then
 * persisting those resources to the database.
 * 
 * A server instance will have multiple worker threads, corresponding to
 * multiple simultaneous connections to the GH API.
 */
public class WorkerThread extends Thread {

	private final WorkQueue queue;

	private final GHLog log = GHLog.getInstance();

	private final TimeOutThread timeOut;

	private final GhmFilter filter;

	private boolean acceptingNewWork = true;

	public WorkerThread(WorkQueue queue, GhmFilter filter) {
		setName(WorkerThread.class.getName());
		this.queue = queue;
		this.filter = filter;

		this.timeOut = new TimeOutThread();
		this.timeOut.start();
	}

	@Override
	public void run() {

		final Database db = queue.getDb();

		while (acceptingNewWork) {
			queue.waitForAvailableWork();

			OwnerContainer ownerContainer = queue.pollOwner().orElse(null);
			if (ownerContainer != null) {
				try {
					timeOut.begin();
					processOwner(ownerContainer, db);
				} catch (Exception e) {
					e.printStackTrace();
					queue.addOwner(ownerContainer);
				} finally {
					timeOut.reset();
					queue.markAsProcessed(ownerContainer);
				}
				continue;

			}

			RepositoryContainer repo = queue.pollRepository().orElse(null);

			if (repo != null) {
				try {
					timeOut.begin();
					processRepo(repo, db);
				} catch (Exception e) {
					printException(e);
					queue.addRepository(repo.getOwner(), repo.getRepo());
				} finally {
					timeOut.reset();
					queue.markAsProcessed(repo);

				}

				continue;
			}

			IssueContainer issue = queue.pollIssue().orElse(null);
			if (issue != null) {
				try {
					timeOut.begin();
					processIssue(issue, db);
				} catch (Exception e) {
					printException(e);
					queue.addIssue(issue.getIssue(), issue.getRepo().orElse(null), issue.getOwner());
				} finally {
					timeOut.reset();
					queue.markAsProcessed(issue);
				}

				continue;
			}

			GHUser user = queue.pollUser().orElse(null);
			if (user != null) {
				try {
					timeOut.begin();
					processUser(user, db);
				} catch (Exception e) {
					printException(e);
					queue.addUserRetry(user);
				} finally {
					timeOut.reset();
					queue.markAsProcessed(user);
				}

				continue;
			}

		}
	}

	private static void printException(Exception e) {
		e.printStackTrace();
//		String exStr = e.getClass().getName() + ": " + e.getMessage();
//		if (e instanceof NoSuchElementException) {
//			e.printStackTrace();
//		}
//		System.err.println(exStr);
	}

	public void setAcceptingNewWork(boolean acceptingNewWork) {
		this.acceptingNewWork = acceptingNewWork;
	}

	private void processOwner(OwnerContainer ownerContainer, Database db) throws IOException {

		Owner owner = ownerContainer.getOwner();

		String ownerName = owner.getName();

		if (filter != null && !filter.processOwner(owner)) {
			return;
		}

		log.processing(ownerName);

		Collection<GHRepository> repositories;
		if (ownerContainer.getType() == OwnerContainer.Type.ORG) {
			GHOrganization org = ownerContainer.getOrg();
			repositories = org.getRepositories().values();
		} else if (ownerContainer.getType() == OwnerContainer.Type.USER) {
			GHUser user = ownerContainer.getUser();
			repositories = user.getRepositories().values();
		} else if (ownerContainer.getType() == OwnerContainer.Type.REPO_LIST) {
			repositories = ownerContainer.getIndividualRepos();
		} else {
			throw new RuntimeException("Unrecognized owner container type");
		}

		List<String> repoNames = new ArrayList<>();

		repositories.forEach(repo -> {
			String repoName = repo.getName();

			if (filter != null && !filter.processRepo(owner, repoName)) {
				return;
			}

			repoNames.add(repoName);

			queue.addRepository(owner, repo);
		});

		if (owner.getType() == Type.ORG) {
			OrganizationJson json = new OrganizationJson();
			json.setName(ownerName);
			json.getRepositories().addAll(repoNames);
			db.persistOrganization(json);

		} else {
			UserRepositoriesJson urj = new UserRepositoriesJson();
			urj.setUserName(ownerName);
			urj.getRepoNames().addAll(repoNames);
			db.persistUserRepositories(urj);
		}

	}

	private void processUser(GHUser user, Database db) throws IOException {

		if (user == null || user.getLogin() == null) {
			return;
		}

		String login = user.getLogin();

		if (filter != null && !filter.processUser(login)) {
			return;
		}

		log.processing(user);

		UserJson json = new UserJson();

		json.setLogin(login);
		json.setName(user.getName());
		json.setEmail(user.getEmail());

		db.persistUser(json);

	}

	private void processIssue(IssueContainer issueContainer, Database db) throws IOException {

		GHIssue issue = issueContainer.getIssue();

		// Use the one from the container if present, otherwise use from GHIssue
		GHRepository repo = issueContainer.getRepo().orElse(issue.getRepository());

		int issueNumber = issue.getNumber();

		String repoName = repo.getName();

		if (filter != null && !filter.processIssue(issueContainer.getOwner(), repoName, issueNumber)) {
			return;
		}

		log.processing(issue, repoName);

		IssueJson json = new IssueJson();

		GHUser reporter = issue.getUser();

		String reporterLogin = sanitizeUserLogin(reporter);

		if (filter == null || filter.processUser(reporterLogin)) {
			if (reporter != null) {
				queue.addUser(reporter);
			}
		}

		json.setClosed(issue.getState() == GHIssueState.CLOSED);

		json.setReporter(reporterLogin);

		json.setPullRequest(issue.isPullRequest());

		json.setBody(issue.getBody());

		json.setNumber(issueNumber);

		json.setHtmlUrl(issue.getHtmlUrl().toString());

		json.setTitle(issue.getTitle());

		List<String> assignees = json.getAssignees();

		issue.getAssignees().forEach(user -> {
			String login = user.getLogin();
			if (login == null) {
				return;
			}

			assignees.add(login);

			if (filter != null && !filter.processUser(login)) {
				return;
			}
			queue.addUser(user);
		});

		json.setCreatedAt(issue.getCreatedAt());

		json.setClosedAt(issue.getClosedAt());

		json.setParentRepo(repoName);

		json.getLabels().addAll(issue.getLabels().stream().map(e -> e.getName()).collect(Collectors.toList()));

		for (GHIssueComment comment : issue.getComments()) {
			IssueCommentJson commentJson = new IssueCommentJson();

			commentJson.setBody(comment.getBody());
			commentJson.setCreatedAt(comment.getCreatedAt());
			commentJson.setUpdatedAt(comment.getUpdatedAt());
			commentJson.setUserLogin(sanitizeUserLogin(comment.getUser()));

			json.getComments().add(commentJson);
		}

		// Acquire issue events, add them to the JSON
		if (filter == null || filter.processIssueEvents(issueContainer.getOwner(), repoName, issue.getNumber())) {

			GitHubClient client = queue.getServerInstance().getEgitClient();

			IssueService service = new IssueService(client);

			List<IssueEventJson> issueEvents = json.getIssueEvents();

			for (PageIterator<IssueEvent> it = service.pageIssueEvents(issueContainer.getOwner().getName(), repoName,
					issueNumber); it.hasNext();) {

				Collection<IssueEvent> c = it.next();

				for (IssueEvent e : c) {

					IssueEventJson issueEventJson = new IssueEventJson();

					String userLogin = sanitizeUserLogin(e.getActor());

					issueEventJson.setActorUserLogin(userLogin);
					issueEventJson.setType(e.getEvent());
					issueEventJson.setCreatedAt(e.getCreatedAt());

					if (e.getEvent().equals(IssueEvent.TYPE_ASSIGNED)
							|| e.getEvent().equals(IssueEvent.TYPE_UNASSIGNED)) {

						IssueEventAssignedUnassignedJson ieauj = new IssueEventAssignedUnassignedJson();
						ieauj.setAssignee(sanitizeUserLogin(e.getAssignee()));
						ieauj.setAssigner(sanitizeUserLogin(e.getAssigner()));
						ieauj.setAssigned(e.getEvent().equals(IssueEvent.TYPE_ASSIGNED));
						issueEventJson.setData(ieauj);
						issueEvents.add(issueEventJson);

					} else if (e.getEvent().equals(IssueEvent.TYPE_LABELED)
							|| e.getEvent().equals(IssueEvent.TYPE_UNLABELED)) {

						IssueEventLabeledUnlabeledJson ielj = new IssueEventLabeledUnlabeledJson();
						ielj.setLabel(e.getLabel().getName());
						ielj.setLabeled(e.getEvent().equals(IssueEvent.TYPE_LABELED));
						issueEventJson.setData(ielj);
						issueEvents.add(issueEventJson);

					} else if (e.getEvent().equals(IssueEvent.TYPE_RENAMED)) {

						Rename rename = e.getRename();
						IssueEventRenamedJson renamed = new IssueEventRenamedJson();
						renamed.setFrom(rename.getFrom());
						renamed.setTo(rename.getTo());
						issueEventJson.setData(renamed);
						issueEvents.add(issueEventJson);

					} else if (e.getEvent().equals(IssueEvent.TYPE_REOPENED)) {
						issueEvents.add(issueEventJson);
					} else if (e.getEvent().equals(IssueEvent.TYPE_MERGED)) {
						issueEvents.add(issueEventJson);
					} else if (e.getEvent().equals(IssueEvent.TYPE_CLOSED)) {
						issueEvents.add(issueEventJson);
					}
				}

			}

		}

		IssueJson oldDbVersion = db.getIssue(issueContainer.getOwner(), repoName, issueNumber).orElse(null);

		db.persistIssue(issueContainer.getOwner(), json);

		db.getRepository(issueContainer.getOwner(), repoName).ifPresent(e -> {
			if (e.getLastIssue() == null) {
				return;
			}

			// If this issue is a new issue in the repository (eg its Number is larger than
			// the largest we have seen)...
			if (e.getLastIssue() < json.getNumber()) {

				log.logDebug("Updating last issue of " + e.getName() + " to " + json.getNumber() + " from "
						+ e.getLastIssue());
				e.setLastIssue(json.getNumber());

				db.persistRepository(e);
			}
		});

		// TODO: Compare old and new versions here, and report differences.

		// Compare the old version of the database entry, and the current version; if
		// different, create a change event and add it to the database.
		if (!JsonUtil.isEqualBySortedAlphanumerics(oldDbVersion, json, new ObjectMapper())) {

			ResourceChangeEventJson rcej = new ResourceChangeEventJson();
			rcej.setOwner(issueContainer.getOwner().getName());
			rcej.setRepo(repoName);
			rcej.setTime(System.currentTimeMillis());
			rcej.setUuid(UUID.randomUUID().toString());
			rcej.setIssueNumber(issueNumber);
			db.persistResourceChangeEvents(Arrays.asList(rcej));

			ObjectMapper om = new ObjectMapper();

			String jsonValue = om.writeValueAsString(json);
			NewFileLogger fl = queue.getServerInstance().getFileLogger();
			fl.out("resource-change-event: " + new Date() + " " + rcej.getTime() + " " + rcej.getUuid() + " "
					+ jsonValue);

		}
	}

	private void processRepo(RepositoryContainer repoContainer, Database db) {

		GHRepository repo = repoContainer.getRepo();

		String repoName = repo.getName();

		Owner owner = repoContainer.getOwner();

		if (filter != null && !filter.processRepo(owner, repoName)) {
			return;
		}

		log.processing(repo);

		int smallestIssue = Integer.MAX_VALUE;
		int largestIssue = -1;

		for (GHIssue e : repo.listIssues(GHIssueState.ALL)) {

			// Skip pull requests
			if (e.isPullRequest()) {
				continue;
			}

			int num = e.getNumber();

			if (filter != null && !filter.processIssue(owner, repoName, num)) {
				continue;
			}

			if (num < smallestIssue) {
				smallestIssue = num;
			}
			if (num > largestIssue) {
				largestIssue = num;
			}

			queue.addIssue(e, owner);
		}

		RepositoryJson json = new RepositoryJson();
		json.setFirstIssue(smallestIssue != Integer.MAX_VALUE ? smallestIssue : null);
		json.setLastIssue(largestIssue != -1 ? largestIssue : null);

		json.setRepositoryId(repo.getId());

		json.setName(repoName);

		if (owner.getType() == Type.ORG) {
			json.setOrgName(owner.getOrgNameOrNull());
		} else {
			json.setOwnerUserName(owner.getUserNameOrNull());
		}

		db.persistRepository(json);
	}

	private static String sanitizeUserLogin(GHUser u) {
		if (u == null) {
			return "Ghost";
		}

		String userLogin = u.getLogin();
		if (userLogin == null) {
			userLogin = "Ghost";
		}

		return userLogin;
	}

	private static String sanitizeUserLogin(org.eclipse.egit.github.core.User u) {
		if (u == null) {
			return "Ghost";
		}

		String userLogin = u.getLogin();
		if (userLogin == null) {
			userLogin = "Ghost";
		}

		return userLogin;
	}

	/**
	 * Some GitHub servers I've used will timeout on rate limit, rather than reject
	 * requests. This class is a workaround around that behaviour, and will
	 * automatically interrupt the thread in this scenario.
	 */
	private class TimeOutThread extends Thread {

		private final Object lock = new Object();

		private Long currExpireTimeInNanos = null;

		public TimeOutThread() {
			setName(TimeOutThread.class.getName());
			setDaemon(true);
		}

		@Override
		public void run() {

			while (acceptingNewWork) {

				GHApiUtil.sleep(15 * 1000);

				synchronized (lock) {
					if (currExpireTimeInNanos == null) {
						continue;
					}

					if (System.nanoTime() > currExpireTimeInNanos) {
						WorkerThread.this.interrupt();
						System.err.println("Sent interrupt for " + currExpireTimeInNanos);
						currExpireTimeInNanos = null;
					}
				}

			}
		}

		public void begin() {
			synchronized (lock) {
				currExpireTimeInNanos = System.nanoTime() + TimeUnit.NANOSECONDS.convert(2, TimeUnit.MINUTES);
			}

		}

		public void reset() {
			synchronized (lock) {
				currExpireTimeInNanos = null;
			}
		}

	}

}
