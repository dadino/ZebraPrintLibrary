package com.dadino.zebraprint.library

import android.content.Context
import com.zebra.sdk.printer.discovery.BluetoothDiscoverer
import com.zebra.sdk.printer.discovery.DeviceFilter
import com.zebra.sdk.printer.discovery.DiscoveredPrinter
import com.zebra.sdk.printer.discovery.DiscoveryHandler
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PrinterFinder(private val context: Context) {

	suspend fun discoverPrinters(filter: DeviceFilter?): Result<List<DiscoveredPrinter>> {
		return suspendCoroutine { continuation ->
			Timber.d("Discovery started")
			var printerList = listOf<DiscoveredPrinter>()

			val handler = object : DiscoveryHandler {
				override fun foundPrinter(printer: DiscoveredPrinter) {
					for (settingsKey in printer.discoveryDataMap.keys) {
						Timber.d("Key: $settingsKey, Value: ${printer.getFriendlyName()}")
					}
					printerList = printerList.map { oldPrinter ->
						if (oldPrinter.address == printer.address) {
							Timber.d("Printer updated: ${printer.getFriendlyName()} (${printer.address})")
							printer
						} else {
							oldPrinter
						}
					}

					if (printerList.none { it.address == printer.address }) {
						Timber.d("Printer found: ${printer.getFriendlyName()} (${printer.address})")
						printerList = printerList + printer
					}
				}

				override fun discoveryFinished() {
					Timber.d("Discovery finished")
					continuation.resume(if (printerList.isNotEmpty()) Result.success(printerList) else Result.failure(NoPrinterFoundException()))
				}

				override fun discoveryError(error: String) {
					Timber.e("Discovery error: $error")
					continuation.resume(Result.failure(RuntimeException(error)))
				}
			}
			if (filter != null) BluetoothDiscoverer.findPrinters(context, handler, filter)
			else BluetoothDiscoverer.findPrinters(context, handler)
		}
	}
}