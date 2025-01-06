package com.mpd.concurrent.futures.atomic;

import static com.google.common.base.Preconditions.checkNotNull;

import androidx.annotation.CallSuper;

import com.google.common.collect.ImmutableList;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.executors.MoreExecutors;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.BitSet;
import java.util.Collection;
import java.util.concurrent.CancellationException;

public abstract class AbstractFutureSuccessCombiner<I, O> extends AbstractListenerFuture<O> {
	private final BitSet completed;
	private @Nullable ImmutableList<Future<? extends I>> parents;

	protected AbstractFutureSuccessCombiner(Future<? extends I>[] futures, Executor executor)
	{
		this(ImmutableList.copyOf(futures), executor);
	}

	protected AbstractFutureSuccessCombiner(
			@NonNull Collection<? extends Future<? extends I>> futures, Executor executor)
	{
		super(null, executor);
		if (futures instanceof ImmutableList) {
			//noinspection rawtypes
			this.parents = (ImmutableList) futures;
		} else {
			this.parents = ImmutableList.copyOf(futures);
		}
		completed = new BitSet(futures.size());
	}

	protected ImmutableList<Future<? extends I>> getParents() {
		return checkNotNull(parents);
	}

	@Override protected boolean shouldQueueExecutionAfterParentComplete(
			Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
	{
		ImmutableList<Future<? extends I>> parents = checkNotNull(this.parents);
		int idx = parents.indexOf(parent);
		if (idx == -1) {
			throw new WrongParentFutureException();
		}
		completed.set(idx);
		if (exception != null) {
			setException(exception, mayInterruptIfRunning);
			return false;
		} else {
			return completed.cardinality() == completed.size();
		}
	}

	@CallSuper @Override protected void afterDone(
			@Nullable O result,
			@Nullable Throwable exception,
			boolean mayInterruptIfRunning,
			FutureListener<? super O> listener)
	{
		super.afterDone(result, exception, mayInterruptIfRunning, listener);
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

	@CallSuper protected void toStringAppendState(
			@Nullable O result, @Nullable Throwable exception, @Nullable Future<? extends O> setAsync, StringBuilder sb)
	{
		super.toStringAppendState(result, exception, setAsync, sb);
		sb.append("completed=").append(completed.cardinality()).append("/").append(completed.size());
	}

	@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
		ImmutableList<Future<? extends I>> parents = this.parents;
		super.addPendingString(sb, maxDepth);
		if (parents != null) {
			for (Future<?> parent : parents) {
				if (!parent.isDone()) {
					parent.addPendingString(sb, maxDepth - 1);
				}
			}
		}
	}

	public static class VoidFutureSuccessCombiner<I> extends AbstractFutureSuccessCombiner<I, Void> {

		public VoidFutureSuccessCombiner(Future<? extends I>[] futures)
		{
			super(futures, MoreExecutors.directExecutor());
		}

		public VoidFutureSuccessCombiner(Collection<? extends Future<? extends I>> futures)
		{
			super(futures, MoreExecutors.directExecutor());
		}

		@Override protected void execute() throws Exception {
			setResult((Void) null);
		}
	}
}
