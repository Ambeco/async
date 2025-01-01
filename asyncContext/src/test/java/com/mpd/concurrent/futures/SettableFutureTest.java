package com.mpd.concurrent.futures;

import static org.hamcrest.CoreMatchers.equalTo;

import com.mpd.concurrent.futures.Future.FutureNotCompleteException;
import com.mpd.test.ErrorCollector;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class SettableFutureTest {
	@Rule public ErrorCollector collector = new ErrorCollector();

	@Test public void construct_incomplete() {
		SettableFuture<String> fut = new SettableFuture<>();
		fut.end();

		collector.checkThrows(FutureNotCompleteException.class, fut::resultNow);
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(false));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThrows(FutureNotCompleteException.class, fut::exceptionNow);
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(TimeUnit.MILLISECONDS));
	}
}