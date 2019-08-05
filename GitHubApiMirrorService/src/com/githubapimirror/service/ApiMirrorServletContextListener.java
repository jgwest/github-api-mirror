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

package com.githubapimirror.service;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import com.githubapimirror.GHLog;

/**
 * This class ensures that when the server is started, that the ZHDatabase and
 * ZHServerInstance also start (kicking off the necessary background threads).
 */
@WebListener
public class ApiMirrorServletContextListener implements ServletContextListener {

	private final GHLog log = GHLog.getInstance();

	public void contextInitialized(ServletContextEvent servletContextEvent) {
		try {
			log.logInfo("* GitHubApiMirrorService started.");
			ApiMirrorInstance.getInstance().getDb();
		} catch (RuntimeException re) {
			re.printStackTrace();
			throw re;
		}
	}

	public void contextDestroyed(ServletContextEvent servletContextEvent) {
	}
}
