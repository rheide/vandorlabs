package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.TileEntityMatterInfuser;
import net.minecraft.block.Block;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Exercise real solar exposure, furnace inventory, fuel accounting and persistence. */
final class MatterInfuserRuntimeChecks {
    static void run(World world){
        BlockPos pos=new BlockPos(140,230,140);long time=world.getWorldTime();boolean rain=world.getWorldInfo().isRaining();
        Block block=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_matter_infuser"));
        try{
            world.setWorldTime(6000);world.getWorldInfo().setRaining(false);world.calculateInitialSkylight();world.setBlockState(pos,block.getDefaultState());
            TileEntityMatterInfuser tile=(TileEntityMatterInfuser)world.getTileEntity(pos);
            tile.setInventorySlotContents(0,new ItemStack(Items.IRON_INGOT,2));tile.setInventorySlotContents(1,new ItemStack(Blocks.REDSTONE_BLOCK));
            world.setBlockState(pos.up(),Blocks.STONE.getDefaultState());tile.update();require(tile.getField(0)==0 && tile.getField(2)==0,"roof prevents processing and fuel use");
            world.setBlockToAir(pos.up());require(tile.hasSunlight(),"exposed lens receives sunlight");
            for(int i=0;i<100;i++)tile.update();require(tile.getField(0)==200 && tile.getField(2)==100,"redstone block lasts a log's 300 ticks");
            world.setWorldTime(18000);world.calculateInitialSkylight();tile.update();require(tile.getField(0)==200 && tile.getField(2)==100,"night pauses fuel and progress");
            world.setWorldTime(6000);world.calculateInitialSkylight();for(int i=0;i<100;i++)tile.update();
            require(tile.getStackInSlot(2).getItem()==ModItems.PROGRAMMABLE_MATTER_INGOT && tile.getStackInSlot(2).getCount()==1,"200 tick output");
            require(tile.getStackInSlot(0).getCount()==1 && tile.getStackInSlot(1).isEmpty(),"input and catalyst consumption");
            NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());TileEntityMatterInfuser copy=new TileEntityMatterInfuser();copy.readFromNBT(tag);
            require(copy.getField(0)==100 && copy.getStackInSlot(2).getCount()==1,"inventory and fuel persist");
            require(tile.isItemValidForSlot(1,new ItemStack(Items.REDSTONE)) && !tile.isItemValidForSlot(1,new ItemStack(Items.COAL)) && !tile.isItemValidForSlot(0,new ItemStack(Items.GOLD_INGOT)),"automation filters");
            tile.setInventorySlotContents(2,new ItemStack(ModItems.PROGRAMMABLE_MATTER_INGOT,64));tile.update();require(tile.getField(0)==100,"full output does not consume fuel");
            tile.setInventorySlotContents(0,new ItemStack(ModItems.BATTERY_MODULE));tile.setInventorySlotContents(2,ItemStack.EMPTY);
            tile.setInventorySlotContents(1,new ItemStack(Blocks.REDSTONE_BLOCK));tile.setField(0,0);
            ItemStack battery=tile.getStackInSlot(0);
            net.minecraftforge.energy.IEnergyStorage energy=battery.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,null);
            require(energy!=null&&energy.getMaxEnergyStored()==10000&&energy.receiveEnergy(123,true)==123&&energy.getEnergyStored()==0,"battery capability simulation shares sole item owner");
            for(int i=0;i<73;i++)tile.update();require(energy.getEnergyStored()==3650,"infuser charges fifty RF per sunny catalyst tick");
            NBTTagCompound charging=tile.writeToNBT(new NBTTagCompound());copy=new TileEntityMatterInfuser();copy.readFromNBT(charging);
            require(com.vandorlabs.rack.ModuleBatteryData.energy(copy.getStackInSlot(0))==3650,"partial battery charge persists in infuser");
            world.setBlockState(pos.up(),Blocks.STONE.getDefaultState());tile.update();require(energy.getEnergyStored()==3650,"covered infuser pauses charge");world.setBlockToAir(pos.up());
            for(int i=73;i<201;i++)tile.update();battery=tile.getStackInSlot(2);
            require(tile.getStackInSlot(0).isEmpty()&&com.vandorlabs.rack.ModuleBatteryData.energy(battery)==10000,"full battery moves into output without duplication");
            com.vandorlabs.tiles.TileEntityModuleRack rack=new com.vandorlabs.tiles.TileEntityModuleRack();
            require(rack.insertModule(0,battery,false)&&battery.isEmpty()&&rack.moduleKind(0)==3,"battery has one physical rack owner");
            require(rack.extractBatteryEnergy(1250,true)==1250&&rack.batteryEnergy()==10000,"rack extraction simulation");
            require(rack.extractBatteryEnergy(1250,false)==1250&&rack.batteryEnergy()==8750,"rack spends physical module charge");
            NBTTagCompound rackData=rack.writeToNBT(new NBTTagCompound());com.vandorlabs.tiles.TileEntityModuleRack reloadedRack=new com.vandorlabs.tiles.TileEntityModuleRack();reloadedRack.readFromNBT(rackData);
            require(!reloadedRack.inert()&&reloadedRack.batteryEnergy()==8750,"rack battery reload");
            battery=reloadedRack.removeModule(0);require(com.vandorlabs.rack.ModuleBatteryData.energy(battery)==8750&&reloadedRack.batteryEnergy()==0&&reloadedRack.removeModule(0).isEmpty(),"removal preserves charge and clears exactly one owner");
            NBTTagCompound broken=rackData.copy();broken.getTagList("Items",10).getCompoundTagAt(0).getCompoundTag("tag").setInteger("BatteryEnergy",10001);
            reloadedRack.readFromNBT(broken);require(reloadedRack.inert()&&reloadedRack.drainModules().isEmpty()&&reloadedRack.writeToNBT(new NBTTagCompound()).equals(broken),"malformed battery retains inert original rack owner");
            int on=com.vandorlabs.tiles.ScreenHousingTextures.DEFAULT_STORAGE,off=0;
            com.vandorlabs.tiles.TileEntityProgrammableLight light=new com.vandorlabs.tiles.TileEntityProgrammableLight();light.setFaceTexture(on);light.setOffFaceTexture(off);light.setOn(false);
            require(light.activeFaceTexture().equals(com.vandorlabs.tiles.ScreenHousingTextures.texture(off)),"explicit OFF texture");
            com.vandorlabs.tiles.TileEntityProgrammableLight restored=new com.vandorlabs.tiles.TileEntityProgrammableLight();restored.readFromNBT(light.writeToNBT(new NBTTagCompound()));require(restored.getOffFaceTexture()==off,"OFF texture persists");
            restored.setOn(true);require(restored.activeFaceTexture().equals(com.vandorlabs.tiles.ScreenHousingTextures.texture(on,true)),"ON texture remains independent");
            System.out.println("[vandorlabs][reprolab] matter-infuser PASS sunlight fuel processing automation persistence battery-capability/charging/rack/reload/extraction/inert light-off-texture");
        }finally{world.setBlockToAir(pos.up());world.setBlockToAir(pos);world.setWorldTime(time);world.getWorldInfo().setRaining(rain);world.calculateInitialSkylight();}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
