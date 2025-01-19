package com.mpd.concurrent.futures;

import static android.util.Log.DEBUG;
import static java.util.concurrent.TimeUnit.DAYS;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.internal.matchers.ThrowableCauseMatcher.hasCause;

import com.mpd.concurrent.futures.Future.AsyncCheckedException;
import com.mpd.concurrent.futures.Future.FutureNotCompleteException;
import com.mpd.test.AsyncContextRule;
import com.mpd.test.ErrorCollector;
import com.tbohne.android.flogger.backend.AndroidBackend;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class) public class SettableFutureTest {
	@Rule public ErrorCollector collector = new ErrorCollector();
	@Rule public AsyncContextRule asyncContextRule = new AsyncContextRule();

	@Before public void enableDebugLogging() {
		AndroidBackend.setLogLevelOverride(DEBUG);
	}

	@Test public void construct_incomplete() throws Throwable {
		SettableFuture<String> fut = new SettableFuture<>();
		fut.end();

		collector.checkThrows(FutureNotCompleteException.class, fut::resultNow);
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(false));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThrows(FutureNotCompleteException.class, fut::exceptionNow);
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(TimeUnit.MILLISECONDS));
	}

	@Test public void toString_afterConstructed_isCorrect() throws Throwable {
		SettableFuture<String> fut = new SettableFuture<>();
		fut.end();

		collector.checkThat(fut.toString(), matchesPattern("SettableFuture@\\d{1,20}\\[ state=unset]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 10);
		collector.checkThat(
				sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.SettableFuture\\("
						+ "SettableFuture:0\\) //SettableFuture@\\d{1,20}\\[ state=unset]$"));
	}

	@Test public void setResult_isSuccessful() throws Throwable {
		SettableFuture<String> fut = new SettableFuture<>();
		fut.end();

		fut.setResult("test");

		collector.checkThat(fut.resultNow(), equalTo("test"));
		collector.checkThat(fut.isSuccessful(), equalTo(true));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), nullValue());
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(TimeUnit.MILLISECONDS));
		collector.checkThat(fut.get(), equalTo("test"));
		collector.checkThat(fut.get(1, DAYS), equalTo("test"));
	}

	@Test public void toString_afterSuccess_isCorrect() throws Throwable {
		SettableFuture<String> fut = new SettableFuture<>();
		fut.end();

		fut.setResult("test");

		collector.checkThat(fut.toString(), matchesPattern("SettableFuture@\\d{1,20}\\[ success=test]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 10);
		collector.checkThat(
				sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.SettableFuture\\("
						+ "SettableFuture:0\\) //SettableFuture@\\d{1,20}\\[ success=test]$"));
	}

	@Test public void setException_UncheckedException_isFailed() throws Throwable {
		SettableFuture<String> fut = new SettableFuture<>();
		fut.catching(ArithmeticException.class, e -> null).end();

		ArithmeticException e = new ArithmeticException("test");
		fut.setException(e);

		collector.checkThrows(ArithmeticException.class, fut::resultNow);
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(TimeUnit.MILLISECONDS));
		collector.checkThrows(ArithmeticException.class, fut::get);
		collector.checkThrows(ArithmeticException.class, () -> fut.get(1, DAYS));
	}

	@Test public void toString_afterUncheckedException_isCorrect() throws Throwable {
		SettableFuture<String> fut = new SettableFuture<>();
		fut.catching(ArithmeticException.class, e -> null).end();

		ArithmeticException e = new ArithmeticException("test");
		fut.setException(e);

		collector.checkThat(
				fut.toString(),
				matchesPattern("SettableFuture@\\d{1,20}\\[ failure=java.lang.ArithmeticException: test]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 10);
		collector.checkThat(
				sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.SettableFuture\\("
						+ "SettableFuture:0\\) //SettableFuture@\\d{1,20}\\[ failure=java.lang.ArithmeticException: test]$"));
	}

	@Test public void setException_checkedException_isFailed() throws Throwable {
		SettableFuture<String> fut = new SettableFuture<>();
		fut.catching(IOException.class, e -> null).end();

		IOException e = new IOException("test");
		fut.setException(e);

		collector.checkThrows(AsyncCheckedException.class, fut::resultNow, hasCause(sameInstance(e)));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(TimeUnit.MILLISECONDS));
		collector.checkThrows(AsyncCheckedException.class, fut::get, hasCause(sameInstance(e)));
		collector.checkThrows(AsyncCheckedException.class, () -> fut.get(1, DAYS), hasCause(sameInstance(e)));
	}

	@Test public void toString_afterCheckedException_isCorrect() throws Throwable {
		SettableFuture<String> fut = new SettableFuture<>();
		fut.catching(IOException.class, e -> null).end();

		IOException e = new IOException("test");
		fut.setException(e);

		collector.checkThat(
				fut.toString(),
				matchesPattern("SettableFuture@\\d{1,20}\\[ failure=java.io.IOException: test]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 10);
		collector.checkThat(
				sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.SettableFuture\\("
						+ "SettableFuture:0\\) //SettableFuture@\\d{1,20}\\[ failure=java.io.IOException: test]$"));
	}

	@Test public void setException_cancelledException_isFailed() throws Throwable {
		SettableFuture<String> fut = new SettableFuture<>();
		fut.end();

		CancellationException e = new CancellationException("test");
		fut.setException(e);

		collector.checkThrows(CancellationException.class, fut::resultNow);
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(true));
		collector.checkThat(fut.exceptionNow(), sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(TimeUnit.MILLISECONDS));
		collector.checkThrows(CancellationException.class, fut::get);
		collector.checkThrows(CancellationException.class, () -> fut.get(1, DAYS));
	}

	@Test public void toString_afterCancelledException_isCorrect() throws Throwable {
		SettableFuture<String> fut = new SettableFuture<>();
		fut.end();

		CancellationException e = new CancellationException("test");
		fut.setException(e);

		collector.checkThat(
				fut.toString(),
				matchesPattern("SettableFuture@\\d{1,20}\\[ cancelled=java.util.concurrent.CancellationException: test]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 10);
		collector.checkThat(
				sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.SettableFuture\\("
						+ "SettableFuture:0\\) //SettableFuture@\\d{1,20}\\[ cancelled=java.util.concurrent.CancellationException: test]$"));
	}
}