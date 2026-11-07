package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.TileEntityRampController;
import java.nio.file.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
/** Real admission and commit revision under explicit actor contexts; platforms remain empty. */
public final class RampAdmissionLegacyOracle {
 private static final BlockPos POS=new BlockPos(4,80,4);
 private static final String[] CURRENT={"{}","{ControllerVersion:9,StartOffsetHalfSteps:-3,EndOffsetHalfSteps:7,TreadPixels:4,ActivateOnPower:1b,TravelAxis:2}","{ControllerVersion:9,StartOffsetHalfSteps:5,EndOffsetHalfSteps:-9,TreadPixels:8,ActivateOnPower:1b,EscalatorMode:2,Elevator:1b,MoatMode:1b,RampDirection:1}"};
 private static final String[] EDITS={"{}","{ramp_speed:2}","{ramp_speed:1}","{ramp_start_half:1}","{ramp_direction:0}","{ramp_vertical_layout:1b}","{ramp_tread_pixels:3}","{ramp_escalator:1}","{ramp_start_half:33,redstone_channel:[I;23]}","{ramp_start:2,ramp_end:-5,ramp_start_half:-3}","{redstone_trigger:2}","{ramp_lift:1b}","{ramp_travel:1,ramp_extend:1b}","{ramp_start_half:\"wrong\",ramp_tread_pixels:4}"};
 public static void main(String[] args)throws Exception{List<BlockRampController> blocks=RampSettingsLegacyOracle.initialize();List<String> lines=new ArrayList<>();for(BlockRampController block:blocks)for(int meta=0;meta<16;meta++)for(int profile=0;profile<CURRENT.length;profile++)for(int context=0;context<7;context++)for(String text:EDITS){
  final int actorContext=context;NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(context==5);world.time=100;world.setBlockState(POS,block.getStateFromMeta(meta),2);TileEntityRampController owner=(TileEntityRampController)world.getTileEntity(POS);if(profile>0){NBTTagCompound raw=JsonToNBT.getTagFromJson(CURRENT[profile]);raw.setInteger("x",4);raw.setInteger("y",80);raw.setInteger("z",4);owner.readFromNBT(raw);}
  EntityPlayer player=context==6?null:new EntityPlayer(world,new com.mojang.authlib.GameProfile(new UUID(0,77),"RampOracle")){public boolean isCreative(){return true;}public boolean isSpectator(){return actorContext==4;}public boolean canPlayerEdit(BlockPos p,net.minecraft.util.EnumFacing face,ItemStack held){return actorContext!=3;}};
  if(player!=null){player.capabilities.allowEdit=true;player.setPosition(context==1||context==2?104.5:4.5,80,3);if(context==2){ItemStack tool=new ItemStack(ModItems.DUPLIFIER);DuplifierApplyOptions.setConnected(tool,true);player.setHeldItem(EnumHand.MAIN_HAND,tool);}}
  JsonObject row=new JsonObject();row.addProperty("block",block.getRegistryName().getResourcePath());row.addProperty("meta",meta);row.addProperty("profile",profile);row.addProperty("context",context);row.addProperty("values",text);row.add("before",RampSettingsLegacyOracle.snapshot(world,block,owner,player));row.addProperty("revisionBefore",owner.configurationRevision());NBTTagCompound values=JsonToNBT.getTagFromJson(text);row.addProperty("applied",ProgrammableSettings.apply(world,POS,values,player));row.addProperty("revisionAfter",owner.configurationRevision());row.add("after",RampSettingsLegacyOracle.snapshot(world,block,owner,player));if(!values.toString().equals(JsonToNBT.getTagFromJson(text).toString())||owner.area()!=0||!owner.vehicleCells().isEmpty())throw new AssertionError("Clipboard mutation or unexpected ownership");lines.add(row.toString());RampSettingsLegacyOracle.retire(world);
 }
 if(lines.size()!=9408)throw new AssertionError("Admission coverage");Path path=Paths.get(args[0]);Files.createDirectories(path.getParent());Files.write(path,lines,java.nio.charset.StandardCharsets.UTF_8);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(!hash.toString().equals("5a133c29471f458f39e913c7472c7fa63a0b66e8b4850184140a6b8a790a7656"))throw new AssertionError("Changed Source admission golden "+hash);System.out.println("Ramp Source admission PASS cases="+lines.size()+" near/distant/connected/claims/spectator/remote/nullActor; revision distinguishes commit from activation; no receipt or journal proof");}
}
