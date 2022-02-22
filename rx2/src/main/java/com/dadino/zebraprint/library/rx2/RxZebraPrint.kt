package com.dadino.zebraprint.library.rx2

import androidx.appcompat.app.AppCompatActivity
import com.dadino.zebraprint.library.Optional
import com.dadino.zebraprint.library.PrintResponse
import com.dadino.zebraprint.library.Printer
import com.dadino.zebraprint.library.ZebraPrint
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import kotlinx.coroutines.rx2.rxCompletable
import kotlinx.coroutines.rx2.rxFlowable
import kotlinx.coroutines.rx2.rxSingle
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


class RxZebraPrint(private val coroutineContext: CoroutineContext = EmptyCoroutineContext, private val useStrictFilteringForGenericDevices: Boolean) {
	private val zebraPrint: ZebraPrint = ZebraPrint(useStrictFilteringForGenericDevices)
	fun setActivity(activity: AppCompatActivity) {
		zebraPrint.setActivity(activity)
	}

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

	fun loadSelectedPrinter(): Single<Optional<Printer>> {
		return rxSingle(coroutineContext) {
			Optional.create(zebraPrint.loadSelectedPrinter())
		}
	}

	fun getSelectedPrinter(): Flowable<Optional<Printer>> {
		return rxFlowable(coroutineContext) {
			zebraPrint.getSelectedPrinter()
				.collect { printer ->
					this.send(Optional.create(printer))
				}
		}
	}

	fun closeConnections(): Completable {
		return rxCompletable(coroutineContext) {
			zebraPrint.closeConnections()
		}
	}
}