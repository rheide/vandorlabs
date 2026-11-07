package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.client.NativeCopifierScreen;
import com.vandorlabs.neoforge.NativeTriggerBlockEntity;
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
public final class CopifierTriggerClientChecks {
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Registration {@SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(CopifierTriggerClientFixture.enabled()){if(NativeCopifierItemChecks.MENU!=NativeCopifierRegistrations.MENU.get())NativeCopifierScreen.register(event,NativeCopifierItemChecks.MENU);}}}
 private static final CopifierCopyClientJourney JOURNEY=new CopifierCopyClientJourney(new CopifierCopyClientJourney.Scenario(){
  public boolean enabled(){return CopifierTriggerClientFixture.enabled();}public String label(){return "copifier-trigger-client";}
  public net.minecraft.core.BlockPos source(){return CopifierTriggerClientFixture.SOURCE;}public net.minecraft.core.BlockPos target(){return CopifierTriggerClientFixture.TARGET;}public net.minecraft.core.BlockPos ack(){return CopifierTriggerClientFixture.ACK;}
  public boolean targetsReady(Minecraft mc){return mc.level.getBlockEntity(target()) instanceof NativeTriggerBlockEntity&&mc.level.getBlockEntity(CopifierTriggerClientFixture.PEER) instanceof NativeTriggerBlockEntity;}
  public boolean copied(net.minecraft.nbt.CompoundTag clipboard){return clipboard.getInt("wall_texture")==7&&clipboard.getInt("trigger_on_texture")==8;}
  private boolean matches(Minecraft mc,boolean changed){int main=changed?7:0;return mc.level.getBlockEntity(target()) instanceof NativeTriggerBlockEntity first&&mc.level.getBlockEntity(CopifierTriggerClientFixture.PEER) instanceof NativeTriggerBlockEntity second&&first.settings().mainTexture==main&&second.settings().mainTexture==main&&first.triggerState().frontOnly==changed&&second.triggerState().frontOnly==changed&&first.triggerState().bands==changed&&second.triggerState().bands==changed;}
  public boolean applied(Minecraft mc){return matches(mc,true);}public boolean undone(Minecraft mc){return matches(mc,false);}public void confirm(int operation){PacketDistributor.sendToServer(new CopifierTriggerClientFixture.Mode(operation));}
 });
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){JOURNEY.tick();}
}
