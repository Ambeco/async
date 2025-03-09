package com.mpd.concurrent.futures.atomic;

import android.os.Build.VERSION_CODES;
import androidx.annotation.CallSuper;
import androidx.annotation.RequiresApi;
import com.mpd.concurrent.asyncContext.AsyncContextScope.DeferredContextScope;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.RunnableFuture;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class FutureRunnable<O> extends AbstractSubmittableFuture<O> implements RunnableFuture<O> {
	// TODO function to use stub instead of Nullable?
	private final Class<? extends Runnable> functionClass;
	private volatile @Nullable O futureResult;
	private volatile @Nullable Runnable function;

	public FutureRunnable(@Nullable DeferredContextScope scope, @NonNull Runnable function) {
		this(scope, function, null);
	}

	public FutureRunnable(@Nullable DeferredContextScope scope, @NonNull Runnable function, @Nullable O result) {
		super(scope);
		this.function = function;
		this.futureResult = result;
		functionClass = function.getClass();
	}

	public FutureRunnable(
			@Nullable DeferredContextScope scope, @NonNull Runnable function, long delay, TimeUnit delayUnit)
	{
		this(scope, function, null, delay, delayUnit);
	}

	@RequiresApi(api = VERSION_CODES.O) public FutureRunnable(
			@Nullable DeferredContextScope scope, @NonNull Runnable function, @Nullable O result, Instant time)
	{
		super(scope, time);
		this.function = function;
		this.futureResult = result;
		functionClass = function.getClass();
	}

	public FutureRunnable(
			@Nullable DeferredContextScope scope,
			@NonNull Runnable function,
			@Nullable O result,
			long delay,
			TimeUnit delayUnit)
	{
		super(scope, delay, delayUnit);
		this.function = function;
		this.futureResult = result;
		functionClass = function.getClass();
	}

	@Override public void execute() throws Exception {
		Runnable function = this.function;
		if (function == null) {
			throw new RunCalledTwiceException(this + " #run appears to have been called twice");
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
