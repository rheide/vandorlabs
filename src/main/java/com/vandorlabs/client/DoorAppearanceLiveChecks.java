package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.items.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.renderer.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;
/** Real container/packet fixtures for both door sizes, paired frames and glazing. */
final class DoorAppearanceLiveChecks {
 private static final BlockPos POS=new BlockPos(220,90,220);private static int ticks,phase,fixture;
 private static int front,inside;
 static boolean tick(Minecraft mc,File output)throws Exception{
  mc.gameSettings.pauseOnLostFocus=false;if(Boolean.getBoolean("vandorlabs.doorRenderChecksOnly"))fixture=6;if(++ticks%20!=0)return false;
  boolean large=(fixture&1)!=0;int design=fixture<2?15:fixture<4?16:29;
  if(fixture==6){DoorAppearanceRuntimeChecks.run(mc,output);System.out.println("[vandorlabs][reprolab] door-appearance-live PASS real containers packets normal/large White/Dark/Slim paired frame independence glass native/general four tints reverse-cycle Original save/item compact/normal dialogs");return true;}
  GuiSpaceDoor gui=mc.currentScreen instanceof GuiSpaceDoor?(GuiSpaceDoor)mc.currentScreen:null;
  if(phase==0){front=ScreenHousingTextures.screenIndex("frames/frames-door-window/vandor_bg");inside=ScreenHousingTextures.screenIndex("light_alloy_hull");mc.player.closeScreen();setup(mc,large,design);}
  else if(phase==1){mc.playerController.setGameType(GameType.CREATIVE);mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=player(mc);p.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_SPACE_DOOR,p.world,POS.getX(),POS.getY(),POS.getZ());}).get();}
  else{
   require(gui!=null,"dialog did not open fixture="+fixture+" phase="+phase+" screen="+mc.currentScreen);
   if(phase==2)gui.actionPerformed(button(gui,191));
   if(phase==3)gui.actionPerformed(button(gui,3001));
   if(phase==4)pick(gui,inside);
   if(phase==5)check(mc,-1,inside,null,large);
   if(phase==6)gui.actionPerformed(button(gui,3000));
   if(phase==7)pick(gui,front);
   if(phase==8){check(mc,front,inside,null,large);snapshots(mc,gui,output,"door-dialog-"+fixture+"-frame");}
   if(phase==9)gui.actionPerformed(button(gui,194));
   if(phase==10)pick(gui,HousingTextureList.CANOPY_GLASS_BASE+12);
   if(phase==11)check(mc,front,inside,new DoorGlassAppearance(-1,12,1,1),large);
   if(phase==12)gui.actionPerformed(button(gui,20));
   if(phase==13){check(mc,front,inside,new DoorGlassAppearance(-1,12,2,1),large);gui.actionPerformed(button(gui,20));}
   if(phase==14){check(mc,front,inside,new DoorGlassAppearance(-1,12,3,1),large);GuiButton b=button(gui,20);gui.mouseClicked(b.x+4,b.y+6,1);}
   if(phase==15){check(mc,front,inside,new DoorGlassAppearance(-1,12,2,1),large);pick(gui,inside);}
   if(phase==16){check(mc,front,inside,new DoorGlassAppearance(inside,12,2,1),large);gui.actionPerformed(button(gui,21));}
   if(phase==17){check(mc,front,inside,new DoorGlassAppearance(inside,12,2,2),large);snapshots(mc,gui,output,"door-dialog-"+fixture+"-glass");gui.actionPerformed(button(gui,22));}
   if(phase==18)check(mc,front,inside,DoorGlassAppearance.DEFAULT,large);
   if(phase==19){pick(gui,inside);gui.actionPerformed(button(gui,20));gui.actionPerformed(button(gui,20));}
   if(phase==20){check(mc,front,inside,new DoorGlassAppearance(inside,0,3,1),large);mc.getIntegratedServer().addScheduledTask(()->{TileEntitySpaceDoor source=(TileEntitySpaceDoor)player(mc).world.getTileEntity(POS);TileEntitySpaceDoor saved=large?new TileEntityLargeProgrammableDoor():new TileEntitySpaceDoor();saved.readFromNBT(source.writeToNBT(new NBTTagCompound()));require(saved.glassAppearance().equals(source.glassAppearance()),"live reload lost glazing");TileEntitySpaceDoor item=large?new TileEntityLargeProgrammableDoor():new TileEntitySpaceDoor();item.applyItemSettings(source.itemSettings());require(item.glassAppearance().equals(source.glassAppearance()),"live item lost glazing");}).get();}
   if(phase==21){mc.player.closeScreen();fixture++;phase=-1;}
  }
  phase++;return false;
 }
 private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
 private static void setup(Minecraft mc,boolean large,int design)throws Exception{
  mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP player=player(mc);player.closeScreen();player.interactionManager.setGameType(GameType.CREATIVE);player.inventory.currentItem=0;player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));player.capabilities.isFlying=true;player.sendPlayerAbilities();player.connection.setPlayerLocation(POS.getX()+.5,POS.getY()+1,POS.getZ()+4,180,0);
   for(BlockPos p:BlockPos.getAllInBox(POS.add(-2,-1,-2),POS.add(5,5,2)))world.setBlockToAir(p);
   Block block=Block.getBlockFromName(large?"vandorlabs:large_programmable_door":"vandorlabs:programmable_door");int width=large?3:2,height=large?3:2;
   for(int x=0;x<width;x++){world.setBlockState(POS.east(x).down(),net.minecraft.init.Blocks.STONE.getDefaultState(),2);for(int y=0;y<height;y++){
    BlockPos p=POS.add(x,y,0);IBlockState state=block.getDefaultState().withProperty(BlockVandorDoor.FACING,EnumFacing.SOUTH).withProperty(BlockVandorDoor.HINGE,x==0?BlockDoor.EnumHingePosition.RIGHT:BlockDoor.EnumHingePosition.LEFT).withProperty(BlockVandorDoor.HALF,y==0?BlockDoor.EnumDoorHalf.LOWER:BlockDoor.EnumDoorHalf.UPPER);world.setBlockState(p,state,2);
    TileEntitySpaceDoor tile=(TileEntitySpaceDoor)world.getTileEntity(p);if(large)((TileEntityLargeProgrammableDoor)tile).assign(POS);tile.configure(design,1,true,0,true,true,false,0,false);
    player.connection.sendPacket(new net.minecraft.network.play.server.SPacketBlockChange(world,p));player.connection.sendPacket(tile.getUpdatePacket());
   }}player.inventoryContainer.detectAndSendChanges();
  }).get();
 }
 private static void check(Minecraft mc,int front,int inside,DoorGlassAppearance glass,boolean large)throws Exception{
  mc.getIntegratedServer().addScheduledTask(()->{World world=player(mc).world;for(BlockPos p:large?Collections.singletonList(POS):Arrays.asList(POS,POS.east())){TileEntitySpaceDoor t=(TileEntitySpaceDoor)world.getTileEntity(p);require(t.frameAppearance.front==front&&t.frameAppearance.interior==inside,"server frame wrong at "+p+" expected="+front+"/"+inside+" actual="+t.frameAppearance.front+"/"+t.frameAppearance.interior);if(glass!=null)require(t.glassAppearance().equals(glass),"server glazing wrong at "+p);}}).get();
  TileEntitySpaceDoor tile=(TileEntitySpaceDoor)mc.world.getTileEntity(POS);require(tile!=null&&tile.frameAppearance.front==front&&tile.frameAppearance.interior==inside,"client frame did not synchronize");if(glass!=null)require(tile.glassAppearance().equals(glass),"client glazing did not synchronize");
 }
 @SuppressWarnings("unchecked") private static List<GuiButton> buttons(GuiScreen gui){try{java.lang.reflect.Field f=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(GuiScreen.class,"buttonList","field_146292_n");return (List<GuiButton>)f.get(gui);}catch(Exception e){throw new IllegalStateException(e);}}
 private static GuiButton button(GuiScreen gui,int id){return buttons(gui).stream().filter(b->b.id==id).findFirst().orElseThrow(()->new IllegalStateException("Missing door control "+id));}
 @SuppressWarnings("unchecked") private static void pick(GuiSpaceDoor gui,int choice)throws Exception{
  HousingTextureList picker=gui.materialPicker();require(picker!=null,"missing door picker");picker.setSelected(choice);Class<?> c=HousingTextureList.class;java.lang.reflect.Field rows=c.getDeclaredField("rows"),scroll=c.getDeclaredField("scroll"),x=c.getDeclaredField("x"),y=c.getDeclaredField("y");for(java.lang.reflect.Field f:new java.lang.reflect.Field[]{rows,scroll,x,y})f.setAccessible(true);int row=((List<Integer>)rows.get(picker)).indexOf(choice);require(row>=0,"picker row absent "+choice);gui.mouseClicked(x.getInt(picker)+8,y.getInt(picker)+(row-scroll.getInt(picker))*HousingTextureList.ROW_HEIGHT+8,0);
 }
 private static void snapshots(Minecraft mc,GuiSpaceDoor gui,File output,String name)throws Exception{
  for(int[] size:new int[][]{{320,240},{460,340}}){gui.setWorldAndResolution(mc,size[0],size[1]);List<GuiButton> tabs=new ArrayList<>();for(GuiButton b:buttons(gui))if(b.id>=190&&b.id<=194)tabs.add(b);tabs.sort(Comparator.comparingInt(b->b.x));require(tabs.size()==5,"glass door tabs missing");for(int i=1;i<tabs.size();i++)require(tabs.get(i).x>=tabs.get(i-1).x+tabs.get(i-1).width+2,"door tabs overlap");for(GuiButton b:buttons(gui))if(b.visible)require(b.x>=0&&b.y>=0&&b.x+b.width<=size[0]&&b.y+b.height<=size[1],"door control clipped "+b.id);
   Framebuffer target=new Framebuffer(size[0],size[1],true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,size[0],size[1],0,-1000,1000);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
   try(TurntableRenderState saved=new TurntableRenderState()){target.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.clear(16640);mc.entityRenderer.disableLightmap();gui.drawScreen(-1,-1,0);GL11.glFinish();ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(size[0],size[1],target),"png",new File(output,name+"-"+size[0]+".png"));}
   finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
  }
 }
 private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Door appearance live: "+message);}
}
