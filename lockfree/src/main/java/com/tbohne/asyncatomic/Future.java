package com.tbohne.asyncatomic;

import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * A generic Future.
 * <p>
 * Generally code will use ValueFuture or VoidFuture directly.  It's worth noting that a future
 * can only complete once, and once it has completed, it will always contain that same result or
 * exception.
 */
public interface Future {
	boolean completed();

	boolean succeeded();

	/**
	 * @return The exception from the async work if the operation did not succeed, or null otherwise.
	 * If the operation was cancelled, this will be a {@link CancellationException}.
	 */
	RuntimeException getThrownException();

	/**
	 * @return true if the operation was cancelled, false otherwise
	 */
	boolean isCancelled();

	/**
	 * @param exception optional exception to be the reason for chained cancellations
	 * @return true if the operation was cancelled, or false if it was already completed for another reason.
	 */
	boolean cancel(CancellationException exception);

	/**
	 * Notifies the async work that one of the listeners was cancelled.
	 * If all listeners are cancelled, this work will also be cancelled.
	 *
	 * @param listener  the listener that was cancelled.
	 * @param exception optional exception to be the reason for chained cancellations.
	 */
	void cancelListener(FutureListener listener, CancellationException exception);

	/**
	 * Attempt to get the stack traces of any threads executing this work, or prerequisite work.
	 * This is best effort, it is possible that threads may be executing callbacks rather than work,
	 * so may be missed, or counted twice.
	 *
	 * @param stacks
	 */
	void fillStackTraces(List<StackTraceElement[]> stacks);

	/**
	 * @param future possible prerequisite
	 * @return true if this work is waiting on that future directly, false otherwise
	 */
	boolean isPrerequisite(Future future);

	/**
	 * Adds a FutureListener to be called when this Future is complete.
	 * <p>
	 * If followup is a Future that is not already a prerequisite, throws IllegalStateException.
	 * These methods should not throw an exception, or it will propagate to the Executor, and
	 * usually cause the application to crash.
	 *
	 * @return a leaf future that you cannot attach followups to. By guaranteeing that every chain
	 * ends in this generic Future, you know all paths are complete and no exceptions will be lost.
	 */
	Future addListener(FutureListener followup);

	/**
	 * Adds an anonymous listener, to prevent children from cancelling.
	 * After this, the Future can only be cancelled directly.
	 */
	void childrenCannotCancel();

	/**
	 * Listener for a future completing.  These methods should not throw an exception, or it
	 * will propagate to the Executor, and usually cause the application to crash.
	 */
	interface FutureListener {
		void onSuccess(Future future);

		void onFailure(Future future,
				RuntimeException t); //common implementation is merely to rethrow to children futures
	}
}