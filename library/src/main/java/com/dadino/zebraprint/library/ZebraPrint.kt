package com.dadino.zebraprint.library

import android.content.Context
import com.zebra.sdk.comm.BluetoothConnectionInsecure
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.printer.ZebraPrinter
import com.zebra.sdk.printer.ZebraPrinterFactory
import com.zebra.sdk.printer.discovery.DeviceFilter
import io.reactivex.Flowable

class ZebraPrint(private val context: Context) {

	private val printerFinder: PrinterFinder by lazy { PrinterFinder(context) }
	private val zplPrinter: ZplPrinter by lazy { ZplPrinter(context) }

	fun findPrinter(filter: DeviceFilter? = null): Flowable<DiscoveryStatus> {
		return printerFinder.findPrinter(filter)
	}

	fun printZPL(address: String,zpl: String) {
		val printerConnection: Connection = BluetoothConnectionInsecure(address)

		try {
			printerConnection.open()

			printerConnection.write(zpl.toByteArray())
		} catch (e: ConnectionException) {
			e.printStackTrace()
		} finally {
			printerConnection.close()
		}
	}

	fun printTemplateWithData(address: String, templateName: String, data: Map<Int, String>) {
		val printerConnection: Connection = BluetoothConnectionInsecure(address)

		val printer: ZebraPrinter = ZebraPrinterFactory.getLinkOsPrinter(printerConnection)
		printer.printStoredFormat(templateName, data)
	}
}