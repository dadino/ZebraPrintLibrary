package com.dadino.zebraprint.library

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
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
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ZebraPrint(var useStrictFilteringForGenericDevices: Boolean = false, var searchOnNetwork: Boolean = true, var searchOnBluetooth: Boolean = true, var searchOnBle: Boolean = true) {
    private var context: Context? = null
    private var activity: WeakReference<AppCompatActivity>? = null
    private val printerFinder: CombinedPrinterFinder by lazy {
        CombinedPrinterFinder(activity?.get() ?: throw ActivityNotSetException())
    }
    private val connectionHandler: ConnectionHandler by lazy { ConnectionHandler() }
    private val selectedPrinterRepo: ISelectedPrinterRepository by lazy {
        DataStoreSelectedPrinterRepository(activity?.get() ?: throw ActivityNotSetException())
    }

    fun setActivity(activity: AppCompatActivity) {
        this.activity = WeakReference(activity)
        this.context = activity.applicationContext
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

            tryPrint(printerAddress = printer?.address, printerName = printer?.friendlyName, printerType = printer?.type, failOnErrors = failOnErrors, printAction = printAction)
        }
    }

    private suspend fun tryPrint(printerName: String?, printerAddress: String?, printerType: PrinterType?, failOnErrors: Boolean = false, printAction: suspend (Connection) -> Unit): Result<PrintResponse> {
        return withContext(Dispatchers.IO) {
            if (printerAddress != null) {
                try {
                    Timber.d("Fails on error: $failOnErrors")
                    val printResult = print(printerName = printerName, printerAddress = printerAddress, printerType = printerType, printAction = printAction)
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
        withContext(Dispatchers.Main) {
            activity?.get()?.let { showPrinterDiscoveryDialog(it) }
                    ?: throw ActivityNotSetException()
        }
        val printer: Printer? = searchPrinter()
        printer?.let {
            saveSelectedPrinter(it)
            return print(printerName = it.friendlyName, printerAddress = it.address, printerType = it.type, printAction = printAction)
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
            discoverPrinters().onEach { progress ->
                Timber.d("ON EACH: New printer list received: ${progress.printerList.joinToString(", ") { it.address }}")
                withContext(Dispatchers.Main) { updatePrinterListDialog(progress) }
            }
                    .buffer(
                            capacity = 0,
                            onBufferOverflow = BufferOverflow.DROP_OLDEST
                    )
                    .takeWhile {
                        printer == null
                    }
                    .collect { printerList ->
                        Timber.d("COLLECT: New printer list received: ${printerList.printerList.joinToString(", ") { it.address }}")
                        if (printer == null) {
                            Timber.d("COLLECT: Showing printer list dialog")
                            withContext(Dispatchers.Main) {
                                printer = activity?.get()?.let { showPrinterListDialog(it, printerList) }
                                        ?: throw ActivityNotSetException()
                            }
                        } else {
                            Timber.d("COLLECT: skipping printer list dialog")
                            activity?.get()?.let { showProgressDialog(it, printerList) }
                                    ?: throw ActivityNotSetException()
                        }
                    }
        } catch (e: Exception) {
            sharedDialog?.dismiss()
            throw e
        } finally {
            sharedDialog?.dismiss()
        }
        return printer
    }


    suspend fun getSelectedPrinter(): Flow<Printer?> {
        return selectedPrinterRepo.getPrinter()
    }

    private var sharedDialog: AlertDialog? = null
    private suspend fun showPrinterListDialog(activity: AppCompatActivity, progress: PrinterDiscoveryProgress): Printer {
        return suspendCancellableCoroutine<Printer> { continuation ->
            if (updatePrinterListDialog(progress).not()) {
                Timber.d("Showing printer list dialog with ${progress.printerList.size} printers")
                sharedDialog?.dismiss()

                val builder = MaterialAlertDialogBuilder(activity)
                        .setBackgroundInsetTop(activity.resources.getDimensionPixelSize(R.dimen.dialog_vertical_margin))
                        .setBackgroundInsetBottom(activity.resources.getDimensionPixelSize(R.dimen.dialog_vertical_margin))

                val titleView = LayoutInflater.from(activity).inflate(R.layout.view_printer_discovery_title, null)
                titleView.findViewById<TextView>(R.id.discovery_title).setText(R.string.select_printer)
                builder.setCustomTitle(titleView)

                val discoveredPrinterAdapter = DiscoveredPrinterAdapter(activity, arrayListOf()) { printer ->
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

                updatePrinterListDialog(progress)
            }
        }
    }

    private suspend fun showProgressDialog(activity: AppCompatActivity, progress: PrinterDiscoveryProgress): Printer {
        return suspendCancellableCoroutine<Printer> { continuation ->
            if (updatePrinterListDialog(progress).not()) {
                Timber.d("Showing progress dialog")
                sharedDialog?.dismiss()

                val builder = MaterialAlertDialogBuilder(activity)
                        .setBackgroundInsetTop(activity.resources.getDimensionPixelSize(R.dimen.dialog_vertical_margin))
                        .setBackgroundInsetBottom(activity.resources.getDimensionPixelSize(R.dimen.dialog_vertical_margin))

                val view = LayoutInflater.from(activity).inflate(R.layout.dialog_progress, null)

                view.findViewById<TextView>(R.id.progress_message).text = progress.message?.format(activity)
                builder.setView(view)
                builder.setCancelable(false)
                continuation.invokeOnCancellation { sharedDialog?.dismiss() }
                sharedDialog = builder.show()
            }
        }
    }

    private fun updatePrinterListDialog(progress: PrinterDiscoveryProgress): Boolean {
        if (sharedDialog == null || sharedDialog?.isShowing == false) return false
        context?.let {
            val messageView = sharedDialog?.findViewById<TextView>(R.id.discovery_message)

            if (progress.message != null) {
                messageView?.text = progress.message.format(it)
                messageView?.visibility = View.VISIBLE
            } else {
                messageView?.visibility = View.GONE
            }
        }
        val adapter = sharedDialog?.listView?.adapter
        return if (adapter != null && adapter is DiscoveredPrinterAdapter) {

            Timber.d("Updating printer list dialog with ${progress.printerList.size} printers")
            adapter.setNotifyOnChange(false)
            adapter.clear()
            adapter.addAll(progress.printerList)
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

    private suspend fun discoverPrinters(filter: DeviceFilter? = null): Flow<PrinterDiscoveryProgress> {
        return withContext(Dispatchers.IO) {
            printerFinder.discoverPrinters(filter, useStrictFilteringForGenericDevices, searchOnNetwork, searchOnBluetooth, searchOnBle)
        }
    }

    private suspend fun printZPL(printerName: String?, printerAddress: String, printerType: PrinterType?, zpl: String): Result<PrintResponse> {
        return print(printerName, printerAddress, printerType) { connection -> ZplPrinter.printZPL(connection, zpl) }
    }

    private suspend fun printByteArray(printerName: String?, printerAddress: String, printerType: PrinterType?, byteArray: ByteArray): Result<PrintResponse> {
        return print(printerName, printerAddress, printerType) { connection -> ZplPrinter.printByteArray(connection, byteArray) }
    }

    private suspend fun printTemplateWithData(printerName: String?, printerAddress: String, printerType: PrinterType?, templateName: String, data: Map<Int, String>): Result<PrintResponse> {
        return print(printerName, printerAddress, printerType) { connection -> ZplPrinter.printZPLTemplate(connection, templateName, data) }
    }

    private suspend fun print(printerName: String?, printerAddress: String, printerType: PrinterType?, printAction: suspend (Connection) -> Unit): Result<PrintResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val statusResult = readPrinterStatus(printerAddress, printerType)
                val status = statusResult.getOrThrow()
                if (status.isReadyToPrint) {
                    try {
                        actuallyPrint(printAction, printerAddress, printerName, printerType, false)
                    } catch (e: Throwable) {
                        if (e is ConnectionException) {
                            Timber.e("Print failed with ConnectionException")
                            actuallyPrint(printAction, printerAddress, printerName, printerType, true)
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

    private suspend fun actuallyPrint(printAction: suspend (Connection) -> Unit, printerAddress: String, printerName: String?, printerType: PrinterType?, forceReconnection: Boolean) {
        printAction(connectionHandler.getConnectionToAddress(context = context, address = printerAddress, printerType = printerType, forceReconnection = forceReconnection))
        Result.success(PrintResponse(printerName = printerName, printerAddress = printerAddress))
    }

    private suspend fun readPrinterStatus(address: String, printerType: PrinterType?): Result<PrinterState> {
        return withContext(Dispatchers.IO) {
            val result = StatusReader.readPrinterState(connectionHandler.getConnectionToAddress(context = context, address = address, printerType = printerType, forceReconnection = false))
            if (result.isFailure && result.exceptionOrNull() is ConnectionException) {
                Timber.e("Read printer status failed with ConnectionException")
                result.exceptionOrNull()?.printStackTrace()
                StatusReader.readPrinterState(connectionHandler.getConnectionToAddress(context = context, address = address, printerType = printerType, forceReconnection = true))
            } else result
        }
    }

    suspend fun closeConnections() {
        return withContext(Dispatchers.IO) {
            connectionHandler.closeConnections()
        }
    }
}