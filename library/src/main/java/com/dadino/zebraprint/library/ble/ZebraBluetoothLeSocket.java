package com.dadino.zebraprint.library.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build.VERSION;

import com.zebra.sdk.btleComm.internal.BluetoothDeviceCollection;
import com.zebra.sdk.btleComm.internal.BluetoothLeDeviceConnectionData;
import com.zebra.sdk.comm.internal.ZebraSocket;
import com.zebra.sdk.util.internal.Sleeper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

@SuppressLint("MissingPermission")
public class ZebraBluetoothLeSocket implements ZebraSocket {

	private static final int MAX_MTU_SIZE = 515;
	private static final int MTU_OFFSET = 3;
	private static final int GATT_BUSY = 132;
	private static final int MTU_REQUEST_API_LEVEL = 21;
	private static final UUID PARSER_DATA_SERVICE_UUID = UUID.fromString("38eb4a80-c570-11e3-9507-0002a5d5c51b");
	private static final UUID DATA_NOTIFICATION_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
	private static final Object connectLock = new Object();
	private static final Object writeLock = new Object();
	private static long DEFAULT_CONNECTION_TIMEOUT_INTERVAL = 30000L;
	private static long DEFAULT_CONNECTION_CLOSE_TIMEOUT_INTERVAL = 1500L;
	private static long DEFAULT_WRITE_TIMEOUT_INTERVAL = 10000L;
	private static UUID CURRENT_DATA_FROM_PRINTER_UUID;
	private static UUID CURRENT_DATA_TO_PRINTER_UUID;
	private UUID PARSER_DATA_FROM_PRINTER_CHAR_UUID;
	private UUID PARSER_DATA_TO_PRINTER_CHAR_UUID;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothDevice mBluetoothDevice;
	private BluetoothGattCharacteristic sendDataCharacteristic;
	private BluetoothGattCharacteristic receiveDataCharacteristic;
	private boolean gattSuccess = true;
	private boolean receiverRegistered = false;
	private String macAddress;
	private Context context;
	private boolean firstWriteSuccessful;
	private boolean connectionIsClosing = false;

	public ZebraBluetoothLeSocket(String var1, Context var2, UUID var3, UUID var4) {
		this.PARSER_DATA_FROM_PRINTER_CHAR_UUID = var3;
		this.PARSER_DATA_TO_PRINTER_CHAR_UUID = var4;
		this.macAddress = var1;
		this.context = var2;
		this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	private BroadcastReceiver mBondingBroadcastReceiver = new BroadcastReceiver() {
		public void onReceive(Context var1, Intent var2) {
			BluetoothDevice var3 = (BluetoothDevice) var2.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
			int var4 = var2.getIntExtra("android.bluetooth.device.extra.BOND_STATE", -1);
			if (var3 != null) {
				if (!var3.getAddress().equals(ZebraBluetoothLeSocket.this.macAddress)) {
					return;
				}

				if (var4 == 12) {
					Sleeper.sleep(1000L);
					BluetoothLeDeviceConnectionData var5 = BluetoothDeviceCollection.findBluetoothLeDeviceConnectionData(ZebraBluetoothLeSocket.this.macAddress);
					if (null != var5 && !var5.isPaired() && ZebraBluetoothLeSocket.this.receiveDataCharacteristic != null) {
						BluetoothGattDescriptor var6 = ZebraBluetoothLeSocket.this.receiveDataCharacteristic.getDescriptor(ZebraBluetoothLeSocket.DATA_NOTIFICATION_UUID);
						var6.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
						boolean var7 = BluetoothDeviceCollection.findBluetoothLeDeviceConnectionData(ZebraBluetoothLeSocket.this.macAddress).getGatt().writeDescriptor(var6);
						if (!var7) {
							ZebraBluetoothLeSocket.this.gattSuccess = false;
							ZebraBluetoothLeSocket.this.close();
						}
					}
				}
			}
		}
	};

	private void requestLargerMtu(BluetoothGatt var1) {
		if (VERSION.SDK_INT >= 21) {
			try {
				Class[] var2 = new Class[]{Integer.TYPE};
				Method var3 = var1.getClass().getDeclaredMethod("requestMtu", var2);
				var3.invoke(BluetoothDeviceCollection.findBluetoothLeDeviceConnectionData(this.macAddress).getGatt(), 515);
			} catch (Exception var4) {
			}
		}
	}

	public void connect() throws IOException {
		if (null == this.context) {
			throw new IOException("Context is null. Call setContext(Context) before calling open().");
		} else {
			synchronized (connectLock) {
				CURRENT_DATA_FROM_PRINTER_UUID = this.PARSER_DATA_FROM_PRINTER_CHAR_UUID;
				CURRENT_DATA_TO_PRINTER_UUID = this.PARSER_DATA_TO_PRINTER_CHAR_UUID;
				BluetoothLeDeviceConnectionData var2 = BluetoothDeviceCollection.findBluetoothLeDeviceConnectionData(this.macAddress);
				BluetoothGatt var11;
				if (var2 == null) {
					IntentFilter var3 = new IntentFilter("android.bluetooth.device.action.BOND_STATE_CHANGED");
					this.context.registerReceiver(this.mBondingBroadcastReceiver, var3);
					this.receiverRegistered = true;
					this.connectionIsClosing = false;
					BluetoothDeviceCollection.recordBluetoothLeDeviceConnectionData(this.macAddress);
					var2 = BluetoothDeviceCollection.findBluetoothLeDeviceConnectionData(this.macAddress);
				} else {
					var11 = var2.getGatt();
					if (var11 == null) {
						throw new IOException("no bluetoothGatt found for connection.");
					}

					if (null != this.mBluetoothDevice && var11.getConnectionState(this.mBluetoothDevice) == BluetoothProfile.STATE_CONNECTED) {
						return;
					}
				}

				var2.setPaired(false);
				var2.getChannelReadOutputStreamMap().put(CURRENT_DATA_FROM_PRINTER_UUID, new ByteArrayOutputStream());
				if (this.mBluetoothAdapter != null && this.macAddress != null) {
					var11 = var2.getGatt();
					if (null != var11) {
						BluetoothDeviceCollection.incrementReferenceCount(this.macAddress);
						var11.discoverServices();
					} else {
						long var4 = System.currentTimeMillis();
						this.mBluetoothDevice = this.mBluetoothAdapter.getRemoteDevice(this.macAddress);
						BluetoothGatt var6 = null;
						var6 = this.mBluetoothDevice.connectGatt(this.context, false, new ZebraBluetoothLeSocket.BluetoothLESocketGattCallback());
						if (null != var6) {
							var2 = BluetoothDeviceCollection.findBluetoothLeDeviceConnectionData(this.macAddress);
							var2.setGatt(var6);
							BluetoothDeviceCollection.incrementReferenceCount(this.macAddress);
						}

						while (null == this.sendDataCharacteristic) {
							Sleeper.sleep(1L);
							if (!this.gattSuccess) {
								throw new IOException("Bluetooth LE Gatt failed to Connect");
							}

							if (System.currentTimeMillis() >= var4 + DEFAULT_CONNECTION_TIMEOUT_INTERVAL) {
								this.close();
								throw new IOException("Printer not found");
							}
						}

						long var7 = System.currentTimeMillis();

						while (!var2.isPaired()) {
							Sleeper.sleep(1L);
							if (!this.gattSuccess) {
								throw new IOException("Bluetooth LE Gatt failed to Connect");
							}

							if (System.currentTimeMillis() >= var7 + DEFAULT_CONNECTION_TIMEOUT_INTERVAL) {
								this.close();
								throw new IOException("Passkey not entered");
							}
						}
					}
				} else {
					throw new IOException("Invalid Bluetooth Configuration");
				}
			}
		}
	}

	public void close() {
		if (this.receiverRegistered && null != this.context) {
			this.receiverRegistered = false;
			this.context.unregisterReceiver(this.mBondingBroadcastReceiver);
		}

		if (this.mBluetoothAdapter != null) {
			BluetoothDeviceCollection.decrementReferenceCount(this.macAddress);
			if (BluetoothDeviceCollection.getReferenceCount(this.macAddress) == 0) {
				this.mBluetoothDevice = null;
				BluetoothLeDeviceConnectionData var1 = BluetoothDeviceCollection.findBluetoothLeDeviceConnectionData(this.macAddress);
				if (null != var1) {
					BluetoothGatt var2 = var1.getGatt();
					if (var2 != null) {
						this.connectionIsClosing = true;
						var2.disconnect();

						for (long var3 = System.currentTimeMillis(); null != BluetoothDeviceCollection.findBluetoothLeDeviceConnectionData(this.macAddress); Sleeper.sleep(10L)) {
							if (System.currentTimeMillis() >= var3 + DEFAULT_CONNECTION_CLOSE_TIMEOUT_INTERVAL) {
								this.removeConnectionData();
							}
						}
					}
				}
			}
		}
	}

	public OutputStream getOutputStream() throws IOException {
		return new ZebraBluetoothLeSocket.BluetoothLeOutputStream();
	}

	public InputStream getInputStream() throws IOException {
		return new ZebraBluetoothLeSocket.BluetoothLeInputStream();
	}

	public void setReadTimeout(int var1) throws IOException {
	}

	protected void removeConnectionData() {
		BluetoothLeDeviceConnectionData var1 = BluetoothDeviceCollection.findBluetoothLeDeviceConnectionData(this.macAddress);
		if (null != var1) {
			var1.getGatt().close();
			BluetoothDeviceCollection.removeBluetoothLeDeviceConnectionData(this.macAddress);
		}
	}

	class BluetoothLeInputStream extends InputStream {

		byte[] tempBufferedData = null;

		public BluetoothLeInputStream() {
		}

		public int read() throws IOException {
			if (this.tempBufferedData != null && this.tempBufferedData.length > 0) {
				byte var5 = this.tempBufferedData[0];
				this.tempBufferedData = Arrays.copyOfRange(this.tempBufferedData, 1, this.tempBufferedData.length);
				return var5;
			} else {
				BluetoothLeDeviceConnectionData var1 = BluetoothDeviceCollection.findBluetoothLeDeviceConnectionData(ZebraBluetoothLeSocket.this.macAddress);
				ByteArrayOutputStream var2 = (ByteArrayOutputStream) var1.getChannelReadOutputStreamMap().get(ZebraBluetoothLeSocket.this.PARSER_DATA_FROM_PRINTER_CHAR_UUID);
				this.tempBufferedData = var2.toByteArray();
				int var3 = this.tempBufferedData.length;
				var2.reset();
				if (var3 <= 0) {
					return -1;
				} else {
					byte var4 = this.tempBufferedData[0];
					this.tempBufferedData = Arrays.copyOfRange(this.tempBufferedData, 1, this.tempBufferedData.length);
					return var4;
				}
			}
		}

		public int read(byte[] var1) throws IOException {
			return this.read(var1, 0, var1.length);
		}

		public int read(byte[] var1, int var2, int var3) throws IOException {
			if (this.tempBufferedData != null && this.tempBufferedData.length > 0) {
				int var9 = 0;

				for (int var10 = 0; var10 < var3 && var2 + var10 < var1.length && var10 < this.tempBufferedData.length; ++var10) {
					var1[var2 + var10] = this.tempBufferedData[var10];
					++var9;
				}

				if (var9 < this.tempBufferedData.length) {
					this.tempBufferedData = Arrays.copyOfRange(this.tempBufferedData, var9, this.tempBufferedData.length);
				} else {
					this.tempBufferedData = null;
				}

				return var9;
			} else {
				BluetoothLeDeviceConnectionData var4 = BluetoothDeviceCollection.findBluetoothLeDeviceConnectionData(ZebraBluetoothLeSocket.this.macAddress);
				ByteArrayOutputStream var5 = (ByteArrayOutputStream) var4.getChannelReadOutputStreamMap().get(ZebraBluetoothLeSocket.this.PARSER_DATA_FROM_PRINTER_CHAR_UUID);
				byte[] var6 = var5.toByteArray();
				int var7 = var6.length;
				var5.reset();
				if (var7 == -1) {
					return -1;
				} else {
					for (int var8 = 0; var8 < var7; ++var8) {
						var1[var2 + var8] = var6[var8];
					}

					return var7;
				}
			}
		}

		public long skip(long var1) throws IOException {
			return 0L;
		}

		public int available() throws IOException {
			if (this.tempBufferedData != null && this.tempBufferedData.length > 0) {
				return this.tempBufferedData.length;
			} else {
				byte[] var1 = new byte[1024];
				int var2 = this.read(var1);
				if (var2 > 0) {
					this.tempBufferedData = Arrays.copyOfRange(var1, 0, var2);
				}

				return var2 >= 0 ? var2 : 0;
			}
		}

		public boolean markSupported() {
			return false;
		}
	}

	public class BluetoothLeOutputStream extends OutputStream {

		public BluetoothLeOutputStream() {
		}

		public void write(int var1) throws IOException {
			this.write(new byte[]{(byte) var1});
		}

		public void write(byte[] var1) throws IOException {
			synchronized (ZebraBluetoothLeSocket.writeLock) {
				BluetoothLeDeviceConnectionData var3 = BluetoothDeviceCollection.findBluetoothLeDeviceConnectionData(ZebraBluetoothLeSocket.this.macAddress);
				BluetoothGatt var4 = var3.getGatt();
				if (var4 == null) {
					throw new IOException("no bluetoothGatt found for connection.");
				} else {
					BluetoothGattService var5 = var4.getService(ZebraBluetoothLeSocket.PARSER_DATA_SERVICE_UUID);
					if (null == var5) {
						throw new IOException("Gatt Service not found.");
					} else {
						ZebraBluetoothLeSocket.this.sendDataCharacteristic = var5.getCharacteristic(ZebraBluetoothLeSocket.this.PARSER_DATA_TO_PRINTER_CHAR_UUID);
						int var6;
						if (var3.isFirstWriteForThisChannel()) {
							var3.setFirstWriteForThisChannel(false);
							ZebraBluetoothLeSocket.this.requestLargerMtu(var4);
							var6 = 100;

							while (var3.isWaitingForMtuToChange() && var6-- > 0) {
								Sleeper.sleep(100L);
							}
						}

						var6 = var3.getMtu();
						int var7 = 0;
						int var8 = 0;

						while (var7 < var1.length) {
							int var9 = var7 + var6;
							if (var9 >= var1.length) {
								var9 = var1.length;
							}

							byte[] var10 = Arrays.copyOfRange(var1, var7, var9);
							var8 += var10.length;
							Map var11 = var3.getChannelWriteFinishedMap();
							var11.put(ZebraBluetoothLeSocket.this.PARSER_DATA_TO_PRINTER_CHAR_UUID, false);
							ZebraBluetoothLeSocket.this.sendDataCharacteristic.setValue(var10);
							boolean var12 = var4.writeCharacteristic(ZebraBluetoothLeSocket.this.sendDataCharacteristic);
							if (!var12) {
								throw new IOException("Bluetooth LE Write failed");
							}

							var7 = var9;
							long var13 = System.currentTimeMillis();

							while (!(Boolean) var11.get(ZebraBluetoothLeSocket.this.PARSER_DATA_TO_PRINTER_CHAR_UUID)) {
								Sleeper.sleep(1L);
								if (System.currentTimeMillis() >= var13 + ZebraBluetoothLeSocket.DEFAULT_WRITE_TIMEOUT_INTERVAL) {
									throw new IOException("Bluetooth LE Write timed out");
								}
							}
						}

						if (var8 != var1.length) {
							throw new IOException("Amount of data written does not match amount of data brought in to be written.");
						}
					}
				}
			}
		}

		public void write(byte[] var1, int var2, int var3) throws IOException {
			this.write(Arrays.copyOfRange(var1, var2, var2 + var3));
		}
	}

	class BluetoothLESocketGattCallback extends BluetoothGattCallback {

		BluetoothLESocketGattCallback() {
		}

		public void onConnectionStateChange(BluetoothGatt var1, int var2, int var3) {
			if (var3 == 2) {
				var1.discoverServices();
			} else if (var3 == 0) {
				ZebraBluetoothLeSocket.this.removeConnectionData();
			} else if (var3 != 1) {
				ZebraBluetoothLeSocket.this.gattSuccess = false;
				ZebraBluetoothLeSocket.this.close();
			}

			super.onConnectionStateChange(var1, var2, var3);
		}

		public void onServicesDiscovered(BluetoothGatt var1, int var2) {
			if (var2 == 0) {
				BluetoothGattService var3 = var1.getService(ZebraBluetoothLeSocket.PARSER_DATA_SERVICE_UUID);
				if (null != var3) {
					ZebraBluetoothLeSocket.this.sendDataCharacteristic = var3.getCharacteristic(ZebraBluetoothLeSocket.CURRENT_DATA_TO_PRINTER_UUID);
					ZebraBluetoothLeSocket.this.receiveDataCharacteristic = var3.getCharacteristic(ZebraBluetoothLeSocket.CURRENT_DATA_FROM_PRINTER_UUID);

					while (var1.getDevice().getBondState() == 11) {
						Sleeper.sleep(10L);
						if (ZebraBluetoothLeSocket.this.connectionIsClosing) {
							return;
						}
					}

					BluetoothGattDescriptor var4 = ZebraBluetoothLeSocket.this.receiveDataCharacteristic.getDescriptor(ZebraBluetoothLeSocket.DATA_NOTIFICATION_UUID);
					var4.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
					ZebraBluetoothLeSocket.this.firstWriteSuccessful = var1.writeDescriptor(var4);
					if (!ZebraBluetoothLeSocket.this.firstWriteSuccessful) {
						if (var1.getDevice().getBondState() != 11) {
							ZebraBluetoothLeSocket.this.gattSuccess = false;
							ZebraBluetoothLeSocket.this.close();
							return;
						}

						while (var1.getDevice().getBondState() == 11) {
							Sleeper.sleep(10L);
							if (ZebraBluetoothLeSocket.this.connectionIsClosing) {
								return;
							}
						}
					} else {
						while (var1.getDevice().getBondState() == 11) {
							Sleeper.sleep(10L);
							if (ZebraBluetoothLeSocket.this.connectionIsClosing) {
								return;
							}
						}
					}

					var1.setCharacteristicNotification(ZebraBluetoothLeSocket.this.receiveDataCharacteristic, true);
					ZebraBluetoothLeSocket.this.gattSuccess = true;
				} else {
					ZebraBluetoothLeSocket.this.gattSuccess = false;
					ZebraBluetoothLeSocket.this.close();
				}
			} else {
				ZebraBluetoothLeSocket.this.gattSuccess = false;
				ZebraBluetoothLeSocket.this.close();
			}
		}

		public void onCharacteristicWrite(BluetoothGatt var1, BluetoothGattCharacteristic var2, int var3) {
			Map var4 = BluetoothDeviceCollection.findBluetoothLeDeviceConnectionData(ZebraBluetoothLeSocket.this.macAddress).getChannelWriteFinishedMap();
			var4.put(var2.getUuid(), true);
			super.onCharacteristicWrite(var1, var2, var3);
		}

		public void onCharacteristicChanged(BluetoothGatt var1, BluetoothGattCharacteristic var2) {
			byte[] var3 = var2.getValue();

			try {
				ByteArrayOutputStream var4 = (ByteArrayOutputStream) BluetoothDeviceCollection.findBluetoothLeDeviceConnectionData(ZebraBluetoothLeSocket.this.macAddress).getChannelReadOutputStreamMap().get(var2.getUuid());
				var4.write(var3);
			} catch (IOException var5) {
			}
		}

		public void onDescriptorWrite(BluetoothGatt var1, BluetoothGattDescriptor var2, int var3) {
			if (var3 == 0) {
				BluetoothDeviceCollection.findBluetoothLeDeviceConnectionData(ZebraBluetoothLeSocket.this.macAddress).setPaired(true);
			} else if (var3 == 132) {
				ZebraBluetoothLeSocket.this.gattSuccess = false;
				ZebraBluetoothLeSocket.this.close();
			}
		}

		public void onMtuChanged(BluetoothGatt var1, int var2, int var3) {
			BluetoothLeDeviceConnectionData var4 = BluetoothDeviceCollection.findBluetoothLeDeviceConnectionData(ZebraBluetoothLeSocket.this.macAddress);
			var4.setMtu(var2 - 3);
			var4.setIsWaitingForMtuToChange(false);
		}
	}
}
