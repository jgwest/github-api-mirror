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

package org.acme;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.githubapimirror.GHLog;
import com.githubapimirror.service.ApiMirrorInstance;
import com.githubapimirror.shared.GHApiUtil;

/**
 * This class ensures that when the server is started, that the ZHDatabase and
 * ZHServerInstance also start (kicking off the necessary background threads).
 */
@WebListener
@ApplicationScoped
public class ApiMirrorServletContextListener implements ServletContextListener {

	@ConfigProperty(name = "config.path")
	Optional<String> configPathProperty;

	@ConfigProperty(name = "db.path")
	Optional<String> dbPathProperty;

	private final GHLog log = GHLog.getInstance();

	public void contextInitialized(ServletContextEvent servletContextEvent) {
		try {
			log.logInfo("* Quarkus GitHubApiMirrorService started: " + configPathProperty);

			ApiMirrorInstance.getInstance().initializeServerInstance(configPathProperty.orElse(null),
					dbPathProperty.orElse(null));
			
			startThreadDumpThread();
		} catch (RuntimeException re) {
			re.printStackTrace();
			throw re;
		}
	}

	public void contextDestroyed(ServletContextEvent servletContextEvent) {
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
