package com.tbohne.async;

public interface FutureResult<R> {
	boolean succeeded();
	R getNow(); //nonblocking, throws RuntimeException
}
