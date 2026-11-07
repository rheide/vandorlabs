package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.client.NativeCopifierScreen;
import com.vandorlabs.neoforge.storage.*;
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
public final class CopifierStorageClientChecks {
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Registration {@SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(CopifierStorageClientFixture.enabled())NativeCopifierScreen.register(event,NativeCopifierItemChecks.MENU);}}
 private static final CopifierCopyClientJourney JOURNEY=new CopifierCopyClientJourney(new CopifierCopyClientJourney.Scenario(){
  public boolean enabled(){return CopifierStorageClientFixture.enabled();}public String label(){return "copifier-storage-client";}
  public net.minecraft.core.BlockPos source(){return CopifierStorageClientFixture.SOURCE;}public net.minecraft.core.BlockPos target(){return CopifierStorageClientFixture.TARGET;}public net.minecraft.core.BlockPos ack(){return CopifierStorageClientFixture.ACK;}
  public boolean targetsReady(Minecraft mc){return mc.level.getBlockEntity(target()) instanceof NativeStorageEntity&&mc.level.getBlockEntity(CopifierStorageClientFixture.PEER) instanceof NativeStorageEntity;}
  public boolean copied(net.minecraft.nbt.CompoundTag clipboard){return clipboard.getInt("wall_texture")==7&&clipboard.getCompound("face_textures").getBoolean("enabled");}
  private boolean matches(Minecraft mc,boolean changed){int main=changed?7:NativeStorageSettings.fresh().housing().mainTexture;return mc.level.getBlockEntity(target()) instanceof NativeStorageEntity first&&mc.level.getBlockEntity(CopifierStorageClientFixture.PEER) instanceof NativeStorageEntity second&&first.settings().housing().mainTexture==main&&second.settings().housing().mainTexture==main&&first.settings().housing().facesEnabled==changed&&second.settings().housing().facesEnabled==changed;}
  public boolean applied(Minecraft mc){return matches(mc,true);}public boolean undone(Minecraft mc){return matches(mc,false);}public void confirm(int operation){PacketDistributor.sendToServer(new CopifierStorageClientFixture.Mode(operation));}
 });
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){JOURNEY.tick();}
}
