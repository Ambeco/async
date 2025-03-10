package com.mpd.concurrent.asyncContext;

import androidx.annotation.NonNull;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.mpd.concurrent.asyncContext.impl.AbstractAsyncContextScope.ContextScopeScopeImpl;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public interface AsyncContextScope extends AutoCloseable {
	AtomicReference<AsyncContextConfig> config = new AtomicReference<>(new AsyncContextConfig.DefaultConfig());
	ThreadLocal<AsyncContextScope> currentScope = new ThreadLocal<>();

	static void initConfig(@Nullable AsyncContextConfig config) {
		AsyncContextScope.config.set(config != null ? config : new AsyncContextConfig.DefaultConfig());
	}

	/**
	 * Gets the current AsyncContext
	 *
	 * If there is no current context, this delegates to the config for handling. The default config reports a warning and
	 * starts a new root.
	 **/
	static AsyncContextScope getCurrentAsyncContextScope() {
		AsyncContextScope context = AsyncContextScope.currentScope.get();
		if (context != null) {
			return context;
		} else {
			AsyncContextScope scope = AsyncContextScope.config.get().onMissingAsyncContextScope(null);
			AsyncContextScope.currentScope.set(scope);
			return scope;
		}
	}

	/**
	 * Starts a new root AsyncContext when external code calls into code you control
	 *
	 * It's scope ends when the Closable is closed, though async work can still add child scopes
	 *
	 * TODO: Resolve conflict between "scope" and remaining available for children, without having forked.
	 * TODO: Probably via a cleaner separation between an AsyncContext and a Scope.
	 *
	 * <code>
	 * public void onStart() {
	 * try (val context = AsyncContext.startRoot("MyAndroidActivity#onStart") {
	 * //your code, which might submit runnables to Executors
	 * }
	 * }
	 * </code>
	 */
	static AsyncContextScope newRootScope(@CompileTimeConstant Object name) {
		return ContextScopeScopeImpl.newRootScope(name);
	}

	/**
	 * Notes that the current AsyncContext will have a child scopes at a later time, possible in a later thread.
	 *
	 * It's scope begins when resumeAsyncContext is called, and ends when the Closable is closed. The deferred scope can
	 * only be entered once.
	 *
	 * <code>
	 * public void onStart() { try (val context = AsyncContext.startRoot("MyAndroidActivity#onStart") { //your code, which
	 * might submit runnables to Executors } }
	 * </code>
	 */
	static DeferredContextScope newDeferredScope(@CompileTimeConstant Object name) {
		return ContextScopeScopeImpl.newDeferredScope(name);
	}

	// scope completion means that all children, recursively, have completed, not that the synchronous scope ended.
	void privateOnChildComplete(AsyncContext child);

	AsyncContext getAsyncContext();

	@Override void close(); // does not throw


	/**
	 * Appends the stack of Context names up to this one
	 *
	 * If there's a root "RootName", with a child scope "CSpan1", and that has a child "Foo", and this is called on the
	 * "Foo" context, then this emits "RootName/CSpan1/Foo" to the StringBuilder.
	 */
	void appendContextStack(StringBuilder sb, int maxDepth);

	@MonotonicNonNull AsyncContextScope getParentScope();

	void toString(StringBuilder sb);

	@NonNull @Override String toString();

	interface DeferredContextScope {
		AsyncContext getAsyncContext();

		AsyncContextScope resumeAsyncContext();
	}
}
