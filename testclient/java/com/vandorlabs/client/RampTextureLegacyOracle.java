package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityControlledRamp;
import com.vandorlabs.ramp.RampGeometry;
import java.io.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Actual part geometry, material-origin lookup, sideways fallback and side-UV callbacks. */
public final class RampTextureLegacyOracle {
    public static void main(String[] args)throws Exception{
        RampSettingsLegacyOracle.initialize();net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(ModBlocks.CONTROLLED_RAMP);java.lang.reflect.Method originFor=TileEntityControlledRamp.class.getDeclaredMethod("originFor",RampGeometry.Box.class,double.class);originFor.setAccessible(true);
        File file=new File(args[1]);file.getAbsoluteFile().getParentFile().mkdirs();int rows=0,parts=0,boxes=0;Gson gson=new Gson();NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
        try(BufferedReader in=new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(args[0])),"UTF-8"));PrintWriter out=new PrintWriter(file,"UTF-8")){
            String line;while((line=in.readLine())!=null){JsonObject input=new JsonParser().parse(line).getAsJsonObject();net.minecraft.block.Block controller=net.minecraft.block.Block.getBlockFromName("vandorlabs:"+input.get("block").getAsString());EnumFacing facing=controller.getStateFromMeta(input.get("meta").getAsInt()).getValue(BlockVandorDirectional.FACING);
                for(JsonElement step:input.getAsJsonArray("states")){JsonObject state=step.getAsJsonObject();world.time=JsonToNBT.getTagFromJson(state.get("saved").getAsString()).getLong("LastStepTick");
                    for(JsonElement entry:state.getAsJsonArray("parts")){JsonObject part=entry.getAsJsonObject();NBTTagCompound tag=JsonToNBT.getTagFromJson(part.get("saved").getAsString());BlockPos cell=new BlockPos(tag.getInteger("x"),tag.getInteger("y"),tag.getInteger("z"));world.clear();world.setBlockState(cell,ModBlocks.CONTROLLED_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing),2);TileEntityControlledRamp tile=(TileEntityControlledRamp)world.getTileEntity(cell);tile.readFromNBT(tag);JsonObject row=new JsonObject();row.addProperty("facing",facing.getIndex());row.addProperty("material",input.get("material").getAsInt());row.addProperty("saved",part.get("saved").getAsString());row.addProperty("pose",tile.pose(.25));JsonArray records=new JsonArray();
                        for(RampGeometry.Box box:tile.geometry(.25)){BlockPos origin=(BlockPos)originFor.invoke(tile,box,.25);double[] shift=tile.textureShift(box,.25);JsonArray values=new JsonArray();for(double value:new double[]{origin.getX(),origin.getY(),origin.getZ(),shift[0],shift[1],tile.sideTextureV(box,box.minY,.25),tile.sideTextureV(box,box.maxY,.25)})values.add(value);records.add(values);boxes++;}
                        row.add("frames",records);out.println(gson.toJson(row));parts++;
                    }
                }rows++;
            }
        }
        if(rows!=768||parts!=9322||boxes!=15596)throw new AssertionError("Source texture coverage="+rows+"/"+parts+"/"+boxes);SegmentedReferenceGolden.check(file,"67f14330509814550172a7948b82379fbc2236aabe55ab9264d06b2d1785f60a");System.out.println("Actual Ramp texture callbacks PASS cases="+rows+" parts="+parts+" boxes="+boxes+" material-origin/sideways-fallback/sideUV");
    }
}
