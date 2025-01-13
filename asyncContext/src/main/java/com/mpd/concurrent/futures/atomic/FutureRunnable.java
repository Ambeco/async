package com.mpd.concurrent.futures.atomic;

import androidx.annotation.CallSuper;
import com.mpd.concurrent.asyncContext.AsyncContext;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.RunnableFuture;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class FutureRunnable<O> extends AbstractSubmittableFuture<O> implements RunnableFuture<O> {
	// TODO function to use stub instead of Nullable?
	private final Class<? extends Runnable> functionClass;
	private volatile @Nullable O futureResult;
	private volatile @Nullable Runnable function;

	public FutureRunnable(@Nullable AsyncContext context, @NonNull Runnable function) {
		this(context, function, null);
	}

	public FutureRunnable(@Nullable AsyncContext context, @NonNull Runnable function, @Nullable O result) {
		super(context);
		this.function = function;
		this.futureResult = result;
		functionClass = function.getClass();
	}

	public FutureRunnable(
			@Nullable AsyncContext context, @NonNull Runnable function, long delay, TimeUnit delayUnit)
	{
		this(context, function, null, delay, delayUnit);
	}

	public FutureRunnable(
			@Nullable AsyncContext context, @NonNull Runnable function, @Nullable O result, long delay, TimeUnit delayUnit)
	{
		super(context, delay, delayUnit);
		this.function = function;
		this.futureResult = result;
		functionClass = function.getClass();
	}

	@Override public void execute() throws Exception {
		Runnable function = this.function;
		if (function == null) {
			throw new RunCalledTwiceException();
		}
		function.run();
		setResult(futureResult);
	}

	@CallSuper @Override protected void afterDone(
			@Nullable O result,
			@Nullable Throwable exception,
			boolean mayInterruptIfRunning,
			FutureListener<? super O> listener)
	{
		super.afterDone(result, exception, mayInterruptIfRunning, listener);
		this.function = null;
		this.futureResult = null;
	}

	protected Class<?> sourceClass() {
		return functionClass;
	}

	protected @Nullable String sourceMethodName() {
		return "run";
	}
}
