package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.coupler.*;
import com.vandorlabs.gear.*;
import com.vandorlabs.items.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Disposable exact mounting/support/item-placement setup for the carried single-pair regression. */
public final class VehicleArmCouplerFixture {
    public static final int CHANNEL=6154033;
    public static final BlockPos BASE=new BlockPos(1900,130,1900),ARM=new BlockPos(1,1,3),FIRST=new BlockPos(1,1,4),SECOND=new BlockPos(1,1,5),SEAT=new BlockPos(1,2,0);
    private static void need(boolean v,String why){if(!v)throw new IllegalStateException("Arm Coupler setup: "+why);}
    private static void held(EntityPlayerMP actor,ItemStack stack){actor.inventory.currentItem=0;actor.inventory.setInventorySlotContents(0,stack);actor.inventoryContainer.detectAndSendChanges();}
    public static void setup(WorldServer world,EntityPlayerMP actor){
        if(System.getProperty("vandorlabs.reprolab")==null)throw new IllegalStateException("Disposable fixture only");actor.dismountRidingEntity();actor.setGameType(GameType.CREATIVE);actor.capabilities.allowFlying=true;actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.setSneaking(false);actor.setPositionAndUpdate(BASE.getX()+1.5,BASE.getY()+3,BASE.getZ()+1.5);
        for(BlockPos pos:BlockPos.getAllInBox(BASE.add(-8,0,-8),BASE.add(10,10,18)))world.setBlockToAir(pos);for(BlockPos pos:BlockPos.getAllInBox(BASE.add(-8,-1,-8),BASE.add(10,-1,18)))world.setBlockState(pos,Blocks.STONE.getDefaultState(),2);
        Block wheel=null;for(Block block:Block.REGISTRY)if(block instanceof BlockTelescopicLandingGear&&!(block instanceof BlockNonWheeledLandingGear)){wheel=block;break;}need(wheel!=null,"ordinary wheeled support registered");
        for(int section:new int[]{0,6}){
            for(int[] at:new int[][]{{0,0},{2,0},{1,2}}){BlockPos pos=BASE.add(at[0],0,section+at[1]);world.setBlockState(pos,wheel.getDefaultState(),2);((TileEntityLandingGear)world.getTileEntity(pos)).configure(0,0,0,0);}
            for(int x=0;x<3;x++)for(int z=0;z<4;z++)world.setBlockState(BASE.add(x,1,section+z),Blocks.IRON_BLOCK.getDefaultState(),2);
            for(int x=0;x<3;x++)world.setBlockState(BASE.add(x,2,section+1),Block.getBlockFromName("vandorlabs:repulsor_array").getDefaultState().withProperty(BlockPropulsionLight.FACING,EnumFacing.DOWN),2);
        }
        world.setBlockState(BASE.add(0,2,0),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);BlockPos seat=BASE.add(SEAT);world.setBlockState(seat,ModBlocks.PILOT_SEAT.getDefaultState(),2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,seat,world.getBlockState(seat),actor,new ItemStack(ModBlocks.PILOT_SEAT));world.setBlockState(BASE.add(2,2,2),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),2);
        BlockPos arm=BASE.add(ARM);world.setBlockToAir(arm);held(actor,new ItemStack(GearRegistry.GEAR_EXTENDER));need(actor.getHeldItemMainhand().getItem().onItemUse(actor,world,arm.north(),EnumHand.MAIN_HAND,EnumFacing.SOUTH,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary arm item on its retained backing block");GearTileEntity tile=(GearTileEntity)world.getTileEntity(arm);need(tile!=null&&tile.rebuild()&&tile.mountDirection()==EnumFacing.SOUTH,"one-section mounted arm forms");tile.operator(actor);NBTTagCompound settings=tile.settings();settings.setInteger("Trigger",1);settings.setString("Channels",Integer.toString(CHANNEL));settings.setDouble("ActiveExtension",1);settings.setBoolean("Deployed",true);tile.configure(settings);
        Block coupler=Block.getBlockFromName("vandorlabs:square_coupler");held(actor,new ItemStack(coupler));need(actor.getHeldItemMainhand().getItem().onItemUse(actor,world,arm,EnumHand.MAIN_HAND,EnumFacing.SOUTH,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary Coupler item placed on the arm endpoint");held(actor,new ItemStack(coupler));need(actor.getHeldItemMainhand().getItem().onItemUse(actor,world,BASE.add(1,1,6),EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"opposing ordinary Coupler item on the second body");
        for(BlockPos local:new BlockPos[]{FIRST,SECOND}){TileCoupler head=(TileCoupler)world.getTileEntity(BASE.add(local));need(head!=null,"placed head tile");head.configure(ChannelList.EMPTY,true,true);need(world.getBlockState(BASE.add(local)).getValue(BlockCoupler.LOCKED),"real inverted input closes each independent head");}
        ItemStack transmitter=new ItemStack(ModItems.REDSTONE_TRANSMITTER,1,3);ItemRedstoneTransmitter.configure(transmitter,ChannelList.of(CHANNEL));held(actor,transmitter);need(tile.target()&&tile.progress()==1,"unpowered arm starts at construction endpoint");
    }
    public static String status(EntityGroundVehicle craft,BlockPos arm){if(craft==null||craft.structure==null)return "no craft";net.minecraft.tileentity.TileEntity raw=craft.view.getTileEntity(arm);if(!(raw instanceof GearTileEntity))return "no arm at "+arm;GearTileEntity tile=(GearTileEntity)raw;return "target="+tile.target()+" progress="+tile.progress()+" blocked="+tile.motionBlocked()+" payload="+(tile.payload()==null?"none":tile.payload().materialized()?"native":"captured")+" joints="+craft.articulation.jointCount()+" error="+tile.error()+" cacheWaits="+craft.collisionCache.waits()+" entries="+craft.collisionCache.entries()+" generation="+craft.collisionCache.generation()+" bypass="+craft.collisionCache.bypass()+" failure="+craft.collisionCache.failure()+" obstruction="+craft.articulation.lastObstruction+" budgetStops="+craft.articulation.budgetStops;}
    private VehicleArmCouplerFixture(){}
}
