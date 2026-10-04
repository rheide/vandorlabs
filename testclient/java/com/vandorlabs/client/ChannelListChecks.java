package com.vandorlabs.client;

import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Multi-channel OR, independent latch banks, persistence and bounded parsing. */
final class ChannelListChecks {
    static void run() {
        require(ChannelList.parse("3, 1, 3,0,2").equals(ChannelList.of(1,2,3)),"canonical channel list");
        require(ChannelList.parse("  ").isEmpty() && ChannelList.parse("0").isEmpty(),"empty/zero channels");
        for(String bad:new String[]{"1,",",1","1,,2","-1","+1","2147483648","1 2","abc"})require(ChannelList.parse(bad)==null,"accepted malformed list: "+bad);
        int[] maximum=new int[ChannelList.MAX_CHANNELS];for(int i=0;i<maximum.length;i++)maximum[i]=Integer.MAX_VALUE-i;
        ChannelList full=ChannelList.of(maximum);require(full.equals(ChannelList.parse(full.toString())),"maximum canonical list cannot be reopened");
        int[] copy=full.toArray();copy[0]=0;require(!full.contains(0),"channel list is mutable");
        persistence();packets();copying();orSignals();latches();
        System.out.println("PASS: channel-list parsing, legacy/list NBT, independent latch banks, multi-source OR, edits and unloads");
    }
    private static void packets() {
        BlockPos pos=new BlockPos(1,80,1);
        net.minecraftforge.fml.common.network.simpleimpl.IMessage[] messages={
            new com.vandorlabs.network.MessageRedstoneChannel(pos,0),
            new com.vandorlabs.network.MessageSyncScreenSelector(pos,"engineering_screen",false,0,false,0,TileEntityAnimatedScreenSelector.INPUT_PANELS[0]),
            new com.vandorlabs.network.MessageSpaceDoor(pos,0,0,false,0,0,0,false,true,0,false),
            new com.vandorlabs.network.MessageRampController(pos,1,2,false,true,false,false,net.minecraft.util.EnumFacing.NORTH),
            new com.vandorlabs.network.MessageLandingGear(pos,1,0,16),
            new com.vandorlabs.network.MessageProgrammableTrapdoor(pos,0,0,false,0,0),
            new com.vandorlabs.network.MessageProgrammableLight(pos,0,15,false,0),
            new com.vandorlabs.network.MessageProgrammableTrigger(pos,0,0,0)
        };
        try {
            for(net.minecraftforge.fml.common.network.simpleimpl.IMessage message:messages) {
                Class<?> type=message.getClass();java.lang.reflect.Method getter=type.getMethod("getRedstoneChannels");
                type.getMethod("withChannels",ChannelList.class).invoke(message,ChannelList.of(41,42));
                io.netty.buffer.ByteBuf encoded=io.netty.buffer.Unpooled.buffer();message.toBytes(encoded);
                int legacyLength=encoded.writerIndex()-9;
                for(int mode=0;mode<4;mode++) {
                    net.minecraftforge.fml.common.network.simpleimpl.IMessage decoded=(net.minecraftforge.fml.common.network.simpleimpl.IMessage)type.newInstance();
                    io.netty.buffer.ByteBuf input=encoded.copy(0,mode==0?encoded.writerIndex():legacyLength);
                    if(mode==2)input.writeByte(65);
                    if(mode==3){input.writeByte(1);input.writeInt(-1);}
                    decoded.fromBytes(input);Object actual=getter.invoke(decoded);input.release();
                    require(mode>=2?actual==null:(mode==0?ChannelList.of(41,42):ChannelList.of(41)).equals(actual),"packet list/legacy/rejection differs: "+type+" mode="+mode);
                }
                encoded.release();
            }
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        System.out.println("PASS: eight channel packet families retain scalar compatibility and reject invalid list extensions");
    }
    private static void copying() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        BlockPos from=new BlockPos(0,100,0),to=from.east(3);
        world.setBlockState(from,com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),2);
        world.setBlockState(to,com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),2);
        TileEntityAnimatedScreenSelector source=(TileEntityAnimatedScreenSelector)world.getTileEntity(from);
        TileEntityAnimatedScreenSelector target=(TileEntityAnimatedScreenSelector)world.getTileEntity(to);
        source.setRedstoneChannels(ChannelList.of(3,7,11));target.setRedstoneChannels(ChannelList.of(5,9));
        NBTTagCompound capture=com.vandorlabs.items.ProgrammableSettings.capture(world,from);
        long mask=com.vandorlabs.items.DuplifierApplyOptions.ALL;
        for(int i=0;i<com.vandorlabs.items.DuplifierApplyOptions.OPTIONS.length;i++)if(com.vandorlabs.items.ProgrammableSettings.CHANNEL.equals(com.vandorlabs.items.DuplifierApplyOptions.OPTIONS[i].key))mask&=~(1L<<i);
        com.vandorlabs.items.ProgrammableSettings.apply(world,to,com.vandorlabs.items.DuplifierApplyOptions.selected(capture,mask));
        require(target.getRedstoneChannels().equals(ChannelList.of(5,9)),"excluded channel option replaced list");
        com.vandorlabs.items.ProgrammableSettings.apply(world,to,capture);
        require(target.getRedstoneChannels().equals(source.getRedstoneChannels()),"Duplifier lost additional channels");
        TileEntitySpaceDoor door=new TileEntitySpaceDoor();door.setRedstoneChannels(ChannelList.of(3,7,11));
        TileEntitySpaceDoor restored=new TileEntitySpaceDoor();restored.applyItemSettings(door.itemSettings());
        require(restored.getRedstoneChannels().equals(door.getRedstoneChannels()),"configured door item lost list");
        TileEntityProgrammableTrapdoor hatch=new TileEntityProgrammableTrapdoor();hatch.setRedstoneChannels(ChannelList.of(3,7,11));
        TileEntityProgrammableTrapdoor copy=new TileEntityProgrammableTrapdoor();copy.readFromNBT(hatch.itemSettings());
        require(copy.getRedstoneChannels().equals(hatch.getRedstoneChannels()),"configured trapdoor item lost list");
    }
    private static void persistence() {
        Class<?>[] types={TileEntityAnimatedScreenSelector.class,TileEntityRampController.class,
                TileEntitySlidingDoor.class,TileEntityRedstoneLight.class,TileEntityLandingGear.class,
                TileEntityProgrammableTrapdoor.class,TileEntityRedstoneChannel.class,
                TileEntityProgrammableLight.class,TileEntitySpaceDoor.class};
        try {
            for(int i=0;i<types.length;i++) {
                Class<? extends TileEntity> type=types[i].asSubclass(TileEntity.class);
                if(TileEntity.getKey(type)==null)net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(type,
                        new net.minecraft.util.ResourceLocation("minecraft","channel_list_check_"+i));
                TileEntity tile=type.newInstance();RedstoneChannelMember member=(RedstoneChannelMember)tile;
                member.setRedstoneChannels(ChannelList.of(17,4,29));
                NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());
                require(tag.getInteger("RedstoneChannel")==4,"legacy scalar not retained: "+type);
                TileEntity restored=type.newInstance();restored.readFromNBT(tag);
                require(((RedstoneChannelMember)restored).getRedstoneChannels().equals(ChannelList.of(4,17,29)),"list NBT lost: "+type);
                tag.removeTag("RedstoneChannels");tag.removeTag("LatchedChannels");tag.setInteger("RedstoneChannel",73);
                restored.readFromNBT(tag);
                require(((RedstoneChannelMember)restored).getRedstoneChannels().equals(ChannelList.of(73)),"legacy NBT not migrated: "+type);
                ((RedstoneChannelMember)restored).setRedstoneChannel(8);
                require(((RedstoneChannelMember)restored).getRedstoneChannels().equals(ChannelList.of(8)),"scalar setter retained stale list: "+type);
            }
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();
        ChannelData.write(bytes,ChannelList.of(1,2,3));require(ChannelData.read(bytes,1).equals(ChannelList.of(1,2,3)),"packet list roundtrip");bytes.release();
        bytes=io.netty.buffer.Unpooled.buffer();bytes.writeByte(65);require(ChannelData.read(bytes,1)==null,"oversized packet accepted");bytes.release();
    }
    private static void orSignals() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
        List<Member> members=new ArrayList<>();Random random=new Random(140041);
        for(int i=0;i<48;i++) {
            Member member=new Member(world,i,ChannelList.of(1+i%5,1+(i+1)%5));
            members.add(member);RedstoneChannels.register(member);
        }
        for(int step=0;step<600;step++) {
            Member source=members.get(random.nextInt(members.size()));source.on=random.nextBoolean();
            RedstoneChannels.inputChanged(source);verify(members);
            if(step%5==0) {
                source.setRedstoneChannels(ChannelList.of(random.nextInt(6),random.nextInt(6)));verify(members);
            }
            if(step%13==0) {
                RedstoneChannels.unregister(source);members.remove(source);verify(members);
                RedstoneChannels.register(source);members.add(source);verify(members);
            }
        }
        for(Member member:members)RedstoneChannels.unregister(member);
        Member sender=new Member(world,100,ChannelList.of(1));sender.on=true;RedstoneChannels.register(sender);
        Member bridge=new Member(world,101,ChannelList.of(1,2));RedstoneChannels.register(bridge);
        Member isolated=new Member(world,102,ChannelList.of(2));RedstoneChannels.register(isolated);
        require(bridge.signal && !isolated.signal,"consumer bridged independent channels");
        for(Member member:Arrays.asList(sender,bridge,isolated))RedstoneChannels.unregister(member);
    }
    private static void latches() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
        Latch a=new Latch(world,1,ChannelList.of(1,2)),b=new Latch(world,2,ChannelList.of(2,3));
        Member one=new Member(world,3,ChannelList.of(1)),two=new Member(world,4,ChannelList.of(2)),three=new Member(world,5,ChannelList.of(3));
        for(Member member:Arrays.asList(a,b,one,two,three))RedstoneChannels.register(member);
        RedstoneChannels.latchChanged(a,true);
        require(one.signal && two.signal && !three.signal,"latch did not activate exactly its list");
        require(a.latchOn() && !b.latchOn() && b.active.equals(ChannelList.of(2)),"partial latch state lost or bridged");
        require(RedstoneChannels.allPowered(world,ChannelList.of(1,2)) && !RedstoneChannels.allPowered(world,ChannelList.of(2,3)),"all-channel observation differs");
        RedstoneChannels.latchChanged(b,true);
        require(one.signal && two.signal && three.signal && a.latchOn() && b.latchOn(),"second list failed to activate");
        RedstoneChannels.latchChanged(a,false);
        require(!one.signal && !two.signal && three.signal && !b.latchOn(),"deactivation bridged or erased another channel");
        RedstoneChannels.unregister(a);RedstoneChannels.unregister(b);
        require(!one.signal && !two.signal && !three.signal,"unloaded latch retained power");
        for(Member member:Arrays.asList(one,two,three))RedstoneChannels.unregister(member);
    }
    private static void verify(List<Member> members) {
        for(Member consumer:members) {
            boolean expected=false;
            for(Member source:members)if(source.on)for(int i=0;i<source.channels.size();i++)expected|=consumer.channels.contains(source.channels.get(i));
            require(consumer.signal==expected,"multi-channel OR differs after membership/input change");
        }
    }
    private static class Member extends TileEntity implements RedstoneChannelMember {
        ChannelList channels;boolean on,signal;
        Member(NonRenderingChecks.MemoryWorld world,int index,ChannelList channels){setWorld(world);setPos(new BlockPos(index,100,0));this.channels=channels;}
        public TileEntity channelTile(){return this;}
        public int getRedstoneChannel(){return channels.first();}
        public ChannelList getRedstoneChannels(){return channels;}
        public void setRedstoneChannel(int value){setRedstoneChannels(ChannelList.of(value));}
        public void setRedstoneChannels(ChannelList next){ChannelList old=channels;channels=next;RedstoneChannels.channelChanged(this,old);}
        public boolean hasLocalRedstoneSignal(){return on;}
        public void setChannelSignal(boolean value){signal=value;}
    }
    private static final class Latch extends Member implements RedstoneChannelLatch {
        ChannelList active=ChannelList.EMPTY;
        Latch(NonRenderingChecks.MemoryWorld world,int index,ChannelList channels){super(world,index,channels);}
        public boolean isChannelLatch(){return true;}
        public boolean latchOn(){return active.containsAll(channels);}
        public ChannelList latchedChannels(){return active;}
        public void applyLinkedLatch(boolean on){active=on?channels:ChannelList.EMPTY;}
        public void applyLinkedChannels(ChannelList active){this.active=active;}
        public boolean hasLocalRedstoneSignal(int channel){return active.contains(channel);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
