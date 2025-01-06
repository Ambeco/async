package com.mpd.concurrent.futures.atomic;

import androidx.annotation.CallSuper;

import com.mpd.concurrent.AsyncBiFunction;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.atomic.AbstractListenerFutures.TwoParentAbstractListenerFuture;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class FutureAsyncBiFunction<I1, I2, O> extends TwoParentAbstractListenerFuture<I1, I2, O> {
	private final Class<? extends AsyncBiFunction> functionClass;
	private volatile @Nullable AsyncBiFunction<I1, I2, O> function;

	FutureAsyncBiFunction(
			Future<? extends I1> parent1,
			Future<? extends I2> parent2,
			@NonNull AsyncBiFunction<I1, I2, O> function,
			@NonNull Executor executor)
	{
		super(parent1, parent2, executor);
		this.function = function;
		this.functionClass = function.getClass();
	}

	@Override protected void execute() {
		AsyncBiFunction<I1, I2, O> function = this.function;
		if (function == null) {
			throw new RunCalledTwiceException();
		}
		setResult(function.apply(getParent1().resultNow(), getParent2().resultNow()));
	}

	@CallSuper @Override protected void afterDone(
			@Nullable O result,
			@Nullable Throwable exception,
			boolean mayInterruptIfRunning,
			FutureListener<? super O> listener)
	{
		super.afterDone(result, exception, mayInterruptIfRunning, listener);
		function = null;
	}

	protected Class<?> sourceClass() {
		return functionClass;
	}

	protected @Nullable String sourceMethodName() {
		return "apply";
	}
}