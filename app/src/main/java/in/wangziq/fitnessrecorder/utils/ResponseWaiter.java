package in.wangziq.fitnessrecorder.utils;

import android.util.Log;

public class ResponseWaiter {
    private static final String TAG = ResponseWaiter.class.getSimpleName();

    private long mRecheckGap;
    private long mTimeout;
    private Boolean success;

    public ResponseWaiter(long recheckGap, long timeout) {
        mRecheckGap = recheckGap;
        mTimeout = timeout;
        success = null;
    }

    public ResponseWaiter(long recheckGap) {
        this(recheckGap, Long.MAX_VALUE);
    }

    public synchronized void ok() {
        success = true;
    }

    public synchronized void fail() {
        success = false;
    }

    public synchronized void reset() {
        success = null;
    }

    public boolean work() {
        try {
            long start = System.currentTimeMillis();
            while (success == null && System.currentTimeMillis() - start < mTimeout)
                Thread.sleep(mRecheckGap);
        } catch (InterruptedException e) {
            Log.e(TAG, "waiting interrupted: " + e.getMessage(), e);
            return false;
        }
        return success;
    }
}
