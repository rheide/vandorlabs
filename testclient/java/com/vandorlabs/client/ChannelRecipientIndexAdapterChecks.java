package com.vandorlabs.client;

import com.vandorlabs.redstone.*;
import java.lang.reflect.*;
import java.util.Set;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Actual private legacy recipient adapter and original immutable BlockPos ownership. */
public final class ChannelRecipientIndexAdapterChecks {
    private static final class Member implements RedstoneChannelMember {
        public TileEntity channelTile(){return null;}public int getRedstoneChannel(){return 1;}public void setRedstoneChannel(int channel){}public boolean hasLocalRedstoneSignal(){return false;}public void setChannelSignal(boolean on){}
        @Override public boolean equals(Object other){return other instanceof Member;}@Override public int hashCode(){return 1;}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception{
        String prefix="com.vandorlabs.redstone.RedstoneChannels$Network$";Class<?> counters=Class.forName(prefix+"Counters"),recipients=Class.forName(prefix+"Recipients");
        Constructor<?> cc=counters.getDeclaredConstructor(),rc=recipients.getDeclaredConstructor(counters);cc.setAccessible(true);rc.setAccessible(true);Object stats=cc.newInstance();
        ChannelRecipientIndex<RedstoneChannelMember,BlockPos> index=(ChannelRecipientIndex<RedstoneChannelMember,BlockPos>)rc.newInstance(stats);
        Member a=new Member(),b=new Member();BlockPos.MutableBlockPos mutable=new BlockPos.MutableBlockPos(-17,64,16);index.put(a,mutable);BlockPos snapshot=index.position(a);mutable.setPos(1000,0,1000);
        require(snapshot!=mutable&&snapshot.equals(new BlockPos(-17,64,16)),"immutable ownership of mutable BlockPos");
        BlockPos immutable=new BlockPos(-17,64,16);index.put(b,immutable);require(index.position(b)==immutable,"immutable input retained directly");
        Set<RedstoneChannelMember> boundary=index.find(new BlockPos(-1,64,16),16);require(boundary.size()==2&&boundary.contains(a)&&boundary.contains(b),"equal-distinct identity members at inclusive edge");
        require(index.find(new BlockPos(-1,65,16),16).isEmpty(),"spherical orthogonal boundary");
        require(index.find(null,0).size()==2&&index.find(null,-1).isEmpty(),"coordinate-free null shortcircuits");
        Field buckets=counters.getDeclaredField("receiverBuckets"),candidates=counters.getDeclaredField("receiverCandidates");buckets.setAccessible(true);candidates.setAccessible(true);
        require(buckets.getLong(stats)==54&&candidates.getLong(stats)==4,"existing network counter owner exact27bucket queries");
        index.put(a,new BlockPos(1000,64,16));index.remove(a);require(index.position(b)==immutable&&index.find(immutable,4).size()==1,"replacement/remove preserves equal other identity");index.remove(b);require(index.isEmpty(),"final removal");
        for(Field field:recipients.getDeclaredFields())require(Modifier.isStatic(field.getModifiers()),"no duplicate legacy recipient fields");
        System.out.println("Channel recipient index adapters PASS mutable-BlockPos immutable-reuse identity boundaries null-shortcircuits exact-network-counters replacement removal no-duplicate-state");
    }
}
