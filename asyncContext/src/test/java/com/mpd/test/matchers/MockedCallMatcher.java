package com.mpd.test.matchers;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;

import com.mpd.test.MockedCall;
import java.lang.reflect.Method;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class MockedCallMatcher<T> extends TypeSafeMatcher<MockedCall<T>> {
	public final Matcher<? super T> object;
	public final Matcher<?> result;
	public final Method method;
	public final Matcher<?>[] args;

	public MockedCallMatcher(Method method, Matcher<?> result, Matcher<?>... args) {
		this(notNullValue(), method, result, args);
	}

	public MockedCallMatcher(T object, Method method, Matcher<?> result, Matcher<?>... args) {
		this(sameInstance(object), method, result, args);
	}

	public MockedCallMatcher(Matcher<? super T> object, Method method, Matcher<?> result, Matcher<?>... args) {
		this.object = object;
		this.result = result;
		this.method = method;
		this.args = args;
	}

	@Override protected boolean matchesSafely(MockedCall<T> call) {
		if (method != call.method || args.length != call.args.length || !object.matches(call.object)) {
			return false;
		}
		for (int i = 0; i < args.length; i++) {
			if (!args[i].matches(call.args[i])) {
				return false;
			}
		}
		return true;
	}

	@Override public void describeTo(Description description) {
		description.appendText("Expected method call ");
		description.appendText(method.toGenericString());
		description.appendText(" on ");
		object.describeTo(description);
		description.appendText("\nwith (");
		for (int i = 0; i < args.length; i++) {
			args[i].describeTo(description);
		}
	}
}
