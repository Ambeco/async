package com.tbohne.async;

import java.util.List;
import java.util.concurrent.CancellationException;

public interface Future {
	boolean finished();

	boolean succeeded();

	RuntimeException getThrownException();

	boolean isCancelled();

	boolean cancel(CancellationException exception);

	void callbackWasCancelled(FutureListener callback, CancellationException exception);

	void fillStackTraces(List<StackTraceElement[]> stacks);

	boolean isPrerequisite(Future future);

	void addListener(FutureListener followup); //if this is not already a prerequisite, throws IllegalStateException

	VoidFuture childrenCannotCancel(); //Calling Future can only be canceled if the returned future is cancelled.
}