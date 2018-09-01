package in.wangziq.fitnessrecorder.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class Messenger extends BroadcastReceiver {
    private static final String TAG = Messenger.class.getSimpleName();

    public abstract static class MessageHandler {
        public abstract String[] getActions();

        public void handleResponse(Intent response) {
            // FIXME
            Log.i(getClass().getSimpleName(), "handleResponse of action" + response.getAction());
        }
    }

    private Context mParent;
    private Map<String, MessageHandler> mMessageHandlers;

    public Messenger(Context context) {
        mParent = context;
        mMessageHandlers = new HashMap<>();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "received broadcast from " + context.getClass().getSimpleName() + ",");
        MessageHandler t;
        final String action = intent.getAction();
        if (action == null || (t = mMessageHandlers.get(action)) == null)
            Log.w(TAG, "unknown action");
        else {
            t.handleResponse(intent);
            Log.i(TAG, "action=" + action);
        }
    }

    public void addHandler(MessageHandler t) {
        LocalBroadcastManager broadcastMgr = LocalBroadcastManager.getInstance(mParent);
        for (String action: t.getActions()) {
            mMessageHandlers.put(action, t);
            broadcastMgr.registerReceiver(this, new IntentFilter(action));
        }
    }

    public void unregister() {
        LocalBroadcastManager.getInstance(mParent).unregisterReceiver(this);
        Log.i(TAG, "unregister");
    }

}
