package com.dadino.zebraprint.library

import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.printer.PrinterStatus
import com.zebra.sdk.printer.ZebraPrinter
import com.zebra.sdk.printer.ZebraPrinterFactory
import io.reactivex.Single
import timber.log.Timber


object StatusReader {

	fun readPrinterStatus(printerConnection: Connection): Single<PrinterStatus> {
		return Single.create { emitter ->
			Timber.d("Status reading started")

			try {
				printerConnection.open()

				val printer: ZebraPrinter = ZebraPrinterFactory.getInstance(printerConnection)

				val printerStatus: PrinterStatus = printer.currentStatus
				Timber.d("Printer status: ${printPrinterStatus(printerStatus)}")
				emitter.onSuccess(printerStatus)
			} catch (e: ConnectionException) {
				Timber.e(e, "Status reading error")
				emitter.onError(e)
			} finally {
				Timber.d("Status reading completed")
				printerConnection.close()
			}
		}
	}

	fun printPrinterStatus(status: PrinterStatus): String {
		return "is ready to print: ${status.isReadyToPrint}\n" +
				"is paused: ${status.isPaused}\n" +
				"is head open: ${status.isHeadOpen}\n" +
				"is head too hot: ${status.isHeadTooHot}\n" +
				"is head cold: ${status.isHeadCold}\n" +
				"is paper out: ${status.isPaperOut}\n" +
				"is ribbon: ${status.isRibbonOut}"
	}
}