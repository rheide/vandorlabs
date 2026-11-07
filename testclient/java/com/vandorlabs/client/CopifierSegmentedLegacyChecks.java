package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.items.ProgrammableSettings;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.segmented.*;
import com.vandorlabs.tiles.DoorGlassAppearance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;

/** Actual copied callbacks, including per-section choices and linked-door side effects. */
public final class CopifierSegmentedLegacyChecks {
    private static final BlockPos BASE=new BlockPos(4,90,4);
    private static SegmentedDoorSettings settings(int code){return new SegmentedDoorSettings(code%4,(code&1)!=0,code%3,code%4-1,(code&2)!=0,code%4,(code&4)!=0,new DoorGlassAppearance(-1,code%19,code%4,code%3),code%3);}
    private static EntityPlayer actor(NonRenderingChecks.MemoryWorld world){return new EntityPlayer(world,new com.mojang.authlib.GameProfile(new java.util.UUID(0,1891),"CopiedSegments")){
        public boolean isSpectator(){return false;}public boolean isCreative(){return false;}
        public boolean canPlayerEdit(BlockPos pos,EnumFacing side,net.minecraft.item.ItemStack held){return true;}
    };}
    private static java.util.List<SegmentedDoorTile> placed(NonRenderingChecks.MemoryWorld world,SegmentedDoorBlock block,int width,int height,EnumFacing face,int code){
        java.util.List<SegmentedDoorTile> tiles=new java.util.ArrayList<>();
        for(int x=0;x<width;x++)for(int row=0;row<height;row++){
            BlockPos pos=BASE.offset(face.rotateY(),x).down(row);SegmentedDoorTile tile=new SegmentedDoorTile();tile.setWorld(world);tile.setPos(pos);
            world.tiles.put(pos,tile);world.states.put(pos,block.getDefaultState().withProperty(com.vandorlabs.blocks.BlockVandorDirectional.FACING,face));tiles.add(tile);
        }
        for(int x=0;x<width;x++)((SegmentedDoorTile)world.tiles.get(BASE.offset(face.rotateY(),x))).refreshColumn();
        SegmentedDoorSettings common=settings(code);tiles.get(0).configureAll(actor(world),ChannelList.of(13,17),10,false,true,-1,common);
        for(int i=0;i<tiles.size();i++){SegmentedDoorTile tile=tiles.get(i);tile.settings=settings(code+i).shared(common);tile.identity=new java.util.UUID(0,9000+i/height);if(tile.motionIdentity!=null)tile.motionIdentity=new java.util.UUID(0,9100);}
        return tiles;
    }
    private static JsonArray captures(NonRenderingChecks.MemoryWorld world,java.util.List<SegmentedDoorTile> tiles){JsonArray out=new JsonArray();for(SegmentedDoorTile tile:tiles)out.add(ProgrammableSettings.capture(world,tile.getPos()).toString());return out;}
    private static JsonArray saved(java.util.List<SegmentedDoorTile> tiles){JsonArray out=new JsonArray();for(SegmentedDoorTile tile:tiles)out.add(tile.writeToNBT(new NBTTagCompound()).toString());return out;}
    private static NBTTagCompound partial(int code){
        NBTTagCompound out=new NBTTagCompound(),configuration=new NBTTagCompound();
        if(code<4){out.setTag("segmented_door",configuration);if(code==1)configuration.setInteger("SegmentedSpeed",99);if(code==2)configuration.setInteger("SegmentedTexture",Integer.MAX_VALUE);if(code==3)configuration.setString("SegmentedSpeed","wrong");}
        else if(code<16){settings(code).write(configuration);configuration.setInteger("SegmentedSpeed",code%2==0?0:30);configuration.setBoolean("SegmentedSignalOpens",(code&1)!=0);out.setTag("segmented_door",configuration);}
        else if(code==16)out.setInteger("redstone_channel",29);
        else if(code==17)out.setIntArray("redstone_channel",new int[]{29,31});
        else if(code==18)out.setString("redstone_channel","wrong");
        else if(code==19)out.setIntArray("redstone_channel",new int[]{-1});
        else if(code==20)out.setInteger("wall_texture",7);
        else if(code==21)out.setString("segmented_door","wrong");
        else if(code==22){NBTTagCompound transmission=new NBTTagCompound();transmission.setInteger("Range",64);transmission.setIntArray("Channels",new int[]{37});out.setTag("Transmission",transmission);}
        else {out.setTag("segmented_door",configuration);out.setIntArray("redstone_channel",new int[]{29,31});}
        return out;
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(SegmentedDoorTile.class,new ResourceLocation("vandorlabs:segmented_door"));
        SegmentedDoorBlock block=new SegmentedDoorBlock();if(args.length>1&&args[1].equals("connected")){connectedUndo(block);return;}JsonArray rows=new JsonArray();int full=0;
        for(int width:new int[]{1,2})for(int height:new int[]{1,3})for(EnumFacing face:EnumFacing.HORIZONTALS)for(int code=0;code<36;code++){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);java.util.List<SegmentedDoorTile> tiles=placed(world,block,width,height,face,11-code%12);EntityPlayer player=actor(world);int selected=code%tiles.size();
            NBTTagCompound values;if(code<12){SegmentedDoorTile source=new SegmentedDoorTile();source.applyItemConfiguration(configuration(code),ChannelList.of(23,27));NonRenderingChecks.MemoryWorld sourceWorld=new NonRenderingChecks.MemoryWorld(false);source.setWorld(sourceWorld);source.setPos(BASE);sourceWorld.tiles.put(BASE,source);sourceWorld.states.put(BASE,block.getDefaultState());values=ProgrammableSettings.capture(sourceWorld,BASE);full++;}else values=partial(code-12);
            JsonObject row=new JsonObject();row.addProperty("width",width);row.addProperty("height",height);row.addProperty("face",face.getIndex());row.addProperty("selected",selected);row.add("input",saved(tiles));row.add("before",captures(world,tiles));row.addProperty("values",values.toString());NBTTagCompound undo=ProgrammableSettings.capture(world,tiles.get(selected).getPos());
            boolean applied=ProgrammableSettings.apply(world,tiles.get(selected).getPos(),values.copy(),player);row.addProperty("applied",applied);row.add("after",captures(world,tiles));
            if(applied){if(!ProgrammableSettings.apply(world,tiles.get(selected).getPos(),undo,player))throw new AssertionError("Source undo callback refused");row.add("undo",captures(world,tiles));}
            for(SegmentedDoorTile tile:tiles){if(world.getTileEntity(tile.getPos())!=tile)throw new AssertionError("Copied section replaced");tile.onChunkUnload();}rows.add(row);
        }
        byte[] raw=new Gson().toJson(rows).getBytes(java.nio.charset.StandardCharsets.UTF_8);Path out=Paths.get(args[0]);Files.createDirectories(out.getParent());Files.write(out,raw);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(raw))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(full!=192||rows.size()!=576||!hash.toString().equals("edbf1837c357a857439d0745462fc018a7ec2223cfc5417012f12fe53672d560"))throw new AssertionError("Released Segmented Door callbacks changed: "+hash);System.out.println("Copifier Segmented Door Source PASS full="+full+" applications="+rows.size()+" sha256="+hash);
    }
    private static NBTTagCompound configuration(int code){NBTTagCompound tag=new NBTTagCompound();settings(code).write(tag);tag.setInteger("SegmentedSpeed",5+code*2);tag.setBoolean("SegmentedLocalRedstone",false);tag.setBoolean("SegmentedSignalOpens",(code&1)!=0);return tag;}
    private static void connectedUndo(SegmentedDoorBlock block){
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);java.util.List<SegmentedDoorTile> tiles=placed(world,block,2,2,EnumFacing.NORTH,0);for(SegmentedDoorTile tile:tiles)tile.settings=SegmentedDoorSettings.DEFAULT;
        EntityPlayer player=actor(world);JsonArray before=captures(world,tiles);net.minecraft.item.ItemStack tool=new net.minecraft.item.ItemStack(new com.vandorlabs.items.ItemDuplifier());NBTTagCompound values=new NBTTagCompound(),configuration=new NBTTagCompound();new SegmentedDoorSettings(3,true,2,0,true,2,true,new DoorGlassAppearance(-1,2,1,2),2).write(configuration);configuration.setInteger("SegmentedSpeed",7);values.setTag("segmented_door",configuration);values.setInteger("redstone_channel",31);tool.setTagInfo(com.vandorlabs.items.ItemDuplifier.SETTINGS_TAG,values);
        com.vandorlabs.items.DuplifierUndo.begin(player,world);int applied;try{applied=com.vandorlabs.items.DuplifierConnectedApply.apply(world,BASE,tool,player,EnumFacing.NORTH);}finally{com.vandorlabs.items.DuplifierUndo.finish(player);}
        if(applied!=4)throw new AssertionError("Connected section count="+applied);if(!com.vandorlabs.items.DuplifierUndo.undo(player))throw new AssertionError("Connected Undo refused");JsonArray after=captures(world,tiles);if(!before.equals(after))throw new AssertionError("Connected Undo lost original shared settings: before="+before+" after="+after);for(SegmentedDoorTile tile:tiles)tile.onChunkUnload();System.out.println("Copifier Segmented connected Undo PASS four sections exact original choices");
    }
}
