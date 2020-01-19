package com.ledger.demolib;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Vector;

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
import com.ledger.lib.apps.btc.Btc;
import com.ledger.lib.apps.btc.BtcTransaction;
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
		/* Sample BIP 32 path used for the address and given transactions */
		private static final String SAMPLE_BTC_ADDRESS = "44'/0'/0'/0/0";		
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

		private static final byte[] SAMPLE_BTC_TX_P2PKH_1 = Dump.hexToBin("02000000000101bb759715dd46ac8c8d00c17c5312a4dca74ebd298b735f07a472873b142abb4e0100000000feffffff0200e1f505000000001976a914bc6004631250401c3454d0713f539315ea10214588acc4984c000100000017a9146b4132f56e81118d2e42681b91cb4748e67b0b0d870247304402204d8a34cb23bec0d235d84d3d5a4b80d4bab2b6f1bcb2c257c9143e6a0e93be250220282cc7232f7a727c6c38b4c0b2e360b9bc4283295d29b607a85d5f558beffe0e01210238ee75891d7d4644de3164bbee8c382a78bf469c689d33361ae0f006e276a39b00000000");
		private static final byte[] SAMPLE_BTC_TX_P2PKH_2 = Dump.hexToBin("020000000001012b68364d5eb817a1ee6ed12332942dec40dd1b7b24fb54f000dba7bfab0c2fc500000000171600142866b1d53df139ab23311caf57a21b034f1965f4feffffff02e0221a1e0100000017a91460d1030c72fbcd8e96e0c9582aa518f67513823e8700c2eb0b000000001976a914bc6004631250401c3454d0713f539315ea10214588ac024730440220478031dbe0ac10693311fc83e907bf518a6226dc58d44f64cfa6bc52b0f0f53202200b65330dce2c606142d2c104e391e32b91668e54d0bd895ca2566fe021c196d0012102a1e834c7b6db596a113ebee7261170cc4d4e80db4a0e51bf862627e7b7150e9400000000");
		private static final byte[] SAMPLE_BTC_TX_P2SH_P2WPKH = Dump.hexToBin("0200000000010129d2010aeff2de839e2718c52cf230114e772383e1b2a94b60a0dd00f231e81d00000000171600142866b1d53df139ab23311caf57a21b034f1965f4feffffff0200a3e1110000000017a914818b33fafe6a7f92caec0e989aaaa40507d8b3af87084224180100000017a914778c699e0e43b526727883bdae1fe201a58d66008702473044022035df7007b8521fa847b0bfcf0dd1a831d82076393bc21e955140b8c6fe030eeb02206e59e12a9aa886d6ccae2f7ce4ed1e293bb8835ad3b6c86596d0d8d1af23ff2a012102a1e834c7b6db596a113ebee7261170cc4d4e80db4a0e51bf862627e7b7150e9400000000");
		private static final byte[] SAMPLE_BTC_TX_P2WPKH = Dump.hexToBin("02000000000101278d0297350e201061ac9ba6b8d77046d0d9de0a84a35cd77545ba856d6f3c9700000000171600142866b1d53df139ab23311caf57a21b034f1965f4feffffff0230612e12010000001600149f451f15ef55ab5ab7350d3178e4fd185706a5610084d71700000000160014bc6004631250401c3454d0713f539315ea1021450247304402202d050d3cabd4c8374e5cb1a5f010a6688e39323512f415a5679bb398e8f942500220386ebebf18ac9fddf8b380382b4dfd4935ed5064796e9fd6d0b6e7396faeef3d012102a1e834c7b6db596a113ebee7261170cc4d4e80db4a0e51bf862627e7b7150e9400000000");
		private static final byte[] SAMPLE_BTC_TX_UNSIGNED = Dump.hexToBin("02000000046574ea263c98ff94f5a68007bc64d6a85f6a50e7257b5ad4c057f04ffd1ac2fd0000000000ffffffff3ba3ee374f7cf835827a8269c4da3241570e32fefa95ba2f5d795fdf2353dab10000000000ffffffffa3c51018e49c6a68af5d487c6327bdea259aeeb4d8a89535e62284308ec4f0e30100000000ffffffff6d81e45f2bd62ce183de7050f7c3434a1669a656a11457936dc9b65a4eb24d7f0100000000ffffffff01c0878b3b0000000017a914e929bf3837c2a0f42e9c57754cf27ca6f64c79488700000000");

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

		/* Get an Bitcoin address */

		class BtcGetAddress extends AsyncTask<Void, Void, WalletAddress> {

			private LedgerDevice device;
			private WeakReference<MainActivity> weakActivity;

			public BtcGetAddress(LedgerDevice device, MainActivity activity) {
				this.device = device;
				this.weakActivity = new WeakReference<>(activity);
			}

			protected WalletAddress doInBackground(Void... params) {
				WalletAddress walletAddress = null;
				Btc application = new Btc(device);
				try {
					LedgerApplication.ApplicationDetails applicationDetails = application.getApplicationDetails();
					walletAddress = application.getWalletAddress(SAMPLE_BTC_ADDRESS, true, Btc.AddressFormat.BECH32);
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


		/* Sign a mixed Legacy/Segwit BTC transaction */

		class BtcSignTX extends AsyncTask<Void, Void, byte[]> {

			private LedgerDevice device;
			private WeakReference<MainActivity> weakActivity;

			public BtcSignTX(LedgerDevice device, MainActivity activity) {
				this.device = device;
				this.weakActivity = new WeakReference<>(activity);
			}

			protected byte[] doInBackground(Void... params) {
				byte[] signedTX = null;
				Btc application = new Btc(device);
				try {
					BtcTransaction sample_btc_tx_p2pkh_1 = new BtcTransaction(SAMPLE_BTC_TX_P2PKH_1);
					BtcTransaction sample_btc_tx_p2pkh_2 = new BtcTransaction(SAMPLE_BTC_TX_P2PKH_2);
					BtcTransaction sample_btc_tx_p2sh_p2wpkh = new BtcTransaction(SAMPLE_BTC_TX_P2SH_P2WPKH);
					BtcTransaction sample_btc_tx_p2wpkh = new BtcTransaction(SAMPLE_BTC_TX_P2WPKH);
					BtcTransaction sample_btc_tx_unsigned = new BtcTransaction(SAMPLE_BTC_TX_UNSIGNED);
					Vector<BtcTransaction> parentTransactions = new Vector<BtcTransaction>();
					parentTransactions.add(sample_btc_tx_p2pkh_1);
					parentTransactions.add(sample_btc_tx_p2pkh_2);
					parentTransactions.add(sample_btc_tx_p2sh_p2wpkh);
					parentTransactions.add(sample_btc_tx_p2wpkh);
					Vector<String> associatedKeysets = new Vector<String>();
					associatedKeysets.add("44'/0'/0'/0/0");
					associatedKeysets.add("44'/0'/0'/0/0");
					associatedKeysets.add("44'/0'/0'/0/0");
					associatedKeysets.add("44'/0'/0'/0/0");
					LedgerApplication.ApplicationDetails applicationDetails = application.getApplicationDetails();
					BtcTransaction signedTransaction = application.signP2PKHTransaction(sample_btc_tx_unsigned, parentTransactions, associatedKeysets, null);
					if (signedTransaction != null) {
						signedTX = signedTransaction.serialize(false, false);
					}
				}
				catch(Exception e) {
					e.printStackTrace();
				}
				return signedTX;
			}

			protected void onPostExecute(byte[] signedTX) {
				MainActivity activity = weakActivity.get();
				if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
					return;
				}				
				if (signedTX != null) {
					activity.toast("TX signed");
					activity.debug(Dump.dump(signedTX));
				}
				else {
					activity.toast("Error signing transaction");
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

		public BtcGetAddress btcGetAddress(LedgerDevice device, MainActivity activity) {
			return new BtcGetAddress(device, activity);
		}

		public BtcSignTX btcSignTX(LedgerDevice device, MainActivity activity) {
			return new BtcSignTX(device, activity);
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
