package com.mpd.concurrent.futures.atomic;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.mpd.concurrent.executors.MoreExecutors.directExecutor;

import androidx.annotation.CallSuper;

import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public final class AbstractListenerFutures {
	private AbstractListenerFutures() {}

	public abstract static class SingleParentImmediateListenerFuture<I, O> extends AbstractListenerFuture<O> {
		/**
		 * @noinspection unchecked
		 */
		private static final AtomicReferenceFieldUpdater<SingleParentImmediateListenerFuture<?, ?>, Future<?>>
				atomicParent =
				AtomicReferenceFieldUpdater.newUpdater((Class<SingleParentImmediateListenerFuture<?, ?>>) (Class<?>) AbstractFuture.class,
						(Class<Future<?>>) (Class<?>) Future.class,
						"parent");

		private volatile @Nullable Future<? extends I> parent; // TODO: atomicParent

		protected SingleParentImmediateListenerFuture(@NonNull Future<? extends I> parent) {
			super(null, directExecutor());
			this.parent = parent;
			parent.setListener(this);
		}

		protected Future<? extends I> getParent() {
			//noinspection unchecked
			return checkNotNull((Future<? extends I>) atomicParent.get(this));
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
			atomicParent.lazySet(this, null);
		}

		@CallSuper @Override protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
			Future<?> parent = atomicParent.get(this);
			super.onCancelled(exception, mayInterruptIfRunning);
			if (parent != null) {
				parent.cancel(exception, mayInterruptIfRunning);
			}
		}

		@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
			Future<?> parent = atomicParent.get(this);
			super.addPendingString(sb, maxDepth);
			if (parent != null) {
				parent.addPendingString(sb, maxDepth - 1);
			}
		}
	}

	public abstract static class SingleParentTransformListenerFuture<I, O> extends AbstractListenerFuture<O> {
		/**
		 * @noinspection unchecked
		 */
		private static final AtomicReferenceFieldUpdater<SingleParentTransformListenerFuture<?, ?>, Future<?>>
				atomicParent =
				AtomicReferenceFieldUpdater.newUpdater((Class<SingleParentTransformListenerFuture<?, ?>>) (Class<?>) AbstractFuture.class,
						(Class<Future<?>>) (Class<?>) Future.class,
						"parent");

		private volatile @Nullable Future<? extends I> parent; // TODO: atomicParent

		protected SingleParentTransformListenerFuture(@NonNull Future<? extends I> parent, Executor executor) {
			super(null, executor);
			this.parent = parent;
			parent.setListener(this);
		}

		protected Future<? extends I> getParent() {
			//noinspection unchecked
			return checkNotNull((Future<? extends I>) atomicParent.get(this));
		}

		@Override protected boolean shouldQueueExecutionAfterParentComplete(
				Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
		{
			if (parent != getParent()) {
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
			atomicParent.lazySet(this, null);
		}

		@CallSuper @Override protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
			Future<?> parent = atomicParent.get(this);
			super.onCancelled(exception, mayInterruptIfRunning);
			if (parent != null) {
				parent.cancel(exception, mayInterruptIfRunning);
			}
		}

		@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
			Future<?> parent = atomicParent.get(this);
			super.addPendingString(sb, maxDepth);
			if (parent != null) {
				parent.addPendingString(sb, maxDepth - 1);
			}
		}
	}

	public abstract static class SingleParentCatchingAbstractListenerFuture<E extends Throwable, O>
			extends AbstractListenerFuture<O>
	{
		/**
		 * @noinspection unchecked
		 */
		private static final AtomicReferenceFieldUpdater<SingleParentCatchingAbstractListenerFuture<?, ?>, Future<?>>
				atomicParent =
				AtomicReferenceFieldUpdater.newUpdater((Class<SingleParentCatchingAbstractListenerFuture<?, ?>>) (Class<?>) AbstractFuture.class,
						(Class<Future<?>>) (Class<?>) Future.class,
						"parent");

		private final Class<E> exceptionClass;
		private volatile @Nullable Future<? extends O> parent; // TODO: atomicParent

		protected SingleParentCatchingAbstractListenerFuture(
				Class<E> exceptionClass, @NonNull Future<? extends O> parent, Executor executor)
		{
			super(null, executor);
			this.parent = parent;
			this.exceptionClass = exceptionClass;
			parent.setListener(this);
		}

		protected Future<? extends O> getParent() {
			//noinspection unchecked
			return checkNotNull((Future<? extends O>) atomicParent.get(this));
		}

		protected Class<E> getExceptionClass() {
			return exceptionClass;
		}

		@Override protected boolean shouldQueueExecutionAfterParentComplete(
				Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
		{
			if (parent != getParent()) {
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
			atomicParent.lazySet(this, null);
		}

		@CallSuper @Override protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
			Future<?> parent = atomicParent.get(this);
			super.onCancelled(exception, mayInterruptIfRunning);
			if (parent != null) {
				parent.cancel(exception, mayInterruptIfRunning);
			}
		}

		@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
			Future<?> parent = atomicParent.get(this);
			super.addPendingString(sb, maxDepth);
			if (parent != null) {
				parent.addPendingString(sb, maxDepth - 1);
			}
		}
	}

	public abstract static class TwoParentAbstractListenerFuture<I1, I2, O> extends AbstractListenerFuture<O> {
		/**
		 * @noinspection unchecked
		 */
		private static final AtomicReferenceFieldUpdater<TwoParentAbstractListenerFuture<?, ?, ?>, Future<?>>
				atomicParent1 =
				AtomicReferenceFieldUpdater.newUpdater((Class<TwoParentAbstractListenerFuture<?, ?, ?>>) (Class<?>) AbstractFuture.class,
						(Class<Future<?>>) (Class<?>) Future.class,
						"parent1");

		/**
		 * @noinspection unchecked
		 */
		private static final AtomicReferenceFieldUpdater<TwoParentAbstractListenerFuture<?, ?, ?>, Future<?>>
				atomicParent2 =
				AtomicReferenceFieldUpdater.newUpdater((Class<TwoParentAbstractListenerFuture<?, ?, ?>>) (Class<?>) AbstractFuture.class,
						(Class<Future<?>>) (Class<?>) Future.class,
						"parent2");

		/**
		 * @noinspection unchecked
		 */
		private static final AtomicIntegerFieldUpdater<TwoParentAbstractListenerFuture<?, ?, ?>>
				atomicComplete =
				AtomicIntegerFieldUpdater.newUpdater((Class<TwoParentAbstractListenerFuture<?, ?, ?>>) (Class<?>) AbstractFuture.class,
						"atomicComplete");
		private final int completeBitfield = 0; // TODO: completeBitfield
		private volatile @Nullable Future<? extends I1> parent1; // TODO: atomicParent1
		private volatile @Nullable Future<? extends I2> parent2; // TODO: atomicParent2

		protected TwoParentAbstractListenerFuture(
				@NonNull Future<? extends I1> parent1, @NonNull Future<? extends I2> parent2, Executor executor)
		{
			super(null, executor);
			this.parent1 = parent1;
			this.parent2 = parent2;
			parent1.setListener(this);
			parent2.setListener(this);
		}

		protected Future<? extends I1> getParent1() {
			//noinspection unchecked
			return checkNotNull((Future<? extends I1>) atomicParent1.get(this));
		}

		protected Future<? extends I2> getParent2() {
			//noinspection unchecked
			return checkNotNull((Future<? extends I2>) atomicParent2.get(this));
		}

		@Override protected boolean shouldQueueExecutionAfterParentComplete(
				Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
		{
			int completedFutureBit;
			if (parent == atomicParent1.get(this)) {
				//noinspection PointlessBitwiseExpression
				completedFutureBit = 1 << 0;
			} else if (parent == atomicParent2.get(this)) {
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
			atomicParent1.lazySet(this, null);
			atomicParent2.lazySet(this, null);
		}

		@CallSuper @Override protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
			Future<?> parent1 = atomicParent1.get(this);
			Future<?> parent2 = atomicParent2.get(this);
			super.onCancelled(exception, mayInterruptIfRunning);
			if (parent1 != null) {
				parent1.cancel(exception, mayInterruptIfRunning);
			}
			if (parent2 != null) {
				parent2.cancel(exception, mayInterruptIfRunning);
			}
		}

		@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
			Future<?> parent1 = atomicParent1.get(this);
			Future<?> parent2 = atomicParent2.get(this);
			super.addPendingString(sb, maxDepth);
			boolean didAppend = false;
			if (parent1 != null) {
				parent1.addPendingString(sb, maxDepth - 1);
				didAppend = true;
			}
			if (parent2 != null) {
				if (didAppend) {
					sb.append("\nand also:\n");
				}
				parent2.addPendingString(sb, maxDepth - 1);
			}
		}
	}
}
