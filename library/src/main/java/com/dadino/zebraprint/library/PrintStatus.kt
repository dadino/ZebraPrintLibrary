package com.dadino.zebraprint.library

sealed class PrintStatus {
	object PrintInProgress : PrintStatus()
	object PrintCompleted : PrintStatus()
	class PrintError(val error: Throwable) : PrintStatus()
}
