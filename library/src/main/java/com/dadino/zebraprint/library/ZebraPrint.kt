package com.dadino.zebraprint.library

import android.content.Context
import com.dadino.zebraprint.library.ConnectionFactory.getConnectionToAddress
import com.zebra.sdk.printer.PrinterStatus
import com.zebra.sdk.printer.discovery.DeviceFilter
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single

class ZebraPrint(private val context: Context) {

	private var lastUsedPrinterAddress: String? = null
	private val printerFinder: PrinterFinder by lazy { PrinterFinder(context) }

	fun printZPLWithLastUsedPrinter(zpl: String): Completable {
		return beginZPLPrinting(zpl = zpl, printerAddress = lastUsedPrinterAddress)
	}

	fun beginZPLPrinting(zpl: String, printerAddress: String?): Completable {
		if (printerAddress != null) return printZPL(address = printerAddress, zpl = zpl)
		else {
			showPrinterDiscoveryDialog()
		}
	}


	fun findPrinter(filter: DeviceFilter? = null): Flowable<DiscoveryStatus> {
		return printerFinder.findPrinter(filter)
	}

	private fun printZPL(address: String, zpl: String): Completable {
		return Single.just(getConnectionToAddress(address))
			.flatMapCompletable { connection ->
				ZplPrinter.printZPL(connection, zpl)
			}
	}

	private fun printTemplateWithData(address: String, templateName: String, data: Map<Int, String>): Completable {
		return Single.just(getConnectionToAddress(address))
			.flatMapCompletable { connection ->
				ZplPrinter.printZPLTemplate(connection, templateName, data)
			}
	}

	fun readPrinterStatus(address: String): Single<PrinterStatus> {
		return Single.just(getConnectionToAddress(address))
			.flatMap { connection ->
				StatusReader.readPrinterStatus(connection)
			}
	}
}