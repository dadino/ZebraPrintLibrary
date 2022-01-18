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

	fun findPrinter(filter: DeviceFilter?): Flowable<DiscoveryStatus> {
		return Flowable.create<DiscoveryStatus>(
			{ emitter ->
				Timber.d("Discovery started")
				emitter.onNext(DiscoveryStatus.DiscoveryInProgress)
				BluetoothDiscoverer.findPrinters(context, object : DiscoveryHandler {
					override fun foundPrinter(printer: DiscoveredPrinter) {
						Timber.d("Printer found: ${printer.address}")
						emitter.onNext(DiscoveryStatus.PrinterDiscovered(printer))
					}

					override fun discoveryFinished() {
						Timber.d("Discovery finished")
						emitter.onNext(DiscoveryStatus.DiscoveryCompleted)
					}

					override fun discoveryError(error: String) {
						Timber.e("Discovery error: $error")
						emitter.onNext(DiscoveryStatus.DiscoveryError(RuntimeException(error)))
					}
				}, filter)
			}, BackpressureStrategy.LATEST
		)
			.onErrorReturn { DiscoveryStatus.DiscoveryError(it) }
	}
}

sealed class DiscoveryStatus {
	object DiscoveryInProgress : DiscoveryStatus()
	class DiscoveryError(val error: Throwable) : DiscoveryStatus()
	class PrinterDiscovered(val printer: DiscoveredPrinter) : DiscoveryStatus()
	object DiscoveryCompleted : DiscoveryStatus()
}