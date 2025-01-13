package com.mpd.concurrent.futures.locked;

import androidx.annotation.CallSuper;
import com.mpd.concurrent.asyncContext.AsyncContext;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.RunnableFuture;
import com.mpd.concurrent.futures.locked.AbstractListenerFutures.SubmittableListenerFuture;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class FutureRunnable<O> extends SubmittableListenerFuture<O> implements RunnableFuture<O> {
	private @Nullable Runnable function;
	private @Nullable O result;

	public FutureRunnable(@Nullable AsyncContext context, @NonNull Runnable function) {
		this(context, null, function, null);
	}

	public FutureRunnable(@Nullable AsyncContext context, @NonNull Runnable function, @Nullable O result) {
		super(context);
		this.function = function;
		this.result = result;
	}

	public FutureRunnable(
			@Nullable AsyncContext context, @Nullable Future<?> parent, @NonNull Runnable function, Executor executor)
	{
		this(context, parent, function, null, executor);
	}

	public FutureRunnable(
			@Nullable AsyncContext context,
			@Nullable Future<?> parent,
			@NonNull Runnable function,
			@Nullable O result,
			Executor executor)
	{
		super(context, parent, executor);
		this.function = function;
		this.result = result;
	}

	public FutureRunnable(
			@Nullable AsyncContext context,
			@Nullable Future<?> parent,
			@NonNull Runnable function,
			long delay,
			TimeUnit delayUnit,
			Executor executor)
	{
		super(context, parent, delay, delayUnit, executor);
		this.function = function;
		this.result = null;
	}

	public FutureRunnable(
			@Nullable AsyncContext context,
			@Nullable Future<?> parent,
			@NonNull Runnable function,
			@Nullable O result,
			long delay,
			TimeUnit delayUnit,
			Executor executor)
	{
		super(context, parent, delay, delayUnit, executor);
		this.function = function;
		this.result = result;
	}

	@Override public void execute() throws Exception {
		Runnable function = this.function;
		if (function == null) {
			throw new RunCalledTwiceException();
		}
		function.run();
		setResult(result);
	}

	@CallSuper @Override protected void onCompletedLocked(@Nullable Throwable e) {
		super.onCompletedLocked(e);
		this.function = null;
		this.result = null;
	}

	@Override protected @Nullable Object toStringSource() {
		Runnable function = this.function;
		if (function == null) {
			return super.toStringSource();
		} else {
			return this.function;
		}
	}
}
