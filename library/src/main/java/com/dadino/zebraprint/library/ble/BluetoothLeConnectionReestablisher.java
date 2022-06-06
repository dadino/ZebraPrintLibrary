package com.dadino.zebraprint.library.ble;

import android.content.Context;

import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.comm.ConnectionReestablisher;
import com.zebra.sdk.comm.internal.ConnectionReestablisherBase;
import com.zebra.sdk.printer.PrinterReconnectionHandler;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException;
import com.zebra.sdk.printer.ZebraPrinterLinkOs;
import com.zebra.sdk.printer.discovery.DiscoveryException;

import java.util.concurrent.TimeoutException;

public class BluetoothLeConnectionReestablisher extends ConnectionReestablisherBase implements ConnectionReestablisher {

	public BluetoothLeConnectionReestablisher(Connection var1, long var2) {
		super(var1, var2);
	}

	public void reestablishConnection(PrinterReconnectionHandler var1) throws DiscoveryException, ConnectionException, TimeoutException, ZebraPrinterLanguageUnknownException {
		BluetoothLeConnection var2 = (BluetoothLeConnection) this.zebraPrinterConnection;
		String var3 = var2.getMACAddress();
		int var4 = var2.getMaxTimeoutForRead();
		int var5 = var2.getTimeToWaitForMoreData();
		Context var6 = var2.getContext();
		BluetoothLeConnection var7 = new BluetoothLeConnection(var3, var4, var5, var6);
		String var8 = this.waitForPrinterToComeOnlineViaSgdAndGetFwVer(var7);
		ZebraPrinterLinkOs var9 = ZebraPrinterFactory.createLinkOsPrinter(ZebraPrinterFactory.getInstance(var7));
		var1.printerOnline(var9, var8);
	}
}
