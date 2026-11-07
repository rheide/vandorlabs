package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.client.NativeCopifierScreen;
import com.vandorlabs.neoforge.segmented.*;
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
public final class CopifierSegmentedClientChecks {
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Registration {@SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(CopifierSegmentedClientFixture.enabled()){if(NativeCopifierItemChecks.MENU!=NativeCopifierRegistrations.MENU.get())NativeCopifierScreen.register(event,NativeCopifierItemChecks.MENU);}}}
 private static final CopifierCopyClientJourney JOURNEY=new CopifierCopyClientJourney(new CopifierCopyClientJourney.Scenario(){
  public boolean enabled(){return CopifierSegmentedClientFixture.enabled();}public String label(){return "copifier-segmented-client";}
  public net.minecraft.core.BlockPos source(){return CopifierSegmentedClientFixture.SOURCE;}public net.minecraft.core.BlockPos target(){return CopifierSegmentedClientFixture.TARGET;}public net.minecraft.core.BlockPos ack(){return CopifierSegmentedClientFixture.ACK;}
  public boolean targetsReady(Minecraft mc){return mc.level.getBlockEntity(target()) instanceof NativeSegmentedDoorEntity&&mc.level.getBlockEntity(CopifierSegmentedClientFixture.PEER) instanceof NativeSegmentedDoorEntity;}
  public boolean copied(net.minecraft.nbt.CompoundTag clipboard){return clipboard.getCompound("segmented_door").getInt("SegmentedSpeed")==7&&clipboard.getInt("redstone_channel")==31;}
  private boolean matches(Minecraft mc,boolean changed){for(var pos:new net.minecraft.core.BlockPos[]{CopifierSegmentedClientFixture.TARGET,CopifierSegmentedClientFixture.TARGET.above(),CopifierSegmentedClientFixture.PEER,CopifierSegmentedClientFixture.PEER.above()}){if(!(mc.level.getBlockEntity(pos) instanceof NativeSegmentedDoorEntity owner))return false;var settings=owner.settings();if(changed){if(settings.direction!=3||settings.position!=2||!settings.window||owner.ticksPerSection()!=7)return false;}else if(!settings.same(NativeSegmentedDoorSettings.DEFAULT)||owner.ticksPerSection()!=10||!owner.localRedstone())return false;}return true;}
  public boolean applied(Minecraft mc){return matches(mc,true);}public boolean undone(Minecraft mc){return matches(mc,false);}public void confirm(int operation){PacketDistributor.sendToServer(new CopifierSegmentedClientFixture.Mode(operation));}
 });
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){JOURNEY.tick();}
}
