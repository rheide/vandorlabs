package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.client.NativeCopifierScreen;
import com.vandorlabs.neoforge.door.*;
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
public final class CopifierDoorClientChecks {
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Registration {@SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(CopifierDoorClientFixture.enabled()){if(NativeCopifierItemChecks.MENU!=NativeCopifierRegistrations.MENU.get())NativeCopifierScreen.register(event,NativeCopifierItemChecks.MENU);}}}
 private static final CopifierCopyClientJourney JOURNEY=new CopifierCopyClientJourney(new CopifierCopyClientJourney.Scenario(){
  public boolean enabled(){return CopifierDoorClientFixture.enabled();}public String label(){return "copifier-door-client";}
  public net.minecraft.core.BlockPos source(){return CopifierDoorClientFixture.SOURCE.above();}public net.minecraft.core.BlockPos target(){return CopifierDoorClientFixture.TARGET.above();}public net.minecraft.core.BlockPos ack(){return CopifierDoorClientFixture.ACK;}
  public boolean targetsReady(Minecraft mc){return mc.level.getBlockEntity(CopifierDoorClientFixture.TARGET) instanceof NativeProgrammableDoorEntity&&mc.level.getBlockEntity(CopifierDoorClientFixture.PEER) instanceof NativeProgrammableDoorEntity;}
  public boolean copied(net.minecraft.nbt.CompoundTag clipboard){return clipboard.getInt("door_design")==15&&clipboard.getInt("door_face_texture")==7;}
  private boolean matches(Minecraft mc,boolean changed){int design=changed?15:CopifierDoorClientFixture.large()?23:2,face=changed?7:-1,depth=changed?2:CopifierDoorClientFixture.large()?1:0;return mc.level.getBlockEntity(CopifierDoorClientFixture.TARGET) instanceof NativeProgrammableDoorEntity first&&mc.level.getBlockEntity(CopifierDoorClientFixture.PEER) instanceof NativeProgrammableDoorEntity second&&first.settings().door().design==design&&second.settings().door().design==design&&first.settings().faceTexture()==face&&second.settings().faceTexture()==face&&first.settings().depth()==depth&&second.settings().depth()==depth;}
  public boolean applied(Minecraft mc){return matches(mc,true);}public boolean undone(Minecraft mc){return matches(mc,false);}public void confirm(int operation){PacketDistributor.sendToServer(new CopifierDoorClientFixture.Mode(operation));}
 });
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){JOURNEY.tick();}
}
