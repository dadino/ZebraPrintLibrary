package com.dadino.zebraprint.library

import com.dadino.quickstart3.contextformattable.ContextFormattable


data class PrinterDiscoveryProgress(
	val printerList: List<Printer>,
	val message: ContextFormattable?
)
