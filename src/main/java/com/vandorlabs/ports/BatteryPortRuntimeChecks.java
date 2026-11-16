package com.vandorlabs.ports;

import com.vandorlabs.blocks.BlockBattery;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.energy.*;

/** The changed Battery path only: previous save policy, configured faces and one resource owner. */
public final class BatteryPortRuntimeChecks {
    private static int checks;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("Battery port: "+why);}
    private static TileEntityBattery put(WorldServer world,BlockBattery block,BlockPos at,EnumFacing face,NBTTagCompound data){
        world.getChunkFromBlockCoords(at);world.removeTileEntity(at);world.setBlockState(at,block.getDefaultState().withProperty(BlockBattery.FACING,face),2);TileEntityBattery tile=(TileEntityBattery)world.getTileEntity(at);
        if(data==null)tile.place(face);else tile.readFromNBT(data);tile.onLoad();DeferredTileLoad.flush(world);return tile;
    }
    private static NBTTagCompound legacy(BlockPos at){NBTTagCompound data=new NBTTagCompound();data.setString("id","vandorlabs:battery");data.setInteger("x",at.getX());data.setInteger("y",at.getY());data.setInteger("z",at.getZ());data.setInteger("Energy",43210);data.setBoolean("BlockShape",false);data.setInteger("HousingTexture",1);NBTTagCompound caps=new NBTTagCompound(),opaque=new NBTTagCompound();opaque.setByteArray("opaque",new byte[]{7,0,-1,22});caps.setTag("foreign:unavailable",opaque);data.setTag("ForgeCaps",caps);data.setString("UnknownOwnField","retained");return data;}
    public static void run(WorldServer world,EntityPlayerMP actor){
        BlockBattery block=(BlockBattery)Block.getBlockFromName("vandorlabs:battery_block");BlockPos at=new BlockPos(938,112,48);
        try{
            for(EnumFacing face:EnumFacing.values()){
                NBTTagCompound old=legacy(at);TileEntityBattery tile=put(world,block,at,face,old);
                need(!tile.inert()&&tile.stored()==43210&&!tile.isBlockShape()&&tile.getHousingTexture()==1,"old charge, shape and casing load on "+face);
                need(tile.settings().face(face.getIndex())==ResourcePortSettings.Face.INPUT&&tile.settings().face(face.getOpposite().getIndex())==ResourcePortSettings.Face.OUTPUT&&tile.settings().throughput==1000,"old metadata-derived front/rear/rate "+face);
                IEnergyStorage input=tile.getCapability(CapabilityEnergy.ENERGY,face),output=tile.getCapability(CapabilityEnergy.ENERGY,face.getOpposite());
                need(input.receiveEnergy(5000,true)==1000&&output.extractEnergy(5000,true)==1000&&tile.stored()==43210,"old adapter simulation retains1000 rate and contents");
                NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());need(old.equals(saved),"previous-release own fields and opaque caps remain byte-equivalent before configuration");need(!saved.hasKey(ResourcePortData.FACES)&&old.getCompoundTag("ForgeCaps").equals(saved.getCompoundTag("ForgeCaps"))&&saved.getString("UnknownOwnField").equals("retained"),"opaque old capability/unknown fields preserved");
                NBTTagCompound expectedPolicy=tile.configuration();
                TileEntityBattery copy=new TileEntityBattery();copy.setWorld(world);copy.setPos(at);copy.readFromNBT(saved);
                need(!copy.inert()&&copy.stored()==43210&&copy.inputSide()==EnumFacing.NORTH&&world.getTileEntity(at)==tile,"detached NBT decode retains charge without borrowing another owner's physical facing");
                need(copy.writeToNBT(new NBTTagCompound()).equals(saved),"detached legacy snapshot retains raw omitted policy fields");
                // Legacy input/output faces live in block metadata, not tile NBT. Bind them
                // through the real installed owner and its deferred, identity-checked callback.
                world.removeTileEntity(at);world.setTileEntity(at,copy);copy.onLoad();DeferredTileLoad.flush(world);
                need(!copy.inert()&&copy.stored()==43210&&copy.configuration().equals(expectedPolicy),"installed legacy persistence roundtrip after deferred metadata binding");
                need(copy.writeToNBT(new NBTTagCompound()).equals(saved),"installed legacy roundtrip does not invent saved policy fields");
            }
            TileEntityBattery tile=put(world,block,at,EnumFacing.UP,null);need(tile.settings().throughput==0&&!tile.settings().pullInputs,"new battery uses port defaults");
            IEnergyStorage input=tile.getCapability(CapabilityEnergy.ENERGY,EnumFacing.UP);need(input.getMaxEnergyStored()==100000&&input.receiveEnergy(100001,false)==100000&&input.receiveEnergy(1,false)==0,"one100k owner, no legacy receive cap for new battery");
            ResourcePortSettings policy=ResourcePortSettings.placed(EnumFacing.UP.getIndex()).withTransfer(7,false,ResourcePortSettings.OnTexture.CONNECTED).withHousing(1);tile.configure(policy,0,ChannelList.EMPTY);
            IEnergyStorage output=tile.getCapability(CapabilityEnergy.ENERGY,EnumFacing.DOWN);need(output.extractEnergy(100000,false)==7&&output.extractEnergy(100000,false)==0&&tile.stored()==99993,"configured shared output budget, no repeated withdrawal");
            NBTTagCompound snapshot=tile.writeToNBT(new NBTTagCompound());need(snapshot.getInteger("Energy")==99993&&!snapshot.hasKey("PortEnergy"),"canonical Energy key, no duplicate port buffer");
            NBTTagCompound publicData=tile.getUpdateTag();need(publicData.getInteger("Energy")==99993&&!publicData.hasKey("ForgeCaps")&&!publicData.hasKey("UnknownOwnField"),"public charge display excludes private opaque data");
            for(int corruption=0;corruption<3;corruption++){
                NBTTagCompound bad=snapshot.copy();if(corruption==0)bad.setInteger("Energy",100001);else if(corruption==1)bad.setIntArray(ResourcePortData.FACES,new int[]{1,2});else bad.setInteger("PortEnergy",1);
                TileEntityBattery failed=new TileEntityBattery();failed.readFromNBT(bad);need(failed.inert()&&failed.getCapability(CapabilityEnergy.ENERGY,null)==null&&failed.getRedstoneChannels().isEmpty(),"malformed owner inert, no capability/output");need(failed.writeToNBT(new NBTTagCompound()).equals(bad),"malformed original snapshot retained exactly");
            }
            inertCopy(world);
            actor.setGameType(net.minecraft.world.GameType.SURVIVAL);actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));need(actor.interactionManager.tryHarvestBlock(at)&&world.isAirBlock(at),"actual Battery harvest");
            java.util.List<net.minecraft.entity.item.EntityItem> drops=world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,new net.minecraft.util.math.AxisAlignedBB(at).grow(2));ItemStack carrier=ItemStack.EMPTY;int owners=0;for(net.minecraft.entity.item.EntityItem item:drops)if(!item.isDead&&item.getItem().getItem()==net.minecraft.item.Item.getItemFromBlock(block)){owners+=item.getItem().getCount();carrier=item.getItem().copy();item.setDead();}need(owners==1&&carrier.getSubCompound("BlockEntityTag").getInteger("Energy")==99993,"one configured Battery resource item");
            world.setBlockToAir(at);world.setBlockState(at.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),2);actor.setHeldItem(EnumHand.MAIN_HAND,carrier);actor.setPosition(at.getX()+.5,at.getY(),at.getZ()-3);need(carrier.getItem().onItemUse(actor,world,at.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)==EnumActionResult.SUCCESS&&actor.getHeldItemMainhand().isEmpty(),"actual Battery carrier placement consumes once");TileEntityBattery placed=(TileEntityBattery)world.getTileEntity(at);need(placed.stored()==99993&&placed.configuration().equals(tile.configuration()),"placement retains resources and configured six-face policy");
            System.out.println("[vandorlabs][reprolab] battery-port-runtime PASS checks="+checks+" old-six-facing1000/100k/new-unlimited/opaque-capabilities/inert-original/shared-budget/mine-placement");
        }finally{world.removeTileEntity(at);world.setBlockToAir(at);world.setBlockToAir(at.down());}
    }
    public static void inertCopy(WorldServer world){
        BlockBattery block=(BlockBattery)Block.getBlockFromName("vandorlabs:battery_block");
        NBTTagCompound inertData=new NBTTagCompound();ResourcePortData.write(inertData,ResourcePortSettings.placed(EnumFacing.UP.getIndex()),0,ChannelList.EMPTY);inertData.setString("id","vandorlabs:battery");inertData.setInteger("Energy",99993);inertData.setBoolean("BlockShape",true);inertData.setIntArray(ResourcePortData.FACES,new int[]{1,2});
        com.vandorlabs.vehicle.VehicleStructure held=com.vandorlabs.vehicle.VehicleStructure.display(java.util.Collections.singletonList(new com.vandorlabs.vehicle.VehicleStructure.Cell(BlockPos.ORIGIN,block.getDefaultState(),inertData)));
        com.vandorlabs.vehicle.VehicleWorld view=new com.vandorlabs.vehicle.VehicleWorld(world,held);
        need(view.getTileEntity(BlockPos.ORIGIN) instanceof TileEntityBattery&&((TileEntityBattery)view.getTileEntity(BlockPos.ORIGIN)).inert(),"unreadable saved policy retains inert Battery in copy view");
        java.util.List<com.vandorlabs.copier.CopyMaterials.Need> materials=com.vandorlabs.copier.CopyMaterials.forSnapshot(held,view,false);
        need(materials.size()==1&&materials.get(0).exact&&materials.get(0).count==1,"inert positive charge requires one exact resource carrier");
        need(!com.vandorlabs.copier.CopyMaterials.satisfies(materials.get(0),new ItemStack(block))&&com.vandorlabs.copier.CopyMaterials.satisfies(materials.get(0),materials.get(0).item.copy()),"empty block cannot pay for retained inert charge");
        need(materials.get(0).item.getSubCompound("BlockEntityTag").getInteger("Energy")==99993,"exact inert carrier retains the original charge");
        System.out.println("[vandorlabs][reprolab] battery-inert-copy PASS positive-original-energy exact-carrier bare-material-refusal");
    }
    private BatteryPortRuntimeChecks(){}
}
