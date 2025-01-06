package com.mpd.concurrent.executors;

import static com.mpd.concurrent.asyncContext.AsyncContext.getCurrentExecutionContext;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.mpd.concurrent.AsyncCallable;
import com.mpd.concurrent.asyncContext.AsyncContext;
import com.mpd.concurrent.executors.locked.MpdAsJavaExecutor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.Future.AsyncCheckedException;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.SchedulableFuture;
import com.mpd.concurrent.futures.SubmittableFuture;
import com.mpd.concurrent.futures.atomic.AbstractListenerFuture;
import com.mpd.concurrent.futures.atomic.FutureAsyncCallable;
import com.mpd.concurrent.futures.atomic.FutureCallable;
import com.mpd.concurrent.futures.atomic.FutureRunnable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface Executor extends AutoCloseable {
	static <O> O callCallable(Callable<O> task) {
		try {
			return task.call();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new AsyncCheckedException(e);
		}
	}

	static @ThreadInExecutorEnum int threadInExecutorEnum(boolean value) {
		return value ? ThreadInExecutorEnum.THREAD_IN_EXECUTOR : ThreadInExecutorEnum.THREAD_NOT_IN_EXECUTOR;
	}

	int getWidth();

	@CanIgnoreReturnValue <O> SubmittableFuture<O> execute(SubmittableFuture<O> task);

	default Future<?> submit(Runnable task) {
		return execute(new FutureRunnable<Void>(null, task));
	}

	default Future<?> submit(Runnable task, RunnablePriority priority) {
		AsyncContext context = getCurrentExecutionContext().clone();
		context.put(RunnablePriority.class, priority);
		return execute(new FutureRunnable<Void>(context, task));
	}

	default <O> Future<O> submit(Runnable task, O result) {
		return execute(new FutureRunnable<O>(null, task, result));
	}

	default <O> Future<O> submit(Runnable task, O result, RunnablePriority priority) {
		AsyncContext context = getCurrentExecutionContext().clone();
		context.put(RunnablePriority.class, priority);
		return execute(new FutureRunnable<O>(context, task, result));
	}

	default <O> Future<O> submit(Callable<O> task) {
		return execute(new FutureCallable<>(null, task));
	}

	default <O> Future<O> submit(Callable<O> task, RunnablePriority priority) {
		AsyncContext context = getCurrentExecutionContext().clone();
		context.put(RunnablePriority.class, priority);
		return execute(new FutureCallable<>(context, task));
	}

	default <O> Future<O> submitAsync(AsyncCallable<O> task) {
		return execute(new FutureAsyncCallable<>(null, task));
	}

	default <O> Future<O> submitAsync(AsyncCallable<O> task, RunnablePriority priority) {
		AsyncContext context = getCurrentExecutionContext().clone();
		context.put(RunnablePriority.class, priority);
		return execute(new FutureAsyncCallable<>(context, task));
	}

	@Deprecated default void execute(Runnable task) {
		if (task instanceof SubmittableFuture<?>) {
			execute((SubmittableFuture<?>) task);
		} else {
			execute(new FutureRunnable<Void>(null, task));
		}
	}

	@Deprecated default void execute(Runnable task, RunnablePriority priority) {
		if (task instanceof SubmittableFuture<?>) {
			execute((SubmittableFuture<?>) task);
		} else {
			AsyncContext context = getCurrentExecutionContext().clone();
			context.put(RunnablePriority.class, priority);
			execute(new FutureRunnable<Void>(context, task));
		}
	}

	default <O> SchedulableFuture<O> schedule(SchedulableFuture<O> task) {
		long delayMs = task.getDelay(TimeUnit.MILLISECONDS);
		Preconditions.checkArgument(delayMs > 0);
		FutureRunnable<Void> delayed = new FutureRunnable<>(null, () -> {}, null, delayMs, TimeUnit.MILLISECONDS);
		if (task instanceof AbstractListenerFuture) {
			delayed.setListener((AbstractListenerFuture<O>) task);
		} else {
			delayed.setListener(new FutureListener<Void>() {
				@Override public void onFutureSucceeded(Future<? extends Void> future, Void result) {
					Executor.this.submit(task);
				}

				@Override
				public void onFutureFailed(Future<? extends Void> future, Throwable exception, boolean mayInterruptIfRunning) {
					task.setException(exception, mayInterruptIfRunning);
				}
			});
		}
		Future.futureConfig.getDelegateScheduledExecutor().schedule(delayed, delayMs, TimeUnit.MILLISECONDS);
		return task;
	}

	default Future<?> schedule(Runnable task, long delay, TimeUnit unit) {
		return schedule(new FutureRunnable<>(null, task, null, delay, unit));
	}

	default Future<?> schedule(Runnable task, long delay, TimeUnit unit, RunnablePriority priority) {
		AsyncContext context = getCurrentExecutionContext().clone();
		context.put(RunnablePriority.class, priority);
		return schedule(new FutureRunnable<>(context, task, null, delay, unit));
	}

	default <O> Future<O> schedule(Callable<O> task, long delay, TimeUnit unit) {
		return schedule(new FutureCallable<>(null, task, delay, unit));
	}

	default <O> Future<O> schedule(Callable<O> task, long delay, TimeUnit unit, RunnablePriority priority) {
		AsyncContext context = getCurrentExecutionContext().clone();
		context.put(RunnablePriority.class, priority);
		return schedule(new FutureCallable<>(context, task, delay, unit));
	}

	default <O> Future<O> scheduleAsync(AsyncCallable<O> task, long delay, TimeUnit unit) {
		return schedule(new FutureAsyncCallable<>(null, task, delay, unit));
	}

	default <O> Future<O> scheduleAsync(AsyncCallable<O> task, long delay, TimeUnit unit, RunnablePriority priority) {
		AsyncContext context = getCurrentExecutionContext().clone();
		context.put(RunnablePriority.class, priority);
		return schedule(new FutureAsyncCallable<>(context, task, delay, unit));
	}

	@ThreadInExecutorEnum int ownsThread(Thread thread);

	boolean isIdleNow();

	void registerListener(ExecutorListener onIdleCallback);

	boolean unregisterListener(ExecutorListener onIdleCallback);

	default void awaitIdle(long timeout, TimeUnit unit) throws TimeoutException {
		ExecutorIdleLatchListener listener = new ExecutorIdleLatchListener();
		try {
			registerListener(listener);
			if (!listener.await(timeout, unit)) {
				throw new TimeoutException();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			unregisterListener(listener);
		}
	}

	@Override default void close() {
		shutdown();
		try {
			awaitIdle(1, TimeUnit.HOURS);
		} catch (TimeoutException e) {
			throw new RuntimeException(e);
		}
	}

	void shutdown();

	default ScheduledExecutorService asJavaExecutor() {
		if (this instanceof ScheduledExecutorService) {
			return (ScheduledExecutorService) this;
		} else {
			return new MpdAsJavaExecutor(this);
		}
	}

	@NonNull @Override String toString();

	enum RunnablePriority {
		@Deprecated PRIORITY_IMMEDIATE_INTERRUPT(-20), // These always run first.
		@Deprecated
		PRIORITY_MAIN_THREAD_BLOCKED(-19), //main thread is calling Future.get and will not continue until this completes
		PRIORITY_UI_INITIAL_LOAD(-4), // UI is blank until the initial load completes. That's top priority.
		PRIORITY_UI_USER_WAITING_FOR_RESULT(-3), // user hit a button and is waiting for the result to display.
		PRIORITY_UI_USER_WAITING_ON_SECONDARY(-2), // user might be waiting for the result on a secondary device, like a
		// watch or web UI or similar.
		PRIORITY_BG_USER_EXPECTS_MILLIS(-1), // user expects this effect ASAP, but won't necessarily show UI.
		PRIORITY_DEFAULT(0),
		PRIORITY_UI_USER_NOT_WAITING(1), // things the UI wants to load but the user isn't actually "waiting" for.
		PRIORITY_BG_TIMED_METRICS(2),
		PRIORITY_BG_USER_EXPECTS_SECONDS(3),
		PRIORITY_BG_METRICS(4),
		PRIORITY_BG_USER_EXPECTS_MINUTES(11),
		PRIORITY_BG_USER_EXPECTS_HOURS(13), // Idle Background syncs
		PRIORITY_BG_USER_EXPECTS_DAYS(15),  // Daily Background syncs
		PRIORITY_BG_MAINTENANCE(17),
		PRIORITY_NA(19); // will never run

		public final int value;

		RunnablePriority(int value) {
			this.value = value;
		}

		public RunnablePriority upTo(RunnablePriority other) {
			return (value < other.value) ? other : this;
		}

		public RunnablePriority downTo(RunnablePriority other) {
			return (value < other.value) ? this : other;
		}
	}

	@Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE}) @Retention(RetentionPolicy.SOURCE) @IntDef(
			{
					ThreadInExecutorEnum.THREAD_UNKNOWN_IN_EXEC,
					ThreadInExecutorEnum.THREAD_IN_EXECUTOR,
					ThreadInExecutorEnum.THREAD_NOT_IN_EXECUTOR}) @interface ThreadInExecutorEnum {
		int THREAD_UNKNOWN_IN_EXEC = 0;
		int THREAD_IN_EXECUTOR = 1;
		int THREAD_NOT_IN_EXECUTOR = 2;
	}

	interface ExecutorListener {
		default void beforeExecute(SubmittableFuture<?> r) {}

		default void afterExecute(SubmittableFuture<?> r) {}

		default void onIdle() {}
	}

	class ExecutorIdleLatchListener extends CountDownLatch implements ExecutorListener {
		public ExecutorIdleLatchListener() {
			super(1);
		}

		@Override public void onIdle() {
			countDown();
		}
	}
}
