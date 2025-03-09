package com.mpd.test.rules;

import androidx.test.espresso.IdlingRegistry;
import com.mpd.test.ExecutorsIdlingResource;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class RegisterIdlingResourceRule implements TestRule {
	@Override public Statement apply(Statement base, Description description) {
		return new Statement() {
			@Override public void evaluate() throws Throwable {
				ExecutorsIdlingResource executorsIdlingResource = new ExecutorsIdlingResource();
				IdlingRegistry.getInstance().register(executorsIdlingResource);
				try {
					base.evaluate();
				} finally {
					IdlingRegistry.getInstance().unregister(executorsIdlingResource);
				}
			}
		};
	}
}
