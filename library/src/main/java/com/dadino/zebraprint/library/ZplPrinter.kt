package com.dadino.zebraprint.library

import android.content.Context
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.printer.ZebraPrinter
import com.zebra.sdk.printer.ZebraPrinterFactory
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import timber.log.Timber

class ZplPrinter(private val context: Context) {

	fun printZPL(printerConnection: Connection, zpl: String): Flowable<PrintStatus> {
		return Flowable.create(
			{ emitter ->
				Timber.d("ZPL printing started")
				emitter.onNext(PrintStatus.PrintInProgress)

				try {
					printerConnection.open()

					printerConnection.write(zpl.toByteArray())
				} catch (e: ConnectionException) {
					Timber.e(e, "ZPL printing error")
					emitter.onNext(PrintStatus.PrintError(e))
				} finally {
					Timber.d("ZPL printing completed")
					printerConnection.close()
					emitter.onNext(PrintStatus.PrintCompleted)
				}
			}, BackpressureStrategy.LATEST
		)
	}

	fun printZPLTemplate(printerConnection: Connection, templateName: String, data: Map<Int, String>): Flowable<PrintStatus> {
		return Flowable.create(
			{ emitter ->
				Timber.d("ZPL printing started")
				emitter.onNext(PrintStatus.PrintInProgress)

				try {
					val printer: ZebraPrinter = ZebraPrinterFactory.getLinkOsPrinter(printerConnection)
					printer.printStoredFormat(templateName, data)
				} catch (e: ConnectionException) {
					Timber.e(e, "ZPL printing error")
					emitter.onNext(PrintStatus.PrintError(e))
				} finally {
					Timber.d("ZPL printing completed")
					printerConnection.close()
					emitter.onNext(PrintStatus.PrintCompleted)
				}
			}, BackpressureStrategy.LATEST
		)
	}
}