package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.client.NativeCopifierScreen;
import com.vandorlabs.neoforge.trapdoor.*;
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
public final class CopifierTrapdoorClientChecks {
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Registration {@SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(CopifierTrapdoorClientFixture.enabled())NativeCopifierScreen.register(event,NativeCopifierItemChecks.MENU);}}
 private static final CopifierCopyClientJourney JOURNEY=new CopifierCopyClientJourney(new CopifierCopyClientJourney.Scenario(){
  public boolean enabled(){return CopifierTrapdoorClientFixture.enabled();}public String label(){return "copifier-trapdoor-client";}
  public net.minecraft.core.BlockPos source(){return CopifierTrapdoorClientFixture.SOURCE;}public net.minecraft.core.BlockPos target(){return CopifierTrapdoorClientFixture.TARGET;}public net.minecraft.core.BlockPos ack(){return CopifierTrapdoorClientFixture.ACK;}
  public boolean targetsReady(Minecraft mc){return mc.level.getBlockEntity(target()) instanceof NativeTrapdoorEntity&&mc.level.getBlockEntity(CopifierTrapdoorClientFixture.PEER) instanceof NativeTrapdoorEntity;}
  public boolean copied(net.minecraft.nbt.CompoundTag clipboard){return clipboard.getInt("wall_texture")==7&&clipboard.getBoolean("door_sliding");}
  private boolean matches(Minecraft mc,boolean changed){if(!(mc.level.getBlockEntity(target()) instanceof NativeTrapdoorEntity first)||!(mc.level.getBlockEntity(CopifierTrapdoorClientFixture.PEER) instanceof NativeTrapdoorEntity second))return false;return matches(first,changed)&&matches(second,changed);}
  private boolean matches(NativeTrapdoorEntity owner,boolean changed){var data=owner.settings().choices();return data.texture==(changed?7:NativeTrapdoorSettings.defaultTexture())&&data.position==(changed?1:0)&&data.sliding==changed&&data.layout()==(changed?2:1)&&data.trigger==(changed?0:1);}
  public boolean applied(Minecraft mc){return matches(mc,true);}public boolean undone(Minecraft mc){return matches(mc,false);}public void confirm(int operation){PacketDistributor.sendToServer(new CopifierTrapdoorClientFixture.Mode(operation));}
 });
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){JOURNEY.tick();}
}
