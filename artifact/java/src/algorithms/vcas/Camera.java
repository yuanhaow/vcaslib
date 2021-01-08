package algorithms.vcas;

/*
This is an implementation of the Camera object described in the paper
"Constant-Time Snapshots with Applications to Concurrent Data Structures"
Yuanhao Wei, Naama Ben-David, Guy E. Blelloch, Panagiota Fatourou, Eric Ruppert, Yihan Sun
PPoPP 2021

Copyright (C) 2021 Yuanhao Wei

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

import jdk.internal.vm.annotation.Contended;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import main.support.ThreadID;

public class Camera {
  public static final int PADDING = 32;
  public static long[] dummyCounters = new long[ThreadID.MAX_THREADS*PADDING];
  @Contended
  public volatile long timestamp;

  private static final AtomicLongFieldUpdater<Camera> timestampUpdater = AtomicLongFieldUpdater.newUpdater(Camera.class, "timestamp");
  private static final ThreadLocal<Integer> backoffAmount = new ThreadLocal<Integer>() {
      @Override
      protected Integer initialValue() {
          return 1;
      }
  };

  public static Camera camera = new Camera();

  public Camera() {
    timestamp = 0;
  }

  private static void backoff(int amount) {
      if(amount == 0) return;
      int limit = amount;
      int tid = ThreadID.threadID.get();
      for(int i = 0; i < limit; i++)
          dummyCounters[tid*PADDING] += i; 
  }

  public static void set(long ts) {
    camera.timestamp = ts;
  }

  public static long takeSnapshot() {
    // return timestampUpdater.getAndIncrement(camera);
    long ts = camera.timestamp;
    int ba = backoffAmount.get();
    //if(ba != 1) System.out.println(ba);
    backoff(ba);
    if(ts == camera.timestamp) {
      if(timestampUpdater.compareAndSet(camera, ts, ts+1))
        ba /= 2;
      else 
        ba *= 2;
    }
    if(ba < 1) ba = 1;
    if(ba > 512) ba = 512;
    backoffAmount.set(ba);
    return ts;
  }

  public static long getTimestamp() {
    return camera.timestamp;
  }
}
