package com.vandorlabs.client;

/** Optional Java-heap allocation counter for the opt-in benchmark only. */
final class BenchmarkAllocations {
    private static final com.sun.management.ThreadMXBean BEAN = findBean();
    private BenchmarkAllocations() { }
    private static com.sun.management.ThreadMXBean findBean() {
        java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean)) return null;
        com.sun.management.ThreadMXBean allocations=(com.sun.management.ThreadMXBean)bean;
        if (!allocations.isThreadAllocatedMemorySupported()) return null;
        if (!allocations.isThreadAllocatedMemoryEnabled()) allocations.setThreadAllocatedMemoryEnabled(true);
        return allocations;
    }
    static long currentThreadBytes() {
        return BEAN==null ? -1 : BEAN.getThreadAllocatedBytes(Thread.currentThread().getId());
    }
}
