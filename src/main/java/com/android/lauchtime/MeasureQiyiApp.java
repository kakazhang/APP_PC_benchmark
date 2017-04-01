package com.android.lauchtime;

import com.android.benchmark.ProcessList;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.sampler.CpuSampler;
import com.android.sampler.MemorySampler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by kakazhang on 17-3-30.
 */

public class MeasureQiyiApp extends MeasureAppInfo {
    private static final int BOOT_INDEX = 0;
    private static final int START_INDEX = 1;

    private IDevice mDevice;
    private List<MeasureParams> mParams;
    private BootActionReceiver mReceiver;
    private ActivityStartReceiver mActivityReceiver;
    private NullReceiver mNullReceiver;

    private List<Long> mAdTime;
    private List<Long> mNoAdTime;

    private ProcessList mProcessList;
    public static class MeasureParams {
        public String cmpName;
        public String filterName;
        public String startTag;
        public String endTag;

        public int repeatTime;
        public int timeout;

    }

    public MeasureQiyiApp(IDevice device, List<MeasureParams> params) {
        mDevice = device;
        mParams = params;
        mProcessList = new ProcessList();

        mAdTime = new ArrayList<Long>();
        mNoAdTime = new ArrayList<Long>();
        mActivityReceiver = null;
        mNullReceiver = new NullReceiver();
    }

    @Override
    public void measureStartTime() {
        //measure app boot time
        MeasureBootTime();

        System.out.println();
        /*measure activity start time*/
        measureActivityTime();
    }

    @Override
    public void measureOtherInfo() {
        /*first half screen information*/
        measurePlayerInfo(mDevice, false);

        showSeperateLine();

        /*second, full screen information*/
        measurePlayerInfo(mDevice, true);

        MeasureDownload(5 * 60 * 1000);
    }

    private void MeasureDownload(int timeout) {
        final String className = "tutorial.ua.com.automation.AndroidTester";
        final String cmp = "tutorial.ua.com.automation.test/android.support.test.runner.AndroidJUnitRunner";
        StringBuilder builder = new StringBuilder("am instrument -w -r -e debug false -e class");
        builder.append(" " + className);
        builder.append(" " + cmp);

        System.out.println("start download");

        try {
            mDevice.executeShellCommand(builder.toString(), mNullReceiver, timeout);
        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (AdbCommandRejectedException e) {
            e.printStackTrace();
        } catch (ShellCommandUnresponsiveException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showSeperateLine() {
        System.out.println("-----------------------------------");
    }

    private void measurePlayerInfo(IDevice device, boolean fullScreen) {
        if (fullScreen)
            System.out.println("full screen information:");
        else
            System.out.println("half screen information:");

        sendToPlayerCmd(fullScreen);

        doSnap(5*1000);
        mProcessList.listAllProcesses(device);
        int pid = mProcessList.getPid("com.qiyi.video");

        CpuSampler cpuSampler = new CpuSampler(device, pid, 1000);
        MemorySampler memSampler = new MemorySampler(device, "com.qiyi.video", 1000);

        cpuSampler.start();
        memSampler.start();

        doSnap(5 * 1000);
        cpuSampler.stop();
        memSampler.stop();

        List<CpuSampler.CpuInfo> cpuInfo = cpuSampler.getCpuInfo();
        List<MemorySampler.MemoryInfo> memInfo = memSampler.getMemoryInfo();

        System.out.println("Cpu information:");

        for (CpuSampler.CpuInfo cpu: cpuInfo) {
            System.out.println(cpu.KernelUsage + cpu.UserUsage);
        }

        System.out.println("Memory information:");

        for (MemorySampler.MemoryInfo mem: memInfo) {
            System.out.println(mem.total_pss);
        }
    }

    private void doSnap(long timeout) {

        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void MeasureBootTime() {
        MeasureParams bootParams = mParams.get(BOOT_INDEX);
        mReceiver = new BootActionReceiver(bootParams);
        mProcessList.listAllProcesses(mDevice);
        if (mProcessList.getPid("com.qiyi.video") != -1) {
            sendKillCmd();
        }

        int rt = bootParams.repeatTime;
        for (int i = 0; i < rt; i++) {
            doSnap(1000);

            sendBootCmd(bootParams);

            doSnap(1000);

            sendKillCmd();
        }

        System.out.println("ADTime:");
        for (Long t : mAdTime) {
            System.out.println(t);
        }

        System.out.println("NOADTime:");
        for (Long t : mNoAdTime) {
            System.out.println(t);
        }
    }

    private void sendBootCmd(MeasureParams params) {
        try {
            final String startCmd = "am start -n " + params.cmpName;
            final String filterCmd = params.filterName;
            final String action = startCmd + " && " + filterCmd;
            System.out.println(action);

            mDevice.executeShellCommand(action, mReceiver, params.timeout);
        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (AdbCommandRejectedException e) {
            e.printStackTrace();
        } catch (ShellCommandUnresponsiveException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        BootResult result = mReceiver.getStartResult();
        if (result.hasAd)
            mAdTime.add(result.bootTime);
        else
            mNoAdTime.add(result.bootTime);

        mReceiver.reset();
    }

    /*list for collecting activity start time*/
    private final static List<Long> mPlayerTimes = new ArrayList<Long>();

    private void measureActivityTime() {
        MeasureParams startParams = mParams.get(START_INDEX);
        final int rt = startParams.repeatTime;
        mActivityReceiver = new ActivityStartReceiver(startParams);

        mProcessList.listAllProcesses(mDevice);
        if (mProcessList.getPid("com.qiyi.video") == -1) {
            sendDisplayMainCmd();
            doSnap(5000);
        }

        System.out.println("start player time:");
        for (int i = 0; i < rt; i++) {
            doSnap(1000);

            sendPlayCmd(startParams);

            doSnap(1000);

            sendDisplayMainCmd();
        }

        for (Long l : mPlayerTimes)
            System.out.println(l);
    }

    private void sendPlayCmd(MeasureParams params) {
        final String startCmd = params.cmpName;

        try {
            mDevice.executeShellCommand(startCmd, mActivityReceiver, params.timeout);
        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (AdbCommandRejectedException e) {
            e.printStackTrace();
        } catch (ShellCommandUnresponsiveException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        long cost = mActivityReceiver.getStartTime();
        mPlayerTimes.add(cost);

        mActivityReceiver.reset();
    }

    private void sendDisplayMainCmd() {
        try {
            mDevice.executeShellCommand("am start -n \"com.qiyi.video/org.qiyi.android.video.MainActivity\"", mNullReceiver);
        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (AdbCommandRejectedException e) {
            e.printStackTrace();
        } catch (ShellCommandUnresponsiveException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendToPlayerCmd(boolean fullscreen) {
        String cmd = null;
        if (fullscreen)
            cmd = "am start -W -a android.intent.action.VIEW -d \"iqiyi://mobile/player?aid=204218901&&rotation=2\"";
        else
            cmd = "am start -W -a android.intent.action.VIEW -d \"iqiyi://mobile/player?aid=204218901\"";

        try {
            mDevice.executeShellCommand(cmd, mNullReceiver);
        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (AdbCommandRejectedException e) {
            e.printStackTrace();
        } catch (ShellCommandUnresponsiveException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendKillCmd() {
        try {
            mDevice.executeShellCommand("am force-stop com.qiyi.video", mNullReceiver);
        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (AdbCommandRejectedException e) {
            e.printStackTrace();
        } catch (ShellCommandUnresponsiveException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class BootResult {
        boolean hasAd;
        long bootTime;
    }

    private class BootActionReceiver extends MultiLineReceiver {
        private volatile boolean isCancelled = false;

        private MeasureParams mBootParams;
        private BootResult result = new BootResult();

        private long beginTime = 0;
        public BootActionReceiver(MeasureParams bootParams) {
            mBootParams = bootParams;
            result.bootTime = 0;
            result.hasAd = false;
        }

        public BootResult getStartResult() {
            return result;
        }

        @Override
        public void processNewLines(String[] lines) {
            for (String line : lines) {
                if (line.contains(mBootParams.startTag)) {
                    beginTime = System.currentTimeMillis();
                } else if (line.contains("ad_image_url")) {
                    result.hasAd = true;
                }  else if (line.contains(mBootParams.endTag)) {
                    isCancelled = true;
                    long endTime = System.currentTimeMillis();
                    result.bootTime = (endTime - beginTime);
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return isCancelled;
        }

        public void reset() {
            result.bootTime = 0;
            result.hasAd = false;
            isCancelled = false;
        }
    }

    private class ActivityStartReceiver extends MultiLineReceiver {
        private volatile boolean isCancelled = false;
        private long costTime;
        private MeasureParams mParams;

        public long getStartTime() {
            return costTime;
        }

        public ActivityStartReceiver(MeasureParams params) {
            mParams = params;
            costTime = 0;
        }

        @Override
        public void processNewLines(String[] lines) {
            for (String line: lines) {
                if (line.contains(mParams.endTag)) {
                    isCancelled = true;
                    String[] strs = line.split("\\s+");
                    if (strs.length > 0) {
                        costTime = Long.parseLong(strs[strs.length-1]);
                    }
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return isCancelled;
        }

        public void reset() {
            isCancelled = false;
            costTime = 0;
        }
    }

    private class NullReceiver extends MultiLineReceiver {
        @Override
        public void processNewLines(String[] lines) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
