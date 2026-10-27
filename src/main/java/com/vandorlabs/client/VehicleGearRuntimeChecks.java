package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityLandingGear;
import com.vandorlabs.vehicle.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Support-plane validation uses actual geometry rather than occupied-cell rounding. */
final class VehicleGearRuntimeChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static VehicleScan scan(WorldServer world,BlockPos seat) {
        VehicleScan scan=new VehicleScan(world,seat);int slices=0;
        while(!scan.step(1) && ++slices<1024);
        require(slices<1024,"gear scan remains bounded");return scan;
    }
    static void run(WorldServer world,BlockTelescopicLandingGear block) {
        BlockPos base=new BlockPos(320,170,80),seat=base.add(2,5,0);
        world.getChunkProvider().loadChunk(19,4);world.getChunkProvider().loadChunk(19,5);
        world.getChunkProvider().loadChunk(20,4);world.getChunkProvider().loadChunk(20,5);
        for(int size=0;size<BlockTelescopicLandingGear.SIZES.length;size++) {
            for(int x=0;x<=8;x++)world.setBlockState(base.add(x,4,0),Blocks.STONE.getDefaultState(),2);
            world.setBlockState(seat,ModBlocks.PILOT_SEAT.getDefaultState(),2);
            world.setBlockState(seat.up(),ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.UPPER,true),2);
            TileEntityLandingGear[] roots=new TileEntityLandingGear[3];
            for(int i=0;i<3;i++) {
                if(i>0){VehicleScan insufficient=scan(world,seat);require(insufficient.result==null && insufficient.error!=null && insufficient.error.contains("at least three"),"one and two gear roots cannot convert");}
                BlockPos at=base.add(i*4,i,0);world.setBlockState(at,block.getDefaultState(),2);
                roots[i]=(TileEntityLandingGear)world.getTileEntity(at);
                require(roots[i].configure(0,0,16*(i+1),size),"gear size configuration");
                require(block.setExtended(world,at,true),"reserve extended gear footprint");
                roots[i].progress=roots[i].previous=i+1;
                for(int y=i+1;y<4;y++)world.setBlockState(base.add(i*4,y,0),Blocks.STONE.getDefaultState(),2);
            }
            VehicleScan aligned=scan(world,seat);
            require(aligned.error==null && aligned.result!=null && aligned.result.gearCount==3,
                    "different mount heights and extension lengths share a support plane, size="+size+": "+aligned.error);
            List<VehicleStructure.Cell> retracted=new ArrayList<>();
            for(VehicleStructure.Cell cell:aligned.result.cells){
                net.minecraft.nbt.NBTTagCompound data=cell.tileData();net.minecraft.block.state.IBlockState state=cell.state;
                if(state.getBlock()==block && !state.getValue(BlockTelescopicLandingGear.LOWER)){data.setFloat("Progress",0);state=state.withProperty(BlockTelescopicLandingGear.EXTENDED,false);}
                retracted.add(new VehicleStructure.Cell(cell.pos,state,data));
            }
            VehicleRotation.Plan restored=VehicleLandingGear.parking(new VehicleStructure(retracted,aligned.result.seat,aligned.result.facing,3),aligned.origin);
            VehicleWorld restoredView=new VehicleWorld(world,restored.structure);int restoredRoots=0;
            for(VehicleStructure.Cell cell:restored.structure.cells)if(cell.state.getBlock()==block && !cell.state.getValue(BlockTelescopicLandingGear.LOWER)){
                TileEntityLandingGear root=(TileEntityLandingGear)restoredView.getTileEntity(cell.pos);restoredRoots++;
                require(cell.state.getValue(BlockTelescopicLandingGear.EXTENDED) && root.progress==root.getExtensionPixels()/16F,"parking extends and settles each gear size");
                for(int down=1;down<=Math.ceil(root.progress)+(size>=3?1:0);down++)require(block.root(restoredView,cell.pos.down(down))==root,"extended parking footprint resolves its owner");
            }
            require(restoredRoots==3,"parking retains all gear roots");
            VehicleStructure originalRetracted=new VehicleStructure(retracted,aligned.result.seat,aligned.result.facing,3);
            for(int turn=0;turn<4;turn++){
                VehicleStructure rotated=VehicleRotation.rotate(originalRetracted,new VehicleWorld(world,originalRetracted),turn);
                VehicleRotation.Plan rotation=VehicleLandingGear.parking(rotated,aligned.origin);VehicleWorld local=new VehicleWorld(world,rotation.structure);
                for(VehicleStructure.Cell cell:rotation.structure.cells)if(cell.state.getBlock()==block && cell.state.getValue(BlockTelescopicLandingGear.LOWER)){
                    TileEntityLandingGear root=block.root(local,cell.pos);require(root!=null,"rotated gear reservation has a root");
                    BlockPos delta=cell.pos.subtract(root.getPos());int radius=size>=3?1:0,minimum=size==4?0:-radius,count=(int)Math.ceil(root.getExtensionPixels()/16D)+(size>=3?1:0);
                    require(delta.getX()>=minimum && delta.getX()<=radius && delta.getZ()>=minimum && delta.getZ()<=radius && delta.getY()>=-count && delta.getY()<=0,"parking removes obsolete rotated gear cells");
                }
            }
            double bottom=Double.POSITIVE_INFINITY;
            List<AxisAlignedBB> collision=new ArrayList<>();
            for(VehicleStructure.Cell cell:aligned.result.cells)if(cell.state.getBlock()==block)
                cell.state.addCollisionBoxToList(world,aligned.origin.add(cell.pos),new AxisAlignedBB(base.add(-2,-7,-2),base.add(11,7,3)),collision,null,false);
            for(AxisAlignedBB box:collision)bottom=Math.min(bottom,box.minY);
            require(Math.abs(bottom-block.vehicleBottomY(world,base))<.001,"support plane agrees with emitted collision geometry for size="+size);
            require(roots[1].configure(0,0,24,size),"half-block mismatch configuration");roots[1].progress=roots[1].previous=1.5F;
            VehicleScan mismatch=scan(world,seat);
            require(mismatch.result==null && mismatch.error!=null && mismatch.error.contains("at least three"),"two supports on the lowest plane cannot convert, size="+size+": "+mismatch.error);
            roots[1].progress=1.49F;
            VehicleScan moving=scan(world,seat);
            require(moving.result==null && moving.error!=null && moving.error.contains("stop moving"),"moving gear remains refused");
            for(BlockPos at:BlockPos.getAllInBox(base.add(-2,-6,-2),base.add(10,6,2))) {
                world.removeTileEntity(at);world.setBlockState(at,Blocks.AIR.getDefaultState(),2);
            }
        }
        BlockPos wheelAt=base.up(12);
        for(String size:new String[]{"small","medium","large"}){
            BlockWheel wheel=(BlockWheel)net.minecraft.block.Block.getBlockFromName("vandorlabs:wheel_"+size);
            world.setBlockState(wheelAt.south(),Blocks.STONE_SLAB.getDefaultState(),2);
            require(wheel.canPlaceBlockOnSide(world,wheelAt,net.minecraft.util.EnumFacing.NORTH),"wheel mounts on slab behind it: "+size);
            net.minecraft.block.state.IBlockState state=wheel.getDefaultState();world.setBlockState(wheelAt,state,2);wheel.onBlockPlacedBy(world,wheelAt,state,null,net.minecraft.item.ItemStack.EMPTY);
            List<BlockPos> members=wheel.members(wheelAt,state);require(members.size()==(size.equals("large")?4:1),"wheel footprint "+size);
            for(int n=0;n<members.size();n++)require(world.getBlockState(members.get(n)).getBlock()==wheel&&world.getBlockState(members.get(n)).getValue(BlockWheel.PART)==n,"wheel reserves correctly numbered parts");
            for(BlockPos member:members)world.setBlockToAir(member);
            world.setBlockToAir(wheelAt.south());
        }
        BlockPos slabAt=wheelAt.up(4);world.setBlockState(slabAt.down(),Blocks.STONE.getDefaultState(),2);
        net.minecraft.block.state.IBlockState topSlab=Blocks.STONE_SLAB.getDefaultState().withProperty(net.minecraft.block.BlockSlab.HALF,net.minecraft.block.BlockSlab.EnumBlockHalf.TOP);
        world.setBlockState(slabAt,topSlab,2);
        require(!VehicleScan.touches(world,slabAt,topSlab,net.minecraft.util.EnumFacing.DOWN),"top slabs exclude the separated floor below");
        require(!VehicleScan.touches(world,slabAt.down(),Blocks.STONE.getDefaultState(),net.minecraft.util.EnumFacing.UP),"top-slab floor exclusion is symmetric");
        world.setBlockToAir(slabAt);world.setBlockToAir(slabAt.down());
        System.out.println("[vandorlabs][reprolab] vehicle-gear PASS five-sizes minimum-three-roots equal-bottom-Y unequal-mounts unequal-extensions half-block-refused moving-refused collision-plane bounded-scan");
    }
}
