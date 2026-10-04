package com.vandorlabs.client;

import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.redstone.RedstoneChannels;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Full OR oracle after coalesced/reordered inputs, membership edits and world isolation. */
final class ChannelReconciliationChecks {
    static void run() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
        List<Member> members=new ArrayList<>();
        for(int i=0;i<64;i++) {
            Member member=new Member(world,i);members.add(member);RedstoneChannels.register(member);
        }
        Random random=new Random(14004);
        for(int step=0;step<2000;step++) {
            // Neighbors may change more than once before one input event is delivered.
            for(int change=0;change<1+random.nextInt(5);change++) {
                Member source=members.get(random.nextInt(members.size()));source.on=random.nextBoolean();
            }
            Member notified=members.get(random.nextInt(members.size()));
            RedstoneChannels.inputChanged(notified);
            verify(members);
            if(step%7==0) {
                Member moved=members.get(random.nextInt(members.size()));
                RedstoneChannels.unregister(moved);members.remove(moved);verify(members);
                moved.on=random.nextBoolean();RedstoneChannels.register(moved);members.add(moved);verify(members);
            }
        }
        for(Member member:members)member.on=false;
        RedstoneChannels.inputChanged(members.get(0));verify(members);
        // Find a source, then deliver unrelated repeated neighbor notifications.
        Member source=members.get(31);source.on=true;RedstoneChannels.inputChanged(source);
        for(Member member:members)member.reads=0;
        for(int repeat=0;repeat<256;repeat++)RedstoneChannels.inputChanged(members.get(repeat%members.size()));
        int reads=0;for(Member member:members)reads+=member.reads;
        System.out.println("Channel steady powered input reads: "+reads+" for 256 events across 64 members");
        NonRenderingChecks.MemoryWorld otherWorld=new NonRenderingChecks.MemoryWorld(false);
        Member isolated=new Member(otherWorld,0);RedstoneChannels.register(isolated);
        require(!isolated.signal,"channel crossed dimensions/worlds");
        RedstoneChannels.unregister(source);members.remove(source);verify(members);
        for(Member member:members)RedstoneChannels.unregister(member);
        RedstoneChannels.unregister(isolated);
        System.out.println("PASS: channel reconciliation matches full OR across 2000 coalesced input sequences, removal/rejoin and separate worlds");
    }
    private static void verify(List<Member> members) {
        boolean powered=false;for(Member member:members)powered|=member.on;
        for(Member member:members)require(member.signal==powered,"channel retained a stale source or missed a live source");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class Member extends TileEntity implements RedstoneChannelMember {
        boolean on,signal;int reads;
        Member(NonRenderingChecks.MemoryWorld world,int index){setWorld(world);setPos(new BlockPos(index,100,0));}
        public TileEntity channelTile(){return this;}
        public int getRedstoneChannel(){return 14004;}
        public void setRedstoneChannel(int value){throw new UnsupportedOperationException();}
        public boolean hasLocalRedstoneSignal(){reads++;return on;}
        public void setChannelSignal(boolean value){signal=value;}
    }
}
