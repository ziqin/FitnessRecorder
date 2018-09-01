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
        StringBuilder logMsg = new StringBuilder()
                .append("received broadcast from ")
                .append(context.getClass().getSimpleName());
        final String action = intent.getAction();
        if (action == null) {
            Log.w(TAG, logMsg.append(", no action").toString());
            return;
        }
        MessageHandler t = mMessageHandlers.get(action);
        if (t == null)
            Log.w(TAG, logMsg.append(", unknown action: ").append(action).toString());
        else {
            t.handleResponse(intent);
            Log.i(TAG, logMsg.append(", action=").append(action).toString());
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
