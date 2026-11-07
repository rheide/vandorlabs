package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.client.NativeCopifierScreen;
import com.vandorlabs.neoforge.glass.NativeGlassEntity;
import com.vandorlabs.ui.CopifierDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class CopifierGlassClientChecks {
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Registration {@SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(CopifierGlassClientFixture.enabled())NativeCopifierScreen.register(event,NativeCopifierItemChecks.MENU);}}
 private static final CopifierCopyClientJourney JOURNEY=new CopifierCopyClientJourney(new CopifierCopyClientJourney.Scenario(){
  public boolean enabled(){return CopifierGlassClientFixture.enabled();}public String label(){return "copifier-glass-client";}
  public net.minecraft.core.BlockPos source(){return CopifierGlassClientFixture.SOURCE;}public net.minecraft.core.BlockPos target(){return CopifierGlassClientFixture.TARGET;}public net.minecraft.core.BlockPos ack(){return CopifierGlassClientFixture.ACK;}
  public boolean targetsReady(Minecraft mc){return mc.level.getBlockEntity(target()) instanceof NativeGlassEntity&&mc.level.getBlockEntity(CopifierGlassClientFixture.PEER) instanceof NativeGlassEntity;}
  public boolean copied(net.minecraft.nbt.CompoundTag clipboard){return clipboard.getInt("glass_tint")==3;}
  private boolean matches(Minecraft mc,int tint,int size){return mc.level.getBlockEntity(target()) instanceof NativeGlassEntity first&&mc.level.getBlockEntity(CopifierGlassClientFixture.PEER) instanceof NativeGlassEntity second&&first.settings().glass().tint==tint&&second.settings().glass().tint==tint&&first.settings().glass().size==size&&second.settings().glass().size==size;}
  public boolean applied(Minecraft mc){return matches(mc,3,0);}public boolean undone(Minecraft mc){return matches(mc,1,1);}public void confirm(int operation){PacketDistributor.sendToServer(new CopifierGlassClientFixture.Mode(operation));}
 });
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){JOURNEY.tick();}
}
