package com.android.benchmark;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;

import java.io.IOException;
import java.util.ArrayList;


public class ProcessList {
    private ArrayList<ProcessInfo> mProcessList;

    private class ProcessInfo {
        String pkgname;
        int pid;

        public ProcessInfo(int id, String name) {
            pkgname = name;
            pid = id;
        }
    }

    public ProcessList() {

        mProcessList = new ArrayList<ProcessInfo>();
    }

    public int getPid(String name) {
        for (ProcessInfo info : mProcessList) {
            if (info.pkgname.equals(name)) {
                return info.pid;
            }
        }

        return -1;
    }

    public void listAllProcesses(IDevice device) {
        try {
            device.executeShellCommand("ps", new ProcessReceiver());
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

    private class ProcessReceiver extends MultiLineReceiver {
        @Override
        public void processNewLines(String[] lines) {
            for (String line: lines) {
                    String[] delm = line.split("\\s+");
                    if (delm.length == 9) {
                        ProcessInfo info = new ProcessInfo(Integer.parseInt(delm[1]), delm[8]);
                        mProcessList.add(info);
                    }
            }
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
