package com.dadino.zebraprint.library

import android.content.Context
import com.zebra.sdk.printer.discovery.DeviceFilter
import com.zebra.sdk.printer.discovery.DiscoveredPrinter
import com.zebra.sdk.printer.discovery.DiscoveryHandler
import com.zebra.sdk.printer.discovery.NetworkDiscoverer
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.*
import timber.log.Timber

interface IPrinterFinder {
	suspend fun discoverPrinters(filter: DeviceFilter?, useStrictFilteringForGenericDevices: Boolean): Flow<List<Printer>>
}

class CombinedPrinterFinder(context: Context) {
	private val bluetoothPrinterFinder = BluetoothPrinterFinder(context)
	private val networkPrinterFinder = NetworkPrinterFinder(context)
	suspend fun discoverPrinters(filter: DeviceFilter?, useStrictFilteringForGenericDevices: Boolean, searchOnNetwork: Boolean, searchOnBluetooth: Boolean): Flow<List<Printer>> {
		return flow {
			var isEmptyList = true
			combine(
				if (searchOnBluetooth) bluetoothPrinterFinder.discoverPrinters(filter, useStrictFilteringForGenericDevices) else flowOf(listOf()),
				if (searchOnNetwork) networkPrinterFinder.discoverPrinters(filter, useStrictFilteringForGenericDevices) else flowOf(listOf())
			) { bluetoothPrinters, networkPrinters -> bluetoothPrinters + networkPrinters }
				.collect {
					isEmptyList = it.isEmpty()
					emit(it)
				}
			if (isEmptyList) {
				throw NoPrinterFoundException()
			}
		}
	}
}

class BluetoothPrinterFinder(context: Context) : IPrinterFinder {
	private val appContext: Context = context.applicationContext

	override suspend fun discoverPrinters(filter: DeviceFilter?, useStrictFilteringForGenericDevices: Boolean): Flow<List<Printer>> {
		return callbackFlow {
			Timber.d("Bluetooth discovery started")
			var printerList = listOf<Printer>()

			val bluetoothDiscoveryHandler = object : DiscoveryHandler {
				override fun foundPrinter(discoveredPrinter: DiscoveredPrinter) {
					val printer = Printer.fromDiscoveredPrinter(discoveredPrinter, PrinterType.Bluetooth)
					printerList = printerList.map { oldPrinter ->
						if (oldPrinter.address == printer.address) {
							Timber.d("Bluetooth printer updated: ${printer.friendlyName} (${printer.address})")
							printer
						} else {
							oldPrinter
						}
					}

					if (printerList.none { it.address == printer.address }) {
						Timber.d("Bluetooth printer found: ${printer.friendlyName} (${printer.address})")
						printerList = printerList + printer
					}

					trySend(printerList)
						.onFailure { throwable ->
							throwable?.printStackTrace()
						}
				}

				override fun discoveryFinished() {
					Timber.d("Bluetooth discovery finished")
					channel.close(
						//if (printerList.isEmpty()) NoPrinterFoundException() else
						null
					)
				}

				override fun discoveryError(error: String) {
					Timber.e("Bluetooth discovery error: $error")
					cancel(error, RuntimeException(error))
				}
			}

			//trySend(printerList)
			CustomBluetoothDiscoverer.findPrinters(appContext, bluetoothDiscoveryHandler, filter, useStrictFilteringForGenericDevices)

			awaitClose {
				//TODO stop discovery
			}
		}
	}
}

class NetworkPrinterFinder(context: Context) : IPrinterFinder {

	override suspend fun discoverPrinters(filter: DeviceFilter?, useStrictFilteringForGenericDevices: Boolean): Flow<List<Printer>> {
		return callbackFlow {
			Timber.d("Network discovery started")
			var printerList = listOf<Printer>()

			val networkDiscoveryHandler = object : DiscoveryHandler {
				override fun foundPrinter(discoveredPrinter: DiscoveredPrinter) {
					val printer = Printer.fromDiscoveredPrinter(discoveredPrinter, PrinterType.Network)
					printerList = printerList.map { oldPrinter ->
						if (oldPrinter.address == printer.address) {
							Timber.d("Network printer updated: ${printer.friendlyName} (${printer.address})")
							printer
						} else {
							oldPrinter
						}
					}

					if (printerList.none { it.address == printer.address }) {
						Timber.d("Network printer found: ${printer.friendlyName} (${printer.address})")
						printerList = printerList + printer
					}

					trySend(printerList)
						.onFailure { throwable ->
							throwable?.printStackTrace()
						}
				}

				override fun discoveryFinished() {
					Timber.d("Network discovery finished")
					channel.close(
						//if (printerList.isEmpty()) NoPrinterFoundException() else
						null
					)
				}

				override fun discoveryError(error: String) {
					Timber.e("Network discovery error: $error")
					cancel(error, RuntimeException(error))
				}
			}

			//trySend(printerList)
			NetworkDiscoverer.findPrinters(networkDiscoveryHandler)

			awaitClose {
				//TODO stop discovery
			}
		}
	}
}