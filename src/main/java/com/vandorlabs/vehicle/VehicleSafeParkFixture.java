package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

/** One source assembly shared by player and process-termination parking fixtures. */
public final class VehicleSafeParkFixture {
    private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("Safe Park fixture: "+why);}
    @FunctionalInterface public interface SourceSetup {void place(WorldServer world,EntityPlayerMP player,BlockPos base)throws Exception;}
    public static EntityGroundVehicle create(WorldServer world,EntityPlayerMP player,BlockPos base)throws Exception {return create(world,player,base,null);}
    public static EntityGroundVehicle create(WorldServer world,EntityPlayerMP player,BlockPos base,SourceSetup setup)throws Exception {
        if(System.getProperty("vandorlabs.reprolab")==null)throw new IllegalStateException("Disposable fixture only");
        player.dismountRidingEntity();player.setGameType(GameType.CREATIVE);player.inventory.currentItem=0;player.inventory.setInventorySlotContents(0,new ItemStack(ModItems.CONFIGURIZER));player.inventoryContainer.detectAndSendChanges();player.setSneaking(false);
        player.capabilities.allowFlying=true;player.capabilities.isFlying=true;player.sendPlayerAbilities();
        for(BlockPos p:BlockPos.getAllInBox(base.add(-20,0,-20),base.add(25,15,20)))world.setBlockToAir(p);
        for(BlockPos p:BlockPos.getAllInBox(base.add(-20,-1,-20),base.add(25,-1,20)))world.setBlockState(p,Blocks.STONE.getDefaultState(),2);
        Block gear=null;for(Block b:Block.REGISTRY)if(b instanceof BlockTelescopicLandingGear&&!(b instanceof BlockNonWheeledLandingGear)){gear=b;break;}need(gear!=null,"gear registered");
        for(int x:new int[]{0,2,4}){BlockPos p=base.east(x);world.setBlockState(p,gear.getDefaultState(),2);((TileEntityLandingGear)world.getTileEntity(p)).configure(0,0,0,0);}
        for(int x=0;x<=4;x++)world.setBlockState(base.add(x,1,0),Blocks.IRON_BLOCK.getDefaultState(),2);
        for(int x:new int[]{0,2,4})world.setBlockState(base.add(x,1,1),Block.getBlockFromName("vandorlabs:antigravity_plate").getDefaultState().withProperty(BlockPropulsionLight.FACING,EnumFacing.DOWN),2);
        world.setBlockState(base.add(2,2,1),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),2);
        BlockPos seat=base.up(2);world.setBlockState(seat,ModBlocks.PILOT_SEAT.getDefaultState(),2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,seat,world.getBlockState(seat),player,new ItemStack(ModBlocks.PILOT_SEAT));((TileEntityPilotSeat)world.getTileEntity(seat)).setTaxiMode(false);
        world.setBlockState(base.add(2,2,0),Block.getBlockFromName("vandorlabs:vehicle_controller").getDefaultState(),2);
        BlockPos chest=base.add(4,2,0);world.setBlockState(chest,Blocks.CHEST.getDefaultState(),2);((TileEntityChest)world.getTileEntity(chest)).setCustomName("Safe Park cargo");((TileEntityChest)world.getTileEntity(chest)).setInventorySlotContents(7,new ItemStack(Items.DIAMOND,11));
        if(setup!=null)setup.place(world,player,base);
        VehicleScan scan=new VehicleScan(world,seat);for(int n=0;n<1000&&!scan.step(256);n++){}need(scan.result!=null,"scan: "+scan.error);
        EntityGroundVehicle original=VehicleTransfer.assemble(world,scan.result,scan.origin,player);original.rotationYaw=37;need(original.setHover(true),"real Hover admission with lift and propulsion");original.setPosition(original.posX,original.posY+7,original.posZ);original.hoverTarget=original.posY;
        return original;
    }
    private VehicleSafeParkFixture(){}
}
