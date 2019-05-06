package com.tbohne.async;

import com.tbohne.async.VoidFuture.FutureListener;

import java.util.List;

public interface Future {
	boolean finished();
	boolean succeeded();
	RuntimeException getThrownException();
	boolean isCancelled();
	boolean cancel();
	void callbackWasCancelled(FutureListener callback);
	void fillStackTraces(List<StackTraceElement[]> stacks);

	boolean isPrerequisite(Future future);
	<T extends Future & FutureListener> T then(T followup); //if this is not already a prerequisite, throws IllegalStateException
	VoidFuture childrenCannotCancel(); //Calling Future can only be canceled if the returned future is cancelled.
}