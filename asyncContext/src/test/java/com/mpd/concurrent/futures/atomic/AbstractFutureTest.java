package com.mpd.concurrent.futures.atomic;

import static org.hamcrest.Matchers.matchesPattern;

import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.SettableFuture;
import com.mpd.test.ErrorCollector;
import java.util.regex.Pattern;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class) public class AbstractFutureTest {
	@Rule public ErrorCollector collector = new ErrorCollector();

	@Test public void toString_recursiveFuture_limitedDepth() {
		SettableFuture<String> fut1 = new SettableFuture<>();
		Future<String> fut2 = fut1.transform(s -> s);

		fut1.setResult(fut2);

		collector.checkThat(fut1.toString(),
				matchesPattern("SettableFuture@\\d{1,20}\\[ "
						+ "setAsync=FutureFunction<AbstractFutureTest\\$\\$Lambda\\$\\d{1,3}/0x\\p{XDigit}{8,16}>]"));
		StringBuilder sb = new StringBuilder();
		fut1.addPendingString(sb, 4);
		collector.checkThat(sb.toString(), matchesPattern(Pattern.compile(".+", Pattern.DOTALL)));


		collector.checkThat(sb.toString(), matchesPattern(Pattern.compile(
				"^\n\\s\\sat com.mpd.concurrent.futures.SettableFuture\\(SettableFuture:0\\) //[^\\n]+"
						+ "\n\\s\\sat AbstractFutureTest\\$\\$Lambda\\$\\d{1,3}/0x\\p{XDigit}{8,16}.apply\\(AbstractFutureTest:0\\) //[^\\n]+"
						+ "\n\\s\\sat com.mpd.concurrent.futures.SettableFuture\\(SettableFuture:0\\) //[^\\n]+"
						+ "\n\\s\\sat AbstractFutureTest\\$\\$Lambda\\$\\d{1,3}/0x\\p{XDigit}{8,16}.apply\\(AbstractFutureTest:0\\) //[^\\n]+",
				Pattern.DOTALL)));
	}
}
