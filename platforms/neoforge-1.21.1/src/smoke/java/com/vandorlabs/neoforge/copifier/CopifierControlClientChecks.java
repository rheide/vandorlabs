package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.client.NativeCopifierScreen;
import com.vandorlabs.neoforge.NativeSignalControlBlockEntity;
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
public final class CopifierControlClientChecks {
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Registration {@SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(CopifierControlClientFixture.enabled())NativeCopifierScreen.register(event,NativeCopifierItemChecks.MENU);}}
 private static final CopifierCopyClientJourney JOURNEY=new CopifierCopyClientJourney(new CopifierCopyClientJourney.Scenario(){
  public boolean enabled(){return CopifierControlClientFixture.enabled();}public String label(){return "copifier-control-client";}
  public net.minecraft.core.BlockPos source(){return CopifierControlClientFixture.SOURCE;}public net.minecraft.core.BlockPos target(){return CopifierControlClientFixture.TARGET;}public net.minecraft.core.BlockPos ack(){return CopifierControlClientFixture.ACK;}
  public boolean targetsReady(Minecraft mc){return mc.level.getBlockEntity(target()) instanceof NativeSignalControlBlockEntity&&mc.level.getBlockEntity(CopifierControlClientFixture.PEER) instanceof NativeSignalControlBlockEntity;}
  public boolean copied(net.minecraft.nbt.CompoundTag clipboard){return clipboard.getInt("active")==2&&clipboard.getCompound("signal_settings").getInt("LowLimit")==2;}
  private boolean matches(Minecraft mc,boolean changed){int step=changed?2:0,low=changed?2:5,high=changed?12:15,rotation=changed?3:0;return mc.level.getBlockEntity(target()) instanceof NativeSignalControlBlockEntity first&&mc.level.getBlockEntity(CopifierControlClientFixture.PEER) instanceof NativeSignalControlBlockEntity second&&first.getStep()==step&&second.getStep()==step&&first.getLowLimit()==low&&second.getLowLimit()==low&&first.getHighLimit()==high&&second.getHighLimit()==high&&first.mountRotation()==rotation&&second.mountRotation()==rotation;}
  public boolean applied(Minecraft mc){return matches(mc,true);}public boolean undone(Minecraft mc){return matches(mc,false);}public void confirm(int operation){PacketDistributor.sendToServer(new CopifierControlClientFixture.Mode(operation));}
 });
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){JOURNEY.tick();}
}
