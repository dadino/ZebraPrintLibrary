package com.dadino.zebraprint.library

import com.zebra.sdk.printer.PrinterStatus


class PrinterDiscoveryCancelledException() : RuntimeException("Print discovery cancelled")
class NoPrinterFoundException() : RuntimeException("No printer found")
class PrinterNotReadyToPrint(val status: PrinterStatus) : RuntimeException("Printer not ready to print")