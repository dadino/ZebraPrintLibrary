package com.dadino.zebraprint.library

import com.zebra.sdk.printer.discovery.DiscoveredPrinter
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers


fun DiscoveredPrinter.getFriendlyName(): String? = discoveryDataMap["FRIENDLY_NAME"]

fun <E, T : Flowable<E>> T.toAsync(): Flowable<E> {
	return subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
}

fun <E, T : Single<E>> T.toAsync(): Single<E> {
	return subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
}

fun <E, T : Observable<E>> T.toAsync(): Observable<E> {
	return subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
}

fun <T : Completable> T.toAsync(): Completable {
	return subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
}

fun <E, T : Flowable<E>> T.onMain(): Flowable<E> {
	return subscribeOn(AndroidSchedulers.mainThread()).observeOn(AndroidSchedulers.mainThread())
}

fun <E, T : Single<E>> T.onMain(): Single<E> {
	return subscribeOn(AndroidSchedulers.mainThread()).observeOn(AndroidSchedulers.mainThread())
}

fun <E, T : Observable<E>> T.onMain(): Observable<E> {
	return subscribeOn(AndroidSchedulers.mainThread()).observeOn(AndroidSchedulers.mainThread())
}

fun <T : Completable> T.onMain(): Completable {
	return subscribeOn(AndroidSchedulers.mainThread()).observeOn(AndroidSchedulers.mainThread())
}
