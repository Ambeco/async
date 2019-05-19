package com.tbohne.async;

/**
 * The result of an async operation referred to by a future.
 */
public interface FutureResult<R> {
	boolean succeeded();

	/**
	 * @return exception if future did not succeed, null otherwise
	 */
	RuntimeException getThrownException();

	/**
	 * Nonblocking fetch of result.
	 *
	 * @return result of async work if future did succeed
	 * @throws {@link RuntimeException} if the future did not succeed, or
	 *                {@link IllegalStateException} if the future did not complete.
	 */
	R getNow();
}
