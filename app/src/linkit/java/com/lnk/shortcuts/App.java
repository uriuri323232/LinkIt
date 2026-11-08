package com.lnk.shortcuts;

import android.app.Application;
import android.os.StrictMode;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // מאפשר פתיחת תיקיות במנהלי קבצים שמקבלים נתיב (file://) ולא רק מזהה מסמך
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
    }
}
