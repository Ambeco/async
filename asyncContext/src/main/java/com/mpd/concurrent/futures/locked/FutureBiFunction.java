package com.mpd.concurrent.futures.locked;

import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.locked.AbstractListenerFutures.TwoParentAbstractListenerFuture;
import java.util.function.BiFunction;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

abstract class FutureBiFunction<I1, I2, O> extends TwoParentAbstractListenerFuture<I1, I2, O> {
	private final @Nullable BiFunction<I1, I2, O> function;

	FutureBiFunction(
			Future<? extends I1> parent1,
			Future<? extends I2> parent2,
			@NonNull BiFunction<I1, I2, O> function,
			@NonNull Executor executor)
	{
		super(parent1, parent2, executor);
		this.function = function;
	}

	@Override protected void execute(I1 arg1, I2 arg2) {
		BiFunction<I1, I2, O> function = this.function;
		if (function == null) {
			throw new RunCalledTwiceException();
		}
		setResult(function.apply(arg1, arg2));
	}

	@Override protected @Nullable Object toStringSource() {
		BiFunction<I1, I2, O> function = this.function;
		if (function == null) {
			return super.toStringSource();
		} else {
			return this.function;
		}
	}
}
