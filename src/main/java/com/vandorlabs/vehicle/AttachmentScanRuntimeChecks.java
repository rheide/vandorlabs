package com.vandorlabs.vehicle;

import com.vandorlabs.programmer.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Cargo discovery reuses the complete vehicle graph without inventing pilot/support requirements. */
public final class AttachmentScanRuntimeChecks {
    private static int assertions;
    private static void require(boolean test,String why){assertions++;if(!test)throw new IllegalStateException("Attachment scan: "+why);}
    private static VehicleScan finish(VehicleScan scan){for(int n=0;n<65536&&!scan.step(64);n++){}require(scan.error!=null||scan.result!=null,"bounded scan terminates");return scan;}
    public static void run(WorldServer world){
        assertions=0;BlockPos base=new BlockPos(1204,112,260);Block programmer=Block.getBlockFromName("vandorlabs:data_chip_programmer");
        for(BlockPos at:BlockPos.getAllInBox(base.add(-3,-2,-3),base.add(21,3,3)))world.getChunkFromBlockCoords(at);
        clear(world,base);
        try{
            world.setBlockState(base,Blocks.STONE.getDefaultState(),2);world.setBlockState(base.east(),Blocks.CHEST.getDefaultState(),2);world.setBlockState(base.east(2),Blocks.BRICK_BLOCK.getDefaultState(),2);
            TileEntityChest chest=(TileEntityChest)world.getTileEntity(base.east());ItemStack cargo=new ItemStack(net.minecraft.init.Items.DIAMOND,3);cargo.setStackDisplayName("Coupler cargo");chest.setInventorySlotContents(0,cargo);NBTTagCompound before=chest.writeToNBT(new NBTTagCompound());
            VehicleScan normal=finish(new VehicleScan(world,base));require(normal.result==null&&normal.error.contains("Pilot Seat"),"ordinary assembly still requires a pilot");
            VehicleScan whole=finish(VehicleScan.attachment(world,base,Collections.emptySet()));require(whole.error==null&&whole.result.cells.size()==3&&whole.result.gearCount==0,"cargo without pilot or landing supports is an exact display fragment");require(whole.result.matches(world,whole.origin)&&before.equals(chest.writeToNBT(new NBTTagCompound())),"source block and named inventory data remain unchanged");
            VehicleScan cut=finish(VehicleScan.attachment(world,base,Collections.singleton(base.east(2))));require(cut.error==null&&cut.result.cells.size()==2,"excluded hull cells terminate ordinary graph branches");
            VehicleWorld view=new VehicleWorld(world,whole.result);view.origin=whole.origin;VehicleScan carried=finish(VehicleScan.attachment(view,BlockPos.ORIGIN,Collections.singleton(new BlockPos(1,0,0))));require(carried.error==null&&carried.result.cells.size()==1&&carried.result.at(BlockPos.ORIGIN).state.getBlock()==Blocks.STONE,"same graph partitions a read-only carried view without touching its parent");require(whole.result.matches(world,whole.origin),"carried partition leaves actual world and source snapshot intact");
            boolean excluded=false;try{VehicleScan.attachment(world,base,Collections.singleton(base));}catch(IllegalArgumentException expected){excluded=true;}require(excluded,"seed cannot be part of excluded hull");
            for(EnumFacing facing:EnumFacing.HORIZONTALS){
                BlockPos root=base.add(7,0,0);for(int part=0;part<4;part++)world.setBlockState(ProgrammerBlock.cell(root,facing,part),programmer.getDefaultState().withProperty(ProgrammerBlock.FACING,facing).withProperty(ProgrammerBlock.PART,part),2);
                VehicleScan all=finish(VehicleScan.attachment(world,root,Collections.emptySet()));require(all.error==null&&all.result.cells.size()==4,"complete programmer reservations follow every facing");
                BlockPos member=ProgrammerBlock.cell(root,facing,3);VehicleScan partial=finish(VehicleScan.attachment(world,root,Collections.singleton(member)));require(partial.result==null&&partial.error.contains("existing vehicle assembly"),"a reserved member cannot be borrowed from the excluded hull");
                for(int part=0;part<4;part++)world.setBlockToAir(ProgrammerBlock.cell(root,facing,part));
            }
            world.setBlockState(base.east(2),Blocks.CHEST.getDefaultState(),2);reject(world,base,base.east(2),"double chest companion");world.setBlockState(base.east(2),Blocks.BRICK_BLOCK.getDefaultState(),2);
            BlockPos bed=base.add(11,0,0);world.setBlockState(bed,Blocks.BED.getDefaultState().withProperty(net.minecraft.block.BlockBed.FACING,EnumFacing.EAST).withProperty(net.minecraft.block.BlockBed.PART,net.minecraft.block.BlockBed.EnumPartType.FOOT),2);world.setBlockState(bed.east(),Blocks.BED.getDefaultState().withProperty(net.minecraft.block.BlockBed.FACING,EnumFacing.EAST).withProperty(net.minecraft.block.BlockBed.PART,net.minecraft.block.BlockBed.EnumPartType.HEAD),2);reject(world,bed,bed.east(),"bed head");world.setBlockToAir(bed);world.setBlockToAir(bed.east());
            BlockPos plant=base.add(14,0,0);world.setBlockState(plant,Blocks.DOUBLE_PLANT.getDefaultState().withProperty(net.minecraft.block.BlockDoublePlant.HALF,net.minecraft.block.BlockDoublePlant.EnumBlockHalf.LOWER),2);world.setBlockState(plant.up(),Blocks.DOUBLE_PLANT.getDefaultState().withProperty(net.minecraft.block.BlockDoublePlant.HALF,net.minecraft.block.BlockDoublePlant.EnumBlockHalf.UPPER),2);reject(world,plant,plant.up(),"double plant upper half");world.setBlockToAir(plant);world.setBlockToAir(plant.up());
            BlockPos piston=base.add(16,0,0);world.setBlockState(piston,Blocks.PISTON.getDefaultState().withProperty(net.minecraft.block.BlockPistonBase.FACING,EnumFacing.EAST).withProperty(net.minecraft.block.BlockPistonBase.EXTENDED,true),2);world.setBlockState(piston.east(),Blocks.PISTON_HEAD.getDefaultState().withProperty(net.minecraft.block.BlockPistonExtension.FACING,EnumFacing.EAST),2);reject(world,piston,piston.east(),"extended piston head");world.setBlockToAir(piston);world.setBlockToAir(piston.east());
            BlockPos part=base.add(17,0,0),owner=part.up(2);Block reserved=Block.getBlockFromName("vandorlabs:constructor_part");world.setBlockState(part,reserved.getDefaultState(),2);((com.vandorlabs.tiles.TileEntityConstructorPart)world.getTileEntity(part)).owner(owner,false);
            VehicleScan orphan=finish(VehicleScan.attachment(world,part,Collections.singleton(owner)));require(orphan.result==null&&orphan.error.contains("existing vehicle assembly"),"Core owner links cannot cross into excluded hull");
            require(before.equals(chest.writeToNBT(new NBTTagCompound())),"all successful and rejected scans preserve source inventories");
        }finally{clear(world,base);}
        System.out.println("[vandorlabs][reprolab] attachment-scan PASS assertions="+assertions+" pilot/support-free exact cargo normal-assembly guard world/view graphs bounded exclusions whole programmer/Core reservations source inventory preservation");
    }
    private static void reject(WorldServer world,BlockPos seed,BlockPos excluded,String description){VehicleScan scan=finish(VehicleScan.attachment(world,seed,Collections.singleton(excluded)));require(scan.result==null&&scan.error.contains("existing vehicle assembly"),description+" cannot be cropped from attached section");}
    private static void clear(WorldServer world,BlockPos base){for(BlockPos at:BlockPos.getAllInBox(base.add(-3,-2,-3),base.add(21,3,3)))world.setBlockToAir(at);}
    private AttachmentScanRuntimeChecks(){}
}
