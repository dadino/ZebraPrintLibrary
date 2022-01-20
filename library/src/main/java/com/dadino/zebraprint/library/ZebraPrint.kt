package com.dadino.zebraprint.library

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.dadino.zebraprint.library.ConnectionFactory.getConnectionToAddress
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zebra.sdk.printer.PrinterStatus
import com.zebra.sdk.printer.discovery.DeviceFilter
import com.zebra.sdk.printer.discovery.DiscoveredPrinter
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single

class ZebraPrint(private val context: Context) {

	private var lastUsedPrinterAddress: String? = null
	private val printerFinder: PrinterFinder by lazy { PrinterFinder(context) }

	fun printZPLWithLastUsedPrinter(zpl: String): Completable {
		return beginZPLPrinting(zpl = zpl, printerAddress = lastUsedPrinterAddress)
	}

	private fun beginZPLPrinting(zpl: String, printerAddress: String?): Completable {
		return if (printerAddress != null) printZPL(address = printerAddress, zpl = zpl)
		else {
			findPrinter()
				.doOnTerminate { sharedDialog?.dismiss() }
				.flatMapCompletable { update ->
					when (update) {
						DiscoveryStatus.DiscoveryInProgress   -> showPrinterDiscoveryDialog().ignoreElement()
						is DiscoveryStatus.PrinterListUpdated -> showPrinterListDialog(update.printerList)
							.flatMap { printer -> saveSelectedPrinter(printer) }
							.flatMapCompletable { printerAddress -> printZPL(printerAddress, zpl) }
						DiscoveryStatus.DiscoveryCompleted    -> Completable.complete()
						is DiscoveryStatus.DiscoveryError     -> Completable.error(update.error)
					}
				}
		}
	}

	private var sharedDialog: AlertDialog? = null
	private fun showPrinterListDialog(printerList: List<DiscoveredPrinter>): Single<DiscoveredPrinter> {
		return Single.create<DiscoveredPrinter> { emitter ->
			sharedDialog?.dismiss()

			val builder = MaterialAlertDialogBuilder(context)

			builder.setIcon(R.drawable.ic_printer)
			builder.setTitle(R.string.select_printer)

			builder.setAdapter(DiscoveredPrinterAdapter(context, printerList) { printer ->
				emitter.onSuccess(printer)
				sharedDialog?.dismiss()
			}) { dialog, _ ->
				dialog.dismiss()
			}
			sharedDialog = builder.show()
		}.onMain()
	}

	private fun showPrinterDiscoveryDialog(): Single<Boolean> {
		return Single.create<Boolean> { emitter ->
			sharedDialog?.dismiss()

			val builder = MaterialAlertDialogBuilder(context)

			builder.setView(R.layout.dialog_printer_discovery)

			sharedDialog = builder.show()
		}.onMain()
	}

	private fun showPrintInProgressDialog(): Single<Boolean> {
		return Single.create<Boolean> { emitter ->
			sharedDialog?.dismiss()

			val builder = MaterialAlertDialogBuilder(context)
			builder.setView(R.layout.dialog_print_in_progress)

			sharedDialog = builder.show()
			emitter.onSuccess(true)
		}.onMain()
	}

	private fun saveSelectedPrinter(printer: DiscoveredPrinter): Single<String> {
		return Single.fromCallable {
			lastUsedPrinterAddress = printer.address
			printer.address
		}.toAsync()
	}


	fun findPrinter(filter: DeviceFilter? = null): Flowable<DiscoveryStatus> {
		return printerFinder.findPrinter(filter)
	}

	private fun printZPL(address: String, zpl: String): Completable {
		return showPrintInProgressDialog()
			.map { getConnectionToAddress(address) }
			.flatMapCompletable { connection ->
				ZplPrinter.printZPL(connection, zpl)
			}
			.toAsync()
			.doOnTerminate { sharedDialog?.dismiss() }
	}


	private fun printTemplateWithData(address: String, templateName: String, data: Map<Int, String>): Completable {
		return Single.just(getConnectionToAddress(address))
			.flatMapCompletable { connection ->
				ZplPrinter.printZPLTemplate(connection, templateName, data)
			}
	}

	fun readPrinterStatus(address: String): Single<PrinterStatus> {
		return Single.just(getConnectionToAddress(address))
			.flatMap { connection ->
				StatusReader.readPrinterStatus(connection)
			}
	}
}