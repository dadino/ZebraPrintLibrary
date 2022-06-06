package com.dadino.zebraprint.library

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import com.dadino.zebraprint.library.CustomBluetoothDiscoverer.Companion.isPrinterClass
import com.zebra.sdk.printer.discovery.DeviceFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber


@SuppressLint("MissingPermission")
class BluetoothBondedPrinterFinder(context: Context) : IPrinterFinder {
	private val appContext: Context = context.applicationContext
	private val bluetoothManager: BluetoothManager by lazy { (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager) }

	override suspend fun discoverPrinters(filter: DeviceFilter?, useStrictFilteringForGenericDevices: Boolean): Flow<List<Printer>> {
		return flow {
			emit(bluetoothManager.adapter.bondedDevices
				.filter { isPrinterClass(it, useStrictFilteringForGenericDevices) }
				.map {
					Timber.d("Found paired device: $it (${it.name})")
					Printer(
						address = it.address,
						friendlyName = it.name,
						typeId = PrinterType.BLE.id
					)
				})
		}
	}
}