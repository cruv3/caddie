package com.caddie;

import android.app.Application;

import com.caddie.bridge.PhoneBridgeServer;

public class LLMSmartphoneApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PhoneBridgeServer.getInstance().start(this);
    }
}
