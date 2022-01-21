package com.dadino.zebraprintlibrary

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dadino.zebraprint.library.ZebraPrint
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import timber.log.Timber

class DiscoverPrinterActivity : AppCompatActivity() {
	private val root: View by lazy { findViewById<View>(R.id.root) }
	private val fab: FloatingActionButton by lazy { findViewById<FloatingActionButton>(R.id.fab) }
	private val progressBar: View by lazy { findViewById<View>(R.id.progressBar) }

	private val zebraPrinter: ZebraPrint by lazy { ZebraPrint(this) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_discover_printer)
		Timber.plant(Timber.DebugTree())

		fab.setOnClickListener { print() }
	}

	private fun print() {
		this.lifecycleScope.launch {
			try {
				Timber.d("Print flow started")
				Snackbar.make(root, "Print started", Snackbar.LENGTH_SHORT).show()
				progressBar.visibility = View.VISIBLE
				val printResult = zebraPrinter.printZPLWithLastUsedPrinter(generateLabel())
				Timber.d("Print flow completed")
				val printResponse = printResult.getOrThrow()
				Snackbar.make(root, "Print completed on ${printResponse.printerName} (${printResponse.printerAddress})", Snackbar.LENGTH_SHORT).show()
				progressBar.visibility = View.INVISIBLE
			} catch (e: Exception) {
				Timber.e(e)
				Timber.d("Print flow error")
				Snackbar.make(root, e.message ?: "Print error", Snackbar.LENGTH_SHORT).show()
				progressBar.visibility = View.INVISIBLE
			}
		}

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