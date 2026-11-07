package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.segmented.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraftforge.fluids.*;

/** Actual stationary Segmented volumes, reversible opening fill and independent four-bit outlet masks. */
public final class SegmentedFluidLegacyChecks {
    private static SegmentedDoorBlock block;private static Field SOLID;private static long totalCells,totalFaces;
    private static final BlockPos POS=new BlockPos(0,80,0);
    private static final class World extends NonRenderingChecks.MemoryWorld {int retry;World(){super(false);time=100;}@Override public void scheduleUpdate(BlockPos pos,Block value,int delay){if(value instanceof SegmentedDoorBlock)retry=delay;}}
    private static SegmentedDoorSettings settings(int direction,int depth){return new SegmentedDoorSettings(direction,false,1,0,false,0,false,SegmentedDoorSettings.DEFAULT.glass,depth);}
    private static void phase(SegmentedDoorTile tile,int phase){tile.targetOpen=phase>=1&&phase<=3;tile.from=phase==2||phase==5?.5:phase==3||phase==4?1:0;tile.started=100;tile.duration=20;}
    private static SegmentedDoorTile setup(World world,BlockPos pos,EnumFacing face,int direction,int depth,int height,int row,int column,int width,int motion,boolean rootPresent){
        world.setBlockState(pos,block.getDefaultState().withProperty(SegmentedDoorBlock.FACING,face),2);SegmentedDoorTile own=(SegmentedDoorTile)world.getTileEntity(pos);own.height=height;own.topOffset=row;own.column=column;own.columns=width;own.settings=settings(direction,depth);own.formed=true;
        phase(own,row==0?motion:motion==0||motion==6?3:0);
        if(row>0&&rootPresent){BlockPos at=pos.up(row);world.setBlockState(at,block.getDefaultState().withProperty(SegmentedDoorBlock.FACING,face),2);SegmentedDoorTile root=(SegmentedDoorTile)world.getTileEntity(at);root.topOffset=0;root.height=height;root.column=column;root.columns=width;root.settings=settings((direction+1)%4,(depth+1)%3);root.formed=true;phase(root,motion);}return own;
    }
    private static NBTTagCompound slot(int kind){NBTTagCompound raw=new NBTTagCompound();raw.setString("FluidName",kind==1?"water":kind==2?"lava":"fixture:unknown");raw.setInteger("Amount",kind==1?777:333);NBTTagCompound extra=new NBTTagCompound();extra.setInteger("FixtureKind",kind);raw.setTag("Tag",extra);return raw;}
    private static void stored(SegmentedDoorTile tile,int first,int second){NBTTagCompound raw=new NBTTagCompound(),data=new NBTTagCompound();if(first!=0)data.setTag("Side0",slot(first));if(second!=0)data.setTag("Side1",slot(second));raw.setTag("ContainedFluids",data);tile.fluids.read(raw);}
    private static String hex(byte[] bytes){StringBuilder result=new StringBuilder();for(byte b:bytes)result.append(String.format("%02x",b&255));return result.toString();}
    private static void bits(MessageDigest hash,long value){for(int shift=56;shift>=0;shift-=8)hash.update((byte)(value>>>shift));}
    private static void number(MessageDigest hash,double value){bits(hash,Double.doubleToRawLongBits(value));}
    private static void box(MessageDigest hash,AxisAlignedBB b){number(hash,b.minX);number(hash,b.minY);number(hash,b.minZ);number(hash,b.maxX);number(hash,b.maxY);number(hash,b.maxZ);}
    private static final class Output implements AutoCloseable {final java.io.PrintWriter out;final MessageDigest hash;final String name;int count;Output(String prefix,String name)throws Exception{this.name=name;Path file=Paths.get(prefix+"-"+name+"-forge.jsonl");Files.createDirectories(file.toAbsolutePath().getParent());out=new java.io.PrintWriter(Files.newBufferedWriter(file));hash=MessageDigest.getInstance("SHA-256");}void row(JsonObject row){String line=row.toString();out.println(line);hash.update(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));count++;}public void close(){out.close();String golden=hex(hash.digest());int expected=name.equals("volume")?17856:name.equals("opening")?28672:64512;String frozen=name.equals("volume")?"03fc33223f62c8df9fc03b87b73c7e973cf834e8c3fab72db9d2580bf1c74efe":name.equals("opening")?"74356eb279039fb30c8251d1ea14eadf0230b9371579e2299659f8b81d012a82":"4151ed6de25a76f00cf872211293dd4a3d0dcaec323d369a36ddf3120f669404";if(count!=expected||!golden.equals(frozen))throw new AssertionError("Segmented "+name+" source golden changed cases="+count+" hash="+golden);System.out.println("Actual Segmented fluids "+name+" PASS cases="+count+" golden="+golden);}}
    @SuppressWarnings("unchecked") private static void volumes(Output output)throws Exception{
        int[] dimensions={1,3,32};BlockPos[] origins={BlockPos.ORIGIN,new BlockPos(3300,110,3300),new BlockPos(-2047,63,-17),new BlockPos(29999967,200,-29999967)};
        for(EnumFacing face:EnumFacing.HORIZONTALS)for(int depth=0;depth<3;depth++)for(int direction=0;direction<4;direction++)for(int span:dimensions){Set<Integer> roles=new LinkedHashSet<>(Arrays.asList(0,span/2,span-1));for(int row:roles)for(int column:roles)for(int motion:new int[]{0,1,5})for(BlockPos pos:origins)for(int available=0;available<(row==0?1:2);available++){
            boolean rootPresent=row==0||available==1;World world=new World();SegmentedDoorTile tile=setup(world,pos,face,direction,depth,span,row,column,span,motion,rootPresent);MessageDigest hash=MessageDigest.getInstance("SHA-256");double[] plane=OpeningFluids.plane(world,pos);for(double value:plane)number(hash,value);WaterloggedParts.Volume volume=WaterloggedParts.uncachedVolume(world,pos);List<AxisAlignedBB> solids=(List<AxisAlignedBB>)SOLID.get(volume);bits(hash,solids.size());for(AxisAlignedBB value:solids)box(hash,value);bits(hash,volume.cells.size());for(WaterloggedParts.Cell value:volume.cells){bits(hash,value.side);box(hash,value.box);totalCells++;}bits(hash,volume.faces.size());for(WaterloggedParts.Face value:volume.faces){bits(hash,value.side);bits(hash,value.axis);for(Vec3d vertex:value.vertices){number(hash,vertex.x);number(hash,vertex.y);number(hash,vertex.z);}totalFaces++;}
            JsonObject result=new JsonObject();result.addProperty("face",face.getIndex());result.addProperty("depth",depth);result.addProperty("direction",direction);result.addProperty("height",span);result.addProperty("width",span);result.addProperty("row",row);result.addProperty("column",column);result.addProperty("motion",motion);result.addProperty("rootPresent",rootPresent);result.addProperty("x",pos.getX());result.addProperty("y",pos.getY());result.addProperty("z",pos.getZ());result.addProperty("closed",tile.closed());result.addProperty("cells",volume.cells.size());result.addProperty("faces",volume.faces.size());result.addProperty("hash",hex(hash.digest()));output.row(result);
        }}
    }
    private static void opening(Output output)throws Exception{
        for(EnumFacing face:EnumFacing.HORIZONTALS)for(int motion=0;motion<7;motion++)for(int mask=0;mask<64;mask++){
            World world=new World();SegmentedDoorTile tile=setup(world,POS,face,0,2,2,1,0,1,motion,true);int actual=mask&~(1<<EnumFacing.UP.getIndex());for(EnumFacing side:EnumFacing.values())if(side!=EnumFacing.UP)world.setBlockState(POS.offset(side),(actual&(1<<side.getIndex()))!=0?Blocks.WATER.getDefaultState():Blocks.AIR.getDefaultState(),2);
            for(int first=0;first<4;first++)for(int second=0;second<4;second++){stored(tile,first,second);JsonObject row=new JsonObject();row.addProperty("face",face.getIndex());row.addProperty("motion",motion);row.addProperty("mask",actual);row.addProperty("first",first);row.addProperty("second",second);row.addProperty("open",OpeningFluids.open(world,POS));for(int side=0;side<2;side++){FluidStack value=OpeningFluids.effective(world,POS,side);row.addProperty("side"+side,value==null?0:value.getFluid()==FluidRegistry.WATER?1:value.getFluid()==FluidRegistry.LAVA?2:-1);row.addProperty("amount"+side,value==null?0:value.amount);row.addProperty("tag"+side,value==null||value.tag==null?0:value.tag.getInteger("FixtureKind"));}output.row(row);}
        }
    }
    private static void gush(Output output)throws Exception{
        for(EnumFacing face:EnumFacing.HORIZONTALS)for(int depth=0;depth<3;depth++)for(int motion=0;motion<7;motion++)for(int first=0;first<4;first++)for(int second=0;second<4;second++)for(int target=0;target<6;target++)for(boolean previous:new boolean[]{false,true})for(boolean stop:new boolean[]{false,true})for(boolean limited:new boolean[]{false,true}){
            World world=new World();SegmentedDoorTile tile=setup(world,POS,face,0,depth,2,1,0,1,motion,true);stored(tile,first,second);tile.waterOutlets=previous?15:0;
            net.minecraft.block.state.IBlockState initial=target==0?Blocks.AIR.getDefaultState():target==1?Blocks.WATER.getDefaultState():target==2?Blocks.FLOWING_WATER.getDefaultState().withProperty(BlockLiquid.LEVEL,3):target==3?Blocks.LAVA.getDefaultState():target==4?Blocks.STONE.getDefaultState():Blocks.TALLGRASS.getDefaultState();for(EnumFacing side:EnumFacing.HORIZONTALS)world.states.put(POS.offset(side),initial);world.chunkLimit=limited;
            if(stop)DoorWaterGush.stop(world,POS);else block.updateTick(world,POS,world.getBlockState(POS),world.rand);
            JsonObject result=new JsonObject();result.addProperty("face",face.getIndex());result.addProperty("depth",depth);result.addProperty("motion",motion);result.addProperty("first",first);result.addProperty("second",second);result.addProperty("target",target);result.addProperty("previous",previous);result.addProperty("stop",stop);result.addProperty("limited",limited);result.addProperty("mask",tile.waterOutlets);result.addProperty("retry",world.retry);JsonArray states=new JsonArray();for(int side=0;side<4;side++){net.minecraft.block.state.IBlockState value=world.states.get(POS.offset(EnumFacing.getHorizontal(side)));states.add(value.getMaterial()==net.minecraft.block.material.Material.WATER?value.getValue(BlockLiquid.LEVEL):target==0?-1:target==3?-3:target==4?-4:-5);}result.add("states",states);if(tile.writeToNBT(new NBTTagCompound()).getInteger("SegmentedWaterOutlets")!=tile.waterOutlets)throw new AssertionError("Segmented mask save changed");output.row(result);
        }
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(SegmentedDoorTile.class,new net.minecraft.util.ResourceLocation("vandorlabs","segmented_door"));block=new SegmentedDoorBlock();SOLID=WaterloggedParts.Volume.class.getDeclaredField("solid");SOLID.setAccessible(true);String prefix=System.getProperty("vandorlabs.segmentedFluidReference","build/test-artifacts/segmented-fluid");try(Output volume=new Output(prefix,"volume");Output opening=new Output(prefix,"opening");Output gush=new Output(prefix,"gush")){volumes(volume);opening(opening);gush(gush);}if(totalCells!=158976||totalFaces!=889984)throw new AssertionError("Segmented cell/face count changed");System.out.println("Actual Segmented fluid geometry cells="+totalCells+" lazyFaces="+totalFaces);
    }
}
