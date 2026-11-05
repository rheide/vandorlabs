package com.vandorlabs.client;

import com.vandorlabs.redstone.*;
import java.lang.reflect.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Actual private legacy adapters retain original snapshot identity and immutable BlockPos ownership. */
public final class ChannelSourceIndexAdapterChecks {
    private static final class Member implements RedstoneChannelMember {
        boolean active=true;
        public TileEntity channelTile(){return null;}
        public boolean isChannelMemberActive(){return active;}
        public int getRedstoneChannel(){return 1;}
        public void setRedstoneChannel(int channel){}
        public boolean hasLocalRedstoneSignal(){return false;}
        public void setChannelSignal(boolean on){}
    }
    private static int fields(Class<?> type){int count=0;for(Field field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers()))count++;return count;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception{
        String prefix="com.vandorlabs.redstone.RedstoneChannels$Network$";
        Class<?> counters=Class.forName(prefix+"Counters"),source=Class.forName(prefix+"Source"),index=Class.forName(prefix+"Index");
        Constructor<?> cc=counters.getDeclaredConstructor(),sc=source.getDeclaredConstructor(BlockPos.class,int.class,int.class),ic=index.getDeclaredConstructor(counters);
        cc.setAccessible(true);sc.setAccessible(true);ic.setAccessible(true);Object context=cc.newInstance();
        ChannelSourceIndex<RedstoneChannelMember,ChannelSourceIndex.SourceSnapshot> actual=(ChannelSourceIndex<RedstoneChannelMember,ChannelSourceIndex.SourceSnapshot>)ic.newInstance(context);
        Method at=index.getDeclaredMethod("at",BlockPos.class);at.setAccessible(true);
        Member member=new Member();BlockPos.MutableBlockPos position=new BlockPos.MutableBlockPos(-17,64,16);
        ChannelSourceIndex.SourceSnapshot snapshot=(ChannelSourceIndex.SourceSnapshot)sc.newInstance(position,16,8);position.setPos(1000,0,1000);actual.put(member,snapshot);
        require(actual.source(member)==snapshot&&snapshot.x()==-17&&snapshot.y()==64&&snapshot.z()==16,"original immutable snapshot must be retained, without adapter copy");
        require((Integer)at.invoke(actual,new BlockPos(-1,64,16))==8,"inclusive BlockPos adapter range");
        require((Integer)at.invoke(actual,new BlockPos(-1,65,16))==0,"sphere outside");
        member.active=false;require((Integer)at.invoke(actual,new BlockPos(-17,64,16))==0,"legacy liveness predicate");
        require(((ChannelSourceIndex.QueryContext<?>)context).sourceBuckets==243,"all three tier counters shared with network");
        require(actual.maximum()==8,"inactive membership still retains published bank");actual.remove(member);require(actual.isEmpty()&&actual.maximum()==0,"withdrawal and strengths");
        require(fields(source)==3&&fields(index)==0&&fields(counters)==3,"legacy snapshots/index/context must not duplicate extracted state");
        System.out.println("Channel source index adapters PASS immutable-BlockPos same-snapshot identity liveness exact-counters no-duplicate-state");
    }
}
