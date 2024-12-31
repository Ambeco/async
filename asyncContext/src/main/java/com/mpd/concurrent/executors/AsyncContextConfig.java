package com.mpd.concurrent.executors;

import android.util.Log;

import org.checkerframework.checker.nullness.qual.Nullable;

public interface AsyncContextConfig {
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
			Log.w("ExecutionContextConfig", "Missing trace", exception);
			return AsyncContext.setNewRootContext("(Missing Root Trace)");
		}

		@Override public void onDuplicateTrace(AsyncContext oldContext) {
			DuplicateContextsException exception = new DuplicateContextsException(
					"ExecutionContext not correctly ended. Runnables created in one thread should store the current "
							+ "ExecutionContext when they are created, set it when they start running, and restore the previous "
							+ "ExecutionContext when they finish. It seems like a prior runnable did not correctly end it's "
							+ "ExecutionContext");
			Log.w("ExecutionContextConfig", "Duplicate traces", exception);
		}

		@Override public void popTraceMismatch(
				AsyncContext popContext, @Nullable AsyncContext oldContext, @Nullable AsyncContext topContext)
		{
			TraceMismatchException exception = new TraceMismatchException(
					"ExecutionContext was not freed. Runnables created in one thread should store the current "
							+ "ExecutionContext when they are created, set it before running, and restore the previous ExecutionContext "
							+ "when they finish.");
			Log.w("ExecutionContextConfig", "Duplicate traces", exception);
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
