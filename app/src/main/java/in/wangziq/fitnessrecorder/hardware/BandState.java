package in.wangziq.fitnessrecorder.hardware;

import android.support.annotation.NonNull;

public final class BandState {
    public static final int DEFAULT_VALUE = 0;

    public static final int BLE_CONNECT             = 0b0000000001;
    public static final int AUTH_NOTIFY             = 0b0000000010;
    public static final int KEY_GOT                 = 0b0000000100;
    public static final int RAND_REQUEST            = 0b0000001000;
    public static final int ENCRYPTED               = 0b0000010000;
    public static final int HEART_NOTIFY            = 0b0000100000;
    public static final int HEART_MEASURING         = 0b0001000000;
    public static final int RAW_NOTIFY              = 0b0010000000;
    public static final int ACCELERATION_MEASURING  = 0b0100000000;

    private int mState;
    private long mUpdateTime;

    public BandState() {
        mState = DEFAULT_VALUE;
        mUpdateTime = System.currentTimeMillis();
    }

    public BandState(int value) {
        mState = value;
        mUpdateTime = System.currentTimeMillis();
    }

    // Only for clone
    public BandState(@NonNull BandState o) {
        mState = o.mState;
        mUpdateTime = o.mUpdateTime;
    }

    public int getValue() {
        return mState;
    }

    public void reset() {
        mState = DEFAULT_VALUE;
        mUpdateTime = System.currentTimeMillis();
    }

    public synchronized boolean isNewerThan(BandState anotherState) {
        return anotherState == null || this.mUpdateTime > anotherState.mUpdateTime;
    }

    public boolean isBleConnected() {
        return hasAll(BLE_CONNECT);
    }

    public boolean isAuthNotifyOn() {
        return hasAll(BLE_CONNECT | AUTH_NOTIFY);
    }

    public boolean isKeyGot() {
        return hasAll(BLE_CONNECT | AUTH_NOTIFY | KEY_GOT);
    }

    public boolean isRandRequested() {
        return hasAll(BLE_CONNECT | AUTH_NOTIFY | RAND_REQUEST);
    }

    public boolean isEncrypted() {
        return hasAll(BLE_CONNECT | ENCRYPTED);
    }

    public boolean isHeartRateNotifyOn() {
        return hasAll(BLE_CONNECT | ENCRYPTED | HEART_NOTIFY);
    }

    public boolean isMeasuringHeartRate() {
        return hasAll(BLE_CONNECT | ENCRYPTED | HEART_NOTIFY | HEART_MEASURING);
    }

    public boolean isRawNotifyOn() {
        return hasAll(BLE_CONNECT | ENCRYPTED | RAW_NOTIFY);
    }

    public boolean isMeasuringAcceleration() {
        return hasAll(BLE_CONNECT | ENCRYPTED | RAW_NOTIFY | ACCELERATION_MEASURING);
    }

    private boolean hasAll(int conditions) {
        return (mState & conditions) == conditions;
    }

    public synchronized void setBleConnected(boolean success) {
//        mState = success ? ((mState & (~BLE_CONNECT)) | BLE_CONNECT) : 0;
        mState = (mState & (~BLE_CONNECT)) | (success ? BLE_CONNECT : 0);
        mUpdateTime = System.currentTimeMillis();
    }

    public synchronized void setAuthNotify(boolean enable) {
        mState = (mState & (~AUTH_NOTIFY)) | (enable ? AUTH_NOTIFY : 0);
        mUpdateTime = System.currentTimeMillis();
    }

    public synchronized void setKeyGot(boolean success) {
        mState = (mState & (~KEY_GOT)) | (success ? KEY_GOT : 0);
        mUpdateTime = System.currentTimeMillis();
    }

    public synchronized void setRandRequested(boolean success) {
        mState = (mState & (~RAND_REQUEST)) | (success ? RAND_REQUEST : 0);
        mUpdateTime = System.currentTimeMillis();
    }

    public synchronized void setEncrypted(boolean success) {
        mState = (mState & (~ENCRYPTED)) | (success ? ENCRYPTED : 0);
        mUpdateTime = System.currentTimeMillis();
    }

    public synchronized void setHeartNotify(boolean enable) {
        mState = (mState & (~HEART_NOTIFY)) | (enable ? HEART_NOTIFY : 0);
        mUpdateTime = System.currentTimeMillis();
    }

    public synchronized void setHeartMeasuring(boolean yes) {
        mState = (mState & (~HEART_MEASURING)) | (yes ? HEART_MEASURING : 0);
        mUpdateTime = System.currentTimeMillis();
    }

    public synchronized void setRawNotify(boolean enable) {
        mState = (mState & (~RAW_NOTIFY)) | (enable ? RAW_NOTIFY : 0);
        mUpdateTime = System.currentTimeMillis();
    }

    public synchronized void setAccelerationMeasuring(boolean yes) {
        mState = (mState & (~ACCELERATION_MEASURING)) | (yes ? ACCELERATION_MEASURING : 0);
        mUpdateTime = System.currentTimeMillis();
    }

//    public synchronized void setHeartContinuousMonitor(boolean enable) {
//        mState = (mState & (~HEART_MONITOR)) | (enable ? HEART_MONITOR : 0);
//        mUpdateTime = System.currentTimeMillis();
//    }
//
//    public synchronized void setHeartPing(boolean enable) {
//        mState = (mState & (~HEART_PING)) | (enable ? HEART_PING : 0);
//        mUpdateTime = System.currentTimeMillis();
//    }

    public String toString() {
        // TODO
        return String.format("%9s", Integer.toBinaryString(mState)).replace(' ', '0');
    }

}
