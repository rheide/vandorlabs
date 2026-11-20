package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Ordinary supported item placement exposes the odd-cell split before rendering it. */
final class TrapdoorGroupRuntimeChecks {
 static final BlockPos BASE=new BlockPos(8,105,8);
 private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("trapdoor-groups: "+why);}
 static void run(World world,EntityPlayer player){
  ItemStack held=player.getHeldItemMainhand();float yaw=player.rotationYaw;boolean sneak=player.isSneaking();
  try{
   player.setSneaking(false);
   for(EnumFacing along:new EnumFacing[]{EnumFacing.EAST,EnumFacing.SOUTH})for(int position=0;position<3;position++)for(boolean sliding:new boolean[]{false,true}){
    for(BlockPos at:BlockPos.getAllInBox(BASE.add(-2,-2,-2),BASE.add(5,3,5)))world.setBlockToAir(at);
    TileEntityProgrammableTrapdoor[] leaves=new TileEntityProgrammableTrapdoor[3];
    player.rotationYaw=along.getOpposite().getHorizontalAngle();
    for(int i=0;i<3;i++){
     BlockPos at=BASE.offset(along,i);world.setBlockState(at.down(),Blocks.STONE.getDefaultState(),2);
     NBTTagCompound settings=new NBTTagCompound();settings.setInteger("housingTexture",4);settings.setInteger("TrapdoorPosition",position);settings.setBoolean("TrapdoorSliding",sliding);settings.setInteger("TrapdoorTrigger",0);
     ItemStack stack=new ItemStack(ModBlocks.PROGRAMMABLE_TRAPDOOR,2);stack.setTagInfo("BlockEntityTag",settings);player.setHeldItem(EnumHand.MAIN_HAND,stack);
     need(stack.getItem().onItemUse(player,world,at.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)==EnumActionResult.SUCCESS,"ordinary supported placement member="+i);
     leaves[i]=(TileEntityProgrammableTrapdoor)world.getTileEntity(at);
    }
    TileEntityProgrammableTrapdoor center=leaves[1];need(center.group().size()==3,"actual placement joins all three");center.setGroupOpening(0);
    need(center.writeToNBT(new NBTTagCompound()).getBoolean("TrapdoorCenterSplit"),"odd middle cell contains two geometric half-leaves");
    for(TileEntityProgrammableTrapdoor leaf:leaves)need(Math.abs(leaf.motionTravel()-(1.5-1D/16))<1e-9,"all split leaves use exactly half the group span");
    center.requestOpen(true);
    AxisAlignedBB shaft=new AxisAlignedBB(BASE.offset(along)).shrink(.25).expand(0,3,0);
    need(world.getCollisionBoxes(null,shaft).isEmpty(),"open centre has no phantom full-cell collision axis="+along+" position="+position+" sliding="+sliding+" shaft="+shaft+" hits="+world.getCollisionBoxes(null,shaft));
    Vec3d top=new Vec3d(BASE.offset(along)).addVector(.5,4,.5),bottom=top.addVector(0,-4,0);
    need(OffsetTrapdoorInteractions.trace(world,top,bottom)==null,"open centre has no phantom selected leaf");
    for(int mode:new int[]{3,4,5,6}){
     center.setGroupOpening(mode);need(center.groupOpening()==mode,"cardinal choice publishes mode="+mode);
     EnumFacing expected=mode==3?EnumFacing.NORTH:mode==4?EnumFacing.EAST:mode==5?EnumFacing.SOUTH:EnumFacing.WEST;
     for(TileEntityProgrammableTrapdoor leaf:leaves)need(world.getBlockState(leaf.getPos()).getValue(BlockProgrammableTrapdoor.FACING)==expected,"every leaf uses requested cardinal edge");
    }
    center.setGroupOpening(0);center.requestOpen(true);
    for(TileEntityProgrammableTrapdoor leaf:leaves){NBTTagCompound saved=leaf.writeToNBT(new NBTTagCompound());leaf.readFromNBT(saved);}
    need(center.writeToNBT(new NBTTagCompound()).getBoolean("TrapdoorCenterSplit")&&center.groupOpening()==0,"geometric split survives saved world data");
   }
  }finally{player.rotationYaw=yaw;player.setSneaking(sneak);player.setHeldItem(EnumHand.MAIN_HAND,held);}
  System.out.println("[vandorlabs] trapdoor-groups PASS actual placement axes=2 positions=3 rotating/sliding true centre/cardinal directions/saved geometry/ray clearance");
 }
 static void rendered(net.minecraft.client.Minecraft mc){
  TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)mc.world.getTileEntity(BASE.south());
  need(leaf!=null&&leaf.writeToNBT(new NBTTagCompound()).getBoolean("TrapdoorCenterSplit"),"client sees the split middle tile");
  net.minecraft.client.renderer.BufferBuilder buffer=new net.minecraft.client.renderer.BufferBuilder(8192);buffer.begin(7,BlockSurfaceFormat.get());
  net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(leaf.getHousingTexture()));
  TEProgrammableTrapdoor.drawConfiguredLeaf(buffer,sprite,leaf,mc.world.getBlockState(leaf.getPos()),.5,0xF000A0);buffer.finishDrawing();
  need(buffer.getVertexCount()>=48,"both moving half-leaves reach the real renderer");
  java.nio.ByteBuffer bytes=buffer.getByteBuffer();int stride=buffer.getVertexFormat().getNextOffset();double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
  for(int i=0;i<buffer.getVertexCount();i++){double z=bytes.getFloat(i*stride+8);low=Math.min(low,z);high=Math.max(high,z);}
  need(low<.4&&high>.6,"half-open rendered halves separate in opposite directions");
  System.out.println("[vandorlabs] trapdoor-groups-render PASS synchronized middle cell emits both opposed half-leaves");
 }
 private static int uiStage,uiTicks;
 static boolean tick(net.minecraft.client.Minecraft mc,java.io.File output)throws Exception{
  need(++uiTicks<300,"dialog packet timeout stage="+uiStage+" mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" position="+mc.player.getPosition()+" flying="+mc.player.capabilities.isFlying+" screen="+mc.currentScreen);
  if(uiStage<2&&mc.currentScreen!=null)mc.displayGuiScreen(null);
  if(uiStage==0){
   mc.getIntegratedServer().callFromMainThread(()->{
    net.minecraft.entity.player.EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
    actor.setGameType(net.minecraft.world.GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.inventory.currentItem=0;actor.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(0));actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.inventoryContainer.detectAndSendChanges();
    actor.connection.setPlayerLocation(BASE.getX()+3,BASE.getY()+1,BASE.getZ()-3,0,0);return null;
   }).get();uiStage=1;return false;
  }
  if(uiStage==1){
   if(mc.playerController.getCurrentGameType()!=net.minecraft.world.GameType.CREATIVE||!mc.player.capabilities.isFlying||mc.player.inventory.currentItem!=0||!mc.player.getHeldItemMainhand().isEmpty()||mc.player.getDistanceSq(BASE)>64)return false;
   net.minecraft.tileentity.TileEntity observed=mc.world.getTileEntity(BASE.south());
   if(!(observed instanceof TileEntityProgrammableTrapdoor)||!((TileEntityProgrammableTrapdoor)observed).writeToNBT(new NBTTagCompound()).getBoolean("TrapdoorCenterSplit"))return false;
   rendered(mc);
   mc.getIntegratedServer().callFromMainThread(()->{
    net.minecraft.entity.player.EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
    actor.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_PROGRAMMABLE_TRAPDOOR,actor.world,BASE.getX(),BASE.getY(),BASE.getZ()+1);return null;
   }).get();uiStage=2;return false;
  }
  if(!(mc.currentScreen instanceof GuiProgrammableTrapdoor))return false;
  GuiProgrammableTrapdoor gui=(GuiProgrammableTrapdoor)mc.currentScreen;
  java.util.List<net.minecraft.client.gui.GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiScreen.class,gui,"buttonList","field_146292_n");
  net.minecraft.client.gui.GuiButton opening=buttons.stream().filter(b->b.id==11).findFirst().orElseThrow(()->new IllegalStateException("joined Opening control missing"));
  if(uiStage==2){need(opening.displayString.equals("Opening: Center split"),"loaded centre caption");gui.mouseClicked(opening.x+6,opening.y+6,0);gui.mouseReleased(opening.x+6,opening.y+6,0);uiStage=3;uiTicks=0;return false;}
  if(uiTicks<15)return false;
  int expected=uiStage==3?3:uiStage==4?0:6;
  int actual=mc.getIntegratedServer().callFromMainThread(()->((TileEntityProgrammableTrapdoor)mc.getIntegratedServer().getWorld(0).getTileEntity(BASE.south())).groupOpening()).get();
  need(actual==expected,"actual dialog packet opening="+actual+" expected="+expected);
  need(opening.displayString.equals(com.vandorlabs.ui.TrapdoorOptions.groupOpeningLabel(expected)),"cardinal label matches transmitted option");
  if(uiStage<5){gui.mouseClicked(opening.x+6,opening.y+6,1);gui.mouseReleased(opening.x+6,opening.y+6,1);uiStage++;uiTicks=0;return false;}
  javax.imageio.ImageIO.write(DialogRenderCapture.image(mc,gui,480,320),"png",new java.io.File(output,"trapdoor-cardinal-dialog.png"));
  mc.displayGuiScreen(null);System.out.println("[vandorlabs] trapdoor-groups-dialog PASS real Opening button, cardinal packet, right-click previous and reverse wrap");return true;
 }

}
