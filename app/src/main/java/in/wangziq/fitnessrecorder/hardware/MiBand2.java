package in.wangziq.fitnessrecorder.hardware;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;

import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.IntConsumer;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Timer;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import in.wangziq.fitnessrecorder.utils.BytesUtil;
import in.wangziq.fitnessrecorder.utils.ResponseWaiter;
import in.wangziq.fitnessrecorder.utils.TimerUtil;

public final class MiBand2 {

    public interface TriFloatConsumer { void accept(float x, float y, float z); }

    private static final int REFRESH_TIMEOUT = 100; // 100ms
    private static final int USR_INTERACTION_TIMEOUT = 20000; // 20s
    private static final String TAG = MiBand2.class.getSimpleName();

    private BleDevice mBleDevice;
    private byte[] mAuthKey;
    private byte[] mRand;
    private BandState mState;

    private Consumer<BandState> mDisconnectHandler;
    private SparseArray<Consumer<byte[]>> mNoticeConsumers;
    private IntConsumer mHeartRateHandler;
    private TriFloatConsumer mAccelerationHandler;
    private Timer mHeartRatePingTimer, mAccelerationTimer;

    public MiBand2(@Nullable String macAddress, @Nullable byte[] key) {
        mBleDevice = macAddress == null
                ? null
                : new BleDevice(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(macAddress));
        mAuthKey = key;
        mState = new BandState();
        initAuthNoticeConsumer();
    }

    public void setDisconnectHandler(Consumer<BandState> callback) {
        mDisconnectHandler = callback;
    }

    public BandState getState() {
        return mState;
    }

    public String getMacAddress() {
        return mBleDevice == null ? null : mBleDevice.getMac();
    }

    public byte[] getAuthKey() {
        return mAuthKey;
    }

    @Override
    public String toString() {
        return String.format("MiBand2[address=%s, state=%s]", getMacAddress(), mState.toString());
    }


    /**
     * @param refresh true if it's a new connection without historical key
     * @return connected successfully or failed
     */
    // TODO: should be improved
    // synchronized communication
    // doAfter REFRESH_TIMEOUT: let waitForStateUpdate() get the old state before executing a task
    public boolean connect(boolean refresh) {
        mState.reset();

        TimerUtil.doAfter(REFRESH_TIMEOUT, this::bleConnect);
        waitForStateUpdate();
        if (!mState.isBleConnected()) {
            Log.i(TAG, "pair: bleConnect failed, state=" + mState);
            return false;
        }

        TimerUtil.doAfter(REFRESH_TIMEOUT, this::turnOnAuthNotify);
        waitForStateUpdate();
        if (!mState.isAuthNotifyOn()) {
            Log.i(TAG, "pair: failed to enable authentication notification, state=" + mState);
            return false;
        }

        if (mAuthKey == null || refresh) {
            mAuthKey = BytesUtil.random(16);
            TimerUtil.doAfter(REFRESH_TIMEOUT, this::sendKey);
            waitForStateUpdate();
            if (!mState.isKeyGot()) {
                Log.i(TAG, "pair: failed to receive key, state=" + mState);
                return false;
            }
        }

        TimerUtil.doAfter(REFRESH_TIMEOUT, this::requestRand);
        waitForStateUpdate();
        if (!mState.isRandRequested()) {
            Log.i(TAG, "pair: failed to request rand, state=" + mState);
            return false;
        }

        TimerUtil.doAfter(REFRESH_TIMEOUT, this::sendEncryptedRand);
        waitForStateUpdate();
        if (!mState.isEncrypted()) return false;

        TimerUtil.doAfter(REFRESH_TIMEOUT, this::turnOffAuthNotify);
        waitForStateUpdate();
        return true;
    }

    public void disconnect() {
        // TODO: stop working task
        if (mState.isMeasuringHeartRate()) stopMeasureHeartRate();
        if (mState.isMeasuringAcceleration()) stopMeasureAcceleration();
        BleManager.getInstance().disconnect(mBleDevice);
    }

    public boolean startMeasureHeartRate(IntConsumer heartRateHandler) {
        mHeartRateHandler = heartRateHandler;

        // TODO: check and stop related operations first
        if (!turnOnHeartRateNotify()) {
            Log.e(TAG, "enableHeartRate: failed to turn on heart rate notification");
            return false;
        }

        if (!enableHeartRateContinuousMonitor()) {
            Log.e(TAG, "enableHeartRate: failed to enable heart rate continuous monitor");
            return false;
        }

        enableHeartRatePing();
        return true;
    }

    public boolean stopMeasureHeartRate() {
        Log.i(TAG, "stopMeasureHeartRate");
        if (!mState.isBleConnected()) {
            Log.i(TAG, "stopMeasureHeartRate: already disconnected");
            return true;
        }
        disableHeartRatePing();
        return disableHeartRateContinuousMonitor() && turnOffHeartRateNotify();
    }

    public boolean startMeasureAcceleration(TriFloatConsumer accelerationHandler) {
        mAccelerationHandler = accelerationHandler;
        if (!turnOnRawDataNotify()) return false;
        mAccelerationTimer = TimerUtil.repeatPer(Protocol.Time.ACCELERATION_PERIOD, this::enableAcceleration);
        return enableAcceleration();
    }

    public boolean stopMeasureAcceleration() {
        if (mAccelerationTimer != null) {
            mAccelerationTimer.cancel();
            mAccelerationTimer = null;
        }
        if (!mState.isBleConnected()) {
            Log.i(TAG, "stopMeasureAcceleration: already disconnected");
            return true;
        }
        ResponseWaiter waiter = new ResponseWaiter(REFRESH_TIMEOUT);
        BleManager.getInstance().write(mBleDevice, Protocol.Service.BASIC, Protocol.Characteristic.SENSOR_CONTROL,
                Protocol.Command.ACCELERATION_STOP,
                new BleWriteCallback() {
                    @Override public void onWriteSuccess(int current, int total, byte[] justWrite) {
                        mState.setAccelerationMeasuring(false);
                        waiter.ok();
                        Log.i(TAG, "stopMeasureAcceleration: succeeded");
                    }
                    @Override public void onWriteFailure(BleException exception) {
                        waiter.fail();
                        Log.i(TAG, "stopMeasureAcceleration: failed");
                    }
                });
        return waiter.work();
    }

    private void initAuthNoticeConsumer() {
        mNoticeConsumers = new SparseArray<>();
        mNoticeConsumers.put(Protocol.Response.SEND_KEY_OK, data -> {
            Log.i(TAG, "accept auth notice: key got");
            mState.setKeyGot(true);
        });
        mNoticeConsumers.put(Protocol.Response.SEND_KEY_OOPS, data -> {
            Log.i(TAG, "accept auth notice: the band failed to receive the key");
            mState.setKeyGot(false);
        });
        mNoticeConsumers.put(Protocol.Response.RAND_OK, data -> {
            Log.i(TAG, "accept auth notice: rand received");
            mRand = data;
            mState.setRandRequested(true);
        });
        mNoticeConsumers.put(Protocol.Response.RAND_OOPS, data -> {
            Log.i(TAG, "accept auth notice: failed to receive rand");
            mState.setRandRequested(false);
        });
        mNoticeConsumers.put(Protocol.Response.AUTH_OK, data -> {
            Log.i(TAG, "accept auth notice: encrypted number matched");
            mState.setEncrypted(true);
        });
        mNoticeConsumers.put(Protocol.Response.AUTH_OOPS, data -> {
            Log.i(TAG, "accept auth notice: encrypted number did not match");
            mState.setEncrypted(false);
        });
    }

    private void bleConnect() {
        BleManager.getInstance().connect(getMacAddress(), new BleGattCallback() {
            @Override public void onStartConnect() {}
            @Override public void onConnectFail(BleDevice bleDevice, BleException exception) {
                mState.setBleConnected(false);
            }
            @Override public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                mBleDevice = bleDevice;
                mState.setBleConnected(true);
            }
            @Override public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
                mState.setBleConnected(false);
                if (mDisconnectHandler != null) mDisconnectHandler.accept(mState);
            }
        });
    }

    private void turnOnAuthNotify() {
        BleManager.getInstance().notify(mBleDevice, Protocol.Service.AUTH, Protocol.Characteristic.AUTH, new BleNotifyCallback() {
            @Override public void onNotifySuccess() {
                if (!mState.isAuthNotifyOn()) {
                    mState.setAuthNotify(true);
                    Log.i(TAG, "turnOnAuthNotify: succeeded, or turnOffAuthNotify");
                }
            }
            @Override public void onNotifyFailure(BleException exception) { // FIXME: may have bug
                if (mState.isAuthNotifyOn()) mState.setAuthNotify(false);
//                mState.setAuthNotify(false);
                Log.i(TAG, "turnOnAuthNotify: failed");
            }
            @Override public void onCharacteristicChanged(byte[] data) {
                handleAuthNotification(data);
            }
        });
    }

    private void turnOffAuthNotify() {
        boolean success = BleManager.getInstance().stopNotify(mBleDevice, Protocol.Service.AUTH, Protocol.Characteristic.AUTH);
        if (success) {
            mState.setAuthNotify(false);
            Log.i(TAG, "turnOffAuthNotify: succeeded");
        } else {
            mState.setAuthNotify(true);
            Log.i(TAG, "turnOffAuthNotify: failed");
        }
    }

    private void sendKey() {
        BleManager.getInstance().write(mBleDevice, Protocol.Service.AUTH, Protocol.Characteristic.AUTH,
                BytesUtil.combine(Protocol.Command.SEND_KEY, mAuthKey),
                new BleWriteCallback() {
                    @Override public void onWriteSuccess(int current, int total, byte[] justWrite) {
                        Log.i(TAG, "onWriteSuccess: wrote=" + BytesUtil.toHexStr(justWrite));
                    }
                    @Override public void onWriteFailure(BleException exception) {
                        mState.setKeyGot(false);
                    }
                });
    }

    private void requestRand() {
        BleManager.getInstance().write(mBleDevice, Protocol.Service.AUTH, Protocol.Characteristic.AUTH,
                Protocol.Command.RAND_REQUEST,
                new BleWriteCallback() {
                    @Override public void onWriteSuccess(int current, int total, byte[] justWrite) {}
                    @Override public void onWriteFailure(BleException exception) {
                        mState.setRandRequested(false);
                    }
                });
    }

    private void sendEncryptedRand() {
        byte[] encrypted = aesEncrypt(mRand);
        BleManager.getInstance().write(mBleDevice, Protocol.Service.AUTH, Protocol.Characteristic.AUTH,
                BytesUtil.combine(Protocol.Command.SEND_ENCRYPTED, encrypted),
                new BleWriteCallback() {
                    @Override public void onWriteSuccess(int current, int total, byte[] justWrite) {}
                    @Override public void onWriteFailure(BleException exception) {
                        mState.setEncrypted(false);
                    }
                });
    }

    private byte[] aesEncrypt(byte[] message) {
        Cipher cipher;
        try {
            cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(mAuthKey, "AES"));
            return cipher.doFinal(message);
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "aesEncrypt: " + e.getMessage(), e);
            return null;
        }
    }

    private void handleAuthNotification(byte[] notice) {
        Log.i(TAG, "handleAuthNotification: received notification: " + BytesUtil.toHexStr(notice));
        if (notice.length < 3) {
            Log.w(TAG, "handleAuthNotification: data length < 3!");
            return;
        }
        int head = (int)notice[0] << 16 | (int)notice[1] << 8 | (int)notice[2];
        byte[] body = Arrays.copyOfRange(notice, 3, notice.length);
        Consumer<byte[]> noticeConsumer = mNoticeConsumers.get(head);
        if (noticeConsumer != null) noticeConsumer.accept(body);
        else Log.i(TAG, String.format("handleAuthNotification: unknown auth response header: %04x", head));
    }

    private void waitForStateUpdate() {
        try {
            BandState oldState = new BandState(mState);
            long startTime = System.currentTimeMillis();
            for (;;) {
                Thread.sleep(REFRESH_TIMEOUT);
                if (mState.isNewerThan(oldState)) break;
                if (System.currentTimeMillis() - startTime > USR_INTERACTION_TIMEOUT) break;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForStateUpdate: interrupted: " + e.getMessage(), e);
        }
    }

    private boolean turnOnHeartRateNotify() {
        Log.i(TAG, "turning on heart rate notification");
        ResponseWaiter waiter = new ResponseWaiter(REFRESH_TIMEOUT);
        BleManager.getInstance().notify(
                mBleDevice, Protocol.Service.HEART_RATE, Protocol.Characteristic.HEART_RATE_MEASURE,
                new BleNotifyCallback() {
                    @Override public void onNotifySuccess() {
                        if (!mState.isMeasuringHeartRate()) mState.setHeartNotify(true);
                        waiter.ok();
                        Log.i(TAG, "turnOnHeartRateNotify: succeed");
                    }
                    @Override public void onNotifyFailure(BleException exception) {
                        waiter.fail();
                        mState.setHeartNotify(false);
                        Log.i(TAG, "turnOnHearRateNotify: failed");
                    }
                    @Override public void onCharacteristicChanged(byte[] data) {
                        mState.setHeartMeasuring(true);
                        parseHeartRate(data);
                    }
                }
        );
        return waiter.work();
    }

    private boolean turnOffHeartRateNotify() {
        Log.i(TAG, "turning off heart rate notification");
        boolean success = BleManager.getInstance().stopNotify(mBleDevice,
                Protocol.Service.HEART_RATE, Protocol.Characteristic.HEART_RATE_MEASURE);
        if (success) mState.setHeartNotify(false);
        return success;
    }

    private void parseHeartRate(byte[] data) {
        // In most cases, only data[1] contributes, not sure about data[0], which is usually 0
        int heartRate = (int)data[0] * 0x100 + data[1];
        Log.i(TAG, "parseHeartRate: heartRate=" + heartRate);
        if (mHeartRateHandler != null) mHeartRateHandler.accept(heartRate);
    }

    private boolean enableHeartRateContinuousMonitor() {
        Log.i(TAG, "enabling heart rate continuous monitor");
        ResponseWaiter waiter = new ResponseWaiter(REFRESH_TIMEOUT);
        BleManager.getInstance().write(
                mBleDevice,
                Protocol.Service.HEART_RATE, Protocol.Characteristic.HEART_RATE_CONTROL,
                Protocol.Command.HEART_START_CONTINUOUS,
                new BleWriteCallback() {
                    @Override public void onWriteSuccess(int current, int total, byte[] justWrite) {
                        waiter.ok();
                        Log.i(TAG, "enableHeartRateContinuousMonitor: succeed");
                    }
                    @Override public void onWriteFailure(BleException exception) {
                        waiter.fail();
                        Log.i(TAG, "enableHeartRateContinuousMonitor: failed");
                    }
                });
        return waiter.work();
    }

    private boolean disableHeartRateContinuousMonitor() {
        Log.i(TAG, "disabling heart rate continuous monitor");
        ResponseWaiter waiter = new ResponseWaiter(REFRESH_TIMEOUT);
        BleManager.getInstance().write(
                mBleDevice,
                Protocol.Service.HEART_RATE, Protocol.Characteristic.HEART_RATE_CONTROL,
                Protocol.Command.HEART_STOP_CONTINUOUS,
                new BleWriteCallback() {
                    @Override public void onWriteSuccess(int current, int total, byte[] justWrite) {
                        mState.setHeartMeasuring(false);
                        waiter.ok();
                        Log.i(TAG, "disableHeartRateContinuousMonitor: succeed");
                    }
                    @Override public void onWriteFailure(BleException exception) {
                        waiter.fail();
                        Log.i(TAG, "disableHeartRateContinuousMonitor: failed");
                    }
                });
        return waiter.work();
    }

    private void enableHeartRatePing() {
        mHeartRatePingTimer = TimerUtil.repeatPer(Protocol.Time.HEART_KEEP_ALIVE_PERIOD, () -> {
            Log.i(TAG, "pinging heart rate monitor...");
            BleManager.getInstance().write(
                    mBleDevice,
                    Protocol.Service.HEART_RATE, Protocol.Characteristic.HEART_RATE_CONTROL,
                    Protocol.Command.HEART_KEEP_ALIVE,
                    new BleWriteCallback() {
                        @Override public void onWriteSuccess(int current, int total, byte[] justWrite) {
                            Log.i(TAG, "pingHeartRate :)");
                            mState.setHeartMeasuring(true);
                        }
                        @Override public void onWriteFailure(BleException exception) {
                            Log.i(TAG, "pingHeartRate :(");
                            mState.setHeartMeasuring(false);
                        }
                    });
        });
    }

    private void disableHeartRatePing() {
        if (mHeartRatePingTimer != null) {
            mHeartRatePingTimer.cancel();
            mHeartRatePingTimer = null;
            mState.setHeartMeasuring(false);
        }
    }

    // see https://github.com/Freeyourgadget/Gadgetbridge/pull/703/files for details
    private void parseAcceleration(byte[] value) {
        if (value.length <= 2 || (value.length - 2) % 6 != 0) {
            Log.w(TAG, "parseAcceleration: got unexpected sensor data with length: " + value.length);
            return;
        }
        float count = 0;
        float x = 0, y = 0, z = 0;
        for (int i = 2; i < value.length; i += 6, count += 1) {
            x += (value[i]   | (value[i+1] << 8));
            y += (value[i+2] | (value[i+3] << 8));
            z += (value[i+4] | (value[i+5] << 8));
        }
        x /= count; y /= count; z /= count;
        Log.i(TAG, String.format("parseAcceleration: x=%.3f, y=%.3f, z=%.3f, total=%.3f", x, y, z, Math.sqrt(x*x + y*y + z*z)));
        if (mAccelerationHandler != null) mAccelerationHandler.accept(x, y, z);
    }

    private boolean turnOnRawDataNotify() {
        ResponseWaiter waiter = new ResponseWaiter(REFRESH_TIMEOUT);
        BleManager.getInstance().notify(mBleDevice, Protocol.Service.BASIC, Protocol.Characteristic.SENSOR_DATA,
                new BleNotifyCallback() {
                    @Override public void onNotifySuccess() {
                        mState.setRawNotify(true);
                        waiter.ok();
                        Log.i(TAG, "turnOnRawDataNotify: succeeded");
                    }
                    @Override public void onNotifyFailure(BleException exception) {
                        mState.setRawNotify(false);
                        waiter.fail();
                        Log.e(TAG, "turnOnRawDataNotify: failed");
                    }
                    @Override public void onCharacteristicChanged(byte[] data) {
                        mState.setAccelerationMeasuring(true);
                        Log.i(TAG, "received raw data: length=" + data.length + ", data=" + BytesUtil.toHexStr(data));
                        parseAcceleration(data);
                    }
                });
        return waiter.work();
    }

    // https://github.com/Freeyourgadget/Gadgetbridge/pull/894
    private boolean enableAcceleration() {
        ResponseWaiter waiter = new ResponseWaiter(REFRESH_TIMEOUT);
        BleManager.getInstance().write(
                mBleDevice, Protocol.Service.BASIC, Protocol.Characteristic.SENSOR_CONTROL,
                Protocol.Command.ACCELERATION_INIT,
                new BleWriteCallback() {
                    @Override public void onWriteSuccess(int current, int total, byte[] justWrite) {
                        waiter.ok();
                        Log.i(TAG, "enableAcceleration step 1: succeeded");
                    }
                    @Override public void onWriteFailure(BleException exception) {
                        mState.setAccelerationMeasuring(false);
                        waiter.fail();
                        Log.e(TAG, "enableAcceleration step 1: failed");
                    }
                });
        if (!waiter.work()) return false;

        waiter.reset();
        BleManager.getInstance().write(mBleDevice, Protocol.Service.BASIC, Protocol.Characteristic.SENSOR_CONTROL,
                Protocol.Command.ACCELERATION_START,
                new BleWriteCallback() {
                    @Override public void onWriteSuccess(int current, int total, byte[] justWrite) {
                        mState.setAccelerationMeasuring(true);
                        waiter.ok();
                        Log.i(TAG, "enableAcceleration step 2: succeeded");
                    }
                    @Override public void onWriteFailure(BleException exception) {
                        mState.setAccelerationMeasuring(false);
                        waiter.fail();
                        Log.i(TAG, "enableAcceleration step 2: failed");
                    }
                });
        return waiter.work();
    }
}
