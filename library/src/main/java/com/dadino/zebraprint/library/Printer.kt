package com.dadino.zebraprint.library

import androidx.annotation.DrawableRes
import com.zebra.sdk.printer.discovery.DiscoveredPrinter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Printer(
	@SerialName("address") val address: String,
	@SerialName("name") val friendlyName: String?,
	@SerialName("typeId") val typeId: String
) {
	val type: PrinterType
		get() {
			return PrinterType.fromId(typeId)
		}

	companion object {
		fun fromDiscoveredPrinter(discoveredPrinter: DiscoveredPrinter, printerType: PrinterType): Printer {
			return Printer(
				address = discoveredPrinter.address,
				friendlyName = discoveredPrinter.getFriendlyName(),
				typeId = printerType.id
			)
		}
	}
}

sealed class PrinterType(val id: String, @DrawableRes val icon: Int) {
	object Bluetooth : PrinterType("bluetooth", R.drawable.ic_printer_type_bluetooth)
	object BLE : PrinterType("ble", R.drawable.ic_printer_type_ble)
	object Network : PrinterType("network", R.drawable.ic_printer_type_network)

	companion object {
		fun fromId(id: String): PrinterType {
			return when (id) {
				Bluetooth.id -> Bluetooth
				Network.id   -> Network
				BLE.id       -> BLE
				else         -> Bluetooth
			}
		}
	}
}
