package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ProgrammableSettings;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Actual housing-family Copifier capture and cross-profile apply observations. */
final class CopifierHousingLegacyChecks {
    static void run(){try{
        ModBlocks.PROGRAMMABLE_STAIRS=new BlockProgrammableStairs();
        ModBlocks.PROGRAMMABLE_WALL=new BlockProgrammableWall("programmable_wall",BlockProgrammableWall.Shape.PLAIN);
        ModBlocks.PROGRAMMABLE_PORTHOLE_WALL=new BlockProgrammableWall("programmable_porthole_wall",BlockProgrammableWall.Shape.PORTHOLE);
        ModBlocks.PROGRAMMABLE_PORTHOLE_BLOCK=new BlockProgrammablePortholeBlock();
        Block diagonalPorthole=new BlockProgrammableWall("programmable_diagonal_porthole",BlockProgrammableWall.Shape.DIAGONAL_PORTHOLE);
        ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS=new BlockProgrammableDiagonalGlass();
        Block[] blocks={ModBlocks.PROGRAMMABLE_BLOCK,ModBlocks.PROGRAMMABLE_SLAB,ModBlocks.PROGRAMMABLE_STAIRS,ModBlocks.PROGRAMMABLE_WALL,ModBlocks.PROGRAMMABLE_PORTHOLE_WALL,ModBlocks.PROGRAMMABLE_PORTHOLE_BLOCK,ModBlocks.PROGRAMMABLE_DIAGONAL_WALL,diagonalPorthole,ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS};
        JsonArray rows=new JsonArray();int cases=0;BlockPos pos=new BlockPos(4,80,4);
        for(Block block:blocks)for(int meta=0;meta<16;meta++)for(int profile=0;profile<9;profile++){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);world.setBlockState(pos,block.getStateFromMeta(meta),2);
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
            NBTTagCompound initial=new NBTTagCompound();
            if(profile==1)initial=tile.writeToNBT(new NBTTagCompound());
            if(profile>=2){initial.setInteger("housingTexture",profile==2?14:37);initial.setInteger("SideTexture",8);initial.setBoolean("SlabTileSides",(profile&1)==0);initial.setInteger("MainTextureLayout",profile==3?2:1);initial.setBoolean("FaceTexturesEnabled",true);initial.setIntArray("FaceTextures",new int[]{1,2,3,4,5,6});initial.setIntArray("RedstoneChannels",new int[]{13,17});initial.setInteger(com.vandorlabs.persistence.StairState.TREAD,4);initial.setInteger("StairRoll",2);initial.setBoolean("DiagonalFullWidth",true);initial.setBoolean("JoinPortholes",false);}
            if(profile>=3)initial.setInteger("HousingTextureVersion",1);
            if(profile==4){initial.setString("housingTexture","wrong");initial.setString("MainTextureLayout","wrong");initial.setIntArray("FaceTextures",new int[]{1,2});}
            if(profile==5){initial.setInteger("GlassShade",3);initial.setInteger("GlassTint",4);initial.setInteger("GlassMaterial",8);initial.setBoolean("GlassFramed",false);initial.setInteger("GlassTextureLayout",2);initial.setBoolean("GlassTextureJoin",true);}
            if(profile==6){initial.setInteger("MainTextureLayout",99);initial.setInteger("SideTexture",-1);initial.setIntArray("FaceTextures",new int[]{-1,2,-1,4,-1,6});}
            if(profile==7){initial.setInteger("GlassTint",2);initial.setInteger("PortholeShape",3);initial.setInteger("DiagonalJoin",7);initial.setInteger("DiagonalFill",2);initial.setInteger("MainTextureLayout",2);initial.setBoolean("JoinPortholes",true);}
            if(profile==8){initial.setBoolean("DiagonalHalfHeight",true);initial.setBoolean("DiagonalFullWidth",true);initial.setInteger("DiagonalFill",3);initial.setInteger("PortholeShape",2);}
            initial.setInteger("x",pos.getX());initial.setInteger("y",pos.getY());initial.setInteger("z",pos.getZ());tile.readFromNBT(initial.copy());
            NBTTagCompound captured=ProgrammableSettings.capture(world,pos);if(captured==null)throw new AssertionError("Missing capture "+block);
            JsonObject row=new JsonObject();row.addProperty("block",block.getRegistryName().getResourcePath());row.addProperty("meta",meta);row.addProperty("profile",profile);row.addProperty("input",initial.toString());row.addProperty("capture",captured.toString());
            BlockPos target=pos.east(3);world.setBlockState(target,block.getStateFromMeta(meta^3),2);
            row.addProperty("applied",ProgrammableSettings.apply(world,target,captured.copy()));
            row.addProperty("recaptured",ProgrammableSettings.capture(world,target).toString());
            if(world.getBlockState(target)!=block.getStateFromMeta(meta^3))throw new AssertionError("Apply changed orientation");
            rows.add(row);cases++;
        }
        byte[] bytes=new Gson().toJson(rows).getBytes(StandardCharsets.UTF_8);Path path=Paths.get(System.getProperty("vandorlabs.copifierHousingReference"));Files.createDirectories(path.getParent());Files.write(path,bytes);
        StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        if(!hash.toString().equals("38a0bb9557e05717852f1f26fe020f31fcc3535438ce9d36a533f84f0a7ff31f"))throw new AssertionError("Source housing capture/apply changed: "+hash);
        System.out.println("Copifier housing Source PASS cases="+cases+" sha256="+hash);
    }catch(Exception failure){throw new AssertionError(failure);}}
}
