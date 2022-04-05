package com.dadino.zebraprint.library

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


interface ISelectedPrinterRepository {
	suspend fun savePrinter(printer: Printer)
	suspend fun loadPrinter(): Printer?
	suspend fun getPrinter(): Flow<Printer?>
}

class PrefSelectedPrinterRepository(context: Context) : ISelectedPrinterRepository {
	private val prefs: SharedPreferences = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)

	override suspend fun savePrinter(printer: Printer) {
		return withContext(Dispatchers.IO) {
			val stringRepresentation = Json.encodeToString(printer)
			prefs.edit()
				.putString(KEY, stringRepresentation)
				.commit()
		}
	}

	override suspend fun loadPrinter(): Printer? {
		return withContext(Dispatchers.IO) {
			val json = prefs.getString(KEY, null)
			json?.let { Json.decodeFromString(json) }
		}
	}

	override suspend fun getPrinter(): Flow<Printer?> {
		return flow {
			loadPrinter()
		}
	}

	companion object {
		const val KEY = "selectedPrinter"
		const val PREF_FILE_NAME = "ZebraPrintLibraryPrefs"
	}
}

class DataStoreSelectedPrinterRepository(context: Context) : ISelectedPrinterRepository {
	private val appContext: Context = context.applicationContext
	override suspend fun savePrinter(printer: Printer) {
		appContext.printerDataStore.updateData { currentPrinter ->
			currentPrinter.toBuilder()
				.setAddress(printer.address)
				.setFriendlyName(printer.friendlyName)
				.setTypeId(printer.typeId)
				.build()
		}
	}

	override suspend fun loadPrinter(): Printer? {
		return withContext(Dispatchers.IO) {
			val protoPrinter = appContext.printerDataStore.data.first()
			getPrinterFromProto(protoPrinter)
		}
	}

	override suspend fun getPrinter(): Flow<Printer?> {
		return appContext.printerDataStore.data
			.map {
				getPrinterFromProto(it)
			}
	}

	private fun getPrinterFromProto(protoPrinter: ProtoPrinterOuterClass.ProtoPrinter): Printer? {
		return if (protoPrinter.address == "" || protoPrinter.typeId == "") null
		else Printer(
			address = protoPrinter.address,
			friendlyName = protoPrinter.friendlyName,
			typeId = protoPrinter.typeId
		)
	}
}