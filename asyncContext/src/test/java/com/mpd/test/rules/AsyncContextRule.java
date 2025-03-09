package com.mpd.test.rules;

import com.mpd.concurrent.asyncContext.AsyncContextScope;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class AsyncContextRule implements TestRule {
	@Override public Statement apply(Statement base, Description description) {
		return new Statement() {
			@Override public void evaluate() throws Throwable {
				try (AsyncContextScope ignored = AsyncContextScope.newRootScope(description.getDisplayName())) {
					base.evaluate();
				}
			}
		};
	}
}
