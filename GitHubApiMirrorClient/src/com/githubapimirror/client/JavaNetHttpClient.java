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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubapimirror.shared.GHApiUtil;

/** Simple HTTP client using the java.net API. */
public class JavaNetHttpClient {

	private final String presharedKey;
	private final String apiUrl; // Will not end with a slash, will begin with http(s)://

	private static final String HEADER_AUTHORIZATION = "Authorization";

	public JavaNetHttpClient(String apiUrl, String presharedKey) {
		if (!apiUrl.startsWith("http://") && !apiUrl.startsWith("https://")) {
			throw new IllegalArgumentException("API URL must begin with HTTP(S) prefix");
		}

		apiUrl = ensureDoesNotEndWithSlash(apiUrl);

		this.apiUrl = apiUrl;
		this.presharedKey = presharedKey;
	}

	public <T> ApiResponse<T> get(String apiUrl, Class<T> clazz) {

		ApiResponse<String> body = getRequest(apiUrl);

//		log.out(body.getResponse());

		T parsed = null;
		ObjectMapper om = new ObjectMapper();
		try {
			parsed = om.readValue(body.getResponse(), clazz);
		} catch (Exception e) {
			throw GHApiMirrorClientException.createFromThrowable(e);
		}

		return new ApiResponse<T>(parsed, body.getResponse());

	}

	public <T> ApiResponse<T> post(String apiUrl, Class<T> clazz) {

		ApiResponse<String> body = postRequest(apiUrl);

//		log.out(body.getResponse());

		T parsed = null;
		ObjectMapper om = new ObjectMapper();
		try {
			String response = body.getResponse();
			if (response.equals("")) {
				response = "{}";
			}
			parsed = om.readValue(response, clazz);
		} catch (Exception e) {
			throw GHApiMirrorClientException.createFromThrowable(e);
		}

		return new ApiResponse<T>(parsed, body.getResponse());

	}

	private ApiResponse<String> getRequest(String requestUrlParam) {

		requestUrlParam = ensureDoesNotBeginsWithSlash(requestUrlParam);

		String body = null;
		HttpURLConnection httpRequest;
		try {
			httpRequest = createConnection(this.apiUrl + "/" + requestUrlParam, "GET", presharedKey);
			final int code = httpRequest.getResponseCode();

			InputStream is = httpRequest.getInputStream();

			body = getBody(is);
			is.close();

			if (code != 200) {
				throw new RuntimeException("Request failed - HTTP Code: " + code + "  body: " + body);
			}

		} catch (IOException e) {
			throw GHApiMirrorClientException.createFromThrowable(e);
		}

		return new ApiResponse<String>(body, body);

	}

	private ApiResponse<String> postRequest(String requestUrlParam) {

		requestUrlParam = ensureDoesNotBeginsWithSlash(requestUrlParam);

		String body = null;
		HttpURLConnection httpRequest;
		try {
			httpRequest = createConnection(this.apiUrl + "/" + requestUrlParam, "POST", presharedKey);
			final int code = httpRequest.getResponseCode();

			InputStream is = httpRequest.getInputStream();

			body = getBody(is);
			is.close();

			if (code != 200) {
				throw new RuntimeException("Request failed - HTTP Code: " + code + "  body: " + body);
			}

		} catch (IOException e) {
			throw GHApiMirrorClientException.createFromThrowable(e);
		}

		return new ApiResponse<String>(body, body);

	}

	private static HttpURLConnection createConnection(String uri, String method, String authorization)
			throws IOException {

		URL url = new URL(uri);

		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		if (connection instanceof HttpsURLConnection) {
			HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
			httpsConnection.setSSLSocketFactory(generateSslContext().getSocketFactory());
			httpsConnection.setHostnameVerifier((a, b) -> true);
		}
		connection.setRequestMethod(method);

		if (authorization != null) {
			connection.setRequestProperty(HEADER_AUTHORIZATION, authorization);
		}

		return connection;
	}

	private static String getBody(InputStream is) {
		StringBuilder sb = new StringBuilder();
		int c;
		byte[] barr = new byte[1024 * 64];
		try {
			while (-1 != (c = is.read(barr))) {
				sb.append(new String(barr, 0, c));
			}
		} catch (IOException e) {
			GHApiUtil.throwAsUnchecked(e);
		}
		return sb.toString();
	}

	private static SSLContext generateSslContext() {
		SSLContext sslContext = null;
		try {
			sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, new TrustManager[] { new X509TrustManager() {
				public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
				}

				public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
				}

				public X509Certificate[] getAcceptedIssuers() {

					return new X509Certificate[0];
				}

			} }, new java.security.SecureRandom());
		} catch (Exception e) {
			throw GHApiMirrorClientException.createFromThrowable(e);
		}

		return sslContext;
	}

	private static String ensureDoesNotBeginsWithSlash(String input) {
		while (input.startsWith("/")) {
			input = input.substring(1);
		}

		return input;
	}

	@SuppressWarnings("unused")
	private static String ensureEndsWithSlash(String input) {
		if (!input.endsWith("/")) {
			input = input + "/";
		}
		return input;
	}

	private static String ensureDoesNotEndWithSlash(String input) {
		while (input.endsWith("/")) {
			input = input.substring(0, input.length() - 1);
		}

		return input;

	}

}
