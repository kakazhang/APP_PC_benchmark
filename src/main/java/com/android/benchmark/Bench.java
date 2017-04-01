/*
 * Copyright 2011 Ladislav Thon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.benchmark;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.lauchtime.MeasureQiyiApp;

import java.util.ArrayList;
import java.util.List;

public class Bench {
    private static final int TRY_COUNT = 10;
    private final static String ADB_PATH = "/home/kakazhang/Android/Sdk/platform-tools/adb";
    private static AndroidDebugBridge mBridge = null;

    public void finish() {
        AndroidDebugBridge.terminate();
    }

    private static boolean waiDeviceList() {
        int count = 0;
        boolean hasDeviceList = true;

        while (!mBridge.hasInitialDeviceList()) {
            try {
                count++;
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (count > TRY_COUNT) {
                System.out.println("adb connected timeout");
                hasDeviceList = false;
                break;
            }
        }

        return hasDeviceList;
    }

    private void bridgeInit() {
        /*init android debug bridge with client support*/
        AndroidDebugBridge.init(false);

        mBridge = AndroidDebugBridge.createBridge(ADB_PATH, true);
        if (!waiDeviceList()) {
            System.out.println("connect to adb failed");
            System.exit(-1);
        }
    }

    private static AndroidDebugBridge.IDeviceChangeListener mDeviceChangeListener = new AndroidDebugBridge.IDeviceChangeListener() {
        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public void deviceConnected(IDevice device) {
            startBenchLaunchTime(device);
        }

        @Override
        public void deviceDisconnected(IDevice device) {

        }

        @Override
        public void deviceChanged(IDevice device, int changeMask) {

        }
    };

    private final static String START_TAG = "Starting: Intent";

    private static void startBenchLaunchTime(IDevice device) {
        List<MeasureQiyiApp.MeasureParams> params = new ArrayList<MeasureQiyiApp.MeasureParams>();

        /*constrcut boot cmd*/
        MeasureQiyiApp.MeasureParams param = new MeasureQiyiApp.MeasureParams();
        param.timeout = 10 * 1000;
        param.cmpName = "com.qiyi.video/.WelcomeActivity";
        param.filterName = "logcat -c && logcat -v time | grep -E \"Displayed | ad_image_url\"";
        param.startTag = START_TAG;
        param.endTag = "MainActivity";
        param.repeatTime = 1;

        /*construct player activity start cmd:default half screen*/
        MeasureQiyiApp.MeasureParams halfscreen = new MeasureQiyiApp.MeasureParams();
        halfscreen.cmpName = "am start -W -a android.intent.action.VIEW -d \"iqiyi://mobile/player?aid=204218901\"";
        halfscreen.endTag = "TotalTime";
        halfscreen.timeout = 10 * 1000;
        halfscreen.repeatTime = 1;

        params.add(param);
        params.add(halfscreen);

        new Thread(new MeasureQiyiApp(device, params)).start();
    }

    public static void main(String[] args) throws Exception {
        Bench bench = new Bench();

        AndroidDebugBridge.addDeviceChangeListener(mDeviceChangeListener);

        bench.bridgeInit();

        //mBridge.terminate();
    }
}
