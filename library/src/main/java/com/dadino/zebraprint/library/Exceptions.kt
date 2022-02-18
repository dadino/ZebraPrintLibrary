package com.dadino.zebraprint.library


class PrinterDiscoveryCancelledException() : RuntimeException("Print discovery cancelled")
class NoPrinterFoundException() : RuntimeException("No printer found")
class PrinterNotReadyToPrintException(val status: PrinterState) : RuntimeException("Printer not ready to print")
class PermissionsRequiredException(val permissionList: List<String>) : RuntimeException("Permissions required")