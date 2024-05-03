package com.dadino.zebraprint.library

import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.printer.ZebraPrinter
import com.zebra.sdk.printer.ZebraPrinterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber


object StatusReader {

    suspend fun readPrinterState(printerConnection: Connection): Result<PrinterState> {
        return withContext(Dispatchers.IO) {
            val result = try {
                Timber.d("Status reading started")
                if (printerConnection.isConnected.not()) printerConnection.open()

                val printer: ZebraPrinter = ZebraPrinterFactory.getInstance(printerConnection)

                val printerStatus: PrinterState =
                    PrinterState.fromPrinterStatus(printer.currentStatus)
                Timber.d("Printer status: ${printPrinterStatus(printerStatus)}")
                Result.success(printerStatus)
            } catch (e: ConnectionException) {
                Timber.e(e, "Status reading error")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Status reading error")
                Result.failure(e)
            } finally {
                Timber.d("Status reading completed")
                //printerConnection.close()
            }
            result
        }
    }

    fun printPrinterStatus(status: PrinterState): String {
        return "is ready to print: ${status.isReadyToPrint}\n" +
                "is paused: ${status.isPaused}\n" +
                "is head open: ${status.isHeadOpen}\n" +
                "is head too hot: ${status.isHeadTooHot}\n" +
                "is head cold: ${status.isHeadCold}\n" +
                "is paper out: ${status.isPaperOut}\n" +
                "is ribbon: ${status.isRibbonOut}"
    }
}

