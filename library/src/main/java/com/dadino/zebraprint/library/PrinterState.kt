package com.dadino.zebraprint.library

import com.zebra.sdk.printer.PrinterStatus

data class PrinterState(
	val isPartialFormatInProgress: Boolean,
	val isHeadCold: Boolean,
	val isHeadOpen: Boolean,
	val isHeadTooHot: Boolean,
	val isPaperOut: Boolean,
	val isRibbonOut: Boolean,
	val isReceiveBufferFull: Boolean,
	val isPaused: Boolean,
	val isReadyToPrint: Boolean,
) {
	companion object {
		fun fromPrinterStatus(printerStatus: PrinterStatus): PrinterState {
			return PrinterState(
				isPartialFormatInProgress = printerStatus.isPartialFormatInProgress,
				isHeadCold = printerStatus.isHeadCold,
				isHeadOpen = printerStatus.isHeadOpen,
				isHeadTooHot = printerStatus.isHeadTooHot,
				isPaperOut = printerStatus.isPaperOut,
				isRibbonOut = printerStatus.isRibbonOut,
				isReceiveBufferFull = printerStatus.isReceiveBufferFull,
				isPaused = printerStatus.isPaused,
				isReadyToPrint = printerStatus.isReadyToPrint,
			)
		}
	}
}