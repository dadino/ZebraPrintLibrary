package com.dadino.zebraprint.library.rx2

import android.content.Context
import com.dadino.zebraprint.library.PrintResponse
import com.dadino.zebraprint.library.ZebraPrint
import io.reactivex.Completable
import io.reactivex.Single
import kotlinx.coroutines.rx2.rxCompletable
import kotlinx.coroutines.rx2.rxSingle
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


class RxZebraPrint(private val context: Context, private val coroutineContext: CoroutineContext = EmptyCoroutineContext) {
	private val zebraPrint: ZebraPrint by lazy { ZebraPrint(context) }

	fun tryPrint(zpl: String, printerName: String?, printerAddress: String?): Single<PrintResponse> {
		return rxSingle<PrintResponse>(coroutineContext) {
			zebraPrint.tryPrint(zpl = zpl, printerName = printerName, printerAddress = printerAddress).getOrThrow()
		}
	}

	fun printZPLWithLastUsedPrinter(zpl: String): Single<PrintResponse> {
		return rxSingle<PrintResponse>(coroutineContext) {
			zebraPrint.printZPLWithLastUsedPrinter(zpl = zpl).getOrThrow()
		}
	}

	fun closeConnections(): Completable {
		return rxCompletable(coroutineContext) {
			zebraPrint.closeConnections()
		}
	}
}