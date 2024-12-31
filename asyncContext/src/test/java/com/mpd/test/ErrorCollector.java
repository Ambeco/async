package com.mpd.test;

import static org.junit.Assert.assertThrows;

import org.hamcrest.Matcher;
import org.junit.function.ThrowingRunnable;

public class ErrorCollector extends org.junit.rules.ErrorCollector {

	public <E extends Throwable> void checkThrows(
			Class<E> expectedThrowable, ThrowingRunnable runnable, Matcher<E> matcher)
	{
		try {
			checkThat(assertThrows(expectedThrowable, runnable), matcher);
		} catch (AssertionError e) {
			addError(e);
		}
	}
}
