package com.vandorlabs.redstone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Loaded-member channel policy. Hosts provide membership liveness and synchronous signal batching. */
public final class ChannelNetwork {
    private final Consumer<Runnable> batch;
    public ChannelNetwork(Consumer<Runnable> batch) { this.batch = java.util.Objects.requireNonNull(batch); }
    public void beginTransfer() { transferDepth++; }
    public void endTransfer() {
        if (transferDepth <= 0) throw new IllegalStateException("Unbalanced channel transaction");
        finishTransfer();
    }
    public boolean allPowered(ChannelList channels) {
        if (channels.isEmpty()) return false;
        for (int i = 0; i < channels.size(); i++) if (!powerSources.containsKey(channels.get(i))) return false;
        return true;
    }
    private int transferDepth;
    private final Set<Integer> transferChannels=new java.util.LinkedHashSet<>();
    private final Set<ChannelMember> transferAdded=identitySet();
    private void finishTransfer(){
        if(--transferDepth!=0)return;
        Set<Integer> changed=new java.util.LinkedHashSet<>(transferChannels);Set<ChannelMember> added=identitySet();added.addAll(transferAdded);
        transferChannels.clear();transferAdded.clear();
        settle(changed,true,null);
        batch.accept(()->{for(ChannelMember member:added)if(member.isChannelMemberActive())member.setChannelLevel(anyPowered(subscriptions.get(member)));});
    }
    private final Map<Integer, Set<ChannelMember>> members=new java.util.HashMap<>();
    private final Map<ChannelMember,ChannelList> subscriptions=new IdentityHashMap<>();
    private final Map<Integer,ChannelMember> strongestSources=new java.util.HashMap<>();
    private final Map<Integer,Integer> powerSources=new java.util.HashMap<>();
    private final ChannelLatchBank latchStates=new ChannelLatchBank();

    public void register(ChannelMember member) {
        ChannelList next=member.getRedstoneChannels(),previous=subscriptions.get(member);
        if(next.equals(previous))return;
        Set<Integer> changed=new java.util.LinkedHashSet<>();
        if(previous!=null)for(int i=0;i<previous.size();i++) {
            int channel=previous.get(i);changed.add(channel);
            Set<ChannelMember> set=members.get(channel);
            if(set!=null)set.remove(member);
        }
        if(next.isEmpty())subscriptions.remove(member);else subscriptions.put(member,next);
        boolean latch=isLatch(member);
        for(int i=0;i<next.size();i++) {
            int channel=next.get(i);changed.add(channel);
            members.computeIfAbsent(channel,unused->identitySet()).add(member);
            if(latch && !latchStates.contains(channel))latchStates.initialize(channel,((ChannelLatch)member).latchedLevel(channel));
        }
        settle(changed,true,member);
    }

    public void unregister(ChannelMember member) {
        ChannelList previous=subscriptions.remove(member);
        if(previous==null)return;
        Set<Integer> changed=channels(previous);
        for(int channel:changed) {
            Set<ChannelMember> set=members.get(channel);
            if(set!=null)set.remove(member);
        }
        transferAdded.remove(member);
        settle(changed,true,null);
    }

    public void inputChanged(ChannelMember member) {
        ChannelList channels=subscriptions.get(member);
        if(channels==null)return;
        if(transferDepth>0){transferChannels.addAll(channels(channels));return;}
        Set<ChannelMember> notify=null;
        for(int i=0;i<channels.size();i++) {
            int channel=channels.get(i);Set<ChannelMember> set=members.get(channel);
            if(set==null)continue;
            int before=level(channel);
            if(before!=reconcile(channel,set)) {
                if(notify==null)notify=identitySet();
                notify.addAll(set);
            }
        }
        if(notify!=null) {
            Set<ChannelMember> ready=notify;
            batch.accept(()->{
                for(ChannelMember target:ready)if(subscriptions.containsKey(target)&&target.isChannelMemberActive())target.setChannelLevel(anyPowered(subscriptions.get(target)));
            });
        }
    }

    public void latchChanged(ChannelLatch source,int level) {
        ChannelList channels=subscriptions.get(source);
        if(channels==null)return;
        latchStates.set(channels,level);
        settle(channels(channels),true,null);
    }

    private void settle(Set<Integer> changed,boolean syncLatches,ChannelMember addedOrRemoved) {
        if(transferDepth>0){transferChannels.addAll(changed);if(addedOrRemoved!=null)transferAdded.add(addedOrRemoved);return;}
        batch.accept(()-> {
            if(syncLatches) {
                Set<ChannelMember> latches=identitySet();
                for(int channel:changed) {
                    Set<ChannelMember> set=members.get(channel);boolean hasLatch=false;
                    if(set!=null)for(ChannelMember member:new ArrayList<>(set))if(member.isChannelMemberActive()&&isLatch(member)) {
                        hasLatch=true;latches.add(member);
                    }
                    if(!hasLatch)latchStates.remove(channel);
                }
                // Mirror the complete list once, avoiding intermediate physical handle states.
                for(ChannelMember member:latches) {
                    ChannelList channels=subscriptions.get(member);
                    if(channels==null||!member.isChannelMemberActive())continue;
                    ((ChannelLatch)member).applyLinkedLevels(latchStates.snapshot(channels));
                }
            }
            Set<ChannelMember> notify=identitySet();
            if(addedOrRemoved!=null)notify.add(addedOrRemoved);
            // Settle every affected channel before computing any consumer's OR.
            for(int channel:changed) {
                int before=level(channel);
                Set<ChannelMember> set=members.get(channel);
                if(set==null || set.isEmpty()) {
                    members.remove(channel);powerSources.remove(channel);strongestSources.remove(channel);latchStates.remove(channel);
                } else if(before!=reconcile(channel,set))notify.addAll(set);
            }
            for(ChannelMember member:notify)if((subscriptions.containsKey(member)||member==addedOrRemoved)&&member.isChannelMemberActive())member.setChannelLevel(anyPowered(subscriptions.get(member)));
        });
    }

    public int level(int channel){return powerSources.getOrDefault(channel,0);}
    private int anyPowered(ChannelList channels) {
        int level=0;
        if(channels!=null)for(int i=0;i<channels.size();i++)level=Math.max(level,level(channels.get(i)));
        return level;
    }

    private int reconcile(int channel,Set<ChannelMember> set) {
        ChannelMember previous=strongestSources.get(channel);
        // A still-loaded maximum-strength source proves the maximum without a scan.
        if(previous!=null && set.contains(previous) && previous.isChannelMemberActive() && previous.localSignalLevel(channel)==15){powerSources.put(channel,15);return 15;}
        int level=0;ChannelMember strongest=null;
        for(ChannelMember member:new ArrayList<>(set)) {
            if(!member.isChannelMemberActive())continue;
            int candidate=Math.max(0,Math.min(15,member.localSignalLevel(channel)));
            if(candidate>level){level=candidate;strongest=member;}
            if(level==15)break;
        }
        if(level==0){powerSources.remove(channel);strongestSources.remove(channel);}else{powerSources.put(channel,level);strongestSources.put(channel,strongest);}
        return level;
    }
    private static boolean isLatch(ChannelMember member) {
        return member instanceof ChannelLatch && ((ChannelLatch)member).isChannelLatch();
    }
    private static Set<ChannelMember> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<ChannelMember,Boolean>());
    }
    private static Set<Integer> channels(ChannelList list) {
        Set<Integer> channels=new java.util.LinkedHashSet<>();
        for(int i=0;i<list.size();i++)channels.add(list.get(i));
        return channels;
    }
}
