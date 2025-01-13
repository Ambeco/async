package com.mpd.concurrent.futures.atomic;

import androidx.annotation.CallSuper;
import com.mpd.concurrent.asyncContext.AsyncContext;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.RunnableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class FutureCallable<O> extends AbstractSubmittableFuture<O> implements RunnableFuture<O> {
	// TODO function to use stub instead of Nullable?
	private final Class<? extends Callable> functionClass;
	private volatile @Nullable Callable<? extends O> function;

	public FutureCallable(@Nullable AsyncContext context, @NonNull Callable<? extends O> function) {
		super(context);
		this.function = function;
		functionClass = function.getClass();
	}

	public FutureCallable(
			@Nullable AsyncContext context, @NonNull Callable<? extends O> function, long delay, TimeUnit delayUnit)
	{
		super(context, delay, delayUnit);
		this.function = function;
		functionClass = function.getClass();
	}

	@Override public void execute() throws Exception {
		Callable<? extends O> function = this.function;
		if (function == null) {
			throw new RunCalledTwiceException();
		}
		setResult(function.call());
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
		return "call";
	}
}
