package in.wangziq.fitnessrecorder;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.clj.fastble.BleManager;


import java.text.SimpleDateFormat;
import java.util.Date;

import in.wangziq.fitnessrecorder.utils.BytesUtil;

public final class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 0x01;
    private static final String SETTINGS_KEY = "KEY";
    private static final int HEART_RATE_UPDATE_TOLERANCE = 3000;

    private MiBand2 mBand;

    private Button mConnectButton, mTestButton, mStopButton;
    private TextView mHeartRateText, mTimeText;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initBle();

        mBand = new MiBand2("CE:0B:A3:D2:C7:7F", retrieveKey(true));

        mConnectButton = findViewById(R.id.connect_btn);
        mTestButton = findViewById(R.id.get_heart_rate_btn);
        mStopButton = findViewById(R.id.stop_heart_rate_btn);
        mHeartRateText = findViewById(R.id.heart_rate_text);
        mTimeText = findViewById(R.id.time_text);

        mConnectButton.setOnClickListener(view -> {
            if (BleManager.getInstance().isBlueEnable()) mBand.connect();
            else requestEnableBt();
        });

        mTestButton.setOnClickListener(view -> mBand.turnOnHeartRate((heartRate) -> {
            mHeartRateText.setText(Integer.toString(heartRate));
            mTimeText.setText(SimpleDateFormat.getTimeInstance().format(new Date()));
            Log.i(TAG, "Got heartRate=" + heartRate);
        }));

        mStopButton.setOnClickListener(view -> mBand.turnOffHeartRate());

    }

    private void initBle() {
        BleManager bleManager = BleManager.getInstance();
        bleManager.enableLog(true).setConnectOverTime(10000).setOperateTimeout(5000).init(getApplication());
        if (!bleManager.isSupportBle()) {
            Toast.makeText(this, R.string.not_support_ble, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "initBle: device not support BLE");
            finish();
        }
    }

    private void requestEnableBt() {
        if (!BleManager.getInstance().isBlueEnable()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    Toast.makeText(this, R.string.bt_enabled, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.have_to_enable_bt, Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private byte[] retrieveKey(boolean fromStorage) {
        if (fromStorage) {
            String storeKeyStr = getPreferences(Context.MODE_PRIVATE).getString(SETTINGS_KEY, null);
            if (storeKeyStr == null) return retrieveKey(false);
            return BytesUtil.hexStrToBytes(storeKeyStr);
        } else {
            byte[] newKey = BytesUtil.random(16);
            String newKeyStr = BytesUtil.toHexStr(newKey);
            getPreferences(Context.MODE_PRIVATE).edit()
                    .putString(SETTINGS_KEY, newKeyStr)
                    .apply();
            Log.i(TAG, "generated new key: " + newKeyStr);
            return newKey;
        }
    }
}
