package com.dadino.zebraprint.library

import com.zebra.sdk.comm.Connection
import com.zebra.sdk.printer.ZebraPrinter
import com.zebra.sdk.printer.ZebraPrinterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ZplPrinter {

	suspend fun printByteArray(printerConnection: Connection, byteArray: ByteArray) {
		return withContext(Dispatchers.IO) {
			if (printerConnection.isConnected.not()) printerConnection.open()

			printerConnection.write(byteArray)
		}
	}

	suspend fun printZPL(printerConnection: Connection, zpl: String) {
		return withContext(Dispatchers.IO) {
			if (printerConnection.isConnected.not()) printerConnection.open()

			printerConnection.write(zpl.toByteArray())
		}
	}

	suspend fun printZPLTemplate(printerConnection: Connection, templateName: String, data: Map<Int, String>) {
		return withContext(Dispatchers.IO) {
			val printer: ZebraPrinter = ZebraPrinterFactory.getLinkOsPrinter(printerConnection)
			printer.printStoredFormat(templateName, data)
		}
	}
}