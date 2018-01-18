package org.androidpn.demoapp;

import android.app.Application;

import org.litepal.LitePal;

/**
 * Created by rongwenzhao on 2018/1/18.
 */

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LitePal.initialize(this);
    }
}
