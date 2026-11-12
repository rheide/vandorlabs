package com.vandorlabs.network;

import com.vandorlabs.vehicle.VehicleNetwork;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** Packet round trips and watcher/rider overlap without opening a network connection. */
public final class TrackingPacketChecks {
    private static final class Delivery implements BiConsumer<IMessage, String> {
        final Map<String, Integer> messages = new HashMap<>();
        int total, bytes;

        @Override public void accept(IMessage message, String viewer) {
            byte[] encoded = encode(message);
            IMessage decoded = message instanceof MessageGearMotion
                    ? new MessageGearMotion() : new VehicleNetwork.Packet();
            ByteBuf input = Unpooled.wrappedBuffer(encoded);
            try {
                decoded.fromBytes(input);
                need(!input.isReadable(), "packet consumed all bytes");
                if (decoded instanceof VehicleNetwork.Packet) {
                    need(((VehicleNetwork.Packet) decoded).valid, "valid vehicle packet");
                }
                need(Arrays.equals(encoded, encode(decoded)), "byte-exact message round trip");
            } finally { input.release(); }
            messages.put(viewer, messages.getOrDefault(viewer, 0) + 1);
            total++;
            bytes += encoded.length;
        }

        int count(String viewer) { return messages.getOrDefault(viewer, 0); }
    }

    private static byte[] encode(IMessage message) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            message.toBytes(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally { buffer.release(); }
    }

    private static void need(boolean condition, String detail) {
        if (!condition) throw new AssertionError("Tracking packet delivery: " + detail);
    }

    private static Set<String> watchers(String... viewers) {
        return new LinkedHashSet<>(Arrays.asList(viewers));
    }

    private static Delivery send(IMessage message, Set<String> tracking, List<String> riders) {
        Delivery delivery = new Delivery();
        // This is the existing Forge broadcast; exercise the same missing-rider
        // fallback as the production sender after it has reached these viewers.
        for (String viewer : tracking) delivery.accept(message, viewer);
        TrackingPackets.sendMissing(message, tracking, riders, delivery);
        return delivery;
    }

    public static void run() {
        VehicleNetwork.Packet state = new VehicleNetwork.Packet(VehicleNetwork.STATE);
        state.entity = 41;
        state.token = new UUID(123, 456);
        state.serverTick = 100;
        state.x = -12.5;
        state.y = 72;
        state.z = 31.25;
        state.speed = .25;
        state.yaw = 90;
        need(encode(state).length == 82, "legacy state wire format unchanged");

        Set<String> tracked = watchers("driver", "observer");
        List<String> driver = Collections.singletonList("driver");
        Delivery before = new Delivery(), after = new Delivery();
        for (int i = 0; i < 1000; i++) {
            state.serverTick++;
            for (String viewer : tracked) before.accept(state, viewer);
            before.accept(state, "driver");
            for (String viewer : tracked) after.accept(state, viewer);
            TrackingPackets.sendMissing(state, tracked, driver, after);
        }
        need(before.total == 3000 && after.total == 2000, "tracked driver/observer message counts");
        need(before.bytes == 246000 && after.bytes == 164000, "measured encoded payload totals");
        need(before.count("driver") == 2000 && after.count("driver") == 1000,
                "driver receives one state instead of two");
        need(before.count("observer") == 1000 && after.count("observer") == 1000,
                "observer frequency unchanged");

        // All seats retain their fallback before tracking starts, as individual
        // watchers arrive/leave, and after a passenger dismounts.
        List<String> riders = Arrays.asList("driver", "passenger");
        for (Set<String> tracking : Arrays.asList(watchers(), watchers("driver"),
                watchers("passenger"), watchers("driver", "passenger", "observer"))) {
            Delivery result = send(state, tracking, riders);
            need(result.count("driver") == 1 && result.count("passenger") == 1,
                    "untracked and tracked seats receive exactly once");
            need(result.count("observer") == (tracking.contains("observer") ? 1 : 0),
                    "only watched observers receive state");
            need(result.count("unrelated") == 0, "no wider audience");
        }
        Delivery dismounted = send(state, watchers("observer"), driver);
        need(dismounted.count("passenger") == 0 && dismounted.count("driver") == 1,
                "dismounted nonwatcher stops receiving fallback");

        // Scoped motion and the other migrated senders keep exactly their
        // existing packet formats; only recipient duplication changes.
        state.associationGeneration = new UUID(789, 123);
        need(encode(state).length == 103, "scoped state wire format unchanged");
        send(state, watchers("driver", "passenger", "observer"), riders);
        for (int type : new int[] {VehicleNetwork.DOORS, VehicleNetwork.CANOPIES, VehicleNetwork.TILE}) {
            VehicleNetwork.Packet packet = new VehicleNetwork.Packet(type);
            packet.entity = state.entity;
            packet.token = state.token;
            packet.associationGeneration = state.associationGeneration;
            packet.data = new byte[type == VehicleNetwork.DOORS ? 18 : type == VehicleNetwork.CANOPIES ? 13 : 128];
            Delivery result = send(packet, watchers("driver", "passenger", "observer"), riders);
            need(result.total == 3, "control update has three unique recipients");
        }
        MessageGearMotion gear = new MessageGearMotion();
        gear.entity = state.entity;
        gear.craft = state.token;
        gear.associationGeneration = state.associationGeneration;
        MessageGearMotion.Pose pose = new MessageGearMotion.Pose();
        pose.pos = BlockPos.ORIGIN;
        pose.revision = 7;
        pose.progress = .5;
        gear.poses.add(pose);
        need(encode(gear).length == 76, "scoped gear wire format unchanged");
        need(send(gear, watchers("driver", "observer"), riders).total == 3,
                "gear reaches tracked viewers and missing passenger once");

        System.out.println("tracking-packets PASS 1000 state updates driver+observer "
                + "messages-before=" + before.total + " messages-after=" + after.total
                + " payload-bytes-before=" + before.bytes + " payload-bytes-after=" + after.bytes
                + " driver=2000->1000 observer=1000->1000 "
                + "watcher-arrival/departure passenger-fallback dismount scoped-packet-roundtrips");
    }

    private TrackingPacketChecks() { }
}
