package com.tbohne.async;

import com.tbohne.async.Future.FutureListener;
import com.tbohne.async.TaskCallbacks.SimpleProducerTask;
import com.tbohne.async.impl.QueueableValueFuture;
import com.tbohne.async.impl.QueueableVoidFuture;
import com.tbohne.async.impl.SettableValueFuture;
import com.tbohne.async.impl.SettableVoidFuture;

import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import static com.tbohne.async.impl.QueueableFutureTask.NO_PREREQS;

/**
 * Helper methods for starting and stopping async work
 */
public class Async {
	public static VoidFuture start(Executor executor, Runnable runnable) {
		QueueableVoidFuture step = new QueueableVoidFuture(PrereqStrategy.ALL_PREREQS_SUCCEED,
				NO_PREREQS,
				executor,
				new SimpleProducerTask<Void>() {
					@Override
					public Void onSuccess() {
						runnable.run();
						return null;
					}
				});
		step.onSuccess(null);
		return step;
	}

	public static <R> ValueFuture<R> start(Executor executor, Supplier<R> runnable) {
		QueueableValueFuture<R> step
				= new QueueableValueFuture<>(PrereqStrategy.ALL_PREREQS_SUCCEED,
				NO_PREREQS,
				executor,
				new SimpleProducerTask<R>() {
					@Override
					public R onSuccess() {
						return runnable.get();
					}
				});
		step.onSuccess(null);
		return step;
	}

	public static <R> ValueFuture<R> immediateFuture(R value) {
		SettableValueFuture<R> future = new SettableValueFuture<>();
		future.setResult(value);
		return future;
	}

	public static VoidFuture immediateFuture() {
		SettableVoidFuture future = new SettableVoidFuture();
		future.setResult();
		return future;
	}

	@Deprecated
	public static <R> ValueFuture<R> failedValueFuture(Class<R> valueType,
			RuntimeException exception) {
		SettableValueFuture<R> future = new SettableValueFuture<>();
		future.setFailed(exception);
		return future;
	}

	public static <R> ValueFuture<R> failedValueFuture(RuntimeException exception) {
		SettableValueFuture<R> future = new SettableValueFuture<>();
		future.setFailed(exception);
		return future;
	}

	public static VoidFuture failedVoidFuture(RuntimeException exception) {
		SettableVoidFuture future = new SettableVoidFuture();
		future.setFailed(exception);
		return future;
	}

	/**
	 * Blocks the current thread until a future completes
	 * <p>
	 * This should only be used for finishing async work before returning from a library callback,
	 * or similar pre-future code.
	 */
	public static void blockThreadUntilComplete(VoidFuture future) throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);
		future.addListener(new BlockingListener(latch));
		latch.await();
		if (future.getThrownException() != null) {
			throw future.getThrownException();
		}
	}

	private static class BlockingListener implements FutureListener {
		private final CountDownLatch latch;

		BlockingListener(CountDownLatch latch) {
			this.latch = latch;
		}

		@Override
		public void onSuccess(Future future) {
			latch.countDown();
		}

		@Override
		public void onFailure(Future future, RuntimeException t) {
			latch.countDown();
		}
	}
}
