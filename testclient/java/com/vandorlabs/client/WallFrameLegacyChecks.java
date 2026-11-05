package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockProgrammableWall;
import com.vandorlabs.tiles.*;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Actual current wall factory, typed frame/default reads and sparse save fields for native replay. */
public final class WallFrameLegacyChecks {
    private static int first(String category){for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(category.equals(ScreenHousingTextures.category(i)))return ScreenHousingTextures.choiceAt(i);throw new AssertionError(category);}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class,new net.minecraft.util.ResourceLocation("vandorlabs","wall_frame_fixture"));JsonArray rows=new JsonArray();int frame=first("Frame"),screen=first("Screens");
        for(boolean porthole:new boolean[]{false,true})for(int value:new int[]{-2,-1,0,7,frame,screen,Integer.MAX_VALUE})for(int type=0;type<4;type++){
            BlockProgrammableWall block=new BlockProgrammableWall("wall_frame_fixture",porthole?BlockProgrammableWall.Shape.PORTHOLE:BlockProgrammableWall.Shape.PLAIN);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)block.createNewTileEntity(null,0);if(tile.isJoinPortholes()!=porthole||tile.getMainTextureLayout()!=0)throw new AssertionError("current factory defaults");NBTTagCompound input=new NBTTagCompound();String[] fields={"FrameFront","FrameInterior","FrameExterior","DisplaySplitTop","DisplaySplitBottom","DiagonalFill","DiagonalJoin","PortholeShape"};for(String key:fields){if(type==1)input.setInteger(key,value);else if(type==2)input.setByte(key,(byte)value);else if(type==3)input.setString(key,Integer.toString(value));}
            if(type!=0){input.setInteger("DisplayArtworkVersion",value);input.setBoolean("FrameTile",value!=0);input.setBoolean("FrameBare",value>0);input.setBoolean("DisplayFit",value>=0);input.setBoolean("DisplaySplit",value>1);input.setBoolean("DiagonalFullWidth",(value&1)!=0);input.setBoolean("DiagonalHalfHeight",(value&2)!=0);input.setBoolean("JoinPortholes",value>0);}
            NBTTagCompound opaque=new NBTTagCompound();opaque.setString("owner","foreign:wall");opaque.setLong("version",Long.MAX_VALUE);input.setTag("ForgeCaps",opaque);input.setTag("FutureWall",opaque.copy());NBTTagCompound before=input.copy();tile.readFromNBT(input);if(!input.equals(before))throw new AssertionError("original input mutated");JsonObject row=new JsonObject();row.addProperty("porthole",porthole);row.addProperty("input",input.toString());row.addProperty("saved",tile.writeToNBT(new NBTTagCompound()).toString());rows.add(row);
        }
        int defaultScreen=0;for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if("Screens".equals(ScreenHousingTextures.category(i))&&FrameAppearance.bareChoice(i)==i){defaultScreen=ScreenHousingTextures.choiceAt(i);break;}
        JsonObject root=new JsonObject();root.addProperty("defaultScreen",defaultScreen);root.add("cases",rows);Path target=Paths.get(System.getProperty("vandorlabs.wallFrameExport"));Files.createDirectories(target.toAbsolutePath().getParent());Files.write(target,new Gson().toJson(root).getBytes(StandardCharsets.UTF_8));System.out.println("Wall/frame actual-NBT PASS cases="+rows.size()+" factory plain/porthole missingjoinfalse strict frame tags sparse saved flags defaultScreen="+defaultScreen);
    }
}
