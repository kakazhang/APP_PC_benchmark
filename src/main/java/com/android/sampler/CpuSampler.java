/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.sampler;

import com.android.ddmlib.*;

import java.io.IOException;

public class CpuSampler extends DeviceSampler {
  /**
   * The device is reachable but no cpu usage response was received in time.
   */
  public static final int TYPE_ERROR = INHERITED_TYPE_START;

  public static final int TYPE_NOT_FOUND = INHERITED_TYPE_START + 1;

  /**
   * Maximum number of samples to keep in memory. We not only sample at {@code SAMPLE_FREQUENCY_MS} but we also receive
   * a sample on every GC.
   */
  public static final int SAMPLES = 2048;

  private Long previousKernelUsage = null;
  private Long previousUserUsage = null;
  private Long previousTotalUptime = null;

  private AndroidDebugBridge mBridge;
  public CpuSampler(int sampleFrequencyMs, AndroidDebugBridge bridge) {
    super(new TimelineData(2, SAMPLES), sampleFrequencyMs);
    mBridge = bridge;
  }

  @Override
  public String getName() {
    return "CPU Sampler";
  }

  @Override
  protected void sample(boolean forced) throws InterruptedException {
    Long kernelCpuUsage = null;
    Long userCpuUsage = null;
    Long totalUptime = null;

    int type = TYPE_DATA;

    if (mBridge != null) {
      try {
        int pid = 18260;
        IDevice[] device = mBridge.getDevices();
        if (device.length == 0)
          return;

        ProcessStatReceiver dumpsysReceiver = new ProcessStatReceiver(pid);
        device[0].executeShellCommand("cat " + "/proc/28837/stat", dumpsysReceiver);

        kernelCpuUsage = dumpsysReceiver.getKernelCpuUsage();
        userCpuUsage = dumpsysReceiver.getUserCpuUsage();

        SystemStatReceiver systemStatReceiver = new SystemStatReceiver();
        device[0].executeShellCommand("cat /proc/stat", systemStatReceiver);
        totalUptime = systemStatReceiver.getTotalUptime();
      }
      catch (TimeoutException e) {
        type = TYPE_TIMEOUT;
      }
      catch (AdbCommandRejectedException e) {
        type = TYPE_ERROR;
      }
      catch (ShellCommandUnresponsiveException e) {
        type = TYPE_UNREACHABLE;
      }
      catch (IOException e) {
        type = TYPE_UNREACHABLE;
      }
    }

    if (kernelCpuUsage != null && userCpuUsage != null && totalUptime != null) {
      if (previousKernelUsage != null && previousUserUsage != null && previousTotalUptime != null) {
        long totalTimeDiff = totalUptime - previousTotalUptime;
        if (totalTimeDiff > 0) {
          float kernelPercentUsage = (float)(kernelCpuUsage - previousKernelUsage) * 100.0f / (float)totalTimeDiff;
          kernelPercentUsage = Math.max(Math.min(kernelPercentUsage, 100.0f), 0.0f);
          float userPercentUsage = (float)(userCpuUsage - previousUserUsage) * 100.0f / (float)totalTimeDiff;
          userPercentUsage = Math.max(Math.min(userPercentUsage, 100.0f), 0.0f);
          myTimelineData.add(System.currentTimeMillis(), type, kernelPercentUsage, userPercentUsage);


          System.out.println("kernelPercentUsage:" + kernelPercentUsage);
          System.out.println("userPercentUsage:" + userPercentUsage);
        }
      }
      previousKernelUsage = kernelCpuUsage;
      previousUserUsage = userCpuUsage;
      previousTotalUptime = totalUptime;
    }
    else {
      final TimelineData timelineData = myTimelineData;
      //noinspection SynchronizationOnLocalVariableOrMethodParameter - TimelineData synchronises on itself, and we need size/get to match
      synchronized (timelineData) {
        if (timelineData.size() > 0) {
          TimelineData.Sample lastSample = timelineData.get(timelineData.size() - 1);
          timelineData.add(System.currentTimeMillis(), TYPE_NOT_FOUND, lastSample.values[0], lastSample.values[1]);
        }
      }

    }
  }

  /**
   * Output receiver for contents in the "/proc/[pid]/stat" pseudo file.
   */
  static final class ProcessStatReceiver extends MultiLineReceiver {
    private final int myPid;
    private Long myUserCpuTicks;
    private Long myKernelCpuTicks;

    private ProcessStatReceiver(int pid) {
      myPid = pid;
    }

    /**
     * Get the parsed user space CPU usage.
     *
     * @return user cpu usage or <code>null</code> if it cannot be determined
     */

    public Long getUserCpuUsage() {
      return myUserCpuTicks;
    }

    /**
     * Get the parsed kernel space CPU usage.
     *
     * @return kernel cpu usage or <code>null</code> if it cannot be determined
     */

    public Long getKernelCpuUsage() {
      return myKernelCpuTicks;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public void processNewLines(String[] lines) {
      String[] tokens = lines[0].split("\\s+");
      if (tokens.length >= 15) {
        // Refer to Linux proc man page for the contents at the specified indices.
        Integer pid = Integer.parseInt(tokens[0]);

        myUserCpuTicks = Long.parseLong(tokens[13]);
        myKernelCpuTicks = Long.parseLong(tokens[14]);
      }
    }
  }

  /**
   * Output receiver for contents in the "/proc/stat" pseudo file.
   */
  static final class SystemStatReceiver extends MultiLineReceiver {
    private Long myTotalUptime = null;

    public Long getTotalUptime() {
      return myTotalUptime;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public void processNewLines(String[] lines) {
      long totalUptime = 0L;

      String[] tokens = lines[0].split("\\s+");
      if (tokens.length < 11 || !tokens[0].equals("cpu")) {
        return;
      }

      // Assuming total uptime is the sum of all given numerical values on the aggregated CPU line.
      for (int i = 1; i < tokens.length; ++i) {
        totalUptime += Long.parseLong(tokens[i]);
      }
      myTotalUptime = totalUptime;
    }
  }
}
