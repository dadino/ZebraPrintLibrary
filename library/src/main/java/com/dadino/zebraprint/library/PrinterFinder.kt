package com.dadino.zebraprint.library

import android.content.Context
import com.zebra.sdk.printer.discovery.BluetoothDiscoverer
import com.zebra.sdk.printer.discovery.DeviceFilter
import com.zebra.sdk.printer.discovery.DiscoveredPrinter
import com.zebra.sdk.printer.discovery.DiscoveryHandler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

class PrinterFinder(private val context: Context) {

	suspend fun discoverPrinters(filter: DeviceFilter?): Flow<List<DiscoveredPrinter>> {
		return callbackFlow {
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

					trySend(printerList)
						.onFailure { throwable ->
							throwable?.printStackTrace()
						}
				}

				override fun discoveryFinished() {
					Timber.d("Discovery finished")
					channel.close(if (printerList.isEmpty()) NoPrinterFoundException() else null)
				}

				override fun discoveryError(error: String) {
					Timber.e("Discovery error: $error")

					cancel(error, RuntimeException(error))
				}
			}
			if (filter != null) BluetoothDiscoverer.findPrinters(context, handler, filter)
			else BluetoothDiscoverer.findPrinters(context, handler)

			awaitClose {
				//TODO stop discovery
			}
		}
	}
}