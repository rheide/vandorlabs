package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.client.NativeCopifierScreen;
import com.vandorlabs.neoforge.credentials.NativeCredentialReaderEntity;
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
public final class CopifierReaderClientChecks {
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Registration {@SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(CopifierReaderClientFixture.enabled())NativeCopifierScreen.register(event,NativeCopifierItemChecks.MENU);}}
 private static final CopifierCopyClientJourney JOURNEY=new CopifierCopyClientJourney(new CopifierCopyClientJourney.Scenario(){
  public boolean enabled(){return CopifierReaderClientFixture.enabled();}public String label(){return "copifier-reader-client";}
  public net.minecraft.core.BlockPos source(){return CopifierReaderClientFixture.SOURCE;}public net.minecraft.core.BlockPos target(){return CopifierReaderClientFixture.TARGET;}public net.minecraft.core.BlockPos ack(){return CopifierReaderClientFixture.ACK;}
  public boolean targetsReady(Minecraft mc){return mc.level.getBlockEntity(target()) instanceof NativeCredentialReaderEntity&&mc.level.getBlockEntity(CopifierReaderClientFixture.PEER) instanceof NativeCredentialReaderEntity;}
  public boolean copied(net.minecraft.nbt.CompoundTag clipboard){return clipboard.getInt("switch_rotation")==3&&clipboard.getCompound("Transmission").getInt("Range")==64;}
  private boolean matches(Minecraft mc,boolean changed){if(!(mc.level.getBlockEntity(target()) instanceof NativeCredentialReaderEntity first)||!(mc.level.getBlockEntity(CopifierReaderClientFixture.PEER) instanceof NativeCredentialReaderEntity second))return false;return matches(first,changed)&&matches(second,changed);}
  private boolean matches(NativeCredentialReaderEntity owner,boolean changed){var mount=owner.mountSettings();return owner.mountRotation()==(changed?3:0)&&mount.height==(changed?2:0)&&mount.tilt==(changed?1:0)&&mount.direction==(changed?3:0)&&owner.localRedstone()!=changed;}
  public boolean applied(Minecraft mc){return matches(mc,true);}public boolean undone(Minecraft mc){return matches(mc,false);}public void confirm(int operation){PacketDistributor.sendToServer(new CopifierReaderClientFixture.Mode(operation));}
 });
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){JOURNEY.tick();}
}
