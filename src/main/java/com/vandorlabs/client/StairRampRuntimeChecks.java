package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.compat.RampStairs;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.ramp.RampGeometry;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Compare native stair solids with actual generated cells and retained corner journals. */
final class StairRampRuntimeChecks {
    private static final BlockPos ROOT=new BlockPos(150,44,150);
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("stair ramps: "+message);}
    private static double volume(List<AxisAlignedBB> boxes){
        SortedSet<Double> xs=new TreeSet<>(),ys=new TreeSet<>(),zs=new TreeSet<>();for(AxisAlignedBB b:boxes){xs.add(b.minX);xs.add(b.maxX);ys.add(b.minY);ys.add(b.maxY);zs.add(b.minZ);zs.add(b.maxZ);}
        List<Double> x=new ArrayList<>(xs),y=new ArrayList<>(ys),z=new ArrayList<>(zs);double result=0;
        for(int i=1;i<x.size();i++)for(int j=1;j<y.size();j++)for(int k=1;k<z.size();k++){
            Vec3d center=new Vec3d((x.get(i-1)+x.get(i))/2,(y.get(j-1)+y.get(j))/2,(z.get(k-1)+z.get(k))/2);
            for(AxisAlignedBB b:boxes)if(b.contains(center)){result+=(x.get(i)-x.get(i-1))*(y.get(j)-y.get(j-1))*(z.get(k)-z.get(k-1));break;}
        }
        return result;
    }
    private static double cells(World world){double total=0;int count=0;
        for(BlockPos p:BlockPos.getAllInBox(ROOT.add(-4,-4,-4),ROOT.add(5,8,7)))if(world.getTileEntity(p) instanceof TileEntityControlledRamp){TileEntityControlledRamp part=(TileEntityControlledRamp)world.getTileEntity(p);if(!part.belongsTo(ROOT))continue;total+=volume(part.boxes(0));count++;
            TileEntityControlledRamp copy=new TileEntityControlledRamp();copy.setWorld(world);copy.setPos(p);copy.readFromNBT(part.writeToNBT(new NBTTagCompound()));require(Math.abs(volume(copy.boxes(0))-volume(part.boxes(0)))<1e-6,"cell save retains source stair solids");}
        require(count>0&&total>0,"generated collision cells exist");return total;
    }
    private static void clear(World world){if(world.getTileEntity(ROOT) instanceof TileEntityRampController)((TileEntityRampController)world.getTileEntity(ROOT)).recover(false);for(BlockPos p:BlockPos.getAllInBox(ROOT.add(-4,-4,-4),ROOT.add(5,8,7)))world.setBlockToAir(p);}
    private static double sample(World world,EntityPlayerMP owner,IBlockState material,int mode,int corner){
        clear(world);world.setBlockState(ROOT,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),3);List<BlockPos> sources=new ArrayList<>();
        if(corner==0){for(int row=1;row<=3;row++)for(int lane=0;lane<2;lane++){BlockPos p=(mode==6?ROOT.south().up(row-1):ROOT.south(row)).east(lane);world.setBlockState(p,material,3);sources.add(p);}}
        else{BlockPos p=ROOT.south();world.setBlockState(p,material,3);sources.add(p);world.setBlockState(corner==1?p.east():p.west(),Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,EnumFacing.NORTH).withProperty(BlockStairs.HALF,material.getBlock() instanceof BlockStairs?material.getValue(BlockStairs.HALF):BlockStairs.EnumHalf.BOTTOM),3);}
        Map<BlockPos,NBTTagCompound> finishes=new LinkedHashMap<>();
        if(material.getBlock() instanceof BlockProgrammableStairs)for(BlockPos p:sources){TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);tile.setHousingTexture(CustomBlockMaterials.choice(new net.minecraft.item.ItemStack(Blocks.GOLD_BLOCK)));tile.setSideTexture(CustomBlockMaterials.choice(new net.minecraft.item.ItemStack(Blocks.IRON_BLOCK)));tile.setFaceTextures(new FaceTextures(true,new int[]{1,2,3,4,5,6}));tile.setSlabTileSides(true);finishes.put(p,tile.writeToNBT(new NBTTagCompound()));}
        Map<BlockPos,IBlockState> actual=new LinkedHashMap<>();double nativeVolume=0;
        for(BlockPos p:sources){IBlockState state=world.getBlockState(p).getActualState(world,p);actual.put(p,state);if(material.getBlock() instanceof BlockStairs){List<AxisAlignedBB> nativeBoxes=new ArrayList<>();for(RampGeometry.Box box:RampStairs.boxes(world,state))nativeBoxes.add(new AxisAlignedBB(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ));nativeVolume+=volume(nativeBoxes);if(corner!=0)require(state.getValue(BlockStairs.SHAPE)!=BlockStairs.EnumShape.STRAIGHT,"neighbor produces actual inner/outer corner");}else nativeVolume++;}
        TileEntityRampController ramp=(TileEntityRampController)world.getTileEntity(ROOT);boolean lift=mode==2||mode==3,fill=mode==1||mode==3;int escalator=mode==4?1:mode==5?2:0;
        require(ramp.configureHalfOffsets(owner,0,mode==6?0:2,8,true,false,lift,EnumFacing.SOUTH,0,fill,1,true,false,0,escalator,mode==6),"configure mode="+mode+" material="+material+" corner="+corner+" status="+ramp.status);
        if(mode==6)require(Math.abs(cells(world)-nativeVolume)<1e-5,"upright wall retains exact native stair volume");
        ramp.setChannelSignal(true);ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);require(ramp.attached()&&ramp.isOpen(),"channel deploys every mode");double generated=cells(world);
        if(mode==2||mode==6)require(Math.abs(generated-nativeVolume)<1e-5,"rigid lift/bridge preserves native solid volume mode="+mode+" native="+nativeVolume+" generated="+generated);
        if(material.getBlock() instanceof BlockStairs){int restored=0;for(VehicleStructure.Cell source:VehicleEscalators.originals(ramp)){IBlockState retained=RampStairs.state(source.state,source.tileData());require(retained.getValue(BlockStairs.FACING)==actual.get(source.pos).getValue(BlockStairs.FACING)&&retained.getValue(BlockStairs.HALF)==actual.get(source.pos).getValue(BlockStairs.HALF)&&retained.getValue(BlockStairs.SHAPE)==actual.get(source.pos).getValue(BlockStairs.SHAPE),"journal retains facing, half and neighbor corner");if(finishes.containsKey(source.pos)){NBTTagCompound genuine=source.tileData();genuine.removeTag(RampStairs.STATE);require(finishes.get(source.pos).equals(genuine),"journal retains complete programmable source NBT apart from retained shape marker");}restored++;}require(restored==sources.size(),"all stair originals journaled");}
        if(!finishes.isEmpty())carriedCopy(world,ramp,generated);
        ramp.readFromNBT(ramp.writeToNBT(new NBTTagCompound()));require(ramp.recover(false),"saved recovery succeeds");for(BlockPos p:sources){IBlockState restored=world.getBlockState(p);if(material.getBlock() instanceof BlockStairs)require(restored.getBlock()==material.getBlock()&&restored.getValue(BlockStairs.FACING)==material.getValue(BlockStairs.FACING)&&restored.getValue(BlockStairs.HALF)==material.getValue(BlockStairs.HALF)&&restored.getActualState(world,p).getValue(BlockStairs.SHAPE)==actual.get(p).getValue(BlockStairs.SHAPE),"recovery restores stair facing, half and neighbor corner");else require(restored.equals(material),"recovery restores original blockstate");if(finishes.containsKey(p))require(finishes.get(p).equals(world.getTileEntity(p).writeToNBT(new NBTTagCompound())),"restored programmable stair retains exact source NBT");}
        for(BlockPos p:BlockPos.getAllInBox(ROOT.add(-4,-4,-4),ROOT.add(5,8,7)))require(world.getBlockState(p).getBlock()!=ModBlocks.CONTROLLED_RAMP,"recovery releases all reservations");return generated;
    }
    static void run(World world,EntityPlayerMP owner){double x=owner.posX,y=owner.posY,z=owner.posZ;try{
        owner.setPosition(ROOT.getX()+.5,ROOT.getY()+2,ROOT.getZ()-1.5);double[][] cubes=new double[2][7];for(int mode=0;mode<7;mode++){cubes[0][mode]=sample(world,owner,Blocks.PLANKS.getDefaultState(),mode,0);cubes[1][mode]=sample(world,owner,Blocks.PLANKS.getDefaultState(),mode,1);}
        int cases=0;for(int corner=0;corner<3;corner++)for(BlockStairs.EnumHalf half:BlockStairs.EnumHalf.values())for(EnumFacing direction:corner==0?EnumFacing.HORIZONTALS:new EnumFacing[]{EnumFacing.EAST})for(int mode=0;mode<7;mode++){
            IBlockState stair=Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,direction).withProperty(BlockStairs.HALF,half);double generated=sample(world,owner,stair,mode,corner);require(generated<cubes[corner==0?0:1][mode]-1e-5,"stair void survives mode="+mode+" corner="+corner+" facing="+direction+" half="+half+" stair="+generated+" cube="+cubes[corner==0?0:1][mode]);cases++;
        }
        for(int mode=0;mode<7;mode++){double generated=sample(world,owner,ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,EnumFacing.EAST).withProperty(BlockStairs.HALF,BlockStairs.EnumHalf.TOP),mode,0);require(generated<cubes[0][mode]-1e-5,"programmable stair void survives every mode");cases++;}
        System.out.println("[vandorlabs][reprolab] ramp-stairs-world PASS cases="+cases+" seven-modes cardinal upside-down inner-outer native-volume source-journal NBT recovery");
    }finally{clear(world);owner.setPosition(x,y,z);}}
    private static void carriedCopy(World world,TileEntityRampController ramp,double expectedVolume){
        BlockPos shift=new BlockPos(4-ROOT.getX(),4-ROOT.getY(),4-ROOT.getZ());List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        Set<BlockPos> positions=new LinkedHashSet<>(ramp.vehicleCells());positions.add(ROOT);
        for(BlockPos p:positions){NBTTagCompound data=world.getTileEntity(p).writeToNBT(new NBTTagCompound());data.setBoolean("VehicleRampFrozen",true);cells.add(new VehicleStructure.Cell(p.add(shift),world.getBlockState(p),VehicleTileData.translated(data,shift)));}
        VehicleStructure snapshot=VehicleStructure.read(new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1).write());
        for(int turn=0;turn<4;turn++)snapshot=VehicleRotation.rotate(snapshot,new VehicleWorld(world,snapshot),1);
        VehicleWorld view=new VehicleWorld(world,snapshot);double retained=0;int originals=0;
        for(net.minecraft.tileentity.TileEntity raw:view.tiles.values())if(raw instanceof TileEntityControlledRamp)retained+=volume(((TileEntityControlledRamp)raw).boxes(0));else if(raw instanceof TileEntityRampController)for(VehicleStructure.Cell source:VehicleEscalators.originals((TileEntityRampController)raw)){require(source.state.getBlock()==ModBlocks.PROGRAMMABLE_STAIRS&&source.tileData()!=null,"carried four-turn journal preserves programmable stair and material NBT");originals++;}
        require(originals==6&&Math.abs(retained-expectedVolume)<1e-5,"carried snapshot four-turn keeps exact stair collision volume");
    }
    private StairRampRuntimeChecks(){}
}
