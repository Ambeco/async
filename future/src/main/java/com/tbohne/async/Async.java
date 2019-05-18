package com.tbohne.async;

import com.tbohne.async.VoidFuture.FutureProducer;
import com.tbohne.async.impl.FutureStep.PrereqStrategy;
import com.tbohne.async.impl.ValueFutureStep;
import com.tbohne.async.impl.VoidFutureStep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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
				t.addSuppressed(new IllegalArgumentException("Root has failing dependency"));
				throw t;
			}
		});
		step.onSuccess(null);
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
				t.addSuppressed(new IllegalArgumentException("Root has failing dependency"));
				throw t;
			}
		});
		step.onSuccess(null);
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
		step.onSuccess(null);
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
		step.onSuccess(null);
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
		step.onSuccess(null);
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
		step.onSuccess(null);
		return step;
	}

	public static VoidFuture afterAllVoidComplete(VoidFuture... futures) {
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		HashSet<Future> prerequisites = new HashSet<>(futures.length);
		Collections.addAll(prerequisites, futures);
		step.setPrerequisites(prerequisites, PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static VoidFuture afterAllVoidComplete(Collection<VoidFuture> futures) {
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		step.setPrerequisites(new HashSet<>(futures), PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static <R> ValueFuture<List<R>> afterAllComplete(ValueFuture<R>... futures) {
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
		HashSet<Future> prerequisites = new HashSet<>(futures.length);
		Collections.addAll(prerequisites, futures);
		step.setPrerequisites(prerequisites, PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static <R> ValueFuture<List<R>> afterAllComplete(Collection<ValueFuture<R>> futures) {
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
		step.setPrerequisites(new HashSet<>(futures), PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static VoidFuture afterAllVoidSucceed(VoidFuture... futures) {
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		HashSet<Future> prerequisites = new HashSet<>(futures.length);
		Collections.addAll(prerequisites, futures);
		step.setPrerequisites(prerequisites, PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	public static VoidFuture afterAllVoidSucceed(Collection<VoidFuture> futures) {
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		step.setPrerequisites(new HashSet<>(futures), PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	public static <R> ValueFuture<List<R>> afterAllSucceed(ValueFuture<R>... futures) {
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
		HashSet<Future> prerequisites = new HashSet<>(futures.length);
		Collections.addAll(prerequisites, futures);
		step.setPrerequisites(prerequisites, PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	public static <R> ValueFuture<List<R>> afterAllSucceed(Collection<ValueFuture<R>> futures) {
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
		step.setPrerequisites(new HashSet<>(futures), PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	public static VoidFuture afterAnyVoidSucceed(VoidFuture... futures) {
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		HashSet<Future> prerequisites = new HashSet<>(futures.length);
		Collections.addAll(prerequisites, futures);
		step.setPrerequisites(prerequisites, PrereqStrategy.ANY_PREREQS_COMPLETE);
		return step;
	}

	public static VoidFuture afterAnyVoidSucceed(Collection<VoidFuture> futures) {
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		step.setPrerequisites(new HashSet<>(futures), PrereqStrategy.ANY_PREREQS_COMPLETE);
		return step;
	}

	public static <R> ValueFuture<List<R>> afterAnySucceed(ValueFuture<R>... futures) {
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
		HashSet<Future> prerequisites = new HashSet<>(futures.length);
		Collections.addAll(prerequisites, futures);
		step.setPrerequisites(prerequisites, PrereqStrategy.ANY_PREREQS_COMPLETE);
		return step;
	}

	public static <R> ValueFuture<List<R>> afterAnySucceed(Collection<ValueFuture<R>> futures) {
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
		step.setPrerequisites(new HashSet<>(futures), PrereqStrategy.ANY_PREREQS_COMPLETE);
		return step;
	}

	public static void blockThreadUntilComplete(VoidFuture future)
			throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);
		VoidFuture futureException = future
				.then(getDirectExecutor(), new VoidFuture.FutureListener() {
					@Override
					public void onSuccess(Future future) {
						latch.countDown();
					}

					@Override
					public void onFailure(Future future, RuntimeException t) {
						latch.countDown();
					}
				});
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
		latch.await();
		return future.getNow();
	}
}
