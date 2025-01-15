package com.mpd.test;

import com.mpd.concurrent.asyncContext.AsyncContext;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class AsyncContextRule implements TestRule {
	@Override public Statement apply(Statement base, Description description) {
		return new Statement() {
			@Override public void evaluate() throws Throwable {
				AsyncContext context = AsyncContext.setNewRootContext(description.getDisplayName());
				try {
					base.evaluate();
				} finally {
					AsyncContext.pauseExecutionContext(context, null);
				}
			}
		};
	}
}
