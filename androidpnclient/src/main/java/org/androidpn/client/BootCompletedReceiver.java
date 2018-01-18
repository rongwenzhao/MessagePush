package org.androidpn.client;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.androidpn.demoapp.R;

/**
 * Created by rongwenzhao on 2018/1/18.
 */

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences preferences = context.getSharedPreferences(Constants.SHARED_PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        if (preferences.getBoolean(Constants.SETTINGS_AUTO_START, true)) {
            // Start the service
            ServiceManager serviceManager = new ServiceManager(context);
            serviceManager.setNotificationIcon(R.drawable.notification);
            serviceManager.startService();
        }
    }
}
