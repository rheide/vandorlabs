package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ItemCatalogBlock;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Actual configured pick/drop projection after vanilla's fresh-save item merge. */
public final class ScreenItemLegacyChecks {
 private static final String[] INPUTS={
  "{}",
  "{housingTexture:14,HousingTextureVersion:1,ChannelLevel:12,ChannelSignal:1b,JoinedSignalLevel:9,LightGroupPowered:1b,RedstoneChannels:[I;17,19]}",
  "{redstoneEnabled:1b,displayMode:2,RedstoneInverted:1b,animationSpeedIndex:3,framed:0b,smallInput:1b}",
  "{RedstonePrimary:{Enabled:1b,Title:\"ACTIVE PANEL\",Rows:[{Label:\"PULSE\",RedstoneChannels:[I;17,19],Pulse:1b,Slider:0b,Min:5,Max:15,Level:13,Levels:[I;13,7],LatchedChannels:[I;17],Active:1b,PulseUntil:1000000000L}]}}",
  "{RedstonePrimary:{Enabled:1b,Title:\"SLIDER\",Rows:[{Label:\"THROTTLE\",RedstoneChannels:[I;31,32],Pulse:0b,Slider:1b,Min:3,Max:9,Level:8,Levels:[I;8,6],LatchedChannels:[I;31,32],Active:1b}]},RedstoneSecondary:{Enabled:1b,Title:\"SECOND\",Rows:[{Label:\"TOGGLE\",RedstoneChannels:[I;33],Active:1b,Level:15,Levels:[I;15],LatchedChannels:[I;33]}]}}",
  "{RedstonePrimary:{Enabled:0b,Title:\"DORMANT\",Rows:[{Label:\"PULSE\",RedstoneChannels:[I;17],Pulse:1b,PulseUntil:1000000000L,Active:1b}]},PrimarySurface:-1,SecondarySurface:-1}",
  "{housingTexture:7,HousingTextureVersion:0,MainTextureLayout:2,SlabTileSides:1b,SurfaceTextureLayout:1,SurfaceTileSides:1b,wallPosition:2,CeilingMounted:1b,CeilingPosition:1}",
  "{FrameFront:484,FrameInterior:7,FrameExterior:14,DisplaySplitTop:7,DisplaySplitBottom:14,DisplaySplit:1b,DisplayArtworkVersion:1,FrameTile:1b,DisplayFit:1b,GlassShade:2,GlassTint:3,GlassMaterial:1,GlassTextureLayout:2}",
  "{RedstoneChannels:[I;2147483647,2000000000],ChannelLevel:7,ChannelSignal:0b,ForeignRoot:\"future-choice\"}"
 };
 private static void need(boolean value,String message){if(!value)throw new AssertionError("Source Screen item: "+message);}
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class,new ResourceLocation("minecraft:screen_item_reference"));
  JsonArray cases=new JsonArray();int rowCount=0;
  for(String id:ScreenFamilyLegacyChecks.IDS){
   BlockAnimatedScreenSelector block=null;for(net.minecraft.block.Block candidate:ModBlocks.BLOCKS)if(candidate.getRegistryName().getResourcePath().equals(id))block=(BlockAnimatedScreenSelector)candidate;need(block!=null,"source role "+id);
   net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);
   ItemBlock item=block instanceof BlockProgrammableInput?new ItemProgrammableInput((BlockProgrammableInput)block):block instanceof BlockProgrammableHalfConsole?new ItemProgrammableHalfConsole((BlockProgrammableHalfConsole)block):new ItemCatalogBlock(block);
   item.setRegistryName(block.getRegistryName());net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(item);
   for(IBlockState state:block.getBlockState().getValidStates())for(int input=0;input<INPUTS.length;input++){
    NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(0,80,0);world.setBlockState(pos,state,18);
    TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);need(tile!=null,"actual world-created tile");
    NBTTagCompound raw=JsonToNBT.getTagFromJson(INPUTS[input]),merged=tile.writeToNBT(new NBTTagCompound());merged.merge(raw);tile.readFromNBT(merged);
    ItemStack configured=block.createConfiguredDrop(tile);need(configured.getItem()==item&&configured.getCount()==1,"registered configured item "+id);NBTTagCompound expected=configured.getSubCompound("BlockEntityTag");need(expected!=null,"complete configured envelope");
    for(String runtime:new String[]{"id","x","y","z","ChannelLevel","ChannelSignal","JoinedSignalLevel","LightGroupPowered"})need(!expected.hasKey(runtime),"excluded runtime "+runtime);
    net.minecraft.util.NonNullList<ItemStack> drops=net.minecraft.util.NonNullList.create();block.getDrops(drops,world,pos,state,0);need(drops.size()==1&&ItemStack.areItemStacksEqual(configured,drops.get(0)),"actual getDrops projection "+id+"/"+block.getMetaFromState(state)+"/"+input+" tile="+(world.getTileEntity(pos)==tile)+" pick="+configured+" nbt="+configured.getTagCompound()+" drops="+drops);
    ItemStack picked=block.getPickBlock(state,new RayTraceResult(new Vec3d(.5,80.5,.5),EnumFacing.NORTH,pos),world,pos,null);need(ItemStack.areItemStacksEqual(configured,picked),"actual pick projection");
    for(String slot:new String[]{"RedstonePrimary","RedstoneSecondary"})if(expected.hasKey(slot,10)){NBTTagList rows=expected.getCompoundTag(slot).getTagList("Rows",10);for(int index=0;index<rows.tagCount();index++){NBTTagCompound row=rows.getCompoundTagAt(index);for(String runtime:new String[]{"Level","Levels","LatchedChannels","Active","PulseUntil"})need(!row.hasKey(runtime),"row configuration excludes "+runtime);rowCount++;}}
    JsonObject result=new JsonObject();result.addProperty("id",id);result.addProperty("meta",block.getMetaFromState(state));result.addProperty("input",INPUTS[input]);result.addProperty("expected",expected.toString());cases.add(result);
   }
  }
  Path target=Paths.get("build/test-artifacts/screen-item-reference.json");Files.createDirectories(target.getParent());byte[] bytes=cases.toString().getBytes(StandardCharsets.UTF_8);Files.write(target,bytes);
  StringBuilder hash=new StringBuilder();for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format("%02x",value&255));
  need(cases.size()==630&&rowCount==280&&hash.toString().equals("e47ba9f55fa4d01032e21d7b555a25854c4354cc9849b34384cf755779e1f35c"),"frozen source item corpus "+hash);
  System.out.println("Actual Screen configured item PASS cases="+cases.size()+" rows="+rowCount+" sha256="+hash+" pick/drop/runtime withdrawal");
 }
}
