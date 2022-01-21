package com.dadino.zebraprint.library

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


interface ISelectedPrinterRepository {
	suspend fun savePrinter(printer: Printer)
	suspend fun loadPrinter(): Printer?
}

class PrefSelectedPrinterRepository(context: Context) : ISelectedPrinterRepository {
	private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
	override suspend fun savePrinter(printer: Printer) {
		return withContext(Dispatchers.IO) {
			val stringRepresentation = Json.encodeToString(printer)
			prefs.edit()
				.putString(KEY, stringRepresentation)
				.apply()
		}
	}

	override suspend fun loadPrinter(): Printer? {
		return withContext(Dispatchers.IO) {
			val json = prefs.getString(KEY, null)
			json?.let { Json.decodeFromString(json) }
		}
	}

	companion object {
		private const val KEY = "selectedPrinter"
	}
}