package com.dadino.zebraprint.library.ble;

import android.content.Context;

import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.comm.internal.ZebraConnector;
import com.zebra.sdk.comm.internal.ZebraSocket;
import com.zebra.sdk.util.internal.Sleeper;

import java.io.IOException;
import java.util.UUID;

public class BluetoothLeZebraConnectorImpl implements ZebraConnector {

	private static final UUID PARSER_DATA_FROM_PRINTER_CHAR_UUID = UUID.fromString("38eb4a81-c570-11e3-9507-0002a5d5c51b");
	private static final UUID PARSER_DATA_TO_PRINTER_CHAR_UUID = UUID.fromString("38eb4a82-c570-11e3-9507-0002a5d5c51b");
	protected String macAddress;
	protected Context context;

	public BluetoothLeZebraConnectorImpl(String var1, Context var2) {
		this.macAddress = var1;
		this.context = var2;
	}

	public ZebraSocket open() throws ConnectionException {
		ZebraBluetoothLeSocket var1 = new ZebraBluetoothLeSocket(this.macAddress, this.context, PARSER_DATA_FROM_PRINTER_CHAR_UUID, PARSER_DATA_TO_PRINTER_CHAR_UUID);

		try {
			var1.connect();
			Sleeper.sleep(1000L);
			return var1;
		} catch (IOException var3) {
			throw new ConnectionException(var3.getMessage());
		}
	}

	public void setContext(Context var1) {
		this.context = var1;
	}
}