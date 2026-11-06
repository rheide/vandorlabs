package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockProgrammableWall;
import com.vandorlabs.persistence.WallSettings;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Released callback coordinates are relative to the originally clicked cell. */
public final class DiagonalPlacementLegacyOracle {
    public static void main(String[] args)throws Exception {
        net.minecraft.init.Bootstrap.register();
        com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();
        JsonArray records=new JsonArray();
        BlockPos target=new BlockPos(10,80,10);
        float[] hits={.1F,Math.nextDown(1F/3),1F/3,Math.nextUp(1F/3),.5F,Math.nextDown(2F/3),2F/3,Math.nextUp(2F/3),.9F};
        for(int family=0;family<4;family++) {
            BlockProgrammableWall block=new BlockProgrammableWall("diagonal_placement_oracle_"+family,new BlockProgrammableWall.Shape[]{BlockProgrammableWall.Shape.DIAGONAL,BlockProgrammableWall.Shape.DIAGONAL_PORTHOLE,BlockProgrammableWall.Shape.PLAIN,BlockProgrammableWall.Shape.PORTHOLE}[family]);
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
            EntityPlayer player=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.fromString("e96793d1-04b0-49ba-9740-6340fb01e141"),"DiagonalPlacementOracle")){
                public boolean isSpectator(){return false;}public boolean isCreative(){return true;}
            };
            for(int mode=0;mode<3;mode++)for(EnumFacing face:EnumFacing.values())for(int yaw=0;yaw<4;yaw++)for(float hit:hits) {
                ItemStack stack=new ItemStack(net.minecraft.init.Items.STICK);
                NBTTagCompound choices=new NBTTagCompound();choices.setBoolean(WallSettings.HALF,mode==1);choices.setBoolean(WallSettings.FULL,mode==2);stack.setTagInfo("BlockEntityTag",choices);player.setHeldItem(EnumHand.MAIN_HAND,stack);
                player.rotationYaw=EnumFacing.getHorizontal(yaw).getHorizontalAngle();
                float x=face.getAxis()==EnumFacing.Axis.X?(face==EnumFacing.EAST?1:0):hit;
                float y=face.getAxis()==EnumFacing.Axis.Y?(face==EnumFacing.UP?1:0):hit;
                float z=face.getAxis()==EnumFacing.Axis.Z?(face==EnumFacing.SOUTH?1:0):hit;
                net.minecraft.block.state.IBlockState state=block.getStateForPlacement(world,target,face,x,y,z,0,player,EnumHand.MAIN_HAND);
                JsonObject row=new JsonObject();row.addProperty("family",family);row.addProperty("mode",mode);row.addProperty("face",face.getIndex());row.addProperty("yaw",yaw);row.addProperty("x",x);row.addProperty("y",y);row.addProperty("z",z);row.addProperty("facing",state.getValue(BlockProgrammableWall.FACING).getHorizontalIndex());row.addProperty("inverted",state.getValue(BlockProgrammableWall.INVERTED));row.addProperty("depth",state.getValue(BlockProgrammableWall.DEPTH));records.add(row);
            }
        }
        byte[] bytes=new Gson().toJson(records).getBytes(StandardCharsets.UTF_8);
        StringBuilder hash=new StringBuilder();for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
        if(records.size()!=2592||!hash.toString().equals("c0db1ce9096a76f416f93f97f327bf9791ebb947b83f36b1722df54e8ed8aad7"))throw new AssertionError("Released panel placement changed "+hash);
        Path output=Paths.get(args[0]);Files.createDirectories(output.toAbsolutePath().getParent());Files.write(output,bytes);
        System.out.println("Actual diagonal placement callback reference PASS profiles="+records.size()+" golden="+hash);
    }
}
