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

package com.githubapimirror.service.yaml;

public class IndividualRepoListYaml {

	private Long timeBetweenEventScansInSeconds;

	private String repo;

	public Long getTimeBetweenEventScansInSeconds() {
		return timeBetweenEventScansInSeconds;
	}

	public void setTimeBetweenEventScansInSeconds(Long timeBetweenEventScansInSeconds) {
		this.timeBetweenEventScansInSeconds = timeBetweenEventScansInSeconds;
	}

	public String getRepo() {
		return repo;
	}

	public void setRepo(String repo) {
		this.repo = repo;
	}

}
