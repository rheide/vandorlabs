package com.vandorlabs.neoforge.trapdoor;

import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.nbt.*;

/** Compares native choices with actual released tile decoding, including malformed typed fields. */
public final class TrapdoorSettingsChecks {
    public static void main(String[] args)throws Exception {
        JsonArray fixtures=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();int checks=0;
        for(JsonElement element:fixtures){var row=element.getAsJsonObject();boolean diagonal=row.get("diagonal").getAsBoolean();
            CompoundTag input=TagParser.parseTag(row.get("input").getAsString()),before=input.copy();
            var settings=NativeTrapdoorSettings.read(input,diagonal);var expected=TagParser.parseTag(row.get("item").getAsString());
            var actual=settings.item(row.get("offsetEligible").getAsBoolean());
            if(!actual.equals(expected))throw new AssertionError("Trapdoor "+checks+" diagonal="+diagonal+" input="+input+" expected="+expected+" actual="+actual);
            if(!before.equals(input)||row.get("defaultTexture").getAsInt()!=NativeTrapdoorSettings.defaultTexture())throw new AssertionError("Mutation/catalog drift");
            input.putString("FutureField","opaque");var foreign=new CompoundTag();foreign.putByteArray("Bytes",new byte[]{3,1,7});input.put("ForgeCaps",foreign);
            var saved=new NativeTrapdoorSettings.Saved(input,diagonal);input.putString("FutureField","mutated");var unchanged=saved.unchanged();
            if(!unchanged.getString("FutureField").equals("opaque"))throw new AssertionError("Retained aliases input");
            unchanged.remove("ForgeCaps");if(!saved.unchanged().getCompound("ForgeCaps").equals(foreign))throw new AssertionError("Retained aliases output");
            var edited=saved.edited(settings,row.get("offsetEligible").getAsBoolean());if(!edited.getString("FutureField").equals("opaque")||!edited.getCompound("ForgeCaps").equals(foreign))throw new AssertionError("Edit lost unknown data");checks++;
        }
        System.out.println("Native Trapdoor settings PASS actual-source profiles="+checks+" opaque fields and defensive saves");
    }
}
