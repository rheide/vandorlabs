package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class NativeVehicleMenu extends AbstractContainerMenu {
    public final NativeVehicleControllerEntity controller;
    public final NativeGroundVehicle craft;

    NativeVehicleMenu(int window,NativeVehicleControllerEntity controller) {
        super(NativeVehicleBindings.MENU,window);
        this.controller=controller; this.craft=null;
    }

    NativeVehicleMenu(int window,NativeGroundVehicle craft) {
        super(NativeVehicleBindings.MENU,window);
        this.controller=null; this.craft=craft;
    }

    NativeVehicleMenu(int window,Inventory inventory,RegistryFriendlyByteBuf buffer) {
        super(NativeVehicleBindings.MENU,window);
        var level=inventory.player.level();
        if (buffer.readBoolean()) {
            craft=level.getEntity(buffer.readVarInt()) instanceof NativeGroundVehicle found?found:null;
            controller=null;
        } else {
            var pos=buffer.readBlockPos();
            controller=level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof NativeVehicleControllerEntity tile?tile:null;
            craft=null;
        }
    }

    static boolean near(Player actor,NativeGroundVehicle craft) {
        return craft!=null && craft.isAlive() && actor.isAlive() && !actor.isSpectator() && actor.level()==craft.level()
                && (actor.getVehicle()==craft || craft.getBoundingBox().distanceToSqr(actor.getEyePosition())<=36);
    }

    @Override public boolean stillValid(Player actor) {
        return controller!=null?NativeConfigurationAccess.canEditDialog(actor,controller):near(actor,craft);
    }

    @Override public boolean clickMenuButton(Player actor,int action) {
        if (!(actor instanceof ServerPlayer player) || player.containerMenu!=this || action!=0 || !stillValid(player)) return false;
        if (controller!=null && !controller.mayOperate(player)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Insert the accepted Access Card in the Controller's reader."));
            return false;
        }
        player.closeContainer();
        if (controller!=null) NativeVehicleTransfer.assemble(player,controller.getBlockPos());
        else NativeVehicleTransfer.park(player,craft);
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player,int slot) { return ItemStack.EMPTY; }
}
