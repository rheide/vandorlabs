package com.vandorlabs.neoforge.builders;

import com.vandorlabs.machines.BuilderRecipeCatalog;
import com.vandorlabs.machines.SynthesisRecipePolicy;
import com.vandorlabs.neoforge.gear.NativeGearBindings;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.neoforge.inventory.NativeInventoryItems;
import com.vandorlabs.neoforge.rack.NativeRackData;
import com.vandorlabs.rack.ModuleRackPolicy;
import java.util.Arrays;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Converts an imported Forge constructor tile after the importer has resolved its block and tile IDs. */
final class NativeLegacyBuilderData {
    private static final Set<String> LEGACY_FIELDS = Set.of(
            "InsertedToken", "MachineSurfaceTextures", "NavigationRedstone", "NavigationAnimated", "NavigationSpeed",
            "NavigationTexture", "NavigationSecurity", "NavigationScreenOn", "DriveSignalLevels", "DriveInverted", "DriveParticles",
            "DriveParticleThreshold", "DriveLevel", "RedstoneChannels", "RedstoneChannel", "ChipSlot", "AnyToken",
            "AllowedTokens", "TokenLogo", "TokenBehavior", "TokenRejectedUntil", "TokenPulseUntil",
            "TokenToggled", "TokenToggleApplied", "TokenInsertStarted", "TokenInsertionSequence",
            "TokenReturnToMost", "TokenReturnToLeast", "SynthesisBatterySequences", "SynthesisActivation");
    private static final Set<String> WORK_FIELDS = Set.of(
            "Inventory", "Selected", "SelectedRecipe", "Building", "BuildingRecipe", "Work", "WorkCost", "Active", "Connected", "Mode");
    private static final Set<String> BAY_FIELDS = Set.of("Bay0", "Bay1", "Bay2", "Bay3");

    static boolean supports(NativeBuilderDesigns.Design design) {
        return design.drive() != null || BuilderRecipeCatalog.machine(design.id());
    }

    static CompoundTag convert(CompoundTag source, NativeBuilderDesigns.Design design, boolean formed,
                               HolderLookup.Provider registries) {
        var drive = design.drive();
        boolean legacy = LEGACY_FIELDS.stream().anyMatch(source::contains)
                || source.getCompound("Synthesis").contains("Inventory");
        if (!supports(design) || !legacy) return source;
        if (source.sizeInBytes() > 64 * 1024 || source.contains("Drive") || source.contains("Channels")
                || source.contains("Inventory") || source.contains("BuilderKey") || source.contains("Activation")
                || source.contains("BatterySequences") || source.contains("CoreRenderReady")
                || source.contains("TokenReturnToMost") || source.contains("TokenReturnToLeast")
                || flag(source, "NavigationSecurity", false) || integer(source, "TokenBehavior", 0) != 0)
            throw new IllegalArgumentException("Ambiguous legacy builder snapshot");
        if (drive == null && !formed)
            throw new IllegalArgumentException("Unsupported Forge production ownership");

        // These token fields are Forge's reader bookkeeping. They do not set standalone controls.
        for (String key : Set.of("ChipSlot", "AnyToken", "TokenToggled", "TokenToggleApplied", "NavigationScreenOn"))
            flag(source, key, false);
        for (String key : Set.of("TokenLogo", "AllowedTokens", "NavigationTexture"))
            requireType(source, key, Tag.TAG_INT);
        for (String key : Set.of("TokenPulseUntil", "TokenInsertStarted", "TokenInsertionSequence", "TokenRejectedUntil"))
            requireType(source, key, Tag.TAG_LONG);

        var canonical = source.copy();
        LEGACY_FIELDS.forEach(canonical::remove);
        canonical.putIntArray("Channels", channels(source));
        String machine = drive == null ? design.id() : "";
        var work = productionWork(source, machine);
        ItemStack[] items = inventory(source, machine, work.getInt("Mode"), drive == null || drive.savesChip(), registries);
        if (work.getBoolean("Active") && !items[1].isEmpty())
            throw new IllegalArgumentException("Active Forge builder output has a separate owner");
        canonical.put("Synthesis", work);
        canonical.put("Inventory", NativeBuilderData.save(items, registries));
        long[] sequences = new long[4];
        requireType(source, "SynthesisBatterySequences", Tag.TAG_COMPOUND);
        var oldSequences = source.getCompound("SynthesisBatterySequences");
        if (!BAY_FIELDS.containsAll(oldSequences.getAllKeys()))
            throw new IllegalArgumentException("Unknown Forge battery sequence");
        for (int bay = 0; bay < sequences.length; bay++) {
            String field = "Bay" + bay;
            requireType(oldSequences, field, Tag.TAG_LONG);
            sequences[bay] = oldSequences.getLong(field);
        }
        canonical.putLongArray("BatterySequences", sequences);
        int activation = integer(source, "SynthesisActivation", 0);
        if (activation < 0 || activation > 2 || drive != null && activation != 0)
            throw new IllegalArgumentException("Legacy builder activation");
        canonical.putInt("Activation", activation);
        int activity = integer(source, "MachineActivity", drive == null ? 1 : drive.defaultActivity());
        if (activity < 0 || activity > 2) throw new IllegalArgumentException("Legacy builder activity range");
        requireType(source, "MachineSurfaceTextures", Tag.TAG_INT_ARRAY);
        if (drive == null) {
            if (source.getIntArray("MachineSurfaceTextures").length != 0 || source.contains("DriveParticles")
                    || source.contains("DriveParticleThreshold") || source.contains("DriveLevel")
                    || source.contains("DriveSignalLevels") || source.contains("DriveInverted"))
                throw new IllegalArgumentException("Unsupported Forge production decoration");
            return canonical;
        }

        var defaults = NativeDriveSettings.defaults(drive.exhaust);
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

        canonical.put("Drive", settings.save());
        return canonical;
    }

    private static ItemStack[] inventory(CompoundTag source, String machine, int mode, boolean allowsChip,
                                         HolderLookup.Provider registries) {
        var items = new ItemStack[7];
        Arrays.fill(items, ItemStack.EMPTY);
        var legacy = source.getCompound("Synthesis").getCompound("Inventory").getList("Items", Tag.TAG_COMPOUND);
        if (!legacy.isEmpty() && (machine.isEmpty() || mode != 0))
            throw new IllegalArgumentException("Unsupported Forge builder inventory mode");
        boolean[] seen = new boolean[6];
        var budget = new NativeInventoryItems.Budget();
        for (var value : legacy) {
            var row = (CompoundTag) value;
            if (!row.contains("Slot", Tag.TAG_INT))
                throw new IllegalArgumentException("Forge builder slot type");
            int sourceSlot = row.getInt("Slot");
            if (sourceSlot < 0 || sourceSlot >= seen.length || seen[sourceSlot])
                throw new IllegalArgumentException("Duplicate or invalid Forge builder slot");
            seen[sourceSlot] = true;
            var raw = row.copy();
            raw.remove("Slot");
            requireStackEnvelope(raw);
            String id = raw.getString("id");
            // Forge places batteries after the output. NeoForge reserves slot 3 for the separate chip.
            int targetSlot = sourceSlot <= 2 ? sourceSlot : sourceSlot + 1;
            ItemStack stack = readInventoryItem(raw, id, registries, budget);
            if (stack.isEmpty() || stack.getCount() != raw.getByte("Count") || NativeInventoryItems.containedOwnership(stack))
                throw new IllegalArgumentException("Lossy Forge builder item");
            if (credential(id)) requireExactCredential(raw, stack, id.equals("vandorlabs:data_chip"));
            items[targetSlot] = stack;
        }
        requireType(source, "InsertedToken", Tag.TAG_COMPOUND);
        if (source.contains("InsertedToken")) {
            var raw = source.getCompound("InsertedToken");
            requireStackEnvelope(raw);
            if (!allowsChip || !flag(source, "ChipSlot", true) || raw.getByte("Count") != 1
                    || !raw.getString("id").equals("vandorlabs:data_chip"))
                throw new IllegalArgumentException("Forge builder chip ownership");
            var stack = NativeInventoryItems.legacy(raw, registries, budget);
            requireExactCredential(raw, stack, true);
            items[3] = stack;
        }
        return items;
    }

    private static ItemStack readInventoryItem(CompoundTag raw, String id, HolderLookup.Provider registries,
                                               NativeInventoryItems.Budget budget) {
        if (ModuleRackPolicy.kind(id) == 0) return NativeInventoryItems.legacy(raw, registries, budget);
        var data = raw.getCompound("tag");
        for (String field : Set.of("Items", "Inventory", "BlockEntityTag", "EntityTag", NativeRackData.LEGACY_ITEM))
            if (data.contains(field)) throw new IllegalArgumentException("Ambiguous Forge Rack module item");
        var stack = NativeRackData.readItem(raw, registries);
        if (!NativeRackData.data(stack).getCompound(NativeRackData.LEGACY_ITEM).equals(raw))
            throw new IllegalArgumentException("Lossy Forge Rack module item");
        return stack;
    }

    private static boolean credential(String id) {
        return id.equals("vandorlabs:data_chip") || id.equals("vandorlabs:access_card");
    }

    private static void requireStackEnvelope(CompoundTag raw) {
        if (!raw.contains("id", Tag.TAG_STRING) || !raw.contains("Count", Tag.TAG_BYTE) || raw.getByte("Count") <= 0
                || !raw.contains("Damage", Tag.TAG_SHORT) || raw.getShort("Damage") < 0)
            throw new IllegalArgumentException("Forge builder stack types");
        requireType(raw, "tag", Tag.TAG_COMPOUND);
        requireType(raw, "ForgeCaps", Tag.TAG_COMPOUND);
    }

    private static void requireExactCredential(CompoundTag raw, ItemStack stack, boolean chip) {
        if (!NativeCredentialItems.accepts(stack, chip) || !raw.equals(NativeCredentialItems.legacyCredentialData(stack)))
            throw new IllegalArgumentException("Lossy Forge builder credential");
    }

    private static int[] channels(CompoundTag source) {
        requireType(source, "RedstoneChannels", Tag.TAG_INT_ARRAY);
        int channel = integer(source, "RedstoneChannel", 0);
        if (channel < 0) throw new IllegalArgumentException("Legacy builder channel");
        int[] values = source.contains("RedstoneChannels") ? source.getIntArray("RedstoneChannels") : new int[]{channel};
        var bank = com.vandorlabs.redstone.ChannelList.of(values);
        if (source.contains("RedstoneChannels") && (!Arrays.equals(values, bank.toArray())
                || source.contains("RedstoneChannel") && channel != bank.first()))
            throw new IllegalArgumentException("Conflicting legacy builder channels");
        return bank.toArray();
    }

    private static CompoundTag productionWork(CompoundTag source, String machine) {
        var canonical = new CompoundTag();
        requireType(source, "Synthesis", Tag.TAG_COMPOUND);
        if (!source.contains("Synthesis")) return canonical;
        var work = source.getCompound("Synthesis");
        if (!WORK_FIELDS.containsAll(work.getAllKeys()))
            throw new IllegalArgumentException("Unsupported Forge builder work ownership");
        boolean active = flag(work, "Active", false);
        int credit = integer(work, "Work", 0);
        flag(work, "Connected", false); // Connection state is sampled again in the loaded world.
        int mode = integer(work, "Mode", 0);
        if (mode < 0 || mode > 1 || mode != 0 && !machine.equals("synthesis_tower"))
            throw new IllegalArgumentException("Unsupported Forge builder mode");
        if (active && (mode != 0 || machine.isEmpty()) || !active && (credit != 0 || work.contains("WorkCost")))
            throw new IllegalArgumentException("Unexpected Forge builder work state");
        if (!work.contains("Inventory", Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Missing Forge builder inventory");
        var inventory = work.getCompound("Inventory");
        if (!inventory.getAllKeys().equals(Set.of("Size", "Items")) || integer(inventory, "Size", 0) != 6
                || !(inventory.get("Items") instanceof ListTag items) || items.size() > 6
                || !items.isEmpty() && items.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("Forge builder inventory schema");
        canonical.putInt("Selected", nativeRecipe(work, machine, "Selected"));
        int building = nativeRecipe(work, machine, "Building");
        canonical.putInt("Building", building);
        if (active) {
            if (!work.contains("Building", Tag.TAG_INT) || !work.contains("Work", Tag.TAG_INT))
                throw new IllegalArgumentException("Incomplete Forge builder work ownership");
            int currentCost = fixedWorkCost(machine, building);
            // Forge omitted WorkCost for 8,000 RF jobs, including the older Tower Core format.
            int savedCost = integer(work, "WorkCost", SynthesisRecipePolicy.REQUIRED_RF);
            if (work.contains("WorkCost") && (savedCost != SynthesisRecipePolicy.CORE_RF || savedCost != currentCost)
                    || credit < 0 || credit >= savedCost)
                throw new IllegalArgumentException("Forge builder work credit");
            canonical.putBoolean("Active", true);
            canonical.putInt("Work", credit);
            if (savedCost != SynthesisRecipePolicy.REQUIRED_RF) canonical.putInt("WorkCost", savedCost);
        }
        if (mode != 0) canonical.putInt("Mode", mode);
        return canonical;
    }

    private static int fixedWorkCost(String machine, int building) {
        if (machine.equals("synthesis_tower")) {
            if (building == NativeSynthesisRuntime.DYNAMIC || building == NativeBuilderProducts.RAMP && !NativeBuilderProducts.rampAvailable())
                throw new IllegalArgumentException("Unsupported Forge Tower output");
            return SynthesisRecipePolicy.requiredRf(machine, NativeBuilderProducts.towerBlueprint(building));
        }
        if (machine.equals("assembly_press") && !NativeBuilderProducts.pressPartAvailable(building)
                || machine.equals("enclosed_gantry") && !NativeGearBindings.enabled())
            throw new IllegalArgumentException("Disabled Forge builder output");
        return SynthesisRecipePolicy.REQUIRED_RF;
    }

    private static int nativeRecipe(CompoundTag work, String machine, String field) {
        int forgeIndex = integer(work, field, 0);
        requireType(work, field + "Recipe", Tag.TAG_STRING);
        String id = BuilderRecipeCatalog.canonical(work.getString(field + "Recipe"));
        var forge = BuilderRecipeCatalog.known(machine);
        if (forge.isEmpty() && forgeIndex == 0 && (id.isEmpty() || id.equals("synthesis_tower_core"))) return 0;
        if (forgeIndex < 0 || forgeIndex >= forge.size() || !id.isEmpty() && !forge.get(forgeIndex).equals(id))
            throw new IllegalArgumentException("Conflicting Forge builder recipe");
        id = forge.get(forgeIndex);
        if (machine.equals("synthesis_tower")) {
            if (id.equals("digitized_block")) return NativeSynthesisRuntime.DYNAMIC;
            for (int index = 0; index <= NativeBuilderProducts.MAX_TOWER_PRODUCT; index++)
                if (id.equals(NativeBuilderProducts.towerBlueprint(index))) return index;
        } else {
            int nativeIndex = NativeBuilderProducts.parts(machine).indexOf(id);
            if (nativeIndex >= 0) return nativeIndex;
        }
        throw new IllegalArgumentException("Forge builder recipe is unavailable on NeoForge");
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

    private NativeLegacyBuilderData() {}
}
