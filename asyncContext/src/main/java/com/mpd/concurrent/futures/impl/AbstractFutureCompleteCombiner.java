package com.mpd.concurrent.futures.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import androidx.annotation.CallSuper;

import com.google.common.collect.ImmutableList;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.Futures;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.BitSet;
import java.util.Collection;
import java.util.concurrent.CancellationException;

public abstract class AbstractFutureCompleteCombiner<I, O> extends AbstractListenerFuture<O> {
	private @Nullable ImmutableList<Future<? extends I>> parents;
	private final BitSet completed;

	protected AbstractFutureCompleteCombiner(Future<? extends I>[] futures)
	{
		this(futures, null);
	}

	protected AbstractFutureCompleteCombiner(
			Collection<? extends Future<? extends I>> futures)
	{
		this(futures, null);
	}

	protected AbstractFutureCompleteCombiner(
			Future<? extends I>[] futures, @Nullable Executor executor)
	{
		this(ImmutableList.copyOf(futures), executor);
	}

	protected AbstractFutureCompleteCombiner(
			@NonNull Collection<? extends Future<? extends I>> futures, @Nullable Executor executor)
	{
		super(null, executor, ListenerFutureState.STATE_LISTENING);
		if (futures instanceof ImmutableList) {
			this.parents = (ImmutableList) futures;
		} else {
			this.parents = ImmutableList.copyOf(futures);
		}
		completed = new BitSet(futures.size());
	}

	protected ImmutableList<Future<? extends I>> getParentsLocked() {
		return checkNotNull(parents);
	}

	protected synchronized ImmutableList<Future<? extends I>> getParents() {
		return checkNotNull(parents);
	}

	@CallSuper @Override protected void onCompletedLocked(@Nullable Throwable e) {
		super.onCompletedLocked(e);
		parents = null;
	}

	@CallSuper @Override protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
		ImmutableList<Future<? extends I>> parents = this.parents;
		super.onCancelled(exception, mayInterruptIfRunning);
		if (parents != null) {
			for (Future<?> parent : parents) {
				parent.cancel(exception, mayInterruptIfRunning);
			}
		}
	}

	@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
		sb.append("\n  at ");
		toString(sb);
		super.addPendingString(sb, maxDepth - 1);
	}

	@CallSuper protected void toStringAppendState(
			boolean isDone,
			@Nullable O result,
			@Nullable Throwable exception,
			@Nullable Future<? extends O> setAsync,
			StringBuilder sb)
	{
		super.toStringAppendState(isDone, result, exception, setAsync, sb);
		sb.append("completed=").append(completed.cardinality()).append("/").append(completed.size());
	}

	@Override protected boolean shouldQueueExecutionAfterParentComplete(
			Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
	{
		int idx = checkNotNull(parents).indexOf(parent);
		if (idx == -1) {
			throw new WrongParentFutureException();
		}
		completed.set(idx);
		if (exception instanceof CancellationException) {
			setException(exception, mayInterruptIfRunning);
			return false;
		}
		if (completed.cardinality() < completed.size()) {
			return false;
		}
		Throwable throwable = Futures.getFutureExceptions(parents);
		if (throwable != null) {
			setException(throwable, mayInterruptIfRunning);
			return false;
		} else {
			return true;
		}
	}

	public static class VoidFutureCompleteCombiner<I> extends AbstractFutureCompleteCombiner<I, Void> {

		public VoidFutureCompleteCombiner(Future<? extends I>[] futures)
		{
			super(futures, null);
		}

		public VoidFutureCompleteCombiner(Collection<? extends Future<? extends I>> futures)
		{
			super(futures, null);
		}

		public VoidFutureCompleteCombiner(Future<? extends I>[] futures, @Nullable Executor executor)
		{
			super(ImmutableList.copyOf(futures), executor);
		}

		public VoidFutureCompleteCombiner(
				@NonNull Collection<? extends Future<? extends I>> futures, @Nullable Executor executor)
		{
			super(futures, executor);
		}

		@Override protected void execute() throws Exception {
			setResult((Void) null);
		}
	}
}
