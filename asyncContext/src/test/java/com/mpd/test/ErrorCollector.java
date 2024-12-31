package com.mpd.test;

import static org.junit.Assert.assertThrows;

import org.junit.function.ThrowingRunnable;

public class ErrorCollector extends org.junit.rules.ErrorCollector {

	public <E extends Throwable> E checkThrowsAndGet(Class<E> expectedThrowable, ThrowingRunnable runnable) {
		try {
			return assertThrows(expectedThrowable, runnable);
		} catch (AssertionError e) {
			addError(e);
			return null;
		}
	}
}