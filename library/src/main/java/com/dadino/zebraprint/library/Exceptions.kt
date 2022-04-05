package com.dadino.zebraprint.library

import com.dadino.quickstart3.contextformattable.ContextFormattable
import com.dadino.quickstart3.contextformattable.asFormattable

class PrintErrorException() : PrintLibraryException(R.string.error_printer_generic.asFormattable())
class PrinterNotReachableException() : PrintLibraryException(R.string.error_printer_not_reachable.asFormattable())
class PrinterNotSelectedException() : PrintLibraryException(R.string.error_printer_not_selected.asFormattable())
class PrinterDiscoveryCancelledException() : PrintLibraryException(R.string.error_printer_discovery_cancelled.asFormattable())
class NoPrinterFoundException() : PrintLibraryException(R.string.error_no_printer_found.asFormattable())
class PrinterNotReadyToPrintException(val status: PrinterState) : PrintLibraryException(R.string.error_printer_not_ready_to_print.asFormattable())
class PermissionsRequiredException(val permissionList: List<String>) : PrintLibraryException(R.string.error_permissions_not_granted.asFormattable())

open class PrintLibraryException(val contextFormattable: ContextFormattable) : RuntimeException()