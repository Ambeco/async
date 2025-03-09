package com.mpd.concurrent.futures.atomic;

import static com.mpd.concurrent.executors.MoreExecutors.directExecutor;
import static com.mpd.concurrent.futures.Future.MAY_INTERRUPT;

import android.util.Log;
import com.mpd.concurrent.futures.Future;

public class AbstractFutureHelper {
	public static void ensureTestComplete(Future<?> fut) {
		if (fut != null) {
			Log.d("atomic", "Cancelling " + fut);
			fut.cancel(MAY_INTERRUPT); // cancel and interrupt anything in progress
			for (int i = 0; i < 10 && fut.getListener() instanceof AbstractFuture; i++) {
				fut = (AbstractFuture<?>) fut.getListener();  // traverse down chain up to a depth of 10 to find the end
			}
			if (fut.getListener() instanceof EndListener) { // if it's an end listener, then we don't need to do anything
				Log.d("atomic", fut + " already has an EndListener, so is \"safe\" to leak");
			} else if (fut.getListener() instanceof AbstractFuture.ListenerAlreadyDispatched) {
				Log.d("atomic", fut + " already dispatched to listener. Hopefully the listener was ended?");
			} else if (fut.getListener() == null) { // at the end of the chain, then swallow exceptions and end.
				Log.d("atomic", fut + " doesn't have an EndListener. Adding a catch-all, and #end()");
				fut.catching(Throwable.class, e -> null, directExecutor()).end();
			} else { // If there's an unknown  listener, then all we can do is pray :(
				Log.w("atomic", fut + " has an unknown listener, and we can't forcibly end the chain");
			}
		}
	}
}
