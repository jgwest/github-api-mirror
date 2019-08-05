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

package com.githubapimirror.shared;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

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
}
