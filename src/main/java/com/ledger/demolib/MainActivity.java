package com.ledger.demolib;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import android.view.View.OnClickListener;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDevice;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.IntentFilter;
import android.widget.Toast;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.provider.Settings;
import android.Manifest;
import android.os.Build;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.widget.EditText;

// when debugging manager requests
import retrofit2.Retrofit;
import retrofit2.converter.moshi.MoshiConverterFactory;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Rfc3339DateJsonAdapter;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.OkHttpClient;
// /when debugging manager requests

import com.ledger.lib.transport.LedgerDevice;
import com.ledger.lib.transport.LedgerDeviceUSB;
import com.ledger.lib.transport.LedgerDeviceBLE;
import com.ledger.lib.apps.eth.Erc20Cache;
import com.ledger.lib.transport.GattUtils;

import com.ledger.management.lib.ManagerService; 
import com.ledger.management.lib.ManagerServiceHelper;

/**
 * Demonstration of the Ledger communication library
 */
public class MainActivity extends Activity
{		
		// when debugging manager requests
		private static final String LEDGER_MANAGER_URL = "https://manager.api.live.ledger.com/";

		private static final String TAG="demolib";		
		private static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

		/* USB specific callback */
		private static final String ACTION_USB_PERMISSION = "com.ledger.demolib.USB_PERMISSION";

		/* BLE specific callbacks */
		private static final int REQUEST_ENABLE_BT = 0;
		private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
		private static final int BLE_SCAN_PERIOD_MS = 1000;		

		private Button connectUSBButton;
		private Button scanBLEButton;
		private Button connectBLEButton;
		private Button closeBLEButton;
		private Button getAppVersionButton;
		private Button getWalletIDButton;
		private Button getDeviceVersionButton;
		private Button ethGetAddressButton;
		private Button ethSignTX;
		private Button ethSignERC20TX;
		private Button ethSignLongTX;		
		private Button btcGetAddress;
		private Button btcSignTX;
		private Button mgtGetApps;
		private Button mgtGetFirmware;
		private Button mgtGenuineCheck;
		private Button mgtLifecycle;
		private TextView logView;

		private UsbManager usbManager;
		private UsbDevice usbDevice;
		private PendingIntent usbPermissionIntent;

		private LedgerDevice ledgerDevice;		

		private BluetoothDevice bleDevice;
		private BluetoothGatt bleGatt;
		private boolean bleGattClosing;
		private boolean bleDeviceOpen;
		private String lastDeviceName;

		private ManagerService managerService;
		private OkHttpClient client;

		/* Log to the application */

		private void commonLog(final String logType, final String message) {	
			Log.d(TAG, message);
			runOnUiThread(new Runnable() {
				public void run() {
					Date currentDate = new Date();
					logView.append(dateFormat.format(currentDate) + "\r\n" + "\t" + message);
					logView.append("\r\n");
				}
			});
		}		

		public void info(String message) {
			commonLog("info", message);			
		}
		public void error(String message) {
			commonLog("error", message);
		}
		public void debug(String message) {
			commonLog("debug", message);
		}

		public void toast(String message) {
			Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
		}

		public void setLedgerDevice(LedgerDevice ledgerDevice) {
			this.ledgerDevice = ledgerDevice;
		}

		/* Acknowledge USB access permissions when granted (done) or if starting the application 
		   automatically (not done) and notify if a device is disconnected */

		private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

			@Override
    	public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_USB_PERMISSION.equals(action)) {
            synchronized (this) {
                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if(device != null){
                      debug("permission granted for device " + device);
                      usbDevice = device;
                      Tasks.get().connectLedgerDeviceUSB(usbManager, usbDevice, MainActivity.this).execute();
                   }
                }
                else {
                    debug("permission denied for device " + device);
                    usbDevice = null;
                }
            }
        }
        else
      	if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device != null) {
                debug("device detached " + device);
                if ((usbDevice != null) && (usbDevice.getDeviceId() == device.getDeviceId())) {
                	usbDevice = null;
                	ledgerDevice = null;
                	toast("Device disconnected");
                }
            }
        }
    	}
	  };		

		/* Verify BLE availability and permissions */

		private final BroadcastReceiver bleBondReceiver = new BroadcastReceiver() {

			@Override
    	public void onReceive(Context context, Intent intent) {
    		String action = intent.getAction();
    		if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
    			 int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
    			 if (state == BluetoothDevice.BOND_BONDED) {
	    				debug("Device bonded");
	    				// Temporary workaround
	    				debug("BLE STACK BUG : Close the BLE connection now to commit the bonding keys");	    				
    			 }
    		}
    	}
		};

		private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

			@Override
			public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {		
				if (ledgerDevice instanceof LedgerDeviceBLE) {
					((LedgerDeviceBLE)ledgerDevice).getGattCallback().onCharacteristicChanged(gatt, characteristic);
				}
			}

			@Override
			public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {		
				if (ledgerDevice instanceof LedgerDeviceBLE) {
					((LedgerDeviceBLE)ledgerDevice).getGattCallback().onCharacteristicRead(gatt, characteristic, status);
				}
			}

			@Override
			public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {		
				if (ledgerDevice instanceof LedgerDeviceBLE) {
					((LedgerDeviceBLE)ledgerDevice).getGattCallback().onCharacteristicWrite(gatt, characteristic, status);
				}
			}

			@Override
			public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {		
				if (bleGattClosing && status == BluetoothProfile.STATE_DISCONNECTED) {
					bleGatt.close();
					bleGattClosing = false;
					ledgerDevice = null;
					bleDeviceOpen = false;
				}
				if (ledgerDevice instanceof LedgerDeviceBLE) {
					LedgerDeviceBLE device = (LedgerDeviceBLE)ledgerDevice;
					if (!bleDeviceOpen && status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
						bleDeviceOpen = true;
						Tasks.get().connectLedgerDeviceBLE(device, MainActivity.this).execute();						
					}
					((LedgerDeviceBLE)ledgerDevice).getGattCallback().onConnectionStateChange(gatt, status, newState);
				}
			}

			@Override
			public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {		
				if (ledgerDevice instanceof LedgerDeviceBLE) {
					((LedgerDeviceBLE)ledgerDevice).getGattCallback().onDescriptorRead(gatt, descriptor, status);
				}
			}

			@Override
			public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {		
				if (ledgerDevice instanceof LedgerDeviceBLE) {
					((LedgerDeviceBLE)ledgerDevice).getGattCallback().onDescriptorWrite(gatt, descriptor, status);
				}
			}

			@Override
			public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {		
				if (ledgerDevice instanceof LedgerDeviceBLE) {
					((LedgerDeviceBLE)ledgerDevice).getGattCallback().onMtuChanged(gatt, mtu, status);
				}
			}

			@Override
			public void onServicesDiscovered(BluetoothGatt gatt, int status) {		
				if (ledgerDevice instanceof LedgerDeviceBLE) {
					((LedgerDeviceBLE)ledgerDevice).getGattCallback().onServicesDiscovered(gatt, status);
				}
			}
		};

		public boolean checkBLE() {
			if (!BLE.get().initialize(this)) {
				return false;
			}
			if (!BLE.get().isEnabled()) {
				Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableIntent, REQUEST_ENABLE_BT);				
				return false;
			}
			if (!BLE.get().isLocationEnabled(this)) {
				final AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("This app needs location access");
				builder.setMessage("Please enable any location service so this app can detect beacons");
				builder.setPositiveButton(android.R.string.ok, null);
				builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
				
					@Override
					public void onDismiss(DialogInterface arg0) {
					  Intent myIntent = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS);
					  startActivity(myIntent);									
					}
				});
				builder.show();		
				return false;			
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (!BLE.get().isLocationPermissionGranted(this)) {
					final AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setTitle("This app needs location access");
					builder.setMessage("Please grant location access so this app can detect beacons");
					builder.setPositiveButton(android.R.string.ok, null);
					builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
					
						@Override
						public void onDismiss(DialogInterface arg0) {
							requestPermissions(new String[] { Manifest.permission.ACCESS_COARSE_LOCATION }, PERMISSION_REQUEST_COARSE_LOCATION);
						
						}
					});
					builder.show();
					return false;
				}
			}				
			return true;
		}	  

		@Override
		public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
			switch(requestCode) {
				case PERMISSION_REQUEST_COARSE_LOCATION:
					if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
						debug("Coarse location permission granted");
					}
					else {
						final AlertDialog.Builder builder = new AlertDialog.Builder(this);
						builder.setTitle("Functionality limited");
						builder.setMessage("Since location access has not been granted, this app will only be able to fetch devices by address");
						builder.setPositiveButton(android.R.string.ok, null);
						builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
						
							@Override
							public void onDismiss(DialogInterface dialog) {
								// TODO Auto-generated method stub
							
							}
						});
						builder.show();
					}
			}
		}		

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        setContentView(R.layout.activity_main);
        connectUSBButton = (Button)findViewById(R.id.connectUSBButton);        
        scanBLEButton = (Button)findViewById(R.id.scanBLEButton);
        connectBLEButton = (Button)findViewById(R.id.connectBLEButton);
        closeBLEButton = (Button)findViewById(R.id.closeBLEButton);
        getAppVersionButton = (Button)findViewById(R.id.getAppVersionButton);
        getWalletIDButton = (Button)findViewById(R.id.getWalletIDButton);
        getDeviceVersionButton = (Button)findViewById(R.id.getDeviceVersionButton);
        ethGetAddressButton = (Button)findViewById(R.id.ethGetAddressButton);
        ethSignTX = (Button)findViewById(R.id.ethSignTX);
        ethSignERC20TX = (Button)findViewById(R.id.ethSignERC20TX);
        ethSignLongTX = (Button)findViewById(R.id.ethSignLongTX);
        btcGetAddress = (Button)findViewById(R.id.btcGetAddress);
        btcSignTX = (Button)findViewById(R.id.btcSignTX);
        mgtGetApps = (Button)findViewById(R.id.mgtGetApps);
        mgtGetFirmware = (Button)findViewById(R.id.mgtGetFirmware);
        mgtGenuineCheck = (Button)findViewById(R.id.mgtGenuineCheck);
        mgtLifecycle = (Button)findViewById(R.id.mgtLifecycle);
        logView = (TextView)findViewById(R.id.logView);
				logView.setMovementMethod(new ScrollingMovementMethod());
				logView.setTextIsSelectable(true);        

				/* Set up button callbacks */

        connectUSBButton.setOnClickListener(new OnClickListener() {
        		@Override
        		public void onClick(View view) {        			
        			debug("connectUSB");
        			HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        			Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        			while (deviceIterator.hasNext()) {
        				UsbDevice device = deviceIterator.next();
        				debug("found device " + device);
        				if (device.getVendorId() == LedgerDeviceUSB.LEDGER_VENDOR) {
        					if (!usbManager.hasPermission(device)) {
        						usbDevice = null;
        						usbManager.requestPermission(device, usbPermissionIntent);
        					}
        					else {
        						usbDevice = device;
        						debug("permission already granted for device " + device);
        						Tasks.get().connectLedgerDeviceUSB(usbManager, usbDevice, MainActivity.this).execute();
        					}
        				}
        			}
        		}
        });
        scanBLEButton.setOnClickListener(new OnClickListener() {
        	@Override
        	public void onClick(View view) {
        		if (!checkBLE()) {
        			return;
        		}
        		BLE.get().scan(MainActivity.this, !BLE.get().isScanning(), BLE_SCAN_PERIOD_MS);
        	}
        });
        connectBLEButton.setOnClickListener(new OnClickListener() {
        	@Override
        	public void onClick(View view) {

						LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
						View dialogView = inflater.inflate(R.layout.dialog_device, null);
						AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
						builder.setView(dialogView);
						final EditText userInput = (EditText)dialogView.findViewById(R.id.dialogInput);
						if (lastDeviceName != null) {
							userInput.setText(lastDeviceName);
						}
						TextView dialogTitle = (TextView)dialogView.findViewById(R.id.dialogPrompt);
						dialogTitle.setText("Enter the device name");
						builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					
							@Override
							public void onClick(DialogInterface dialog, int which) {
								String name = userInput.getText().toString();
								BluetoothDevice device = BLE.get().getDevice(name);
								if (device == null)	 {
									debug(name + " not found");
									toast(name + "not found");
									return;
								}
								debug("Connect " + name);
								lastDeviceName = name;
								bleDevice = device;	
								if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {														
									bleGatt = bleDevice.connectGatt(MainActivity.this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
								}
								else {
									bleGatt = bleDevice.connectGatt(MainActivity.this, false, gattCallback);	
								}
								bleDeviceOpen = false;
								ledgerDevice = new LedgerDeviceBLE(bleGatt);
								ledgerDevice.setDebug(true);								
							}
						});
						builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
					
							@Override
							public void onClick(DialogInterface dialog, int which) {
								dialog.cancel();
							}
						});
						builder.setCancelable(false);
						builder.show();				        		
        	}
        });
				closeBLEButton.setOnClickListener(new OnClickListener() {
        	@Override
        	public void onClick(View view) {
        		if ((ledgerDevice != null) && (ledgerDevice instanceof LedgerDeviceBLE)) {
        			ledgerDevice.close();
        			bleGatt.disconnect();
        			bleGattClosing = true;
        			toast("BLE device closed");
        		}
        		else {
        			toast("No BLE device connected");
        		}
        	}
				});
        getAppVersionButton.setOnClickListener(new OnClickListener() {
        		@Override
        		public void onClick(View view) {        			
        			if (ledgerDevice == null) {
        				toast("No device connected");
        				return;
        			}
        			Tasks.get().getAppVersion(ledgerDevice, MainActivity.this).execute();
        		}
        });        
        getWalletIDButton.setOnClickListener(new OnClickListener() {
        		@Override
        		public void onClick(View view) {        			
        			if (ledgerDevice == null) {
        				toast("No device connected");
        				return;
        			}
        			Tasks.get().getWalletID(ledgerDevice, MainActivity.this).execute();
        		}
        });                
        getDeviceVersionButton.setOnClickListener(new OnClickListener() {
        		@Override
        		public void onClick(View view) {        			
        			if (ledgerDevice == null) {
        				toast("No device connected");
        				return;
        			}
        			Tasks.get().getDeviceVersion(ledgerDevice, MainActivity.this).execute();
        		}
        });                
        ethGetAddressButton.setOnClickListener(new OnClickListener() {
        		@Override
        		public void onClick(View view) {        			
        			if (ledgerDevice == null) {
        				toast("No device connected");
        				return;
        			}
        			Tasks.get().ethGetAddress(ledgerDevice, MainActivity.this).execute();
        		}
        });                
        ethSignTX.setOnClickListener(new OnClickListener() {
        		@Override
        		public void onClick(View view) {        			
        			if (ledgerDevice == null) {
        				toast("No device connected");
        				return;
        			}
        			Tasks.get().ethSign(ledgerDevice, MainActivity.this).execute();
        		}
        });                        
        ethSignERC20TX.setOnClickListener(new OnClickListener() {
        		@Override
        		public void onClick(View view) {        			
        			if (ledgerDevice == null) {
        				toast("No device connected");
        				return;
        			}
        			debug("Loading ERC20 cache");
        			Erc20Cache.loadCacheInternal(getApplicationContext());
        			debug("ERC20 cache loaded");        			
        			Tasks.get().ethSignERC20(ledgerDevice, MainActivity.this).execute();
        		}
        });                                
        ethSignLongTX.setOnClickListener(new OnClickListener() {
        		@Override
        		public void onClick(View view) {        			
        			if (ledgerDevice == null) {
        				toast("No device connected");
        				return;
        			}
        			Tasks.get().ethSignLong(ledgerDevice, MainActivity.this).execute();
        		}
        });                                
        btcGetAddress.setOnClickListener(new OnClickListener() {
        		@Override
        		public void onClick(View view) {        			
        			if (ledgerDevice == null) {
        				toast("No device connected");
        				return;
        			}
        			Tasks.get().btcGetAddress(ledgerDevice, MainActivity.this).execute();
        		}
        });                        
        btcSignTX.setOnClickListener(new OnClickListener() {
        		@Override
        		public void onClick(View view) {        			
        			if (ledgerDevice == null) {
        				toast("No device connected");
        				return;
        			}
        			Tasks.get().btcSignTX(ledgerDevice, MainActivity.this).execute();
        		}
        });                                

        mgtGetApps.setOnClickListener(new OnClickListener() {
        		@Override
        		public void onClick(View view) {
        			if (ledgerDevice == null) {
        				toast("No device connected");
        				return;
        			}        			
        			Tasks.get().getManagerApplications(13, ledgerDevice, managerService, MainActivity.this).execute();
        			//Tasks.get().getManagerApplications(1, 0x31100004, "1.5.5", managerService, MainActivity.this).execute();
        		}
        });

        mgtGetFirmware.setOnClickListener(new OnClickListener() {
        		@Override
        		public void onClick(View view) {
        			if (ledgerDevice == null) {
        				toast("No device connected");
        				return;
        			}        			
        			Tasks.get().getManagerLatestFirmware(13, ledgerDevice, managerService, MainActivity.this).execute();
        			//Tasks.get().getManagerLatestFirmware(1, 0x31100003, "1.4.2", managerService, MainActivity.this).execute();
        			//Tasks.get().getManagerLatestFirmware(1, 0x31100004, "1.5.5", managerService, MainActivity.this).execute();
        		}
        });

        mgtGenuineCheck.setOnClickListener(new OnClickListener() {
        		@Override
        		public void onClick(View view) {
        			if (ledgerDevice == null) {
        				toast("No device connected");
        				return;
        			}      
        			Tasks.get().runGenuineCheck(ledgerDevice, client, MainActivity.this).execute();  			
        		}
        });

        mgtLifecycle.setOnClickListener(new OnClickListener() {
        		@Override
        		public void onClick(View view) {
        			if (ledgerDevice == null) {
        				toast("No device connected");
        				return;
        			}      
        			Tasks.get().runLifecycle("nanos/1.5.5/ethereum/app_1.2.3", "nanos/1.5.5/ethereum/app_1.2.3_key", ledgerDevice, client, MainActivity.this).execute();  			
        		}
        });

        /* Set up USB broadcast receiver and callbacks */

				usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
				IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
				filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
				registerReceiver(usbReceiver, filter);     

				/* Set up BLE broadcast receiver */

				filter = new IntentFilter();
				filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
				registerReceiver(bleBondReceiver, filter);

				/* Set up management API */

				//managerService = ManagerServiceHelper.getDefaultManagerService();

				Moshi moshi = new Moshi.Builder()
					.add(Date.class, new Rfc3339DateJsonAdapter().nullSafe())
					.build();
				HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
				interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
				client = new OkHttpClient.Builder()
					.addInterceptor(interceptor)
					.build();
				Retrofit retrofit = new Retrofit.Builder()
					.baseUrl(LEDGER_MANAGER_URL)
					.addConverterFactory(MoshiConverterFactory.create(moshi))
					.client(client)
					.build();
				managerService = retrofit.create(ManagerService.class);				
    }
}
