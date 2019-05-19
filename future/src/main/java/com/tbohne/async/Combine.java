package com.tbohne.async;

import com.tbohne.async.Listeners.BiFutureConsumer;
import com.tbohne.async.Listeners.BiFutureTransformer;
import com.tbohne.async.Listeners.FutureEffect;
import com.tbohne.async.Listeners.FutureProducer;
import com.tbohne.async.Listeners.FutureValueConsumer;
import com.tbohne.async.Listeners.FutureValueTransformer;
import com.tbohne.async.impl.FutureProducers.BiConsumerAsFutureProducer;
import com.tbohne.async.impl.FutureProducers.BiFunctionAsFutureProducer;
import com.tbohne.async.impl.FutureProducers.BiFutureConsumerAsFutureProducer;
import com.tbohne.async.impl.FutureProducers.BiFutureTransformerAsFutureProducer;
import com.tbohne.async.impl.FutureProducers.ConsumerAsFutureProducer;
import com.tbohne.async.impl.FutureProducers.FunctionAsFutureProducer;
import com.tbohne.async.impl.FutureProducers.FutureEffectAsFutureProducer;
import com.tbohne.async.impl.FutureProducers.ValueConsumerAsFutureProducer;
import com.tbohne.async.impl.FutureProducers.ValueTransformerAsFutureProducer;
import com.tbohne.async.impl.PrereqStrategy;
import com.tbohne.async.impl.ValueFutureStep;
import com.tbohne.async.impl.VoidFutureStep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.tbohne.async.DirectExecutor.getDirectExecutor;
import static com.tbohne.async.impl.FutureProducers.NO_OP_VOID_CALLBACK;

/**
 * Methods for adding work after other futures complete.
 * <p>
 * Categories are:
 * <ul>
 * <li>Ignoring resulting values</li>
 * <li>Creating a new future that completes when a prior future does (can be useful for controlling cancellations)</li>
 * <li>Creating a future for new work after existing future completes</li>
 * <li>Creating a future for new work after two futures both succeed</li>
 * <li>Creating a future for new work after two futures both complete or succeed</li>
 * <li>Creating a new future that completes when a list of futures succeeds, including List&lt;Future&lt;X&gt;&gt; to Future&lt;List&lt;X&gt;&gt;</li>
 * </ul>
 */
public class Combine {
	public static <T> VoidFuture ignore(ValueFuture<T> future) {
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		step.setPrerequisites(future);
		return step;
	}

	public static <T> VoidFuture ignore(ValueFuture<T> future,
			Executor executor,
			FutureProducer<Void> followup) {
		VoidFutureStep step = new VoidFutureStep(executor, followup);
		step.setPrerequisites(future);
		return step;
	}

	public static <T> VoidFuture ignore(ValueFuture<T> future,
			Executor executor,
			FutureEffect followup) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new FutureEffectAsFutureProducer(followup));
		step.setPrerequisites(future);
		return step;
	}

	public static <T, U> VoidFuture ignore(BiValueFuture<T, U> future) {
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		step.setPrerequisites(future);
		return step;
	}

	public static <T, U> VoidFuture ignore(BiValueFuture<T, U> future,
			Executor executor,
			FutureProducer<Void> followup) {
		VoidFutureStep step = new VoidFutureStep(executor, followup);
		step.setPrerequisites(future);
		return step;
	}

	public static <T, U> VoidFuture ignore(BiValueFuture<T, U> future,
			Executor executor,
			FutureEffect followup) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new FutureEffectAsFutureProducer(followup));
		step.setPrerequisites(future);
		return step;
	}


	public static VoidFuture afterComplete(VoidFuture future,
			Executor executor,
			FutureEffect listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new FutureEffectAsFutureProducer(listener));
		step.setPrerequisites(future);
		return step;
	}

	public static <R> ValueFuture<R> afterComplete(VoidFuture future,
			Executor executor,
			FutureProducer<R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor, listener);
		step.setPrerequisites(future);
		return step;
	}

	public static <T> VoidFuture afterComplete(ValueFuture<T> future,
			Executor executor,
			FutureValueConsumer<T> listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new ValueConsumerAsFutureProducer<>(future, listener));
		step.setPrerequisites(future);
		return step;
	}

	public static <T> VoidFuture afterComplete(ValueFuture<T> future,
			Executor executor,
			Consumer<FutureResult<T>> listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new ConsumerAsFutureProducer<>(future, listener));
		step.setPrerequisites(future);
		return step;
	}

	public static <T, R> ValueFuture<R> afterComplete(ValueFuture<T> future,
			Executor executor,
			FutureValueTransformer<T, R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor,
				new ValueTransformerAsFutureProducer<>(future, listener));
		step.setPrerequisites(future);
		return step;
	}

	public static <T, R> ValueFuture<R> afterComplete(ValueFuture<T> future,
			Executor executor,
			Function<FutureResult<T>, R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor,
				new FunctionAsFutureProducer<>(future, listener));
		step.setPrerequisites(future);
		return step;
	}

	public static <T, U> VoidFuture afterComplete(BiValueFuture<T, U> future,
			Executor executor,
			BiFutureConsumer<T, U> listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new BiFutureConsumerAsFutureProducer<>(future.getFirst(),
						future.getSecond(),
						listener));
		step.setPrerequisites(future);
		return step;
	}

	public static <T, U> VoidFuture afterComplete(BiValueFuture<T, U> future,
			Executor executor,
			BiConsumer<FutureResult<T>, FutureResult<U>> listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new BiConsumerAsFutureProducer<>(future.getFirst(), future.getSecond(), listener));
		step.setPrerequisites(future);
		return step;
	}

	public static <T, U, R> ValueFuture<R> afterComplete(BiValueFuture<T, U> future,
			Executor executor,
			BiFutureTransformer<T, U, R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor,
				new BiFutureTransformerAsFutureProducer<>(future.getFirst(),
						future.getSecond(),
						listener));
		step.setPrerequisites(future);
		return step;
	}

	public static <T, U, R> ValueFuture<R> afterComplete(BiValueFuture<T, U> future,
			Executor executor,
			BiFunction<FutureResult<T>, FutureResult<U>, R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor,
				new BiFunctionAsFutureProducer<>(future.getFirst(), future.getSecond(), listener));
		step.setPrerequisites(future);
		return step;
	}


	public static VoidFuture afterComplete(VoidFuture first,
			VoidFuture second,
			Executor executor,
			FutureEffect listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new FutureEffectAsFutureProducer(listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static <R> ValueFuture<R> afterComplete(VoidFuture first,
			VoidFuture second,
			Executor executor,
			FutureProducer<R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor, listener);
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static <T> VoidFuture afterComplete(ValueFuture<T> first,
			VoidFuture second,
			Executor executor,
			FutureValueConsumer<T> listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new ValueConsumerAsFutureProducer<>(first, listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static <T> VoidFuture afterComplete(ValueFuture<T> first,
			VoidFuture second,
			Executor executor,
			Consumer<FutureResult<T>> listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new ConsumerAsFutureProducer<>(first, listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static <T, R> ValueFuture<R> afterComplete(ValueFuture<T> first,
			VoidFuture second,
			Executor executor,
			FutureValueTransformer<T, R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor,
				new ValueTransformerAsFutureProducer<>(first, listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static <T, R> ValueFuture<R> afterComplete(ValueFuture<T> first,
			VoidFuture second,
			Executor executor,
			Function<FutureResult<T>, R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor,
				new FunctionAsFutureProducer<>(first, listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static <T, U> VoidFuture afterComplete(ValueFuture<T> first,
			ValueFuture<U> second,
			Executor executor,
			BiFutureConsumer<T, U> listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new BiFutureConsumerAsFutureProducer<>(first, second, listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static <T, U> VoidFuture afterComplete(ValueFuture<T> first,
			ValueFuture<U> second,
			Executor executor,
			BiConsumer<FutureResult<T>, FutureResult<U>> listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new BiConsumerAsFutureProducer<>(first, second, listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static <T, U, R> ValueFuture<R> afterComplete(ValueFuture<T> first,
			ValueFuture<U> second,
			Executor executor,
			BiFutureTransformer<T, U, R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor,
				new BiFutureTransformerAsFutureProducer<>(first, second, listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static <T, U, R> ValueFuture<R> afterComplete(ValueFuture<T> first,
			ValueFuture<U> second,
			Executor executor,
			BiFunction<FutureResult<T>, FutureResult<U>, R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor,
				new BiFunctionAsFutureProducer<>(first, second, listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static <T, U> VoidFuture afterComplete(BiValueFuture<T, U> first,
			VoidFuture second,
			Executor executor,
			BiFutureConsumer<T, U> listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new BiFutureConsumerAsFutureProducer<>(first.getFirst(),
						first.getSecond(),
						listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static <T, U> VoidFuture afterComplete(BiValueFuture<T, U> first,
			VoidFuture second,
			Executor executor,
			BiConsumer<FutureResult<T>, FutureResult<U>> listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new BiConsumerAsFutureProducer<>(first.getFirst(), first.getSecond(), listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static <T, U, R> ValueFuture<R> afterComplete(BiValueFuture<T, U> first,
			VoidFuture second,
			Executor executor,
			BiFutureTransformer<T, U, R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor,
				new BiFutureTransformerAsFutureProducer<>(first.getFirst(),
						first.getSecond(),
						listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static <T, U, R> ValueFuture<R> afterComplete(BiValueFuture<T, U> first,
			VoidFuture second,
			Executor executor,
			BiFunction<FutureResult<T>, FutureResult<U>, R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor,
				new BiFunctionAsFutureProducer<>(first.getFirst(), first.getSecond(), listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	public static VoidFuture afterSuccess(VoidFuture first,
			VoidFuture second,
			Executor executor,
			FutureEffect listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new FutureEffectAsFutureProducer(listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	public static <R> ValueFuture<R> afterSuccess(VoidFuture first,
			VoidFuture second,
			Executor executor,
			FutureProducer<R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor, listener);
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	public static <T> VoidFuture afterSuccess(ValueFuture<T> first,
			VoidFuture second,
			Executor executor,
			FutureValueConsumer<T> listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new ValueConsumerAsFutureProducer<>(first, listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	public static <T> VoidFuture afterSuccess(ValueFuture<T> first,
			VoidFuture second,
			Executor executor,
			Consumer<FutureResult<T>> listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new ConsumerAsFutureProducer<>(first, listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	public static <T, R> ValueFuture<R> afterSuccess(ValueFuture<T> first,
			VoidFuture second,
			Executor executor,
			FutureValueTransformer<T, R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor,
				new ValueTransformerAsFutureProducer<>(first, listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	public static <T, R> ValueFuture<R> afterSuccess(ValueFuture<T> first,
			VoidFuture second,
			Executor executor,
			Function<FutureResult<T>, R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor,
				new FunctionAsFutureProducer<>(first, listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	public static <T, U> VoidFuture afterSuccess(ValueFuture<T> first,
			ValueFuture<U> second,
			Executor executor,
			BiFutureConsumer<T, U> listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new BiFutureConsumerAsFutureProducer<>(first, second, listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	public static <T, U> VoidFuture afterSuccess(ValueFuture<T> first,
			ValueFuture<U> second,
			Executor executor,
			BiConsumer<FutureResult<T>, FutureResult<U>> listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new BiConsumerAsFutureProducer<>(first, second, listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	public static <T, U, R> ValueFuture<R> afterSuccess(ValueFuture<T> first,
			ValueFuture<U> second,
			Executor executor,
			BiFutureTransformer<T, U, R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor,
				new BiFutureTransformerAsFutureProducer<>(first, second, listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	public static <T, U, R> ValueFuture<R> afterSuccess(ValueFuture<T> first,
			ValueFuture<U> second,
			Executor executor,
			BiFunction<FutureResult<T>, FutureResult<U>, R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor,
				new BiFunctionAsFutureProducer<>(first, second, listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	public static <T, U> VoidFuture afterSuccess(BiValueFuture<T, U> first,
			VoidFuture second,
			Executor executor,
			BiFutureConsumer<T, U> listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new BiFutureConsumerAsFutureProducer<>(first.getFirst(),
						first.getSecond(),
						listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	public static <T, U> VoidFuture afterSuccess(BiValueFuture<T, U> first,
			VoidFuture second,
			Executor executor,
			BiConsumer<FutureResult<T>, FutureResult<U>> listener) {
		VoidFutureStep step = new VoidFutureStep(executor,
				new BiConsumerAsFutureProducer<>(first.getFirst(), first.getSecond(), listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	public static <T, U, R> ValueFuture<R> afterSuccess(BiValueFuture<T, U> first,
			VoidFuture second,
			Executor executor,
			BiFutureTransformer<T, U, R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor,
				new BiFutureTransformerAsFutureProducer<>(first.getFirst(),
						first.getSecond(),
						listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	public static <T, U, R> ValueFuture<R> afterSuccess(BiValueFuture<T, U> first,
			VoidFuture second,
			Executor executor,
			BiFunction<FutureResult<T>, FutureResult<U>, R> listener) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor,
				new BiFunctionAsFutureProducer<>(first.getFirst(), first.getSecond(), listener));
		step.setPrerequisites(first, second, PrereqStrategy.ALL_PREREQS_SUCCEED);
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
		ValueFutureStep<List<R>> step = new ValueFutureStep<>(getDirectExecutor(),
				new FutureProducer<List<R>>() {
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
		ValueFutureStep<List<R>> step = new ValueFutureStep<>(getDirectExecutor(),
				new FutureProducer<List<R>>() {
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
		ValueFutureStep<List<R>> step = new ValueFutureStep<>(getDirectExecutor(),
				new FutureProducer<List<R>>() {
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
		ValueFutureStep<List<R>> step = new ValueFutureStep<>(getDirectExecutor(),
				new FutureProducer<List<R>>() {
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
		ValueFutureStep<List<R>> step = new ValueFutureStep<>(getDirectExecutor(),
				new FutureProducer<List<R>>() {
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
		ValueFutureStep<List<R>> step = new ValueFutureStep<>(getDirectExecutor(),
				new FutureProducer<List<R>>() {
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


}
