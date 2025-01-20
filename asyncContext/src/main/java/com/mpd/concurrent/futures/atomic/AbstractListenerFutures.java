package com.mpd.concurrent.futures.atomic;

import static com.mpd.concurrent.executors.MoreExecutors.directExecutor;

import androidx.annotation.CallSuper;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class AbstractListenerFutures {
	private AbstractListenerFutures() {}

	public abstract static class SingleParentImmediateListenerFuture<I, O> extends AbstractListenerFuture<O> {
		// TODO parent to use stub instead of Nullable?
		private volatile @Nullable Future<? extends I> parent;

		protected SingleParentImmediateListenerFuture(@NonNull Future<? extends I> parent) {
			super(null, directExecutor());
			this.parent = parent;
		}

		protected @Nullable Future<? extends I> getParent() {
			return parent;
		}

		@Override protected boolean shouldQueueExecutionAfterParentComplete(
				Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
		{
			if (parent != this.parent) {
				setComplete(FAILED_RESULT, new WrongParentFutureException(exception), NO_INTERRUPT);
				return false;
			} else {
				return true;
			}
		}

		@CallSuper @Override protected void afterDone(
				@Nullable O result,
				@Nullable Throwable exception,
				boolean mayInterruptIfRunning,
				FutureListener<? super O> listener)
		{
			super.afterDone(result, exception, mayInterruptIfRunning, listener);
			parent = null;
		}

		@CallSuper @Override protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
			Future<?> parent = this.parent;
			super.onCancelled(exception, mayInterruptIfRunning);
			if (parent != null) {
				parent.cancel(exception, mayInterruptIfRunning);
			}
		}

		@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
			Future<?> parent = this.parent;
			super.addPendingString(sb, maxDepth);
			if (parent != null && maxDepth > 1) {
				parent.addPendingString(sb, maxDepth - 1);
			}
		}
	}

	public abstract static class SingleParentTransformListenerFuture<I, O> extends AbstractListenerFuture<O> {
		// TODO parent to use stub instead of Nullable?
		private volatile @Nullable Future<? extends I> parent;

		protected SingleParentTransformListenerFuture(@NonNull Future<? extends I> parent, Executor executor) {
			super(null, executor);
			this.parent = parent;
		}

		protected @Nullable Future<? extends I> getParent() {
			return this.parent;
		}

		@Override protected boolean shouldQueueExecutionAfterParentComplete(
				Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
		{
			if (parent != this.parent) {
				setComplete(FAILED_RESULT, new WrongParentFutureException(exception), NO_INTERRUPT);
				return false;
			} else if (exception != null) {
				setComplete(FAILED_RESULT, exception, mayInterruptIfRunning);
				return false;
			} else {
				return true;
			}
		}

		@CallSuper @Override protected void afterDone(
				@Nullable O result,
				@Nullable Throwable exception,
				boolean mayInterruptIfRunning,
				FutureListener<? super O> listener)
		{
			super.afterDone(result, exception, mayInterruptIfRunning, listener);
			this.parent = null;
		}

		@CallSuper @Override protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
			Future<?> parent = this.parent;
			super.onCancelled(exception, mayInterruptIfRunning);
			if (parent != null) {
				parent.cancel(exception, mayInterruptIfRunning);
			}
		}

		@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
			Future<?> parent = this.parent;
			super.addPendingString(sb, maxDepth);
			if (parent != null && maxDepth > 1) {
				parent.addPendingString(sb, maxDepth - 1);
			}
		}
	}

	public abstract static class SingleParentCatchingAbstractListenerFuture<E extends Throwable, O>
			extends AbstractListenerFuture<O>
	{
		// TODO parent to use stub instead of Nullable?
		private final Class<E> exceptionClass;
		private volatile @Nullable Future<? extends O> parent;

		protected SingleParentCatchingAbstractListenerFuture(
				Class<E> exceptionClass, @NonNull Future<? extends O> parent, Executor executor)
		{
			super(null, executor);
			this.parent = parent;
			this.exceptionClass = exceptionClass;
		}

		protected @Nullable Future<? extends O> getParent() {
			return this.parent;
		}

		protected Class<E> getExceptionClass() {
			return exceptionClass;
		}

		@Override protected boolean shouldQueueExecutionAfterParentComplete(
				Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
		{
			if (parent != this.parent) {
				setComplete(FAILED_RESULT, new WrongParentFutureException(exception), NO_INTERRUPT);
				return false;
			} else if (exceptionClass.isInstance(exception)) {
				return true;
			} else {
				//noinspection unchecked
				setComplete((O) result, exception, mayInterruptIfRunning);
				return false;
			}
		}

		@CallSuper @Override protected void afterDone(
				@Nullable O result,
				@Nullable Throwable exception,
				boolean mayInterruptIfRunning,
				FutureListener<? super O> listener)
		{
			super.afterDone(result, exception, mayInterruptIfRunning, listener);
			parent = null;
		}

		@CallSuper @Override protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
			Future<?> parent = this.parent;
			super.onCancelled(exception, mayInterruptIfRunning);
			if (parent != null) {
				parent.cancel(exception, mayInterruptIfRunning);
			}
		}

		@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
			Future<?> parent = this.parent;
			super.addPendingString(sb, maxDepth);
			if (parent != null && maxDepth > 1) {
				parent.addPendingString(sb, maxDepth - 1);
			}
		}
	}

	public abstract static class TwoParentAbstractListenerFuture<I1, I2, O> extends AbstractListenerFuture<O> {
		/**
		 * @noinspection unchecked
		 */
		private static final AtomicIntegerFieldUpdater<TwoParentAbstractListenerFuture<?, ?, ?>>
				atomicComplete =
				AtomicIntegerFieldUpdater.newUpdater((Class<TwoParentAbstractListenerFuture<?, ?, ?>>) (Class<?>) AbstractFuture.class,
						"atomicComplete");
		private final int completeBitfield = 0; // TODO: completeBitfield

		// TODO parent to use stub instead of Nullable?
		private volatile @Nullable Future<? extends I1> parent1;
		private volatile @Nullable Future<? extends I2> parent2;

		protected TwoParentAbstractListenerFuture(
				@NonNull Future<? extends I1> parent1, @NonNull Future<? extends I2> parent2, Executor executor)
		{
			super(null, executor);
			this.parent1 = parent1;
			this.parent2 = parent2;
		}

		protected @Nullable Future<? extends I1> getParent1() {
			return parent1;
		}

		protected @Nullable Future<? extends I2> getParent2() {
			return parent2;
		}

		@Override protected boolean shouldQueueExecutionAfterParentComplete(
				Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
		{
			int completedFutureBit;
			if (parent == this.parent1) {
				//noinspection PointlessBitwiseExpression
				completedFutureBit = 1 << 0;
			} else if (parent == this.parent2) {
				completedFutureBit = 1 << 1;
			} else {
				setException(new WrongParentFutureException());
				return false;
			}
			int oldBitfield;
			int newBitfield;
			do {
				oldBitfield = atomicComplete.get(this);
				newBitfield = oldBitfield | completedFutureBit;
				if (oldBitfield == newBitfield) {
					setException(new ParentSucceededTwiceException());
					return false;
				}
			} while (!atomicComplete.compareAndSet(this, oldBitfield, newBitfield));
			return newBitfield == 3;
		}

		@CallSuper @Override protected void afterDone(
				@Nullable O result,
				@Nullable Throwable exception,
				boolean mayInterruptIfRunning,
				FutureListener<? super O> listener)
		{
			super.afterDone(result, exception, mayInterruptIfRunning, listener);
			this.parent1 = null;
			this.parent2 = null;
		}

		@CallSuper @Override protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
			super.onCancelled(exception, mayInterruptIfRunning);
			Future<?> parent1 = this.parent1;
			if (parent1 != null) {
				parent1.cancel(exception, mayInterruptIfRunning);
			}
			Future<?> parent2 = this.parent2;
			if (parent2 != null) {
				parent2.cancel(exception, mayInterruptIfRunning);
			}
		}

		@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
			Future<?> parent1 = this.parent1;
			Future<?> parent2 = this.parent2;
			super.addPendingString(sb, maxDepth);
			boolean didAppend = false;
			if (parent1 != null && maxDepth > 1) {
				parent1.addPendingString(sb, maxDepth - 1);
				didAppend = true;
			}
			if (parent2 != null && maxDepth > 1) {
				if (didAppend) {
					sb.append("\nand also:\n");
				}
				parent2.addPendingString(sb, maxDepth - 1);
			}
		}
	}
}
