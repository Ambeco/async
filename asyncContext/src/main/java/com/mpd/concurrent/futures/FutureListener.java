package com.mpd.concurrent.futures;

import com.mpd.concurrent.executors.Executor;

public interface FutureListener<I> {
	void onFutureSucceeded(Future<? extends I> future, I result);

	void onFutureFailed(Future<? extends I> future, Throwable exception, boolean mayInterruptIfRunning);

	class OnFutureCompleteCalledTwiceException extends IllegalStateException {
		public OnFutureCompleteCalledTwiceException() {}

		public OnFutureCompleteCalledTwiceException(String message) {
			super(message);
		}

		public OnFutureCompleteCalledTwiceException(String message, Throwable cause) {
			super(message, cause);
		}

		public OnFutureCompleteCalledTwiceException(Throwable cause) {
			super(cause);
		}
	}

	class WrongParentFutureException extends IllegalStateException {
		public WrongParentFutureException() {}

		public WrongParentFutureException(String message) {
			super(message);
		}

		public WrongParentFutureException(String message, Throwable cause) {
			super(message, cause);
		}

		public WrongParentFutureException(Throwable cause) {
			super(cause);
		}
	}

	class RunnableListener<I> implements FutureListener<I> {
		Runnable runnable;
		Executor executor;

		public RunnableListener(Runnable runnable, Executor executor) {
			this.runnable = runnable;
			this.executor = executor;
		}

		@Override public void onFutureSucceeded(Future<? extends I> future, I result) {
			executor.submit(runnable).end();
		}

		@Override
		public void onFutureFailed(Future<? extends I> future, Throwable exception, boolean mayInterruptIfRunning) {
			executor.submit(runnable).end();
		}
	}
}
