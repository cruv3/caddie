package com.llm_smartphone_v2;

import android.app.Application;

import com.llm_smartphone_v2.bridge.PhoneBridgeServer;

public class LLMSmartphoneApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PhoneBridgeServer.getInstance().start(this);
    }
}
