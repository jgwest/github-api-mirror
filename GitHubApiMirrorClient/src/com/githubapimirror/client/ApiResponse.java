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

package com.githubapimirror.client;

import com.githubapimirror.shared.GHApiUtil;

/**
 * Used by the GHApiMirrorHttpClient, to return both the unmarshalled JSON, and
 * the raw JSON string.
 */
public class ApiResponse<T> {

	private final T response;

	private final String responseBody;

	public ApiResponse(T response, String responseBody) {
		this.response = response;
		this.responseBody = responseBody;
	}

	public T getResponse() {
		return response;
	}

	public String getResponseBody() {
		return responseBody;
	}

	public String asPrettyString() {
		return GHApiUtil.toPrettyString(response);
	}
}
