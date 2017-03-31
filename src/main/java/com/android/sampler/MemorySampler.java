package com.android.sampler;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by kakazhang on 17-3-28.
 */

public class MemorySampler extends DeviceSampler {
    /**
     * Maximum number of samples to keep in memory. We not only sample at {@code SAMPLE_FREQUENCY_MS} but we also receive
     * a sample on every GC.
     */
    public static final int SAMPLES = 2048;

    private final String mPkgName;
    private IDevice mDevice = null;
    private MemoryReceiver mReceiver;
    private List<MemoryInfo> mListMemory;

    public MemorySampler(IDevice device, String pkgName, int sampleFrequencyMs) {
        super(new TimelineData(2, SAMPLES), sampleFrequencyMs);

        mPkgName = pkgName;
        mDevice = device;
        mListMemory = new ArrayList<MemoryInfo>();

        mReceiver = new MemoryReceiver();
    }

    public List<MemoryInfo> getMemoryInfo() {
        return mListMemory;
    }

    @Override
    public String getName() {
        return "Memory sampler";
    }

    @Override
    protected void sample(boolean forced) throws InterruptedException {
        if (mDevice == null) {
            System.out.println("No connected device");
            System.exit(-1);
        }

        try {
            mDevice.executeShellCommand("dumpsys meminfo " +mPkgName, mReceiver);
        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (AdbCommandRejectedException e) {
            e.printStackTrace();
        } catch (ShellCommandUnresponsiveException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        MemoryInfo info = mReceiver.getMemoryInfo();
        System.out.println("PSS:" + info.total_pss);
        System.out.println("Total_heapsize:" + info.total_heapsize);
        System.out.println("Total_heapalloc:" + info.total_heapalloc);

        mListMemory.add(info);
    }

    public static class MemoryInfo {
        private Float native_pss = null;
        private Float native_heapsize = null;
        private Float native_heapalloc = null;

        private Float dalvik_pss = null;
        private Float dalvik_heapsize = null;
        private Float dalvik_heapalloc = null;

        public Float total_pss = null;
        public Float total_heapsize = null;
        public Float total_heapalloc = null;
    }

    private class MemoryReceiver extends MultiLineReceiver {
        private static final String DALVIK_MATCHER = "Dalvik Heap";
        private static final String NATIVE_MATCHER = "Native Heap";
        private static final String TOTAL_MATCHER = "TOTAL";

        private static final int TOTAL_LINES = 3;
        private List<String> mDumpInfos = new ArrayList<String>();
        private MemoryInfo mInfo = new MemoryInfo();


        public MemoryInfo getMemoryInfo() {
            return mInfo;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void processNewLines(String[] lines) {
            int count = 0;
            //here we just collect dalvik, native and total memory info
            for (String line : lines) {
                 if (line.contains(NATIVE_MATCHER) || line.contains(DALVIK_MATCHER)
                         || line.contains(TOTAL_MATCHER)) {
                     mDumpInfos.add(line);
                     count++;

                     if (count == TOTAL_LINES)
                         break;
                 }
            }

            parseMemoryInfo(mDumpInfos);
            mDumpInfos.clear();
        }

        private void parseMemoryInfo(List<String> dumpInfos) {
            for (String str : dumpInfos) {
                if (str.contains(NATIVE_MATCHER))
                    parseDalvikOrInfo(str, false);
                else if (str.contains(DALVIK_MATCHER))
                    parseDalvikOrInfo(str, true);
                else {
                    String[] strs = str.split("\\s+");
                    mInfo.total_pss = Float.parseFloat(strs[1]);
                    mInfo.total_heapsize = Float.parseFloat(strs[5]);
                    mInfo.total_heapalloc = Float.parseFloat(strs[6]);
                }
            }
        }

        private void parseDalvikOrInfo(String str, boolean dalvik) {
            String[] strs = str.split("\\s+");

            if (dalvik) {
                mInfo.native_pss = Float.parseFloat(strs[2]);
                mInfo.dalvik_heapsize = Float.parseFloat(strs[6]);
                mInfo.dalvik_heapalloc = Float.parseFloat(strs[7]);
            } else {
                mInfo.dalvik_pss = Float.parseFloat(strs[2]);
                mInfo.native_heapsize = Float.parseFloat(strs[6]);
                mInfo.native_heapalloc = Float.parseFloat(strs[7]);
            }

        }

    }

}
