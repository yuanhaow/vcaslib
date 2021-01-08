
package main.support;
import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;

// Source: https://cruftex.net/2017/03/28/The-6-Memory-Metrics-You-Should-Track-in-Your-Java-Benchmarks.html

public class MemoryStats {
  public static long getGcCount() {
    long sum = 0;
    for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
      long count = b.getCollectionCount();
      if (count != -1) { sum +=  count; }
    }
    return sum;
  }

  public static long getCurrentlyUsedMemory() {
    return
      ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
  }

  public static long getReallyUsedMemory() {
    long before = getGcCount();
    System.gc();
    while (getGcCount() == before);
    return getCurrentlyUsedMemory();
  }

  public static long getSettledUsedMemory() {
    long m;
    long m2 = getReallyUsedMemory();
    do {
      try {
        Thread.sleep(567);
      } catch (InterruptedException e) { 
        System.out.println("Inturrupted while sleeping");
        System.exit(1); 
      }
      
      m = m2;
      m2 = getReallyUsedMemory();
    } while (m2 < getReallyUsedMemory());
    return m;
  }
}
