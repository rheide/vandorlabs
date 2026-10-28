package com.vandorlabs.plate;

import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

public final class PlateRuntimeChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Pressure plate: "+message);}
    public static void run(WorldServer world,EntityPlayerMP player,BlockPos pos){
        PlateBlock block=(PlateBlock)Block.getBlockFromName("vandorlabs:programmable_pressure_plate");EntityItem item=null;
        try{
            world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),2);world.setBlockState(pos,block.getDefaultState(),3);DeferredTileLoad.flush(world);
            PlateTile tile=(PlateTile)world.getTileEntity(pos);require(tile!=null,"registered live tile");ChannelList channels=ChannelList.parse("65421,65422");tile.configure(ScreenHousingTextures.DEFAULT_TRAPDOOR,1,channels);RedstoneFixtureTransmitters.equip(tile,channels);
            for(boolean pressed:new boolean[]{false,true}){
                IBlockState state=block.getDefaultState().withProperty(PlateBlock.POWER,pressed?15:0),vanilla=Blocks.WOODEN_PRESSURE_PLATE.getDefaultState().withProperty(net.minecraft.block.BlockPressurePlate.POWERED,pressed);
                require(block.getBoundingBox(state,world,pos).equals(Blocks.WOODEN_PRESSURE_PLATE.getBoundingBox(vanilla,world,pos)),"exact vanilla travel and footprint "+pressed);require(block.getCollisionBoundingBox(state,world,pos)==null,"no solid collision cube");
                for(EnumFacing face:EnumFacing.values()){require(block.getWeakPower(state,world,pos,face)==Blocks.WOODEN_PRESSURE_PLATE.getWeakPower(vanilla,world,pos,face),"vanilla weak power");require(block.getStrongPower(state,world,pos,face)==Blocks.WOODEN_PRESSURE_PLATE.getStrongPower(vanilla,world,pos,face),"vanilla support conduction");}
            }
            item=new EntityItem(world,pos.getX()+.5,pos.getY()+.05,pos.getZ()+.5,new ItemStack(Items.IRON_INGOT));world.spawnEntity(item);block.onEntityCollidedWithBlock(world,pos,world.getBlockState(pos),item);
            require(world.getBlockState(pos).getValue(PlateBlock.POWER)>0,"dropped item presses native plate");require(RedstoneChannels.level(world,65421)==15&&RedstoneChannels.level(world,65422)==15,"pressed channel bank emits fifteen");require(!tile.isChannelLatch(),"pressure is an independent timed source");
            NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());PlateTile reloaded=new PlateTile();reloaded.readFromNBT(saved);require(reloaded.texture()==ScreenHousingTextures.DEFAULT_TRAPDOOR&&reloaded.getRedstoneChannels().equals(channels)&&reloaded.mode()==1,"saved artwork/channel/mode roundtrip");
            item.setPosition(pos.getX()+4,pos.getY()+.05,pos.getZ()+.5);block.updateTick(world,pos,world.getBlockState(pos),world.rand);require(world.getBlockState(pos).getValue(PlateBlock.POWER)==0&&RedstoneChannels.level(world,65421)==0,"native release clears channel bank");
            tile.configure(ScreenHousingTextures.DEFAULT_TRAPDOOR,2,channels);require(RedstoneChannels.level(world,65421)==15,"released channel mode inverted");tile.configure(ScreenHousingTextures.DEFAULT_TRAPDOOR,0,channels);require(RedstoneChannels.level(world,65421)==0,"disabled channels clear output");
            require(block.tickRate(world)==Blocks.WOODEN_PRESSURE_PLATE.tickRate(world),"native release interval");require(new PlateContainer(tile).canInteractWith(player)==com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,pos),"dialog permission and reach gate");
            io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();new PlateMessage(8,ScreenHousingTextures.DEFAULT_TRAPDOOR,2,2,channels.toString()).toBytes(bytes);new PlateMessage().fromBytes(bytes);require(!bytes.isReadable(),"bounded settings packet roundtrip");bytes.release();
        }finally{if(item!=null)item.setDead();world.setBlockToAir(pos);world.setBlockToAir(pos.down());}
        sensitivityMatrix(world,pos);
        carried(world,pos);
        System.out.println("[vandorlabs][reprolab] pressure-plate PASS vanilla bounds/timing/entity press/release physical analog channelbank modes saved trapdoor material authorization packet");
    }
    private static void carried(WorldServer world,BlockPos at){
        PlateBlock block=(PlateBlock)Block.getBlockFromName("vandorlabs:programmable_pressure_plate");BlockPos local=new BlockPos(1,0,0);int cases=0;
        for(int sensitivity=0;sensitivity<3;sensitivity++)for(boolean hover:new boolean[]{false,true})for(float yaw:new float[]{0,37,90,180,270}){
            PlateTile source=new PlateTile();source.setPos(local);source.configure(ScreenHousingTextures.DEFAULT_TRAPDOOR,1,sensitivity,ChannelList.of(65431));RedstoneFixtureTransmitters.equip(source,ChannelList.of(65431));int expected=sensitivity==0?15:1;
            com.vandorlabs.vehicle.VehicleStructure snapshot=new com.vandorlabs.vehicle.VehicleStructure(java.util.Arrays.asList(new com.vandorlabs.vehicle.VehicleStructure.Cell(BlockPos.ORIGIN,com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.getDefaultState(),null),new com.vandorlabs.vehicle.VehicleStructure.Cell(local,block.getDefaultState(),source.writeToNBT(new NBTTagCompound()))),BlockPos.ORIGIN,EnumFacing.NORTH,3);
            com.vandorlabs.vehicle.EntityGroundVehicle craft=new com.vandorlabs.vehicle.EntityGroundVehicle(world);EntityItem item=null;
            try{
                craft.install(snapshot);craft.hover=hover;craft.view.hover=hover;craft.rotationYaw=yaw;craft.setPosition(at.getX(),at.getY()+2,at.getZ());craft.signals.open();
                com.vandorlabs.vehicle.VehiclePose pose=new com.vandorlabs.vehicle.VehiclePose(craft.posX,craft.posY,craft.posZ,yaw);net.minecraft.util.math.Vec3d point=pose.world(new net.minecraft.util.math.Vec3d(1.5,.02,.5),1,.5);
                item=new EntityItem(world,point.x,point.y,point.z,new ItemStack(Items.IRON_INGOT));require(world.spawnEntity(item),"carried entity fixture spawn");craft.signals.tick();
                require(craft.view.getBlockState(local).getValue(PlateBlock.POWER)==expected&&RedstoneChannels.level(world,65431)==expected,"carried actual pose presses bank taxi/hover="+hover+" yaw="+yaw);
                craft.signals.persist();PlateTile saved=new PlateTile();saved.readFromNBT(craft.structure.at(local).tileData());require(saved.getOutputLevel()==expected&&saved.sensitivity()==sensitivity,"carried press persists into immutable tile snapshot");
                item.setPosition(point.x+8,point.y,point.z+8);long now=world.getTotalWorldTime();PlateVehicles.tick(craft,now+(sensitivity==0?19:9));require(craft.view.getBlockState(local).getValue(PlateBlock.POWER)==expected,"carried native release delay");PlateVehicles.tick(craft,now+(sensitivity==0?20:10));require(craft.view.getBlockState(local).getValue(PlateBlock.POWER)==0&&RedstoneChannels.level(world,65431)==0,"carried release clears physical state and channel");
                ((PlateTile)craft.view.getTileEntity(local)).configure(ScreenHousingTextures.DEFAULT_TRAPDOOR,2,ChannelList.of(65431));craft.signals.tick();require(RedstoneChannels.level(world,65431)==15,"carried released-mode source published through parent bridge");cases++;
            }finally{if(item!=null)item.setDead();craft.signals.close();craft.setDead();}
            require(RedstoneChannels.level(world,65431)==0,"carried plate bridge unregisters on removal");
        }
        System.out.println("[vandorlabs][reprolab] pressure-plate-carried PASS cases="+cases+" taxi/hover arbitrary yaw parent entities physical state bank levels saved snapshots removal");
    }
    private static final class NativeWood extends net.minecraft.block.BlockPressurePlate {
        NativeWood(){super(net.minecraft.block.material.Material.WOOD,Sensitivity.EVERYTHING);}
        int measured(WorldServer world,BlockPos pos){return super.computeRedstoneStrength(world,pos);}
    }
    private static final class NativeWeighted extends net.minecraft.block.BlockPressurePlateWeighted {
        NativeWeighted(int maximum){super(net.minecraft.block.material.Material.IRON,maximum);}
        int measured(WorldServer world,BlockPos pos){return super.computeRedstoneStrength(world,pos);}
    }
    private static void sensitivityMatrix(WorldServer world,BlockPos pos){
        PlateBlock block=(PlateBlock)Block.getBlockFromName("vandorlabs:programmable_pressure_plate");NativeWood wood=new NativeWood();NativeWeighted gold=new NativeWeighted(15),iron=new NativeWeighted(150);java.util.List<EntityItem> spawned=new java.util.ArrayList<>();int cases=0;
        world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),2);world.setBlockState(pos,block.getDefaultState(),2);DeferredTileLoad.flush(world);PlateTile tile=(PlateTile)world.getTileEntity(pos);
        try{for(int sensitivity=0;sensitivity<3;sensitivity++){
            for(int count:new int[]{0,1,2,10,15,16,149,150,151}){
                for(EntityItem item:spawned){item.setPosition(pos.getX()+8,pos.getY(),pos.getZ()+8);item.setDead();}spawned.clear();
                for(int i=0;i<count;i++){EntityItem item=new EntityItem(world,pos.getX()+.5,pos.getY()+.02,pos.getZ()+.5,new ItemStack(Items.IRON_INGOT,64));require(world.spawnEntity(item),"weighted entity fixture");spawned.add(item);}
                tile.configure(ScreenHousingTextures.DEFAULT_TRAPDOOR,1,sensitivity,ChannelList.of(65423));block.resample(world,pos);
                int reference=sensitivity==0?wood.measured(world,pos):sensitivity==1?gold.measured(world,pos):iron.measured(world,pos),actual=world.getBlockState(pos).getValue(PlateBlock.POWER);
                require(actual==reference&&tile.getOutputLevel()==reference&&RedstoneChannels.level(world,65423)==reference,"actual vanilla Wood/Gold/Iron count="+count+" sensitivity="+sensitivity+" expected="+reference+" actual="+actual);
                net.minecraft.block.Block vanilla=sensitivity==0?Blocks.WOODEN_PRESSURE_PLATE:sensitivity==1?Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE:Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE;IBlockState state=world.getBlockState(pos),nativeState=sensitivity==0?vanilla.getDefaultState().withProperty(net.minecraft.block.BlockPressurePlate.POWERED,actual>0):vanilla.getDefaultState().withProperty(net.minecraft.block.BlockPressurePlateWeighted.POWER,actual);
                for(EnumFacing side:EnumFacing.values()){require(block.getWeakPower(state,world,pos,side)==vanilla.getWeakPower(nativeState,world,pos,side),"native weighted physical weak power");require(block.getStrongPower(state,world,pos,side)==vanilla.getStrongPower(nativeState,world,pos,side),"native weighted strong power");}
                require(block.releaseTicks(world,pos)==vanilla.tickRate(world),"native sensitivity release timing");PlateTile saved=new PlateTile();saved.readFromNBT(tile.writeToNBT(new NBTTagCompound()));require(saved.sensitivity()==sensitivity&&saved.getOutputLevel()==actual,"saved analog source level");cases++;
            }
        }
        for(EntityItem item:spawned){item.setPosition(pos.getX()+8,pos.getY(),pos.getZ()+8);item.setDead();}spawned.clear();
        EntityItem exempt=new EntityItem(world,pos.getX()+.5,pos.getY()+.02,pos.getZ()+.5,new ItemStack(Items.IRON_INGOT)){@Override public boolean doesEntityNotTriggerPressurePlate(){return true;}};world.spawnEntity(exempt);spawned.add(exempt);
        for(int sensitivity=0;sensitivity<3;sensitivity++){tile.configure(ScreenHousingTextures.DEFAULT_TRAPDOOR,1,sensitivity,ChannelList.of(65423));int reference=sensitivity==0?wood.measured(world,pos):sensitivity==1?gold.measured(world,pos):iron.measured(world,pos);require(tile.getOutputLevel()==reference&&reference==(sensitivity==0?0:1),"wood filters trigger-exempt entity; weighted native plates count it");}
        }finally{for(EntityItem item:spawned){item.setPosition(pos.getX()+8,pos.getY(),pos.getZ()+8);item.setDead();}world.setBlockToAir(pos);world.setBlockToAir(pos.down());}
        System.out.println("[vandorlabs][reprolab] pressure-plate-sensitivity PASS vanilla-comparisons="+cases+" 0/1/2/10/15/16/149/150/151 entities stack-count-independent exactfloatceil filteredwood weightedexempt timing analogNBT");
    }
    private PlateRuntimeChecks(){}
}
