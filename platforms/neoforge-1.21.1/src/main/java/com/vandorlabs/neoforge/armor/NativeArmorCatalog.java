package com.vandorlabs.neoforge.armor;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

public final class NativeArmorCatalog {
    private static final String PATH = "/assets/vandorlabs/data/armor_textures.json";

    public record Design(int choice, String name, EquipmentSlot slot, String label,
                         ResourceLocation worn, String icon, ResourceLocation model) {}

    private static final Map<Integer, Design> DESIGNS = load();

    public static Iterable<Design> designs() {
        return DESIGNS.values();
    }

    public static Design design(int choice, EquipmentSlot slot) {
        Design design = DESIGNS.get(choice);
        return design != null && design.slot() == slot ? design : null;
    }

    public static Design defaultDesign(EquipmentSlot slot) {
        return DESIGNS.values().stream()
            .filter(design -> design.slot() == slot && design.name().startsWith("civilian_staff_"))
            .findFirst().orElseThrow();
    }

    private static Map<Integer, Design> load() {
        Map<Integer, Design> designs = new LinkedHashMap<>();
        try (var stream = NativeArmorCatalog.class.getResourceAsStream(PATH)) {
            if (stream == null) throw new IllegalStateException("Armor texture catalog missing");
            var reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
            for (var value : JsonParser.parseReader(reader).getAsJsonArray()) {
                var entry = value.getAsJsonObject();
                var design = new Design(entry.get("choice").getAsInt(), entry.get("name").getAsString(),
                    EquipmentSlot.valueOf(entry.get("slot").getAsString()), entry.get("label").getAsString(),
                    ResourceLocation.parse(entry.get("worn").getAsString()), entry.get("icon").getAsString(),
                    ResourceLocation.parse(entry.get("model").getAsString()));
                if (design.choice() < 0x10000000 || design.choice() >= 0x10000000 + 100000
                    || !design.slot().isArmor() || designs.putIfAbsent(design.choice(), design) != null) {
                    throw new IllegalStateException("Invalid armor design " + design);
                }
            }
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
        return Collections.unmodifiableMap(designs);
    }

    private NativeArmorCatalog() {}
}
