package com.mpd.concurrent.futures.atomic;

import androidx.annotation.CallSuper;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.atomic.AbstractListenerFutures.SingleParentCatchingAbstractListenerFuture;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class FutureCatchingFunction<E extends Throwable, O> extends SingleParentCatchingAbstractListenerFuture<E, O> {
	// TODO function to use stub instead of Nullable?
	private final Class<? extends Function> functionClass;
	private volatile @Nullable Function<? super E, ? extends O> function;

	public FutureCatchingFunction(
			@NonNull Future<? extends O> parent, @NonNull Function<? super E, ? extends O> function, Class<E> exceptionClass,
			@NonNull Executor executor)
	{
		super(exceptionClass, parent, executor);
		this.function = function;
		functionClass = function.getClass();
	}

	@Override protected void execute() {
		Function<? super E, ? extends O> function = this.function;
		Future<? extends O> parent = getParent();
		if (parent == null || function == null) {
			throw new RunCalledTwiceException(this + " #run appears to have been called twice");
		}
		if (!parent.isDone()) {
			setException(new ParentNotCompleteException(this
					+ " running, but parent "
					+ parent
					+ " does not appear to be complete. Failing this"));
		}
		Throwable exception = parent.exceptionNow();
		if (exception == null) {
			setResult(parent.resultNow());
		} else if (getExceptionClass().isInstance(exception)) {
			setResult(function.apply(getExceptionClass().cast(exception)));
		} else {
			setException(exception);
		}
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
		return "apply";
	}
}
