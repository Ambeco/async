package com.mpd.concurrent.futures.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import androidx.annotation.CallSuper;

import com.google.common.collect.ImmutableList;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.BitSet;
import java.util.Collection;
import java.util.concurrent.CancellationException;

public abstract class AbstractFutureSuccessCombiner<I, O> extends AbstractListenerFuture<O> {
	private @Nullable ImmutableList<Future<? extends I>> parents;
	private final BitSet completed;

	protected AbstractFutureSuccessCombiner(Future<? extends I>[] futures)
	{
		this(ImmutableList.copyOf(futures), null);
	}

	protected AbstractFutureSuccessCombiner(Collection<? extends Future<? extends I>> futures)
	{
		this(futures, null);
	}

	protected AbstractFutureSuccessCombiner(Future<? extends I>[] futures, @Nullable Executor executor)
	{
		this(ImmutableList.copyOf(futures), executor);
	}

	protected AbstractFutureSuccessCombiner(
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
		checkNotNull(completed).set(idx);
		if (exception != null) {
			setException(exception, mayInterruptIfRunning);
			return false;
		} else {
			return completed.cardinality() == completed.size();
		}
	}

	public static class VoidFutureSuccessCombiner<I> extends AbstractFutureSuccessCombiner<I, Void> {

		public VoidFutureSuccessCombiner(Future<? extends I>[] futures)
		{
			super(futures, null);
		}

		public VoidFutureSuccessCombiner(Collection<? extends Future<? extends I>> futures)
		{
			super(futures, null);
		}

		public VoidFutureSuccessCombiner(Future<? extends I>[] futures, @Nullable Executor executor)
		{
			super(ImmutableList.copyOf(futures), executor);
		}

		public VoidFutureSuccessCombiner(
				@NonNull Collection<? extends Future<? extends I>> futures, @Nullable Executor executor)
		{
			super(futures, executor);
		}

		@Override protected void execute() throws Exception {
			setResult((Void) null);
		}
	}
}
