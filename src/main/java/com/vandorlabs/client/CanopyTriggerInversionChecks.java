package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.canopy.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.items.ProgrammableSettings;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Actual local/channel inputs, configured items, copying and parent-world captured subscriptions. */
final class CanopyTriggerInversionChecks {
    private static final BlockPos BASE=new BlockPos(304,100,80);
    private static final int CHANNEL=29041;
    private static void require(boolean ok,String what){if(!ok)throw new IllegalStateException("canopy trigger inversion: "+what);}
    private static void clear(World world){for(BlockPos pos:BlockPos.getAllInBox(BASE.add(-6,-2,-2),BASE.add(12,8,6)))world.setBlockToAir(pos);}
    private static LargeCanopySettings copy(LargeCanopySettings value){NBTTagCompound tag=new NBTTagCompound();value.write(tag);LargeCanopySettings result=new LargeCanopySettings();result.read(tag);return result;}
    private static final class Source extends net.minecraft.tileentity.TileEntity implements RedstoneChannelMember {
        boolean high;public net.minecraft.tileentity.TileEntity channelTile(){return this;}public int getRedstoneChannel(){return CHANNEL;}public void setRedstoneChannel(int ignored){}public boolean hasLocalRedstoneSignal(){return high;}public void setChannelSignal(boolean ignored){}
        void power(boolean value){high=value;RedstoneChannels.inputChanged(this);}
    }
    static void run(EntityPlayerMP player){
        World world=player.world;Source source=new Source();source.setWorld(world);source.setPos(BASE.west(5));world.getChunkFromBlockCoords(BASE);world.getChunkFromBlockCoords(source.getPos());RedstoneChannels.register(source);
        try{
            for(String kind:new String[]{"canopy","long_canopy","front_canopy_glass","large_front_canopy_glass"}){
                source.power(false);clear(world);BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:"+kind);
                LargeCanopySettings settings=new LargeCanopySettings();settings.openable=true;settings.redstone=true;settings.channels=ChannelList.of(CHANNEL);
                int count=block.opening()?3:1;ItemStack item=new ItemStack(block);NBTTagCompound tag=new NBTTagCompound();settings.write(tag);item.setTagCompound(tag);
                for(int x=0;x<count;x++){BlockPos pos=BASE.east(x);world.setBlockState(pos,block.getDefaultState(),2);block.onBlockPlacedBy(world,pos,block.getDefaultState(),player,item);}
                TileEntityCanopy owner=TileEntityCanopy.at(world,BASE).owner();require(owner.width()==(block.opening()?3:block.width),"full joined fixture "+kind);
                for(boolean on:new boolean[]{true,false}){
                    LargeCanopySettings next=copy(owner.large);next.activateOnPower=on;require(LargeCanopyOwnership.apply(owner,player,next),"apply inversion "+kind);
                    owner.refreshPower();require(owner.targetOpen==!on,"unpowered input polarity "+kind);
                    world.setBlockState(BASE.north(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);owner.refreshPower();require(owner.targetOpen==on,"physical powered input polarity "+kind);
                    world.setBlockToAir(BASE.north());owner.refreshPower();require(owner.targetOpen==!on,"physical falling input polarity "+kind);
                    source.power(true);require(owner.targetOpen==on,"parent channel rising polarity "+kind);source.power(false);require(owner.targetOpen==!on,"parent channel falling polarity "+kind);
                    for(int x=0;x<count;x++)require(TileEntityCanopy.at(world,BASE.east(x)).large.activateOnPower==on,"joined setting propagated "+kind);
                }
                NBTTagCompound saved=owner.writeToNBT(new NBTTagCompound());TileEntityCanopy restored=new TileEntityCanopy();restored.readFromNBT(saved);require(!restored.large.activateOnPower && restored.large.redstone,"tile reload keeps OFF "+kind);
                saved.removeTag("CanopyActivateOnPower");restored.readFromNBT(saved);require(restored.large.activateOnPower,"legacy save defaults ON "+kind);
                ItemStack picked=block.configuredStack(owner);require(picked.hasTagCompound() && !picked.getTagCompound().getBoolean("CanopyActivateOnPower"),"configured drop keeps OFF "+kind);
                NBTTagCompound copied=ProgrammableSettings.capture(world,BASE);require(copied.getInteger(ProgrammableSettings.TRIGGER)==2,"Duplifier captures OFF "+kind);
                ItemStack copiedItem=ProgrammableSettings.applyToItem(new ItemStack(block),copied);require(!copiedItem.isEmpty() && copiedItem.getTagCompound()!=null && !copiedItem.getTagCompound().getBoolean("CanopyActivateOnPower"),"Duplifier item applies OFF "+kind);
                NBTTagCompound onSetting=new NBTTagCompound();onSetting.setInteger(ProgrammableSettings.TRIGGER,1);require(ProgrammableSettings.apply(world,BASE,onSetting,player) && owner.large.activateOnPower,"Duplifier applies ON "+kind);
                require(ProgrammableSettings.apply(world,BASE,copied,player) && !owner.large.activateOnPower,"Duplifier restores OFF "+kind);
                packet(owner.large);captured(world,owner,source,kind);
            }
        }finally{RedstoneChannels.unregister(source);clear(world);}
        System.out.println("[vandorlabs][reprolab] canopy-trigger-inversion PASS Canopy Long-Canopy front3 front5 joined physical-power channel-rising-falling legacy-NBT configured-items Duplifier packet captured-rotated-reload");
    }
    private static void packet(LargeCanopySettings settings){
        io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();try{
            new com.vandorlabs.network.MessageLargeCanopy(BASE,settings).toBytes(bytes);com.vandorlabs.network.MessageLargeCanopy decoded=new com.vandorlabs.network.MessageLargeCanopy();decoded.fromBytes(bytes);
            java.lang.reflect.Field field=decoded.getClass().getDeclaredField("settings");field.setAccessible(true);require(!((LargeCanopySettings)field.get(decoded)).activateOnPower,"settings packet keeps OFF");
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}finally{bytes.release();}
    }
    private static void captured(World world,TileEntityCanopy owner,Source source,String kind){
        List<VehicleStructure.Cell> cells=new ArrayList<>();BlockPos seat=new BlockPos(8,0,0);cells.add(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        Set<BlockPos> footprint=new LinkedHashSet<>();for(int x=0;x<owner.width();x+=owner.block().width)footprint.addAll(TileEntityCanopy.at(world,BASE.east(x)).cells());
        for(BlockPos pos:footprint)cells.add(new VehicleStructure.Cell(pos.subtract(BASE),world.getBlockState(pos),VehicleTileData.translated(world.getTileEntity(pos).writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(BASE))));
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(cells,seat,EnumFacing.NORTH,1));craft.setPosition(304,120,80);craft.rotationYaw=37;
        try{
            craft.signals.tick();TileEntityCanopy tile=(TileEntityCanopy)craft.view.getTileEntity(BlockPos.ORIGIN);require(tile.targetOpen,"captured OFF opens on low "+kind);
            source.power(true);require(!tile.targetOpen,"captured OFF closes on high "+kind);for(int tick=0;tick<16;tick++)VehicleCanopies.tick(craft);require(tile.progress==0,"captured closes completely "+kind);
            NBTTagCompound saved=craft.writeToNBT(new NBTTagCompound());craft.setDead();craft=new EntityGroundVehicle(world);craft.readFromNBT(saved);craft.signals.tick();tile=(TileEntityCanopy)craft.view.getTileEntity(BlockPos.ORIGIN);require(!tile.large.activateOnPower && !tile.targetOpen,"captured save/reload retains inversion "+kind);
            source.power(false);require(tile.targetOpen,"reloaded captured OFF opens on falling channel "+kind);for(int tick=0;tick<16;tick++)VehicleCanopies.tick(craft);require(tile.progress==1,"captured opens completely "+kind);
            for(VehicleStructure.Cell cell:craft.structure.cells)if(cell.state.getBlock() instanceof BlockCanopy)require(!cell.tileData().getBoolean("CanopyActivateOnPower"),"captured snapshot retains constituent OFF "+kind);
        }finally{craft.setDead();source.power(false);}
    }
    static void invertInGui(net.minecraft.client.Minecraft mc)throws java.io.IOException{
        require(mc.currentScreen instanceof GuiLargeCanopy,"actual settings dialog open");GuiLargeCanopy gui=(GuiLargeCanopy)mc.currentScreen;
        for(int i=0;i<3;i++){
            java.util.List<net.minecraft.client.gui.GuiButton> controls=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiScreen.class,gui,"buttonList","field_146292_n");
            net.minecraft.client.gui.GuiButton trigger=controls.stream().filter(button->button.id==7).findFirst().get();
            if(trigger.displayString.equals("Trigger: redstone OFF"))return;
            gui.mouseClicked(trigger.x+trigger.width/2,trigger.y+trigger.height/2,0);
        }
        throw new IllegalStateException("canopy trigger inversion: actual mouse did not cycle to OFF");
    }
    private CanopyTriggerInversionChecks(){}
}
