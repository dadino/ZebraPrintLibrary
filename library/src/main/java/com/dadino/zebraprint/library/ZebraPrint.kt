package com.dadino.zebraprint.library

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.printer.discovery.DeviceFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ZebraPrint {
	private var activity: AppCompatActivity? = null
	private val printerFinder: PrinterFinder by lazy { PrinterFinder(requireActivity()) }
	private val connectionHandler: ConnectionHandler by lazy { ConnectionHandler() }
	private val selectedPrinterRepo: ISelectedPrinterRepository by lazy { DataStoreSelectedPrinterRepository(requireActivity()) }

	fun setActivity(activity: AppCompatActivity) {
		this.activity = activity
		activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
			override fun onDestroy(owner: LifecycleOwner) {
				runBlocking { closeConnections() }
				super.onDestroy(owner)
			}
		})
	}

	private fun requireActivity(): AppCompatActivity {
		return activity ?: throw RuntimeException("Activity not set in ZebraPrint. Remember to call zebraPrint.setActivity(activity) before using ZebraPrint APIs")
	}

	suspend fun printZplWithSelectedPrinter(zpl: String): Result<PrintResponse> {
		return printWithSelectedPrinter { connection -> ZplPrinter.printZPL(connection, zpl) }
	}

	suspend fun printTemplateWithSelectedPrinter(templateName: String, data: Map<Int, String>): Result<PrintResponse> {
		return printWithSelectedPrinter { connection -> ZplPrinter.printZPLTemplate(connection, templateName, data) }
	}

	suspend fun printByteArrayWithSelectedPrinter(byteArray: ByteArray): Result<PrintResponse> {
		return printWithSelectedPrinter { connection -> ZplPrinter.printByteArray(connection, byteArray) }
	}

	private suspend fun printWithSelectedPrinter(printAction: suspend (Connection) -> Unit): Result<PrintResponse> {
		return withContext(Dispatchers.IO) {
			checkPermissions()
			val printer = loadSelectedPrinter()

			tryPrint(printerAddress = printer?.address, printerName = printer?.friendlyName, printAction = printAction)
		}
	}

	private suspend fun tryPrint(printerName: String?, printerAddress: String?, printAction: suspend (Connection) -> Unit): Result<PrintResponse> {
		return withContext(Dispatchers.IO) {
			if (printerAddress != null) {
				try {
					val printResult = print(printerName = printerName, printerAddress = printerAddress, printAction = printAction)
					if (printResult.isSuccess) printResult
					else {
						val exception = printResult.exceptionOrNull()
						if (exception is PrinterNotReadyToPrintException) throw exception
						else searchPrinterThenPrint(printAction = printAction)
					}
				} catch (e: ConnectionException) {
					searchPrinterThenPrint(printAction = printAction)
				}
			} else {
				searchPrinterThenPrint(printAction = printAction)
			}
		}
	}

	@SuppressLint("MissingPermission")
	private suspend fun searchPrinterThenPrint(printAction: suspend (Connection) -> Unit): Result<PrintResponse> {
		withContext(Dispatchers.Main) { showPrinterDiscoveryDialog() }
		var printer: Printer? = null
		try {
			discoverPrinters().collect { printerList ->
				withContext(Dispatchers.Main) {
					printer = showPrinterListDialog(printerList)
				}
			}
		} catch (e: Exception) {
			sharedDialog?.dismiss()
			throw e
		}
		printer?.let {
			saveSelectedPrinter(it)
			return print(printerName = it.friendlyName, printerAddress = it.address, printAction = printAction)
		} ?: throw PrinterDiscoveryCancelledException()
	}

	@SuppressLint("MissingPermission")
	suspend fun searchPrinterAndSave(): Result<Boolean> {
		checkPermissions()
		withContext(Dispatchers.Main) { showPrinterDiscoveryDialog() }
		var printer: Printer? = null
		try {
			discoverPrinters().collect { printerList ->
				Timber.d("New printer list received: ${printerList.joinToString(", ") { it.address }}")
				withContext(Dispatchers.Main) {
					printer = showPrinterListDialog(printerList)
				}
			}
		} catch (e: Exception) {
			sharedDialog?.dismiss()
			throw e
		}
		printer?.let {
			saveSelectedPrinter(it)
			return Result.success(true)
		} ?: throw PrinterDiscoveryCancelledException()
	}


	suspend fun getSelectedPrinter(): Flow<Printer?> {
		return selectedPrinterRepo.getPrinter()
	}

	private var sharedDialog: AlertDialog? = null
	private suspend fun showPrinterListDialog(printerList: List<Printer>): Printer {
		return suspendCoroutine<Printer> { continuation ->
			Timber.d("Showing printer list dialog with ${printerList.size} printers")
			sharedDialog?.dismiss()

			val builder = MaterialAlertDialogBuilder(requireActivity())
				.setBackgroundInsetTop(requireActivity().resources.getDimensionPixelSize(R.dimen.dialog_vertical_margin))
				.setBackgroundInsetBottom(requireActivity().resources.getDimensionPixelSize(R.dimen.dialog_vertical_margin))

			builder.setIcon(R.drawable.ic_printer)
			builder.setTitle(R.string.select_printer)

			val discoveredPrinterAdapter = DiscoveredPrinterAdapter(requireActivity(), printerList) { printer ->
				continuation.resume(printer)
				sharedDialog?.dismiss()
			}
			builder.setAdapter(discoveredPrinterAdapter) { dialog, _ ->
				dialog.dismiss()
			}
			builder.setCancelable(true)
			builder.setOnCancelListener { continuation.resumeWithException(PrinterDiscoveryCancelledException()) }
			sharedDialog = builder.show()
		}
	}

	private fun showPrinterDiscoveryDialog() {
		sharedDialog?.dismiss()
		val builder = MaterialAlertDialogBuilder(requireActivity())
			.setBackgroundInsetTop(requireActivity().resources.getDimensionPixelSize(R.dimen.dialog_vertical_margin))
			.setBackgroundInsetBottom(requireActivity().resources.getDimensionPixelSize(R.dimen.dialog_vertical_margin))
		builder.setView(R.layout.dialog_printer_discovery)
		sharedDialog = builder.show()
	}

	private fun checkPermissions(): Boolean {
		val notGrantedPermissions = arrayListOf<String>()
		getPermissionRequired().forEach { permission ->
			if (ContextCompat.checkSelfPermission(requireActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
				notGrantedPermissions.add(permission)
			}
		}
		if (notGrantedPermissions.isNotEmpty())
			throw PermissionsRequiredException(notGrantedPermissions)
		else return true
	}

	private fun getPermissionRequired(): List<String> {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
		} else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
	}

	suspend fun loadSelectedPrinter(): Printer? {
		return selectedPrinterRepo.loadPrinter()
	}

	private suspend fun saveSelectedPrinter(printer: Printer) {
		return selectedPrinterRepo.savePrinter(printer)
	}

	private suspend fun discoverPrinters(filter: DeviceFilter? = null): Flow<List<Printer>> {
		return withContext(Dispatchers.IO) {
			printerFinder.discoverPrinters(filter)
		}
	}

	private suspend fun printZPL(printerName: String?, printerAddress: String, zpl: String): Result<PrintResponse> {
		return print(printerName, printerAddress) { connection -> ZplPrinter.printZPL(connection, zpl) }
	}

	private suspend fun printByteArray(printerName: String?, printerAddress: String, byteArray: ByteArray): Result<PrintResponse> {
		return print(printerName, printerAddress) { connection -> ZplPrinter.printByteArray(connection, byteArray) }
	}

	private suspend fun printTemplateWithData(printerName: String?, printerAddress: String, templateName: String, data: Map<Int, String>): Result<PrintResponse> {
		return print(printerName, printerAddress) { connection -> ZplPrinter.printZPLTemplate(connection, templateName, data) }
	}

	private suspend fun print(printerName: String?, printerAddress: String, printAction: suspend (Connection) -> Unit): Result<PrintResponse> {
		return withContext(Dispatchers.IO) {
			try {
				val statusResult = readPrinterStatus(printerAddress)
				val status = statusResult.getOrThrow()
				if (status.isReadyToPrint) {
					try {
						actuallyPrint(printAction, printerAddress, printerName, false)
					} catch (e: Throwable) {
						if (e is ConnectionException) {
							Timber.e("Print failed with ConnectionException")
							actuallyPrint(printAction, printerAddress, printerName, true)
						} else {
							Result.failure<PrintResponse>(e)
						}
					}
					Result.success(PrintResponse(printerName = printerName, printerAddress = printerAddress))
				} else {
					Result.failure(PrinterNotReadyToPrintException(status))
				}
			} catch (e: Exception) {
				Result.failure(e)
			}
		}
	}

	private suspend fun actuallyPrint(printAction: suspend (Connection) -> Unit, printerAddress: String, printerName: String?, forceReconnection: Boolean) {
		printAction(connectionHandler.getConnectionToAddress(printerAddress, forceReconnection = forceReconnection))
		Result.success(PrintResponse(printerName = printerName, printerAddress = printerAddress))
	}

	private suspend fun readPrinterStatus(address: String): Result<PrinterState> {
		return withContext(Dispatchers.IO) {
			val result = StatusReader.readPrinterState(connectionHandler.getConnectionToAddress(address, forceReconnection = false))
			if (result.isFailure && result.exceptionOrNull() is ConnectionException) {
				Timber.e("Read printer status failed with ConnectionException")
				result.exceptionOrNull()?.printStackTrace()
				StatusReader.readPrinterState(connectionHandler.getConnectionToAddress(address, forceReconnection = true))
			} else result
		}
	}

	suspend fun closeConnections() {
		return withContext(Dispatchers.IO) {
			connectionHandler.closeConnections()
		}
	}
}