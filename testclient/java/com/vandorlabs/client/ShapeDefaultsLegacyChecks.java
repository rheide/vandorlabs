package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Separate actual block-factory/default housing oracle; the frozen stair input rows stay untouched. */
public final class ShapeDefaultsLegacyChecks {
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class,new net.minecraft.util.ResourceLocation("vandorlabs","shape_default_fixture"));
        BlockProgrammableSlab slab=new BlockProgrammableSlab();BlockProgrammableStairs stairs=new BlockProgrammableStairs();JsonArray rows=new JsonArray();
        for(boolean stair:new boolean[]{false,true})for(boolean previous:new boolean[]{false,true})for(int scenario=0;scenario<18;scenario++){
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)(stair?stairs.createTileEntity(null,stairs.getDefaultState()):slab.createNewTileEntity(null,0));
            if(previous){tile.setHousingTexture(12);tile.setMainTextureLayout(2);tile.setSideTexture(7);tile.setFaceTextures(new com.vandorlabs.tiles.FaceTextures(false,new int[]{1,2,3,4,5,6}));tile.setStairGeometry(16,2);}
            NBTTagCompound initial=tile.writeToNBT(new NBTTagCompound()),input=new NBTTagCompound();
            switch(scenario){
                case 1:input.setByte("housingTexture",(byte)7);input.setByte("SideTexture",(byte)9);input.setByte("MainTextureLayout",(byte)2);break;
                case 2:input.setInteger("housingTexture",14);break;
                case 3:input.setInteger("housingTexture",15);break;
                case 4:input.setInteger("housingTexture",Integer.MAX_VALUE);input.setInteger("HousingTextureVersion",1);break;
                case 5:input.setInteger("housingTexture",-1);input.setInteger("HousingTextureVersion",1);break;
                case 6:input.setInteger("MainTextureLayout",99);break;
                case 7:input.setBoolean("SlabTileSides",false);break;
                case 8:input.setInteger("SlabTileSides",0);break;
                case 9:input.setInteger("MainTextureLayout",0);input.setBoolean("SlabTileSides",true);break;
                case 10:input.setInteger("SideTexture",Integer.MAX_VALUE);break;
                case 11:input.setBoolean("FaceTexturesEnabled",true);input.setIntArray("FaceTextures",new int[]{1,2,3});break;
                case 12:input.setBoolean("FaceTexturesEnabled",false);input.setIntArray("FaceTextures",new int[]{1,-2,Integer.MAX_VALUE,3,4,5});break;
                case 13:input.setString("FaceTexturesEnabled","true");input.setTag("FaceTextures",new NBTTagList());break;
                case 14:input.setInteger("StairTread",16);input.setInteger("StairRoll",2);break;
                case 15:input.setInteger("StairTread",3);input.setLong("StairRoll",Long.MAX_VALUE);break;
                case 16:input.setString("StairTread","16");input.setFloat("StairRoll",1.75F);break;
                case 17:input.setInteger("HousingTextureVersion",1);input.setInteger("housingTexture",7);input.setInteger("SideTexture",9);input.setInteger("MainTextureLayout",2);input.setIntArray("FaceTextures",new int[]{1,2,3,4,5,6});break;
            }
            NBTTagCompound opaque=new NBTTagCompound();opaque.setString("owner","foreign:shape");opaque.setLong("min",Long.MIN_VALUE);opaque.setLong("max",Long.MAX_VALUE);input.setTag("ForgeCaps",opaque);input.setTag("FutureShape",opaque.copy());NBTTagCompound before=input.copy();tile.readFromNBT(input);if(!input.equals(before))throw new AssertionError("default read mutated original input");
            JsonObject row=new JsonObject();row.addProperty("stair",stair);row.addProperty("initial",initial.toString());row.addProperty("input",input.toString());row.addProperty("saved",tile.writeToNBT(new NBTTagCompound()).toString());rows.add(row);
        }
        Path target=Paths.get(System.getProperty("vandorlabs.shapeDefaultsExport"));Files.createDirectories(target.toAbsolutePath().getParent());Files.write(target,new Gson().toJson(rows).getBytes(StandardCharsets.UTF_8));System.out.println("Shape actual block-factory defaults PASS cases="+rows.size()+" typed housing previous fallback stair reset opaque original input");
    }
}
