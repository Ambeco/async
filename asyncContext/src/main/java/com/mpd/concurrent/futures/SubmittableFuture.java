package com.mpd.concurrent.futures;

import com.mpd.concurrent.asyncContext.AsyncContext;
import com.mpd.concurrent.executors.Executor.RunnablePriority;

// a Runnable that can be submitted to a executor and is its own Future
public interface SubmittableFuture<O> extends Future<O>, Runnable {

	AsyncContext getAsyncContext();

	RunnablePriority getRunnablePriority();

	class RunCalledTwiceException extends IllegalStateException {
		public RunCalledTwiceException() {}

		public RunCalledTwiceException(String message) {
			super(message);
		}

		public RunCalledTwiceException(String message, Throwable cause) {
			super(message, cause);
		}

		public RunCalledTwiceException(Throwable cause) {
			super(cause);
		}
	}

	class ImplementationDidNotCompleteOrAsyncException extends IllegalStateException {
		public ImplementationDidNotCompleteOrAsyncException() {}

		public ImplementationDidNotCompleteOrAsyncException(String message) {
			super(message);
		}

		public ImplementationDidNotCompleteOrAsyncException(String message, Throwable cause) {
			super(message, cause);
		}

		public ImplementationDidNotCompleteOrAsyncException(Throwable cause) {
			super(cause);
		}
	}
}
