package com.nicro.clientapptest;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Start the service
       /* ServiceManager serviceManager = new ServiceManager(this);
        serviceManager.setNotificationIcon(org.androidpn.demoapp.R.drawable.notification);
        serviceManager.startService();*/
    }
}
