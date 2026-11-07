package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.*;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.io.*;
import java.util.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Actual released all-member ticks, manual commands, power edges and helper-wait retry. */
public final class CanopyMotionLegacyOracle {
    private static final BlockPos ROOT=new BlockPos(8,80,8);
    private static JsonArray pos(BlockPos p){JsonArray a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;}
    private static JsonObject snapshot(CanopyApplyLegacyOracle.World world,TileEntityCanopy root){JsonObject out=new JsonObject();out.add("saved",CanopySettingsLegacyOracle.tags(root.writeToNBT(new NBTTagCompound())));JsonArray cells=new JsonArray();for(BlockPos cell:root.cells()){JsonObject c=new JsonObject();c.add("pos",pos(cell));c.addProperty("released",root.isReleasedCell(cell));c.addProperty("id",world.getBlockState(cell).getBlock().getRegistryName().toString());cells.add(c);}out.add("cells",cells);return out;}
    private static void capture(PrintWriter out,Gson gson,BlockCanopy block,EnumFacing facing,int mount,int columns,int pattern){
        CanopyApplyLegacyOracle.World world=new CanopyApplyLegacyOracle.World();world.time=1;TileEntityCanopy root=CanopyApplyLegacyOracle.setup(world,block,facing,mount,true,columns,pattern==3?1:0,pattern==3);
        for(TileEntity raw:world.tiles.values())if(raw instanceof TileEntityCanopy){TileEntityCanopy member=(TileEntityCanopy)raw;member.large.redstone=pattern==1||pattern==2;member.large.activateOnPower=pattern!=2;member.large.channels=com.vandorlabs.redstone.ChannelList.of(2,7);}
        JsonObject row=new JsonObject();row.addProperty("id",block.kind);row.addProperty("facing",facing.getIndex());row.addProperty("mount",mount);row.addProperty("columns",columns);row.addProperty("pattern",pattern);row.add("initial",snapshot(world,root));JsonArray members=new JsonArray();List<BlockPos> positions=new ArrayList<>(world.tiles.keySet());positions.sort(Comparator.comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));for(BlockPos cell:positions){TileEntity raw=world.tiles.get(cell);if(raw instanceof TileEntityCanopy){JsonObject m=new JsonObject();m.add("pos",pos(cell));m.add("saved",CanopySettingsLegacyOracle.tags(raw.writeToNBT(new NBTTagCompound())));members.add(m);}}row.add("members",members);
        BlockPos foreign=null;if(pattern==3)for(BlockPos cell:root.cells())if(root.isReleasedCell(cell)){foreign=cell;break;}if(foreign!=null)row.add("foreign",pos(foreign));JsonArray ticks=new JsonArray();
        for(int tick=0;tick<40;tick++){
            int action=0;
            if(pattern==0&&(tick==0||tick==9||tick==14||tick==34)||pattern==1&&tick==8||pattern==2&&tick==24||pattern==3&&(tick==0||tick==18||tick==28))action=1;
            if(pattern==1&&(tick==0||tick==9||tick==24)||pattern==2&&(tick==8||tick==32))action=2;
            if(pattern==1&&(tick==16||tick==32)||pattern==2&&(tick==0||tick==16||tick==25))action=3;
            if(pattern==3&&tick==0&&foreign!=null)world.setBlockState(foreign,Blocks.STONE.getDefaultState(),2);
            if(pattern==3&&tick==8){if(foreign!=null)world.setBlockState(foreign,Blocks.AIR.getDefaultState(),2);root.retryReservationClose();}
            if(action==1)root.toggle();if(action==2)root.setChannelSignal(true);if(action==3)root.setChannelSignal(false);
            world.time++;List<TileEntity> all=new ArrayList<>(world.tiles.values());for(TileEntity raw:all)if(raw instanceof TileEntityCanopy&&!raw.isInvalid()&&world.tiles.get(raw.getPos())==raw)((TileEntityCanopy)raw).update();
            JsonObject frame=snapshot(world,root);frame.addProperty("tick",tick);frame.addProperty("action",action);ticks.add(frame);
        }
        row.add("ticks",ticks);out.println(gson.toJson(row));net.minecraftforge.event.world.WorldEvent.Unload unload=new net.minecraftforge.event.world.WorldEvent.Unload(world);CanopyReservations.unload(unload);com.vandorlabs.redstone.RedstoneChannels.worldUnloaded(unload);com.vandorlabs.world.LoadedTileIndex.Lifecycle.unload(unload);
    }
    public static void main(String[] args)throws Exception{List<BlockCanopy> blocks=CanopyPlacementLegacyOracle.initialize();com.vandorlabs.VandorLabs.logger=org.apache.logging.log4j.LogManager.getLogger("CanopyMotionReference");File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0;try(PrintWriter out=new PrintWriter(file,"UTF-8")){for(BlockCanopy block:blocks)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int mount=0;mount<(block.opening()||LargeCanopySettings.supports(block.kind)?4:1);mount++)for(int columns:new int[]{1,3}){if(columns==3&&!block.opening())continue;for(int pattern=0;pattern<4;pattern++){capture(out,gson,block,facing,mount,columns,pattern);profiles++;}}}SegmentedReferenceGolden.check(file,"2c32764a6dd30e26a90b90786c60275032e14ee0ef7edfe10c67870710babe86");System.out.println("Actual canopy motion PASS profiles="+profiles+" ticks="+(profiles*40)+" file="+file);}
}
