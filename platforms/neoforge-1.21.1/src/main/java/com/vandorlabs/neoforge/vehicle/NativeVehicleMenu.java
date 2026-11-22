package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public final class NativeVehicleMenu extends AbstractContainerMenu {
    public static final int CONVERT=0, SCAN=1;
    private final SimpleContainerData metrics=new SimpleContainerData(20);
    public final NativeVehicleControllerEntity controller;
    public final NativeGroundVehicle craft;

    NativeVehicleMenu(int window,NativeVehicleControllerEntity controller) {
        super(NativeVehicleBindings.MENU,window);
        addDataSlots(metrics);
        this.controller=controller; this.craft=null;
    }

    NativeVehicleMenu(int window,NativeGroundVehicle craft) {
        super(NativeVehicleBindings.MENU,window);
        addDataSlots(metrics);
        this.controller=null; this.craft=craft;
    }

    NativeVehicleMenu(int window,Inventory inventory,RegistryFriendlyByteBuf buffer) {
        super(NativeVehicleBindings.MENU,window);
        addDataSlots(metrics);
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

    // Vanilla sends each menu property as a signed short. Preserve full counts and float values.
    private void put(int field,int value) {
        metrics.set(field*2,value&0xffff);
        metrics.set(field*2+1,value>>>16);
    }
    private int get(int field) {
        return (metrics.get(field*2)&0xffff) | (metrics.get(field*2+1)<<16);
    }
    private float number(int field) {return Float.intBitsToFloat(get(field));}
    private void number(int field,double value) {put(field,Float.floatToIntBits((float)value));}

    public int blocks() {
        return craft!=null && craft.visual()!=null?craft.visual().cells().size():get(0);
    }
    public NativeVehiclePropulsion.Readout flightReadout() {
        if (craft!=null) return craft.flightReadout();
        if (get(0)<=0) return null;
        return new NativeVehiclePropulsion.Readout(number(1),number(2),get(3),get(4),get(5),number(6),number(7),number(8),get(9)!=0);
    }

    private void scan(ServerPlayer player) {
        try {
            var source=NativeVehicleScan.scan(player.serverLevel(),controller.getBlockPos());
            var info=NativeVehiclePropulsion.preview(source.local(),player.serverLevel(),controller.getBlockPos());
            put(0,source.local().cells().size());
            number(1,info.mass()); number(2,info.effectiveMass()); put(3,info.drives()); put(4,info.fuel());
            put(5,info.capacity()); number(6,info.thrust()); number(7,info.takeoffMass()); number(8,info.ceiling()); put(9,info.canHover()?1:0);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            put(0,-1);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(failure.getMessage()));
        }
        broadcastChanges();
    }

    @Override public boolean clickMenuButton(Player actor,int action) {
        if (!(actor instanceof ServerPlayer player) || player.containerMenu!=this || !stillValid(player)) return false;
        if (action==SCAN && controller!=null) { scan(player); return true; }
        if (action!=CONVERT) return false;
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
