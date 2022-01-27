package com.dadino.zebraprint.library

import com.zebra.sdk.printer.PrinterStatus


class PrinterDiscoveryCancelledException() : RuntimeException("Print discovery cancelled")
class NoPrinterFoundException() : RuntimeException("No printer found")
class PrinterNotReadyToPrintException(val status: PrinterStatus) : RuntimeException("Printer not ready to print")
class PermissionsRequiredException(val permissionList: List<String>) : RuntimeException("Permissions required")