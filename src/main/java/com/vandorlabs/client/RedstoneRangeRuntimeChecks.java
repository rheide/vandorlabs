package com.vandorlabs.client;

import com.vandorlabs.redstone.*;
import com.vandorlabs.items.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Spatial bus algorithm checks plus real module NBT/crafting and saved handheld integration. */
final class RedstoneRangeRuntimeChecks {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("redstone range: "+message);}
    private static class Member implements RedstoneChannelMember {
        final TileEntity tile=new com.vandorlabs.tiles.TileEntityRedstoneChannel();ChannelList channels,outputs=ChannelList.EMPTY;int range=16;int output,received,calls;boolean active=true;
        Member(WorldServer world,BlockPos pos,int channel){tile.setWorld(world);tile.setPos(pos);channels=ChannelList.of(channel);}
        public TileEntity channelTile(){return tile;}public boolean isChannelMemberActive(){return active;}public int getRedstoneChannel(){return channels.first();}public void setRedstoneChannel(int channel){channels=ChannelList.of(channel);}public ChannelList getRedstoneChannels(){return channels;}public void setRedstoneChannels(ChannelList next){channels=next;}public boolean hasLocalRedstoneSignal(){return output>0;}public int localSignalLevel(int channel){return output;}public void setChannelSignal(boolean on){received=on?15:0;calls++;}public void setChannelLevel(int level){received=level;calls++;}
        public ChannelList transmittedChannels(){return outputs;}public int transmissionRange(){return range;}
        void close(){active=false;RedstoneChannels.unregister(this);}
    }
    private static final class Latch extends Member implements RedstoneChannelLatch {
        Latch(WorldServer world,BlockPos pos,int channel){super(world,pos,channel);}public boolean isChannelLatch(){return true;}public boolean latchOn(){return output>0;}public int latchedLevel(int channel){return output;}public void applyLinkedLatch(boolean on){output=on?15:0;}public void applyLinkedLevels(Map<Integer,Integer> values){output=values.getOrDefault(getRedstoneChannel(),0);}
    }
    private static ItemStack module(int tier,ChannelList channels){ItemStack stack=new ItemStack(ModItems.REDSTONE_TRANSMITTER,1,tier);ItemRedstoneTransmitter.configure(stack,channels);return stack;}
    private static void configure(Member member,ItemStack radio){
        member.outputs=radio.isEmpty()?ChannelList.EMPTY:ItemRedstoneTransmitter.channels(radio);
        member.range=radio.isEmpty()?16:ItemRedstoneTransmitter.range(radio)==0?256:ItemRedstoneTransmitter.range(radio);
        RedstoneChannels.channelChanged(member,member.getRedstoneChannels());RedstoneChannels.inputChanged(member);
    }
    /** Only the existing source-index matrix, with no recipes, vehicles or unrelated gameplay. */
    static void sourceIndexFocus(WorldServer world){
        checks=0;BlockPos origin=new BlockPos(2100,160,2100);List<Member> cleanup=new ArrayList<>();
        try{
            int channel=65281;Member source=new Member(world,origin,channel);source.output=11;cleanup.add(source);RedstoneChannels.register(source);
            for(int range:new int[]{4,8,16,32,64,128,256}){
                source.outputs=ChannelList.of(channel);source.range=range;RedstoneChannels.inputChanged(source);
                require(RedstoneChannels.level(world,origin.east(range),channel)==11,"inclusive source range="+range);
                require(RedstoneChannels.level(world,origin.up(range),channel)==11,"vertical source range="+range);
                require(RedstoneChannels.level(world,origin.east(range+1),channel)==0,"outside source range="+range);
                require(RedstoneChannels.level(world,origin.add(range,1,0),channel)==0,"spherical source range="+range);
            }
            source.close();denseSources(world,origin,channel+3,cleanup);
            System.out.println("[vandorlabs][reprolab] channel-source-index-range PASS checks="+checks);
        }finally{for(Member member:cleanup)member.close();}
    }
    private static void denseSources(WorldServer world,BlockPos origin,int denseChannel,List<Member> cleanup){
            for(int i=0;i<1024;i++){Member analog=new Member(world,origin.add(i%5,0,(i/5)%5),denseChannel){public ChannelList getRedstoneChannels(){return ChannelList.EMPTY;}};analog.output=8;cleanup.add(analog);configure(analog,module(0,ChannelList.of(denseChannel)));}
            Member distantMaximum=new Member(world,origin.east(1024),denseChannel){public ChannelList getRedstoneChannels(){return ChannelList.EMPTY;}};distantMaximum.output=15;cleanup.add(distantMaximum);configure(distantMaximum,module(0,ChannelList.of(denseChannel)));
            long[] denseBefore=RedstoneChannels.diagnostics(world);long denseStart=System.nanoTime();for(int i=0;i<2048;i++)require(RedstoneChannels.level(world,origin.add(i%7,0,(i/7)%7),denseChannel)==8,"dense analog strongest exact receiver query");long denseNanos=System.nanoTime()-denseStart;long[] denseAfter=RedstoneChannels.diagnostics(world);long denseCandidates=denseAfter[3]-denseBefore[3];require(denseCandidates==2048,"strength banks inspect one candidate for equal overlapping analog sources");System.out.println("[vandorlabs][reprolab] redstone-range-dense-analog sources=1024 distantMaxSource=15 queries=2048 sourceCandidates="+denseCandidates+" previousLinearCandidates=2097152 totalNanos="+denseNanos);
    }
    static void run(WorldServer world,EntityPlayerMP actor)throws Exception{
        checks=0;int channel=65281;BlockPos origin=new BlockPos(2100,160,2100);List<Member> cleanup=new ArrayList<>();
        Member source=new Member(world,origin,channel);source.output=11;cleanup.add(source);RedstoneChannels.register(source);
        try{
            NBTTagCompound before=source.tile.writeToNBT(new NBTTagCompound());source.transmittedChannels();source.transmissionRange();require(before.equals(source.tile.writeToNBT(new NBTTagCompound())),"read-only member query preserves exact previous-release tile NBT");
            Member near=new Member(world,origin.east(),channel);cleanup.add(near);RedstoneChannels.register(near);require(near.received==0,"test member has no implicit source");
            for(int tier=0;tier<4;tier++){
                int range=tier==3?256:ItemRedstoneTransmitter.RANGES[tier],distance=range;
                Member edge=new Member(world,origin.east(distance),channel),outside=new Member(world,origin.east(distance+1),channel),up=new Member(world,origin.up(distance),channel),diagonal=new Member(world,origin.add(distance,1,0),channel);Collections.addAll(cleanup,edge,outside,up,diagonal);
                for(Member m:new Member[]{edge,outside,up,diagonal})RedstoneChannels.register(m);
                configure(source,module(tier,ChannelList.of(channel)));
                require(edge.received==11&&up.received==11,"inclusive horizontal/vertical range boundary tier="+tier);require(outside.received==(range==0?11:0)&&diagonal.received==(range==0?11:0),"spherical distance and infinite tier="+tier);
                ItemStack saved=module(tier,ChannelList.of(channel));require(ItemRedstoneTransmitter.channels(new ItemStack(saved.writeToNBT(new NBTTagCompound()))).contains(channel),"item configuration NBT roundtrip tier="+tier);
                for(Member m:new Member[]{edge,outside,up,diagonal})m.close();
            }
            configure(source,ItemStack.EMPTY);require(near.received==0,"clearing configured output channels withdraws output immediately");
            Latch left=new Latch(world,origin,channel),right=new Latch(world,origin.east(8),channel),remote=new Latch(world,origin.east(40),channel);Collections.addAll(cleanup,left,right,remote);for(Latch latch:new Latch[]{left,right,remote})configure(latch,module(0,ChannelList.of(channel)));
            left.output=15;RedstoneChannels.latchChanged(left,true);require(right.output==15&&remote.output==0,"only in-range linked latches mirror ON");right.output=0;RedstoneChannels.latchChanged(right,false);require(left.output==0&&near.received==0,"nearby linked OFF clears original owner instead of retaining stale ON");for(Latch latch:new Latch[]{left,right,remote})latch.close();
            ItemStack original=module(0,ChannelList.of(channel,channel+1)),target=module(2,ChannelList.of(7));InventoryCrafting table=new InventoryCrafting(new Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p){return true;}},3,3);table.setInventorySlotContents(4,original);table.setInventorySlotContents(5,target);RecipeTransmitterCopy recipe=new RecipeTransmitterCopy();ItemStack copied=recipe.getCraftingResult(table);require(ItemRedstoneTransmitter.range(copied)==256&&ItemRedstoneTransmitter.channels(copied).equals(ItemRedstoneTransmitter.channels(original)),"center-source copy keeps target range and copies channels");require(ItemStack.areItemStacksEqual(recipe.getRemainingItems(table).get(4),original),"copy returns exact original source");table.setInventorySlotContents(0,target);require(!recipe.matches(table,world),"copy rejects unrelated crafting items");
            Member handheld=new Member(world,actor.getPosition().east(),channel+2);cleanup.add(handheld);RedstoneChannels.register(handheld);ItemStack manual=module(0,ChannelList.of(channel+2));RedstoneChannels.manualToggle(actor,manual);require(handheld.received==15,"ordinary world use creates an in-range saved source");NBTTagCompound manualSave=ManualRedstoneData.get(world).writeToNBT(new NBTTagCompound());ManualRedstoneData reload=new ManualRedstoneData();reload.readFromNBT(manualSave);require(!reload.entries.isEmpty(),"manual toggle source persists in world saved data");RedstoneChannels.manualToggle(actor,manual);require(handheld.received==0,"second world use switches saved source off");
            ManualRedstoneData manualData=ManualRedstoneData.get(world);
            NBTTagCompound retainedManual=manualData.writeToNBT(new NBTTagCompound());
            int manualChannel=65290;
            List<Member> manualReceivers=new ArrayList<>();
            try{
                for(int i=0;i<256;i++){
                    BlockPos at=actor.getPosition().add(1000+i*40,0,1000);
                    manualData.apply(at,ChannelList.of(manualChannel),16,true);
                    Member receiver=new Member(world,at,manualChannel);manualReceivers.add(receiver);RedstoneChannels.register(receiver);
                }
                ItemStack trigger=module(0,ChannelList.of(manualChannel));
                RedstoneChannels.manualToggle(actor,trigger);
                for(Member receiver:manualReceivers)require(receiver.received==15,"saved remote source initialized");
                long[] manualBefore=RedstoneChannels.diagnostics(world);
                RedstoneChannels.manualToggle(actor,trigger);
                long[] manualAfter=RedstoneChannels.diagnostics(world);
                long visited=manualAfter[0]-manualBefore[0],queried=manualAfter[2]-manualBefore[2];
                for(Member receiver:manualReceivers)require(receiver.received==15,"local toggle preserved remote source");
                require(visited<100&&queried<100,"local manual toggle must not reindex 256 unchanged remote sources");
                System.out.println("[vandorlabs][reprolab] manual-source-performance sources=256 receiverBuckets="+visited+" sourceBuckets="+queried);
            }finally{
                for(Member receiver:manualReceivers)receiver.close();
                manualData.readFromNBT(retainedManual);
                ItemStack trigger=module(0,ChannelList.of(manualChannel));
                RedstoneChannels.manualToggle(actor,trigger);RedstoneChannels.manualToggle(actor,trigger);
            }
            com.vandorlabs.tiles.TileEntityPilotSeat seatData=new com.vandorlabs.tiles.TileEntityPilotSeat();seatData.setPos(BlockPos.ORIGIN);
            com.vandorlabs.vehicle.VehicleStructure controllerStructure=new com.vandorlabs.vehicle.VehicleStructure(Collections.singletonList(new com.vandorlabs.vehicle.VehicleStructure.Cell(BlockPos.ORIGIN,com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.getDefaultState(),seatData.writeToNBT(new NBTTagCompound()))),BlockPos.ORIGIN,net.minecraft.util.EnumFacing.NORTH,1);
            com.vandorlabs.vehicle.EntityGroundVehicle controlled=new com.vandorlabs.vehicle.EntityGroundVehicle(world);
            Member modeSource=new Member(world,origin,64900);cleanup.add(modeSource);
            try{
                controlled.install(controllerStructure);controlled.setPosition(origin.getX(),origin.getY(),origin.getZ());
                com.vandorlabs.tiles.TileEntityPilotSeat carriedSeat=(com.vandorlabs.tiles.TileEntityPilotSeat)controlled.view.getTileEntity(BlockPos.ORIGIN);
                configure(modeSource,module(0,ChannelList.of(64900)));
                carriedSeat.setModeChannels(2,ChannelList.of(64900));carriedSeat.setVehicleChannels(ChannelList.of(64900),world);
                modeSource.output=15;RedstoneChannels.inputChanged(modeSource);
                require(carriedSeat.pollMode(world)==2&&carriedSeat.poll(world)==1,"carried controller detects finite source at its physical position");
                controlled.setPosition(origin.getX()+100,origin.getY(),origin.getZ());
                require(carriedSeat.pollMode(world)==-1&&carriedSeat.poll(world)==0,"moving beyond finite range withdraws carried mode signal");
                modeSource.tile.setPos(BlockPos.ORIGIN);RedstoneChannels.positionChanged(modeSource);
                require(carriedSeat.pollMode(world)==-1&&carriedSeat.poll(world)==-1,"source at snapshot-local origin does not reach distant craft");
                modeSource.tile.setPos(origin);RedstoneChannels.positionChanged(modeSource);controlled.setPosition(origin.getX(),origin.getY(),origin.getZ());controlled.rotationYaw=90;
                require(carriedSeat.pollMode(world)==2&&carriedSeat.poll(world)==1,"rotated craft reentry produces physical channel edges");
            }finally{modeSource.close();controlled.signals.close();controlled.setDead();}
            configure(source,module(0,ChannelList.of(channel)));source.output=0;RedstoneChannels.inputChanged(source);
            for(int i=0;i<2048;i++){Member far=new Member(world,origin.add(10000+i*32,0,10000),channel);cleanup.add(far);RedstoneChannels.register(far);}
            long[] start=RedstoneChannels.diagnostics(world);long tick=System.nanoTime();for(int i=0;i<100;i++){source.output=i%2==0?15:0;RedstoneChannels.inputChanged(source);}long elapsed=System.nanoTime()-tick;long[] end=RedstoneChannels.diagnostics(world);long candidates=end[1]-start[1],deliveries=end[4]-start[4];require(candidates<2000&&deliveries<=200,"finite activation ignores 2048 far same-channel receivers");
            System.out.println("[vandorlabs][reprolab] redstone-range-performance receivers=2049 activations=100 receiverBuckets="+(end[0]-start[0])+" receiverCandidates="+candidates+" callbacks="+deliveries+" totalNanos="+elapsed+" legacyBroadcastCallbacks=204900");
            net.minecraft.block.Block lever=net.minecraft.block.Block.getBlockFromName("vandorlabs:industrial_power_lever");
            com.vandorlabs.tiles.TileEntityRedstoneChannel configured=new com.vandorlabs.tiles.TileEntityRedstoneChannel();configured.setPos(BlockPos.ORIGIN);
            TransmitterSlots.configure(configured,ChannelList.of(channel),64,true);
            com.vandorlabs.vehicle.VehicleStructure materialSnapshot=com.vandorlabs.vehicle.VehicleStructure.display(Collections.singletonList(new com.vandorlabs.vehicle.VehicleStructure.Cell(BlockPos.ORIGIN,lever.getDefaultState(),configured.writeToNBT(new NBTTagCompound()))));
            List<com.vandorlabs.copier.CopyMaterials.Need> needs=com.vandorlabs.copier.CopyMaterials.forSnapshot(materialSnapshot,new com.vandorlabs.vehicle.VehicleWorld(world,materialSnapshot),false);
            require(needs.size()==1&&needs.get(0).item.getItem()==net.minecraft.item.Item.getItemFromBlock(lever),"Survival Copy requires only the configured block, no transmitter inventory");
            ItemStack configuredHost=new ItemStack(lever);configuredHost.setTagInfo("BlockEntityTag",configured.writeToNBT(new NBTTagCompound()));
            require(com.vandorlabs.copier.CopyMaterials.satisfies(needs.get(0),configuredHost),"built-in channel/range settings do not make a block an inventory carrier");
            require(com.vandorlabs.copier.CopyMaterials.forSnapshot(materialSnapshot,new com.vandorlabs.vehicle.VehicleWorld(world,materialSnapshot),true).isEmpty(),"Creative Copy retains free-copy behavior");
            denseSources(world,origin,channel+3,cleanup);
            com.vandorlabs.copier.CopyRuntimeChecks.transmitterOwnershipFocus(world,actor);
            WirelessRedstoneRuntimeChecks.run(world);
            System.out.println("[vandorlabs][reprolab] redstone-range PASS checks="+checks+" finite ranges sphere boundaries built-in-settings linked OFF saved item manual world source copy recipe spatial scalability");
        }finally{for(Member member:cleanup)member.close();}
    }
}
