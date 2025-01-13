package com.mpd.concurrent.futures.atomic;

import androidx.annotation.CallSuper;
import com.mpd.concurrent.AsyncFunction;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.atomic.AbstractListenerFutures.SingleParentTransformListenerFuture;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class FutureAsyncFunction<I, O> extends SingleParentTransformListenerFuture<I, O> {
	// TODO function to use stub instead of Nullable?
	private final Class<? extends AsyncFunction> functionClass;
	private volatile @Nullable AsyncFunction<? super I, ? extends O> function;

	public FutureAsyncFunction(
			@NonNull Future<? extends I> parent,
			@NonNull AsyncFunction<? super I, ? extends O> function,
			@NonNull Executor executor)
	{
		super(parent, executor);
		this.function = function;
		functionClass = function.getClass();
	}

	@Override protected void execute() {
		AsyncFunction<? super I, ? extends O> function = this.function;
		if (function == null) {
			throw new RunCalledTwiceException();
		}
		setResult(function.apply(getParent().resultNow()));
	}

	@CallSuper @Override protected void afterDone(
			@Nullable O result,
			@Nullable Throwable exception,
			boolean mayInterruptIfRunning,
			FutureListener<? super O> listener)
	{
		super.afterDone(result, exception, mayInterruptIfRunning, listener);
		this.function = null;
	}

	protected Class<?> sourceClass() {
		return functionClass;
	}

	protected @Nullable String sourceMethodName() {
		return "apply";
	}
}
