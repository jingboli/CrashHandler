package cc.lijingbo.crashhandler;

import android.app.Application;

/**
 * @作者: lijingbo
 * @日期: 2017-10-18 13:32
 */

public class CrashHandlerApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.getInstance().init(getApplicationContext());
    }
}
