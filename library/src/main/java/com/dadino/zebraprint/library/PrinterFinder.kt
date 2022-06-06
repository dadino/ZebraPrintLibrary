package com.dadino.zebraprint.library

import android.content.Context
import com.dadino.quickstart3.contextformattable.asFormattable
import com.zebra.sdk.printer.discovery.DeviceFilter
import kotlinx.coroutines.flow.*
import timber.log.Timber


interface IPrinterFinder {
	suspend fun discoverPrinters(filter: DeviceFilter?, useStrictFilteringForGenericDevices: Boolean): Flow<List<Printer>>
}

class CombinedPrinterFinder(context: Context) {
	private val bluetoothBondedPrinterFinder = BluetoothBondedPrinterFinder(context)
	private val bluetoothPrinterFinder = BluetoothPrinterFinder(context)
	private val blePrinterFinder = BlePrinterFinder(context)
	private val networkPrinterFinder = NetworkPrinterFinder(context)

	suspend fun discoverPrinters(filter: DeviceFilter?, useStrictFilteringForGenericDevices: Boolean, searchOnNetwork: Boolean, searchOnBluetooth: Boolean, searchOnBle: Boolean): Flow<PrinterDiscoveryProgress> {
		return if (USE_PARALLEL_DISCOVERY) discoverPrintersParallel(filter, useStrictFilteringForGenericDevices, searchOnNetwork, searchOnBluetooth, searchOnBle)
		else discoverPrintersSequential(filter, useStrictFilteringForGenericDevices, searchOnNetwork, searchOnBluetooth, searchOnBle)
	}

	private suspend fun discoverPrintersParallel(filter: DeviceFilter?, useStrictFilteringForGenericDevices: Boolean, searchOnNetwork: Boolean, searchOnBluetooth: Boolean, searchOnBle: Boolean): Flow<PrinterDiscoveryProgress> {
		return flow {
			var isEmptyList = true
			combine(
				if (searchOnBluetooth) bluetoothPrinterFinder.discoverPrinters(filter, useStrictFilteringForGenericDevices) else flowOf(listOf()),
				if (searchOnBle) blePrinterFinder.discoverPrinters(filter, useStrictFilteringForGenericDevices) else flowOf(listOf()),
				if (searchOnNetwork) networkPrinterFinder.discoverPrinters(filter, useStrictFilteringForGenericDevices) else flowOf(listOf())
			) { bluetoothPrinters, blePrinters, networkPrinters -> blePrinters + networkPrinters + bluetoothPrinters }
				.collect {
					isEmptyList = it.isEmpty()
					Timber.d("Found printers: ${it.joinToString(", ")}")
					emit(PrinterDiscoveryProgress(it, null))
				}
			if (isEmptyList) {
				throw NoPrinterFoundException()
			}
		}
	}

	private suspend fun discoverPrintersSequential(filter: DeviceFilter?, useStrictFilteringForGenericDevices: Boolean, searchOnNetwork: Boolean, searchOnBluetooth: Boolean, searchOnBle: Boolean): Flow<PrinterDiscoveryProgress> {
		return channelFlow {
			val printerList = arrayListOf<Printer>()
			send(PrinterDiscoveryProgress(printerList = printerList, message = R.string.printer_discovery_dialog_message_in_progress.asFormattable()))

			if (searchOnBle || searchOnBluetooth) {
				send(PrinterDiscoveryProgress(printerList = printerList, message = R.string.printer_discovery_dialog_message_paired_in_progress.asFormattable()))
				bluetoothBondedPrinterFinder.discoverPrinters(filter, useStrictFilteringForGenericDevices).collect {
					printerList.updateWith(it)
					send(PrinterDiscoveryProgress(printerList = printerList, message = R.string.printer_discovery_dialog_message_paired_in_progress.asFormattable()))
				}
			}
			if (searchOnBluetooth) {
				send(PrinterDiscoveryProgress(printerList = printerList, message = R.string.printer_discovery_dialog_message_bluetooth_in_progress.asFormattable()))
				bluetoothPrinterFinder.discoverPrinters(filter, useStrictFilteringForGenericDevices).collect {
					printerList.updateWith(it)
					send(PrinterDiscoveryProgress(printerList = printerList, message = R.string.printer_discovery_dialog_message_bluetooth_in_progress.asFormattable()))
				}
			}

			if (searchOnBle) {
				send(PrinterDiscoveryProgress(printerList = printerList, message = R.string.printer_discovery_dialog_message_ble_in_progress.asFormattable()))
				blePrinterFinder.discoverPrinters(filter, useStrictFilteringForGenericDevices).collect {
					printerList.updateWith(it)
					send(PrinterDiscoveryProgress(printerList = printerList, message = R.string.printer_discovery_dialog_message_ble_in_progress.asFormattable()))
				}
			}

			if (searchOnNetwork) {
				send(PrinterDiscoveryProgress(printerList = printerList, message = R.string.printer_discovery_dialog_message_network_in_progress.asFormattable()))
				networkPrinterFinder.discoverPrinters(filter, useStrictFilteringForGenericDevices).collect {
					printerList.updateWith(it)
					send(PrinterDiscoveryProgress(printerList = printerList, message = R.string.printer_discovery_dialog_message_network_in_progress.asFormattable()))
				}
			}

			send(PrinterDiscoveryProgress(printerList = printerList, message = R.string.printer_discovery_dialog_message_done.asFormattable()))

			if (printerList.isEmpty()) throw NoPrinterFoundException()
		}
	}

	companion object {
		private const val USE_PARALLEL_DISCOVERY = false
	}
}

fun ArrayList<Printer>.updateWith(list: List<Printer>) {
	list.forEach { newPrinter ->
		val indexOfFirst = this.indexOfFirst { it.address == newPrinter.address && it.typeId == newPrinter.typeId }
		if (indexOfFirst == -1) this.add(newPrinter)
		else this[indexOfFirst] = newPrinter
	}
}

