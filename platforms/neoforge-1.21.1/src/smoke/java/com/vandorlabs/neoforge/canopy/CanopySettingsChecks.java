package com.vandorlabs.neoforge.canopy;

import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.nbt.*;

/** Independent typed records come from the released Forge decoder, not the new shared policy. */
public final class CanopySettingsChecks {
    private static int checks;
    private static void need(boolean value,String message) { checks++; if(!value)throw new AssertionError(message); }
    private static CompoundTag tags(JsonObject input)throws Exception {
        CompoundTag tag=new CompoundTag();
        for(var entry:input.entrySet()) {
            var value=entry.getValue().getAsJsonObject(); int type=value.get("type").getAsInt();
            String text=value.get("snbt").getAsString();
            Tag decoded=switch(type) {
                case 5 -> FloatTag.valueOf(Float.parseFloat(text.substring(0,text.length()-1)));
                case 6 -> DoubleTag.valueOf(Double.parseDouble(text.substring(0,text.length()-1)));
                default -> TagParser.parseTag("{v:"+text+"}").get("v");
            };
            need(decoded!=null&&decoded.getId()==type,"typed Source input "+entry.getKey());
            tag.put(entry.getKey(),decoded);
        }
        return tag;
    }
    private static void state(JsonObject expected,NativeCanopySettings settings) {
        var d=settings.data();
        int[] ints={d.depth,d.scale,d.shade,d.tint,d.mount,d.motion,d.hinge,d.angle,d.material,d.textureLayout(),d.length()};
        String[] names={"depth","scale","shade","tint","mount","motion","hinge","angle","material","layout","length"};
        for(int i=0;i<ints.length;i++)need(expected.get(names[i]).getAsInt()==ints[i],"Source scalar "+names[i]+" expected="+expected.get(names[i])+" actual="+ints[i]);
        boolean[] bools={d.slideForward,d.tileMaterial,d.openable,d.redstone,d.activateOnPower,d.valid()};
        names=new String[]{"slideForward","tileMaterial","openable","redstone","activateOnPower","valid"};
        for(int i=0;i<bools.length;i++)need(expected.get(names[i]).getAsBoolean()==bools[i],"Source flag "+names[i]);
        JsonArray channels=expected.getAsJsonArray("channels"); need(channels.size()==d.channels.size(),"Source channel count");
        for(int i=0;i<channels.size();i++)need(channels.get(i).getAsInt()==d.channels.get(i),"Source channel value");
    }
    public static void main(String[] args)throws Exception {
        int profiles=0;
        try(var reader=Files.newBufferedReader(Path.of(args[0]))) {
            for(String line;(line=reader.readLine())!=null;) {
                var row=JsonParser.parseString(line).getAsJsonObject(); var input=tags(row.getAsJsonObject("input"));
                var settings=NativeCanopySettings.read(input); state(row.getAsJsonObject("state"),settings);
                var saved=new CompoundTag(); settings.write(saved);
                need(saved.equals(tags(row.getAsJsonObject("saved"))),"Source saved typed fields profile="+profiles);
                state(row.getAsJsonObject("state"),NativeCanopySettings.read(saved)); profiles++;
            }
        }
        need(profiles==1827,"frozen Source coverage");
        System.out.println("[vandorlabs][canopy-settings] PASS SourceProfiles="+profiles+" fields="+checks);
    }
}
