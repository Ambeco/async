package com.tbohne.asynclocked;

import java.util.Set;

/**
 * Prerequisite waiting strategies
 */
public enum PrereqStrategy {
	/**
	 * Waits for all prerequisites to complete with either a success or exception.
	 */
	ALL_PREREQS_COMPLETE {
		public boolean areReady(Set<Future> prerequisites,
				Future completedFuture,
				boolean futureSucceeded) {
			prerequisites.remove(completedFuture);
			return prerequisites.isEmpty();
		}
	},
	/**
	 * Waits for all prerequisites to succeed, or any to throw an exception.
	 */
	ALL_PREREQS_SUCCEED {
		public boolean areReady(Set<Future> prerequisites,
				Future completedFuture,
				boolean futureSucceeded) {
			if (futureSucceeded) {
				prerequisites.remove(completedFuture);
				return prerequisites.isEmpty();
			} else {
				prerequisites.clear();
				return true;
			}
		}
	},
	/**
	 * Waits for any prerequisites to complete with either a success or exception.
	 */
	ANY_PREREQS_COMPLETE {
		public boolean areReady(Set<Future> prerequisites,
				Future completedFuture,
				boolean futureSucceeded) {
			prerequisites.clear();
			return true;
		}
	},
	/**
	 * Waits for any prerequisites to succeed, or all to have exceptions.
	 */
	ANY_PREREQS_SUCCEED {
		public boolean areReady(Set<Future> prerequisites,
				Future completedFuture,
				boolean futureSucceeded) {
			if (futureSucceeded) {
				prerequisites.clear();
				return true;
			} else {
				prerequisites.remove(completedFuture);
				return prerequisites.isEmpty();
			}
		}
	};

	/**
	 * Checks if the prerequisites have been satisfied.
	 * <p>
	 * This removes completed futures from the list, and if the conditions are met, clears the list
	 * entirely. This helps keep down memory usage.
	 *
	 * @param prerequisites   Mutable list of prerequisites. This may remove the completedFuture.
	 * @param completedFuture Prerequisite that has just completed
	 * @param futureSucceeded true if completedFuture succeeded, or false otherwise.
	 * @return true if the prerequisites have been satisified, or false otherwise.
	 */
	public abstract boolean areReady(Set<Future> prerequisites,
			Future completedFuture,
			boolean futureSucceeded);
}
