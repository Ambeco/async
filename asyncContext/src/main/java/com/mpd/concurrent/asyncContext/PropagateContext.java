package com.mpd.concurrent.asyncContext;

import com.mpd.concurrent.AsyncFunction;
import com.mpd.concurrent.AsyncSupplier;
import com.mpd.concurrent.asyncContext.AsyncContextScope.DeferredContextScope;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class PropagateContext {

	private PropagateContext() {}

	public static <T, U> Function<T, U> propagate(Function<T, U> f) {
		DeferredContextScope propagatedContext = AsyncContextScope.newDeferredScope(f);
		return (t) -> {
			try (AsyncContextScope ignored = propagatedContext.resumeAsyncContext()) {
				return f.apply(t);
			}
		};
	}

	public static <T, U> AsyncFunction<T, U> propagate(AsyncFunction<T, U> f) {
		DeferredContextScope propagatedContext = AsyncContextScope.newDeferredScope(f);
		return (t) -> {
			try (AsyncContextScope ignored = propagatedContext.resumeAsyncContext()) {
				return f.apply(t);
			}
		};
	}

	public static <T> Supplier<T> propagate(Supplier<T> f) {
		DeferredContextScope propagatedContext = AsyncContextScope.newDeferredScope(f);
		return () -> {
			try (AsyncContextScope ignored = propagatedContext.resumeAsyncContext()) {
				return f.get();
			}
		};
	}

	public static <T> AsyncSupplier<T> propagate(AsyncSupplier<T> f) {
		DeferredContextScope propagatedContext = AsyncContextScope.newDeferredScope(f);
		return () -> {
			try (AsyncContextScope ignored = propagatedContext.resumeAsyncContext()) {
				return f.call();
			}
		};
	}

	public static <T> Consumer<T> propagate(Consumer<T> f) {
		DeferredContextScope propagatedContext = AsyncContextScope.newDeferredScope(f);
		return (t) -> {
			try (AsyncContextScope ignored = propagatedContext.resumeAsyncContext()) {
				f.accept(t);
			}
		};
	}

	public static Runnable propagate(Runnable f) {
		DeferredContextScope propagatedContext = AsyncContextScope.newDeferredScope(f);
		return () -> {
			try (AsyncContextScope ignored = propagatedContext.resumeAsyncContext()) {
				f.run();
			}
		};
	}
}
