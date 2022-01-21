package com.dadino.zebraprint.library

import android.content.Context
import com.zebra.sdk.printer.discovery.BluetoothDiscoverer
import com.zebra.sdk.printer.discovery.DeviceFilter
import com.zebra.sdk.printer.discovery.DiscoveredPrinter
import com.zebra.sdk.printer.discovery.DiscoveryHandler
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import timber.log.Timber

class PrinterFinder(private val context: Context) {
	private var printerList: List<DiscoveredPrinter> = listOf()

	fun findPrinter(filter: DeviceFilter?): Flowable<DiscoveryStatus> {
		return Flowable.create<DiscoveryStatus>(
			{ emitter ->
				Timber.d("Discovery started")
				emitter.onNext(DiscoveryStatus.DiscoveryInProgress)
				printerList = listOf()

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

						emitter.onNext(DiscoveryStatus.PrinterListUpdated(printerList))
					}

					override fun discoveryFinished() {
						Timber.d("Discovery finished")
						emitter.onNext(DiscoveryStatus.DiscoveryCompleted)
					}

					override fun discoveryError(error: String) {
						Timber.e("Discovery error: $error")
						emitter.onError(RuntimeException(error))
					}
				}
				if (filter != null) BluetoothDiscoverer.findPrinters(context, handler, filter)
				else BluetoothDiscoverer.findPrinters(context, handler)
			}, BackpressureStrategy.LATEST
		)
			.toAsync()
	}

}

sealed class DiscoveryStatus {
	object DiscoveryInProgress : DiscoveryStatus()
	data class PrinterListUpdated(val printerList: List<DiscoveredPrinter>) : DiscoveryStatus()
	object DiscoveryCompleted : DiscoveryStatus()
}