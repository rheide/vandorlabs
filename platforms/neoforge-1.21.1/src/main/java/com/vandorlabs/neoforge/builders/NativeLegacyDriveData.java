package com.vandorlabs.neoforge.builders;

import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import java.util.Arrays;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Converts a Forge drive/computer tile after the importer has resolved its block and tile IDs. */
final class NativeLegacyDriveData {
    private static final Set<String> LEGACY_FIELDS = Set.of(
            "InsertedToken", "MachineSurfaceTextures", "NavigationRedstone", "NavigationAnimated", "NavigationSpeed",
            "NavigationTexture", "NavigationSecurity", "NavigationScreenOn", "DriveSignalLevels", "DriveInverted", "DriveParticles",
            "DriveParticleThreshold", "DriveLevel", "RedstoneChannels", "RedstoneChannel", "ChipSlot", "AnyToken",
            "AllowedTokens", "TokenLogo", "TokenBehavior", "TokenRejectedUntil", "TokenPulseUntil",
            "TokenToggled", "TokenToggleApplied", "TokenInsertStarted", "TokenInsertionSequence",
            "TokenReturnToMost", "TokenReturnToLeast");

    static CompoundTag convert(CompoundTag source, NativeDriveDesign drive, HolderLookup.Provider registries) {
        if (drive == null || LEGACY_FIELDS.stream().noneMatch(source::contains)) return source;
        if (source.sizeInBytes() > 64 * 1024 || source.contains("Drive") || source.contains("Channels")
                || source.contains("Inventory") || source.contains("Synthesis") || source.contains("BuilderKey")
                || source.contains("SynthesisBatterySequences") || source.contains("SynthesisActivation")
                || source.contains("CoreRenderReady") || source.contains("TokenReturnToMost")
                || source.contains("TokenReturnToLeast") || flag(source, "NavigationSecurity", false)
                || integer(source, "TokenBehavior", 0) != 0)
            throw new IllegalArgumentException("Ambiguous legacy drive snapshot");

        // These token fields are Forge's reader bookkeeping. They do not set standalone controls.
        for (String key : Set.of("ChipSlot", "AnyToken", "TokenToggled", "TokenToggleApplied", "NavigationScreenOn"))
            flag(source, key, false);
        for (String key : Set.of("TokenLogo", "AllowedTokens", "NavigationTexture"))
            requireType(source, key, Tag.TAG_INT);
        for (String key : Set.of("TokenPulseUntil", "TokenInsertStarted", "TokenInsertionSequence", "TokenRejectedUntil"))
            requireType(source, key, Tag.TAG_LONG);

        var defaults = NativeDriveSettings.defaults(drive.exhaust);
        requireType(source, "MachineSurfaceTextures", Tag.TAG_INT_ARRAY);
        int[] finishes = source.getIntArray("MachineSurfaceTextures");
        if (finishes.length > (drive.computer() ? 1 : 2)) throw new IllegalArgumentException("Legacy drive surfaces");
        int particles = integer(source, "DriveParticles", defaults.particles());
        int savedLevel = integer(source, "DriveLevel", 15);
        if (particles < 0 || particles > 3 || savedLevel < 0 || savedLevel > 15)
            throw new IllegalArgumentException("Legacy drive settings range");
        var settings = new NativeDriveSettings(flag(source, "NavigationRedstone", defaults.redstone()),
                flag(source, "DriveInverted", defaults.inverted()), flag(source, "DriveSignalLevels", defaults.levels()),
                particles, integer(source, "DriveParticleThreshold", defaults.threshold()),
                finishes.length > 0 ? finishes[0] : defaults.display(), finishes.length > 1 ? finishes[1] : defaults.keyboard(),
                flag(source, "NavigationAnimated", defaults.animated()), integer(source, "NavigationSpeed", defaults.speed()));
        int activity = integer(source, "MachineActivity", drive.defaultActivity());
        if (activity < 0 || activity > 2) throw new IllegalArgumentException("Legacy drive activity range");
        requireType(source, "RedstoneChannels", Tag.TAG_INT_ARRAY);
        int channel = integer(source, "RedstoneChannel", 0);
        if (channel < 0) throw new IllegalArgumentException("Legacy drive channel");
        int[] channels = source.contains("RedstoneChannels") ? source.getIntArray("RedstoneChannels") : new int[]{channel};
        var bank = com.vandorlabs.redstone.ChannelList.of(channels);
        if (source.contains("RedstoneChannels") && (!Arrays.equals(channels, bank.toArray())
                || source.contains("RedstoneChannel") && channel != bank.first()))
            throw new IllegalArgumentException("Conflicting legacy drive channels");

        ItemStack[] items = new ItemStack[7];
        Arrays.fill(items, ItemStack.EMPTY);
        requireType(source, "InsertedToken", Tag.TAG_COMPOUND);
        if (source.contains("InsertedToken")) {
            var raw = source.getCompound("InsertedToken");
            if (!drive.hasChip() || !flag(source, "ChipSlot", true) || !raw.getString("id").equals("vandorlabs:data_chip")
                    || !raw.contains("Count", Tag.TAG_BYTE) || raw.getByte("Count") != 1
                    || !raw.contains("Damage", Tag.TAG_SHORT) || raw.getShort("Damage") < 0)
                throw new IllegalArgumentException("Legacy drive chip ownership");
            requireType(raw, "tag", Tag.TAG_COMPOUND);
            requireType(raw, "ForgeCaps", Tag.TAG_COMPOUND);
            items[3] = NativeCredentialItems.readStack(raw, registries);
            if (!NativeCredentialItems.accepts(items[3], true)
                    || !raw.equals(NativeCredentialItems.legacyCredentialData(items[3])))
                throw new IllegalArgumentException("Legacy drive chip contents");
        }

        var canonical = source.copy();
        LEGACY_FIELDS.forEach(canonical::remove);
        canonical.put("Drive", settings.save());
        canonical.putIntArray("Channels", bank.toArray());
        canonical.put("Inventory", NativeBuilderData.save(items, registries));
        return canonical;
    }

    private static boolean flag(CompoundTag tag, String key, boolean fallback) {
        requireType(tag, key, Tag.TAG_BYTE);
        if (!tag.contains(key)) return fallback;
        byte value = tag.getByte(key);
        if (value != 0 && value != 1) throw new IllegalArgumentException("Legacy drive flag " + key);
        return value == 1;
    }

    private static int integer(CompoundTag tag, String key, int fallback) {
        requireType(tag, key, Tag.TAG_INT);
        return tag.contains(key) ? tag.getInt(key) : fallback;
    }

    private static void requireType(CompoundTag tag, String key, int type) {
        if (tag.contains(key) && !tag.contains(key, type))
            throw new IllegalArgumentException("Legacy drive field type " + key);
    }

    private NativeLegacyDriveData() {}
}
