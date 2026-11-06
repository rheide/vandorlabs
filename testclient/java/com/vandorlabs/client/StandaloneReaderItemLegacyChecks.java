package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import java.nio.file.*;

/** Actual pick/drop and placement callbacks: configured readers never copy physical tokens. */
public final class StandaloneReaderItemLegacyChecks {
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(ModItems.ACCESS_CARD);net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(ModItems.DATA_CHIP);
        java.lang.reflect.Field field=Item.class.getDeclaredField("BLOCK_TO_ITEM");field.setAccessible(true);java.util.Map<net.minecraft.block.Block,Item> items=(java.util.Map<net.minecraft.block.Block,Item>)field.get(null);
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(0,80,0);
        EntityPlayer player=new EntityPlayer(world,new com.mojang.authlib.GameProfile(new java.util.UUID(0,49),"ReaderPlacement")){public boolean isSpectator(){return false;}public boolean isCreative(){return true;}};
        JsonArray rows=new JsonArray();
        for(boolean chip:new boolean[]{false,true}) {
            BlockTokenSlot block=new BlockTokenSlot(chip?"data_chip_slot":"access_card_slot",chip);ItemBlock item=new ItemBlock(block);items.put(block,item);
            for(EnumFacing face:EnumFacing.values())for(int yaw=0;yaw<4;yaw++)for(int mount=0;mount<64;mount++) {
                player.rotationYaw=yaw*90;NBTTagCompound config=new NBTTagCompound();config.setInteger("Channel",27);config.setIntArray("RedstoneChannels",new int[]{27,31});config.setInteger("BaseHeight",mount%4);config.setInteger("BaseTilt",mount/4%4);config.setInteger("TiltDirection",mount/16);config.setInteger("ControlMountVersion",1);
                ItemStack held=new ItemStack(item);held.setTagInfo("RedstoneChannelSettings",config);
                net.minecraft.block.state.IBlockState state=block.getStateForPlacement(world,pos,face,.2F,.4F,.8F,0,player,EnumHand.MAIN_HAND);world.setBlockState(pos,state,2);block.onBlockPlacedBy(world,pos,state,player,held);
                TileEntityTokenSlot tile=(TileEntityTokenSlot)world.getTileEntity(pos);tile.configure(true,127,0);tile.setInventorySlotContents(0,new ItemStack(chip?ModItems.DATA_CHIP:ModItems.ACCESS_CARD,1,2));
                ItemStack picked=block.getPickBlock(state,new RayTraceResult(new Vec3d(.5,80.5,.5),face,pos),world,pos,player);NonNullList<ItemStack> drops=NonNullList.create();block.getDrops(drops,world,pos,state,0);
                NBTTagCompound choices=picked.getSubCompound("RedstoneChannelSettings");if(choices==null||choices.hasKey("InsertedToken")||choices.hasKey("TokenBehavior")||choices.hasKey("AnyToken")||choices.hasKey("MountRotation")||!choices.equals(drops.get(0).getSubCompound("RedstoneChannelSettings")))throw new AssertionError("Reader configured pick/drop ownership");
                JsonObject row=new JsonObject();row.addProperty("chip",chip);row.addProperty("face",face.getIndex());row.addProperty("yaw",yaw);row.addProperty("mount",mount);row.addProperty("rotation",tile.getMountRotation());row.addProperty("choices",choices.toString());row.addProperty("owned",tile.getStackInSlot(0).getCount());rows.add(row);
            }
        }
        String serialized=new Gson().toJson(rows);java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");StringBuilder hash=new StringBuilder();for(byte b:digest.digest(serialized.getBytes("UTF-8")))hash.append(String.format("%02x",b&255));if(!hash.toString().equals("b1db97a6069763e1147c4ad19b1c40d12c3c135502a5fd71405d644f4cf93a6a"))throw new AssertionError("Released reader item callbacks changed "+hash);JsonObject root=new JsonObject();root.addProperty("schema",1);root.addProperty("golden",hash.toString());root.add("cases",rows);Path output=Paths.get(System.getProperty("vandorlabs.standaloneReaderItemReference","build/test-artifacts/standalone-reader-item-forge.json"));Files.createDirectories(output.toAbsolutePath().getParent());try(java.io.Writer writer=Files.newBufferedWriter(output)){new Gson().toJson(root,writer);}System.out.println("Actual standalone reader item callbacks PASS cases="+rows.size()+" golden="+hash);
    }
}
