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

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NewFileLogger {

	private static final SimpleDateFormat PRETTY_DATE_FORMAT = new SimpleDateFormat("MMM d h:mm:ss.SSS a");

	private static final long startTimeInNanos = System.nanoTime();

	private InternalRollingLogger fileLogger_synch_lock = null;

	private final Object lock = new Object();

	public NewFileLogger(Path outputDir) {
		if (outputDir == null) {
			return;
		}

		fileLogger_synch_lock = new InternalRollingLogger(outputDir);
		fileLogger_synch_lock.start();
	}

	public void out(String str) {
		if (fileLogger_synch_lock == null) {
			return;
		}

		String output = time() + " " + str;

		synchronized (lock) {
			if (fileLogger_synch_lock != null) {
				fileLogger_synch_lock.addOutMsgToQueue(output);
			}
		}
	}

	public void err(String str) {
		if (fileLogger_synch_lock == null) {
			return;
		}

		String output = time() + " " + str;

		synchronized (lock) {
			if (fileLogger_synch_lock != null) {
				fileLogger_synch_lock.addErrMsgToQueue(output);
			}
		}

	}

	private static final String time() {
		long time = TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTimeInNanos, TimeUnit.NANOSECONDS);

		long seconds = time / 1000;

		long msecs = time % 1000;

		String msecsStr = Long.toString(msecs);

		while (msecsStr.length() < 3) {
			msecsStr = "0" + msecsStr;
		}

		return PRETTY_DATE_FORMAT.format(new Date()) + " [" + seconds + "." + msecsStr + "]";

	}

	private static class InternalRollingLogger extends Thread {
		private final Path logDir;

		private List<FileLoggerEntry> entries_synch_lock = new ArrayList<>();

		private final Object lock = new Object();

		private final String FILE_PREFIX = "gham-";
		private final String FILE_SUFFIX = ".log";

		private final long MAX_LOG_FILE_SIZE = 1024 * 1024 * 50;

		public InternalRollingLogger(Path logDir) {
			setName(this.getClass().getName());
			setDaemon(true);
			this.logDir = logDir;
		}

		@Override
		public void run() {

			final String EOL = System.lineSeparator();

			// Delete old log files from previous processes
			try {
				Files.list(logDir)
						.filter(e -> e.getFileName().startsWith(FILE_PREFIX) && e.getFileName().endsWith(FILE_SUFFIX))
						.forEach(e -> {
							try {
								Files.delete(e);
							} catch (IOException e1) {
								throw new UncheckedIOException(e1);
							}
						});
			} catch (IOException e2) {
				throw new UncheckedIOException(e2);
			}

			int currNumber = 0;

			FileWriter fw = null;
			long charsLogged = 0;

			List<FileLoggerEntry> entries = new ArrayList<>();
			while (true) {

				if (fw == null) {
					try {
						currNumber++;
						charsLogged = 0;

						Path toDelete = logDir.resolve(FILE_PREFIX + (currNumber - 2) + FILE_SUFFIX);
						Files.deleteIfExists(toDelete);

						fw = new FileWriter(logDir.resolve(FILE_PREFIX + currNumber + FILE_SUFFIX).toFile());
					} catch (IOException e1) {
						fw = null;
					}
				}

				synchronized (lock) {
					try {
						lock.wait();

						entries.addAll(entries_synch_lock);
						entries_synch_lock.clear();

					} catch (InterruptedException e) {
						GHApiUtil.sleep(100);
					}

				}
				if (entries.size() > 0) {
					try {

						for (FileLoggerEntry e : entries) {
							fw.write(e.msg + EOL);
							charsLogged += e.msg.length();
						}

						fw.flush();

						if (charsLogged > MAX_LOG_FILE_SIZE) {
							fw.close();
							fw = null;
						}
					} catch (IOException e1) {
						/* ignore */
					}

					entries.clear();

				}

			}
		}

		private void addOutMsgToQueue(String msg) {
			synchronized (lock) {
				entries_synch_lock.add(new FileLoggerEntry(FileLoggerEntry.Type.OUT, msg));
				lock.notify();
			}
		}

		private void addErrMsgToQueue(String msg) {
			synchronized (lock) {
				entries_synch_lock.add(new FileLoggerEntry(FileLoggerEntry.Type.ERR, msg));
				lock.notify();
			}
		}

		private static class FileLoggerEntry {
			enum Type {
				OUT, ERR
			};

			private final String msg;
			@SuppressWarnings("unused")
			private final Type type;

			public FileLoggerEntry(Type type, String msg) {
				this.msg = msg;
				this.type = type;
			}

		}
	}
}
