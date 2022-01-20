package com.dadino.zebraprintlibrary

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.dadino.zebraprint.library.DiscoveryStatus
import com.dadino.zebraprint.library.StatusReader.printPrinterStatus
import com.dadino.zebraprint.library.ZebraPrint
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import timber.log.Timber

class DiscoverPrinterActivity : AppCompatActivity() {

	private val zebraPrinter: ZebraPrint by lazy { ZebraPrint(this) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_discover_printer)
		Timber.plant(Timber.DebugTree())

		findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { print() }
	}

	private var discoveryDisposable: Disposable? = null
	private fun beginPrinterDiscovery() {
		discoveryDisposable?.dispose()
		discoveryDisposable = zebraPrinter.findPrinter(null)
			.flatMap {
				when (it) {
					is DiscoveryStatus.PrinterListUpdated -> Flowable.fromIterable(it.printerList)
					else                                  -> Flowable.fromIterable(listOf())
				}
			}
			.flatMap { printer -> zebraPrinter.readPrinterStatus(printer.address).toFlowable() }
			.subscribeBy(onNext = {
				Timber.d("Printer status received: ${printPrinterStatus(it)}")

			}, onComplete = {}, onError = { it.printStackTrace() })
	}

	private var printDisposable: Disposable? = null
	private fun print() {
		printDisposable?.dispose()
		printDisposable = zebraPrinter.printZPLWithLastUsedPrinter(generateLabel())
			.subscribeBy(
				onComplete = { Timber.d("Label printed") },
				onError = { it.printStackTrace() }
			)
	}

	private fun generateLabel(): String {
		return " ^XA\n" +
				"    ^CI28\n" +
				"    ^LL100\n" +
				"    ^FO60,50^A0N,20,20^FB480,4,0,C,0^FH_^FDRICEVUTA DI PAGAMENTO\\&^FS\n" +
				"    \n" +
				"    ^FO60,80^A0N,60,60^FB480,2,0,C,0^FH_^FDVERBALE\\&N.B 25543\\&^FS\n" +
				"    \n" +
				"    ^FO60,210^A0N,32,32^FB480,4,0,C,0^FH_^FDDOVUTO 103,50_C2_A0_E2_82_AC\\&PAGATO 100,50_C2_A0_E2_82_AC\\&IL 03/11/21 ALLE 13:26\\&NELLE MANI DEL VERBALIZZANTE\\&^FS\n" +
				"    \n" +
				"    ^FO60,360^A0N,20,20^FB480,2,0,C,0^FH_^FDTRASGRESSORE: Giancarlo Esposito\\&^FS\n" +
				"    \n" +
				"    ^FO60,420^A0N,20,20^FH_^FDFIRMA DEL TRASGRESSORE^FS\n" +
				"    \n" +
				"    ^FO60,520^GB480,1,1,B,0^FS\n" +
				"    ^FO60,551^A0N,20,20^FH_^FDFIRMA DEL VERBALIZZANTE^FS\n" +
				"    \n" +
				"    ^FO60,651^GB480,1,1,B,0^FS\n" +
				"    ^XZ"

	}
}