package in.wangziq.fitnessrecorder.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import in.wangziq.fitnessrecorder.config.Constants;
import in.wangziq.fitnessrecorder.hardware.BandState;
import in.wangziq.fitnessrecorder.hardware.MiBand2;
import in.wangziq.fitnessrecorder.persistance.DbTool;
import in.wangziq.fitnessrecorder.utils.BytesUtil;

public final class CommService extends Service {

    private static final String TAG = CommService.class.getSimpleName();

    private static final int WAKELOCK_TIMEOUT = 36000000; // 10 hours

    private MiBand2 mBand;
    private Thread mWorkThread;
    private SharedPreferences mSettings;
    private DbTool mDatabase;
    private PowerManager.WakeLock mWakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
        mSettings = getSharedPreferences(Constants.Settings.DEVICE, Context.MODE_PRIVATE);
        mBand = loadBandFromSettings();
        mDatabase = new DbTool(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        disconnect();
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action;
        if (intent == null || (action = intent.getAction()) == null) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        switch (action) {
            case Constants.Action.STATE_UPDATE:
                broadcastState(mBand.getState());
                break;
            case Constants.Action.PAIR:
                pairAndConnect(intent.getStringExtra(Constants.Extra.MAC));
                break;
            case Constants.Action.CONNECT:
                connect();
                break;
            case Constants.Action.DISCONNECT:
                disconnect();
                break;
            case Constants.Action.START_HEART_RATE:
                startHeartRateMeasure();
                break;
            case Constants.Action.STOP_HEART_RATE:
                stopHeartRateMeasure();
                break;
            default:
                Log.i(TAG, "onStartCommand: Unknown action: " + action);
        }
        return START_NOT_STICKY;
    }

    private void broadcastState(BandState state) {
        // TODO:
        // 1.sync -> async
        // 2. add more information, e.g. battery
        String mac;
        byte[] authKey;
        int stateValue;
        if (mBand == null) {
            mac = null;
            authKey = null;
            stateValue = 0;
        } else {
            mac = mBand.getMacAddress();
            authKey = mBand.getAuthKey();
            stateValue = state.getValue();
        }
        Intent i = new Intent(Constants.Action.STATE_UPDATE)
                .putExtra(Constants.Extra.MAC, mac)
                .putExtra(Constants.Extra.KEY, authKey)
                .putExtra(Constants.Extra.STATE, stateValue);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
        Log.i(TAG, "broadcastState: macAddress=" + mac + ", authKey=" + BytesUtil.toHexStr(authKey) + ", state=" + state);
    }

    private void pairAndConnect(String macAddress) {
        if (mWorkThread != null && mWorkThread.isAlive()) {
            Log.i(TAG, "pairAndConnect: the last thread is still working, current task canceled");
            return;
        }
        disconnect();
        mBand = createMiBandInstanceWithCallback(macAddress, null);
        mWorkThread = new Thread(() -> {
            boolean success = mBand.connect(true);
            Intent response = new Intent(Constants.Action.PAIR);
            if (success) {
                Log.i(TAG, "pairAndConnect: paired successfully, state=" + mBand.getState());
                mSettings.edit()
                        .putString(Constants.Settings.DEVICE_MAC, mBand.getMacAddress())
                        .putString(Constants.Settings.DEVICE_KEY, BytesUtil.toHexStr(mBand.getAuthKey()))
                        .apply();
                response.putExtra(Constants.Extra.STATUS, Constants.Status.OK);
            } else {
                Log.i(TAG, "pairAndConnect: failed to pair, state=" + mBand.getState());
                response.putExtra(Constants.Extra.STATUS, Constants.Status.FAILED);
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(response);
            mWorkThread = null;
        });
        mWorkThread.start();
    }

    private void connect() {
        if (mWorkThread != null && mWorkThread.isAlive()) {
            Log.w(TAG, "pairAndConnect: the last thread is still working, current task canceled");
            return;
        }
        mWorkThread = new Thread(() -> {
            boolean success = mBand.connect(false);
            Intent response = new Intent(Constants.Action.CONNECT);
            if (success) {
                Log.i(TAG, "connect: connected successfully, state=" + mBand.getState());
                response.putExtra(Constants.Extra.STATUS, Constants.Status.OK);
            } else {
                Log.i(TAG, "connect: failed to connect, state=" + mBand.getState());
                response.putExtra(Constants.Extra.STATUS, Constants.Status.FAILED);
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(response);
            mWorkThread = null;
        });
        mWorkThread.start();
    }

    private void disconnect() {
        if (mBand.getState().isBleConnected()) mBand.disconnect();
    }

    private void startHeartRateMeasure() {
        if (mWorkThread != null && mWorkThread.isAlive()) {
            Log.w(TAG, "startHeartRateMeasure: the last thread is still working, current task canceled");
            return;
        }

        PowerManager powerMgr = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerMgr != null) mWakeLock = powerMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KeepHeartBeat");
        mWakeLock.acquire(WAKELOCK_TIMEOUT);

        mWorkThread = new Thread(() -> {
            boolean success = false;
            if (!mBand.getState().isMeasuringHeartRate()) {
                success = mBand.startMeasureHeartRate(heartRate -> {
                    mDatabase.insertHeartRate(heartRate);

                    Intent i = new Intent(Constants.Action.BROADCAST_HEART_RATE)
                            .putExtra(Constants.Extra.HEART_RATE, heartRate);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(i);
                });
            }
            Intent response = new Intent(Constants.Action.START_HEART_RATE)
                    .putExtra(Constants.Extra.STATE, success ? Constants.Status.OK : Constants.Status.FAILED);
            LocalBroadcastManager.getInstance(this).sendBroadcast(response);
            mWorkThread = null;
        });
        mWorkThread.start();
    }

    private void stopHeartRateMeasure() {
        if (mWakeLock != null) mWakeLock.release();

        if (mWorkThread != null && mWorkThread.isAlive()) {
            Log.w(TAG, "startHeartRateMeasure: the last thread is still working, current task canceled");
            return;
        }
        mWorkThread = new Thread(() -> {
            mBand.stopMeasureHeartRate();
            // FIXME: current implementation: always return OK
            Intent response = new Intent(Constants.Action.STOP_HEART_RATE).putExtra(Constants.Extra.STATE, Constants.Status.OK);
            LocalBroadcastManager.getInstance(this).sendBroadcast(response);
            mWorkThread = null;
        });
        mWorkThread.start();
    }

    private MiBand2 loadBandFromSettings() {
        String macAddress = mSettings.getString(Constants.Settings.DEVICE_MAC, null);
        byte[] authKey = BytesUtil.hexStrToBytes(mSettings.getString(Constants.Settings.DEVICE_KEY, null));
        return createMiBandInstanceWithCallback(macAddress, authKey);
    }

    private MiBand2 createMiBandInstanceWithCallback(String macAddress, byte[] authKey) {
        MiBand2 miBand = new MiBand2(macAddress, authKey);
        miBand.setDisconnectHandler(bandState -> {
            Log.i(TAG, "onDisconnect: state=" + bandState);
            broadcastState(mBand.getState());
            // TODO: reconnect for several times
        });
        return miBand;
    }

    public static void startActionStateUpdate(Context context) {
        Intent i = new Intent(context, CommService.class)
                .setAction(Constants.Action.STATE_UPDATE);
        context.startService(i);
    }

    public static void startActionPair(Context context, String macAddress) {
        Intent i = new Intent(context, CommService.class)
                .setAction(Constants.Action.PAIR)
                .putExtra(Constants.Extra.MAC, macAddress);
        context.startService(i);
    }

    public static void startActionConnect(Context context) {
        Intent i = new Intent(context, CommService.class).setAction(Constants.Action.CONNECT);
        context.startService(i);
    }

    public static void startActionDisconnect(Context context) {
        Intent i = new Intent(context, CommService.class).setAction(Constants.Action.DISCONNECT);
        context.startService(i);
    }

    public static void startActionStartHeartRateMeasure(Context context) {
        Intent i = new Intent(context, CommService.class).setAction(Constants.Action.START_HEART_RATE);
        context.startService(i);
    }

    public static void startActionStopHeartRateMeasure(Context context) {
        Intent i = new Intent(context, CommService.class).setAction(Constants.Action.STOP_HEART_RATE);
        context.startService(i);
    }
}
