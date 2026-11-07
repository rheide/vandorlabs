package com.vandorlabs.client;

import com.vandorlabs.segmented.*;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;

/** Focused actual placed ownership, prepared-context and ordinary behind-gap interaction. */
final class SegmentedGateLiveChecks {
 private static final BlockPos BASE=new BlockPos(1480,100,1440),LEVER=BASE.east().up().south();
 private static int stage,ticks,checks;private static double eyeX,eyeY;
 private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Segmented gate stage="+stage+": "+why);}
 private static int choice(String id){for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(id.equals(ScreenHousingTextures.IDS[i]))return ScreenHousingTextures.choiceAt(i);throw new AssertionError("Missing catalog "+id);}
 static boolean tick(Minecraft mc,java.io.File output)throws Exception{
  if(stage==8)return true;
  if(mc.world==null||mc.player==null||mc.getIntegratedServer()==null)return false;
  if(stage==0){mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;mc.getIntegratedServer().addScheduledTask(()->{
   EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());net.minecraft.world.WorldServer world=actor.getServerWorld();
   for(BlockPos p:BlockPos.getAllInBox(BASE.add(-2,-2,-3),BASE.add(20,5,5)))world.setBlockToAir(p);
   net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:segmented_door");
   for(int style=0;style<3;style++)for(int column=0;column<3;column++)for(int row=0;row<3;row++)world.setBlockState(BASE.add(style*5+column,row,0),block.getDefaultState().withProperty(SegmentedDoorBlock.FACING,EnumFacing.NORTH),3);
   for(int i=0;i<6;i++)DeferredTileLoad.flush(world);
   SegmentedDoorSettings latent=new SegmentedDoorSettings(0,false,1,0,true,2,true,SegmentedDoorSettings.DEFAULT.glass);
   for(int style=0;style<3;style++){
    int selected=choice(SegmentedArtworkPolicy.gateId(style));need(SegmentedDoorTile.validTexture(selected)&&!ScreenHousingTextures.generalTexture(selected),"gate is Segmented-only choice");
    SegmentedDoorTile root=SegmentedDoorTile.at(world,BASE.add(style*5,2,0));root.configureAll(actor,com.vandorlabs.redstone.ChannelList.EMPTY,10,false,true,selected,latent);
    for(int column=0;column<3;column++){SegmentedDoorTile cell=SegmentedDoorTile.at(world,BASE.add(style*5+column,1,0));need(cell.settings.window&&cell.settings.join&&!cell.canBake(),"latent window survives model selection and model stays complex");net.minecraft.nbt.NBTTagCompound saved=cell.writeToNBT(new net.minecraft.nbt.NBTTagCompound());need(saved.getInteger("SegmentedTexture")==selected&&cell.itemSettings().getInteger("SegmentedTexture")==selected,"saved/configured artwork survives projection");}
   }
   for(int column=0;column<3;column++)for(int row=0;row<3;row++)world.setBlockState(BASE.add(15+column,row,0),block.getDefaultState().withProperty(SegmentedDoorBlock.FACING,EnumFacing.NORTH),3);for(int i=0;i<6;i++)DeferredTileLoad.flush(world);
   SegmentedDoorTile.at(world,BASE.add(15,2,0)).configureAll(actor,com.vandorlabs.redstone.ChannelList.EMPTY,10,false,true,choice(SegmentedArtworkPolicy.tileId(2)),SegmentedDoorSettings.DEFAULT);
   world.setBlockState(LEVER.south(),Blocks.STONE.getDefaultState(),3);world.setBlockState(LEVER,Blocks.LEVER.getDefaultState().withProperty(net.minecraft.block.BlockLever.FACING,net.minecraft.block.BlockLever.EnumOrientation.NORTH),3);
   boolean found=false;for(int x=6;x<=10&&!found;x++)for(int y=6;y<=10&&!found;y++){double px=BASE.getX()+1+x/16D,py=BASE.getY()+1+y/16D;RayTraceResult hit=world.rayTraceBlocks(new Vec3d(px,py,BASE.getZ()-1),new Vec3d(px,py,BASE.getZ()+3));if(hit!=null&&LEVER.equals(hit.getBlockPos())){eyeX=px;eyeY=py;found=true;}}
   need(found,"actual world ray traverses authored Picket gap and finds the lever behind");actor.setGameType(GameType.CREATIVE);actor.inventory.clear();actor.inventory.currentItem=0;actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.capabilities.allowFlying=true;actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.connection.setPlayerLocation(eyeX,eyeY-actor.getEyeHeight(),BASE.getZ()-1,0,0);actor.sendContainerToPlayer(actor.inventoryContainer);
  }).get();mc.playerController.setGameType(GameType.CREATIVE);mc.player.inventory.currentItem=0;stage=1;ticks=0;return false;}
  need(++ticks<400,"synchronization timeout mode="+mc.playerController.getCurrentGameType()+" held="+mc.player.getHeldItemMainhand()+" position="+mc.player.getPositionVector());
  if(stage==1){SegmentedDoorTile root=SegmentedDoorTile.at(mc.world,BASE.east().up(2));if(root==null||!root.ready()||root.effectiveTexture()!=choice(SegmentedArtworkPolicy.gateId(0))||mc.player.getDistanceSq(eyeX,eyeY-mc.player.getEyeHeight(),BASE.getZ()-1)>.03||!mc.player.getHeldItemMainhand().isEmpty()||mc.player.inventory.currentItem!=0)return false;
   for(int style=0;style<3;style++){SegmentedDoorTile part=SegmentedDoorTile.at(mc.world,BASE.add(style*5+1,2,0));if(part==null||!part.ready()||part.effectiveTexture()!=choice(SegmentedArtworkPolicy.gateId(style)))return false;SegmentedGateContexts.Prepared prepared=SegmentedGateContexts.prepare(part);need(prepared!=null&&prepared.context!=null&&prepared.kind(1)==style,"actual installed column publishes prepared model rows");}
   SegmentedDoorTile glazing=SegmentedDoorTile.at(mc.world,BASE.add(16,2,0));if(glazing==null||!glazing.ready()||glazing.effectiveTexture()!=choice(SegmentedArtworkPolicy.tileId(2)))return false;
   SegmentedGateContexts.Prepared first=SegmentedGateContexts.prepare(root);long reads=SegmentedGateContexts.loadedReads(),builds=SegmentedGateContexts.builds();for(int i=0;i<10000;i++)need(SegmentedGateContexts.prepare(root)==first,"warm context identity");need(reads==SegmentedGateContexts.loadedReads()&&builds==SegmentedGateContexts.builds(),"warm preparation performs no topology reads/rebuilds");
   SegmentedGateBlendChecks.run(mc,BASE,output);
   Vec3d start=mc.player.getPositionEyes(1),end=start.addVector(0,0,5);RayTraceResult hit=mc.world.rayTraceBlocks(start,end);need(hit!=null&&LEVER.equals(hit.getBlockPos()),"real client world ray passes the gap instead of the broad hover/physical leaf");mc.playerController.processRightClickBlock(mc.player,mc.world,LEVER,hit.sideHit,hit.hitVec,EnumHand.MAIN_HAND);stage=2;ticks=0;return false;
  }
  if(stage==2){if(ticks<5)return false;boolean powered=mc.getIntegratedServer().callFromMainThread(()->mc.getIntegratedServer().getWorld(0).getBlockState(LEVER).getValue(net.minecraft.block.BlockLever.POWERED)).get();if(!powered||!mc.world.getBlockState(LEVER).getValue(net.minecraft.block.BlockLever.POWERED))return false;
   mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));boolean bar=false;for(int x=1;x<16&&!bar;x++){double px=BASE.getX()+1+x/16D;RayTraceResult pointed=actor.world.rayTraceBlocks(new Vec3d(px,eyeY,BASE.getZ()-1),new Vec3d(px,eyeY,BASE.getZ()+3));if(pointed!=null&&pointed.getBlockPos().equals(BASE.east().up())){eyeX=px;bar=true;}}need(bar,"visible authored bar available for actual Configurizer click");actor.connection.setPlayerLocation(eyeX,eyeY-actor.getEyeHeight(),BASE.getZ()-1,0,0);actor.sendContainerToPlayer(actor.inventoryContainer);}).get();stage=3;ticks=0;return false;
  }
  if(stage==3){if(mc.player.getHeldItemMainhand().getItem()!=com.vandorlabs.items.ModItems.CONFIGURIZER||mc.player.getDistanceSq(eyeX,eyeY-mc.player.getEyeHeight(),BASE.getZ()-1)>.03)return false;Vec3d start=mc.player.getPositionEyes(1);RayTraceResult pointed=mc.world.rayTraceBlocks(start,start.addVector(0,0,5));need(pointed!=null&&pointed.getBlockPos().equals(BASE.east().up()),"actual synchronized eye ray hits a visible authored bar before requesting configuration");mc.playerController.processRightClickBlock(mc.player,mc.world,pointed.getBlockPos(),pointed.sideHit,pointed.hitVec,EnumHand.MAIN_HAND);stage=4;ticks=0;return false;}
  if(stage==4){if(!(mc.currentScreen instanceof GuiSegmentedDoor)||ticks<4)return false;GuiSegmentedDoor gui=(GuiSegmentedDoor)mc.currentScreen;
   java.lang.reflect.Field buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.client.gui.GuiScreen.class,"buttonList","field_146292_n");@SuppressWarnings("unchecked") java.util.List<net.minecraft.client.gui.GuiButton> controls=(java.util.List<net.minecraft.client.gui.GuiButton>)buttons.get(gui);net.minecraft.client.gui.GuiButton direction=null,scope=null;
   for(net.minecraft.client.gui.GuiButton button:controls){need(button.id<8||button.id>11,"gate dialog hides only inapplicable layout/window/shape/join controls");need(button.id!=93,"gate dialog hides latent glass tab");if(button.id==6)direction=button;if(button.id==15)scope=button;}
   need(direction!=null&&scope!=null&&scope.displayString.contains("This segment"),"real dedicated gate dialog retains motion and explicit local artwork scope");
   java.lang.reflect.Field textures=GuiSegmentedDoor.class.getDeclaredField("textures");textures.setAccessible(true);HousingTextureList list=(HousingTextureList)textures.get(gui);java.lang.reflect.Field groups=HousingTextureList.class.getDeclaredField("groups");groups.setAccessible(true);@SuppressWarnings("unchecked") java.util.Map<String,java.util.List<HousingTextureList.Option>> grouped=(java.util.Map<String,java.util.List<HousingTextureList.Option>>)groups.get(list);need(grouped.containsKey(SegmentedArtworkPolicy.CATEGORY)&&grouped.get(SegmentedArtworkPolicy.CATEGORY).size()==9,"new and preserved/default artwork in one Segmented category");
   gui.mouseClicked(scope.x+4,scope.y+4,1);controls=(java.util.List<net.minecraft.client.gui.GuiButton>)buttons.get(gui);for(net.minecraft.client.gui.GuiButton button:controls)if(button.id==15){need(button.displayString.contains("All segments"),"right click reverses explicit whole-door artwork scope");gui.mouseClicked(button.x+4,button.y+4,0);break;}
   direction=null;controls=(java.util.List<net.minecraft.client.gui.GuiButton>)buttons.get(gui);for(net.minecraft.client.gui.GuiButton button:controls)if(button.id==6)direction=button;need(direction!=null,"motion control persists after scope change");gui.mouseClicked(direction.x+4,direction.y+4,1);java.lang.reflect.Field settings=GuiSegmentedDoor.class.getDeclaredField("settings");settings.setAccessible(true);need(((SegmentedDoorSettings)settings.get(gui)).direction==3,"right click selects previous movement direction");controls=(java.util.List<net.minecraft.client.gui.GuiButton>)buttons.get(gui);for(net.minecraft.client.gui.GuiButton button:controls)if(button.id==6){gui.mouseClicked(button.x+4,button.y+4,0);break;}
   SegmentedDoorDialogChecks.save(mc,gui,output,"supplied_gate");stage=5;ticks=0;return false;
  }
  if(stage==5){if(ticks<5)return false;boolean restored=mc.getIntegratedServer().callFromMainThread(()->{SegmentedDoorTile tile=SegmentedDoorTile.at(mc.getIntegratedServer().getWorld(0),BASE.east().up());return tile.settings.direction==0&&tile.settings.window&&tile.settings.shape==2&&tile.settings.join&&tile.effectiveTexture()==choice(SegmentedArtworkPolicy.gateId(0));}).get();if(!restored)return false;mc.displayGuiScreen(null);stage=6;ticks=0;return false;}
  if(stage==6){if(ticks<10)return false;Object previous=com.vandorlabs.client.segmented.SegmentedDoorRenderer.currentGateBake();mc.refreshResources();need(previous!=null&&previous!=com.vandorlabs.client.segmented.SegmentedDoorRenderer.currentGateBake(),"actual Source reload replaces the gate bake");stage=7;ticks=0;return false;}
  if(stage==7){if(ticks<5)return false;SegmentedGateBlendChecks.run(mc,BASE,output);System.out.println("[vandorlabs][reprolab] segmented-gates-live PASS checks="+checks+" actual3styles/latentWindow/savedItemProjection/warm10000NoReads/behindGapWorldRay/realLeverPacket/dedicatedConfigurator/pickerCategory/reverseCycles/exactResourceReload");System.out.println("[vandorlabs][reprolab] utility26-focused PASS segmented-gates-only");stage=8;return true;}

  return false;
 }
}
