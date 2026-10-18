package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

final class NonWheeledGearRuntimeChecks {
    static void run(WorldServer world,EntityPlayerMP player)throws Exception{
        BlockPos pos=new BlockPos(300,200,160);
        for(String name:new String[]{"ski_landing_gear","landing_strut","angled_landing_strut"}){
            BlockNonWheeledLandingGear block=(BlockNonWheeledLandingGear)Block.getBlockFromName("vandorlabs:"+name);
            require(block.getCreativeTabToDisplayOn()==null,"retired gear hidden from creative catalog");
            for(int size:new int[]{0,4})for(EnumFacing facing:EnumFacing.HORIZONTALS){
                world.setBlockState(pos,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing));
                try{
                    TileEntityLandingGear tile=(TileEntityLandingGear)world.getTileEntity(pos);require(tile.configure(0,0,16,size),"configure "+name+size+facing);
                    require(block.setExtended(world,pos,true),"extend footprint "+name);tile.progress=tile.previous=1;
                    double bottom=Double.POSITIVE_INFINITY;for(AxisAlignedBB box:block.vehicleBoxes(world,pos))bottom=Math.min(bottom,box.minY);
                    require(Math.abs(bottom-(pos.getY()-1))<.001,"extended support plane");
                    TileEntityLandingGear copy=(TileEntityLandingGear)TileEntity.create(world,tile.writeToNBT(new NBTTagCompound()));require(copy.getSize()==size && copy.getExtensionPixels()==16 && copy.progress==1,"size and extension persist");
                    ItemStack drop=block.getPickBlock(world.getBlockState(pos),null,world,pos,player);require(drop.getSubCompound("BlockEntityTag").getInteger("GearSize")==size,"configured item size");
                }finally{world.setBlockToAir(pos);}
            }
            roundTrip(world,player,block);
        }
        System.out.println("[vandorlabs][reprolab] non-wheeled-gear PASS three families both sizes four facings persistence scan hover-only block-round-trip");
    }
    private static void roundTrip(WorldServer world,EntityPlayerMP player,BlockNonWheeledLandingGear gear)throws Exception{
        BlockPos base=new BlockPos(300,101,120),seat=base.add(1,2,1);EntityGroundVehicle craft=null;Vec3d before=player.getPositionVector();
        try{
            for(int x=-2;x<=5;x++)for(int z=-2;z<=5;z++)world.setBlockState(base.add(x,-2,z),Blocks.STONE.getDefaultState());
            for(int x=0;x<3;x++)for(int z=0;z<3;z++)world.setBlockState(base.add(x,1,z),Blocks.STONE.getDefaultState());
            for(BlockPos p:new BlockPos[]{base,base.east(2),base.south(2)}){
                world.setBlockState(p,gear.getDefaultState());TileEntityLandingGear tile=(TileEntityLandingGear)world.getTileEntity(p);tile.configure(0,0,16,0);require(gear.setExtended(world,p,true),"fixture extended gear");tile.progress=tile.previous=1;
            }
            if(gear.ski){world.setBlockState(base.up(),gear.getDefaultState());require(gear.connectedRoot(world,base.up()).equals(base),"vertical ski root");require(!((TileEntityLandingGear)world.getTileEntity(base.up())).isRoot(),"vertical ski extension shared");}
            world.setBlockState(seat,com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.getDefaultState());
            world.setBlockState(seat.up(),com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.UPPER,true));
            world.setBlockState(seat.east(),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState());world.setBlockState(seat.west(),ModBlocks.VEHICLE_CONTROLLER.getDefaultState());
            for(int x=0;x<3;x++)world.setBlockState(base.add(x,2,0),Block.getBlockFromName("vandorlabs:antigravity_plate").getDefaultState());
            VehicleScan scan=new VehicleScan(world,seat);for(int i=0;i<200 && !scan.step(128);i++);
            require(scan.error==null && scan.result!=null,"vehicle discovery "+scan.error);require(scan.result.gearCount==3 && scan.result.hasNonWheeledGear,"support detection");require(!VehicleTaxiMode.enabled(scan.result),"Taxi disabled");
            player.setPositionAndUpdate(seat.getX()+.5,seat.getY()+1,seat.getZ()+.5);
            craft=VehicleTransfer.assemble(world,scan.result,scan.origin,player);require(craft.hover && !craft.setHover(false),"direct Hover assembly and Taxi rejection");
            VehicleTransfer.park(world,craft,scan.origin,player);craft=null;
            require(world.getBlockState(seat).getBlock()==ModBlocks.PILOT_SEAT && world.getBlockState(base).getBlock()==gear,"direct Hover to blocks");
            require(((TileEntityLandingGear)world.getTileEntity(base)).progress==1,"parking preserves extension");
        }finally{
            if(craft!=null && !craft.isDead){try{VehicleTransfer.park(world,craft,new BlockPos(craft.posX,craft.posY,craft.posZ),player);}catch(Exception failure){throw new IllegalStateException("gear round-trip cleanup",failure);}}
            for(int x=-2;x<=5;x++)for(int y=-2;y<=4;y++)for(int z=-2;z<=5;z++)world.setBlockToAir(base.add(x,y,z));
            player.setPositionAndUpdate(before.x,before.y,before.z);
        }
    }
    private static void require(boolean value,String text){if(!value)throw new IllegalStateException("non-wheeled gear: "+text);}
}
