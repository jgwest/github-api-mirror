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

package com.githubapimirror;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * This class allows a runnable to report when work has completed; if work is
 * not completed within a fixed interval, then the runnable is interrupted.
 * 
 * This class was primarily written to handle the case where (very rarely)
 * requests to GitHub are accepted but never answered, and likewise they don't
 * time out our HTTP request timeouts in the dependency GitHub client libraries.
 */
public class HeartbeatThreadRunner<T> {

	private final Object lock = new Object();

	// We have must have seen at least one piece of new work in this interval
	private final static long MAX_ELAPSED_TIME_IN_NANOS = TimeUnit.NANOSECONDS.convert(5, TimeUnit.MINUTES);

	private long lastWorkSeenInNanos_synch_lock;

	private final HeartbeatThreadRunnable<T> runnable;

	public HeartbeatThreadRunner(HeartbeatThreadRunnable<T> runnable) {
		this.lastWorkSeenInNanos_synch_lock = System.nanoTime();
		this.runnable = runnable;
	}

	public Optional<T> run() throws Exception {

		HeartbeatThread<T> thread = new HeartbeatThread<T>(runnable, this);
		thread.start();

		while (!thread.isComplete()) {

			try {
				thread.join(1000);
			} catch (InterruptedException e) {
				throw e;
			}

			long elapsedTimeSinceLastWork;
			synchronized (lock) {
				elapsedTimeSinceLastWork = System.nanoTime() - lastWorkSeenInNanos_synch_lock;
			}

			if (elapsedTimeSinceLastWork > MAX_ELAPSED_TIME_IN_NANOS) {
				thread.interrupt();
				return Optional.empty();
			}
		}

		if (thread.getException().isPresent()) {
			throw thread.getException().get();
		}

		return thread.getReturnValue();

	}

	public void informWorkUnitCompleted() {
		synchronized (lock) {
			lastWorkSeenInNanos_synch_lock = System.nanoTime();
		}
	}

	/**
	 * This thread is responsible for invoking the runner and managing its life
	 * cycle.
	 */
	private static class HeartbeatThread<T> extends Thread {
		private final HeartbeatThreadRunner<T> parent;

		private final HeartbeatThreadRunnable<T> runnable;

		private final Object lock = new Object();

		private boolean isComplete_synch_lock = false;

		private Exception thrownException_synch_lock;

		private T returnValue_synch_lock;

		public HeartbeatThread(HeartbeatThreadRunnable<T> runnable, HeartbeatThreadRunner<T> parent) {
			this.runnable = runnable;
			this.parent = parent;
			this.setName(runnable.getClass().getName());
			this.setDaemon(true);
		}

		@Override
		public void run() {

			Exception thrownException = null;
			T result = null;
			try {

				try {
					result = runnable.run(parent);
				} catch (Exception e) {
					thrownException = e;
				}

			} finally {

				synchronized (lock) {
					returnValue_synch_lock = result;
					thrownException_synch_lock = thrownException;
					isComplete_synch_lock = true;
				}

			}
		}

		public Optional<T> getReturnValue() {
			synchronized (lock) {
				return Optional.ofNullable(returnValue_synch_lock);
			}
		}

		public Optional<Exception> getException() {
			synchronized (lock) {
				return Optional.ofNullable(thrownException_synch_lock);
			}
		}

		public boolean isComplete() {
			synchronized (lock) {
				return isComplete_synch_lock;
			}
		}

	}

	/**
	 * A generic runner which may throw an exception and return a value; it should
	 * also report when work is completed via 'thr', so that it is not interrupted.
	 */
	public interface HeartbeatThreadRunnable<T> {
		public T run(HeartbeatThreadRunner<T> thr) throws Exception;
	}

}
