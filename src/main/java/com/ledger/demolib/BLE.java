package com.ledger.demolib;

import java.util.UUID;
import java.lang.ref.WeakReference;
import java.util.HashMap;

import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.content.Context;
import android.Manifest;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.os.Build;
import android.os.Handler;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;

import com.ledger.lib.transport.LedgerDeviceBLE;
import com.ledger.lib.transport.GattUtils;

public class BLE {

		private static BLE singleton = null;

		private boolean scanning;
		private BluetoothManager bluetoothManager;
		private BluetoothAdapter bluetoothAdapter;		
		private Handler handler;
		private ScanCallback scanCallback;
		private HashMap<String, BluetoothDevice> devices;

		protected BLE() {
			handler = new Handler();
		}

		private class ScanCallback implements BluetoothAdapter.LeScanCallback {

			private WeakReference<MainActivity> weakActivity;

			public ScanCallback(MainActivity activity) {
				this.weakActivity = new WeakReference<>(activity);
			}

			@Override
			public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {			
				MainActivity activity = weakActivity.get();
				if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
					return;
				}
				activity.debug("Seen " + device.getAddress() + " / " + device.getName() + " / " + GattUtils.bondToString(device.getBondState()));
				if (!devices.containsKey(device.getAddress())) {
					devices.put(device.getAddress(), device);
				}
			}

			public MainActivity getMainActivity() {
				MainActivity activity = weakActivity.get();
				if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
					return null;
				}
				return activity;
			}
		}

		public boolean initialize(MainActivity activity) {
			if (bluetoothManager == null) {
				bluetoothManager = (BluetoothManager)activity.getSystemService(Context.BLUETOOTH_SERVICE);
				if (bluetoothManager == null) {
					activity.error("Failed to initialize BluetoothManager");
					activity.toast("Failed to initialize BluetoothManager");
					return false;
				}
			}
			if (bluetoothAdapter == null) {
				bluetoothAdapter = bluetoothManager.getAdapter();
				if (bluetoothAdapter == null) {
					activity.error("Failed to get BluetoothAdapter");
					activity.toast("Failed to get BluetoothAdapter");
					return false;					
				}
			}
			if (!bluetoothAdapter.isEnabled()) {
				activity.debug("Bluetooth is not enabled");
			}
			return true;
		}

		public boolean isEnabled() {
			if (bluetoothAdapter != null) {
				return bluetoothAdapter.isEnabled();
			}
			else {
				return false;
			}
		}

		public boolean isLocationEnabled(MainActivity activity) {
	    int locationMode = 0;
	    String locationProviders;

	    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
	        try  {
	        	locationMode = Settings.Secure.getInt(activity.getContentResolver(), Settings.Secure.LOCATION_MODE);
	        } 
	        catch (SettingNotFoundException e) {
	        }

	        return locationMode != Settings.Secure.LOCATION_MODE_OFF;

	    }
	    else {
	    	locationProviders = Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
	    	return !TextUtils.isEmpty(locationProviders);
	    }
		} 

		public boolean isLocationPermissionGranted(MainActivity activity) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				return activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
			}
			else {
				return true;
			}
		}

		public void scan(MainActivity activity, boolean enable, int scanPeriodMS) {
			if (enable) {
				if (scanning) {
					activity.debug("Already scanning");
					return;
				}
				if (bluetoothAdapter == null) {
					activity.debug("Adapter not available");
					return;
				}
				if (!isLocationEnabled(activity) || !isLocationPermissionGranted(activity)) {
					activity.debug("Permissions not granted");
					return;
				}
				handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						MainActivity activity = scanCallback.getMainActivity();
						scanning = false;						
						bluetoothAdapter.stopLeScan(scanCallback);
						if (activity != null) {
							activity.debug("Stop scanning");			
						}
					}
				}, scanPeriodMS);
				scanCallback = new ScanCallback(activity);
				devices = new HashMap<String, BluetoothDevice>();
				bluetoothAdapter.startLeScan(new UUID[] { LedgerDeviceBLE. SERVICE_UUID }, scanCallback);
				activity.debug("Start scanning");
				scanning = true;
			}
			else {
				scanning = false;
				bluetoothAdapter.stopLeScan(scanCallback);				
				activity.debug("Stop scanning");
			}
		}

		public boolean isScanning() {
			return scanning;
		}

		public BluetoothDevice getDevice(String name) {
			if (devices == null) {
				return null;
			}
			for (BluetoothDevice device : devices.values()) {
				if (device.getName().equalsIgnoreCase(name)) {
					return device;
				}
			}
			return null;
		}

		public static synchronized BLE get() {
			if (singleton == null) {
				singleton = new BLE();
			}
			return singleton;
		}

}
