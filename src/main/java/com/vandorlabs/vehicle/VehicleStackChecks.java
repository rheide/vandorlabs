package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.gear.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Complete parked child craft retain their own controls and inventories inside the lower capture. */
public final class VehicleStackChecks {
    private static int checks;
    private static void require(boolean value,String reason){checks++;if(!value)throw new IllegalStateException("Stacked vehicles: "+reason);}
    private static void clear(WorldServer world,BlockPos base){for(BlockPos at:BlockPos.getAllInBox(base.add(-1,-4,-1),base.add(8,9,4))){world.removeTileEntity(at);world.setBlockToAir(at);}}
    private static BlockPos controller(BlockPos deck){return deck.add(2,1,1);}
    private static BlockPos seat(BlockPos deck){return deck.add(1,1,1);}
    private static BlockPos autopilot(BlockPos deck){return deck.add(5,1,1);}
    private static void build(WorldServer world,EntityPlayerMP actor,BlockPos deck,boolean auto,int channel){
        for(int x=0;x<7;x++)for(int z=0;z<3;z++)world.setBlockState(deck.add(x,0,z),Blocks.IRON_BLOCK.getDefaultState(),2);
        for(int x:new int[]{0,3,6}){
            BlockPos root=deck.add(x,-1,0);world.setBlockState(root,GearRegistry.GEAR_EXTENDER.getDefaultState(),2);world.setBlockState(root.down(),GearRegistry.LANDING_WHEEL.getDefaultState(),2);GearTileEntity gear=(GearTileEntity)world.getTileEntity(root);gear.operator(actor);require(gear.rebuild()&&gear.landingDeployed(),"complete native wheel support");
        }
        world.setBlockState(seat(deck),ModBlocks.PILOT_SEAT.getDefaultState(),2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,seat(deck),world.getBlockState(seat(deck)),actor,new ItemStack(ModBlocks.PILOT_SEAT));
        world.setBlockState(controller(deck),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);ModBlocks.VEHICLE_CONTROLLER.onBlockPlacedBy(world,controller(deck),world.getBlockState(controller(deck)),actor,new ItemStack(ModBlocks.VEHICLE_CONTROLLER));
        TileEntityVehicleController control=(TileEntityVehicleController)world.getTileEntity(controller(deck));control.setModeChannels(1,ChannelList.of(channel));control.initializeControls();
        Block thrust=Block.getBlockFromName("vandorlabs:impulse_engine");world.setBlockState(deck.add(0,1,2),thrust.getDefaultState().withProperty(BlockPropulsionLight.FACING,EnumFacing.NORTH),2);
        if(auto){world.setBlockState(autopilot(deck),Block.getBlockFromName("vandorlabs:autopilot").getDefaultState(),2);((TileEntityAutopilot)world.getTileEntity(autopilot(deck))).setRedstoneChannels(ChannelList.of(channel+1));}
        world.setBlockState(deck.add(4,1,1),Blocks.CHEST.getDefaultState(),2);TileEntityChest chest=(TileEntityChest)world.getTileEntity(deck.add(4,1,1));chest.setCustomName("Parked cargo "+channel);chest.setInventorySlotContents(0,new ItemStack(Items.DIAMOND,channel==1201?3:7));
        DeferredTileLoad.flush(world);
    }
    private static VehicleScan scan(WorldServer world,BlockPos seed){VehicleScan scan=new VehicleScan(world,seed);for(int step=0;step<1000&&!scan.step(128);step++){}return scan;}
    public static void run(WorldServer world,EntityPlayerMP actor)throws Exception{
        checks=0;BlockPos base=new BlockPos(880,130,352);double px=actor.posX,py=actor.posY,pz=actor.posZ;boolean creative=actor.capabilities.isCreativeMode;
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getChunkFromChunkCoords((base.getX()>>4)+x,(base.getZ()>>4)+z);
        try{actor.capabilities.isCreativeMode=true;actor.setPosition(base.getX()+2,base.getY()+2,base.getZ()+4);
            for(boolean outerAuto:new boolean[]{false,true}){
                clear(world,base);build(world,actor,base,true,1201);VehicleScan single=scan(world,controller(base));require(single.result!=null&&single.supportCount()==3,"unstacked complete craft retains normal support rules: "+single.error);
                clear(world,base);build(world,actor,base,outerAuto,1201);BlockPos child=base.up(5);build(world,actor,child,true,2201);
                for(int x:new int[]{0,3,6})for(int y=1;y<=2;y++)world.setBlockState(base.add(x,y,0),Blocks.IRON_BLOCK.getDefaultState(),2);
                for(int x=0;x<7;x++)for(int z=0;z<3;z++)world.setBlockState(base.add(x,-3,z),Blocks.STONE.getDefaultState(),2);
                Map<BlockPos,NBTTagCompound> childNBT=new LinkedHashMap<>();for(BlockPos at:BlockPos.getAllInBox(child.add(0,-2,0),child.add(6,2,2))){TileEntity tile=world.getTileEntity(at);if(tile!=null)childNBT.put(at,tile.writeToNBT(new NBTTagCompound()));}
                for(BlockPos selected:new BlockPos[]{controller(base),seat(base)}){
                    VehicleScan scan=scan(world,selected);require(scan.result!=null,"complete parked child captured from lower selected control: "+scan.error);require(scan.supportCount()==3&&scan.result.gearCount==3,"only lowest outer supports count");
                    require(scan.result.controller().pos.equals(controller(base).subtract(scan.origin))&&scan.result.seat.equals(seat(base).subtract(scan.origin)),"child controls never replace requested outer Pilot or Controller");
                    for(Map.Entry<BlockPos,NBTTagCompound> entry:childNBT.entrySet()){VehicleStructure.Cell cell=scan.result.at(entry.getKey().subtract(scan.origin));require(cell!=null&&VehicleTileData.translated(entry.getValue(),BlockPos.ORIGIN.subtract(scan.origin)).equals(cell.tileData()),"every child settings/inventory tile remains exact");}
                    require(scan.result.at(base.down(3).subtract(scan.origin))==null,"terrain below outer supports excluded");
                    VehicleStructure decoded=VehicleStructure.read(scan.result.write());EntityGroundVehicle test=new EntityGroundVehicle(world);test.install(decoded);BlockPos expectedAuto=outerAuto?autopilot(base).subtract(scan.origin):null;TileEntityAutopilot chosen=test.autopilot.controller();require(expectedAuto==null?chosen==null:chosen!=null&&chosen.getPos().equals(expectedAuto),"child Autopilot does not control outer craft when main Autopilot absent/present");
                    if(chosen!=null){test.view.tiles.remove(expectedAuto);test.view.tiles.put(expectedAuto,chosen);test.view.tileRevision++;require(test.autopilot.controller()==chosen,"mutable tile map order cannot promote child Autopilot");}test.setDead();
                    if(selected.equals(controller(base))){EntityGroundVehicle craft=null;try{craft=VehicleTransfer.assemble(world,scan.result,scan.origin,actor);require(craft.structure.controller().pos.equals(controller(base).subtract(scan.origin))&&world.isAirBlock(child.add(4,1,1)),"actual lower conversion removes complete child into one entity");require(((TileEntityChest)craft.view.getTileEntity(child.add(4,1,1).subtract(scan.origin))).getStackInSlot(0).getCount()==7,"child cargo inventory retained exactly once in moving craft");VehicleTransfer.park(world,craft,scan.origin,actor,false);require(world.getTileEntity(child.add(4,1,1)) instanceof TileEntityChest&&((TileEntityChest)world.getTileEntity(child.add(4,1,1))).getStackInSlot(0).getCount()==7,"parking restores child cargo once");}finally{if(craft!=null){craft.signals.close();craft.setDead();world.removeEntityDangerously(craft);}}}
                }
                world.setBlockToAir(controller(child));VehicleScan incomplete=scan(world,controller(base));require(incomplete.result==null,"extra child Pilot without complete child controller refused");
            }
        }finally{clear(world,base);actor.capabilities.isCreativeMode=creative;actor.setPosition(px,py,pz);}
        System.out.println("[vandorlabs][reprolab] stacked-vehicle-boundaries PASS checks="+checks+" complete lower+child actual assemble/park outer-only supports controls inventories terrain normal-unstacked incomplete-child refusal Autopilot none/present stable tile updates");
    }
    private VehicleStackChecks(){}
}
