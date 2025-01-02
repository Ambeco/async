package com.mpd.concurrent.futures.atomic;

import com.mpd.concurrent.AsyncFunction;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.impl.AbstractListenerFutures.SingleParentCatchingAbstractListenerFuture;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class FutureCatchingAsyncFunction<E extends Throwable, O>
		extends SingleParentCatchingAbstractListenerFuture<E, O>
{
	private @Nullable AsyncFunction<? super E, ? extends O> function;

	public FutureCatchingAsyncFunction(
			Class<E> clazz,
			@NonNull Future<? extends O> parent,
			@NonNull AsyncFunction<? super E, ? extends O> function,
			@NonNull Executor executor)
	{
		super(clazz, parent, executor);
		this.function = function;
	}

	@Override protected void execute(E exception) {
		AsyncFunction<? super E, ? extends O> function = this.function;
		if (function == null) {
			throw new RunCalledTwiceException();
		}
		setResult(function.apply(exception));
	}

	@Override protected void onCompletedLocked(@Nullable Throwable e) {
		super.onCompletedLocked(e);
		this.function = null;
	}

	@Override protected @Nullable Object toStringSource() {
		AsyncFunction<? super E, ? extends O> function = this.function;
		if (function == null) {
			return super.toStringSource();
		} else {
			return this.function;
		}
	}
}
