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

package com.githubapimirror.service.yaml;

import java.util.ArrayList;
import java.util.List;

public class ConfigFileYaml {

	private String githubServer;

	private String githubUsername;
	private String githubPassword;

	private List<String> userRepoList = new ArrayList<>();

	private List<String> orgList = new ArrayList<>();

	private List<IndividualRepoListYaml> individualRepoList = new ArrayList<>();

	private String presharedKey;

	private String dbPath;

	private Integer githubRateLimit;

	private Long timeBetweenEventScansInSeconds;

	private Long pauseBetweenRequestsInMsecs;

	private String fileLoggerPath;

	public ConfigFileYaml() {
	}

	public String getGithubServer() {
		return githubServer;
	}

	public void setGithubServer(String githubServer) {
		this.githubServer = githubServer;
	}

	public String getGithubUsername() {
		return githubUsername;
	}

	public void setGithubUsername(String githubUsername) {
		this.githubUsername = githubUsername;
	}

	public String getGithubPassword() {
		return githubPassword;
	}

	public void setGithubPassword(String githubPassword) {
		this.githubPassword = githubPassword;
	}

	public List<String> getOrgList() {
		return orgList;
	}

	public void setOrgList(List<String> orgList) {
		this.orgList = orgList;
	}

	public List<IndividualRepoListYaml> getIndividualRepoList() {
		return individualRepoList;
	}

	public void setIndividualRepoList(List<IndividualRepoListYaml> individualRepoList) {
		this.individualRepoList = individualRepoList;
	}

	public String getPresharedKey() {
		return presharedKey;
	}

	public void setPresharedKey(String presharedKey) {
		this.presharedKey = presharedKey;
	}

	public String getDbPath() {
		return dbPath;
	}

	public void setDbPath(String dbPath) {
		this.dbPath = dbPath;
	}

	public Integer getGithubRateLimit() {
		return githubRateLimit;
	}

	public void setGithubRateLimit(Integer githubRateLimit) {
		this.githubRateLimit = githubRateLimit;
	}

	public List<String> getUserRepoList() {
		return userRepoList;
	}

	public void setUserRepoList(List<String> userRepoList) {
		this.userRepoList = userRepoList;
	}

	public Long getTimeBetweenEventScansInSeconds() {
		return timeBetweenEventScansInSeconds;
	}

	public void setTimeBetweenEventScansInSeconds(Long timeBetweenEventScansInSeconds) {
		this.timeBetweenEventScansInSeconds = timeBetweenEventScansInSeconds;
	}

	public Long getPauseBetweenRequestsInMsecs() {
		return pauseBetweenRequestsInMsecs;
	}

	public void setPauseBetweenRequestsInMsecs(Long pauseBetweenRequestsInMsecs) {
		this.pauseBetweenRequestsInMsecs = pauseBetweenRequestsInMsecs;
	}

	public String getFileLoggerPath() {
		return fileLoggerPath;
	}

	public void setFileLoggerPath(String fileLoggerPath) {
		this.fileLoggerPath = fileLoggerPath;
	}
}
