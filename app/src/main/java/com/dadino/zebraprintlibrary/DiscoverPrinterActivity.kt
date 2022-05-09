package com.dadino.zebraprintlibrary

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dadino.zebraprint.library.PermissionsRequiredException
import com.dadino.zebraprint.library.PrintLibraryException
import com.dadino.zebraprint.library.ZebraPrint
import com.dadino.zebraprint.library.rx2.RxZebraPrint
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.snackbar.Snackbar
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.coroutines.launch
import timber.log.Timber

class DiscoverPrinterActivity : AppCompatActivity() {
	private val root: View by lazy { findViewById<View>(R.id.root) }
	private val progressBar: View by lazy { findViewById<View>(R.id.progressBar) }
	private val failOnErrorsCheckbox: MaterialCheckBox by lazy { findViewById<MaterialCheckBox>(R.id.fail_on_errors) }
	private val useStrictDiscoverCheckbox: MaterialCheckBox by lazy { findViewById<MaterialCheckBox>(R.id.use_strict_discover) }
	private val searchOnBluetooth: MaterialCheckBox by lazy { findViewById<MaterialCheckBox>(R.id.search_on_bluetooth) }
	private val searchOnNetwork: MaterialCheckBox by lazy { findViewById<MaterialCheckBox>(R.id.search_on_network) }
	private val coroutinesPrintZpl: View by lazy { findViewById<View>(R.id.coroutines_print_zpl) }
	private val coroutinesSearch: View by lazy { findViewById<View>(R.id.coroutines_search) }
	private val coroutinesSelectedPrinter: TextView by lazy { findViewById<TextView>(R.id.coroutines_selected_printer) }
	private val rx2PrintZpl: View by lazy { findViewById<View>(R.id.rx2_print_zpl) }
	private val rx2Search: View by lazy { findViewById<View>(R.id.rx2_search) }
	private val rx2SelectedPrinter: TextView by lazy { findViewById<TextView>(R.id.rx2_selected_printer) }

	private val zebraPrinter: ZebraPrint = ZebraPrint(useStrictFilteringForGenericDevices = false)
	private val zebraPrinterRx: RxZebraPrint = RxZebraPrint(useStrictFilteringForGenericDevices = false)

	private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_discover_printer)
		Timber.plant(Timber.DebugTree())

		zebraPrinter.setActivity(this)
		zebraPrinterRx.setActivity(this)

		requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
			map.entries.forEach {
				Timber.d("Permission ${it.key} granted: ${it.value}")
			}
		}

		coroutinesPrintZpl.setOnClickListener { printWithCoroutines() }
		coroutinesSearch.setOnClickListener { searchWithCoroutines() }

		rx2PrintZpl.setOnClickListener { printWithRx() }
		rx2Search.setOnClickListener { searchWithRx() }

		useStrictDiscoverCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
			zebraPrinter.useStrictFilteringForGenericDevices = isChecked
			zebraPrinterRx.setUseStrictFilteringForGenericDevices(isChecked)
		}
		zebraPrinter.useStrictFilteringForGenericDevices = useStrictDiscoverCheckbox.isChecked
		zebraPrinterRx.setUseStrictFilteringForGenericDevices(useStrictDiscoverCheckbox.isChecked)


		searchOnBluetooth.setOnCheckedChangeListener { buttonView, isChecked ->
			zebraPrinter.searchOnBluetooth = isChecked
			zebraPrinterRx.setSearchOnBluetooth(isChecked)
		}
		zebraPrinter.searchOnBluetooth = searchOnBluetooth.isChecked
		zebraPrinterRx.setSearchOnBluetooth(searchOnBluetooth.isChecked)

		searchOnNetwork.setOnCheckedChangeListener { buttonView, isChecked ->
			zebraPrinter.searchOnNetwork = isChecked
			zebraPrinterRx.setSearchOnNetwork(isChecked)
		}
		zebraPrinter.searchOnNetwork = searchOnNetwork.isChecked
		zebraPrinterRx.setSearchOnNetwork(searchOnNetwork.isChecked)

		collectSelectedPrinter()
		subscribeToSelectedPrinter()
	}

	private fun collectSelectedPrinter() {
		this.lifecycleScope.launch {
			zebraPrinter.getSelectedPrinter().collect { printer ->
				if (printer != null)
					coroutinesSelectedPrinter.text = "${printer.friendlyName} - ${printer.address} - ${printer.type.id}"
				coroutinesSelectedPrinter.visibility = if (printer != null) View.VISIBLE else View.GONE
			}
		}
	}

	private fun subscribeToSelectedPrinter() {
		val a = zebraPrinterRx.getSelectedPrinter()
			.observeOn(AndroidSchedulers.mainThread())
			.subscribeBy(
				onNext = { printerOptional ->
					val printer = printerOptional.element()
					if (printer != null)
						rx2SelectedPrinter.text = "${printer.friendlyName} - ${printer.address} - ${printer.type.id}"
					rx2SelectedPrinter.visibility = if (printer != null) View.VISIBLE else View.GONE
				},
				onError = { it.printStackTrace() }
			)
	}

	private fun printWithCoroutines() {
		this.lifecycleScope.launch {
			try {
				Timber.d("Print flow started")
				Snackbar.make(root, "Print started", Snackbar.LENGTH_SHORT).show()
				progressBar.visibility = View.VISIBLE
				val printResult = zebraPrinter.printZplWithSelectedPrinter(generateLabel(), failOnErrors = failOnErrorsCheckbox.isChecked)
				Timber.d("Print flow completed")
				val printResponse = printResult.getOrThrow()
				Snackbar.make(root, "Print completed on ${printResponse.printerName} (${printResponse.printerAddress})", Snackbar.LENGTH_SHORT).show()
				progressBar.visibility = View.INVISIBLE
			} catch (e: Exception) {
				onError(e, "print")
			}
		}
	}

	private fun requestPermissionsForPrinter(permissionList: List<String>) {
		requestPermissionLauncher.launch(permissionList.toTypedArray())
	}

	private fun searchWithCoroutines() {
		this.lifecycleScope.launch {
			try {
				Timber.d("Search flow started")
				Snackbar.make(root, "Print started", Snackbar.LENGTH_SHORT).show()
				progressBar.visibility = View.VISIBLE
				zebraPrinter.searchPrinterAndSave()
				Timber.d("Search flow completed")
				Snackbar.make(root, "Printer saved", Snackbar.LENGTH_SHORT).show()
				progressBar.visibility = View.INVISIBLE
			} catch (e: Exception) {
				onError(e, "search")
			}
		}
	}

	private var printDisposable: Disposable? = null
	private fun printWithRx() {
		printDisposable?.dispose()
		printDisposable = zebraPrinterRx.printZplWithSelectedPrinter(generateLabel(), failOnErrors = failOnErrorsCheckbox.isChecked)
			.doOnSubscribe {
				Timber.d("Print flow started")
				Snackbar.make(root, "Print started", Snackbar.LENGTH_SHORT).show()
				progressBar.visibility = View.VISIBLE
			}
			.subscribeBy(
				onSuccess = { printResponse ->
					Timber.d("Print flow completed")
					Snackbar.make(root, "Print completed on ${printResponse.printerName} (${printResponse.printerAddress})", Snackbar.LENGTH_SHORT).show()
					progressBar.visibility = View.INVISIBLE
				},
				onError = {
					onError(it, "print")
				})
	}

	private var searchDisposable: Disposable? = null
	private fun searchWithRx() {
		searchDisposable?.dispose()
		searchDisposable = zebraPrinterRx.searchPrinterAndSave()
			.doOnSubscribe {
				Timber.d("Search flow started")
				Snackbar.make(root, "Search started", Snackbar.LENGTH_SHORT).show()
				progressBar.visibility = View.VISIBLE
			}
			.subscribeBy(onComplete = {
				Timber.d("Search flow completed")
				Snackbar.make(root, "Printer saved", Snackbar.LENGTH_SHORT).show()
				progressBar.visibility = View.INVISIBLE
			},
				onError = {
					onError(it, "search")
				})
	}

	private fun onError(e: Throwable, functionName: String) {
		when (e) {
			is PermissionsRequiredException -> {
				requestPermissionsForPrinter(e.permissionList)
			}
			is PrintLibraryException        -> {
				Timber.e(e)
				Timber.d("$functionName flow error")
				Snackbar.make(root, e.contextFormattable.format(this) ?: "$functionName error", Snackbar.LENGTH_SHORT).show()
			}
			else                            -> {
				Timber.e(e)
				Timber.d("$functionName flow error")
				Snackbar.make(root, e.message ?: "$functionName error", Snackbar.LENGTH_SHORT).show()
			}
		}
		progressBar.visibility = View.INVISIBLE
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