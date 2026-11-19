package com.vandorlabs.neoforge.armor;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.copifier.NativeCopifierItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = "vandorlabs")
public final class NativeArmorStand {
    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteractSpecific event) {
        var player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof ArmorStand stand)
            || !accessible(player, stand)) return;
        var tool = player.getMainHandItem();
        boolean configure = tool.is(VandorLabsBootstrap.CONFIGURIZER.get());
        if (!configure && !(tool.getItem() instanceof NativeCopifierItem)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));

        var slot = clickedSlot(stand, event.getLocalPos().y);
        if (slot == null || !NativeArmorCopy.supports(stand.getItemBySlot(slot))) return;
        if (player instanceof ServerPlayer server) {
            if (tool.getItem() instanceof NativeCopifierItem copifier) {
                copifier.transferArmorAppearance(server, stand.getItemBySlot(slot));
                return;
            }
            server.openMenu(new SimpleMenuProvider(
                (id, inventory, owner) -> new NativeArmorMenu(id, inventory, stand, slot),
                stand.getItemBySlot(slot).getHoverName()), buffer -> {
                    buffer.writeBoolean(true);
                    buffer.writeVarInt(stand.getId());
                    buffer.writeEnum(slot);
                });
        }
    }

    static boolean canConfigure(Player player, ArmorStand stand) {
        return accessible(player, stand) && player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get());
    }

    private static boolean accessible(Player player, ArmorStand stand) {
        return player.isAlive() && !player.isSpectator() && stand.isAlive() && !stand.isMarker()
            && stand.level() == player.level() && player.canInteractWithEntity(stand, 1.0);
    }

    private static EquipmentSlot clickedSlot(ArmorStand stand, double y) {
        if (!Double.isFinite(y) || y < 0 || y > stand.getBbHeight() + .0625) return null;
        boolean small = stand.isSmall();
        double height = y / (stand.getScale() * stand.getAgeScale());
        if (height >= .1 && height < (small ? .9 : .55) && stand.hasItemInSlot(EquipmentSlot.FEET)) {
            return EquipmentSlot.FEET;
        }
        if (height >= (small ? 1.2 : .9) && height < (small ? 1.9 : 1.6)
            && stand.hasItemInSlot(EquipmentSlot.CHEST)) return EquipmentSlot.CHEST;
        if (height >= .4 && height < (small ? 1.4 : 1.2) && stand.hasItemInSlot(EquipmentSlot.LEGS)) {
            return EquipmentSlot.LEGS;
        }
        return height >= 1.6 && stand.hasItemInSlot(EquipmentSlot.HEAD) ? EquipmentSlot.HEAD : null;
    }

    private NativeArmorStand() {}
}
