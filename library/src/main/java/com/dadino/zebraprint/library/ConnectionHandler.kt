package com.dadino.zebraprint.library

import android.content.Context
import com.zebra.sdk.btleComm.BluetoothLeConnection
import com.zebra.sdk.comm.BluetoothConnectionInsecure
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.TcpConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.regex.Pattern


class ConnectionHandler {
	private var printerConnection: Connection? = null
	private var lastConnectedAddress: String? = null
	private val ipPattern = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[1-9])\\.)(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\\.){2}(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\\b")

	suspend fun getConnectionToAddress(context: Context?, address: String, printerType: PrinterType?, forceReconnection: Boolean): Connection {
		return withContext(Dispatchers.IO) {
			Timber.d("Connection requested to $address (type: ${printerType?.id}), force reconnection? $forceReconnection")
			val connection = printerConnection
			if (forceReconnection || lastConnectedAddress != address || connection == null || connection.isConnected.not()) {
				try {
					connection?.close()
				} catch (e: Throwable) {
					e.printStackTrace()
				}
				createNewConnection(context, address, printerType)
			} else {
				connection
			}
		}
	}

	private fun createNewConnection(context: Context?, address: String, printerType: PrinterType?): Connection {
		Timber.d("Connecting to $address (type: ${printerType?.id})")

		val connection = if (printerType == null) {
			if (isTcpAddress(address)) TcpConnection(address, TcpConnection.DEFAULT_ZPL_TCP_PORT)
			else BluetoothConnectionInsecure(address)
		} else {
			when (printerType) {
				PrinterType.BLE       -> BluetoothLeConnection(address, context)
				PrinterType.Bluetooth -> BluetoothConnectionInsecure(address)
				PrinterType.Network   -> TcpConnection(address, TcpConnection.DEFAULT_ZPL_TCP_PORT)
			}
		}
		lastConnectedAddress = address
		printerConnection = connection
		Timber.d("Created connection: $connection")
		return connection
	}


	private fun isTcpAddress(address: String): Boolean {
		return ipPattern.matcher(address).matches()
	}

	suspend fun closeConnections() {
		withContext(Dispatchers.IO) {
			printerConnection?.close()
		}
	}
}