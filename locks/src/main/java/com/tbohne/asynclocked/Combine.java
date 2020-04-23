package com.tbohne.asynclocked;

import com.tbohne.asynclocked.Future.FutureListener;
import com.tbohne.asynclocked.TaskCallbacks.BiConsumerTask;
import com.tbohne.asynclocked.TaskCallbacks.BiTransformerTask;
import com.tbohne.asynclocked.TaskCallbacks.ConsumerTask;
import com.tbohne.asynclocked.TaskCallbacks.ProducerTask;
import com.tbohne.asynclocked.TaskCallbacks.SideEffectTask;
import com.tbohne.asynclocked.TaskCallbacks.TransformerTask;
import com.tbohne.asynclocked.impl.FutureProducers.BiConsumerAsFutureProducer;
import com.tbohne.asynclocked.impl.FutureProducers.BiFunctionAsFutureProducer;
import com.tbohne.asynclocked.impl.FutureProducers.BiFutureConsumerAsFutureProducer;
import com.tbohne.asynclocked.impl.FutureProducers.BiFutureTransformerAsFutureProducer;
import com.tbohne.asynclocked.impl.FutureProducers.ConsumerAsFutureProducer;
import com.tbohne.asynclocked.impl.FutureProducers.FunctionAsFutureProducer;
import com.tbohne.asynclocked.impl.FutureProducers.FutureEffectAsFutureProducer;
import com.tbohne.asynclocked.impl.FutureProducers.ToListTask;
import com.tbohne.asynclocked.impl.FutureProducers.ValueConsumerAsFutureProducer;
import com.tbohne.asynclocked.impl.FutureProducers.ValueTransformerAsFutureProducer;
import com.tbohne.asynclocked.impl.QueueableFutureTask;
import com.tbohne.asynclocked.impl.QueueableValueFuture;
import com.tbohne.asynclocked.impl.QueueableVoidFuture;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.tbohne.asynclocked.DirectExecutor.getDirectExecutor;
import static com.tbohne.asynclocked.impl.FutureProducers.NO_OP_VOID_CALLBACK;
import static com.tbohne.asynclocked.impl.QueueableFutureTask.toSet;

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
		return new QueueableVoidFuture(PrereqStrategy.ALL_PREREQS_SUCCEED,
				QueueableFutureTask.toSet(future),
				getDirectExecutor(),
				NO_OP_VOID_CALLBACK);
	}

	public static <T> VoidFuture ignore(ValueFuture<T> future,
			Executor executor,
			ProducerTask<Void> followup) {
		return new QueueableVoidFuture(PrereqStrategy.ALL_PREREQS_SUCCEED,
				QueueableFutureTask.toSet(future),
				executor,
				followup);
	}

	public static <T> VoidFuture ignore(ValueFuture<T> future,
			Executor executor,
			SideEffectTask followup) {
		return new QueueableVoidFuture(PrereqStrategy.ALL_PREREQS_SUCCEED,
				QueueableFutureTask.toSet(future),
				executor,
				new FutureEffectAsFutureProducer(followup));
	}

	public static <T, U> VoidFuture ignore(BiValueFuture<T, U> future) {
		return new QueueableVoidFuture(PrereqStrategy.ALL_PREREQS_SUCCEED,
				QueueableFutureTask.toSet(future),
				getDirectExecutor(),
				NO_OP_VOID_CALLBACK);
	}

	public static <T, U> VoidFuture ignore(BiValueFuture<T, U> future,
			Executor executor,
			ProducerTask<Void> followup) {
		return new QueueableVoidFuture(PrereqStrategy.ALL_PREREQS_SUCCEED,
				QueueableFutureTask.toSet(future),
				executor,
				followup);
	}

	public static <T, U> VoidFuture ignore(BiValueFuture<T, U> future,
			Executor executor,
			SideEffectTask followup) {
		return new QueueableVoidFuture(PrereqStrategy.ALL_PREREQS_SUCCEED,
				QueueableFutureTask.toSet(future),
				executor,
				new FutureEffectAsFutureProducer(followup));
	}


	public static Future thenDo(VoidFuture future, FutureListener listener) {
		return future.addListener(listener);
	}

	public static VoidFuture thenDo(VoidFuture future, Executor executor, SideEffectTask listener) {
		return new QueueableVoidFuture(PrereqStrategy.ALL_PREREQS_SUCCEED,
				QueueableFutureTask.toSet(future),
				executor,
				new FutureEffectAsFutureProducer(listener));
	}

	public static <R> ValueFuture<R> thenDo(VoidFuture future,
			Executor executor,
			ProducerTask<R> listener) {
		return new QueueableValueFuture<>(PrereqStrategy.ALL_PREREQS_SUCCEED,
				QueueableFutureTask.toSet(future),
				executor,
				listener);
	}

	public static <T> VoidFuture thenDo(ValueFuture<T> future,
			Executor executor,
			ConsumerTask<T> listener) {
		return new QueueableVoidFuture(PrereqStrategy.ALL_PREREQS_SUCCEED,
				QueueableFutureTask.toSet(future),
				executor,
				new ValueConsumerAsFutureProducer<>(future, listener));
	}

	public static <T> VoidFuture thenDo(ValueFuture<T> future,
			Executor executor,
			Consumer<FutureResult<T>> listener) {
		return new QueueableVoidFuture(PrereqStrategy.ALL_PREREQS_SUCCEED,
				QueueableFutureTask.toSet(future),
				executor,
				new ConsumerAsFutureProducer<>(future, listener));
	}

	public static <T, R> ValueFuture<R> thenDo(ValueFuture<T> future,
			Executor executor,
			TransformerTask<T, R> listener) {
		return new QueueableValueFuture<>(PrereqStrategy.ALL_PREREQS_SUCCEED,
				QueueableFutureTask.toSet(future),
				executor,
				new ValueTransformerAsFutureProducer<>(future, listener));
	}

	public static <T, R> ValueFuture<R> thenDo(ValueFuture<T> future,
			Executor executor,
			Function<FutureResult<T>, R> listener) {
		return new QueueableValueFuture<>(PrereqStrategy.ALL_PREREQS_SUCCEED,
				QueueableFutureTask.toSet(future),
				executor,
				new FunctionAsFutureProducer<>(future, listener));
	}

	public static <T, U> VoidFuture thenDo(BiValueFuture<T, U> future,
			Executor executor,
			BiConsumerTask<T, U> listener) {
		return new QueueableVoidFuture(PrereqStrategy.ALL_PREREQS_SUCCEED,
				QueueableFutureTask.toSet(future),
				executor,
				new BiFutureConsumerAsFutureProducer<>(future.getFirst(),
						future.getSecond(),
						listener));
	}

	public static <T, U> VoidFuture thenDo(BiValueFuture<T, U> future,
			Executor executor,
			BiConsumer<FutureResult<T>, FutureResult<U>> listener) {
		return new QueueableVoidFuture(PrereqStrategy.ALL_PREREQS_SUCCEED,
				QueueableFutureTask.toSet(future),
				executor,
				new BiConsumerAsFutureProducer<>(future.getFirst(), future.getSecond(), listener));
	}

	public static <T, U, R> ValueFuture<R> thenDo(BiValueFuture<T, U> future,
			Executor executor,
			BiTransformerTask<T, U, R> listener) {
		return new QueueableValueFuture<>(PrereqStrategy.ALL_PREREQS_SUCCEED,
				QueueableFutureTask.toSet(future),
				executor,
				new BiFutureTransformerAsFutureProducer<>(future.getFirst(),
						future.getSecond(),
						listener));
	}

	public static <T, U, R> ValueFuture<R> thenDo(BiValueFuture<T, U> future,
			Executor executor,
			BiFunction<FutureResult<T>, FutureResult<U>, R> listener) {
		return new QueueableValueFuture<>(PrereqStrategy.ALL_PREREQS_SUCCEED,
				QueueableFutureTask.toSet(future),
				executor,
				new BiFunctionAsFutureProducer<>(future.getFirst(), future.getSecond(), listener));
	}


	public static VoidFuture afterBoth(VoidFuture first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			SideEffectTask listener) {
		return new QueueableVoidFuture(prereqStrategy,
				QueueableFutureTask.toSet(first, second),
				executor,
				new FutureEffectAsFutureProducer(listener));
	}

	public static <R> ValueFuture<R> afterComplete(VoidFuture first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			ProducerTask<R> listener) {
		return new QueueableValueFuture<>(prereqStrategy,
				QueueableFutureTask.toSet(first, second),
				executor,
				listener);
	}

	public static <T> VoidFuture afterBoth(ValueFuture<T> first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			ConsumerTask<T> listener) {
		return new QueueableVoidFuture(prereqStrategy,
				QueueableFutureTask.toSet(first, second),
				executor,
				new ValueConsumerAsFutureProducer<>(first, listener));
	}

	public static <T> VoidFuture afterBoth(ValueFuture<T> first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			Consumer<FutureResult<T>> listener) {
		return new QueueableVoidFuture(prereqStrategy,
				QueueableFutureTask.toSet(first, second),
				executor,
				new ConsumerAsFutureProducer<>(first, listener));
	}

	public static <T, R> ValueFuture<R> afterBoth(ValueFuture<T> first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			TransformerTask<T, R> listener) {
		return new QueueableValueFuture<>(prereqStrategy,
				QueueableFutureTask.toSet(first, second),
				executor,
				new ValueTransformerAsFutureProducer<>(first, listener));
	}

	public static <T, R> ValueFuture<R> afterBoth(ValueFuture<T> first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			Function<FutureResult<T>, R> listener) {
		return new QueueableValueFuture<>(prereqStrategy,
				QueueableFutureTask.toSet(first, second),
				executor,
				new FunctionAsFutureProducer<>(first, listener));
	}

	public static <T, U> VoidFuture afterBoth(ValueFuture<T> first,
			ValueFuture<U> second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			BiConsumerTask<T, U> listener) {
		return new QueueableVoidFuture(prereqStrategy,
				QueueableFutureTask.toSet(first, second),
				executor,
				new BiFutureConsumerAsFutureProducer<>(first, second, listener));
	}

	public static <T, U> VoidFuture afterBoth(ValueFuture<T> first,
			ValueFuture<U> second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			BiConsumer<FutureResult<T>, FutureResult<U>> listener) {
		return new QueueableVoidFuture(prereqStrategy,
				QueueableFutureTask.toSet(first, second),
				executor,
				new BiConsumerAsFutureProducer<>(first, second, listener));
	}

	public static <T, U, R> ValueFuture<R> afterBoth(ValueFuture<T> first,
			ValueFuture<U> second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			BiTransformerTask<T, U, R> listener) {
		return new QueueableValueFuture<>(prereqStrategy,
				QueueableFutureTask.toSet(first, second),
				executor,
				new BiFutureTransformerAsFutureProducer<>(first, second, listener));
	}

	public static <T, U, R> ValueFuture<R> afterBoth(ValueFuture<T> first,
			ValueFuture<U> second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			BiFunction<FutureResult<T>, FutureResult<U>, R> listener) {
		return new QueueableValueFuture<>(prereqStrategy,
				QueueableFutureTask.toSet(first, second),
				executor,
				new BiFunctionAsFutureProducer<>(first, second, listener));
	}

	public static <T, U> VoidFuture afterBoth(BiValueFuture<T, U> first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			BiConsumerTask<T, U> listener) {
		return new QueueableVoidFuture(prereqStrategy,
				QueueableFutureTask.toSet(first, second),
				executor,
				new BiFutureConsumerAsFutureProducer<>(first.getFirst(),
						first.getSecond(),
						listener));
	}

	public static <T, U> VoidFuture afterBoth(BiValueFuture<T, U> first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			BiConsumer<FutureResult<T>, FutureResult<U>> listener) {
		return new QueueableVoidFuture(prereqStrategy,
				QueueableFutureTask.toSet(first, second),
				executor,
				new BiConsumerAsFutureProducer<>(first.getFirst(), first.getSecond(), listener));
	}

	public static <T, U, R> ValueFuture<R> afterBoth(BiValueFuture<T, U> first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			BiTransformerTask<T, U, R> listener) {
		return new QueueableValueFuture<>(prereqStrategy,
				QueueableFutureTask.toSet(first, second),
				executor,
				new BiFutureTransformerAsFutureProducer<>(first.getFirst(),
						first.getSecond(),
						listener));
	}

	public static <T, U, R> ValueFuture<R> afterBoth(BiValueFuture<T, U> first,
			VoidFuture second,
			PrereqStrategy prereqStrategy,
			Executor executor,
			BiFunction<FutureResult<T>, FutureResult<U>, R> listener) {
		return new QueueableValueFuture<>(prereqStrategy,
				QueueableFutureTask.toSet(first, second),
				executor,
				new BiFunctionAsFutureProducer<>(first.getFirst(), first.getSecond(), listener));
	}


	public static VoidFuture afterAllVoid(PrereqStrategy prereqStrategy, VoidFuture... futures) {
		return new QueueableVoidFuture(prereqStrategy,
				toSet(futures),
				getDirectExecutor(),
				NO_OP_VOID_CALLBACK);
	}

	public static VoidFuture afterAllVoid(PrereqStrategy prereqStrategy,
			Collection<VoidFuture> futures) {
		return new QueueableVoidFuture(prereqStrategy,
				new HashSet<>(futures),
				getDirectExecutor(),
				NO_OP_VOID_CALLBACK);
	}

	@SafeVarargs
	public static <R> ValueFuture<List<R>> afterAll(PrereqStrategy prereqStrategy,
			ValueFuture<R>... futures) {
		return new QueueableValueFuture<>(prereqStrategy,
				toSet(futures),
				getDirectExecutor(),
				new ToListTask<>(Arrays.asList(futures)));
	}

	public static <R> ValueFuture<List<R>> afterAll(PrereqStrategy prereqStrategy,
			Collection<ValueFuture<R>> futures) {
		return new QueueableValueFuture<>(prereqStrategy,
				new HashSet<>(futures),
				getDirectExecutor(),
				new ToListTask<>(futures));
	}
}
