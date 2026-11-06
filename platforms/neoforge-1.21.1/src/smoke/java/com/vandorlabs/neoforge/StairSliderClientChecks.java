package com.vandorlabs.neoforge;
import com.vandorlabs.neoforge.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
/** Actual container drag changes pointer position; a click at the final point cannot establish this. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class StairSliderClientChecks {
 private static int stage,ticks,checks;private static void need(boolean value,String reason){checks++;if(!value)throw new IllegalStateException("Stair slider client stage="+stage+": "+reason);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!StairSliderClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(stage==4&&mc.level==null){System.out.println("[vandorlabs][stair-slider-client] client PASS checks="+checks+" actual press0/drag3/release/background/no-world-change and C2S/S2C");stage=5;mc.stop();return;}if(mc.player==null||mc.level==null||mc.gameMode==null)return;need(++ticks<900,"timeout mode="+mc.gameMode.getPlayerMode()+" held="+mc.player.getMainHandItem()+" slot="+mc.player.getInventory().selected+" pos="+mc.player.position()+" screen="+mc.screen);if(!(mc.level.getBlockEntity(StairSliderClientFixture.ROOT) instanceof com.vandorlabs.neoforge.shape.ProgrammableShapeEntity tile))return;switch(stage){case 0->{if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||!mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())||mc.player.getInventory().selected!=0||!mc.player.getOffhandItem().isEmpty()||mc.player.position().distanceToSqr(.5,96,-3)>.01)return;mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(.5,96.5,.01),Direction.NORTH,StairSliderClientFixture.ROOT,false));stage=1;}case 1->{if(!(mc.screen instanceof NativeCubeScreen screen))return;var slider=screen.children().stream().filter(x->x instanceof NativeIntegerSlider).map(x->(NativeIntegerSlider)x).findFirst().orElseThrow();double x=slider.getX()+4,y=slider.getY()+8,end=slider.getX()+slider.getWidth()-4;need(screen.mouseClicked(x,y,0)&&screen.editingShapeState().tread==1,"press selects tread1");need(screen.mouseDragged(end,y,0,end-x,0)&&screen.editingShapeState().tread==8,"drag to new pointer selects tread8 actual="+screen.editingShapeState().tread);screen.mouseReleased(end,y,0);need(!NativeIntegerSlider.dragFocused(screen.getFocused(),x,y,0,x-end,0),"released slider refuses shared routing");screen.mouseDragged(x,y,0,x-end,0);need(screen.editingShapeState().tread==8,"released drag cannot alter tread");screen.setFocused(screen.activePicker().searchField());need(!NativeIntegerSlider.dragFocused(screen.getFocused(),x,y,0,100,0),"non-slider focus refuses shared routing");screen.mouseDragged(x,y,0,100,0);need(screen.editingShapeState().tread==8,"non-slider focus cannot alter tread");stage=2;}case 2->{if(tile.stairState().tread!=8)return;PacketDistributor.sendToServer(new StairSliderClientFixture.Mode());stage=3;}case 3->{if(!mc.level.getBlockState(StairSliderClientFixture.ACK).is(Blocks.STONE))return;mc.player.closeContainer();mc.level.disconnect();mc.disconnect();stage=4;}default->{}}}
}
