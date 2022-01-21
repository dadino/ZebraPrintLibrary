package com.dadino.zebraprint.library

import com.zebra.sdk.comm.BluetoothConnectionInsecure
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.TcpConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern


class ConnectionHandler {
	private var printerConnection: Connection? = null
	private var lastConnectedAddress: String? = null
	private val ipPattern = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[1-9])\\.)(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\\.){2}(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\\b")

	suspend fun getConnectionToAddress(address: String): Connection {
		return withContext(Dispatchers.IO) {
			val connection = printerConnection

			if (lastConnectedAddress == address && connection != null && connection.isConnected) connection
			else if (lastConnectedAddress != address && connection != null && connection.isConnected) {
				connection.close()
				createNewConnection(address)
			} else createNewConnection(address)
		}
	}

	private fun createNewConnection(address: String): Connection {
		val connection = if (isTcpAddress(address)) TcpConnection(address, TcpConnection.DEFAULT_ZPL_TCP_PORT)
		else BluetoothConnectionInsecure(address)

		lastConnectedAddress = address
		printerConnection = connection
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