package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Actual clicked-wall/trapdoor same-plane selection plus post-pair hinge/rotation inheritance. */
public final class TrapdoorContactPlacementLegacyOracle {
 public static void main(String[] args)throws Exception{ItemBlock item=TrapdoorPlacementLegacyOracle.initialize()[1];BlockProgrammableDiagonalTrapdoor block=(BlockProgrammableDiagonalTrapdoor)item.getBlock();BlockProgrammableWall wall=(BlockProgrammableWall)ModBlocks.PROGRAMMABLE_DIAGONAL_WALL;Path output=Paths.get(args[0]);Files.createDirectories(output.toAbsolutePath().getParent());java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");int profiles=0;try(java.io.BufferedWriter writer=Files.newBufferedWriter(output,StandardCharsets.UTF_8)){
  for(int supportKind=0;supportKind<7;supportKind++)for(int mode=0;mode<3;mode++)for(EnumFacing side:EnumFacing.values())for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(int configured=0;configured<4;configured++){
   NonRenderingChecks.MemoryWorld w=new NonRenderingChecks.MemoryWorld(false);BlockPos target=new BlockPos(10,80,10),support=target.offset(side.getOpposite());boolean trapdoor=supportKind!=0;
   if(!trapdoor){w.setBlockState(support,wall.getDefaultState().withProperty(BlockProgrammableWall.FACING,face).withProperty(BlockProgrammableWall.INVERTED,inverted),2);((TileEntityAnimatedScreenSelector)w.getTileEntity(support)).setDiagonalGeometry(mode,0);}else{w.setBlockState(support,block.getDefaultState().withProperty(BlockTrapDoor.FACING,face).withProperty(BlockTrapDoor.HALF,inverted?BlockTrapDoor.DoorHalf.TOP:BlockTrapDoor.DoorHalf.BOTTOM),2);NBTTagCompound n=new NBTTagCompound();n.setInteger("x",support.getX());n.setInteger("y",support.getY());n.setInteger("z",support.getZ());n.setInteger("housingTexture",0);n.setInteger("TrapdoorPosition",mode);n.setInteger("TrapdoorTrigger",0);n.setInteger("DiagonalHingeSide",(supportKind-1)%3);n.setBoolean("DiagonalRotationFlipped",supportKind>3);n.setInteger("DiagonalFacing",face.getHorizontalIndex());n.setBoolean("DiagonalInverted",inverted);((TileEntityProgrammableDiagonalTrapdoor)w.getTileEntity(support)).readFromNBT(n);}
   EntityPlayer p=new EntityPlayer(w,new com.mojang.authlib.GameProfile(UUID.fromString("fcf30a04-a028-4e19-8cf4-02477b6ef0cd"),"TrapdoorContactOracle")){public boolean isSpectator(){return false;}public boolean isCreative(){return true;}};p.rotationYaw=face.getHorizontalAngle();ItemStack held=new ItemStack(item);NBTTagCompound tag=null;if(configured!=0){tag=new NBTTagCompound();if(configured==1)tag.setInteger("TrapdoorPosition",mode);if(configured==2)tag.setInteger("DiagonalHingeSide",2);if(configured==3)tag.setBoolean("DiagonalRotationFlipped",true);held.setTagInfo("BlockEntityTag",tag);}p.setHeldItem(EnumHand.MAIN_HAND,held);
   JsonObject record=new JsonObject();record.addProperty("supportKind",supportKind);record.addProperty("mode",mode);record.addProperty("side",side.getIndex());record.addProperty("facing",face.getHorizontalIndex());record.addProperty("inverted",inverted);record.addProperty("configured",configured);record.addProperty("input",tag==null?"":tag.toString());net.minecraft.block.state.IBlockState state=block.getStateForPlacement(w,target,side,.1F,.7F,.9F,0,p,EnumHand.MAIN_HAND);record.addProperty("initialFacing",state.getValue(BlockTrapDoor.FACING).getHorizontalIndex());record.addProperty("initialTop",state.getValue(BlockTrapDoor.HALF)==BlockTrapDoor.DoorHalf.TOP);if(!item.placeBlockAt(held,p,w,target,side,.1F,.7F,.9F,state))throw new AssertionError("actual contact placement");record.add("placed",TrapdoorPlacementLegacyOracle.snapshot(w,target));if(trapdoor)record.add("support",TrapdoorPlacementLegacyOracle.snapshot(w,support));String line=record.toString()+"\n";writer.write(line);digest.update(line.getBytes(StandardCharsets.UTF_8));profiles++;com.vandorlabs.redstone.RedstoneChannels.worldUnloaded(new net.minecraftforge.event.world.WorldEvent.Unload(w));
  }
 }StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));if(profiles!=4032||!hash.toString().equals("3e3e18e79f8a1ee5fdd22e50c10f34d7a6bc1fe3b00511aa77bf7e6aec3290f5"))throw new AssertionError("Released Trapdoor contact placement changed "+hash);System.out.println("Actual Trapdoor contact placement PASS profiles="+profiles+" golden="+hash);
 }
}
