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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ZebraPrint(var useStrictFilteringForGenericDevices: Boolean = false) {
	private var activity: WeakReference<AppCompatActivity>? = null
	private val printerFinder: PrinterFinder by lazy { PrinterFinder(activity?.get() ?: throw ActivityNotSetException()) }
	private val connectionHandler: ConnectionHandler by lazy { ConnectionHandler() }
	private val selectedPrinterRepo: ISelectedPrinterRepository by lazy { DataStoreSelectedPrinterRepository(activity?.get() ?: throw ActivityNotSetException()) }

	fun setActivity(activity: AppCompatActivity) {
		this.activity = WeakReference(activity)
		printerFinder.toString()
		selectedPrinterRepo.toString()
		activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
			override fun onDestroy(owner: LifecycleOwner) {
				runBlocking { closeConnections() }
				super.onDestroy(owner)
			}
		})
	}

	suspend fun printZplWithSelectedPrinter(zpl: String, failOnErrors: Boolean = false): Result<PrintResponse> {
		return printWithSelectedPrinter(failOnErrors) { connection -> ZplPrinter.printZPL(connection, zpl) }
	}

	suspend fun printTemplateWithSelectedPrinter(templateName: String, data: Map<Int, String>, failOnErrors: Boolean = false): Result<PrintResponse> {
		return printWithSelectedPrinter(failOnErrors) { connection -> ZplPrinter.printZPLTemplate(connection, templateName, data) }
	}

	suspend fun printByteArrayWithSelectedPrinter(byteArray: ByteArray, failOnErrors: Boolean = false): Result<PrintResponse> {
		return printWithSelectedPrinter(failOnErrors) { connection -> ZplPrinter.printByteArray(connection, byteArray) }
	}

	private suspend fun printWithSelectedPrinter(failOnErrors: Boolean = false, printAction: suspend (Connection) -> Unit): Result<PrintResponse> {
		return withContext(Dispatchers.IO) {
			activity?.get()?.let { checkPermissions(it) } ?: throw ActivityNotSetException()
			val printer = loadSelectedPrinter()

			tryPrint(printerAddress = printer?.address, printerName = printer?.friendlyName, failOnErrors = failOnErrors, printAction = printAction)
		}
	}

	private suspend fun tryPrint(printerName: String?, printerAddress: String?, failOnErrors: Boolean = false, printAction: suspend (Connection) -> Unit): Result<PrintResponse> {
		return withContext(Dispatchers.IO) {
			if (printerAddress != null) {
				try {
					Timber.d("Fails on error: $failOnErrors")
					val printResult = print(printerName = printerName, printerAddress = printerAddress, printAction = printAction)
					if (printResult.isSuccess) printResult
					else {
						val exception = printResult.exceptionOrNull() ?: PrintErrorException()
						if (failOnErrors || exception is PrinterNotReadyToPrintException) throw exception
						else searchPrinterThenPrint(printAction = printAction)
					}
				} catch (e: ConnectionException) {
					if (failOnErrors.not()) searchPrinterThenPrint(printAction = printAction)
					else throw PrinterNotReachableException()
				}
			} else if (failOnErrors.not()) {
				searchPrinterThenPrint(printAction = printAction)
			} else {
				throw PrinterNotSelectedException()
			}
		}
	}

	@SuppressLint("MissingPermission")
	private suspend fun searchPrinterThenPrint(printAction: suspend (Connection) -> Unit): Result<PrintResponse> {
		withContext(Dispatchers.Main) { activity?.get()?.let { showPrinterDiscoveryDialog(it) } ?: throw ActivityNotSetException() }
		val printer: Printer? = searchPrinter()
		printer?.let {
			saveSelectedPrinter(it)
			return print(printerName = it.friendlyName, printerAddress = it.address, printAction = printAction)
		} ?: throw PrinterDiscoveryCancelledException()
	}

	@SuppressLint("MissingPermission")
	suspend fun searchPrinterAndSave(): Result<Boolean> {
		activity?.get()?.let {
			checkPermissions(it)
			withContext(Dispatchers.Main) { showPrinterDiscoveryDialog(it) }
		} ?: throw ActivityNotSetException()

		val printer: Printer? = searchPrinter()

		printer?.let {
			saveSelectedPrinter(it)
			return Result.success(true)
		} ?: throw PrinterDiscoveryCancelledException()
	}

	private suspend fun searchPrinter(): Printer? {
		var printer: Printer? = null
		try {
			discoverPrinters().onEach { printerList ->
				Timber.d("ON EACH: New printer list received: ${printerList.joinToString(", ") { it.address }}")
				withContext(Dispatchers.Main) { updatePrinterListDialog(printerList) }
			}
				.buffer(
					capacity = 0,
					onBufferOverflow = BufferOverflow.DROP_OLDEST
				).collect { printerList ->
					Timber.d("COLLECT: New printer list received: ${printerList.joinToString(", ") { it.address }}")
					if (printer == null) {
						Timber.d("COLLECT: Showing printer list dialog")
						withContext(Dispatchers.Main) {
							printer = activity?.get()?.let { showPrinterListDialog(it, printerList) } ?: throw ActivityNotSetException()
						}
					} else {
						Timber.d("COLLECT: skipping printer list dialog")
					}
				}
		} catch (e: Exception) {
			sharedDialog?.dismiss()
			throw e
		}
		return printer
	}


	suspend fun getSelectedPrinter(): Flow<Printer?> {
		return selectedPrinterRepo.getPrinter()
	}

	private var sharedDialog: AlertDialog? = null
	private suspend fun showPrinterListDialog(activity: AppCompatActivity, printerList: List<Printer>): Printer {
		return suspendCancellableCoroutine<Printer> { continuation ->
			if (updatePrinterListDialog(printerList).not()) {
				Timber.d("Showing printer list dialog with ${printerList.size} printers")
				sharedDialog?.dismiss()

				val builder = MaterialAlertDialogBuilder(activity)
					.setBackgroundInsetTop(activity.resources.getDimensionPixelSize(R.dimen.dialog_vertical_margin))
					.setBackgroundInsetBottom(activity.resources.getDimensionPixelSize(R.dimen.dialog_vertical_margin))

				builder.setIcon(R.drawable.ic_printer)
				builder.setTitle(R.string.select_printer)

				val discoveredPrinterAdapter = DiscoveredPrinterAdapter(activity, printerList) { printer ->
					continuation.resume(printer)
					sharedDialog?.dismiss()
				}
				builder.setAdapter(discoveredPrinterAdapter) { dialog, _ ->
					dialog.dismiss()
				}
				builder.setCancelable(true)
				builder.setOnCancelListener { continuation.resumeWithException(PrinterDiscoveryCancelledException()) }
				continuation.invokeOnCancellation { sharedDialog?.dismiss() }
				sharedDialog = builder.show()
			}
		}
	}

	private fun updatePrinterListDialog(printerList: List<Printer>): Boolean {
		if (sharedDialog == null || sharedDialog?.isShowing == false) return false
		val adapter = sharedDialog?.listView?.adapter
		return if (adapter != null && adapter is DiscoveredPrinterAdapter) {

			Timber.d("Updating printer list dialog with ${printerList.size} printers")
			adapter.setNotifyOnChange(false)
			adapter.clear()
			adapter.addAll(printerList)
			adapter.notifyDataSetChanged()
			true
		} else false
	}

	private fun showPrinterDiscoveryDialog(activity: AppCompatActivity) {
		sharedDialog?.dismiss()
		val builder = MaterialAlertDialogBuilder(activity)
			.setBackgroundInsetTop(activity.resources.getDimensionPixelSize(R.dimen.dialog_vertical_margin))
			.setBackgroundInsetBottom(activity.resources.getDimensionPixelSize(R.dimen.dialog_vertical_margin))
		builder.setView(R.layout.dialog_printer_discovery)
		sharedDialog = builder.show()
	}

	private fun checkPermissions(activity: AppCompatActivity): Boolean {
		val notGrantedPermissions = arrayListOf<String>()
		getPermissionRequired().forEach { permission ->
			if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
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
			printerFinder.discoverPrinters(filter, useStrictFilteringForGenericDevices)
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