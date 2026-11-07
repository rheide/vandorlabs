package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.canopy.LargeCanopySettings;
import java.io.*;
import java.util.*;
import net.minecraft.nbt.*;

/** Actual released canopy decoder and writer, before a portable policy replaces either. */
public final class CanopySettingsLegacyOracle {
    private static final String[] KEYS = {"CanopyDepth", "CanopyScale", "CanopyShade", "CanopyTint",
        "CanopyMount", "CanopyMotion", "CanopyHinge", "CanopyAngle", "CanopyMaterial",
        "CanopySlideForward", "CanopyTileMaterial", "CanopyTextureLayout", "CanopyOpenable",
        "CanopyRedstone", "CanopyActivateOnPower", "CanopyChannels"};

    static JsonObject tags(NBTTagCompound tag) {
        JsonObject out = new JsonObject();
        for (String key : new TreeSet<>(tag.getKeySet())) {
            NBTBase value = tag.getTag(key);
            JsonObject scalar = new JsonObject();
            scalar.addProperty("type", value.getId());
            scalar.addProperty("snbt", canonical(value));
            out.add(key, scalar);
        }
        return out;
    }

    private static String canonical(NBTBase value) {
        if (value instanceof NBTTagCompound) {
            NBTTagCompound compound=(NBTTagCompound)value;
            StringJoiner result=new StringJoiner(",","{","}");
            for(String key:new TreeSet<>(compound.getKeySet()))
                result.add(new NBTTagString(key).toString()+":"+canonical(compound.getTag(key)));
            return result.toString();
        }
        if (value instanceof NBTTagList) {
            NBTTagList list=(NBTTagList)value;
            StringJoiner result=new StringJoiner(",","[","]");
            for(int i=0;i<list.tagCount();i++)result.add(canonical(list.get(i)));
            return result.toString();
        }
        return value.toString();
    }

    private static JsonObject state(LargeCanopySettings settings) {
        JsonObject out = new JsonObject();
        out.addProperty("depth", settings.depth); out.addProperty("scale", settings.scale);
        out.addProperty("shade", settings.shade); out.addProperty("tint", settings.tint);
        out.addProperty("mount", settings.mount); out.addProperty("motion", settings.motion);
        out.addProperty("hinge", settings.hinge); out.addProperty("angle", settings.angle);
        out.addProperty("material", settings.material); out.addProperty("layout", settings.textureLayout());
        out.addProperty("slideForward", settings.slideForward); out.addProperty("tileMaterial", settings.tileMaterial);
        out.addProperty("openable", settings.openable); out.addProperty("redstone", settings.redstone);
        out.addProperty("activateOnPower", settings.activateOnPower); out.addProperty("length", settings.length());
        out.addProperty("valid", LargeCanopySettings.valid(settings.depth, settings.scale, settings.shade, settings.tint));
        JsonArray channels = new JsonArray();
        for (int channel : settings.channels.toArray()) channels.add(channel);
        out.add("channels", channels);
        return out;
    }

    private static void emit(PrintWriter out, Gson gson, NBTTagCompound input) {
        NBTTagCompound original = input.copy();
        LargeCanopySettings settings = new LargeCanopySettings();
        settings.read(input);
        // Legacy numeric-tag equals deliberately treats NaN as unequal, even after a copy.
        if (!tags(input).equals(tags(original))) throw new AssertionError("Source canopy read mutated input");
        NBTTagCompound saved = new NBTTagCompound();
        settings.write(saved);
        LargeCanopySettings decoded = new LargeCanopySettings(); decoded.read(saved);
        if (!state(settings).equals(state(decoded))) throw new AssertionError("Source canopy settings round trip");
        JsonObject row = new JsonObject();
        row.add("input", tags(input)); row.add("state", state(settings)); row.add("saved", tags(saved));
        out.println(gson.toJson(row));
    }

    public static void main(String[] args) throws Exception {
        File file = new File(args[0]); file.getAbsoluteFile().getParentFile().mkdirs();
        List<NBTBase> values = new ArrayList<>();
        for (int value : new int[]{Integer.MIN_VALUE, -1, 0, 1, 2, 3, 9, 10, 15, 16, 64, 71, 80, 81, 100, Integer.MAX_VALUE}) {
            values.add(new NBTTagInt(value)); values.add(new NBTTagByte((byte)value));
            values.add(new NBTTagShort((short)value)); values.add(new NBTTagLong(value));
            values.add(new NBTTagFloat(value + .5F)); values.add(new NBTTagDouble(value + .5D));
        }
        values.add(new NBTTagString("future:canopy")); values.add(new NBTTagCompound());
        values.add(new NBTTagList()); values.add(new NBTTagByteArray(new byte[]{1, 2}));
        for (float value : new float[]{Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY})
            values.add(new NBTTagFloat(value));
        for (double value : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
            values.add(new NBTTagDouble(value));
        for (int[] channels : new int[][]{new int[0], {7, 3, 7, 0}, {-1}, {Integer.MAX_VALUE, 2}, new int[65]})
            values.add(new NBTTagIntArray(channels));
        Gson gson = new Gson(); int profiles = 0;
        try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
            emit(out, gson, new NBTTagCompound()); profiles++;
            for (String key : KEYS) for (NBTBase value : values) {
                NBTTagCompound input = new NBTTagCompound(); input.setTag(key, value.copy());
                emit(out, gson, input); profiles++;
            }
            // Independent persisted layout and legacy tile flag have deliberately different precedence.
            for (int layout = -1; layout <= 3; layout++) for (boolean tiled : new boolean[]{false, true})
                for (int depth : new int[]{0, 1, 16, 17, 80}) {
                    NBTTagCompound input = new NBTTagCompound(); input.setInteger("CanopyTextureLayout", layout);
                    input.setBoolean("CanopyTileMaterial", tiled); input.setInteger("CanopyDepth", depth);
                    input.setBoolean("CanopyOpenable", true); input.setInteger("CanopyHinge", 3);
                    emit(out, gson, input); profiles++;
                }
        }
        SegmentedReferenceGolden.check(file, "b4a5e1431f177d36159b30f7dd5981ea38f20bae981757fa0ca32794a778a86d");
        System.out.println("Actual standalone canopy settings PASS profiles=" + profiles + " file=" + file);
    }
}
