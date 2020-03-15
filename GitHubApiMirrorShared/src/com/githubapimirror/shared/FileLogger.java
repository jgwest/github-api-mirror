/*
 * Copyright 2020 Jonathan West
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

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class FileLogger {

	private static final SimpleDateFormat PRETTY_DATE_FORMAT = new SimpleDateFormat("MMM d h:mm:ss.SSS a");

	private boolean pathOutput_synch_entries = false;

	private final List<FileLoggerEntry> logEntries_synch = new ArrayList<>();

	private final Optional<FLThread> innerThread;

	private final Optional<Path> outputPath;

	public FileLogger(Path outputFile /* nullable */) {
		this.outputPath = Optional.ofNullable(outputFile);

		if (this.outputPath.isPresent()) {
			innerThread = Optional.of(new FLThread(outputFile));
			innerThread.get().start();
		} else {
			innerThread = Optional.empty();
		}

	}

	public void out(String str) {
		add(str, false);
	}

	public void err(String str) {
		add(str, true);
	}

	private void add(String str, boolean stdErr) {

		if (!outputPath.isPresent()) {
			return;
		}

		str = "" + (PRETTY_DATE_FORMAT.format(new Date())) + " " + str;

		FileLoggerEntry e = new FileLoggerEntry(str, stdErr);

		synchronized (logEntries_synch) {

			if (!pathOutput_synch_entries) {
				pathOutput_synch_entries = true;
				System.out.println("File logger logging to: " + outputPath);
			}

			logEntries_synch.add(e);
			logEntries_synch.notify();
		}

	}

	private class FLThread extends Thread {

		private final OutputStream os;

		public FLThread(Path outputFile) {
			setDaemon(true);
			setName(FLThread.class.getName());

			try {
				if (Files.exists(outputFile)) {
					os = Files.newOutputStream(outputFile, StandardOpenOption.APPEND);
				} else {
					os = Files.newOutputStream(outputFile);
				}

			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}

		}

		@Override
		public void run() {

			List<FileLoggerEntry> local = new ArrayList<>();

			while (true) {
				synchronized (logEntries_synch) {
					try {
						logEntries_synch.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					local.addAll(logEntries_synch);
					logEntries_synch.clear();
				}

				local.forEach(e -> {
					byte[] barr = (e.msg + "\n").getBytes();
					try {
						if (e.stderr) {
							os.write(barr);
						} else {
							os.write(barr);
						}
					} catch (IOException e1) {
						throw new UncheckedIOException(e1);
					}
				});

				try {
					os.flush();
				} catch (IOException e1) {
					throw new UncheckedIOException(e1);
				}

				local.clear();
			}
		}
	}

	private static class FileLoggerEntry {
		final String msg;
		final boolean stderr;

		FileLoggerEntry(String msg, boolean stderr) {
			this.msg = msg;
			this.stderr = stderr;
		}

	}
}
