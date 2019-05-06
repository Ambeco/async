package com.tbohne.async;

import com.tbohne.async.VoidFuture.FutureProducer;
import com.tbohne.async.impl.ValueFutureStep;
import com.tbohne.async.impl.VoidFutureStep;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import static com.tbohne.async.DirectExecutor.getDirectExecutor;
import static com.tbohne.async.impl.FutureStep.NO_OP_VOID_CALLBACK;

public class Async {
	public static VoidFuture start(Executor executor, Runnable runnable) {
		VoidFutureStep step = new VoidFutureStep(executor, new FutureProducer<Void>(){
			@Override
			public Void onSuccess() {
				runnable.run();
				return null;
			}

			@Override
			public Void onFailure(RuntimeException t) {
				throw new IllegalArgumentException("Root has failing dependency");
			}
		});
		step.onSuccess();
		return step;
	}

	public static <R> ValueFuture<R> start(Executor executor, Supplier<R> runnable) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor, new FutureProducer<R>(){
			@Override
			public R onSuccess() {
				return runnable.get();
			}

			@Override
			public R onFailure(RuntimeException t) {
				throw new IllegalArgumentException("Root has failing dependency");
			}
		});
		step.onSuccess();
		return step;
	}

	public static <R> ValueFuture<R> immediateFuture(R value) {
		ValueFutureStep<R> step = new ValueFutureStep<>(getDirectExecutor(), new FutureProducer<R>(){
			@Override
			public R onSuccess() {
				return value;
			}

			@Override
			public R onFailure(RuntimeException t) {
				throw new IllegalArgumentException("Root has failing dependency");
			}
		});
		step.onSuccess();
		return step;
	}

	public static <R, E extends RuntimeException> ValueFuture<R> failedValueFuture(Class<R> valueType, E exception) {
		//TODO: Make actual failed future for stopping executor
		ValueFutureStep<R> step = new ValueFutureStep<>(getDirectExecutor(), new FutureProducer<R>(){
			@Override
			public R onSuccess() {
				throw exception;
			}

			@Override
			public R onFailure(RuntimeException t) {
				throw new IllegalArgumentException("Root has failing dependency");
			}
		});
		step.onSuccess();
		return step;
	}

	public static <R, E extends RuntimeException> ValueFuture<R> failedValueFuture(E exception) {
		//TODO: Make actual failed future for stopping executor
		ValueFutureStep<R> step = new ValueFutureStep<>(getDirectExecutor(), new FutureProducer<R>(){
			@Override
			public R onSuccess() {
				throw exception;
			}

			@Override
			public R onFailure(RuntimeException t) {
				throw new IllegalArgumentException("Root has failing dependency");
			}
		});
		step.onSuccess();
		return step;
	}

	public static <E extends RuntimeException> VoidFuture failedVoidFuture(E exception) {
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), new FutureProducer<Void>(){
			@Override
			public Void onSuccess() {
				throw exception;
			}

			@Override
			public Void onFailure(RuntimeException t) {
				throw new IllegalArgumentException("Root has failing dependency");
			}
		});
		step.onSuccess();
		return step;
	}

	public static VoidFuture afterAllVoidDone(VoidFuture... futures) {
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		step.setPrerequisites(Arrays.asList(futures));
		return step;
	}

	public static VoidFuture afterAllVoidDone(List<VoidFuture> futures) {
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		step.setPrerequisites(futures);
		return step;
	}

	public static <R> ValueFuture<List<R>> afterAllDone(ValueFuture<R>... futures) {
		ValueFutureStep<List<R>>
				step = new ValueFutureStep<>(getDirectExecutor(), new FutureProducer<List<R>>(){
			@Override
			public List<R> onSuccess() {
				List<R> results = new ArrayList<>(futures.length);
				for (ValueFuture<R> future : futures) {
					results.add(future.getNow());
				}
				return results;
			}

			@Override
			public List<R> onFailure(RuntimeException t) {
				throw t;
			}
		});
		step.setPrerequisites(Arrays.asList(futures));
		return step;
	}

	public static <R> ValueFuture<List<R>> afterAllDone(List<ValueFuture<R>> futures) {
		ValueFutureStep<List<R>>
				step = new ValueFutureStep<>(getDirectExecutor(), new FutureProducer<List<R>>(){
			@Override
			public List<R> onSuccess() {
				List<R> results = new ArrayList<>(futures.size());
				for (ValueFuture<R> future : futures) {
					results.add(future.getNow());
				}
				return results;
			}

			@Override
			public List<R> onFailure(RuntimeException t) {
				throw t;
			}
		});
		step.setPrerequisites(futures);
		return step;
	}

	public static void blockThreadUntilComplete(VoidFuture future)
			throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);
		VoidFuture futureException = future
				.then(getDirectExecutor(), new VoidFuture.FutureListener() {
					@Override
					public void onSuccess() {
						latch.countDown();
					}

					@Override
					public void onFailure(RuntimeException t) {
						latch.countDown();
					}
				});
		//TODO ATTACH CANCEL LISTENER
		latch.await();
		if (future.getThrownException() != null) {
			throw future.getThrownException();
		}
	}

	public static <R> R blockThreadUntilComplete(ValueFuture<R> future)
			throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);
		VoidFuture futureException = future
				.then(getDirectExecutor(), new ValueFuture.FutureValueConsumer<R>() {
					@Override
					public void onSuccess(R result) {
						latch.countDown();
					}

					@Override
					public void onFailure(RuntimeException t) {
						latch.countDown();
					}
				});
		//TODO ATTACH CANCEL LISTENER
		latch.await();
		return future.getNow();
	}
}
