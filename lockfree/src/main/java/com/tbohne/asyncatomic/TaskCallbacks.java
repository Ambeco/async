package com.tbohne.asyncatomic;

/**
 * Various listener types that can be attached to futures.
 * For listeners that produce values, there are also simple base classes that merely propagate
 * exceptions, for easier chaining.  These helpers deliberately not provided for non-returning
 * listeners, to affirm that all exceptions are explicitly handled.
 */
public class TaskCallbacks {
	private TaskCallbacks() {}

	public interface SideEffectTask {
		void onSuccess();

		void onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	public interface ProducerTask<R> {
		R onSuccess();

		R onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	public interface ConsumerTask<T> {
		void onSuccess(T result);

		void onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	public interface TransformerTask<T, R> {
		R onSuccess(T result);

		R onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	public interface BiConsumerTask<T, U> {
		void onSuccess(T first, U second);

		void onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	public interface BiTransformerTask<T, U, R> {
		R onSuccess(T result, U second);

		R onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	public abstract static class SimpleProducerTask<R> implements ProducerTask<R> {
		public R onFailure(RuntimeException t) {
			throw t;
		}
	}

	public abstract static class SimpleTransfomerTask<T, R> implements TransformerTask<T, R> {
		public R onFailure(RuntimeException t) {
			throw t;
		}
	}

	public abstract static class SimpleBiTransfomerTask<T, U, R>
			implements BiTransformerTask<T, U, R> {
		public R onFailure(RuntimeException t) {
			throw t;
		}
	}
}
