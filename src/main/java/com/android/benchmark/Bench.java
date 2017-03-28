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
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sampler.CpuSampler;
import com.android.sampler.MemorySampler;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

public class Bench {
    private static final int TRY_COUNT = 100;
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
                Thread.sleep(50);
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
        AndroidDebugBridge.init(true);

        mBridge = AndroidDebugBridge.createBridge(ADB_PATH, false);
        if (!waiDeviceList()) {
            System.out.println("connect to adb failed");
            System.exit(-1);
        }
    }


    private static void startCpuSampler(int timeout) {
        CpuSampler sampler = new CpuSampler(1000, mBridge);
        sampler.start();
    }

    private static void startMemorySampler(int timeout) {
        MemorySampler sampler = new MemorySampler(timeout, mBridge, "com.qiyi.video");
        sampler.start();
    }

    private static AndroidDebugBridge.IDeviceChangeListener mDeviceChangeListener = new AndroidDebugBridge.IDeviceChangeListener() {
        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public void deviceConnected(IDevice device) {
            mProcessList = new ProcessList();
            mProcessList.listAllProcesses(device);

            int pid = mProcessList.getPid("com.qiyi.video");
            System.out.println("pid:" + pid);
            startMemorySampler(1000);
        }

        @Override
        public void deviceDisconnected(IDevice device) {

        }

        @Override
        public void deviceChanged(IDevice device, int changeMask) {

        }
    };

    private static ProcessList mProcessList;

    public static void main(String[] args) throws Exception {
        Bench bench = new Bench();

        AndroidDebugBridge.addDeviceChangeListener(mDeviceChangeListener);

        bench.bridgeInit();

        //mBridge.terminate();
    }
}
