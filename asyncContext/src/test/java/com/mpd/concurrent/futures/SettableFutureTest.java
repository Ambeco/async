package com.mpd.concurrent.futures;

import static android.util.Log.DEBUG;
import static android.util.Log.VERBOSE;
import static com.mpd.concurrent.futures.atomic.AbstractFutureHelper.ensureTestComplete;
import static java.util.concurrent.TimeUnit.DAYS;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.internal.matchers.ThrowableCauseMatcher.hasCause;

import com.mpd.concurrent.futures.Future.AsyncCheckedException;
import com.mpd.concurrent.futures.Future.FutureNotCompleteException;
import com.tbohne.android.flogger.backend.AndroidBackend;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

@RunWith(RobolectricTestRunner.class) public class SettableFutureTest extends TestWithStandardRules {
	@Nullable SettableFuture<String> fut;

	@Before public void enableDebugLogging() {
		AndroidBackend.setLogLevelOverride(DEBUG);
		ShadowLog.setLoggable("atomic", VERBOSE);
		ShadowLog.setLoggable("futures", VERBOSE);
	}

	@After public void ensureFutureComplete() {
		ensureTestComplete(fut);
		this.fut = null;
	}

	@Test public void construct_incomplete() throws Throwable {
		fut = new SettableFuture<>();

		collector.checkThrows(FutureNotCompleteException.class, fut::resultNow);
		collector.checkSucceeds(fut::isSuccessful, equalTo(false));
		collector.checkSucceeds(fut::isDone, equalTo(false));
		collector.checkSucceeds(fut::isCancelled, equalTo(false));
		collector.checkThrows(FutureNotCompleteException.class, fut::exceptionNow);
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(TimeUnit.MILLISECONDS));
	}

	@Test public void toString_afterConstructed_isCorrect() throws Throwable {
		fut = new SettableFuture<>();

		collector.checkSucceeds(fut::toString, matchesPattern("SettableFuture@\\d{1,20}\\[ state=unset]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 10);
		collector.checkThat(
				sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.SettableFuture\\("
						+ "SettableFuture:0\\) //SettableFuture@\\d{1,20}\\[ state=unset]$"));
	}

	@Test public void setResult_isSuccessful() throws Throwable {
		fut = new SettableFuture<>();

		fut.setResult("setResult_isSuccessful");

		collector.checkSucceeds(fut::resultNow, equalTo("setResult_isSuccessful"));
		collector.checkSucceeds(fut::isSuccessful, equalTo(true));
		collector.checkSucceeds(fut::isDone, equalTo(true));
		collector.checkSucceeds(fut::isCancelled, equalTo(false));
		collector.checkSucceeds(fut::exceptionNow, nullValue());
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(TimeUnit.MILLISECONDS));
		collector.checkSucceeds(fut::get, equalTo("setResult_isSuccessful"));
		collector.checkSucceeds(() -> fut.get(1, DAYS), equalTo("setResult_isSuccessful"));
	}

	@Test public void toString_afterSuccess_isCorrect() throws Throwable {
		fut = new SettableFuture<>();

		fut.setResult("toString_afterSuccess_isCorrect");

		collector.checkThat(
				fut.toString(),
				matchesPattern("SettableFuture@\\d{1,20}\\[ success=toString_afterSuccess_isCorrect]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 10);
		collector.checkThat(
				sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.SettableFuture\\("
						+ "SettableFuture:0\\) //SettableFuture@\\d{1,20}\\[ success=toString_afterSuccess_isCorrect]$"));
	}

	@Test public void setException_UncheckedException_isFailed() throws Throwable {
		fut = new SettableFuture<>();

		ArithmeticException e = new ArithmeticException("setException_UncheckedException_isFailed");
		fut.setException(e);

		collector.checkThrows(ArithmeticException.class, fut::resultNow);
		collector.checkSucceeds(fut::isSuccessful, equalTo(false));
		collector.checkSucceeds(fut::isDone, equalTo(true));
		collector.checkSucceeds(fut::isCancelled, equalTo(false));
		collector.checkSucceeds(fut::exceptionNow, sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(TimeUnit.MILLISECONDS));
		collector.checkThrows(ArithmeticException.class, fut::get);
		collector.checkThrows(ArithmeticException.class, () -> fut.get(1, DAYS));
	}

	@Test public void toString_afterUncheckedException_isCorrect() throws Throwable {
		fut = new SettableFuture<>();

		ArithmeticException e = new ArithmeticException("toString_afterUncheckedException_isCorrect");
		fut.setException(e);

		collector.checkThat(
				fut.toString(),
				matchesPattern(
						"SettableFuture@\\d{1,20}\\[ failure=java.lang.ArithmeticException: toString_afterUncheckedException_isCorrect]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 10);
		collector.checkThat(
				sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.SettableFuture\\("
						+ "SettableFuture:0\\) //SettableFuture@\\d{1,20}\\[ failure=java.lang.ArithmeticException: toString_afterUncheckedException_isCorrect]$"));
	}

	@Test public void setException_checkedException_isFailed() throws Throwable {
		fut = new SettableFuture<>();

		IOException e = new IOException("setException_checkedException_isFailed");
		fut.setException(e);

		collector.checkThrows(AsyncCheckedException.class, fut::resultNow, hasCause(sameInstance(e)));
		collector.checkSucceeds(fut::isSuccessful, equalTo(false));
		collector.checkSucceeds(fut::isDone, equalTo(true));
		collector.checkSucceeds(fut::isCancelled, equalTo(false));
		collector.checkSucceeds(fut::exceptionNow, sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(TimeUnit.MILLISECONDS));
		collector.checkThrows(AsyncCheckedException.class, fut::get, hasCause(sameInstance(e)));
		collector.checkThrows(AsyncCheckedException.class, () -> fut.get(1, DAYS), hasCause(sameInstance(e)));
	}

	@Test public void toString_afterCheckedException_isCorrect() throws Throwable {
		fut = new SettableFuture<>();

		IOException e = new IOException("toString_afterCheckedException_isCorrect");
		fut.setException(e);

		collector.checkThat(
				fut.toString(),
				matchesPattern(
						"SettableFuture@\\d{1,20}\\[ failure=java.io.IOException: toString_afterCheckedException_isCorrect]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 10);
		collector.checkThat(
				sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.SettableFuture\\("
						+ "SettableFuture:0\\) //SettableFuture@\\d{1,20}\\[ failure=java.io.IOException: toString_afterCheckedException_isCorrect]$"));
	}

	@Test public void setException_cancelledException_isFailed() throws Throwable {
		fut = new SettableFuture<>();

		CancellationException e = new CancellationException("setException_cancelledException_isFailed");
		fut.setException(e);

		collector.checkThrows(CancellationException.class, fut::resultNow);
		collector.checkSucceeds(fut::isSuccessful, equalTo(false));
		collector.checkSucceeds(fut::isDone, equalTo(true));
		collector.checkSucceeds(fut::isCancelled, equalTo(true));
		collector.checkSucceeds(fut::exceptionNow, sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(TimeUnit.MILLISECONDS));
		collector.checkThrows(CancellationException.class, fut::get);
		collector.checkThrows(CancellationException.class, () -> fut.get(1, DAYS));
	}

	@Test public void toString_afterCancelledException_isCorrect() throws Throwable {
		fut = new SettableFuture<>();

		CancellationException e = new CancellationException("toString_afterCancelledException_isCorrect");
		fut.setException(e);

		collector.checkThat(
				fut.toString(),
				matchesPattern(
						"SettableFuture@\\d{1,20}\\[ cancelled=java.util.concurrent.CancellationException: toString_afterCancelledException_isCorrect]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 10);
		collector.checkThat(
				sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.SettableFuture\\("
						+ "SettableFuture:0\\) //SettableFuture@\\d{1,20}\\[ cancelled=java.util.concurrent.CancellationException: toString_afterCancelledException_isCorrect]$"));
	}
}