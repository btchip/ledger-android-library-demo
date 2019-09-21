package com.ledger.demolib;

import java.lang.ref.WeakReference;
import java.util.List;

import android.os.AsyncTask;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDevice;
import android.bluetooth.BluetoothGatt;
import android.util.Log;

import retrofit2.Response;

import okhttp3.OkHttpClient;

import com.ledger.lib.transport.LedgerDevice;
import com.ledger.lib.transport.LedgerDeviceUSB;
import com.ledger.lib.transport.LedgerDeviceBLE;
import com.ledger.lib.apps.LedgerApplication;
import com.ledger.lib.apps.eth.Eth;
import com.ledger.lib.apps.dashboard.Dashboard;
import com.ledger.lib.apps.common.WalletAddress;
import com.ledger.lib.apps.common.ECDSADeviceSignature;
import com.ledger.lib.utils.Dump;

import com.ledger.management.lib.ManagerService;
import com.ledger.management.lib.model.GetDeviceVersionParameters;
import com.ledger.management.lib.model.DeviceVersion;
import com.ledger.management.lib.model.GetCurrentFirmwareParameters;
import com.ledger.management.lib.model.FinalFirmware;
import com.ledger.management.lib.model.GetApplicationsByDeviceParameters;
import com.ledger.management.lib.model.LatestFirmware;
import com.ledger.management.lib.model.GetLatestFirmwareParameters;
import com.ledger.management.lib.model.ApplicationVersion;
import com.ledger.management.lib.model.ApplicationVersionList;
import com.ledger.management.lib.model.FinalFirmware;
import com.ledger.management.lib.GenuineCheckService;
import com.ledger.management.lib.LifecycleService;

public class Tasks {

		/* Sample BIP 32 path used for the address and given transactions */
		private static final String SAMPLE_ETH_ADDRESS = "44'/60'/0'/0'/0";
		/* Simple ETH transaction without data */
		// 0.01234 max fees 0.000441 to 0x28Ee52a8f3D6E5d15f8B131996950D7F296C7952
		// 1b99d4b65691b71fca885bbce5a3647f0b7fe59672fb5aa79046ee26ba9872aecd470dc9e3bd38e439a3a30e451e6423f6b32746d0779aa996ac5e31be9e8a21119000
		private static final byte[] SAMPLE_ETH_TX = Dump.hexToBin("e8018504e3b292008252089428ee52a8f3d6e5d15f8b131996950d7f296c7952872bd72a2487400080");
		/* Fragmented ETH transaction with data */
		private static final byte[] SAMPLE_LONG_ETH_TX = Dump.hexToBin("f9010d018504e3b2920082520894e41d2489571d322189246dafa5ebde1f4699f498872bd72a24874000b8e4010203040000000000000000000000000000000000000000000000000000000000000000111111111111111111111111111111111111111111111111111111111111111122222222222222222222222222222222222222222222222222222222222222223333333333333333333333333333333333333333333333333333333333333333444444444444444444444444444444444444444444444444444444444444444455555555555555555555555555555555555555555555555555555555555555556666666666666666666666666666666666666666666666666666666666666666");		
		/* Simple ERC 20 token transfer */
		// ZRX 42 max fees 0.000441 to 0x28Ee52a8f3D6E5d15f8B131996950D7F296C7952
		// 1c9eb8502b3d19d6c4a936e5dd2cde3ec7d2437f79cea92d09b8bc6b9773f4223d34fc4b91b4b880d40f16ade62f2bd25a4906f1518867f0ba4d7bfa54e15732b19000		
		private static final byte[] SAMPLE_ETH_ERC20_TX = Dump.hexToBin("f86d018504e3b2920082520894e41d2489571d322189246dafa5ebde1f4699f498872bd72a24874000b844a9059cbb000000000000000000000000e41d2489571d322189246dafa5ebde1f4699f49800000000000000000000000000000000000000000000000246ddf97976680000");
		private static final String SAMPLE_ETH_ERC20_CONTRACT_ADDRESS = "0xe41d2489571d322189246dafa5ebde1f4699f498";

		private static final String PERSO_PROD = "perso_11";
		private static final int API_VERSION = 17;

		private static Tasks singleton = null;

		/* Connect to the device and returns a high level communication object */

		class ConnectLedgerDeviceUSB extends AsyncTask<Void, Void, LedgerDeviceUSB> {

			private UsbManager usbManager;
			private UsbDevice device;
			private WeakReference<MainActivity> weakActivity;

			public ConnectLedgerDeviceUSB(UsbManager usbManager, UsbDevice device, MainActivity activity) {
				this.usbManager = usbManager;
				this.device = device;
				this.weakActivity = new WeakReference<>(activity);
			}

			protected LedgerDeviceUSB doInBackground(Void... params) {
				LedgerDeviceUSB ledgerDevice = new LedgerDeviceUSB(usbManager, device);
				ledgerDevice.setDebug(true);
				try {
					ledgerDevice.open();
				}
				catch(Exception e) {
					e.printStackTrace();
					ledgerDevice = null;
				}
				return ledgerDevice;
			}

			protected void onPostExecute(LedgerDeviceUSB result) {
				MainActivity activity = weakActivity.get();
				if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
					return;
				}
				activity.setLedgerDevice(result);
				if (result != null) {					
					activity.toast("Device connected");
				}
				else {
					activity.toast("Error connecting device");
				}
			}
		}

		class ConnectLedgerDeviceBLE extends AsyncTask<Void, Void, Boolean> {

			private LedgerDeviceBLE device;
			private WeakReference<MainActivity> weakActivity;

			public ConnectLedgerDeviceBLE(LedgerDeviceBLE device, MainActivity activity) {
				this.device = device;
				this.weakActivity = new WeakReference<>(activity);
			}

			protected Boolean doInBackground(Void... params) {
				try {
					device.open();
				}
				catch(Exception e) {
					e.printStackTrace();
					return false;
				}
				return true;
			}

			protected void onPostExecute(Boolean result) {
				MainActivity activity = weakActivity.get();
				if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
					return;
				}
				if (result) {					
					activity.toast("Device connected");
				}
				else {
					activity.toast("Error connecting device");
				}
			}
		}

		/* Fetch the generic application version */

		class GetAppVersion extends AsyncTask<Void, Void, LedgerApplication.ApplicationDetails> {

			private LedgerDevice device;
			private WeakReference<MainActivity> weakActivity;

			public GetAppVersion(LedgerDevice device, MainActivity activity) {
				this.device = device;
				this.weakActivity = new WeakReference<>(activity);
			}

			protected LedgerApplication.ApplicationDetails doInBackground(Void... params) {
				LedgerApplication.ApplicationDetails applicationDetails = null;
				LedgerApplication application = new LedgerApplication(device);
				try {
					applicationDetails = application.getApplicationDetails();
				}
				catch(Exception e) {
					e.printStackTrace();
				}
				return applicationDetails;
			}

			protected void onPostExecute(LedgerApplication.ApplicationDetails applicationDetails) {
				MainActivity activity = weakActivity.get();
				if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
					return;
				}				
				if (applicationDetails != null) {
					activity.toast("Got application details");
					activity.debug(applicationDetails.toString());
				}
				else {
					activity.toast("Error getting application details");
				}
			}
		}

		/* Fetch the wallet ID */

		class GetWalletID extends AsyncTask<Void, Void, byte[]> {

			private LedgerDevice device;
			private WeakReference<MainActivity> weakActivity;

			public GetWalletID(LedgerDevice device, MainActivity activity) {
				this.device = device;
				this.weakActivity = new WeakReference<>(activity);
			}

			protected byte[] doInBackground(Void... params) {
				byte[] walletID = null;
				LedgerApplication application = new LedgerApplication(device);
				try {
					walletID = application.getWalletID();
				}
				catch(Exception e) {
					e.printStackTrace();
				}
				return walletID;
			}

			protected void onPostExecute(byte[] walletID) {
				MainActivity activity = weakActivity.get();
				if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
					return;
				}				
				if (walletID != null) {
					activity.toast("Got wallet ID");
					activity.debug(Dump.dump(walletID));
				}
				else {
					activity.toast("Error getting Wallet ID");
				}
			}
		}

		/* Fetch the device version */

		class GetDeviceVersion extends AsyncTask<Void, Void, Dashboard.DeviceDetails> {

			private LedgerDevice device;
			private WeakReference<MainActivity> weakActivity;

			public GetDeviceVersion(LedgerDevice device, MainActivity activity) {
				this.device = device;
				this.weakActivity = new WeakReference<>(activity);
			}

			protected Dashboard.DeviceDetails doInBackground(Void... params) {
				Dashboard.DeviceDetails deviceDetails = null;
				Dashboard dashboard = new Dashboard(device);
				try {
					deviceDetails = dashboard.getDeviceDetails();
				}
				catch(Exception e) {
					e.printStackTrace();
				}
				return deviceDetails;
			}

			protected void onPostExecute(Dashboard.DeviceDetails deviceDetails) {
				MainActivity activity = weakActivity.get();
				if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
					return;
				}				
				if (deviceDetails != null) {
					activity.toast("Got device details");
					activity.debug(deviceDetails.toString());
				}
				else {
					activity.toast("Error getting device details");
				}
			}
		}


		/* Get an Ethereum address */

		class EthGetAddress extends AsyncTask<Void, Void, WalletAddress> {

			private LedgerDevice device;
			private WeakReference<MainActivity> weakActivity;

			public EthGetAddress(LedgerDevice device, MainActivity activity) {
				this.device = device;
				this.weakActivity = new WeakReference<>(activity);
			}

			protected WalletAddress doInBackground(Void... params) {
				WalletAddress walletAddress = null;
				Eth application = new Eth(device);
				try {
					LedgerApplication.ApplicationDetails applicationDetails = application.getApplicationDetails();
					walletAddress = application.getWalletAddress(SAMPLE_ETH_ADDRESS, true);
				}
				catch(Exception e) {
					e.printStackTrace();
				}
				return walletAddress;
			}

			protected void onPostExecute(WalletAddress walletAddress) {
				MainActivity activity = weakActivity.get();
				if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
					return;
				}				
				if (walletAddress != null) {
					activity.toast(walletAddress.getAddress());
					activity.debug(walletAddress.toString());
				}
				else {
					activity.toast("Error getting wallet address");
				}
			}
		}

		/* Sign a simple Ethereum transaction */

		class EthSign extends AsyncTask<Void, Void, ECDSADeviceSignature> {

			private LedgerDevice device;
			private WeakReference<MainActivity> weakActivity;

			public EthSign(LedgerDevice device, MainActivity activity) {
				this.device = device;
				this.weakActivity = new WeakReference<>(activity);
			}

			protected ECDSADeviceSignature doInBackground(Void... params) {
				ECDSADeviceSignature deviceSignature = null;
				Eth application = new Eth(device);
				try {
					LedgerApplication.ApplicationDetails applicationDetails = application.getApplicationDetails();
					deviceSignature = application.signTransaction(SAMPLE_ETH_ADDRESS, SAMPLE_ETH_TX);
				}
				catch(Exception e) {
					e.printStackTrace();
				}
				return deviceSignature;
			}

			protected void onPostExecute(ECDSADeviceSignature deviceSignature) {
				MainActivity activity = weakActivity.get();
				if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
					return;
				}				
				if (deviceSignature != null) {
					activity.toast("TX signed");
					activity.debug(deviceSignature.toString());
				}
				else {
					activity.toast("Error signing transaction");
				}
			}
		}

		/* Sign a long Ethereum transaction */

		class EthSignLong extends AsyncTask<Void, Void, ECDSADeviceSignature> {

			private LedgerDevice device;
			private WeakReference<MainActivity> weakActivity;

			public EthSignLong(LedgerDevice device, MainActivity activity) {
				this.device = device;
				this.weakActivity = new WeakReference<>(activity);
			}

			protected ECDSADeviceSignature doInBackground(Void... params) {
				ECDSADeviceSignature deviceSignature = null;
				Eth application = new Eth(device);
				try {
					LedgerApplication.ApplicationDetails applicationDetails = application.getApplicationDetails();
					deviceSignature = application.signTransaction(SAMPLE_ETH_ADDRESS, SAMPLE_LONG_ETH_TX);
				}
				catch(Exception e) {
					e.printStackTrace();
				}
				return deviceSignature;
			}

			protected void onPostExecute(ECDSADeviceSignature deviceSignature) {
				MainActivity activity = weakActivity.get();
				if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
					return;
				}				
				if (deviceSignature != null) {
					activity.toast("TX signed");
					activity.debug(deviceSignature.toString());
				}
				else {
					activity.toast("Error signing transaction");
				}
			}
		}

		/* Sign an ERC 20 Ethereum transaction */

		class EthSignERC20 extends AsyncTask<Void, Void, ECDSADeviceSignature> {

			private LedgerDevice device;
			private WeakReference<MainActivity> weakActivity;

			public EthSignERC20(LedgerDevice device, MainActivity activity) {
				this.device = device;
				this.weakActivity = new WeakReference<>(activity);
			}

			protected ECDSADeviceSignature doInBackground(Void... params) {
				ECDSADeviceSignature deviceSignature = null;
				Eth application = new Eth(device);
				try {
					LedgerApplication.ApplicationDetails applicationDetails = application.getApplicationDetails();
					byte[] tokenInfos = application.getErc20TokenInformation(SAMPLE_ETH_ERC20_CONTRACT_ADDRESS);
					deviceSignature = application.signErc20Transaction(SAMPLE_ETH_ADDRESS, SAMPLE_ETH_ERC20_TX, tokenInfos);
				}
				catch(Exception e) {
					e.printStackTrace();
				}
				return deviceSignature;
			}

			protected void onPostExecute(ECDSADeviceSignature deviceSignature) {
				MainActivity activity = weakActivity.get();
				if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
					return;
				}				
				if (deviceSignature != null) {
					activity.toast("ERC20 TX signed");
					activity.debug(deviceSignature.toString());
				}
				else {
					activity.toast("Error signing ERC20 transaction");
				}
			}
		}

		/* Get available applications from the Manager API */

		class GetManagerApplications extends AsyncTask<Void, Void, ApplicationVersionList> {

			private LedgerDevice device;
			private GetDeviceVersionParameters parameters;
			private WeakReference<MainActivity> weakActivity;
			private ManagerService managerService;
			private String versionName;

			public GetManagerApplications(long provider, LedgerDevice device, ManagerService managerService, MainActivity activity) {
				parameters = new GetDeviceVersionParameters();
				parameters.provider = provider;
				this.device = device;
				this.managerService = managerService;
				this.weakActivity = new WeakReference<>(activity);
			}				

			public GetManagerApplications(long provider, long targetId, String versionName, ManagerService managerService, MainActivity activity) {
				parameters = new GetDeviceVersionParameters();
				parameters.provider = provider;
				parameters.targetId = targetId;
				this.versionName = versionName;
				this.managerService = managerService;
				this.weakActivity = new WeakReference<>(activity);
			}

			protected ApplicationVersionList doInBackground(Void... params) {
				DeviceVersion deviceVersion = null;
				FinalFirmware firmware = null;
				ApplicationVersionList applications = null;
				try {
					if (device != null) {
						Dashboard dashboard = new Dashboard(device);
						Dashboard.DeviceDetails deviceDetails = dashboard.getDeviceDetails();						
						parameters.targetId = deviceDetails.getTargetId();
						this.versionName = deviceDetails.getVersion();
					}
					Response<DeviceVersion> response1 = managerService.getDeviceVersion(parameters, API_VERSION).execute();
					if (!response1.isSuccessful()) {
						throw new RuntimeException("getDeviceVersion response error " + response1);
					}
					deviceVersion = response1.body();
					Response<FinalFirmware> response2 = managerService.getCurrentFirmware(
						new GetCurrentFirmwareParameters(parameters.provider, versionName, deviceVersion), API_VERSION).execute();
					if (!response2.isSuccessful()) {
						throw new RuntimeException("getCurrentFirmware response error " + response2);
					}
					firmware = response2.body();
					Response<ApplicationVersionList> response3 = managerService.getApplicationsByDevice(
						new GetApplicationsByDeviceParameters(parameters.provider, deviceVersion, firmware), API_VERSION).execute();
					if (!response3.isSuccessful()) {
						throw new RuntimeException("getApplicationsByDevice response error " + response3);
					}
					applications = response3.body();
				}
				catch(Exception e) {
					e.printStackTrace();
				}
				return applications;
			}

			protected void onPostExecute(ApplicationVersionList applications) {
				MainActivity activity = weakActivity.get();
				if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
					return;
				}				
				if (applications != null) {
					activity.toast("Got applications");
					for (ApplicationVersion applicationVersion : applications.applicationVersionList) {
						activity.debug(applicationVersion.name + " " + applicationVersion.version);
					}
				}
				else {
					activity.toast("Error getting available applications");
				}
			}
		}

		/* Get latest firmware available from the Manager API */

		class GetManagerLatestFirmware extends AsyncTask<Void, Void, LatestFirmware> {

			private LedgerDevice device;
			private GetDeviceVersionParameters parameters;
			private WeakReference<MainActivity> weakActivity;
			private ManagerService managerService;
			private String versionName;

			public GetManagerLatestFirmware(long provider, LedgerDevice device, ManagerService managerService, MainActivity activity) {
				parameters = new GetDeviceVersionParameters();
				parameters.provider = provider;
				this.device = device;
				this.managerService = managerService;
				this.weakActivity = new WeakReference<>(activity);
			}

			public GetManagerLatestFirmware(long provider, long targetId, String versionName, ManagerService managerService, MainActivity activity) {
				parameters = new GetDeviceVersionParameters();
				parameters.provider = provider;
				parameters.targetId = targetId;
				this.versionName = versionName;
				this.managerService = managerService;
				this.weakActivity = new WeakReference<>(activity);
			}

			protected LatestFirmware doInBackground(Void... params) {
				DeviceVersion deviceVersion = null;
				FinalFirmware firmware = null;
				LatestFirmware osuFirmware = null;
				try {
					if (device != null) {
						Dashboard dashboard = new Dashboard(device);
						Dashboard.DeviceDetails deviceDetails = dashboard.getDeviceDetails();						
						parameters.targetId = deviceDetails.getTargetId();
						this.versionName = deviceDetails.getVersion();
					}					
					Response<DeviceVersion> response1 = managerService.getDeviceVersion(parameters, API_VERSION).execute();
					if (!response1.isSuccessful()) {
						throw new RuntimeException("getDeviceVersion response error " + response1);
					}
					deviceVersion = response1.body();
					Response<FinalFirmware> response2 = managerService.getCurrentFirmware(
						new GetCurrentFirmwareParameters(parameters.provider, versionName, deviceVersion), API_VERSION).execute();
					if (!response2.isSuccessful()) {
						throw new RuntimeException("getCurrentFirmware response error " + response2);
					}
					firmware = response2.body();
					Response<LatestFirmware> response3 = managerService.getLatestFirmware(
						new GetLatestFirmwareParameters(parameters.provider, deviceVersion, firmware), API_VERSION).execute();
					if (!response3.isSuccessful()) {
						throw new RuntimeException("getLatestFirmware response error " + response3);
					}
					osuFirmware = response3.body();
					if (osuFirmware.successful()) {
						Response<FinalFirmware> response4 = managerService.getFinalFirmwareById(
							osuFirmware.osuFirmware.nextSeFirmwareFirmwareVersion, API_VERSION).execute();
						if (response4.isSuccessful()) {
							osuFirmware.nextFirmware = response4.body();
						}
					}
				}
				catch(Exception e) {
					e.printStackTrace();
				}
				return osuFirmware;
			}

			protected void onPostExecute(LatestFirmware firmware) {
				MainActivity activity = weakActivity.get();
				if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
					return;
				}				
				if (firmware != null) {
					activity.toast("Got firmware");
					if (!firmware.successful()) {
						activity.debug("No firmware available");
					}
					else {
						activity.debug("New firmware available, OSU " + firmware.osuFirmware.name);
						if (firmware.nextFirmware != null) {
							activity.debug("Next firmware " + firmware.nextFirmware.name);
						}
					}
				}
				else {
					activity.toast("Error getting latest firmware");
				}
			}
		}

		/* Run a remote Genuine Check */

		class RunGenuineCheck extends AsyncTask<Void, Void, String> {

			private LedgerDevice device;
			private WeakReference<MainActivity> weakActivity;
			private OkHttpClient client;

			public RunGenuineCheck(LedgerDevice device, OkHttpClient client, MainActivity activity) {
				this.device = device;
				this.client = client;
				this.weakActivity = new WeakReference<>(activity);
			}
			protected String doInBackground(Void... params) {
				String response = null;
				try {
					Dashboard dashboard = new Dashboard(device);
					Dashboard.DeviceDetails deviceDetails = dashboard.getDeviceDetails();						
					GenuineCheckService genuineCheckService = GenuineCheckService.getGenuineCheckService(client, device, deviceDetails.getTargetId(), PERSO_PROD);
					genuineCheckService.setDebug(true);
					response = genuineCheckService.start();					
				}
				catch(Exception e) {
					e.printStackTrace();
				}
				return response;
			}

			protected void onPostExecute(String response) {
				MainActivity activity = weakActivity.get();
				if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
					return;
				}				
				if (response != null) {
					activity.toast("Genuine Check successful");
					activity.debug("Genuine Check " + response);
				}
				else {
					activity.toast("Error running Genuine Check");
				}
			}
		}

		/* Perform a lifecycle management operation */

		class RunLifecycle extends AsyncTask<Void, Void, String> {

			private LedgerDevice device;
			private String firmware;
			private String firmwareKey;
			private WeakReference<MainActivity> weakActivity;
			private OkHttpClient client;

			public RunLifecycle(String firmware, String firmwareKey, LedgerDevice device, OkHttpClient client, MainActivity activity) {
				this.firmware = firmware;
				this.firmwareKey = firmwareKey;
				this.device = device;
				this.client = client;
				this.weakActivity = new WeakReference<>(activity);
			}
			protected String doInBackground(Void... params) {
				String response = null;
				try {
					Dashboard dashboard = new Dashboard(device);
					Dashboard.DeviceDetails deviceDetails = dashboard.getDeviceDetails();						
					LifecycleService lifecycleService = LifecycleService.getLifecycleService(client, device, 
						deviceDetails.getTargetId(), PERSO_PROD, firmware, firmwareKey);
					lifecycleService.setDebug(true);
					response = lifecycleService.start();					
				}
				catch(Exception e) {
					e.printStackTrace();
				}
				return response;
			}

			protected void onPostExecute(String response) {
				MainActivity activity = weakActivity.get();
				if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
					return;
				}				
				if (response != null) {
					activity.toast("Lifecycle successful");
					activity.debug("Lifecycle " + response);
				}
				else {
					activity.toast("Error running Lifecycle");
				}
			}
		}

		public ConnectLedgerDeviceUSB connectLedgerDeviceUSB(UsbManager usbManager, UsbDevice device, MainActivity activity) {
			return new ConnectLedgerDeviceUSB(usbManager, device, activity);
		}

		public ConnectLedgerDeviceBLE connectLedgerDeviceBLE(LedgerDeviceBLE device, MainActivity activity) {
			return new ConnectLedgerDeviceBLE(device, activity);
		}

		public GetAppVersion getAppVersion(LedgerDevice device, MainActivity activity) {
			return new GetAppVersion(device, activity);
		}

		public GetWalletID getWalletID(LedgerDevice device, MainActivity activity) {
			return new GetWalletID(device, activity);
		}

		public GetDeviceVersion getDeviceVersion(LedgerDevice device, MainActivity activity) {
			return new GetDeviceVersion(device, activity);
		}

		public EthGetAddress ethGetAddress(LedgerDevice device, MainActivity activity) {
			return new EthGetAddress(device, activity);
		}

		public EthSign ethSign(LedgerDevice device, MainActivity activity) {
			return new EthSign(device, activity);
		}

		public EthSignLong ethSignLong(LedgerDevice device, MainActivity activity) {
			return new EthSignLong(device, activity);
		}

		public EthSignERC20 ethSignERC20(LedgerDevice device, MainActivity activity) {
			return new EthSignERC20(device, activity);
		}

		public GetManagerApplications getManagerApplications(long provider, long targetId, String versionName, ManagerService managerService, MainActivity activity) {
			return new GetManagerApplications(provider, targetId, versionName, managerService, activity);
		}

		public GetManagerApplications getManagerApplications(long provider, LedgerDevice device, ManagerService managerService, MainActivity activity) {
			return new GetManagerApplications(provider, device, managerService, activity);
		}

		public GetManagerLatestFirmware getManagerLatestFirmware(long provider, long targetId, String versionName, ManagerService managerService, MainActivity activity) {
			return new GetManagerLatestFirmware(provider, targetId, versionName, managerService, activity);
		}

		public GetManagerLatestFirmware getManagerLatestFirmware(long provider, LedgerDevice device, ManagerService managerService, MainActivity activity) {
			return new GetManagerLatestFirmware(provider, device, managerService, activity);
		}

		public RunGenuineCheck runGenuineCheck(LedgerDevice device, OkHttpClient client, MainActivity activity) {
			return new RunGenuineCheck(device, client, activity);
		}

		public RunLifecycle runLifecycle(String firmware, String firmwareKey, LedgerDevice device, OkHttpClient client, MainActivity activity) {
			return new RunLifecycle(firmware, firmwareKey, device, client, activity);
		}		

		public static synchronized Tasks get() {
			if (singleton == null) {
				singleton = new Tasks();
			}
			return singleton;
		}
}
