package com.mpd.concurrent.futures.impl;

import com.mpd.concurrent.AsyncBiFunction;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.impl.AbstractListenerFutures.TwoParentAbstractListenerFuture;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class FutureAsyncBiFunction<I1, I2, O> extends TwoParentAbstractListenerFuture<I1, I2, O> {
	private final @Nullable AsyncBiFunction<I1, I2, O> function;

	FutureAsyncBiFunction(
			Future<? extends I1> parent1,
			Future<? extends I2> parent2,
			@NonNull AsyncBiFunction<I1, I2, O> function,
			@NonNull Executor executor)
	{
		super(parent1, parent2, executor);
		this.function = function;
	}

	@Override protected void execute(I1 arg1, I2 arg2) {
		AsyncBiFunction<I1, I2, O> function = this.function;
		if (function == null) {
			throw new RunCalledTwiceException();
		}
		setResult(function.apply(arg1, arg2));
	}

	@Override protected @Nullable String toStringSource() {
		AsyncBiFunction<I1, I2, O> function = this.function;
		if (function == null) {
			return super.toStringSource();
		} else {
			return this.function.toString();
		}
	}
}