package com.tbohne.async;

import com.tbohne.async.Listeners.FutureListener;
import com.tbohne.async.Listeners.SimpleFutureProducer;
import com.tbohne.async.impl.SettableValueFutureStep;
import com.tbohne.async.impl.SettableVoidFutureStep;
import com.tbohne.async.impl.ValueFutureStep;
import com.tbohne.async.impl.VoidFutureStep;

import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

public class Async {
	public static VoidFuture start(Executor executor, Runnable runnable) {
		VoidFutureStep step = new VoidFutureStep(executor, new SimpleFutureProducer<Void>() {
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
		ValueFutureStep<R> step = new ValueFutureStep<>(executor, new SimpleFutureProducer<R>() {
			@Override
			public R onSuccess() {
				return runnable.get();
			}
		});
		step.onSuccess(null);
		return step;
	}

	public static <R> ValueFuture<R> immediateFuture(R value) {
		SettableValueFutureStep<R> future = new SettableValueFutureStep<>();
		future.setResult(value);
		return future;
	}

	public static <R> VoidFuture immediateFuture() {
		SettableVoidFutureStep future = new SettableVoidFutureStep();
		future.setResult();
		return future;
	}

	public static <R> ValueFuture<R> failedValueFuture(Class<R> valueType,
			RuntimeException exception) {
		SettableValueFutureStep<R> future = new SettableValueFutureStep<>();
		future.setFailed(exception);
		return future;
	}

	public static <R> ValueFuture<R> failedValueFuture(RuntimeException exception) {
		SettableValueFutureStep<R> future = new SettableValueFutureStep<>();
		future.setFailed(exception);
		return future;
	}

	public static VoidFuture failedVoidFuture(RuntimeException exception) {
		SettableVoidFutureStep future = new SettableVoidFutureStep();
		future.setFailed(exception);
		return future;
	}

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
