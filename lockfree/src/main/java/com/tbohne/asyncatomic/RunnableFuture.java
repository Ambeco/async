package com.tbohne.asyncatomic;

/**
 * A Future that has a .run() method that completes the future
 * <p>
 * RunnableFuture.run should never throw an exception, it should set the exception as the result
 * of the Future. It should always complete the future, regardless. If either of these are violated,
 * the Executor itself may throw an {@link IllegalStateException}.
 * <p>
 * This is useful for queueing future work in Executors without creating more futures and executors
 * recursively. In fact, queuing future work usually needs 0 allocations due to this concept.
 */
public interface RunnableFuture extends Runnable, Future {}
