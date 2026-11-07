package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.programmer.ProgrammerBlock;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
/** Actual vanilla connection callbacks, distinct from the released empty side-support shape. */
public final class ProgrammerContactsLegacyChecks {
 public static void main(String[] args)throws Exception{net.minecraft.init.Bootstrap.register();varWorld();}
 private static void varWorld()throws Exception{ProgrammerBlock block=new ProgrammerBlock();NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos root=new BlockPos(0,80,0);Block[] neighbors={net.minecraft.init.Blocks.OAK_FENCE,net.minecraft.init.Blocks.NETHER_BRICK_FENCE,net.minecraft.init.Blocks.COBBLESTONE_WALL,net.minecraft.init.Blocks.IRON_BARS,net.minecraft.init.Blocks.GLASS_PANE,net.minecraft.init.Blocks.STAINED_GLASS_PANE};JsonArray rows=new JsonArray();for(int meta=0;meta<16;meta++)for(EnumFacing side:EnumFacing.HORIZONTALS)for(int kind=0;kind<neighbors.length;kind++){world.states.clear();IBlockState state=block.getStateFromMeta(meta);world.states.put(root,state);BlockPos pos=root.offset(side);world.states.put(pos,neighbors[kind].getDefaultState());IBlockState actual=neighbors[kind].getActualState(neighbors[kind].getDefaultState(),world,pos);String name=side.getOpposite().getName();boolean connected=false,found=false;for(IProperty<?> property:actual.getPropertyKeys())if(property.getName().equals(name)){connected=Boolean.TRUE.equals(value(actual,property));found=true;}if(!found)throw new AssertionError("No Source connection property "+kind+"/"+name);JsonObject row=new JsonObject();row.addProperty("meta",meta);row.addProperty("side",side.getIndex());row.addProperty("kind",kind);row.addProperty("connected",connected);row.addProperty("solidSupport",block.isSideSolid(state,world,root,side));rows.add(row);}String json=new Gson().toJson(rows);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)))hash.append(String.format(Locale.ROOT,"%02x",b&255));if(rows.size()!=384||!hash.toString().equals("dbbc7bd78029ce41ef9c42414202bd0b35de0d09644d7d1afa3fe72a1feb9efc"))throw new AssertionError("Source Programmer contact contract changed: "+hash);JsonObject result=new JsonObject();result.addProperty("golden",hash.toString());result.add("cases",rows);Path output=Paths.get(System.getProperty("vandorlabs.programmerContactsReference","build/test-artifacts/programmer-contacts-forge.json"));Files.createDirectories(output.toAbsolutePath().getParent());Files.write(output,new Gson().toJson(result).getBytes(java.nio.charset.StandardCharsets.UTF_8));System.out.println("Actual Source Programmer contacts PASS cases="+rows.size()+" golden="+hash);}
 private static <T extends Comparable<T>>T value(IBlockState state,IProperty<T> property){return state.getValue(property);}
}
