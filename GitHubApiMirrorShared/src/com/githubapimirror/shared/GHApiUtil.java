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

package com.githubapimirror.shared;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Various utility functions */
public class GHApiUtil {

	public static String toPrettyString(Object j) {
		ObjectMapper om = new ObjectMapper();

		try {
			return om.writerWithDefaultPrettyPrinter().writeValueAsString(j);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}

	}

	public static List<String> readFileIntoLines(File f) {
		List<String> result = new ArrayList<>();

		BufferedReader br = null;
		try {
			FileInputStream fis = new FileInputStream(f);
			br = new BufferedReader(new InputStreamReader(fis));

			String str;
			while (null != (str = br.readLine())) {
				result.add(str);
			}
		} catch (Exception e) {
			throwAsUnchecked(e);
		} finally {
			try {
				if (br != null) {
					br.close();
				}
			} catch (IOException e) {
				/* ignore */
			}
		}

		return result;

	}

	public static void throwAsUnchecked(Throwable t) {

		if (t instanceof RuntimeException) {
			throw (RuntimeException) t;
		} else if (t instanceof IOException) {
			throw new UncheckedIOException((IOException) t);
		} else if (t instanceof Error) {
			throw (Error) t;
		} else {
			throw new RuntimeException(t);
		}

	}

	public static void sleep(long timeInMsecs) {
		try {
			Thread.sleep(timeInMsecs);
		} catch (InterruptedException e) {
			throwAsUnchecked(e);
		}
	}

	/**
	 * Keep calling a runnable until it no longer throws an exception; use an
	 * exponential backoff between failures.
	 */
	public static void waitForPass(int numberOfAttempts, boolean showExceptions, long initialDelayOnFailureInMsecs,
			double growthRate, long maxDelayInMsecs, Runnable r) throws Throwable {

		Throwable lastThrowable = null;

		long delay = initialDelayOnFailureInMsecs;

		while (numberOfAttempts > 0) {
			numberOfAttempts--;

			try {
				r.run();
				return;
			} catch (Throwable t) {
				if (showExceptions) {
					t.printStackTrace();
				}

				lastThrowable = t;
				GHApiUtil.sleep(delay);
				delay *= growthRate;
				if (delay >= maxDelayInMsecs) {
					delay = maxDelayInMsecs;
				}
			}

		}

		throw lastThrowable;
	}

	public static String getAllThreadStacktraces() {

		String CRLF = System.getProperty("line.separator");

		Map<Thread, StackTraceElement[]> threads = Thread.getAllStackTraces();

		List<Map.Entry<Thread, StackTraceElement[]>> threadList = new ArrayList<>();
		threadList.addAll(threads.entrySet());

		Collections.sort(threadList, new Comparator<Map.Entry<Thread, StackTraceElement[]>>() {

			@Override
			public int compare(Entry<Thread, StackTraceElement[]> o1, Entry<Thread, StackTraceElement[]> o2) {
				return (int) (o1.getKey().getId() - o2.getKey().getId());
			}

		});

		StringBuilder sb = new StringBuilder();

		threadList.forEach((e) -> {
			Thread t = e.getKey();
			StackTraceElement[] stes = e.getValue();

			sb.append("- Thread " + t.getId() + " [" + t.getName() + "]: " + CRLF);
			if (stes.length > 0) {
				for (StackTraceElement ste : stes) {
					sb.append("    " + ste + CRLF);
				}
			} else {
				sb.append("    None." + CRLF);
			}
			sb.append("" + CRLF);
		});

		return sb.toString();
	}

}
