package in.wangziq.fitnessrecorder.activities;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.clj.fastble.BleManager;

import java.text.SimpleDateFormat;
import java.util.Date;

import in.wangziq.fitnessrecorder.config.Constants;
import in.wangziq.fitnessrecorder.R;
import in.wangziq.fitnessrecorder.hardware.BandState;
import in.wangziq.fitnessrecorder.services.CommService;

public final class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_SCAN_BAND = 2;

    private Button mButton;
    private TextView mHeartRateText, mTimeText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mButton = findViewById(R.id.btn);
        mHeartRateText = findViewById(R.id.heart_rate_text);
        mTimeText = findViewById(R.id.time_text);

        initBle();
        registerBroadcast();
        CommService.startActionStateUpdate(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        Log.i(TAG, "onDestroy: unregistered broadcast receiver");
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

    // TODO: refactor: observer pattern
    private void registerBroadcast() {
        LocalBroadcastManager broadcastMgr = LocalBroadcastManager.getInstance(this);
        broadcastMgr.registerReceiver(mBroadcastReceiver, new IntentFilter(Constants.Action.STATE_UPDATE));
        broadcastMgr.registerReceiver(mBroadcastReceiver, new IntentFilter(Constants.Action.CONNECT));
        broadcastMgr.registerReceiver(mBroadcastReceiver, new IntentFilter(Constants.Action.PAIR));
        broadcastMgr.registerReceiver(mBroadcastReceiver, new IntentFilter(Constants.Action.START_HEART_RATE));
        broadcastMgr.registerReceiver(mBroadcastReceiver, new IntentFilter(Constants.Action.STOP_HEART_RATE));
        broadcastMgr.registerReceiver(mBroadcastReceiver, new IntentFilter(Constants.Action.BROADCAST_HEART_RATE));
        Log.i(TAG, "registered broadcast receiver");
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

    private void prepareScan() {
        mButton.setText(R.string.btn_pair);
        mButton.setEnabled(true);
        mButton.setOnClickListener(view -> {
            requestScan();
        });
    }

    private void requestScan() {
        clearShownText();
        // TODO: consider setText(scanning) ?
        if (!checkAndEnableBt()) return;
        // mButton.setEnabled(false); // should keep enabled
        Intent i = new Intent(this, ScanBandActivity.class);
        startActivityForResult(i, REQUEST_SCAN_BAND);
    }

    private void requestPair(String macAddress) {
        // if (!checkAndEnableBt()) return; // seems meaningless
        mButton.setText(R.string.btn_pairing);
        mButton.setEnabled(false);
        CommService.startActionPair(this, macAddress);
    }

    private void prepareConnect() {
        mButton.setText(R.string.btn_connect);
        mButton.setEnabled(true);
        mButton.setOnClickListener(view -> requestConnect());
    }

    private void requestConnect() {
        if (!checkAndEnableBt()) return;
        mButton.setText(R.string.btn_connecting);
        mButton.setEnabled(false);
        CommService.startActionConnect(this);
    }

    private void clearShownText() {
        mHeartRateText.setText(R.string.default_heart_rate);
        mHeartRateText.setText(null);
    }

    private void prepareStartHeartRateMeasurement() {
        mButton.setText(R.string.btn_start);
        mButton.setEnabled(true);
        mButton.setOnClickListener(view -> requestStartHeartRateMeasure());
        clearShownText();
        mTimeText.setText(null);
    }

    private void prepareStopHeartRateMeasurement() {
        mHeartRateText.setText(R.string.loading_heart_rate);
        mButton.setText(R.string.btn_stop);
        mButton.setEnabled(true);
        mButton.setOnClickListener(view -> requestStopHeartRateMeasure());
    }

    private void requestStartHeartRateMeasure() {
        mButton.setText(R.string.btn_enabling);
        mButton.setEnabled(false);
        Intent i = new Intent(this, CommService.class).setAction(Constants.Action.START_HEART_RATE);
        startService(i);
        // TODO: request + bind stop listener
    }

    private void requestStopHeartRateMeasure() {
        Intent i = new Intent(this, CommService.class).setAction(Constants.Action.STOP_HEART_RATE);
        startService(i);
        mButton.setText(R.string.btn_disabling);
        mButton.setEnabled(false);
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

    private void handleStateUpdate(Intent intent) {
        String macAddress = intent.getStringExtra(Constants.Extra.MAC);
        byte[] authKey = intent.getByteArrayExtra(Constants.Extra.KEY);
        BandState state = new BandState(intent.getIntExtra(Constants.Extra.STATE, BandState.DEFAULT_VALUE));
        if (macAddress == null || authKey == null) {
            prepareScan();
        } else if (state.isEncrypted()) {
            if (state.isMeasuringHeartRate()) prepareStopHeartRateMeasurement();
            else prepareStartHeartRateMeasurement();
        } else {
            prepareConnect();
        }
    }

    private void handlePairResponse(Intent intent) {
        int status = intent.getIntExtra(Constants.Extra.STATUS, Constants.Status.UNKNOWN);
        if (status == Constants.Status.OK) {
            prepareStartHeartRateMeasurement();
            Toast.makeText(this, R.string.toast_pair_ok, Toast.LENGTH_SHORT).show();
        } else {
            prepareScan();
            Toast.makeText(this, R.string.toast_pair_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleConnectResponse(Intent intent) {
        int status = intent.getIntExtra(Constants.Extra.STATUS, Constants.Status.UNKNOWN);
        if (status == Constants.Status.OK) {
            prepareStartHeartRateMeasurement();
            Toast.makeText(this, R.string.toast_connect_ok, Toast.LENGTH_SHORT).show();
        } else {
            prepareConnect();
            Toast.makeText(this, R.string.toast_connect_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleStartHeartRateResponse(Intent response) {
        if (response.getIntExtra(Constants.Extra.STATE, Constants.Status.UNKNOWN) == Constants.Status.OK) {
            prepareStopHeartRateMeasurement();
            Log.i(TAG, "handleStartHeartRateResponse: enabled successfully");
        } else {
            prepareStartHeartRateMeasurement();
            Log.i(TAG, "handleStartHeartRateResponse: failed to enable");
        }
    }

    private void handleStopHeartRateResponse(Intent response) {
        if (response.getIntExtra(Constants.Extra.STATE, Constants.Status.UNKNOWN) == Constants.Status.OK) {
            prepareStartHeartRateMeasurement();
        } else {
            prepareStopHeartRateMeasurement();
        }
    }

    private void handleHeartRateBroadcast(Intent data) {
        int heartRate = data.getIntExtra(Constants.Extra.HEART_RATE, -1);
        if (heartRate > 0) {
            mHeartRateText.setText(Integer.toString(heartRate));
            mTimeText.setText(SimpleDateFormat.getTimeInstance().format(new Date()));
            Log.i(TAG, "handleHeartRateBroadcast: heartRate=" + heartRate);
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action;
            if (intent == null || (action = intent.getAction()) == null) return;
            switch (action) {
                case Constants.Action.STATE_UPDATE:
                    handleStateUpdate(intent);
                    break;
                case Constants.Action.PAIR:
                    handlePairResponse(intent);
                    break;
                case Constants.Action.CONNECT:
                    handleConnectResponse(intent);
                    break;
                case Constants.Action.START_HEART_RATE:
                    handleStartHeartRateResponse(intent);
                    break;
                case Constants.Action.STOP_HEART_RATE:
                    handleStopHeartRateResponse(intent);
                    break;
                case Constants.Action.BROADCAST_HEART_RATE:
                    handleHeartRateBroadcast(intent);
                    break;
                default: Log.i(TAG, "onReceive: Unknown action: " + action);
            }
        }
    };
}