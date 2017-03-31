package com.android.lauchtime;

/**
 * Created by kakazhang on 17-3-30.
 */

public abstract class MeasureAppInfo implements Runnable {
    @Override
    public void run() {
        startMeasure();
    }

    /*construct measure sequence*/
    public final void startMeasure() {
        measureStartTime();
        measureOtherInfo();
    }

    public abstract void measureStartTime();
    public abstract void measureOtherInfo();
}
