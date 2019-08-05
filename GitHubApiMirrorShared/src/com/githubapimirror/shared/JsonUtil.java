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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** JSON utility functions */
public class JsonUtil {

	public static String toString(Object j) {
		ObjectMapper om = new ObjectMapper();
		try {
			return om.writeValueAsString(j);
		} catch (JsonProcessingException e) {
			GHApiUtil.throwAsUnchecked(e);
			return null;
		}
	}

	public static String toPrettyString(Object j) {
		ObjectMapper om = new ObjectMapper();

		try {
			return om.writerWithDefaultPrettyPrinter().writeValueAsString(j);
		} catch (JsonProcessingException e) {
			GHApiUtil.throwAsUnchecked(e);
			return null;
		}

	}
}
