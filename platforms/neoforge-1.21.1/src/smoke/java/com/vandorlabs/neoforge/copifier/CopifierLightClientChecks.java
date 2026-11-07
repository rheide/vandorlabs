package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.client.NativeCopifierScreen;
import com.vandorlabs.neoforge.light.*;
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
public final class CopifierLightClientChecks {
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Registration {@SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(CopifierLightClientFixture.enabled()){if(NativeCopifierItemChecks.MENU!=NativeCopifierRegistrations.MENU.get())NativeCopifierScreen.register(event,NativeCopifierItemChecks.MENU);}}}
 private static final CopifierCopyClientJourney JOURNEY=new CopifierCopyClientJourney(new CopifierCopyClientJourney.Scenario(){
  public boolean enabled(){return CopifierLightClientFixture.enabled();}public String label(){return "copifier-light-client";}
  public net.minecraft.core.BlockPos source(){return CopifierLightClientFixture.SOURCE;}public net.minecraft.core.BlockPos target(){return CopifierLightClientFixture.TARGET;}public net.minecraft.core.BlockPos ack(){return CopifierLightClientFixture.ACK;}
  public boolean targetsReady(Minecraft mc){return mc.level.getBlockEntity(target()) instanceof NativeLightEntity&&mc.level.getBlockEntity(CopifierLightClientFixture.PEER) instanceof NativeLightEntity;}
  public boolean copied(net.minecraft.nbt.CompoundTag clipboard){return !clipboard.getBoolean("active")&&clipboard.getInt("light_level")==7;}
  private boolean matches(Minecraft mc,boolean changed){int configured=changed?7:15;return mc.level.getBlockEntity(target()) instanceof NativeLightEntity first&&mc.level.getBlockEntity(CopifierLightClientFixture.PEER) instanceof NativeLightEntity second&&first.settings().light().manualOn!=changed&&second.settings().light().manualOn!=changed&&first.settings().light().configuredLevel==configured&&second.settings().light().configuredLevel==configured;}
  public boolean applied(Minecraft mc){return matches(mc,true);}public boolean undone(Minecraft mc){return matches(mc,false);}public void confirm(int operation){PacketDistributor.sendToServer(new CopifierLightClientFixture.Mode(operation));}
 });
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){JOURNEY.tick();}
}
