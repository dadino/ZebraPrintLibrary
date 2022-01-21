package com.dadino.zebraprint.library

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.printer.PrinterStatus
import com.zebra.sdk.printer.discovery.DeviceFilter
import com.zebra.sdk.printer.discovery.DiscoveredPrinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ZebraPrint(private val context: Context) {

	private var lastUsedPrinterAddress: DiscoveredPrinter? = null
	private val printerFinder: PrinterFinder by lazy { PrinterFinder(context) }
	private val connectionHandler: ConnectionHandler = ConnectionHandler()

	suspend fun printZPLWithLastUsedPrinter(zpl: String): Result<PrintResponse> {
		return withContext(Dispatchers.IO) {
			val printer = loadSelectedPrinter()

			tryPrint(zpl = zpl, printerAddress = printer.getOrNull()?.address, printerName = printer.getOrNull()?.getFriendlyName())
		}
	}

	suspend fun tryPrint(zpl: String, printerName: String?, printerAddress: String?): Result<PrintResponse> {
		return withContext(Dispatchers.IO) {
			if (printerAddress != null) {
				try {
					val printResult = printZPL(printerName = printerName, printerAddress = printerAddress, zpl = zpl)
					if (printResult.isSuccess) printResult
					else {
						val exception = printResult.exceptionOrNull()
						if (exception is PrinterNotReadyToPrint) throw exception
						else searchPrinterThenPrint(zpl)
					}
				} catch (e: ConnectionException) {
					searchPrinterThenPrint(zpl)
				}
			} else {
				searchPrinterThenPrint(zpl)
			}
		}
	}

	private suspend fun searchPrinterThenPrint(zpl: String): Result<PrintResponse> {
		withContext(Dispatchers.Main) { showPrinterDiscoveryDialog() }
		val discoveryResult = discoverPrinters()
		withContext(Dispatchers.Main) { if (discoveryResult.isFailure) sharedDialog?.dismiss() }
		val printer: DiscoveredPrinter
		withContext(Dispatchers.Main) { printer = showPrinterListDialog(discoveryResult.getOrThrow()) }
		val saved = saveSelectedPrinter(printer)
		return printZPL(printerName = printer.getFriendlyName(), printerAddress = printer.address, zpl = zpl)
	}

	private var sharedDialog: AlertDialog? = null
	private suspend fun showPrinterListDialog(printerList: List<DiscoveredPrinter>): DiscoveredPrinter {
		return suspendCoroutine<DiscoveredPrinter> { continuation ->
			sharedDialog?.dismiss()

			val builder = MaterialAlertDialogBuilder(context)

			builder.setIcon(R.drawable.ic_printer)
			builder.setTitle(R.string.select_printer)

			builder.setAdapter(DiscoveredPrinterAdapter(context, printerList) { printer ->
				continuation.resume(printer)
				sharedDialog?.dismiss()
			}) { dialog, _ ->
				dialog.dismiss()
			}
			builder.setCancelable(true)
			builder.setOnCancelListener { continuation.resumeWithException(PrinterDiscoveryCancelledException()) }
			sharedDialog = builder.show()
		}
	}

	private fun showPrinterDiscoveryDialog() {
		sharedDialog?.dismiss()
		val builder = MaterialAlertDialogBuilder(context)
		builder.setView(R.layout.dialog_printer_discovery)
		//builder.setCancelable(true)
		//builder.setOnCancelListener { continuation.resumeWithException(PrinterDiscoveryCancelledException()) }
		sharedDialog = builder.show()
	}

	private suspend fun loadSelectedPrinter(): Result<DiscoveredPrinter?> {
		return withContext(Dispatchers.IO) {
			Result.success(lastUsedPrinterAddress)
		}
	}

	private suspend fun saveSelectedPrinter(printer: DiscoveredPrinter): Result<DiscoveredPrinter> {
		return withContext(Dispatchers.IO) {
			lastUsedPrinterAddress = printer
			Result.success(printer)
		}
	}

	private suspend fun discoverPrinters(filter: DeviceFilter? = null): Result<List<DiscoveredPrinter>> {
		return withContext(Dispatchers.IO) {
			printerFinder.discoverPrinters(filter)
		}
	}

	private suspend fun printZPL(printerName: String?, printerAddress: String, zpl: String): Result<PrintResponse> {
		return withContext(Dispatchers.IO) {
			try {
				val statusResult = readPrinterStatus(printerAddress)
				val status = statusResult.getOrThrow()
				if (status.isReadyToPrint) {
					ZplPrinter.printZPL(connectionHandler.getConnectionToAddress(printerAddress), zpl)
					Result.success(PrintResponse(printerName = printerName, printerAddress = printerAddress))
				} else {
					Result.failure(PrinterNotReadyToPrint(status))
				}
			} catch (e: Exception) {
				Result.failure(e)
			}
		}
	}

	private suspend fun printTemplateWithData(address: String, templateName: String, data: Map<Int, String>): Result<PrintResponse> {
		return withContext(Dispatchers.IO) {
			try {

				ZplPrinter.printZPLTemplate(connectionHandler.getConnectionToAddress(address), templateName, data)
				Result.success(PrintResponse(printerName = null, printerAddress = address))
			} catch (e: Exception) {
				e.printStackTrace()
				Result.failure(e)
			}
		}
	}

	private suspend fun readPrinterStatus(address: String): Result<PrinterStatus> {
		return withContext(Dispatchers.IO) {
			StatusReader.readPrinterStatus(connectionHandler.getConnectionToAddress(address))
		}
	}

	private suspend fun closeConnections() {
		return withContext(Dispatchers.IO) {
			connectionHandler.closeConnections()
		}
	}
}