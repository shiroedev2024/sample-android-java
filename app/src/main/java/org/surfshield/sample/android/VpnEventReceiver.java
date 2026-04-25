package org.surfshield.sample.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class VpnEventReceiver extends BroadcastReceiver {
    private static final String TAG = "VpnEventReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String expectedAction = context.getPackageName() + ".leaf.VPN_EVENT";

        if (expectedAction.equals(intent.getAction())) {
            String eventType = intent.getStringExtra("eventType");
            String data = intent.getStringExtra("data");
            long timestamp = intent.getLongExtra("timestamp", 0L);

            Log.d(TAG, "Received VPN Broadcast -> Event: " + eventType + " | Data: " + data);
        }
    }
}
