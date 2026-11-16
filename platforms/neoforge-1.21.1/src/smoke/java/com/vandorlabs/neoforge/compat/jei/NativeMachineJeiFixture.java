package com.vandorlabs.neoforge.compat.jei;
import com.vandorlabs.neoforge.infuser.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
/** Server assertion for JEI's own authenticated transfer packet; no fixture transfer implementation. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeMachineJeiFixture {
 public static final BlockPos POS=new BlockPos(0,96,0);private static java.util.UUID actor;
 public static boolean enabled(){return Boolean.getBoolean("vandorlabs.machineJeiCheck");}
 private static void need(boolean v,String why){if(!v)throw new AssertionError("Native machine JEI: "+why);}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(!enabled()||!(e.getEntity() instanceof ServerPlayer p))return;actor=p.getUUID();var w=p.serverLevel();for(int x=-2;x<=2;x++)for(int z=-4;z<=2;z++){w.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<100;y++)w.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}w.setBlock(POS,NativeInfuserBindings.BLOCK.defaultBlockState(),3);w.setBlock(POS.above(),Blocks.STONE.defaultBlockState(),3);p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getInventory().selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.getInventory().setItem(9,new ItemStack(Items.IRON_INGOT));p.getInventory().setItem(10,new ItemStack(Items.REDSTONE,7));p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));p.inventoryMenu.broadcastChanges();float pitch=(float)Math.toDegrees(Math.atan2(p.getEyeHeight()-.5,2.5));p.connection.teleport(.5,96,-2.5,0,pitch);System.out.println("[vandorlabs][machine-jei] setup PASS Survival/realInventory/pausedInfuser");}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(!enabled()||!e.getEntity().getUUID().equals(actor)||!(e.getEntity() instanceof ServerPlayer p))return;need(p.level().getBlockEntity(POS) instanceof NativeInfuserEntity,"Infuser exists");var t=(NativeInfuserEntity)p.level().getBlockEntity(POS);need(t.getItem(0).is(Items.IRON_INGOT)&&t.getItem(0).getCount()==1&&t.getItem(1).is(Items.REDSTONE)&&t.getItem(1).getCount()==7&&t.getItem(2).isEmpty(),"JEI packet moved iron1+redstone7 into actual machine");need(p.getInventory().getItem(9).isEmpty()&&p.getInventory().getItem(10).isEmpty(),"sole recipe ownership removed from player");System.out.println("[vandorlabs][machine-jei] logout PASS actualJEIpacket/exactServerSlotOwnership");}
 private NativeMachineJeiFixture(){}
}
