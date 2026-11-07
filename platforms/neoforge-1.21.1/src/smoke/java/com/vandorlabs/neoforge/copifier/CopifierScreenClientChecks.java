package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.client.NativeCopifierScreen;
import com.vandorlabs.neoforge.screen.*;
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
public final class CopifierScreenClientChecks {
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)public static final class Registration {@SubscribeEvent public static void screens(RegisterMenuScreensEvent event){if(CopifierScreenClientFixture.enabled()){if(NativeCopifierItemChecks.MENU!=NativeCopifierRegistrations.MENU.get())NativeCopifierScreen.register(event,NativeCopifierItemChecks.MENU);}}}
 private static final CopifierCopyClientJourney JOURNEY=new CopifierCopyClientJourney(new CopifierCopyClientJourney.Scenario(){
  public boolean enabled(){return CopifierScreenClientFixture.enabled();}public String label(){return "copifier-screen-client";}
  public net.minecraft.core.BlockPos source(){return CopifierScreenClientFixture.SOURCE;}public net.minecraft.core.BlockPos target(){return CopifierScreenClientFixture.TARGET;}public net.minecraft.core.BlockPos ack(){return CopifierScreenClientFixture.ACK;}
  public boolean targetsReady(Minecraft mc){return mc.level.getBlockEntity(target()) instanceof NativeScreenEntity&&mc.level.getBlockEntity(CopifierScreenClientFixture.PEER) instanceof NativeScreenEntity;}
  public boolean copied(net.minecraft.nbt.CompoundTag clipboard){return clipboard.getInt("wall_texture")==7&&clipboard.getCompound("redstone_rows").getCompound("Primary").getString("Title").equals("COPIED PANEL");}
  private boolean matches(Minecraft mc,boolean changed){if(!(mc.level.getBlockEntity(target()) instanceof NativeScreenEntity first)||!(mc.level.getBlockEntity(CopifierScreenClientFixture.PEER) instanceof NativeScreenEntity second))return false;return matches(first,changed)&&matches(second,changed);}
  private boolean matches(NativeScreenEntity owner,boolean changed){if(changed)return owner.screenSettings().housing().mainTexture==7&&owner.screenSettings().scalar().smallInput&&owner.controls(0).enabled()&&owner.controls(0).rows().size()==2&&owner.screenSettings().secondarySurface()==-4;return owner.screenSettings().housing().mainTexture==NativeScreenCatalog.INSTANCE.defaultHousing()&&!owner.screenSettings().scalar().smallInput&&!owner.controls(0).enabled()&&owner.controls(0).rows().isEmpty()&&owner.screenSettings().secondarySurface()==-1;}
  public boolean applied(Minecraft mc){return matches(mc,true);}public boolean undone(Minecraft mc){return matches(mc,false);}public void confirm(int operation){PacketDistributor.sendToServer(new CopifierScreenClientFixture.Mode(operation));}
 });
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){JOURNEY.tick();}
}
