package com.mpd.concurrent.futures.locked;

import static com.google.common.base.Preconditions.checkNotNull;

import androidx.annotation.CallSuper;

import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.locked.AbstractListenerFutures.SingleParentTransformListenerFuture;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.function.Function;

public class FutureFunction<I, O> extends SingleParentTransformListenerFuture<I, O> {
	private @Nullable Function<? super I, ? extends O> function;

	public FutureFunction(
			@NonNull Future<? extends I> parent,
			@NonNull Function<? super I, ? extends O> function,
			@NonNull Executor executor)
	{
		super(parent, executor);
		this.function = function;
	}

	@Override protected void execute(I arg) {
		setResult(checkNotNull(function).apply(arg));
	}

	@CallSuper @Override protected void onCompletedLocked(@Nullable Throwable e) {
		super.onCompletedLocked(e);
		this.function = null;
	}
}
