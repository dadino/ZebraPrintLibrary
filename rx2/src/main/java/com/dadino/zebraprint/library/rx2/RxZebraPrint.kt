package com.dadino.zebraprint.library.rx2

import androidx.appcompat.app.AppCompatActivity
import com.dadino.quickstart3.base.Optional
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


class RxZebraPrint(private val coroutineContext: CoroutineContext = EmptyCoroutineContext, useStrictFilteringForGenericDevices: Boolean) {

	private val zebraPrint: ZebraPrint = ZebraPrint(useStrictFilteringForGenericDevices)

	fun getUseStrictFilteringForGenericDevices(): Boolean {
		return zebraPrint.useStrictFilteringForGenericDevices
	}

	fun setUseStrictFilteringForGenericDevices(useStrictFilteringForGenericDevices: Boolean) {
		zebraPrint.useStrictFilteringForGenericDevices = useStrictFilteringForGenericDevices
	}

	fun setActivity(activity: AppCompatActivity) {
		zebraPrint.setActivity(activity)
	}

	fun printZplWithSelectedPrinter(zpl: String, failOnErrors: Boolean = false): Single<PrintResponse> {
		return rxSingle(coroutineContext) {
			zebraPrint.printZplWithSelectedPrinter(zpl = zpl, failOnErrors = failOnErrors).getOrThrow()
		}
	}

	fun printTemplateWithSelectedPrinter(templateName: String, data: Map<Int, String>, failOnErrors: Boolean = false): Single<PrintResponse> {
		return rxSingle(coroutineContext) {
			zebraPrint.printTemplateWithSelectedPrinter(templateName = templateName, data = data, failOnErrors = failOnErrors).getOrThrow()
		}
	}

	fun printByteArrayWithSelectedPrinter(byteArray: ByteArray, failOnErrors: Boolean = false): Single<PrintResponse> {
		return rxSingle(coroutineContext) {
			zebraPrint.printByteArrayWithSelectedPrinter(byteArray = byteArray, failOnErrors = failOnErrors).getOrThrow()
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