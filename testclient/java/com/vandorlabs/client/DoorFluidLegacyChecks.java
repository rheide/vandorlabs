package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Actual current opening planes and ordered compact fluid cells/faces, independent of native geometry. */
public final class DoorFluidLegacyChecks {
    private static MessageDigest hash;private static Field SOLID;private static long cells,faces;
    private static void bits(long value){for(int shift=56;shift>=0;shift-=8)hash.update((byte)(value>>>shift));}
    private static void number(double value){bits(Double.doubleToRawLongBits(value));}
    private static void box(AxisAlignedBB b){number(b.minX);number(b.minY);number(b.minZ);number(b.maxX);number(b.maxY);number(b.maxZ);}
    private static String hex(byte[] values){StringBuilder out=new StringBuilder();for(byte b:values)out.append(String.format("%02x",b&255));return out.toString();}
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();SOLID=WaterloggedParts.Volume.class.getDeclaredField("solid");SOLID.setAccessible(true);
        BlockDetailedDoor pair=new BlockDetailedDoor("fluid_door_template",BlockVandorDoor.DoorMotion.HINGED,false,true,false,1,15,11.24F,90,-90,-15,15);Field field=TileEntitySpaceDoor.class.getDeclaredField("RESOLVED_MODELS");field.setAccessible(true);AtomicReferenceArray<BlockSpaceDoor> cache=(AtomicReferenceArray<BlockSpaceDoor>)field.get(null);BlockSpaceDoor[] previous=new BlockSpaceDoor[4];for(int key=0;key<4;key++){previous[key]=cache.get(key);cache.set(key,new BlockSpaceDoor(TileEntitySpaceDoor.modelId(2,(key&2)!=0,(key&1)!=0),(key&2)!=0,(key&1)!=0,pair));}
        BlockConfigurableSpaceDoor block=new BlockConfigurableSpaceDoor("door_fluid_fixture",true,pair);Path file=Paths.get(System.getProperty("vandorlabs.doorFluidReference","build/test-artifacts/door-fluid-forge.jsonl"));Files.createDirectories(file.toAbsolutePath().getParent());MessageDigest complete=MessageDigest.getInstance("SHA-256");int cases=0;
        try(java.io.PrintWriter writer=new java.io.PrintWriter(Files.newBufferedWriter(file))){
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean sliding:new boolean[]{false,true})for(boolean framed:new boolean[]{false,true})for(boolean hinges:new boolean[]{false,true})for(int depth=0;depth<3;depth++)for(boolean left:new boolean[]{false,true})for(boolean paired:new boolean[]{false,true})for(boolean bottomOpen:new boolean[]{false,true})for(boolean topOpen:new boolean[]{false,true})for(BlockPos origin:new BlockPos[]{BlockPos.ORIGIN,new BlockPos(3300,110,3300),new BlockPos(-2047,63,-17),new BlockPos(29999983,240,-29999983)}){
                NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);IBlockState bottom=block.getDefaultState().withProperty(BlockVandorDoor.FACING,facing).withProperty(BlockVandorDoor.OPEN,bottomOpen),top=block.getDefaultState().withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER).withProperty(BlockVandorDoor.HINGE,left?BlockDoor.EnumHingePosition.LEFT:BlockDoor.EnumHingePosition.RIGHT).withProperty(BlockVandorDoor.OPEN,topOpen);world.setBlockState(origin,bottom,2);world.setBlockState(origin.up(),top,2);
                if(paired){BlockPos pos=origin.offset(left?facing.rotateY():facing.rotateYCCW());world.setBlockState(pos,bottom,2);world.setBlockState(pos.up(),top.withProperty(BlockVandorDoor.HINGE,left?BlockDoor.EnumHingePosition.RIGHT:BlockDoor.EnumHingePosition.LEFT),2);}
                NBTTagCompound tag=new NBTTagCompound();tag.setInteger("SpaceDoorSchema",2);tag.setBoolean("SpaceDoorSliding",sliding);tag.setBoolean("SpaceFramed",framed);tag.setBoolean("SpaceDoorHinges",hinges);tag.setInteger("SpaceDoorPlacementDepth",depth);tag.setInteger("x",origin.getX());tag.setInteger("y",origin.getY());tag.setInteger("z",origin.getZ());((TileEntitySpaceDoor)world.getTileEntity(origin)).readFromNBT(tag);
                for(boolean upper:new boolean[]{false,true}){BlockPos pos=upper?origin.up():origin;hash=MessageDigest.getInstance("SHA-256");double[] plane=OpeningFluids.plane(world,pos);for(double n:plane)number(n);WaterloggedParts.Volume volume=WaterloggedParts.uncachedVolume(world,pos);List<AxisAlignedBB> solids=(List<AxisAlignedBB>)SOLID.get(volume);bits(solids.size());for(AxisAlignedBB b:solids)box(b);bits(volume.cells.size());for(WaterloggedParts.Cell cell:volume.cells){bits(cell.side);box(cell.box);cells++;}bits(volume.faces.size());for(WaterloggedParts.Face face:volume.faces){bits(face.side);bits(face.axis);for(Vec3d vertex:face.vertices){number(vertex.x);number(vertex.y);number(vertex.z);}faces++;}
                    JsonObject row=new JsonObject();row.addProperty("face",facing.getIndex());row.addProperty("x",origin.getX());row.addProperty("y",origin.getY());row.addProperty("z",origin.getZ());row.addProperty("sliding",sliding);row.addProperty("framed",framed);row.addProperty("hinges",hinges);row.addProperty("depth",depth);row.addProperty("left",left);row.addProperty("paired",paired);row.addProperty("bottomOpen",bottomOpen);row.addProperty("topOpen",topOpen);row.addProperty("upper",upper);row.addProperty("hash",hex(hash.digest()));String json=new Gson().toJson(row);complete.update(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));writer.println(json);cases++;
                }
            }
        }finally{for(int key=0;key<4;key++)cache.set(key,previous[key]);}
        String golden=hex(complete.digest());if(cases!=12288||cells!=105472||faces!=594432||!golden.equals("bfe2147f851cab0f89884db2de66ea8eb45998561cfe2905f3544145a42fe1a5"))throw new AssertionError("Actual door fluid geometry changed "+golden);System.out.println("Actual ordinary Door fluid geometry PASS cases="+cases+" cells="+cells+" faces="+faces+" golden="+golden+" closed planes/current compact runs/independent half OPEN");
    }
}
