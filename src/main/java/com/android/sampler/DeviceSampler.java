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

import com.android.ddmlib.Client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public abstract class DeviceSampler implements Runnable {
  /**
   * Sample type when the device cannot be seen.
   */
  public static final int TYPE_UNREACHABLE = 0;
  /**
   * Sample created from a valid response.
   */
  public static final int TYPE_DATA = 1;
  /**
   * The device is reachable but no response was received in time.
   */
  public static final int TYPE_TIMEOUT = 2;
  /**
   * This is the valid start index for inherited classes.
   */
  public static final int INHERITED_TYPE_START = 3;

  protected TimelineData myTimelineData;
  protected final List<TimelineEventListener> myListeners = new ArrayList<TimelineEventListener>();
  protected int mySampleFrequencyMs;
  /**
   * The future representing the task being executed, which will return null upon successful completion.
   * If null, no current task is being executed.
   */
  //protected volatile Future<?> myExecutingTask;
  protected volatile Client myClient;
  private final Semaphore myDataSemaphore;
  protected volatile boolean myRunning;
  protected volatile CountDownLatch myTaskStatus;
  protected volatile boolean myIsPaused;

  /**
   *ThreadPool for execute sampler runnable
   */
  private final static int NUM_OF_PROCESSORS = Runtime.getRuntime().availableProcessors();
  private static ExecutorService mExecutorService = Executors.newFixedThreadPool(NUM_OF_PROCESSORS);

  public DeviceSampler(TimelineData timelineData, int sampleFrequencyMs) {
    myTimelineData = timelineData;
    mySampleFrequencyMs = sampleFrequencyMs;
    myDataSemaphore = new Semaphore(0, true);
  }

  @SuppressWarnings("ConstantConditions")
  public void start() {
    if (!myRunning) {
      myRunning = true;
      myTaskStatus = new CountDownLatch(1);
      mExecutorService.execute(this);

      for (TimelineEventListener listener : myListeners) {
        listener.onStart();
      }
    }
  }

  @SuppressWarnings("ConstantConditions")
  public void stop() {
    System.out.println("stop sampling");
    if (myRunning) {
      myRunning = false;

      myDataSemaphore.release();

      //myExecutingTask.cancel(true);
      try {
        myTaskStatus.await();
      }
      catch (InterruptedException ignored) {
        // We're stopping anyway, so just ignore the interruption.
      }

      if (myClient != null) {
        myClient.setHeapUpdateEnabled(false);
      }

      //myExecutingTask = null;

      for (TimelineEventListener listener : myListeners) {
        listener.onStop();
      }
    }
  }

  public TimelineData getTimelineData() {
    return myTimelineData;
  }

  protected boolean requiresSamplerRestart(Client client) {
    return client != myClient;
  }

  public final void setClient(Client client) {
    if (requiresSamplerRestart(client)) {
      stop();
      myClient = client;
      prepareSampler(client);
      myTimelineData.clear();
      if (!myIsPaused) {
        start();
      }
    }
  }

  public final void setIsPaused(boolean paused) {
    myIsPaused = paused;
    if (myIsPaused) {
      if (myClient != null) {
        stop();
      }
    }
    else {
      myTimelineData.clear();
      prepareSampler(myClient);
      start();
    }
  }

  public final boolean getIsPaused() {
    return myIsPaused;
  }

  protected void prepareSampler(Client client) {
  }

  public void addListener(TimelineEventListener listener) {
    myListeners.add(listener);
  }


  protected void forceSample() {
    myDataSemaphore.release();
  }

  @Override
  public void run() {
    long timeToWait = mySampleFrequencyMs;
    while (myRunning) {
      try {
        long start = System.currentTimeMillis();
        boolean acquired = myDataSemaphore.tryAcquire(timeToWait, TimeUnit.MILLISECONDS);
        if (myRunning && !myIsPaused) {
          sample(acquired);
        }
        timeToWait -= System.currentTimeMillis() - start;
        if (timeToWait <= 0) {
          timeToWait = mySampleFrequencyMs;
        }
      }
      catch (InterruptedException e) {
        myRunning = false;
      }
    }
    myTaskStatus.countDown();
  }

  public abstract String getName();

  protected abstract void sample(boolean forced) throws InterruptedException;
}
