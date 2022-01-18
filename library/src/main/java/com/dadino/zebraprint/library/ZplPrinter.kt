package com.dadino.zebraprint.library

import android.content.Context
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.printer.ZebraPrinter
import com.zebra.sdk.printer.ZebraPrinterFactory
import io.reactivex.Completable
 object ZplPrinter {

	fun printZPL(printerConnection: Connection, zpl: String): Completable {
		return Completable.fromCallable {
			printerConnection.open()

			printerConnection.write(zpl.toByteArray())

			printerConnection.close()
		}
	}

	fun printZPLTemplate(printerConnection: Connection, templateName: String, data: Map<Int, String>): Completable {
		return Completable.fromCallable {

			val printer: ZebraPrinter = ZebraPrinterFactory.getLinkOsPrinter(printerConnection)
			printer.printStoredFormat(templateName, data)

			printerConnection.close()
		}
	}
}