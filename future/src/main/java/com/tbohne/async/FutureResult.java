package com.tbohne.async;

public interface FutureResult<R> {
	boolean succeeded();
	RuntimeException getThrownException();
	R getNow(); //nonblocking, throws RuntimeException
}
