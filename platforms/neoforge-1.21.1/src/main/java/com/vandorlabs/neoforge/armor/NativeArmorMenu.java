package com.vandorlabs.neoforge.armor;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.NativeHeldToolMenu;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;

public final class NativeArmorMenu extends NativeHeldToolMenu {
    NativeArmorMenu(int id, Inventory inventory, InteractionHand hand) {
        super(NativeArmorBindings.MENU, id, inventory, hand,
            stack -> stack.getItem() instanceof NativeArmorItem);
    }

    void configure(ServerPlayer player, int choice) {
        if (stillValid(player) && NativeConfigurationAccess.canConfigure(player)
            && NativeArmorItem.configure(heldStack(), choice)) {
            synchronizeTool(player);
            int index = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
            player.connection.send(new ClientboundContainerSetSlotPacket(
                ClientboundContainerSetSlotPacket.PLAYER_INVENTORY, 0, index, heldStack()));
        }
    }
}
