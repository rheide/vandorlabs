package com.vandorlabs.neoforge.copier;

import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.neoforge.navigation.observer.*;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Complete formed observer snapshots share the native formation geometry and keep distinct identities. */
final class NativeCopyObservers {
    private static final Set<String> CORE = Set.of("id","x","y","z","ObserverKey","ConstructorCoreX","ConstructorCoreZ",
        "ObserverInverted","ObserverWidth","ObserverRadar","TransmittedChannels","TransmissionRange");

    static Set<BlockPos> validate(List<NativeRawCell> cells) {
        var owners = new ArrayList<NativeRawCell>();
        var parts = new HashMap<BlockPos, NativeRawCell>();
        for (var cell : cells) {
            if (cell.state.getBlock() instanceof NativeObserverCoreBlock) owners.add(cell);
            if (cell.state.getBlock() instanceof NativeObserverPartBlock) parts.put(cell.pos, cell);
        }
        var matter = new HashSet<BlockPos>();
        var keys = new HashSet<UUID>();
        for (var owner : owners) {
            var raw = owner.tile();
            if (!owner.state.getValue(NativeObserverCoreBlock.FORMED) || raw == null || !raw.hasUUID("ObserverKey")
                || !keys.add(raw.getUUID("ObserverKey")) || !raw.getAllKeys().stream().allMatch(key -> copySafe(raw,key))) {
                throw new IllegalArgumentException("Copy requires a complete formed observer without foreign ownership");
            }
            var core = (NativeObserverCoreBlock) owner.state.getBlock();
            var design = NativeObserverDesigns.scaled(core.design.id(), raw.getInt("ObserverWidth"));
            var layout = new CoreLayout(design.width(), design.height(), design.depth(),
                owner.state.getValue(NativeObserverCoreBlock.FACING).get3DDataValue(),
                raw.getInt("ConstructorCoreX"), raw.getInt("ConstructorCoreZ"));
            if (!layout.canonicalCorner()) throw new IllegalArgumentException("Unknown observer frame corner");
            boolean inverted = raw.getBoolean("ObserverInverted");
            for (int x=0; x<design.width(); x++) for (int y=0; y<design.height(); y++) for (int z=0; z<design.depth(); z++) {
                var pos = owner.pos.offset(layout.x(x,z), inverted ? -y : y, layout.z(x,z));
                if (pos.equals(owner.pos)) continue;
                var cell = parts.remove(pos);
                if (cell == null || !cell.hasTile()) throw new IllegalArgumentException("Capture the entire observer frame");
                NativeObserverTransactions.requirePart(cell.tile(), owner.pos.subtract(pos), raw.getUUID("ObserverKey"));
                com.vandorlabs.neoforge.transfer.NativeConstructorFrame.validatePart(cell.tile(),layout.frame(x,y,z));
                if (layout.frame(x,y,z)) matter.add(pos);
            }
        }
        if (!parts.isEmpty()) throw new IllegalArgumentException("Capture each observer part with its own frame core");
        return matter;
    }

    private static boolean copySafe(CompoundTag raw, String key) {
        return CORE.contains(key)
            || key.equals("keepPacked") && raw.contains(key,Tag.TAG_BYTE) && raw.getByte(key)==0
            || Set.of("NeoForgeData","components","ForgeCaps","neoforge:attachments").contains(key)
                && raw.contains(key,Tag.TAG_COMPOUND) && raw.getCompound(key).isEmpty();
    }

    static boolean observer(BlockState state) {
        return state.getBlock() instanceof NativeObserverCoreBlock || state.getBlock() instanceof NativeObserverPartBlock;
    }

    static void translateIdentity(NativeRawCell cell, BlockPos destination, UUID copyOwner, CompoundTag raw) {
        if (!observer(cell.state)) return;
        boolean part = cell.state.getBlock() instanceof NativeObserverPartBlock;
        String field = part ? "ObserverOwner" : "ObserverKey";
        var ownerPos = part ? destination.offset(BlockPos.of(raw.getLong("ConstructorOwner"))) : destination;
        var label = "vandorlabs:copy-observer/" + copyOwner + "/" + ownerPos.asLong() + "/" + raw.getUUID(field);
        raw.putUUID(field, UUID.nameUUIDFromBytes(label.getBytes(StandardCharsets.UTF_8)));
    }

    static void requireIdle(ServerLevel world, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof NativeObserverEntity observer) || observer.inert() || observer.pendingFormation())
            throw new IllegalArgumentException("Wait for the observer's physical ownership operation to finish");
    }

    private NativeCopyObservers() {}
}
