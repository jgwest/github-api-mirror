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

package com.githubapimirror.service.war;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import com.githubapimirror.GHLog;
import com.githubapimirror.service.ApiMirrorInstance;
import com.githubapimirror.shared.GHApiUtil;

/**
 * This class ensures that when the server is started, that the ZHDatabase and
 * ZHServerInstance also start (kicking off the necessary background threads).
 */
@WebListener
public class ApiMirrorServletContextListener implements ServletContextListener {

	private final GHLog log = GHLog.getInstance();

	public void contextInitialized(ServletContextEvent servletContextEvent) {
		try {
			log.logInfo("* WAR GitHubApiMirrorService started.");

			String configPath = lookupString("github-api-mirror/config-path").get();

			
			String dbPath = null;
			
			// The database path of the YAML can be override by this JNDI value in the
			// server.xml. This is used when running within a container.
			Optional<String> jndiOverride = lookupString("github-api-mirror/db-path");
			if (jndiOverride.isPresent()) {
				dbPath = jndiOverride.get();
			}

			ApiMirrorInstance.getInstance().initializeServerInstance(configPath, dbPath);
			
			startThreadDumpThread();
		} catch (RuntimeException re) {
			re.printStackTrace();
			throw re;
		}
	}

	public void contextDestroyed(ServletContextEvent servletContextEvent) {
	}
	
	private static Optional<String> lookupString(String key) {

		try {
			InitialContext context = new InitialContext();
			Object value = context.lookup(key);
			if (value == null) {
				return Optional.empty();
			}
			return Optional.of(value.toString());
		} catch (NamingException e) {
			return Optional.empty();
		}
	}


	/** Start a thread which outputs all the VM's thread stacks traces. */
	private void startThreadDumpThread() {

		Thread t = (new Thread(ApiMirrorServletContextListener.class.getName()) {

			@Override
			public void run() {

				try {
					while (true) {
						TimeUnit.HOURS.sleep(12);
						log.logInfo(GHApiUtil.getAllThreadStacktraces());
					}
				} catch (InterruptedException e) {
					/* ignore */
				}

			}
		});

		t.setDaemon(true);

		t.start();
	}

}
