package com.mpd.concurrent.futures.atomic;

import android.os.Build.VERSION_CODES;
import androidx.annotation.CallSuper;
import androidx.annotation.RequiresApi;
import com.mpd.concurrent.asyncContext.AsyncContext;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.RunnableFuture;
import java.time.Instant;
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

	@RequiresApi(api = VERSION_CODES.O) public FutureCallable(
			@Nullable AsyncContext context, @NonNull Callable<? extends O> function, Instant delayUnit)
	{
		super(context, delayUnit);
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
			throw new RunCalledTwiceException(this + " #run appears to have been called twice");
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
