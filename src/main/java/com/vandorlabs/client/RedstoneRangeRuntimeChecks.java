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
        final TileEntity tile=new com.vandorlabs.tiles.TileEntityRedstoneChannel();ChannelList channels;int output,received,calls;boolean active=true;
        Member(WorldServer world,BlockPos pos,int channel){tile.setWorld(world);tile.setPos(pos);channels=ChannelList.of(channel);}
        public TileEntity channelTile(){return tile;}public boolean isChannelMemberActive(){return active;}public int getRedstoneChannel(){return channels.first();}public void setRedstoneChannel(int channel){channels=ChannelList.of(channel);}public ChannelList getRedstoneChannels(){return channels;}public void setRedstoneChannels(ChannelList next){channels=next;}public boolean hasLocalRedstoneSignal(){return output>0;}public int localSignalLevel(int channel){return output;}public void setChannelSignal(boolean on){received=on?15:0;calls++;}public void setChannelLevel(int level){received=level;calls++;}
        void close(){active=false;RedstoneChannels.unregister(this);}
    }
    private static final class Latch extends Member implements RedstoneChannelLatch {
        Latch(WorldServer world,BlockPos pos,int channel){super(world,pos,channel);}public boolean isChannelLatch(){return true;}public boolean latchOn(){return output>0;}public int latchedLevel(int channel){return output;}public void applyLinkedLatch(boolean on){output=on?15:0;}public void applyLinkedLevels(Map<Integer,Integer> values){output=values.getOrDefault(getRedstoneChannel(),0);}
    }
    private static ItemStack module(int tier,ChannelList channels){ItemStack stack=new ItemStack(ModItems.REDSTONE_TRANSMITTER,1,tier);ItemRedstoneTransmitter.configure(stack,channels);return stack;}
    static void run(WorldServer world,EntityPlayerMP actor){
        checks=0;int channel=65281;BlockPos origin=new BlockPos(2100,160,2100);List<Member> cleanup=new ArrayList<>();
        Member source=new Member(world,origin,channel);source.output=11;cleanup.add(source);RedstoneChannels.register(source);
        try{
            NBTTagCompound before=source.tile.writeToNBT(new NBTTagCompound());source.transmittedChannels();source.transmissionRange();require(before.equals(source.tile.writeToNBT(new NBTTagCompound())),"read-only absent module preserves exact previous-release tile NBT");
            Member near=new Member(world,origin.east(),channel);cleanup.add(near);RedstoneChannels.register(near);require(near.received==0,"no item never transmits");
            for(int tier=0;tier<4;tier++){
                int range=ItemRedstoneTransmitter.RANGES[tier],distance=range==0?4096:range;
                Member edge=new Member(world,origin.east(distance),channel),outside=new Member(world,origin.east(distance+1),channel),up=new Member(world,origin.up(distance),channel),diagonal=new Member(world,origin.add(distance,1,0),channel);Collections.addAll(cleanup,edge,outside,up,diagonal);
                for(Member m:new Member[]{edge,outside,up,diagonal})RedstoneChannels.register(m);
                TransmitterSlots.set(source,module(tier,ChannelList.of(channel)));
                require(edge.received==11&&up.received==11,"inclusive horizontal/vertical range boundary tier="+tier);require(outside.received==(range==0?11:0)&&diagonal.received==(range==0?11:0),"spherical distance and infinite tier="+tier);
                ItemStack saved=TransmitterSlots.get(source);require(ItemRedstoneTransmitter.channels(new ItemStack(saved.writeToNBT(new NBTTagCompound()))).contains(channel),"item configuration NBT roundtrip tier="+tier);
                for(Member m:new Member[]{edge,outside,up,diagonal})m.close();
            }
            TransmitterSlots.set(source,ItemStack.EMPTY);require(near.received==0,"removing module withdraws output immediately");
            Latch left=new Latch(world,origin,channel),right=new Latch(world,origin.east(8),channel),remote=new Latch(world,origin.east(40),channel);Collections.addAll(cleanup,left,right,remote);for(Latch latch:new Latch[]{left,right,remote})TransmitterSlots.set(latch,module(0,ChannelList.of(channel)));
            left.output=15;RedstoneChannels.latchChanged(left,true);require(right.output==15&&remote.output==0,"only in-range linked latches mirror ON");right.output=0;RedstoneChannels.latchChanged(right,false);require(left.output==0&&near.received==0,"nearby linked OFF clears original owner instead of retaining stale ON");for(Latch latch:new Latch[]{left,right,remote})latch.close();
            ItemStack original=module(0,ChannelList.of(channel,channel+1)),target=module(2,ChannelList.of(7));InventoryCrafting table=new InventoryCrafting(new Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p){return true;}},3,3);table.setInventorySlotContents(4,original);table.setInventorySlotContents(5,target);RecipeTransmitterCopy recipe=new RecipeTransmitterCopy();ItemStack copied=recipe.getCraftingResult(table);require(ItemRedstoneTransmitter.range(copied)==256&&ItemRedstoneTransmitter.channels(copied).equals(ItemRedstoneTransmitter.channels(original)),"center-source copy keeps target range and copies channels");require(ItemStack.areItemStacksEqual(recipe.getRemainingItems(table).get(4),original),"copy returns exact original source");table.setInventorySlotContents(0,target);require(!recipe.matches(table,world),"copy rejects unrelated crafting items");
            Member handheld=new Member(world,actor.getPosition().east(),channel+2);cleanup.add(handheld);RedstoneChannels.register(handheld);ItemStack manual=module(0,ChannelList.of(channel+2));RedstoneChannels.manualToggle(actor,manual);require(handheld.received==15,"ordinary world use creates an in-range saved source");NBTTagCompound manualSave=ManualRedstoneData.get(world).writeToNBT(new NBTTagCompound());ManualRedstoneData reload=new ManualRedstoneData();reload.readFromNBT(manualSave);require(!reload.entries.isEmpty(),"manual toggle source persists in world saved data");RedstoneChannels.manualToggle(actor,manual);require(handheld.received==0,"second world use switches saved source off");
            TransmitterSlots.set(source,module(0,ChannelList.of(channel)));source.output=0;RedstoneChannels.inputChanged(source);
            for(int i=0;i<2048;i++){Member far=new Member(world,origin.add(10000+i*32,0,10000),channel);cleanup.add(far);RedstoneChannels.register(far);}
            long[] start=RedstoneChannels.diagnostics(world);long tick=System.nanoTime();for(int i=0;i<100;i++){source.output=i%2==0?15:0;RedstoneChannels.inputChanged(source);}long elapsed=System.nanoTime()-tick;long[] end=RedstoneChannels.diagnostics(world);long candidates=end[1]-start[1],deliveries=end[4]-start[4];require(candidates<2000&&deliveries<=200,"finite activation ignores 2048 far same-channel receivers");
            System.out.println("[vandorlabs][reprolab] redstone-range-performance receivers=2049 activations=100 receiverBuckets="+(end[0]-start[0])+" receiverCandidates="+candidates+" callbacks="+deliveries+" totalNanos="+elapsed+" legacyBroadcastCallbacks=204900");
            WirelessRedstoneRuntimeChecks.run(world);
            System.out.println("[vandorlabs][reprolab] redstone-range PASS checks="+checks+" four tiers sphere boundaries noitem linked OFF saved item manual world source copy recipe spatial scalability");
        }finally{for(Member member:cleanup)member.close();}
    }
}
