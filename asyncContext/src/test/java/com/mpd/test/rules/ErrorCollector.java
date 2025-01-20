package com.mpd.test.rules;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.concurrent.Callable;
import org.hamcrest.Matcher;
import org.junit.function.ThrowingRunnable;

public class ErrorCollector extends org.junit.rules.ErrorCollector {

	public <T> T checkSucceeds(Callable<T> callable, Matcher<T> matcher) {
		return checkSucceeds("", callable, matcher);
	}

	public <T> T checkSucceeds(String reason, Callable<T> callable, Matcher<T> matcher) {
		return checkSucceeds(() -> {
			T value = callable.call();
			assertThat(reason, value, matcher);
			return value;
		});
	}

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
