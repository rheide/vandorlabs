package com.vandorlabs.client;

import com.vandorlabs.container.ContainerStorageStatus;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketWindowProperty;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;

/** Actual Container listener lifecycle and vanilla SPacketWindowProperty round trips. */
public final class StorageStatusDeltaChecks {
    private static final class Listener implements IContainerListener {
        final ContainerStorageStatus client = new ContainerStorageStatus(new TileEntity() { });
        private final java.util.function.BiConsumer<Integer,Integer> receive;
        Listener(){receive=client::updateProgressBar;}
        Listener(java.util.function.BiConsumer<Integer,Integer> receive){this.receive=receive;}
        int messages, bytes;
        @Override public void sendAllContents(Container container, NonNullList<ItemStack> items) { }
        @Override public void sendSlotContents(Container container, int slot, ItemStack stack) { }
        @Override public void sendAllWindowProperties(Container container, IInventory inventory) { }
        @Override public void sendWindowProperty(Container container, int property, int value) {
            PacketBuffer wire = new PacketBuffer(Unpooled.buffer());
            try {
                new SPacketWindowProperty(container.windowId, property, value).writePacketData(wire);
                bytes += wire.readableBytes();
                SPacketWindowProperty decoded = new SPacketWindowProperty();
                decoded.readPacketData(wire);
                receive.accept(decoded.getProperty(), decoded.getValue());
                messages++;
            } catch (IOException failure) { throw new AssertionError(failure); }
            finally { wire.release(); }
        }
        void reset() { messages = bytes = 0; }
    }
    private static void need(boolean yes, String message) { if (!yes) throw new AssertionError(message); }
    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.register();
        ContainerStorageStatus server = new ContainerStorageStatus(new TileEntity() { });
        int[] expected = {65535, Integer.MAX_VALUE, 0xff3f76e4};
        System.arraycopy(expected, 0, server.values, 0, expected.length);
        Listener first = new Listener();
        server.addListener(first);
        need(first.messages == 6 && Arrays.equals(first.client.values, expected), "initial addListener snapshot");
        first.reset();
        for (int i = 0; i < 1000; i++) server.detectAndSendChanges();
        need(first.messages == 0, "idle container still sent window properties");
        Listener late = new Listener();
        server.addListener(late);
        need(first.messages == 0 && late.messages == 6 && Arrays.equals(late.client.values, expected), "late listener snapshot");
        late.reset();
        expected[0] = 65536;
        server.values[0] = expected[0];
        server.detectAndSendChanges();
        need(first.messages == 2 && late.messages == 2, "one changed field did not send exactly two properties per listener");
        need(Arrays.equals(first.client.values, expected) && Arrays.equals(late.client.values, expected), "16-bit carry lost");
        server.removeListener(first);
        first.reset(); late.reset();
        server.detectAndSendChanges();
        need(first.messages == 0 && late.messages == 0, "removed listener still received data");
        server.addListener(first);
        need(first.messages == 6 && late.messages == 0, "re-added listener did not receive a new complete snapshot");
        expected[1] = Integer.MIN_VALUE; expected[2] = -1;
        System.arraycopy(expected, 0, server.values, 0, expected.length);
        first.reset(); late.reset(); server.detectAndSendChanges();
        need(first.messages == 4 && Arrays.equals(first.client.values, expected), "full-width signed capacity/color round trip");
        batteryMeter();
        System.out.println("storage-status-delta PASS: actual Container initial/late/removal lifecycle and vanilla packet bytes; "
                + "1000 idle samples before=6000 messages after=0; one changed field before=6 after=2 messages per listener");
    }
    @SuppressWarnings("unchecked")
    private static void batteryMeter(){
        // Standalone Bootstrap does not run Forge capability injection. Use the real registered capability.
        try{if(net.minecraftforge.energy.CapabilityEnergy.ENERGY==null){
            java.lang.reflect.Field field=net.minecraftforge.common.capabilities.CapabilityManager.class.getDeclaredField("providers");field.setAccessible(true);
            java.util.Map<?,?> providers=(java.util.Map<?,?>)field.get(net.minecraftforge.common.capabilities.CapabilityManager.INSTANCE);String key=net.minecraftforge.energy.IEnergyStorage.class.getName().intern();
            if(!providers.containsKey(key))net.minecraftforge.energy.CapabilityEnergy.register();
            net.minecraftforge.energy.CapabilityEnergy.ENERGY=(net.minecraftforge.common.capabilities.Capability<net.minecraftforge.energy.IEnergyStorage>)providers.get(key);
        }}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
        need(net.minecraftforge.energy.CapabilityEnergy.ENERGY!=null,"actual Forge energy capability initialized");
        com.vandorlabs.tiles.TileEntityBattery battery=new com.vandorlabs.tiles.TileEntityBattery();
        net.minecraft.nbt.NBTTagCompound saved=new net.minecraft.nbt.NBTTagCompound();saved.setInteger("Energy",99993);battery.readFromNBT(saved);
        ContainerStorageStatus previous=new ContainerStorageStatus(battery);
        com.vandorlabs.ports.ResourcePortContainer server=new com.vandorlabs.ports.ResourcePortContainer(battery),client=new com.vandorlabs.ports.ResourcePortContainer(battery);
        Listener before=new Listener(),after=new Listener(client::updateProgressBar);previous.addListener(before);server.addListener(after);
        need(before.messages==6&&before.bytes==30&&after.messages==2&&after.bytes==10&&client.batteryValues[0]==99993,"Battery exact initial packet counts and full-width charge roundtrip");
        before.reset();after.reset();for(int n=0;n<1000;n++){previous.detectAndSendChanges();server.detectAndSendChanges();}
        need(before.messages==0&&after.messages==0,"Battery idle meter sends no packets");
        saved.setInteger("Energy",65536);battery.readFromNBT(saved);server.detectAndSendChanges();
        need(after.messages==2&&after.bytes==10&&client.batteryValues[0]==65536,"Battery charge carry survives signed-short wire halves");
        System.out.println("battery-meter-packets PASS initial before6/30bytes after2/10bytes idle1000=0 changed2/10bytes roundtrip99993/65536");
    }
    private StorageStatusDeltaChecks() { }
}
