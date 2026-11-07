package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.items.*;
import com.vandorlabs.neoforge.client.NativeCopifierScreen;
import com.vandorlabs.ui.CopifierDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Actual screen clicks and ordered network packets, including scale restoration and stale sessions. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class CopifierEditorClientChecks {
 private static int stage,ticks,wait,checks;private static NativeCopifierPayload stale;
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Registration {@SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(CopifierEditorClientFixture.enabled())NativeCopifierScreen.register(event,NativeCopifierItemChecks.MENU);}}
 private static void need(boolean yes,String why){checks++;if(!yes)throw new IllegalStateException("Copifier editor client stage="+stage+": "+why);}
 private static void mode(int operation){PacketDistributor.sendToServer(new CopifierEditorClientFixture.Mode(operation));}
 private static void click(NativeCopifierScreen screen,int id,int button){var control=screen.controls().stream().filter(c->c.id==id).findFirst().orElseThrow();need(control.enabled&&screen.mouseClicked(control.x+control.width/2D,control.y+7,button),"actual control="+id);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!CopifierEditorClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(stage==8&&mc.level==null){System.out.println("[vandorlabs][copifier-editor-client] client PASS checks="+checks+" fivepages/reverseclick/realC2S-S2C/readOnlyPreview/scaleRestore/staleWindow");stage=9;mc.stop();return;}if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<1400,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" position="+mc.player.position()+" screen="+mc.screen);
 switch(stage){case 0->{if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||mc.player.getInventory().selected!=0||!mc.player.getMainHandItem().is(NativeCopifierItemChecks.ITEM)||!mc.player.getOffhandItem().isEmpty()||mc.player.position().distanceToSqr(.5,96,-3)>.01)return;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);stage=1;}
 case 1->{if(!(mc.screen instanceof NativeCopifierScreen screen)||!screen.loaded())return;need(mc.options.guiScale().get()==2,"large dialog lowers scale3 to2");for(int page=0;page<DuplifierOptionCatalog.pages().length;page++){if(screen.page()!=page)click(screen,page,0);int current=page;var first=screen.controls().stream().filter(c->c.id>=CopifierDialog.OPTION_START&&DuplifierOptionCatalog.options()[c.id-CopifierDialog.OPTION_START].page==current).findFirst().orElseThrow();String label=first.label;click(screen,first.id,1);need(!screen.controls().stream().filter(c->c.id==first.id).findFirst().orElseThrow().label.equals(label),"right click toggles previous boolean");click(screen,first.id,0);need(screen.controls().stream().filter(c->c.id==first.id).findFirst().orElseThrow().label.equals(label),"left click restores option");for(var c:screen.controls())need(c.x>=0&&c.y>=0&&c.x+c.width<=mc.getWindow().getGuiScaledWidth()&&c.y+c.height<=mc.getWindow().getGuiScaledHeight(),"all controls within viewport");}
  int page=DuplifierOptionCatalog.options()[64].page;if(screen.page()!=page)click(screen,page,0);click(screen,CopifierDialog.OPTION_START+64,1);click(screen,CopifierDialog.CONNECTED,1);click(screen,CopifierDialog.IGNORE,0);mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,0,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,mc.player);stage=2;}
 case 2->{if(!(mc.screen instanceof NativeCopifierScreen screen))return;var root=NativeCopifierItem.data(screen.getMenu().editorStack());if(!NativeCopifierOptions.connected(root)||!NativeCopifierOptions.ignoreDirectionality(root)||(NativeCopifierOptions.maskWords(root)[1]&1)!=0)return;stale=new NativeCopifierPayload(screen.getMenu().containerId,NativeCopifierOptions.maskWords(root),false,false);need(mc.player.containerMenu.getCarried().isEmpty(),"preview quickmove leaves no carried stack");mc.player.closeContainer();stage=20;}
 case 20->{if(mc.screen instanceof NativeCopifierScreen||mc.player.containerMenu!=mc.player.inventoryMenu)return;need(mc.options.guiScale().get()==3,"scale restored before world interaction");mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(.5,96.5,0),net.minecraft.core.Direction.NORTH,CopifierEditorClientFixture.ROOT,false));stage=21;}
 case 21->{if(!(mc.level.getBlockEntity(CopifierEditorClientFixture.ROOT) instanceof com.vandorlabs.neoforge.ProgrammableBlockEntity a)||!(mc.level.getBlockEntity(CopifierEditorClientFixture.PEER) instanceof com.vandorlabs.neoforge.ProgrammableBlockEntity b)||a.settings().mainTexture!=7||b.settings().mainTexture!=7)return;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);stage=22;}
 case 22->{if(!(mc.screen instanceof NativeCopifierScreen screen)||!screen.loaded())return;mode(0);stage=3;}
 case 3->{if(mc.screen instanceof NativeCopifierScreen||!mc.level.getBlockState(CopifierEditorClientFixture.ACK).is(Blocks.STONE))return;need(mc.options.guiScale().get()==3,"original scale restored on server close");PacketDistributor.sendToServer(stale);wait=ticks;stage=4;}
 case 4->{if(ticks<wait+10)return;mode(1);stage=5;}
 case 5->{if(!mc.level.getBlockState(CopifierEditorClientFixture.ACK.east()).is(Blocks.STONE))return;mode(2);stage=6;}
 case 6->{if(!mc.level.getBlockState(CopifierEditorClientFixture.ACK.east(2)).is(Blocks.STONE))return;mc.level.disconnect();mc.disconnect();stage=8;}default->{}}
 }
}
