package com.dadino.zebraprint.library.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import com.zebra.sdk.btleComm.internal.BluetoothLeHelper;
import com.zebra.sdk.comm.ConnectionA;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.comm.ConnectionReestablisher;
import com.zebra.sdk.comm.internal.ConnectionInfo;
import com.zebra.sdk.comm.internal.NotMyConnectionDataException;
import com.zebra.sdk.comm.internal.ZebraConnector;
import com.zebra.sdk.util.internal.RegexUtil;
import com.zebra.sdk.util.internal.Sleeper;

import java.util.List;

public class BluetoothLeConnection extends ConnectionA {

	protected static final int DEFAULT_TIME_TO_WAIT_FOR_MORE_DATA = 500;
	protected static final int DEFAULT_MAX_TIMEOUT_FOR_READ = 5000;
	protected String friendlyName;
	protected String macAddress;
	private Context context;

	public BluetoothLeConnection(String var1) {
		this(var1, 5000, 500, (Context) null);
	}

	public BluetoothLeConnection(String var1, Context var2) {
		this(var1, 5000, 500, var2);
	}

	public BluetoothLeConnection(String var1, int var2, int var3, Context var4) {
		this(new BluetoothLeZebraConnectorImpl(BluetoothLeHelper.formatMacAddress(var1), var4), BluetoothLeHelper.formatMacAddress(var1), var2, var3, var4);
	}

	protected BluetoothLeConnection(ZebraConnector var1, String var2, int var3, int var4, Context var5) {
		this.friendlyName = "";
		this.context = var5;
		this.zebraConnector = var1;
		this.macAddress = var2;
		this.maxTimeoutForRead = var3;
		this.timeToWaitForMoreData = var4;
	}

	protected BluetoothLeConnection(ConnectionInfo var1) throws NotMyConnectionDataException {
		this((ConnectionInfo) var1, (Context) null);
	}

	protected BluetoothLeConnection(ConnectionInfo var1, Context var2) throws NotMyConnectionDataException {
		this.friendlyName = "";
		String var4 = var1.getMyData();
		String var5 = "^\\s*(" + getConnectionBuilderPrefix() + ":" + ")?([a-fA-F0-9]{2}:[a-fA-F0-9]{2}:[a-fA-F0-9]{2}:[a-fA-F0-9]{2}:[a-fA-F0-9]{2}:[a-fA-F0-9]{2})\\s*$";
		List var3 = RegexUtil.getMatches(var5, var4);
		if (var3.size() != 3) {
			String var6 = "(.*)mBL=(.*?)&(.*)";
			var3 = RegexUtil.getMatches(var6, var4);
			if (var3.isEmpty()) {
				String var7 = "^\\s*(" + getConnectionBuilderPrefix() + ":" + ")?([^:]+)\\s*$";
				var3 = RegexUtil.getMatches(var7, var4);
				if (var3.isEmpty()) {
					throw new NotMyConnectionDataException("BTLE Connection doesn't understand " + var4);
				}
			}
		}

		var4 = BluetoothLeHelper.formatMacAddress((String) var3.get(2));
		this.context = var2;
		this.zebraConnector = new BluetoothLeZebraConnectorImpl(var4, this.context);
		this.macAddress = var4;
		this.maxTimeoutForRead = 5000;
		this.timeToWaitForMoreData = 500;
	}

	private static String getConnectionBuilderPrefix() {
		return "BTLE";
	}

	public void open() throws ConnectionException {
		BluetoothLeHelper.cancelBluetoothDiscovery();
		super.open();
		this.friendlyName = this.getFriendlyNameFromDevice();
	}

	public void close() throws ConnectionException {
		if (this.isConnected) {
			Sleeper.sleep(5000L);
		}

		this.friendlyName = "";
		super.close();
	}

	public ConnectionReestablisher getConnectionReestablisher(long var1) throws ConnectionException {
		return new BluetoothLeConnectionReestablisher(this, var1);
	}

	public String toString() {
		return "BluetoothLe:" + this.getMACAddress() + ":" + this.getFriendlyName();
	}

	public String getSimpleConnectionName() {
		return this.getMACAddress() + ":" + this.getFriendlyName();
	}

	public String getMACAddress() {
		return this.macAddress;
	}

	public String getFriendlyName() {
		return this.friendlyName;
	}

	@SuppressLint("MissingPermission")
	private String getFriendlyNameFromDevice() throws ConnectionException {
		try {
			return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(this.macAddress).getName();
		} catch (IllegalArgumentException var2) {
			throw new ConnectionException("Error reading from connection: " + var2.getMessage());
		}
	}

	public Context getContext() {
		return this.context;
	}

	public void setContext(Context var1) {
		if (this.zebraConnector != null) {
			((BluetoothLeZebraConnectorImpl) this.zebraConnector).setContext(var1);
		}

		this.context = var1;
	}
}
