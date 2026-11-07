package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.LargeCanopySettings;
import com.vandorlabs.items.ProgrammableSettings;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.io.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Actual installed copied edits, joined-owner effects, resize refusal and settings Undo. */
public final class CopifierCanopyLegacyOracle {
    private static JsonArray cells(CanopyApplyLegacyOracle.World world){
        List<BlockPos> positions=new ArrayList<>();for(Map.Entry<BlockPos,TileEntity> entry:world.tiles.entrySet())if(entry.getValue() instanceof TileEntityCanopy)positions.add(entry.getKey());positions.sort(Comparator.comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));JsonArray result=new JsonArray();
        for(BlockPos pos:positions){JsonObject row=new JsonObject();row.addProperty("x",pos.getX());row.addProperty("y",pos.getY());row.addProperty("z",pos.getZ());row.addProperty("saved",world.tiles.get(pos).writeToNBT(new NBTTagCompound()).toString());row.addProperty("choices",ProgrammableSettings.capture(world,pos).toString());result.add(row);}return result;
    }
    private static NBTTagCompound values(int profile){
        NBTTagCompound result=new NBTTagCompound();if(profile==0){result.setInteger("glass_shade",2);result.setInteger("glass_tint",3);}else if(profile==1){NBTTagCompound config=new NBTTagCompound();config.setInteger("CanopyDepth",17);config.setInteger("CanopyScale",90);config.setInteger("CanopyHinge",2);config.setBoolean("CanopyOpenable",true);config.setInteger("CanopyMotion",1);config.setInteger("CanopyAngle",1);result.setTag("block_configuration",config);}else if(profile==2){result.setInteger("redstone_trigger",2);result.setIntArray("redstone_channel",new int[]{41,43});}else{result.setInteger("wall_texture",7);}return result;
    }
    public static void main(String[] args)throws Exception{
        List<BlockCanopy> blocks=CanopyPlacementLegacyOracle.initialize();com.vandorlabs.VandorLabs.logger=org.apache.logging.log4j.LogManager.getLogger("CopiedCanopies");JsonArray rows=new JsonArray();
        for(BlockCanopy block:blocks)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int profile=0;profile<4;profile++){
            CanopyApplyLegacyOracle.World world=new CanopyApplyLegacyOracle.World();world.time=1;int columns=block.opening()?3:1;TileEntityCanopy owner=CanopyApplyLegacyOracle.setup(world,block,facing,0,true,columns,0,false);EntityPlayer actor=new EntityPlayer(world,new com.mojang.authlib.GameProfile(new UUID(0,1903),"CopiedCanopyOracle")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}public boolean canPlayerEdit(BlockPos pos,EnumFacing side,net.minecraft.item.ItemStack stack){return true;}};
            BlockPos selected=owner.getPos();for(BlockPos pos:world.tiles.keySet())if(world.tiles.get(pos) instanceof TileEntityCanopy&&!pos.equals(owner.getPos())){selected=pos;break;}
            NBTTagCompound before=ProgrammableSettings.capture(world,selected),edit=values(profile);JsonObject row=new JsonObject();row.addProperty("id",block.kind);row.addProperty("facing",facing.getIndex());row.addProperty("profile",profile);row.addProperty("x",selected.getX());row.addProperty("y",selected.getY());row.addProperty("z",selected.getZ());row.addProperty("values",edit.toString());row.add("before",cells(world));boolean applied=ProgrammableSettings.apply(world,selected,edit,actor);row.addProperty("applied",applied);row.add("after",cells(world));if(applied)ProgrammableSettings.apply(world,selected,before,actor);row.add("undo",cells(world));rows.add(row);
        }
        if(rows.size()!=176)throw new AssertionError("Canopy copy coverage="+rows.size());File target=new File(args[0]);target.getAbsoluteFile().getParentFile().mkdirs();try(Writer writer=new OutputStreamWriter(new FileOutputStream(target),java.nio.charset.StandardCharsets.UTF_8)){new Gson().toJson(rows,writer);writer.write('\n');}SegmentedReferenceGolden.check(target,"861a889403880b76fa2dc8c71cc9ca06365bc9a32953a51ba88bfb07034c9aef");System.out.println("Actual copied Canopy world callbacks PASS cases="+rows.size()+" all11roles/allfacings/joined members/partial choices/resize/Undo");
    }
}
