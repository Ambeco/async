package com.mpd.test.rules;

import com.google.common.flogger.FluentLogger;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class LogExecutionRule extends TestWatcher {
	private static final FluentLogger log = FluentLogger.forEnclosingClass();


	@Override protected void starting(Description description) {
		log.atInfo().log("executing %s", description.getDisplayName());
	}

	@Override protected void finished(Description description) {
		log.atInfo().log("finished execution of test %s", description.getDisplayName());
	}
}
