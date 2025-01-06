package com.mpd.concurrent.futures.atomic;

import androidx.annotation.CallSuper;

import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.atomic.AbstractListenerFutures.SingleParentCatchingAbstractListenerFuture;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.function.Function;

public class FutureCatchingFunction<E extends Throwable, O> extends SingleParentCatchingAbstractListenerFuture<E, O> {
	private final Class<? extends Function> functionClass;
	private volatile @Nullable Function<? super E, ? extends O> function;

	public FutureCatchingFunction(
			@NonNull Future<? extends O> parent,
			@NonNull Function<? super E, ? extends O> function, Class<E> exceptionClass,
			@NonNull Executor executor)
	{
		super(exceptionClass, parent, executor);
		this.function = function;
		functionClass = function.getClass();
	}

	@Override protected void execute() {
		Function<? super E, ? extends O> function = this.function;
		if (function == null) {
			throw new RunCalledTwiceException();
		}
		Throwable exception = getParent().exceptionNow();
		if (getExceptionClass().isInstance(exception)) {
			setResult(function.apply(getExceptionClass().cast(exception)));
		} else {
			setResult(getParent());
		}
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
