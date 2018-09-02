package in.wangziq.fitnessrecorder.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.annimon.stream.function.Consumer;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.data.BleScanState;
import com.clj.fastble.scan.BleScanRuleConfig;

import java.util.ArrayList;
import java.util.List;

import in.wangziq.fitnessrecorder.R;
import in.wangziq.fitnessrecorder.config.Constants;
import in.wangziq.fitnessrecorder.hardware.Protocol;
import in.wangziq.fitnessrecorder.services.CommService;


public final class ScanBandActivity extends AppCompatActivity {

    private static final String TAG = ScanBandActivity.class.getSimpleName();
    private static final int LOCATION_PERMISSION_REQUEST = 1;
    private static final int SCAN_TIMEOUT = 10 * 1000;

    private DevicesInfoAdapter mDevicesInfoAdapter;
    private MenuItem mScanMenuItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pairing);

        DeviceInfoHolder.setOnItemSelected(this::onSelected);
        mDevicesInfoAdapter = new DevicesInfoAdapter();
        RecyclerView devicesRecyclerView = findViewById(R.id.devices_recycler_view);
        devicesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        devicesRecyclerView.setAdapter(mDevicesInfoAdapter);

        CommService.startActionDisconnect(this, false);
        new Handler().postDelayed(this::requestLocationPermissionAndScanBt, 500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        BleManager bleManager = BleManager.getInstance();
        if (bleManager.getScanSate() == BleScanState.STATE_SCANNING)
            bleManager.cancelScan();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean ans = super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_pairing, menu);

        final BleManager bleManager = BleManager.getInstance();
        mScanMenuItem = menu.findItem(R.id.item_scan);
        mScanMenuItem.setOnMenuItemClickListener(menuItem -> {
            if (bleManager.getScanSate() == BleScanState.STATE_SCANNING) bleManager.cancelScan();
            else requestLocationPermissionAndScanBt();
            return true;
        });
        return ans;
    }

    private void requestLocationPermissionAndScanBt() {
        final String PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION;
        if (ContextCompat.checkSelfPermission(this, PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, PERMISSION)) {
                Toast.makeText(this, R.string.toast_need_location_permission, Toast.LENGTH_SHORT).show();
                Log.i(TAG, "requestLocationPermissionAndScanBt: denied in the past");
            } else {
                ActivityCompat.requestPermissions(this, new String[] {PERMISSION}, LOCATION_PERMISSION_REQUEST);
                Log.i(TAG, "requestLocationPermissionAndScanBt: requesting");
            }
        } else {
            Log.i(TAG, "requestLocationPermissionAndScanBt: permission already granted");
            bleScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "onRequestPermissionsResult: location permission granted");
//                    bleScan();
                } else {
                    Log.i(TAG, "onRequestPermissionsResult: location permission denied");
                    Toast.makeText(this, R.string.toast_need_location_permission, Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                Log.i(TAG, "onRequestPermissionsResult: unknown request: " + requestCode);
        }
    }

    private void bleScan() {
        BleManager bleManager = BleManager.getInstance();
        if (!bleManager.isBlueEnable()) {
            Toast.makeText(ScanBandActivity.this, R.string.toast_have_to_enable_bt, Toast.LENGTH_SHORT).show();
            return;
        }
        bleManager.initScanRule(new BleScanRuleConfig.Builder()
                .setDeviceName(true, Protocol.BT_BROADCAST_NAME)
                .setScanTimeOut(SCAN_TIMEOUT)
                .build());
        bleManager.scan(new BleScanCallback() {
            @Override public void onScanFinished(List<BleDevice> scanResultList) {
                mScanMenuItem.setTitle(R.string.item_scan);
                Log.i(TAG, "onScanFinished");
            }
            @Override public void onScanStarted(boolean success) {
                mDevicesInfoAdapter.clear();
                if (success) {
                    mScanMenuItem.setTitle(R.string.item_stop_scan);
                }
            }
            @Override public void onScanning(BleDevice device) {
                mDevicesInfoAdapter.addItem(device);
                Log.i(TAG, String.format("onScanning: find device[name=%s, mac=%s]", device.getName(), device.getMac()));
            }
        });
    }

    private void onSelected(BleDevice device) {
        Log.i(TAG, "onSelected: mac=" + device.getMac() + ", rssi=" + device.getRssi());
        Intent selectionResult = new Intent().putExtra(Constants.Extra.MAC, device.getMac());
        setResult(RESULT_OK, selectionResult);
        finishAfterTransition();
    }
}


class DeviceInfoHolder extends RecyclerView.ViewHolder {
    private static final String TAG = DeviceInfoHolder.class.getSimpleName();
    private static Consumer<BleDevice> sOnItemSelected;

    private BleDevice mDevice;
    private TextView mDeviceNameText;
    private TextView mDeviceAddressText;

    public DeviceInfoHolder(View itemView) {
        super(itemView);
        mDeviceNameText = itemView.findViewById(R.id.devices_name_text_view);
        mDeviceAddressText = itemView.findViewById(R.id.devices_address_text_view);
        itemView.setOnClickListener(view -> sOnItemSelected.accept(mDevice));
    }

    public static void setOnItemSelected(Consumer<BleDevice> onItemSelected) {
        sOnItemSelected = onItemSelected;
    }

    public void bindDeviceInfo(BleDevice device) {
        mDevice = device;
        String name = mDevice.getName();
        mDeviceNameText.setText(name == null ? "Unknown Device" : name);
        mDeviceAddressText.setText(mDevice.getMac());
    }
}

class DevicesInfoAdapter extends RecyclerView.Adapter<DeviceInfoHolder> {

    private List<BleDevice> mModel;

    public DevicesInfoAdapter() {
        mModel = new ArrayList<>();
    }

    @NonNull
    @Override
    public DeviceInfoHolder onCreateViewHolder(@NonNull ViewGroup parent, int position) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_devices_info, parent, false);
        return new DeviceInfoHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceInfoHolder holder, int position) {
        holder.bindDeviceInfo(mModel.get(position));
    }

    @Override
    public int getItemCount() {
        return mModel.size();
    }

    public void addItem(BleDevice device) {
        mModel.add(device);
        notifyItemInserted(mModel.size());
    }

    public void clear() {
        mModel.clear();
        notifyDataSetChanged();
    }
}