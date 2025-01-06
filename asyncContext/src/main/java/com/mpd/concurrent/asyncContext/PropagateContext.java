package com.mpd.concurrent.asyncContext;

import com.mpd.concurrent.AsyncFunction;
import com.mpd.concurrent.AsyncSupplier;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class PropagateContext {

	private PropagateContext() {}

	public static <T, U> Function<T, U> propagate(Function<T, U> f) {
		AsyncContext propagatedContext = AsyncContext.getCurrentExecutionContext();
		return (t) -> {
			AsyncContext oldContext = AsyncContext.resumeExecutionContext(propagatedContext);
			try {
				return f.apply(t);
			} finally {
				AsyncContext.pauseExecutionContext(propagatedContext, oldContext);
			}
		};
	}

	public static <T, U> AsyncFunction<T, U> propagate(AsyncFunction<T, U> f) {
		AsyncContext propagatedContext = AsyncContext.getCurrentExecutionContext();
		return (t) -> {
			AsyncContext oldContext = AsyncContext.resumeExecutionContext(propagatedContext);
			try {
				return f.apply(t);
			} finally {
				AsyncContext.pauseExecutionContext(propagatedContext, oldContext);
			}
		};
	}

	public static <T> Supplier<T> propagate(Supplier<T> f) {
		AsyncContext propagatedContext = AsyncContext.getCurrentExecutionContext();
		return () -> {
			AsyncContext oldContext = AsyncContext.resumeExecutionContext(propagatedContext);
			try {
				return f.get();
			} finally {
				AsyncContext.pauseExecutionContext(propagatedContext, oldContext);
			}
		};
	}

	public static <T> AsyncSupplier<T> propagate(AsyncSupplier<T> f) {
		AsyncContext propagatedContext = AsyncContext.getCurrentExecutionContext();
		return () -> {
			AsyncContext oldContext = AsyncContext.resumeExecutionContext(propagatedContext);
			try {
				return f.call();
			} finally {
				AsyncContext.pauseExecutionContext(propagatedContext, oldContext);
			}
		};
	}

	public static <T> Consumer<T> propagate(Consumer<T> f) {
		AsyncContext propagatedContext = AsyncContext.getCurrentExecutionContext();
		return (t) -> {
			AsyncContext oldContext = AsyncContext.resumeExecutionContext(propagatedContext);
			try {
				f.accept(t);
			} finally {
				AsyncContext.pauseExecutionContext(propagatedContext, oldContext);
			}
		};
	}

	public static Runnable propagate(Runnable f) {
		AsyncContext propagatedContext = AsyncContext.getCurrentExecutionContext();
		return () -> {
			AsyncContext oldContext = AsyncContext.resumeExecutionContext(propagatedContext);
			try {
				f.run();
			} finally {
				AsyncContext.pauseExecutionContext(propagatedContext, oldContext);
			}
		};
	}
}
