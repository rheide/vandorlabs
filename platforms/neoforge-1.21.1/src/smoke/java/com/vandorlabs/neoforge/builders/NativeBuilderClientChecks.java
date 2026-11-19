package com.vandorlabs.neoforge.builders;
import com.vandorlabs.neoforge.builders.client.NativeBuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeBuilderClientChecks{
 private static int stage,ticks,phaseTicks;private static boolean capture;private static int captured;private static java.util.List<net.minecraft.world.inventory.Slot> resizedSlots;private static int originalScale,resizeWindow;private static void need(boolean v,String s){if(!v)throw new AssertionError("Builder client stage="+stage+": "+s);}private static void next(int n){stage=n;phaseTicks=0;}private static void mode(int n){PacketDistributor.sendToServer(new NativeBuilderClientFixture.Mode(n));}
 private static boolean ack(Minecraft m,int n){return m.level.getBlockState(NativeBuilderClientFixture.ackPos(n)).is(Blocks.STONE);}
 private static void use(Minecraft m,BlockPos p,Direction side){m.gameMode.useItemOn(m.player,InteractionHand.MAIN_HAND,new BlockHitResult(p.getCenter().add(0,0,-.5),side,p,false));}
 private static void open(Minecraft m){m.gameMode.useItemOn(m.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(2.8,96.95,0),Direction.NORTH,NativeBuilderClientFixture.ROOT.east(2),false));}
 private static void click(NativeBuilderScreen s,int id,int mouse){var b=s.control(id);need(b!=null&&b.active&&s.mouseClicked(b.getX()+b.getWidth()/2D,b.getY()+8,mouse),"real widget "+id);}
 private static String footprint(NativeBuilderEntity tile){if(tile==null||!tile.formed())return "core not formed";for(int x=0;x<3;x++)for(int y=0;y<5;y++)for(int z=0;z<3;z++){var at=tile.cell(x,y,z);var state=tile.getLevel().getBlockState(at);if(state.getBlock()==NativeBuilderFormation.matter())return "stale Matter cell "+at;if(at.equals(tile.getBlockPos()))continue;var raw=tile.getLevel().getBlockEntity(at);if(state.getBlock()!=NativeBuilderBindings.PART||!(raw instanceof NativeBuilderPartEntity part)||part.inert()||!part.owner().equals(tile.getBlockPos())||part.root()!=tile)return "unowned client cell "+at+" state="+state+" entity="+raw;}return "";}
 private static boolean bankAim(Minecraft m,int bay){return aimed(m,com.vandorlabs.machines.SynthesisPresentation.bayButtonX(),96+com.vandorlabs.machines.SynthesisPresentation.bayCenterY(bay),com.vandorlabs.machines.SynthesisPresentation.rackFront());}
 private static boolean aimed(Minecraft m,double x,double y,double z){if(m.gameMode.getPlayerMode()!=GameType.SURVIVAL||m.player.getInventory().selected!=0||m.player.position().distanceToSqr(x,96,-2.5)>.01)return false;var target=new Vec3(x,y,z).subtract(m.player.getEyePosition()).normalize();return m.player.getLookAngle().dot(target)>.9999;}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!NativeBuilderClientFixture.enabled()||NativeBuilderClientFixture.partsOnly()||NativeBuilderClientFixture.drivesOnly())return;var m=Minecraft.getInstance();try{if(m.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen intro){intro.onClose();return;}if(stage==20&&m.level==null){need(captured>=3,"full+compact Production and Redstone screenshots");System.out.println("[vandorlabs][builder-client] client PASS actualSurvivalPlacement/durableFormation/ProductionRedstone/previousCycle/slots/preview/chargedOutput/Digitize/enclosure/reverseVisual/configuredDynamicRecipe");stage=21;m.stop();return;}need(!(m.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen),"unexpected disconnect");if(m.level==null||m.player==null||m.gameMode==null)return;need(++ticks<2400&&++phaseTicks<600,"timeout mode="+m.gameMode.getPlayerMode()+" slot="+m.player.getInventory().selected+" held="+m.player.getMainHandItem()+" position="+m.player.position()+" screen="+m.screen+" "+NativeBuilderWorldChecks.status());var raw=m.level.getBlockEntity(NativeBuilderClientFixture.ROOT);var tile=raw instanceof NativeBuilderEntity t?t:null;var gui=m.screen instanceof NativeBuilderScreen s?s:null;
 switch(stage){
  case 0->{if(m.gameMode.getPlayerMode()!=GameType.SURVIVAL||m.player.getInventory().selected!=0||!m.player.getMainHandItem().is(NativeBuilderBindings.CORES.get("synthesis_tower").asItem())||m.player.position().distanceToSqr(.5,96,-3.5)>.01||m.player.getLookAngle().dot(new Vec3(.5,96,.5).subtract(m.player.getEyePosition()).normalize())<.9999)return;m.gameMode.useItemOn(m.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(.5,96,.5),Direction.UP,NativeBuilderClientFixture.ROOT.below(),false));next(1);}
  case 1->{if(tile==null||!m.player.getMainHandItem().isEmpty())return;mode(0);next(2);}
  case 2->{if(!ack(m,0))return;use(m,NativeBuilderClientFixture.ROOT,Direction.NORTH);next(3);}
  case 3->{if(tile==null||!tile.formed()||tile.transactionBlocked())return;String mismatch=footprint(tile);if(!mismatch.isEmpty()){if(phaseTicks%100==0)System.out.println("[vandorlabs][builder-client] footprint WAIT "+mismatch);return;}System.out.println("[vandorlabs][builder-client] footprint PASS all45cells/exactPartOwners/noMatter");mode(1);next(4);}
  case 4->{if(!ack(m,1)||!com.vandorlabs.neoforge.rack.NativeBatteryModule.valid(m.player.getMainHandItem())||!aimed(m,com.vandorlabs.machines.SynthesisPresentation.bayButtonX(),96+com.vandorlabs.machines.SynthesisPresentation.bayCenterY(0),com.vandorlabs.machines.SynthesisPresentation.rackFront()))return;use(m,NativeBuilderClientFixture.ROOT.above(),Direction.NORTH);next(5);}
  case 5->{if(tile==null||tile.batteryEnergy()!=10000||!m.player.getMainHandItem().isEmpty())return;mode(2);next(6);}
  case 6->{if(!ack(m,2)||!com.vandorlabs.neoforge.credentials.NativeCredentialItems.accepts(m.player.getMainHandItem(),true)||!aimed(m,com.vandorlabs.machines.SynthesisPresentation.CHIP_X,96+com.vandorlabs.machines.SynthesisPresentation.CHIP_Y,com.vandorlabs.machines.SynthesisPresentation.CHIP_Z))return;use(m,NativeBuilderClientFixture.ROOT.above(),Direction.NORTH);next(7);}
  case 7->{if(tile==null||tile.getItem(3).isEmpty()||!m.player.getMainHandItem().isEmpty())return;mode(3);next(8);}
  case 8->{if(!ack(m,3)||!aimed(m,2.8,96.95,0))return;open(m);next(9);}
  case 9->{if(gui==null)return;com.vandorlabs.neoforge.NativeBlockRecipeRenderChecks.run();NativeBuilderDigitizeMotionChecks.run();need(gui.controlsFit()&&gui.getMenu().slots.get(0).x==58,"Source full layout and43slots fit");capture=true;click(gui,1,0);next(10);}
  case 10->{if(capture||gui==null||gui.getMenu().value(0)!=1)return;resizedSlots=new java.util.ArrayList<>(gui.getMenu().slots);resizeWindow=gui.getMenu().containerId;originalScale=m.options.guiScale().get();m.options.guiScale().set(3);m.resizeDisplay();next(50);}
  case 50->{if(gui==null||m.getWindow().getGuiScaledHeight()!=240||phaseTicks<5)return;need(gui.controlsFit()&&gui.getMenu().containerId==resizeWindow&&gui.getMenu().slots.size()==43&&gui.getMenu().slots.get(0).x==58,"actual guiScale3 compact Source layout/window preserved");for(int i=0;i<43;i++)need(gui.getMenu().slots.get(i)==resizedSlots.get(i),"resize preserves exact server slot identity "+i);capture=true;next(51);}
  case 51->{if(capture||gui==null)return;m.options.guiScale().set(originalScale);m.resizeDisplay();need(gui.controlsFit()&&gui.getMenu().containerId==resizeWindow&&gui.getMenu().slots.get(0).x==58,"full Source layout restored");for(int i=0;i<43;i++)need(gui.getMenu().slots.get(i)==resizedSlots.get(i),"restore preserves slot identity "+i);System.out.println("[vandorlabs][builder-client] resize PASS actualGuiScale2Full/3Compact/43sameSlotIdentities/sameWindow");click(gui,101,0);gui.channelField().setValue("71,72");click(gui,10,1);next(11);}
  case 11->{if(gui==null||gui.getMenu().value(7)!=0)return;need(gui.getMenu().slots.stream().noneMatch(net.minecraft.world.inventory.Slot::isActive)&&gui.control(102)==null,"Redstone hides all inventories and Mode control");capture=true;mode(4);next(12);}
  case 12->{if(capture||!ack(m,4)||gui==null)return;gui.onClose();mode(5);next(13);}
  case 13->{if(!ack(m,5)||!m.player.getMainHandItem().is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return;open(m);next(14);}
  case 14->{if(gui==null)return;need(gui.controlsFit()&&gui.getMenu().value(0)==1,"same Configurizer menu retains choices");mode(6);next(15);}
  case 15->{if(!ack(m,6)||gui==null)return;m.gameMode.handleInventoryMouseClick(gui.getMenu().containerId,7,0,ClickType.QUICK_MOVE,m.player);next(16);}
  case 16->{if(gui==null||gui.getMenu().value(2)<1600||gui.getMenu().value(2)>=8000)return;gui.onClose();mode(7);next(25);}
  case 25->{if(!ack(m,7)||m.screen!=null||m.player.position().distanceToSqr(-5.5,96,-7.5)>.01||tile==null||tile.buildProgress()<=0||!tile.processing())return;need(footprint(tile).isEmpty(),"overview client footprint remains formed");NativeBuilderWorldChecks.arm();next(26);}
  case 26->{if(!NativeBuilderWorldChecks.done())return;mode(8);next(27);}
  case 27->{if(!ack(m,8)||!aimed(m,2.8,96.95,0))return;open(m);next(28);}
  case 28->{if(gui==null||gui.getMenu().contents().getItem(1).isEmpty())return;m.gameMode.handleInventoryMouseClick(gui.getMenu().containerId,1,0,ClickType.QUICK_MOVE,m.player);next(17);}
  case 17->{if(gui==null||!gui.getMenu().contents().getItem(1).isEmpty())return;gui.onClose();mode(9);next(18);}
  case 18->{if(!ack(m,9)||!m.player.getMainHandItem().isEmpty()||!aimed(m,com.vandorlabs.machines.SynthesisPresentation.bayButtonX(),96+com.vandorlabs.machines.SynthesisPresentation.bayCenterY(0),com.vandorlabs.machines.SynthesisPresentation.rackFront()))return;use(m,NativeBuilderClientFixture.ROOT.above(),Direction.NORTH);next(19);}
  case 19->{if(com.vandorlabs.neoforge.rack.NativeBatteryModule.energy(m.player.getMainHandItem())!=2000)return;mode(10);next(22);}
  case 22->{if(!ack(m,10)||!m.player.getMainHandItem().isEmpty()||!aimed(m,com.vandorlabs.machines.SynthesisPresentation.CHIP_X,96+com.vandorlabs.machines.SynthesisPresentation.CHIP_Y,com.vandorlabs.machines.SynthesisPresentation.CHIP_Z))return;use(m,NativeBuilderClientFixture.ROOT.above(),Direction.NORTH);next(23);}
  case 23->{if(tile==null||!tile.getItem(3).isEmpty())return;mode(11);next(24);}
  case 24->{if(!ack(m,11))return;next(30);}
  case 30,31,32,33->{int bay=stage-30,operation=11+bay;if(!ack(m,operation)||!com.vandorlabs.neoforge.rack.NativeBatteryModule.valid(m.player.getMainHandItem())||!bankAim(m,bay))return;use(m,NativeBuilderClientFixture.ROOT.offset(0,bay==0?1:0,0),Direction.NORTH);next(34+bay);}
  case 34,35,36,37->{int bay=stage-34;if(tile==null||tile.batteryEnergy(bay)!=10000||!m.player.getMainHandItem().isEmpty())return;mode(12+bay);next(bay==3?38:31+bay);}
  case 38->{if(!ack(m,15)||!aimed(m,2.8,96.95,0))return;open(m);next(39);}
  case 39->{if(gui==null||gui.getMenu().value(3)!=40000||gui.getMenu().value(4)!=40000)return;need(gui.getMenu().slots.size()==43,"client43slots and unsigned40000RF");mode(16);next(40);}
  case 40->{if(!ack(m,16)||gui==null)return;gui.onClose();mode(17);next(41);}
  case 41,42,43,44->{int bay=stage-41;if(!ack(m,17+bay)||!m.player.getMainHandItem().isEmpty()||!bankAim(m,bay))return;use(m,NativeBuilderClientFixture.ROOT.offset(0,bay==0?1:0,0),Direction.NORTH);next(45+bay);}
  case 45,46,47,48->{int bay=stage-45;if(com.vandorlabs.neoforge.rack.NativeBatteryModule.energy(m.player.getMainHandItem())!=10000)return;mode(18+bay);next(bay==3?49:42+bay);}
  case 49->{if(!ack(m,21))return;mode(22);next(60);}
  case 60->{if(!ack(m,22)||!com.vandorlabs.neoforge.credentials.NativeChipTypes.type(m.player.getMainHandItem()).equals("catalog")||!aimed(m,com.vandorlabs.machines.SynthesisPresentation.CHIP_X,96+com.vandorlabs.machines.SynthesisPresentation.CHIP_Y,com.vandorlabs.machines.SynthesisPresentation.CHIP_Z))return;use(m,NativeBuilderClientFixture.ROOT.above(),Direction.NORTH);next(61);}
  case 61->{if(tile==null||tile.getItem(3).isEmpty()||!m.player.getMainHandItem().isEmpty())return;mode(23);next(62);}
  case 62->{if(!ack(m,23)||!aimed(m,2.8,96.95,0))return;open(m);next(63);}
  case 63->{if(gui==null||gui.getMenu().value(9)!=0)return;need(gui.getMenu().slots.size()==43,"mode append preserves43slot ABI");click(gui,102,0);next(64);}
  case 64->{if(gui==null||gui.getMenu().value(9)!=1||gui.getMenu().slots.get(0).x!=18)return;need(gui.getMenu().slots.get(0).x==18&&gui.getMenu().slots.get(3).x==108&&!gui.getMenu().slots.get(1).isActive()&&gui.getMenu().slots.get(0).getMaxStackSize()==1,"Digitize input/chip layout and hidden output, single sample");click(gui,102,1);next(65);}
  case 65->{if(gui==null||gui.getMenu().value(9)!=0)return;need(gui.getMenu().slots.get(1).isActive(),"reverse mode cycle restores output slot");click(gui,102,1);next(66);}
  case 66->{if(gui==null||gui.getMenu().value(9)!=1)return;gui.onClose();mode(24);next(67);}
  case 67->{if(!ack(m,24)||!aimed(m,1.5,98.75,1.625)||!NativeBlockRecipeData.eligible(m.player.getMainHandItem()))return;need(m.gameMode.getPlayerMode()==GameType.SURVIVAL&&m.player.getInventory().selected==0,"enclosure interaction actual synchronized Survival");m.gameMode.useItemOn(m.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(1.5,98.75,0),Direction.NORTH,NativeBuilderClientFixture.ROOT.offset(1,2,0),false));next(68);}
  case 68->{if(tile==null||!m.player.getMainHandItem().isEmpty()||!tile.processing()||tile.buildProgress()<.15)return;mode(25);next(69);}
  case 69->{if(!ack(m,25)||!aimed(m,2.8,96.95,0))return;open(m);next(70);}
  case 70->{if(gui==null||gui.getMenu().value(9)!=1||gui.getMenu().contents().getItem(0).isEmpty())return;need(!gui.getMenu().slots.get(1).isActive(),"processing output remains hidden");capture=true;click(gui,102,0);next(71);}
  case 71->{if(gui==null||phaseTicks<8||capture)return;need(gui.getMenu().value(9)==1&&!gui.getMenu().contents().getItem(0).isEmpty(),"active mode refusal retains exact input");gui.onClose();mode(26);next(72);}
  case 72->{if(!ack(m,26)||m.screen!=null||m.player.position().distanceToSqr(-5.5,96,-7.5)>.01||tile==null||!tile.processing())return;NativeBuilderWorldChecks.arm(true);next(73);}
  case 73->{if(!NativeBuilderWorldChecks.done())return;next(74);}
  case 74->{if(tile==null||tile.processing()||!tile.getItem(0).isEmpty())return;mode(27);next(75);}
  case 75->{if(!ack(m,27)||!aimed(m,2.8,96.95,0))return;open(m);next(76);}
  case 76->{if(gui==null||gui.getMenu().value(9)!=1)return;click(gui,102,0);next(77);}
  case 77->{if(gui==null||gui.getMenu().value(9)!=0)return;click(gui,5,0);next(78);}
  case 78->{if(gui==null||gui.getMenu().value(0)!=5)return;mode(28);next(79);}
  case 79->{if(!ack(m,28)||gui==null||!gui.getMenu().slots.get(7).hasItem())return;m.gameMode.handleInventoryMouseClick(gui.getMenu().containerId,7,0,ClickType.QUICK_MOVE,m.player);next(80);}
  case 80->{if(gui==null||gui.getMenu().contents().getItem(1).isEmpty())return;capture=true;m.gameMode.handleInventoryMouseClick(gui.getMenu().containerId,1,0,ClickType.QUICK_MOVE,m.player);next(81);}
  case 81->{if(gui==null||capture||!gui.getMenu().contents().getItem(1).isEmpty())return;gui.onClose();mode(29);next(82);}
  case 82->{if(!ack(m,29))return;m.level.disconnect();m.disconnect();next(20);}

 }
 }catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][builder-client] FAIL "+failure);m.stop();}}
 @SubscribeEvent public static void frame(RenderFrameEvent.Post event)throws Exception{if(!capture)return;var m=Minecraft.getInstance();if(!(m.screen instanceof NativeBuilderScreen))return;capture=false;try(var image=net.minecraft.client.Screenshot.takeScreenshot(m.getMainRenderTarget())){var path=m.gameDirectory.toPath().resolve("builder-menu-"+(captured++)+".png");image.writeToFile(path);
 if(m.screen instanceof NativeBuilderScreen screen&&screen.page()==0){var bounds=screen.previewBounds();double sx=image.getWidth()/(double)m.getWindow().getGuiScaledWidth(),sy=image.getHeight()/(double)m.getWindow().getGuiScaledHeight();int left=(int)Math.ceil(bounds[0]*sx),right=(int)Math.floor(bounds[2]*sx),top=(int)Math.ceil(bounds[1]*sy),bottom=(int)Math.floor(bounds[3]*sy),ink=0,minY=bottom,maxY=top;var colors=new java.util.HashSet<Integer>();for(int y=top;y<bottom;y++)for(int x=left;x<right;x++){int pixel=image.getPixelRGBA(x,y)&0xffffff;if(pixel!=0x18110d){ink++;colors.add(pixel);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}}need(ink>200&&colors.size()>20,"actual textured full-structure GUI preview");need(minY>top&&maxY<bottom-1,"preview top/bottom fit inside clip bounds");}
}}
}
