package com.local.smsmail;

import android.app.Application;

public final class ForwarderApp extends Application {
    @Override public void onCreate() { super.onCreate();
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(()->Scheduler.recover(this,false));
    }
}
