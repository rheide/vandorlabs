package com.vandorlabs.redstone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SignalUpdateBatchTest {
    private SignalUpdateBatchTest() { }
    public static void main(String[] args) {
        List<String> events=new ArrayList<>();
        Object sameJob=new Object();
        SignalUpdateBatch.apply(() -> {
            events.add("first signal");
            SignalUpdateBatch.afterSignals(sameJob,()->events.add("obsolete job"));
            SignalUpdateBatch.apply(() -> {
                events.add("second signal");
                SignalUpdateBatch.afterSignals(sameJob,()-> {
                    require(!SignalUpdateBatch.isActive(),"settlement retained old batch");
                    events.add("group settled");
                    SignalUpdateBatch.apply(()->SignalUpdateBatch.afterSignals(new Object(),
                            ()->events.add("new batch settled")));
                });
            });
            require(events.size()==2,"derived update ran before all signals");
        });
        require(events.equals(Arrays.asList("first signal","second signal","group settled","new batch settled")),
                "nested batch lost ordering or failed to coalesce work");
        try {
            SignalUpdateBatch.apply(()-> {throw new IllegalArgumentException("test");});
            throw new AssertionError("exception swallowed");
        } catch (IllegalArgumentException expected) {
            require(!SignalUpdateBatch.isActive(),"exception leaked batch state");
        }
        SignalUpdateBatch.apply(()->SignalUpdateBatch.afterSignals(sameJob,()->events.add("clean batch")));
        require(events.get(4).equals("clean batch"),"later batch lost job");
        try {
            SignalUpdateBatch.apply(()-> {
                SignalUpdateBatch.afterSignals(sameJob,()->events.add("partial signals settled"));
                throw new IllegalArgumentException("receiver failed after an earlier signal");
            });
            throw new AssertionError("receiver exception swallowed");
        } catch (IllegalArgumentException expected) {
            require(!SignalUpdateBatch.isActive(),"partial update leaked batch state");
            require(events.get(5).equals("partial signals settled"),
                    "already applied signals did not settle after another receiver failed");
        }
        System.out.println("Signal batch PASS: nested updates, coalescing, synchronous settlement, cleanup");
    }
    private static void require(boolean result,String message) {
        if (!result) throw new AssertionError(message);
    }
}
