package com.tbohne.async;

import com.tbohne.async.Future.FutureListener;
import com.tbohne.async.TaskCallbacks.BiConsumerTask;
import com.tbohne.async.TaskCallbacks.BiTransformerTask;
import com.tbohne.async.TaskCallbacks.ConsumerTask;
import com.tbohne.async.TaskCallbacks.ProducerTask;
import com.tbohne.async.TaskCallbacks.SideEffectTask;
import com.tbohne.async.TaskCallbacks.TransformerTask;
import com.tbohne.async.impl.FutureProducers.BiConsumerAsFutureProducer;
import com.tbohne.async.impl.FutureProducers.BiFunctionAsFutureProducer;
import com.tbohne.async.impl.FutureProducers.BiFutureConsumerAsFutureProducer;
import com.tbohne.async.impl.FutureProducers.BiFutureTransformerAsFutureProducer;
import com.tbohne.async.impl.FutureProducers.ConsumerAsFutureProducer;
import com.tbohne.async.impl.FutureProducers.FunctionAsFutureProducer;
import com.tbohne.async.impl.FutureProducers.FutureEffectAsFutureProducer;
import com.tbohne.async.impl.FutureProducers.ValueConsumerAsFutureProducer;
import com.tbohne.async.impl.FutureProducers.ValueTransformerAsFutureProducer;
import com.tbohne.async.impl.QueueableValueFuture;
import com.tbohne.async.impl.QueueableVoidFuture;

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
		QueueableVoidFuture step = new QueueableVoidFuture(getDirectExecutor(),
				NO_OP_VOID_CALLBACK);
		step.setPrerequisites(future);
		return step;
	}

	public static <T> VoidFuture ignore(ValueFuture<T> future,
			Executor executor,
			ProducerTask<Void> followup) {
		QueueableVoidFuture step = new QueueableVoidFuture(executor, followup);
		step.setPrerequisites(future);
		return step;
	}

	public static <T> VoidFuture ignore(ValueFuture<T> future,
			Executor executor,
			SideEffectTask followup) {
		QueueableVoidFuture step = new QueueableVoidFuture(executor,
				new FutureEffectAsFutureProducer(followup));
		step.setPrerequisites(future);
		return step;
	}

	public static <T, U> VoidFuture ignore(BiValueFuture<T, U> future) {
		QueueableVoidFuture step = new QueueableVoidFuture(getDirectExecutor(),
				NO_OP_VOID_CALLBACK);
		step.setPrerequisites(future);
		return step;
	}

	public static <T, U> VoidFuture ignore(BiValueFuture<T, U> future,
			Executor executor,
			ProducerTask<Void> followup) {
		QueueableVoidFuture step = new QueueableVoidFuture(executor, followup);
		step.setPrerequisites(future);
		return step;
	}

	public static <T, U> VoidFuture ignore(BiValueFuture<T, U> future,
			Executor executor,
			SideEffectTask followup) {
		QueueableVoidFuture step = new QueueableVoidFuture(executor,
				new FutureEffectAsFutureProducer(followup));
		step.setPrerequisites(future);
		return step;
	}


	public static Future afterComplete(VoidFuture future, FutureListener listener) {
		return future.addListener(listener);
	}

	public static VoidFuture afterComplete(VoidFuture future,
			Executor executor,
			SideEffectTask listener) {
		QueueableVoidFuture step = new QueueableVoidFuture(executor,
				new FutureEffectAsFutureProducer(listener));
		step.setPrerequisites(future);
		return step;
	}

	public static <R> ValueFuture<R> afterComplete(VoidFuture future,
			Executor executor,
			ProducerTask<R> listener) {
		QueueableValueFuture<R> step = new QueueableValueFuture<>(executor, listener);
		step.setPrerequisites(future);
		return step;
	}

	public static <T> VoidFuture afterComplete(ValueFuture<T> future,
			Executor executor,
			ConsumerTask<T> listener) {
		QueueableVoidFuture step = new QueueableVoidFuture(executor,
				new ValueConsumerAsFutureProducer<>(future, listener));
		step.setPrerequisites(future);
		return step;
	}

	public static <T> VoidFuture afterComplete(ValueFuture<T> future,
			Executor executor,
			Consumer<FutureResult<T>> listener) {
		QueueableVoidFuture step = new QueueableVoidFuture(executor,
				new ConsumerAsFutureProducer<>(future, listener));
		step.setPrerequisites(future);
		return step;
	}

	public static <T, R> ValueFuture<R> afterComplete(ValueFuture<T> future,
			Executor executor,
			TransformerTask<T, R> listener) {
		QueueableValueFuture<R> step = new QueueableValueFuture<>(executor,
				new ValueTransformerAsFutureProducer<>(future, listener));
		step.setPrerequisites(future);
		return step;
	}

	public static <T, R> ValueFuture<R> afterComplete(ValueFuture<T> future,
			Executor executor,
			Function<FutureResult<T>, R> listener) {
		QueueableValueFuture<R> step = new QueueableValueFuture<>(executor,
				new FunctionAsFutureProducer<>(future, listener));
		step.setPrerequisites(future);
		return step;
	}

	public static <T, U> VoidFuture afterComplete(BiValueFuture<T, U> future,
			Executor executor,
			BiConsumerTask<T, U> listener) {
		QueueableVoidFuture step = new QueueableVoidFuture(executor,
				new BiFutureConsumerAsFutureProducer<>(future.getFirst(),
						future.getSecond(),
						listener));
		step.setPrerequisites(future);
		return step;
	}

	public static <T, U> VoidFuture afterComplete(BiValueFuture<T, U> future,
			Executor executor,
			BiConsumer<FutureResult<T>, FutureResult<U>> listener) {
		QueueableVoidFuture step = new QueueableVoidFuture(executor,
				new BiConsumerAsFutureProducer<>(future.getFirst(), future.getSecond(), listener));
		step.setPrerequisites(future);
		return step;
	}

	public static <T, U, R> ValueFuture<R> afterComplete(BiValueFuture<T, U> future,
			Executor executor,
			BiTransformerTask<T, U, R> listener) {
		QueueableValueFuture<R> step = new QueueableValueFuture<>(executor,
				new BiFutureTransformerAsFutureProducer<>(future.getFirst(),
						future.getSecond(),
						listener));
		step.setPrerequisites(future);
		return step;
	}

	public static <T, U, R> ValueFuture<R> afterComplete(BiValueFuture<T, U> future,
			Executor executor,
			BiFunction<FutureResult<T>, FutureResult<U>, R> listener) {
		QueueableValueFuture<R> step = new QueueableValueFuture<>(executor,
				new BiFunctionAsFutureProducer<>(future.getFirst(), future.getSecond(), listener));
		step.setPrerequisites(future);
		return step;
	}


	public static VoidFuture afterBoth(VoidFuture first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			SideEffectTask listener) {
		QueueableVoidFuture step = new QueueableVoidFuture(executor,
				new FutureEffectAsFutureProducer(listener));
		step.setPrerequisites(first, second, prereqStrategy);
		return step;
	}

	public static <R> ValueFuture<R> afterComplete(VoidFuture first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			ProducerTask<R> listener) {
		QueueableValueFuture<R> step = new QueueableValueFuture<>(executor, listener);
		step.setPrerequisites(first, second, prereqStrategy);
		return step;
	}

	public static <T> VoidFuture afterBoth(ValueFuture<T> first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			ConsumerTask<T> listener) {
		QueueableVoidFuture step = new QueueableVoidFuture(executor,
				new ValueConsumerAsFutureProducer<>(first, listener));
		step.setPrerequisites(first, second, prereqStrategy);
		return step;
	}

	public static <T> VoidFuture afterBoth(ValueFuture<T> first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			Consumer<FutureResult<T>> listener) {
		QueueableVoidFuture step = new QueueableVoidFuture(executor,
				new ConsumerAsFutureProducer<>(first, listener));
		step.setPrerequisites(first, second, prereqStrategy);
		return step;
	}

	public static <T, R> ValueFuture<R> afterBoth(ValueFuture<T> first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			TransformerTask<T, R> listener) {
		QueueableValueFuture<R> step = new QueueableValueFuture<>(executor,
				new ValueTransformerAsFutureProducer<>(first, listener));
		step.setPrerequisites(first, second, prereqStrategy);
		return step;
	}

	public static <T, R> ValueFuture<R> afterBoth(ValueFuture<T> first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			Function<FutureResult<T>, R> listener) {
		QueueableValueFuture<R> step = new QueueableValueFuture<>(executor,
				new FunctionAsFutureProducer<>(first, listener));
		step.setPrerequisites(first, second, prereqStrategy);
		return step;
	}

	public static <T, U> VoidFuture afterBoth(ValueFuture<T> first,
			ValueFuture<U> second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			BiConsumerTask<T, U> listener) {
		QueueableVoidFuture step = new QueueableVoidFuture(executor,
				new BiFutureConsumerAsFutureProducer<>(first, second, listener));
		step.setPrerequisites(first, second, prereqStrategy);
		return step;
	}

	public static <T, U> VoidFuture afterBoth(ValueFuture<T> first,
			ValueFuture<U> second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			BiConsumer<FutureResult<T>, FutureResult<U>> listener) {
		QueueableVoidFuture step = new QueueableVoidFuture(executor,
				new BiConsumerAsFutureProducer<>(first, second, listener));
		step.setPrerequisites(first, second, prereqStrategy);
		return step;
	}

	public static <T, U, R> ValueFuture<R> afterBoth(ValueFuture<T> first,
			ValueFuture<U> second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			BiTransformerTask<T, U, R> listener) {
		QueueableValueFuture<R> step = new QueueableValueFuture<>(executor,
				new BiFutureTransformerAsFutureProducer<>(first, second, listener));
		step.setPrerequisites(first, second, prereqStrategy);
		return step;
	}

	public static <T, U, R> ValueFuture<R> afterBoth(ValueFuture<T> first,
			ValueFuture<U> second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			BiFunction<FutureResult<T>, FutureResult<U>, R> listener) {
		QueueableValueFuture<R> step = new QueueableValueFuture<>(executor,
				new BiFunctionAsFutureProducer<>(first, second, listener));
		step.setPrerequisites(first, second, prereqStrategy);
		return step;
	}

	public static <T, U> VoidFuture afterBoth(BiValueFuture<T, U> first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			BiConsumerTask<T, U> listener) {
		QueueableVoidFuture step = new QueueableVoidFuture(executor,
				new BiFutureConsumerAsFutureProducer<>(first.getFirst(),
						first.getSecond(),
						listener));
		step.setPrerequisites(first, second, prereqStrategy);
		return step;
	}

	public static <T, U> VoidFuture afterBoth(BiValueFuture<T, U> first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			BiConsumer<FutureResult<T>, FutureResult<U>> listener) {
		QueueableVoidFuture step = new QueueableVoidFuture(executor,
				new BiConsumerAsFutureProducer<>(first.getFirst(), first.getSecond(), listener));
		step.setPrerequisites(first, second, prereqStrategy);
		return step;
	}

	public static <T, U, R> ValueFuture<R> afterBoth(BiValueFuture<T, U> first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			BiTransformerTask<T, U, R> listener) {
		QueueableValueFuture<R> step = new QueueableValueFuture<>(executor,
				new BiFutureTransformerAsFutureProducer<>(first.getFirst(),
						first.getSecond(),
						listener));
		step.setPrerequisites(first, second, prereqStrategy);
		return step;
	}

	public static <T, U, R> ValueFuture<R> afterBoth(BiValueFuture<T, U> first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			BiFunction<FutureResult<T>, FutureResult<U>, R> listener) {
		QueueableValueFuture<R> step = new QueueableValueFuture<>(executor,
				new BiFunctionAsFutureProducer<>(first.getFirst(), first.getSecond(), listener));
		step.setPrerequisites(first, second, prereqStrategy);
		return step;
	}



	public static VoidFuture afterAllVoid(PrereqStrategy prereqStrategy, VoidFuture... futures) {
		QueueableVoidFuture step = new QueueableVoidFuture(getDirectExecutor(),
				NO_OP_VOID_CALLBACK);
		HashSet<Future> prerequisites = new HashSet<>(futures.length);
		Collections.addAll(prerequisites, futures);
		step.setPrerequisites(prerequisites, prereqStrategy);
		return step;
	}

	public static VoidFuture afterAllVoid(PrereqStrategy prereqStrategy, Collection<VoidFuture> futures) {
		QueueableVoidFuture step = new QueueableVoidFuture(getDirectExecutor(),
				NO_OP_VOID_CALLBACK);
		step.setPrerequisites(new HashSet<>(futures), prereqStrategy);
		return step;
	}

	@SafeVarargs
	public static <R> ValueFuture<List<R>> afterAll(PrereqStrategy prereqStrategy, ValueFuture<R>... futures) {
		QueueableValueFuture<List<R>> step = new QueueableValueFuture<>(getDirectExecutor(),
				new ProducerTask<List<R>>() {
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
		step.setPrerequisites(prerequisites, prereqStrategy);
		return step;
	}

	public static <R> ValueFuture<List<R>> afterAll(PrereqStrategy prereqStrategy, Collection<ValueFuture<R>> futures) {
		QueueableValueFuture<List<R>> step = new QueueableValueFuture<>(getDirectExecutor(),
				new ProducerTask<List<R>>() {
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
		step.setPrerequisites(new HashSet<>(futures), prereqStrategy);
		return step;
	}
}
