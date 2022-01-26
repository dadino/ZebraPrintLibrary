package com.dadino.zebraprint.library.rx2

import android.content.Context
import com.dadino.zebraprint.library.Optional
import com.dadino.zebraprint.library.PrintResponse
import com.dadino.zebraprint.library.Printer
import com.dadino.zebraprint.library.ZebraPrint
import io.reactivex.Completable
import io.reactivex.Single
import kotlinx.coroutines.rx2.rxCompletable
import kotlinx.coroutines.rx2.rxSingle
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


class RxZebraPrint(private val context: Context, private val coroutineContext: CoroutineContext = EmptyCoroutineContext) {
	private val zebraPrint: ZebraPrint by lazy { ZebraPrint(context) }

	fun printZplWithSelectedPrinter(zpl: String): Single<PrintResponse> {
		return rxSingle(coroutineContext) {
			zebraPrint.printZplWithSelectedPrinter(zpl = zpl).getOrThrow()
		}
	}

	fun printTemplateWithSelectedPrinter(templateName: String, data: Map<Int, String>): Single<PrintResponse> {
		return rxSingle(coroutineContext) {
			zebraPrint.printTemplateWithSelectedPrinter(templateName = templateName, data = data).getOrThrow()
		}
	}

	fun printByteArrayWithSelectedPrinter(byteArray: ByteArray): Single<PrintResponse> {
		return rxSingle(coroutineContext) {
			zebraPrint.printByteArrayWithSelectedPrinter(byteArray = byteArray).getOrThrow()
		}
	}

	fun searchPrinterAndSave(): Completable {
		return rxCompletable(coroutineContext) {
			zebraPrint.searchPrinterAndSave()
		}
	}

	fun getSelectedPrinter(): Single<Optional<Printer>> {
		return rxSingle(coroutineContext) {
			Optional.create(zebraPrint.getSelectedPrinter())
		}
	}

	fun closeConnections(): Completable {
		return rxCompletable(coroutineContext) {
			zebraPrint.closeConnections()
		}
	}
}