package com.mpd.concurrent.futures.atomic;

import android.os.Build.VERSION_CODES;
import androidx.annotation.CallSuper;
import androidx.annotation.RequiresApi;
import com.mpd.concurrent.AsyncCallable;
import com.mpd.concurrent.asyncContext.AsyncContext;
import com.mpd.concurrent.futures.FutureListener;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class FutureAsyncCallable<O> extends AbstractSubmittableFuture<O> {
	// TODO function to use stub instead of Nullable?
	private final Class<? extends AsyncCallable> functionClass;
	private volatile @Nullable AsyncCallable<? extends O> function;

	public FutureAsyncCallable(@Nullable AsyncContext context, @NonNull AsyncCallable<? extends O> function) {
		super(context);
		this.function = function;
		functionClass = function.getClass();
	}

	@RequiresApi(api = VERSION_CODES.O) public FutureAsyncCallable(
			@Nullable AsyncContext context, @NonNull AsyncCallable<? extends O> function, Instant time)
	{
		super(context, time);
		this.function = function;
		functionClass = function.getClass();
	}

	public FutureAsyncCallable(
			@Nullable AsyncContext context, @NonNull AsyncCallable<? extends O> function, long delay, TimeUnit delayUnit)
	{
		super(context, delay, delayUnit);
		this.function = function;
		functionClass = function.getClass();
	}

	@Override protected void execute() throws Exception {
		AsyncCallable<? extends O> function = this.function;
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
