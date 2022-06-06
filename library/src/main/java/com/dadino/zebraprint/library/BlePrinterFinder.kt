package com.dadino.zebraprint.library

import android.content.Context
import com.zebra.sdk.btleComm.BluetoothLeDiscoverer
import com.zebra.sdk.printer.discovery.DeviceFilter
import com.zebra.sdk.printer.discovery.DiscoveredPrinter
import com.zebra.sdk.printer.discovery.DiscoveryHandler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

class BlePrinterFinder(context: Context) : IPrinterFinder {
	private val appContext: Context = context.applicationContext

	override suspend fun discoverPrinters(filter: DeviceFilter?, useStrictFilteringForGenericDevices: Boolean): Flow<List<Printer>> {
		return callbackFlow {
			Timber.d("BLE discovery started")
			var printerList = listOf<Printer>()

			val bluetoothDiscoveryHandler = object : DiscoveryHandler {
				override fun foundPrinter(discoveredPrinter: DiscoveredPrinter) {
					val printer = Printer.fromDiscoveredPrinter(discoveredPrinter, PrinterType.BLE)
					printerList = printerList.map { oldPrinter ->
						if (oldPrinter.address == printer.address) {
							Timber.d("BLE printer updated: ${printer.friendlyName} (${printer.address})")
							printer
						} else {
							oldPrinter
						}
					}

					if (printerList.none { it.address == printer.address }) {
						Timber.d("BLE printer found: ${printer.friendlyName} (${printer.address})")
						printerList = printerList + printer
					}

					trySend(printerList)
						.onFailure { throwable ->
							throwable?.printStackTrace()
						}
				}

				override fun discoveryFinished() {
					Timber.d("BLE discovery finished")
					channel.close(
						//if (printerList.isEmpty()) NoPrinterFoundException() else
						null
					)
				}

				override fun discoveryError(error: String) {
					Timber.e("BLE discovery error: $error")
					cancel(error, RuntimeException(error))
				}
			}

			//trySend(printerList)
			BluetoothLeDiscoverer.findPrinters(appContext, bluetoothDiscoveryHandler)

			awaitClose {
				//TODO stop discovery
			}
		}
	}

}