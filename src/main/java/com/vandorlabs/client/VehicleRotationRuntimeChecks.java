package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Exercise every registered vanilla/Vandor block and its persistent metadata states. */
public final class VehicleRotationRuntimeChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("vehicle rotation: "+message);}
    public static void run(WorldServer world){
        List<VehicleStructure.Cell> cells=new ArrayList<>();int blocks=0,states=0;
        cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        for(Block block:Block.REGISTRY){
            String domain=block.getRegistryName().getResourceDomain();
            if(!domain.equals("minecraft") && !domain.equals("vandorlabs"))continue;
            blocks++;Set<IBlockState> variants=new LinkedHashSet<>();variants.add(block.getDefaultState());
            for(int meta=0;meta<16;meta++)try{variants.add(block.getStateFromMeta(meta));}catch(IllegalArgumentException | IndexOutOfBoundsException invalidMetadata){}
            for(IBlockState state:variants){
                int n=cells.size();BlockPos pos=new BlockPos(n%32,n/1024,(n/32)%32);
                TileEntity tile=state.getBlock().hasTileEntity(state)?state.getBlock().createTileEntity(world,state):null;
                NBTTagCompound data=null;
                if(tile!=null){tile.setWorld(world);tile.setPos(pos);data=tile.writeToNBT(new NBTTagCompound());}
                cells.add(new VehicleStructure.Cell(pos,state,data));states++;
                if(cells.size()==4000){check(world,cells);cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));}
            }
        }
        if(cells.size()>1)check(world,cells);
        System.out.println("[vandorlabs][reprolab] vehicle-rotation-registry PASS blocks="+blocks+" states="+states+" four-turn state/NBT restoration");
    }
    private static void check(WorldServer world,List<VehicleStructure.Cell> cells){
        VehicleStructure source=new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1),rotated=source;
        VehicleStructure quarter=VehicleRotation.rotate(source,new VehicleWorld(world,source),1);
        for(VehicleStructure.Cell cell:source.cells){
            VehicleStructure.Cell turned=quarter.at(VehicleRotation.cell(cell.pos,(int)source.bounds.maxX,(int)source.bounds.maxZ,1));
            if(cell.state.getBlock() instanceof BlockGlassWall){
                net.minecraft.util.math.AxisAlignedBB box=cell.state.getBoundingBox(world,cell.pos);
                net.minecraft.util.math.AxisAlignedBB expected=new net.minecraft.util.math.AxisAlignedBB(1-box.maxZ,box.minY,box.minX,1-box.minZ,box.maxY,box.maxX);
                require(expected.equals(turned.state.getBoundingBox(world,turned.pos)),"quarter-turn glass depth/axis "+cell.state);
            }
            if(cell.state.getBlock() instanceof BlockVandorSwitch && cell.state.getValue(BlockVandorSwitch.FACING).getAxis()==EnumFacing.Axis.Y && cell.tileData()!=null){
                require(turned.tileData().getInteger("MountRotation")==((cell.tileData().getInteger("MountRotation")+1)&3),"quarter-turn floor/ceiling control orientation");
            }
        }
        for(int n=0;n<4;n++)rotated=VehicleRotation.rotate(rotated,new VehicleWorld(world,rotated),1);
        require(source.write().equals(rotated.write()),"four turns must preserve every block state and complete private tile data");
    }
}
