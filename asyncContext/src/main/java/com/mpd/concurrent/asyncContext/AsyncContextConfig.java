package com.mpd.concurrent.asyncContext;

import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.mpd.concurrent.asyncContext.impl.AbstractAsyncContextScope.ContextScopeScopeImpl;
import org.checkerframework.checker.nullness.qual.Nullable;

public interface AsyncContextConfig {
	FluentLogger log = FluentLogger.forEnclosingClass();

	AsyncContextScope onMissingAsyncContextScope(@Nullable @CompileTimeConstant Object name);

	AsyncContextScope onNewRootInExistingScope(AsyncContextScope oldScope, AsyncContextScope newRootScope);

	AsyncContextScope onResumeOverLeakedScope(AsyncContextScope leakedScope, AsyncContextScope resumingScope);

	void onEndOverLeakedScope(
			AsyncContextScope leakedScope, AsyncContextScope closingScope, @Nullable AsyncContextScope previousScope);

	class DefaultConfig implements AsyncContextConfig {
		@Override public AsyncContextScope onMissingAsyncContextScope(@Nullable @CompileTimeConstant Object name) {
			MissingAsyncContextScopeException exception = new MissingAsyncContextScopeException(
					"Current thread is not associated with an AsyncContext Scope. Runnables created in one thread should store the current "
							+ "ExecutionContext when they are created, set it before running, and restore the previous ExecutionContext "
							+ "when they finish. The runnable that didn't propagate the ExecutionContext should be near the root of this callstack.");
			log.atWarning().withCause(exception).log("Missing trace");
			return ContextScopeScopeImpl.newRootScope("(Missing Root Trace)");
		}

		@Override
		public AsyncContextScope onNewRootInExistingScope(AsyncContextScope oldScope, AsyncContextScope newRootScope) {
			DuplicateContextsException exception = new DuplicateContextsException(
					"ExecutionContext not correctly ended. Runnables created in one thread should store the current "
							+ "ExecutionContext when they are created, set it when they start running, and restore the previous "
							+ "ExecutionContext when they finish. It seems like a prior runnable did not correctly end it's "
							+ "ExecutionContext");
			log.atWarning().withCause(exception).log("Duplicate trace");
			return newRootScope;
		}

		@Override
		public AsyncContextScope onResumeOverLeakedScope(AsyncContextScope leakedScope, AsyncContextScope resumingScope) {
			DuplicateContextsException exception = new DuplicateContextsException(
					"ExecutionContext not correctly ended. Runnables created in one thread should store the current "
							+ "ExecutionContext when they are created, set it when they start running, and restore the previous "
							+ "ExecutionContext when they finish. It seems like a prior runnable did not correctly end it's "
							+ "ExecutionContext");
			log.atWarning().withCause(exception).log("Duplicate trace");
			return resumingScope;
		}

		@Override public void onEndOverLeakedScope(
				AsyncContextScope leakedScope, AsyncContextScope closingScope, @Nullable AsyncContextScope previousScope)
		{
			TraceMismatchException exception = new TraceMismatchException(
					"ExecutionContext was not freed. Runnables created in one thread should store the current "
							+ "ExecutionContext when they are created, set it before running, and restore the previous ExecutionContext "
							+ "when they finish.");
			log.atWarning().withCause(exception).log("Mismatched trace stack");
		}
	}

	class MissingAsyncContextScopeException extends IllegalStateException {
		MissingAsyncContextScopeException(String msg) {
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
