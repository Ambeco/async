package com.mpd.concurrent.futures.locked;

import androidx.annotation.CallSuper;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.locked.AbstractListenerFutures.SingleParentCatchingAbstractListenerFuture;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class FutureCatchingFunction<E extends Throwable, O> extends SingleParentCatchingAbstractListenerFuture<E, O> {
	private @Nullable Function<? super E, ? extends O> function;

	public FutureCatchingFunction(
			@NonNull Future<? extends O> parent,
			@NonNull Function<? super E, ? extends O> function,
			Class<E> clazz,
			@NonNull Executor executor)
	{
		super(clazz, parent, executor);
		this.function = function;
	}

	@Override protected void execute(E exception) {
		Function<? super E, ? extends O> function = this.function;
		if (function == null) {
			throw new RunCalledTwiceException();
		}
		setResult(function.apply(exception));
	}

	@CallSuper @Override protected void onCompletedLocked(@Nullable Throwable e) {
		super.onCompletedLocked(e);
		this.function = null;
	}

	@Override protected @Nullable Object toStringSource() {
		Function<? super E, ? extends O> function = this.function;
		if (function == null) {
			return super.toStringSource();
		} else {
			return this.function;
		}
	}
}
