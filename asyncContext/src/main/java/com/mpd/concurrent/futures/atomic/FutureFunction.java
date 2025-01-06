package com.mpd.concurrent.futures.atomic;

import static com.google.common.base.Preconditions.checkNotNull;

import androidx.annotation.CallSuper;

import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.atomic.AbstractListenerFutures.SingleParentTransformListenerFuture;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.function.Function;

public class FutureFunction<I, O> extends SingleParentTransformListenerFuture<I, O> {
	private final Class<? extends Function> functionClass;
	private volatile @Nullable Function<? super I, ? extends O> function;

	public FutureFunction(
			@NonNull Future<? extends I> parent,
			@NonNull Function<? super I, ? extends O> function,
			@NonNull Executor executor)
	{
		super(parent, executor);
		this.function = function;
		this.functionClass = function.getClass();
	}

	@Override protected void execute() {
		setResult(checkNotNull(function).apply(getParent().resultNow()));
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
