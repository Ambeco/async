package com.mpd.concurrent.asyncContext;

import com.google.common.flogger.FluentLogger;

import org.checkerframework.checker.nullness.qual.Nullable;

public interface AsyncContextConfig {
	FluentLogger log = FluentLogger.forEnclosingClass();

	AsyncContext onMissingTrace();

	void onDuplicateTrace(AsyncContext oldContext);

	void popTraceMismatch(
			AsyncContext popContext, @Nullable AsyncContext oldContext, @Nullable AsyncContext topContext);

	class DefaultConfig implements AsyncContextConfig {
		@Override public AsyncContext onMissingTrace() {
			MissingRootContextException exception = new MissingRootContextException(
					"ExecutionContext was not propagated. Runnables created in one thread should store the current "
							+ "ExecutionContext when they are created, set it before running, and restore the previous ExecutionContext "
							+ "when they finish. The runnable that didn't propagate the ExecutionContext should be near the root of this callstack.");
			log.atWarning().withCause(exception).log("Missing trace");
			return AsyncContext.setNewRootContext("(Missing Root Trace)");
		}

		@Override public void onDuplicateTrace(AsyncContext oldContext) {
			DuplicateContextsException exception = new DuplicateContextsException(
					"ExecutionContext not correctly ended. Runnables created in one thread should store the current "
							+ "ExecutionContext when they are created, set it when they start running, and restore the previous "
							+ "ExecutionContext when they finish. It seems like a prior runnable did not correctly end it's "
							+ "ExecutionContext");
			log.atWarning().withCause(exception).log("Duplicate trace");
		}

		@Override public void popTraceMismatch(
				AsyncContext popContext, @Nullable AsyncContext oldContext, @Nullable AsyncContext topContext)
		{
			TraceMismatchException exception = new TraceMismatchException(
					"ExecutionContext was not freed. Runnables created in one thread should store the current "
							+ "ExecutionContext when they are created, set it before running, and restore the previous ExecutionContext "
							+ "when they finish.");
			log.atWarning().withCause(exception).log("Mismatched trace stack");
		}
	}

	class MissingRootContextException extends IllegalStateException {
		MissingRootContextException(String msg) {
			super(msg);
		}
	}

	class DuplicateContextsException extends IllegalStateException {
		DuplicateContextsException(String msg) {
			super(msg);
		}
	}

	class TraceMismatchException extends IllegalStateException {
		TraceMismatchException(String msg) {
			super(msg);
		}
	}
}
