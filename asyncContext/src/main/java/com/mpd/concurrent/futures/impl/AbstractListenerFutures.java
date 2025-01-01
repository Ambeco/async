package com.mpd.concurrent.futures.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.mpd.concurrent.executors.MoreExecutors.directExecutor;

import androidx.annotation.CallSuper;

import com.mpd.concurrent.executors.AsyncContext;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

public final class AbstractListenerFutures {
	private AbstractListenerFutures() {}

	public abstract static class SingleParentImmediateListenerFuture<I, O> extends AbstractListenerFuture<O> {
		private @Nullable Future<? extends I> parent;

		protected SingleParentImmediateListenerFuture(@NonNull Future<? extends I> parent) {
			super(null, directExecutor(), ListenerFutureState.STATE_LISTENING);
			this.parent = parent;
			parent.setListener(this);
		}

		protected synchronized Future<? extends I> getParent() {
			return checkNotNull(parent);
		}

		protected Future<? extends I> getParentLocked() {
			return checkNotNull(parent);
		}

		protected abstract void execute(Future<? extends I> parent);

		@CallSuper @Override protected void onCompletedLocked(@Nullable Throwable e) {
			super.onCompletedLocked(e);
			parent = null;
		}

		@CallSuper @Override protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
			Future<?> parent;
			synchronized (this) {
				parent = this.parent;
			}
			super.onCancelled(exception, mayInterruptIfRunning);
			if (parent != null) {
				parent.cancel(exception, mayInterruptIfRunning);
			}
		}

		@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
			super.addPendingString(sb, maxDepth);
			Future<? extends I> parent = this.parent;
			if (parent != null) {
				parent.addPendingString(sb, maxDepth - 1);
			}
		}

		@Override protected boolean shouldQueueExecutionAfterParentComplete(
				Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
		{
			if (parent != this.parent) {
				onCompletingLocked(FAILED_RESULT, new WrongParentFutureException(exception), NO_INTERRUPT);
				return false;
			} else {
				return true;
			}
		}

		@Override protected final void execute() {
			execute(checkNotNull(parent));
		}
	}

	public abstract static class SingleParentTransformListenerFuture<I, O> extends AbstractListenerFuture<O> {
		private @Nullable Future<? extends I> parent;

		protected SingleParentTransformListenerFuture(@NonNull Future<? extends I> parent, @Nullable Executor executor) {
			super(null, executor, ListenerFutureState.STATE_LISTENING);
			this.parent = parent;
			parent.setListener(this);
		}

		protected synchronized Future<? extends I> getParent() {
			return checkNotNull(parent);
		}

		protected Future<? extends I> getParentLocked() {
			return checkNotNull(parent);
		}

		protected abstract void execute(I arg);

		@CallSuper @Override protected void onCompletedLocked(@Nullable Throwable e) {
			super.onCompletedLocked(e);
			parent = null;
		}

		@CallSuper @Override protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
			Future<?> parent;
			synchronized (this) {
				parent = this.parent;
			}
			super.onCancelled(exception, mayInterruptIfRunning);
			if (parent != null) {
				parent.cancel(exception, mayInterruptIfRunning);
			}
		}

		@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
			super.addPendingString(sb, maxDepth);
			Future<? extends I> parent = this.parent;
			if (parent != null) {
				parent.addPendingString(sb, maxDepth - 1);
			}
		}

		@Override protected boolean shouldQueueExecutionAfterParentComplete(
				Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
		{
			if (parent != this.parent) {
				onCompletingLocked(FAILED_RESULT, new WrongParentFutureException(exception), NO_INTERRUPT);
				return false;
			} else if (exception != null) {
				onCompletingLocked(FAILED_RESULT, exception, mayInterruptIfRunning);
				return false;
			} else {
				return true;
			}
		}

		@Override protected final void execute() {
			execute(checkNotNull(parent).resultNow());
		}
	}

	public abstract static class SingleParentCatchingAbstractListenerFuture<E extends Throwable, O>
			extends AbstractListenerFuture<O>
	{
		private final Class<E> throwableClass;
		private @Nullable Future<? extends O> parent;

		protected SingleParentCatchingAbstractListenerFuture(
				Class<E> throwableClass, @NonNull Future<? extends O> parent, @Nullable Executor executor)
		{
			super(null, executor, ListenerFutureState.STATE_LISTENING);
			this.parent = parent;
			this.throwableClass = throwableClass;
			parent.setListener(this);
		}

		protected synchronized Future<? extends O> getParent() {
			return checkNotNull(parent);
		}

		protected Future<? extends O> getParentLocked() {
			return checkNotNull(parent);
		}

		protected abstract void execute(E exception);

		@CallSuper @Override protected void onCompletedLocked(@Nullable Throwable e) {
			super.onCompletedLocked(e);
			parent = null;
		}

		@CallSuper @Override protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
			Future<?> parent;
			synchronized (this) {
				parent = this.parent;
			}
			super.onCancelled(exception, mayInterruptIfRunning);
			if (parent != null) {
				parent.cancel(exception, mayInterruptIfRunning);
			}
		}

		@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
			super.addPendingString(sb, maxDepth);
			Future<? extends O> parent = this.parent;
			if (parent != null) {
				parent.addPendingString(sb, maxDepth - 1);
			}
		}

		@Override protected boolean shouldQueueExecutionAfterParentComplete(
				Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
		{
			if (parent != this.parent) {
				onCompletingLocked(FAILED_RESULT, new WrongParentFutureException(exception), NO_INTERRUPT);
				return false;
			} else if (throwableClass.isInstance(exception)) {
				return true;
			} else {
				onCompletingLocked((O) result, exception, mayInterruptIfRunning);
				return false;
			}
		}

		@Override protected final void execute() {
			execute(throwableClass.cast(checkNotNull(parent).exceptionNow()));
		}
	}

	public abstract static class TwoParentAbstractListenerFuture<I1, I2, O> extends AbstractListenerFuture<O> {
		protected @Nullable Future<? extends I1> parent1;
		protected @Nullable Future<? extends I2> parent2;
		protected int completeBitfield = 0;

		protected TwoParentAbstractListenerFuture(
				@NonNull Future<? extends I1> parent1, @NonNull Future<? extends I2> parent2, @Nullable Executor executor)
		{
			super(null, executor, ListenerFutureState.STATE_LISTENING);
			this.parent1 = parent1;
			this.parent2 = parent2;
			parent1.setListener(this);
			parent2.setListener(this);
		}

		protected synchronized Future<? extends I1> getParent1() {
			return checkNotNull(parent1);
		}

		protected Future<? extends I1> getParent1Locked() {
			return checkNotNull(parent1);
		}

		protected synchronized Future<? extends I2> getParent2() {
			return checkNotNull(parent2);
		}

		protected Future<? extends I2> getParent2Locked() {
			return checkNotNull(parent2);
		}

		protected abstract void execute(I1 arg1, I2 arg2);

		@CallSuper @Override protected void onCompletedLocked(@Nullable Throwable e) {
			super.onCompletedLocked(e);
			parent1 = null;
			parent2 = null;
		}

		@CallSuper @Override protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
			Future<?> parent1;
			Future<?> parent2;
			synchronized (this) {
				parent1 = this.parent1;
				parent2 = this.parent2;
			}
			super.onCancelled(exception, mayInterruptIfRunning);
			if (parent1 != null) {
				parent1.cancel(exception, mayInterruptIfRunning);
			}
			if (parent2 != null) {
				parent2.cancel(exception, mayInterruptIfRunning);
			}
		}

		@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
			super.addPendingString(sb, maxDepth);
			boolean didAppend = false;
			Future<? extends I1> parent1 = this.parent1;
			if (parent1 != null) {
				parent1.addPendingString(sb, maxDepth - 1);
				didAppend = true;
			}
			Future<? extends I2> parent2 = this.parent2;
			if (parent2 != null) {
				if (didAppend) {
					sb.append("\nand also:\n");
				}
				parent2.addPendingString(sb, maxDepth - 1);
			}
		}

		@Override protected boolean shouldQueueExecutionAfterParentComplete(
				Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
		{
			if (parent == parent1) {
				completeBitfield |= 1;
				if (exception != null) {
					onCompletingLocked(FAILED_RESULT, exception, mayInterruptIfRunning);
					return false;
				} else {
					return completeBitfield == 3;
				}
			} else if (parent == parent2) {
				completeBitfield |= 2;
				if (exception != null) {
					onCompletingLocked(FAILED_RESULT, exception, mayInterruptIfRunning);
					return false;
				} else {
					return completeBitfield == 3;
				}
			} else {
				onCompletingLocked(FAILED_RESULT, new WrongParentFutureException(exception), NO_INTERRUPT);
				return false;
			}
		}

		@Override protected final void execute() {
			execute(checkNotNull(parent1).resultNow(), checkNotNull(parent2).resultNow());
		}
	}

	public abstract static class SubmittableListenerFuture<O> extends AbstractListenerFuture<O> {
		private @Nullable Future<?> parent;

		protected SubmittableListenerFuture(@Nullable AsyncContext context) {
			super(context, ListenerFutureState.STATE_RUN_QUEUED);
			this.parent = null;
		}

		protected SubmittableListenerFuture(@Nullable AsyncContext context, @NonNull Future<?> parent) {
			super(context, ListenerFutureState.STATE_LISTENING);
			this.parent = parent;
		}

		protected SubmittableListenerFuture(
				@Nullable AsyncContext context, @NonNull Future<?> parent, @Nullable Executor executor)
		{
			super(context, executor, ListenerFutureState.STATE_RUN_QUEUED);
			this.parent = parent;
		}

		protected SubmittableListenerFuture(
				@Nullable AsyncContext context,
				@Nullable Future<?> parent,
				long delay,
				TimeUnit delayUnit,
				@Nullable Executor executor)
		{
			super(context, delay, delayUnit, executor);
			this.parent = parent;
		}

		protected synchronized Future<?> getParent() {
			return checkNotNull(parent);
		}

		protected Future<?> getParentLocked() {
			return checkNotNull(parent);
		}

		@CallSuper @Override protected void onCompletedLocked(@Nullable Throwable e) {
			super.onCompletedLocked(e);
			parent = null;
		}

		@CallSuper @Override protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
			Future<?> parent;
			synchronized (this) {
				parent = this.parent;
			}
			super.onCancelled(exception, mayInterruptIfRunning);
			if (parent != null) {
				parent.cancel(exception, mayInterruptIfRunning);
			}
		}

		@Override protected boolean shouldQueueExecutionAfterParentComplete(
				Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
		{
			if (parent != this.parent) {
				onCompletingLocked(FAILED_RESULT, new WrongParentFutureException(exception), NO_INTERRUPT);
				return false;
			} else if (exception != null) {
				onCompletingLocked(FAILED_RESULT, exception, mayInterruptIfRunning);
				return false;
			} else {
				return true;
			}
		}
	}
}
