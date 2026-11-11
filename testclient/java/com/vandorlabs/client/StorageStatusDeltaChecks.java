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
                client.updateProgressBar(decoded.getProperty(), decoded.getValue());
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
        System.out.println("storage-status-delta PASS: actual Container initial/late/removal lifecycle and vanilla packet bytes; "
                + "1000 idle samples before=6000 messages after=0; one changed field before=6 after=2 messages per listener");
    }
    private StorageStatusDeltaChecks() { }
}
