package in.wangziq.fitnessrecorder;

import android.bluetooth.BluetoothGatt;
import android.util.Log;

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
import in.wangziq.fitnessrecorder.utils.TimerUtil;


public class MiBand2 {

    private static final String TAG = MiBand2.class.getSimpleName();

    private BleManager mBleManager;
    private byte[] mAuthKey;
    private String mMacAddress;
    private BleDevice mBleDevice;
    private AuthState mAuthState;
    private Timer mHeartRatePingTimer;

    public enum AuthState {
        NeverPaired,
        EverPaired,
        Pairing,
        RePairing,
        Authenticated
    }

    public MiBand2(String macAddress, byte[] authKey) {
        mBleManager = BleManager.getInstance();
        mMacAddress = macAddress;
        mAuthKey = authKey;
        setAuthState(AuthState.EverPaired);

        Log.i(TAG, String.format("MiBand2 construction: macAddress=%s, authKey=%s",
                mMacAddress, BytesUtil.toHexStr(mAuthKey)));
    }

    public void setHasEverPaired(boolean ever) {
        setAuthState(ever ? AuthState.EverPaired : AuthState.NeverPaired);
    }

    private synchronized void setAuthState(AuthState newState) {
        Log.i(TAG, String.format("setAuthState: old=%s, new=%s",
                mAuthState == null ? "null" : mAuthState.name(),
                newState == null ? "null" : newState.name()));
        mAuthState = newState;
    }

    public void connect() {
        mBleManager.connect(mMacAddress, new BleGattCallback() {
            @Override
            public void onStartConnect() {
                Log.i(TAG, String.format("onStartConnect: connecting to device[mac=%s]", mMacAddress));
            }

            @Override
            public void onConnectFail(BleDevice bleDevice, BleException exception) {
                Log.i(TAG, "onConnectFail");
            }

            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                Log.i(TAG, "onConnectSuccess: device: " + bleDevice.getName());
                mBleDevice = bleDevice;
                turnAuthNotification(true);
            }

            @Override
            public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
                Log.i(TAG, "onDisConnected");
                cleanWhenDisconnected();
            }
        });
    }

    private void turnAuthNotification(boolean enable) {
        if (enable) {
            mBleManager.notify(mBleDevice, Protocol.SERVICE_AUTH, Protocol.CHARACTERISTIC_AUTH, new BleNotifyCallback() {
                @Override
                public void onNotifySuccess() {
                    Log.i(TAG, "turnAuthNotification onNotifySuccess");
                    if (mAuthState == AuthState.NeverPaired) { // FIXME: if never paired before
                        sendKey();
                    } else if (mAuthState == AuthState.EverPaired) {
                        requestRandom();
                    }
                }

                @Override
                public void onNotifyFailure(BleException exception) {
                    Log.w(TAG, "onNotifyFailure: " + exception.getDescription());
                }

                @Override
                public void onCharacteristicChanged(byte[] data) {
                    consumeAuthNotification(data);
                }
            });
        } else {
            mBleManager.stopNotify(mBleDevice, Protocol.SERVICE_AUTH, Protocol.CHARACTERISTIC_AUTH);
        }
    }

    private void consumeAuthNotification(byte[] notice) {
        Log.i(TAG, "consumeAuthNotification: received notification: " + BytesUtil.toHexStr(notice));

        if (notice.length < 3) {
            Log.w(TAG, "consumeAuthNotification: data length < 3!");
            return;
        }

        int head = (int)notice[0] << 16 | (int)notice[1] << 8 | (int)notice[2];

        switch (head) {
            case Protocol.SEND_KEY_RESPONSE_OK:
                Log.i(TAG, "consumeAuthNotification: key received");
                requestRandom();
                break;
            case Protocol.SEND_KEY_RESPONSE_OOPS:
                Log.i(TAG, "consumeAuthNotification: key receiving failed");
                setAuthState(AuthState.NeverPaired);
                break;
            case Protocol.RAND_RESPONSE_OK:
                Log.i(TAG, "consumeAuthNotification: random number received");
                byte[] body = Arrays.copyOfRange(notice, 3, notice.length);
                sendEncryptedRandom(body);
                break;
            case Protocol.RAND_RESPONSE_OOPS:
                Log.i(TAG, "consumeAuthNotification: random number request error");
                setAuthState(mAuthState == AuthState.RePairing ? AuthState.EverPaired : AuthState.NeverPaired);
                break;
            case Protocol.AUTH_RESPONSE_OK:
                setAuthState(AuthState.Authenticated);
                Log.i(TAG, "consumeAuthNotification: authenticated successfully");
                Log.i(TAG, "consumeAuthNotification: turn off authentication notification...");
                turnAuthNotification(false);
                break;
            case Protocol.AUTH_RESPONSE_OOPS:
                Log.i(TAG, "consumeAuthNotification: encrypted number does not match");
                setAuthState(AuthState.NeverPaired);
                break;
            default:
                Log.i(TAG, String.format("onCharacteristicChanged: unknown response header: %04x", head));
                setAuthState(AuthState.NeverPaired);
        }
    }

    private void sendKey() {
        setAuthState(AuthState.Pairing);
        Log.i(TAG, "sendKey: sending");
        mBleManager.write(mBleDevice, Protocol.SERVICE_AUTH, Protocol.CHARACTERISTIC_AUTH,
                BytesUtil.combine(Protocol.SEND_KEY_CMD, mAuthKey),
                new BleWriteCallback() {
                    @Override
                    public void onWriteSuccess(int current, int total, byte[] justWrite) {
                        Log.i(TAG, "sendKey onWriteSuccess: sent " + BytesUtil.toHexStr(justWrite));
                    }

                    @Override
                    public void onWriteFailure(BleException exception) {
                        Log.e(TAG, "sendKey onWriteFailure: " + exception.getDescription());
                        setAuthState(AuthState.NeverPaired);
                    }
                });
    }

    private void requestRandom() {
        Log.i(TAG, "requestRandom: sending request for random number...");
        if (mAuthState == AuthState.EverPaired) setAuthState(AuthState.RePairing);
        mBleManager.write(mBleDevice, Protocol.SERVICE_AUTH, Protocol.CHARACTERISTIC_AUTH,
                Protocol.RAND_REQUEST,
                new BleWriteCallback() {
                    @Override
                    public void onWriteSuccess(int current, int total, byte[] justWrite) {
                        Log.i(TAG, "requestRandom onWriteSuccess: sent " + BytesUtil.toHexStr(justWrite));
                    }

                    @Override
                    public void onWriteFailure(BleException exception) {
                        Log.e(TAG, "requestRandom onWriteFailure: " + exception.getDescription());
                        setAuthState(mAuthState == AuthState.RePairing ? AuthState.EverPaired : AuthState.NeverPaired);
                    }
                });
    }

    private void sendEncryptedRandom(byte[] random) {
        Log.i(TAG, "sendEncryptedRandom: sending encrpyted random number...");
        mBleManager.write(mBleDevice, Protocol.SERVICE_AUTH, Protocol.CHARACTERISTIC_AUTH,
                BytesUtil.combine(Protocol.SEND_ENCRYPTED_CMD, aesEncrypt(random)),
                new BleWriteCallback() {
                    @Override
                    public void onWriteSuccess(int current, int total, byte[] justWrite) {
                        Log.i(TAG, "sendEncryptedRandom onWriteSuccess: sent " + BytesUtil.toHexStr(justWrite));
                    }

                    @Override
                    public void onWriteFailure(BleException exception) {
                        Log.e(TAG, "sendEncryptedRandom onWriteFailure: " + exception.getDescription());
                        setAuthState(mAuthState == AuthState.RePairing ? AuthState.EverPaired : AuthState.NeverPaired);
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

    public void turnOnHeartRate(HeartRateConsumer callback) {
        mBleManager.write(mBleDevice, Protocol.SERVICE_HEART_RATE, Protocol.CHARACTERISTIC_HEART_RATE_CONTROL,
                Protocol.HEART_STOP_CONTINUOUS, emptyWriteCallback("stop continuous heart rate monitor"));
        TimerUtil.doAfter(100, () -> mBleManager.write(mBleDevice, Protocol.SERVICE_HEART_RATE, Protocol.CHARACTERISTIC_HEART_RATE_CONTROL,
                Protocol.HEART_STOP_MANUAL, emptyWriteCallback("stop manual heart rate measurement")));
        TimerUtil.doAfter(500, () ->
            mBleManager.notify(mBleDevice, Protocol.SERVICE_HEART_RATE, Protocol.CHARACTERISTIC_HEART_RATE_MEASURE,
                    new BleNotifyCallback() {
                        @Override
                        public void onNotifySuccess() {
                            Log.i(TAG, "heart rate measurement notification: onNotifySuccess");
                            startHeartRateContinuous();
                        }
                        @Override
                        public void onNotifyFailure(BleException exception) {
                            Log.i(TAG, "heart rate measurement notification: onNotifyFailure: " + exception.getDescription());
                        }
                        @Override
                        public void onCharacteristicChanged(byte[] data) {
                            Log.d(TAG, "onCharacteristicChanged: data=" + BytesUtil.toHexStr(data));
                            callback.accept((int)data[0] * 0x100 + data[1]);
                        }
                    })
        );
    }

    private void startHeartRateContinuous() {
        Log.i(TAG, "startHeartRateContinuous: sending heart rate monitor continuous command...");
        mBleManager.write(mBleDevice, Protocol.SERVICE_HEART_RATE, Protocol.CHARACTERISTIC_HEART_RATE_CONTROL,
                Protocol.HEART_START_CONTIUOUS,
                new BleWriteCallback() {
                    @Override
                    public void onWriteSuccess(int current, int total, byte[] justWrite) {
                        pingHeartRate(true);
                    }

                    @Override
                    public void onWriteFailure(BleException exception) {
                        Log.e(TAG, "startHeartRateContinuous onWriteFailure: " + exception.getDescription());
                    }
                });
    }

    public void turnOffHeartRate() {
        pingHeartRate(false);
        mBleManager.write(mBleDevice, Protocol.SERVICE_HEART_RATE, Protocol.CHARACTERISTIC_HEART_RATE_CONTROL,
                Protocol.HEART_STOP_CONTINUOUS, emptyWriteCallback("stop continuous heart rate monitor"));
        TimerUtil.doAfter(200, () -> mBleManager.stopNotify(mBleDevice, Protocol.SERVICE_HEART_RATE, Protocol.CHARACTERISTIC_HEART_RATE_MEASURE));
    }

    private void pingHeartRate(boolean enable) {
        if (enable) {
            mHeartRatePingTimer = TimerUtil.repeatPer(Protocol.HEART_KEEP_ALIVE_PERIOD, () -> {
                Log.d(TAG, "pinging heart rate monitor...");
                mBleManager.write(mBleDevice,
                        Protocol.SERVICE_HEART_RATE,
                        Protocol.CHARACTERISTIC_HEART_RATE_CONTROL,
                        Protocol.HEART_KEEP_ALIVE,
                        emptyWriteCallback("heart rate ping"));
            });
        } else {
            if (mHeartRatePingTimer != null) {
                mHeartRatePingTimer.cancel();
                mHeartRatePingTimer = null;
            }
        }
    }

    private void cleanWhenDisconnected() {
        if (mAuthState == AuthState.Authenticated || mAuthState == AuthState.RePairing)
            setAuthState(AuthState.EverPaired);
        else
            setAuthState(AuthState.NeverPaired);

        pingHeartRate(false);
    }

    // Consumer<Integer> substitution
    public interface HeartRateConsumer {
        void accept(Integer heartRate);
    }

    // Helper

    private static BleWriteCallback emptyWriteCallback(String info) {
        return new BleWriteCallback() {
            @Override
            public void onWriteSuccess(int current, int total, byte[] justWrite) {
                Log.i(TAG, info + " onWriteSuccess: sent " + BytesUtil.toHexStr(justWrite));
            }
            @Override
            public void onWriteFailure(BleException exception) {
                Log.e(TAG, info + " onWriteFailure: " + exception.getDescription());
            }
        };
    }

}
