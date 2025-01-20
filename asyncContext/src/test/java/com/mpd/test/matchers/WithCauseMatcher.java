package com.mpd.test.matchers;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class WithCauseMatcher<ThrownT extends Throwable> extends TypeSafeMatcher<ThrownT> {

	private final Matcher<? extends Throwable> matcher;

	public WithCauseMatcher(Matcher<? extends Throwable> matcher) {
		this.matcher = matcher;
	}

	public static <ThrownT extends Throwable> WithCauseMatcher<ThrownT> withCause(Matcher<? extends Throwable> matcher) {
		return new WithCauseMatcher<>(matcher);
	}

	@Override public void describeTo(Description description) {
		description.appendText("with Cause ");
		matcher.describeTo(description);
	}

	@Override protected boolean matchesSafely(Throwable e) {
		if (e == null) {
			return false;
		}
		Throwable cause = e.getCause();
		if (cause == null) {
			return false;
		} else {
			return matcher.matches(cause);
		}
	}

	@Override protected void describeMismatchSafely(ThrownT item, Description mismatchDescription) {
		mismatchDescription.appendText("was \"").appendValue(item).appendValue("\" with Cause ").appendValue(item != null
				? item.getCause()
				: null);
	}
}
