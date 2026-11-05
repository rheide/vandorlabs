package com.vandorlabs.neoforge.redstone;

import java.util.*;
import net.minecraft.server.level.ServerLevel;

/** Scoped derived physical jobs after synchronous channel banks settle; not a world-feedback solver. */
public final class NativeSignalUpdateBatch {
    private static final class Pending {
        final Map<NativeChannelMember,Runnable> jobs=new IdentityHashMap<>();
        final ArrayDeque<NativeChannelMember> order=new ArrayDeque<>();
    }
    private static final ThreadLocal<Pending> PENDING=new ThreadLocal<>();
    public static boolean isActive(){return PENDING.get()!=null;}
    public static void afterSignals(NativeChannelMember owner,Runnable action){
        Pending pending=PENDING.get();if(pending==null)throw new IllegalStateException("No active native signal batch");
        if(!pending.jobs.containsKey(owner))pending.order.add(owner);pending.jobs.put(owner,Objects.requireNonNull(action));
    }
    public static void apply(Runnable signals){
        if(isActive()){signals.run();return;}
        Pending pending=new Pending();PENDING.set(pending);Throwable failure=null;
        try{signals.run();}catch(RuntimeException|Error original){failure=original;throw original;}
        finally{
            try{int rounds=0;while(!pending.order.isEmpty()){
                if(++rounds>10000)throw new IllegalStateException("Native physical callbacks did not converge");
                int count=pending.order.size();for(int i=0;i<count&&!pending.order.isEmpty();i++){
                NativeChannelMember owner=pending.order.remove();Runnable action=pending.jobs.remove(owner);if(action==null)continue;
                var tile=owner.channelBlockEntity();if(tile!=null&&tile.getLevel() instanceof ServerLevel level&&NativeRedstoneChannels.open(level)&&owner.isChannelMemberActive())action.run();
                // A physical callback can synchronously settle new banks and replace/add keyed jobs here.
            }}}catch(RuntimeException|Error derived){if(failure!=null)failure.addSuppressed(derived);else throw derived;}
            finally{pending.jobs.clear();pending.order.clear();PENDING.remove();}
        }
    }
    static void worldUnloaded(ServerLevel level){
        Pending pending=PENDING.get();if(pending==null)return;
        pending.order.removeIf(owner->{var tile=owner.channelBlockEntity();if(tile!=null&&tile.getLevel()==level){pending.jobs.remove(owner);return true;}return false;});
    }
    private NativeSignalUpdateBatch(){}
}
