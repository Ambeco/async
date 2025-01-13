package com.mpd.concurrent.futures.locked;

import androidx.annotation.CallSuper;
import com.mpd.concurrent.AsyncFunction;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.locked.AbstractListenerFutures.SingleParentTransformListenerFuture;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class FutureAsyncFunction<I, O> extends SingleParentTransformListenerFuture<I, O> {
	private @Nullable AsyncFunction<? super I, ? extends O> function;

	public FutureAsyncFunction(
			@NonNull Future<? extends I> parent,
			@NonNull AsyncFunction<? super I, ? extends O> function,
			@NonNull Executor executor)
	{
		super(parent, executor);
		this.function = function;
	}

	@Override protected void execute(I arg) {
		AsyncFunction<? super I, ? extends O> function = this.function;
		if (function == null) {
			throw new RunCalledTwiceException();
		}
		setResult(function.apply(arg));
	}

	@CallSuper @Override protected void onCompletedLocked(@Nullable Throwable e) {
		super.onCompletedLocked(e);
		this.function = null;
	}

	@Override protected @Nullable Object toStringSource() {
		AsyncFunction<? super I, ? extends O> function = this.function;
		if (function == null) {
			return super.toStringSource();
		} else {
			return this.function;
		}
	}
}
