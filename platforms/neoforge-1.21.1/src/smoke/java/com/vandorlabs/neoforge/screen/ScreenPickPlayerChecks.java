package com.vandorlabs.neoforge.screen;
import com.vandorlabs.screen.ScreenFamily;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
/** Calls actual Minecraft.pickBlock; no fake clone stack is sent to the server. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class ScreenPickPlayerChecks {
 private static int step,phase,ticks,wait;private static final java.util.List<Integer> oldBytes=new java.util.ArrayList<>();
 private static void need(boolean value,String message){if(!value)throw new AssertionError("Screen actual pick client step="+step+" phase="+phase+": "+message);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!ScreenPickClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.gameMode==null)return;need(++ticks<2400,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" count="+mc.player.getMainHandItem().getCount()+" state="+mc.player.inventoryMenu.getStateId()+" pos="+mc.player.position()+" screen="+mc.screen);if(step==28){if(mc.level.getBlockState(ScreenPickClientFixture.ACK).is(net.minecraft.world.level.block.Blocks.STONE)){System.out.println("[vandorlabs][screen-pick-client] client PASS picks=28 roles=7 requestBytes="+ScreenPickPayload.BYTES+" oldClientCreativeBodies="+oldBytes);mc.stop();}return;}if(!(mc.level.getBlockEntity(ScreenPickClientFixture.ROOT) instanceof NativeScreenEntity tile)||tile.family()!=ScreenFamily.values()[step/4])return;switch(phase){case 0->{if(mc.screen!=null||mc.gameMode.getPlayerMode()!=GameType.CREATIVE||mc.player.getInventory().selected!=ScreenPickClientFixture.initialSlot(step%4)||mc.player.position().distanceToSqr(.5,96,-3)>.01||ticks<wait+15)return;if(!(mc.hitResult instanceof BlockHitResult hit)||!hit.getBlockPos().equals(ScreenPickClientFixture.ROOT))return;need(!tile.getUpdateTag(mc.level.registryAccess()).toString().contains(ScreenPickClientFixture.MARKER)&&!tile.getUpdateTag(mc.level.registryAccess()).contains("ScreenChipReader"),"normal client projection has no hidden snapshot");var old=tile.getBlockState().getCloneItemStack(hit,mc.level,hit.getBlockPos(),mc.player);var b=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),mc.level.registryAccess());try{b.writeShort(36+mc.player.getInventory().selected);net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(b,old);oldBytes.add(b.readableBytes());}finally{b.release();}try{var method=Minecraft.class.getDeclaredMethod("pickBlock");method.setAccessible(true);method.invoke(mc);}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}phase=1;}
 case 1->{if(mc.player.getInventory().selected!=ScreenPickClientFixture.selectedSlot(step%4)||mc.player.getMainHandItem().getCount()!=ScreenPickClientFixture.pickedCount(step%4))return;var configuration=NativeScreenItemSettings.configuration(mc.player.getMainHandItem());if(configuration==null||!configuration.getString("PrivatePickRoot").equals(ScreenPickClientFixture.MARKER))return;ScreenPickClientFixture.verify(mc.player.getMainHandItem(),mc.level.registryAccess());PacketDistributor.sendToServer(new ScreenPickClientFixture.Ack(step));step++;phase=0;wait=ticks;}}
 }
}
