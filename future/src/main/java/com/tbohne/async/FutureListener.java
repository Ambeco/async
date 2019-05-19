package com.tbohne.async;

public interface FutureListener {
	void onSuccess(Future future);
	void onFailure(Future future, RuntimeException t); //common implementation is merely to rethrow to children futures
}
