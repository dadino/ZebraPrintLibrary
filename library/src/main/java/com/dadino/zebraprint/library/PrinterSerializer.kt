package com.dadino.zebraprint.library

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import androidx.datastore.migrations.SharedPreferencesMigration
import androidx.datastore.migrations.SharedPreferencesView
import com.dadino.zebraprint.library.PrefSelectedPrinterRepository.Companion.PREF_FILE_NAME
import com.dadino.zebraprint.library.ProtoPrinterOuterClass.ProtoPrinter
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream

object PrinterSerializer : Serializer<ProtoPrinter> {
	override val defaultValue: ProtoPrinter = ProtoPrinter.getDefaultInstance()

	override suspend fun readFrom(input: InputStream): ProtoPrinter {
		try {
			return ProtoPrinter.parseFrom(input)
		} catch (exception: InvalidProtocolBufferException) {
			throw CorruptionException("Cannot read proto.", exception)
		}
	}

	override suspend fun writeTo(
		t: ProtoPrinter,
		output: OutputStream
	) = t.writeTo(output)
}

val Context.printerDataStore: DataStore<ProtoPrinter> by dataStore(
	fileName = "printer.pb",
	serializer = PrinterSerializer,
	produceMigrations = { context ->
		listOf(
			SharedPreferencesMigration(context, PREF_FILE_NAME) { sharedPrefs: SharedPreferencesView, currentData: ProtoPrinter ->
				val json = sharedPrefs.getString(PrefSelectedPrinterRepository.KEY, null)
				val prefPrinter: Printer? = json?.let { Json.decodeFromString(json) }

				if (currentData.address == "" && prefPrinter != null) {
					Timber.d("Migrating printer from preferences to data store: $prefPrinter")
					currentData.toBuilder()
						.setAddress(prefPrinter.address)
						.setFriendlyName(prefPrinter.friendlyName ?: "")
						.setTypeId(prefPrinter.typeId)
						.build()
				} else {
					Timber.d("Not migrating printer from preferences to data store")
					currentData
				}
			}
		)
	}
)