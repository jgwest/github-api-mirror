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

package com.githubapimirror.tests;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.githubapimirror.ServerInstance;
import com.githubapimirror.ServerInstance.ServerInstanceBuilder;
import com.githubapimirror.shared.GHApiUtil;

/**
 * Utility methods used by the test class(es).
 * 
 */
public abstract class AbstractTest {

	void waitForPass(long timeToWaitInSeconds, Runnable r) {
		waitForPass(timeToWaitInSeconds, false, r);

	}

	void waitForPass(long timeToWaitInSeconds, boolean showExceptions, Runnable r) {

		long expireTimeInNanos = System.nanoTime()
				+ TimeUnit.NANOSECONDS.convert(timeToWaitInSeconds, TimeUnit.SECONDS);

		Throwable lastThrowable = null;

		int delay = 50;

		while (System.nanoTime() < expireTimeInNanos) {

			try {
				r.run();
				return;
			} catch (Throwable t) {
				if (showExceptions) {
					t.printStackTrace();
				}

				lastThrowable = t;
				GHApiUtil.sleep(delay);
				delay *= 1.5;
				if (delay >= 1000) {
					delay = 1000;
				}
			}

		}

		if (lastThrowable instanceof RuntimeException) {
			throw (RuntimeException) lastThrowable;
		} else {
			throw (Error) lastThrowable;
		}

	}

	protected static ServerInstanceBuilder getClientBuilder() {
		File f = new File(new File(System.getProperty("user.home"), ".githubapitests"), "test.properties");

		Properties props = new Properties();
		if (f.exists()) {
			try {
				props.load(new FileInputStream(f));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		String ghUsername = getProperty("github.username", props);

		String ghPassword = getProperty("github.password", props);

		ServerInstanceBuilder build = ServerInstance.builder();

		build = build.credentials(ghUsername, ghPassword);

		return build;

	}

	private static String getProperty(String property, Properties props) {

		String apiKey = (String) props.getOrDefault(property, null);

		if (apiKey == null) {
			apiKey = System.getProperty(property);
		}

		if (apiKey == null) {
			fail("Unable to find property '" + property + "'. Specify with -D" + property
					+ "=(...value..), see com.githubapimirror.tests.AbstractTest for details.");
		}

		return apiKey;

	}

}
