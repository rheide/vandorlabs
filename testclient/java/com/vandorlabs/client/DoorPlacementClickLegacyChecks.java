package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ItemLargeProgrammableDoor;
import com.vandorlabs.tiles.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Actual specialized/vanilla item callbacks retain the original clicked-cell fractions. */
public final class DoorPlacementClickLegacyChecks {
    private static Block find(String id){for(Block block:ModBlocks.BLOCKS)if(block.getRegistryName().getResourcePath().equals(id))return block;throw new AssertionError(id);}
    public static void main(String[] args)throws Exception {
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntitySpaceDoor.class,new ResourceLocation("vandorlabs","space_door"));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityLargeProgrammableDoor.class,new ResourceLocation("vandorlabs","large_programmable_door"));
        Block[] blocks={find("programmable_door"),find("large_programmable_door")};
        ItemBlock[] items={new ItemBlock(blocks[0]),new ItemLargeProgrammableDoor((BlockLargeProgrammableDoor)blocks[1])};
        for(int i=0;i<2;i++){net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(blocks[i]);items[i].setRegistryName(blocks[i].getRegistryName());net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(items[i]);}
        JsonArray records=new JsonArray();BlockPos center=new BlockPos(10,80,10);
        float[] hits={.1F,Math.nextDown(1F/3),1F/3,Math.nextUp(1F/3),.5F,Math.nextDown(2F/3),2F/3,Math.nextUp(2F/3),.9F};
        for(int family=0;family<2;family++)for(EnumFacing face:EnumFacing.values())for(int yaw=0;yaw<4;yaw++)for(float hit:hits)for(boolean replace:new boolean[]{false,true})for(boolean configured:new boolean[]{false,true}){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
            EntityPlayer player=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.fromString("b29c361b-a045-494d-a7ef-a8769bf54885"),"DoorClickOracle")){public boolean isSpectator(){return false;}public boolean isCreative(){return false;}};
            player.rotationYaw=EnumFacing.getHorizontal(yaw).getHorizontalAngle();
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.setBlockState(center.add(x,-1,z),net.minecraft.init.Blocks.STONE.getDefaultState(),2);
            BlockPos clicked=replace?center:center.offset(face.getOpposite());if(!replace)world.setBlockState(clicked,net.minecraft.init.Blocks.STONE.getDefaultState(),2);
            ItemStack stack=new ItemStack(items[family],2);if(configured){NBTTagCompound choices=new NBTTagCompound();choices.setInteger("SpaceDesign",7);choices.setInteger("SpaceDoorPlacementDepth",2);stack.setTagInfo("SpaceDoorSettings",choices);}player.setHeldItem(EnumHand.MAIN_HAND,stack);
            float x=face.getAxis()==EnumFacing.Axis.X?(face==EnumFacing.EAST?1:0):hit,y=face.getAxis()==EnumFacing.Axis.Y?(face==EnumFacing.UP?1:0):hit,z=face.getAxis()==EnumFacing.Axis.Z?(face==EnumFacing.SOUTH?1:0):hit;
            EnumActionResult result=items[family].onItemUse(player,world,clicked,EnumHand.MAIN_HAND,face,x,y,z);boolean success=result==EnumActionResult.SUCCESS;
            JsonObject row=new JsonObject();row.addProperty("family",family);row.addProperty("face",face.getIndex());row.addProperty("yaw",yaw);row.addProperty("x",x);row.addProperty("y",y);row.addProperty("z",z);row.addProperty("replace",replace);row.addProperty("configured",configured);row.addProperty("success",success);row.addProperty("remaining",stack.getCount());
            if(success){EnumFacing front=EnumFacing.getHorizontal(yaw).getOpposite();BlockPos root=family==0?center:center.offset(front.rotateYCCW(),-1);TileEntitySpaceDoor tile=(TileEntitySpaceDoor)world.getTileEntity(root);if(tile==null)throw new AssertionError("Missing actual placed owner "+row);row.addProperty("depth",tile.getPlacementDepth());row.addProperty("facing",world.getBlockState(root).getValue(family==0?BlockVandorDoor.FACING:BlockLargeProgrammableDoor.FACING).getIndex());if(family==0)row.addProperty("rightHinge",world.getBlockState(root).getValue(BlockVandorDoor.HINGE)==BlockDoor.EnumHingePosition.RIGHT);}
            records.add(row);com.vandorlabs.redstone.RedstoneChannels.worldUnloaded(new net.minecraftforge.event.world.WorldEvent.Unload(world));
        }
        byte[] bytes=new Gson().toJson(records).getBytes(StandardCharsets.UTF_8);Path output=Paths.get(args[0]);Files.createDirectories(output.toAbsolutePath().getParent());Files.write(output,bytes);StringBuilder hash=new StringBuilder();for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(Locale.ROOT,"%02x",value&255));if(records.size()!=1728||!hash.toString().equals("51f15a499523de44b43b86863ea36ee29acd5c6552154d27a9e45e8eaa093587"))throw new AssertionError("Released Door item callbacks changed "+hash);System.out.println("Actual Door original-click item callbacks PASS cases="+records.size()+" golden="+hash);
    }
}
