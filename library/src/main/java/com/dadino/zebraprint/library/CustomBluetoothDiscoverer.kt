package com.dadino.zebraprint.library

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.comm.internal.BluetoothHelper
import com.zebra.sdk.comm.internal.BtServiceDiscoverer
import com.zebra.sdk.printer.discovery.DeviceFilter
import com.zebra.sdk.printer.discovery.DiscoveredPrinterBluetooth
import com.zebra.sdk.printer.discovery.DiscoveryHandler
import com.zebra.sdk.printer.discovery.ServiceDiscoveryHandler
import timber.log.Timber


class CustomBluetoothDiscoverer private constructor(
	private val context: Context,
	private val discoveryHandler: DiscoveryHandler,
	private val deviceFilter: DeviceFilter?,
	private val useStrictFilteringForGenericDevices: Boolean
) {
	private var btReceiver: BtReceiver? = null
	private var btMonitor: BtRadioMonitor? = null

	private fun unregisterTopLevelReceivers(context1: Context) {
		if (btReceiver != null) {
			context1.unregisterReceiver(btReceiver)
		}
		if (btMonitor != null) {
			context1.unregisterReceiver(btMonitor)
		}
	}

	@RequiresPermission(value = "android.permission.BLUETOOTH_SCAN")
	private fun doBluetoothDisco() {
		btReceiver = BtReceiver()
		btMonitor = BtRadioMonitor()
		context.registerReceiver(btReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
		context.registerReceiver(btReceiver, IntentFilter(BluetoothDevice.ACTION_UUID))
		context.registerReceiver(btReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
		context.registerReceiver(btMonitor, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
		(context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.startDiscovery()
	}

	private inner class BtRadioMonitor() : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			val action = intent.action
			if (BluetoothAdapter.ACTION_STATE_CHANGED == action) {
				val extras = intent.extras
				val state = extras!!.getInt(BluetoothAdapter.EXTRA_STATE)
				if (state == BluetoothAdapter.STATE_OFF) {
					discoveryHandler.discoveryFinished()
					unregisterTopLevelReceivers(context)
				}
			}
		}
	}

	@SuppressLint("MissingPermission")
	private inner class BtReceiver() : BroadcastReceiver() {
		private val foundDevices: MutableMap<String, BluetoothDevice> = hashMapOf()
		override fun onReceive(context: Context, intent: Intent) {
			when (intent.action) {
				BluetoothDevice.ACTION_FOUND               -> {
					processFoundPrinter(intent)
				}
				BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
					discoveryHandler.discoveryFinished()
					unregisterTopLevelReceivers(context)
				}
				BluetoothDevice.ACTION_UUID                -> {
					processUuidReceived(intent)
				}
			}
		}

		fun processUuidReceived(intent: Intent) {
			val bluetoothDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
			val uuids = intent.getParcelableArrayListExtra<ParcelUuid>(BluetoothDevice.EXTRA_UUID)

			if (bluetoothDevice != null) {
				Timber.d("Received uuid for device ${bluetoothDevice.name} (${bluetoothDevice.address}): ${uuids?.joinToString(", ") { it.uuid.toString() }}")
				addDeviceIfWanted(bluetoothDevice)
			}
		}

		private fun processFoundPrinter(intent: Intent) {
			val bluetoothDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
			if (bluetoothDevice != null) addDeviceIfWanted(bluetoothDevice)
		}

		private fun addDeviceIfWanted(bluetoothDevice: BluetoothDevice) {
			Timber.d("Checking device: ${bluetoothDevice.name} ${bluetoothDevice.address} -> Device class: ${bluetoothDevice.bluetoothClass.deviceClass}")

			if ((deviceFilter == null || deviceFilter.shouldAddPrinter(bluetoothDevice))
				&& isPrinterClass(bluetoothDevice)
			) {
				discoveryHandler.foundPrinter(DiscoveredPrinterBluetooth(bluetoothDevice.address, bluetoothDevice.name))
				foundDevices[bluetoothDevice.address] = bluetoothDevice
			}
		}

		private fun isPrinterClass(bluetoothDevice: BluetoothDevice): Boolean {
			return bluetoothDevice.bluetoothClass != null
					&& (bluetoothDevice.bluetoothClass.deviceClass == 1664 || isPrinterGenericClass(bluetoothDevice))
		}

		private fun isPrinterGenericClass(bluetoothDevice: BluetoothDevice): Boolean {
			return if (useStrictFilteringForGenericDevices) {
				if (bluetoothDevice.bluetoothClass.deviceClass == BluetoothClass.Device.Major.UNCATEGORIZED) {
					val uuids = bluetoothDevice.uuids

					if (uuids == null || uuids.isEmpty()) {
						bluetoothDevice.fetchUuidsWithSdp()
						false
					} else {
						Timber.d("Generic bluetooth device ${bluetoothDevice.name} (${bluetoothDevice.address}) has the following services: ${uuids.joinToString(", ") { it.uuid.toString() }}")
						uuids.any { "00001101-0000-1000-8000-00805F9B34FB".equals(it.uuid.toString(), ignoreCase = true) }
					}
				} else false
			} else bluetoothDevice.bluetoothClass.deviceClass == BluetoothClass.Device.Major.UNCATEGORIZED
		}
	}

	companion object {
		@SuppressLint("MissingPermission")
		@Throws(ConnectionException::class)
		fun findPrinters(context: Context, discoveryHandler: DiscoveryHandler, deviceFilter: DeviceFilter?, useStrictFilteringForGenericDevices: Boolean) {
			val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
			if (bluetoothAdapter == null) {
				discoveryHandler.discoveryError("No bluetooth radio found")
			} else if (!bluetoothAdapter.isEnabled) {
				discoveryHandler.discoveryError("Bluetooth radio is currently disabled")
			} else {
				if (bluetoothAdapter.isDiscovering) {
					bluetoothAdapter.cancelDiscovery()
				}
				CustomBluetoothDiscoverer(context.applicationContext, discoveryHandler, deviceFilter, useStrictFilteringForGenericDevices).doBluetoothDisco()
			}
		}

		fun findServices(context: Context, address: String?, discoveryHandler: ServiceDiscoveryHandler) {
			val serviceDiscoverer = BtServiceDiscoverer(BluetoothHelper.formatMacAddress(address), discoveryHandler)
			serviceDiscoverer.doDiscovery(context)
		}
	}
}
