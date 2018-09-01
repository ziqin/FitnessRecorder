package in.wangziq.fitnessrecorder.activities;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.databinding.ObservableBoolean;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.clj.fastble.BleManager;

import in.wangziq.fitnessrecorder.config.Constants;
import in.wangziq.fitnessrecorder.R;
import in.wangziq.fitnessrecorder.databinding.ActivityMainBinding;
import in.wangziq.fitnessrecorder.hardware.BandState;
import in.wangziq.fitnessrecorder.services.CommService;
import in.wangziq.fitnessrecorder.utils.BytesUtil;

public final class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_SCAN_BAND = 2;

    private String mMacAddress;
    private byte[] mAuthKey;

    private ActivityMainBinding mData;
    private Messenger mMessenger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mData = DataBindingUtil.setContentView(this, R.layout.activity_main);

        mData.setConnectTransaction(mConnectTransaction);
        mData.setHeartRateTransaction(mHeartRateTransaction);
        mData.setAccelerationTransaction(mAccelerationTransaction);

        mMessenger = new Messenger(this);
        mMessenger.addHandler(mStateUpdateRequest);
        mMessenger.addHandler(mConnectTransaction);
        mMessenger.addHandler(mHeartRateTransaction);
        mMessenger.addHandler(mAccelerationTransaction);

        initBle();
        mStateUpdateRequest.request();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMessenger.unregister();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean ans = super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_main, menu);

        MenuItem pairMenuItem = menu.findItem(R.id.start_pair);
        MenuItem exportMenuItem = menu.findItem(R.id.export_data);

        pairMenuItem.setOnMenuItemClickListener(menuItem -> {
            requestScan();
            return true;
        });
        exportMenuItem.setIntent(new Intent(this, ExportDataActivity.class));
        return ans;
    }

    private void initBle() {
        BleManager bleManager = BleManager.getInstance();
        bleManager.enableLog(true).setConnectOverTime(10000).setOperateTimeout(5000).init(getApplication());
        if (!bleManager.isSupportBle()) {
            Toast.makeText(this, R.string.toast_not_support_ble, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "initBle: device not support BLE");
            finish();
        }
        new Handler().postDelayed(this::checkAndEnableBt, 500);
    }

    /**
     * @return true if bluetooth is already enabled, otherwise false
     */
    private boolean checkAndEnableBt() {
        if (BleManager.getInstance().isBlueEnable()) {
            return true;
        } else {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT);
            return false;
        }
    }

    private void requestScan() {
        if (!checkAndEnableBt()) return;
        Intent i = new Intent(this, ScanBandActivity.class);
        startActivityForResult(i, REQUEST_SCAN_BAND);
    }

    private void requestPair(String macAddress) {
        // if (!checkAndEnableBt()) return; // seems meaningless
        CommService.startActionPair(this, macAddress);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) Toast.makeText(this, R.string.toast_bt_enabled, Toast.LENGTH_SHORT).show();
                else Toast.makeText(this, R.string.toast_have_to_enable_bt, Toast.LENGTH_SHORT).show();
                break;

            case REQUEST_SCAN_BAND:
                if (resultCode == RESULT_OK && data != null) {
                    String macAddress = data.getStringExtra(Constants.Extra.MAC);
                    requestPair(macAddress);
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private Request mStateUpdateRequest = new Request() {
        @Override public String[] getActions() {
            return new String[] { Constants.Action.STATE_UPDATE };
        }

        @Override public void request() {
            Intent i = new Intent(MainActivity.this, CommService.class)
                    .setAction(Constants.Action.STATE_UPDATE);
            startService(i);
        }

        @Override public void handleResponse(Intent response) {
            super.handleResponse(response);
            mMacAddress = response.getStringExtra(Constants.Extra.MAC);
            mAuthKey = response.getByteArrayExtra(Constants.Extra.KEY);
            BandState state = new BandState(response.getIntExtra(Constants.Extra.STATE, BandState.DEFAULT_VALUE));
            Log.i(TAG, String.format("StateUpdateRequest.handleResponse: mac=%s, authKey=%s, state=%s",
                    mMacAddress, BytesUtil.toHexStr(mAuthKey), state));
            mData.setConnected(state.isEncrypted());
            mHeartRateTransaction.running.set(state.isMeasuringHeartRate());
            mAccelerationTransaction.running.set(state.isMeasuringAcceleration());
        }
    };

    private Transaction mConnectTransaction = new Transaction() {
        @Override public String[] getActions() {
            return new String[] {
                    Constants.Action.PAIR,
                    Constants.Action.CONNECT,
                    Constants.Action.DISCONNECT
            };
        }

        @Override public void start() {
            if (!checkAndEnableBt()) {
                Log.w(TAG, "ConnectTransaction.start: bluetooth disabled");
                return;
            }
            if (mMacAddress == null) requestScan();
            else if (mAuthKey == null) CommService.startActionPair(MainActivity.this, mMacAddress);
            else CommService.startActionConnect(MainActivity.this);
            Log.i(TAG, "ConnectTransaction.start: mac=" + mMacAddress + ", authKey=" + BytesUtil.toHexStr(mAuthKey));
        }

        @Override public void stop() {
            CommService.startActionDisconnect(MainActivity.this);
            Log.i(TAG, "ConnectTransaction.stop: disconnect");
        }

        @Override public void handleResponse(Intent response) {
            super.handleResponse(response);
            int status = response.getIntExtra(Constants.Extra.STATUS, Constants.Status.UNKNOWN);
            switch (response.getAction()) {
                case Constants.Action.PAIR:
                case Constants.Action.CONNECT:
                    if (status == Constants.Status.OK) mData.setConnected(true);
                    break;
                case Constants.Action.DISCONNECT:
                    if (status == Constants.Status.OK) mData.setConnected(false);
                    break;
            }
        }
    };


    private Transaction mHeartRateTransaction = new Transaction() {
        @Override public String[] getActions() {
            return new String[] {
                    Constants.Action.START_HEART_RATE,
                    Constants.Action.STOP_HEART_RATE,
                    Constants.Action.BROADCAST_HEART_RATE
            };
        }

        @Override public void start() {
            Log.i(TAG, "HeartRateTransaction starting");
            CommService.startActionStartHeartRateMeasure(MainActivity.this);
        }

        @Override public void stop() {
            Log.i(TAG, "HeartRateTransaction stopping");
            CommService.startActionStopHeartRateMeasure(MainActivity.this);
        }

        @Override public void handleResponse(Intent response) {
            super.handleResponse(response);
            int status;
            switch (response.getAction()) {
                case Constants.Action.BROADCAST_HEART_RATE:
                    int heartRate = response.getIntExtra(Constants.Extra.HEART_RATE, -1);
                    mData.setHeartRate(heartRate);
                    Log.i(TAG, "HeartRateTransaction.handleResponse: got heart rate = " + heartRate);
                    break;
                case Constants.Action.START_HEART_RATE:
                    status = response.getIntExtra(Constants.Extra.STATUS, Constants.Status.UNKNOWN);
                    Log.i(TAG, "HeartRateTransaction.handleResponse: START_HEART_RATE status=" + status);
                    boolean success = status == Constants.Status.OK;
                    running.set(success);
                    if (!success) Toast.makeText(MainActivity.this, R.string.toast_heart_rate_on_failed, Toast.LENGTH_SHORT).show();
                    break;
                case Constants.Action.STOP_HEART_RATE:
                    status = response.getIntExtra(Constants.Extra.STATUS, Constants.Status.UNKNOWN);
                    Log.i(TAG, "HeartRateTransaction.handleResponse: START_HEART_RATE status=" + status);
                    if (status == Constants.Status.OK) running.set(false);
                    else Toast.makeText(MainActivity.this, R.string.toast_heart_rate_off_failed, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };


    private Transaction mAccelerationTransaction = new Transaction() {
        @Override public String[] getActions() {
            return new String[] {
                    Constants.Action.START_ACCELERATION,
                    Constants.Action.STOP_ACCELERATION,
                    Constants.Action.BROADCAST_ACCELERATION
            };
        }

        @Override public void start() {
            Intent i = new Intent(MainActivity.this, CommService.class)
                    .setAction(Constants.Action.START_ACCELERATION);
            startService(i);
        }

        @Override public void stop() {
            Intent i = new Intent(MainActivity.this, CommService.class)
                    .setAction(Constants.Action.STOP_ACCELERATION);
            startService(i);
        }

        @Override public void handleResponse(Intent response) {
            super.handleResponse(response);
            int status;
            switch (response.getAction()) {
                case Constants.Action.BROADCAST_ACCELERATION:
                    float x = response.getFloatExtra(Constants.Extra.ACCELERATION_X, 0);
                    float y = response.getFloatExtra(Constants.Extra.ACCELERATION_Y, 0);
                    float z = response.getFloatExtra(Constants.Extra.ACCELERATION_Z, 0);
                    mData.setAccelerationX(x);
                    mData.setAccelerationY(y);
                    mData.setAccelerationZ(z);
                    Log.i(TAG, "AccelerationTransaction.handleResponse: x=" + x + " y=" + y + " z=" + z);
                    break;
                case Constants.Action.START_ACCELERATION:
                    status = response.getIntExtra(Constants.Extra.STATUS, Constants.Status.UNKNOWN);
                    boolean success = status == Constants.Status.OK;
                    running.set(success);
                    if (!success) Toast.makeText(MainActivity.this, R.string.toast_acceleration_on_failed, Toast.LENGTH_SHORT).show();
                    break;
                case Constants.Action.STOP_ACCELERATION:
                    status = response.getIntExtra(Constants.Extra.STATUS, Constants.Status.UNKNOWN);
                    if (status == Constants.Status.OK) running.set(false);
                    else Toast.makeText(MainActivity.this, R.string.toast_acceleration_off_failed, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };


    public static abstract class Request extends Messenger.MessageHandler {
        public abstract void request();
    }


    public static abstract class Transaction extends Messenger.MessageHandler {
        public final ObservableBoolean running = new ObservableBoolean();
        public abstract void start();
        public abstract void stop();
    }
}