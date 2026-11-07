package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ProgrammableSettings;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;

/** Actual regular/diagonal setters, ignored partial keys, metadata changes and callback undo. */
public final class CopifierTrapdoorLegacyChecks {
    private static final BlockPos SOURCE=new BlockPos(4,90,4),TARGET=SOURCE.east(6);
    private static NBTTagCompound profile(int code){
        NBTTagCompound tag=new NBTTagCompound();if(code==0)return tag;
        tag.setInteger("housingTexture",code%3==0?7:code%3==1?ScreenHousingTextures.DEFAULT_TRAPDOOR:Integer.MAX_VALUE);
        tag.setInteger("TrapdoorPosition",code%3);tag.setBoolean("TrapdoorSliding",(code&1)!=0);tag.setInteger("TrapdoorTrigger",code%3);
        tag.setBoolean("TrapdoorTileTexture",(code&2)!=0);tag.setInteger("TrapdoorTextureLayout",code%3);tag.setBoolean("TrapdoorCover",(code&4)!=0);
        tag.setBoolean("TrapdoorSlideOverSurface",(code&2)!=0);tag.setBoolean("TrapdoorSlideIntoWall",(code&2)==0);
        tag.setInteger("DiagonalHingeSide",code%3);tag.setBoolean("DiagonalRotationFlipped",(code&4)!=0);
        tag.setInteger("RedstoneChannel",13);tag.setIntArray("RedstoneChannels",new int[]{13,17});
        if(code==10){tag.setString("TrapdoorPosition","wrong");tag.setString("TrapdoorTextureLayout","wrong");}
        if(code==11){tag.setInteger("TrapdoorPosition",99);tag.setInteger("TrapdoorTrigger",99);tag.setInteger("TrapdoorTextureLayout",99);}
        return tag;
    }
    private static TileEntityProgrammableTrapdoor put(NonRenderingChecks.MemoryWorld world,Block block,BlockPos pos,int meta,NBTTagCompound input){
        world.states.put(pos,block.getStateFromMeta(meta));TileEntityProgrammableTrapdoor tile=block instanceof BlockProgrammableDiagonalTrapdoor?new TileEntityProgrammableDiagonalTrapdoor():new TileEntityProgrammableTrapdoor();tile.setWorld(world);tile.setPos(pos);world.tiles.put(pos,tile);NBTTagCompound data=input.copy();data.setInteger("x",pos.getX());data.setInteger("y",pos.getY());data.setInteger("z",pos.getZ());tile.readFromNBT(data);return tile;
    }
    private static NBTTagCompound partial(int code){
        NBTTagCompound values=new NBTTagCompound();
        if(code<4)values.setInteger("trapdoor_texture_layout",code-1);
        else if(code<6)values.setBoolean("trapdoor_tile_texture",code==4);
        else if(code<8)values.setBoolean("trapdoor_cover",code==6);
        else if(code<12)values.setInteger("trapdoor_cover_facing",code==11?-5:code-8);
        else if(code<16)values.setInteger("trapdoor_position",code-12);
        else if(code<20){NBTTagCompound geometry=new NBTTagCompound();if(code==19)geometry.setString("mode","wrong");else geometry.setInteger("mode",code-17);values.setTag("diagonal_geometry",geometry);}
        else if(code<22)values.setBoolean("door_sliding",code==20);
        else if(code<24)values.setBoolean("trapdoor_slide_over_surface",code==22);
        else if(code<26)values.setBoolean("trapdoor_slide_into_wall",code==24);
        else if(code<29){values.setBoolean("door_sliding",true);values.setBoolean("trapdoor_slide_over_surface",true);values.setBoolean("trapdoor_slide_into_wall",true);values.setInteger("trapdoor_hinge_side",code-26);values.setBoolean("trapdoor_rotation_flipped",true);}
        else if(code<32)values.setInteger("trapdoor_hinge_side",code-29);
        else if(code<34)values.setBoolean("trapdoor_rotation_flipped",code==32);
        else if(code<38)values.setInteger("redstone_trigger",code-34);
        else if(code<41)values.setInteger("wall_texture",code==38?-1:code==39?Integer.MAX_VALUE:7);
        else if(code==41)values.setIntArray("redstone_channel",new int[]{29,31});
        else if(code==42)values.setIntArray("redstone_channel",new int[]{-1});
        else if(code==43)values.setString("redstone_channel","wrong");
        else if(code==44){values.setInteger("wall_texture",-1);values.setBoolean("trapdoor_tile_texture",false);values.setIntArray("redstone_channel",new int[]{29,31});}
        else if(code==45){values.setInteger("redstone_trigger",99);values.setInteger("trapdoor_texture_layout",2);values.setIntArray("redstone_channel",new int[]{29,31});}
        else if(code==46){NBTTagCompound transmission=new NBTTagCompound();transmission.setIntArray("Channels",new int[]{37});transmission.setIntArray("Range",new int[]{64});values.setTag("Transmission",transmission);}
        else{values.setInteger("trapdoor_texture_layout",1);values.setBoolean("trapdoor_tile_texture",false);}
        return values;
    }
    private static int face(NonRenderingChecks.MemoryWorld world,BlockPos pos){return world.getBlockState(pos).getValue(BlockProgrammableTrapdoor.FACING).getIndex();}
    private static void observe(JsonArray rows,Block block,int meta,NBTTagCompound input,NBTTagCompound values,boolean full){
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);TileEntityProgrammableTrapdoor owner=put(world,block,TARGET,meta,input);
        JsonObject row=new JsonObject();row.addProperty("diagonal",block instanceof BlockProgrammableDiagonalTrapdoor);row.addProperty("meta",meta);row.addProperty("face",face(world,TARGET));row.addProperty("input",input.toString());row.addProperty("before",ProgrammableSettings.capture(world,TARGET).toString());row.addProperty("offset",owner.canOffsetClosedLeaf());row.addProperty("full",full);row.addProperty("values",values.toString());NBTTagCompound undo=ProgrammableSettings.capture(world,TARGET);
        boolean applied=ProgrammableSettings.apply(world,TARGET,values.copy());row.addProperty("applied",applied);row.addProperty("after",ProgrammableSettings.capture(world,TARGET).toString());row.addProperty("afterFace",face(world,TARGET));row.addProperty("afterTop",world.getBlockState(TARGET).getValue(BlockProgrammableTrapdoor.HALF)==net.minecraft.block.BlockTrapDoor.DoorHalf.TOP);
        if(applied){if(!ProgrammableSettings.apply(world,TARGET,undo))throw new AssertionError("Source callback undo refused");row.addProperty("undo",ProgrammableSettings.capture(world,TARGET).toString());}
        if(world.getTileEntity(TARGET)!=owner)throw new AssertionError("Copied Trapdoor replaced its owner");owner.onChunkUnload();rows.add(row);
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableTrapdoor.class,new ResourceLocation("minecraft:trapdoor_copy"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableDiagonalTrapdoor.class,new ResourceLocation("minecraft:diagonal_trapdoor_copy"));
        Block[] blocks={ModBlocks.PROGRAMMABLE_TRAPDOOR,ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR};JsonArray rows=new JsonArray();int full=0;
        for(Block block:blocks){
            for(int meta=0;meta<16;meta++)for(int code=0;code<12;code++){NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);TileEntityProgrammableTrapdoor source=put(world,block,SOURCE,meta,profile(code));NBTTagCompound values=ProgrammableSettings.capture(world,SOURCE);source.onChunkUnload();observe(rows,block,meta^3,profile(11-code),values,true);full++;}
            for(int face=0;face<4;face++)for(int code=0;code<48;code++)observe(rows,block,face,profile(1+code%11),partial(code),false);
        }
        byte[] raw=new Gson().toJson(rows).getBytes(java.nio.charset.StandardCharsets.UTF_8);Path output=Paths.get(args[0]);Files.createDirectories(output.getParent());Files.write(output,raw);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(raw))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(full!=384||rows.size()!=768||!hash.toString().equals("a267729194c93911e166d3b7b1e4997be3c45fbc32070043308d199bf9d286d7"))throw new AssertionError("Released Trapdoor callbacks changed: "+hash);System.out.println("Copifier Trapdoor Source PASS full="+full+" applications="+rows.size()+" sha256="+hash);
    }
}
