package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.client.NativeCopifierScreen;
import com.vandorlabs.neoforge.propulsion.NativePropulsionEntity;
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
public final class CopifierPropulsionClientChecks {
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Registration {@SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(CopifierPropulsionClientFixture.enabled())NativeCopifierScreen.register(event,NativeCopifierItemChecks.MENU);}}
 private static final CopifierCopyClientJourney JOURNEY=new CopifierCopyClientJourney(new CopifierCopyClientJourney.Scenario(){
  public boolean enabled(){return CopifierPropulsionClientFixture.enabled();}public String label(){return "copifier-propulsion-client";}
  public net.minecraft.core.BlockPos source(){return CopifierPropulsionClientFixture.SOURCE;}public net.minecraft.core.BlockPos target(){return CopifierPropulsionClientFixture.TARGET;}public net.minecraft.core.BlockPos ack(){return CopifierPropulsionClientFixture.ACK;}
  public boolean targetsReady(Minecraft mc){return mc.level.getBlockEntity(target()) instanceof NativePropulsionEntity&&mc.level.getBlockEntity(CopifierPropulsionClientFixture.PEER) instanceof NativePropulsionEntity;}
  public boolean copied(net.minecraft.nbt.CompoundTag clipboard){return clipboard.getInt("wall_texture")==7&&clipboard.getInt("particles")==3;}
  private boolean matches(Minecraft mc,boolean changed){return mc.level.getBlockEntity(target()) instanceof NativePropulsionEntity first&&mc.level.getBlockEntity(CopifierPropulsionClientFixture.PEER) instanceof NativePropulsionEntity second&&matches(first,changed)&&matches(second,changed);}
  private boolean matches(NativePropulsionEntity owner,boolean changed){var data=owner.settings().data();return data.sideTexture==(changed?7:64)&&data.manualOn==changed&&data.particleLevel==(changed?3:1)&&data.join!=changed;}
  public boolean applied(Minecraft mc){return matches(mc,true);}public boolean undone(Minecraft mc){return matches(mc,false);}public void confirm(int operation){PacketDistributor.sendToServer(new CopifierPropulsionClientFixture.Mode(operation));}
 });
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){JOURNEY.tick();}
}
