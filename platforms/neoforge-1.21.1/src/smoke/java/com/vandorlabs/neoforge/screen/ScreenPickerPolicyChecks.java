package com.vandorlabs.neoforge.screen;

/** Actual constructor memberships and half-display categories across both screen picker roles. */
public final class ScreenPickerPolicyChecks {
    public static void main(String[] args)throws Exception{
        var root=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("screenPickerReference")))).getAsJsonObject();int checks=0,accepted=0;
        if(root.getAsJsonArray("profiles").size()!=2||root.get("materials").getAsInt()!=com.vandorlabs.neoforge.ModernFinishCatalog.ENTRIES.size())throw new AssertionError("Current source picker coverage");
        for(var element:root.getAsJsonArray("profiles")){
            var profile=element.getAsJsonObject();var expected=new java.util.HashSet<Integer>();for(var id:profile.getAsJsonArray("choices"))expected.add(id.getAsInt());boolean input=profile.get("input").getAsBoolean();int half=0;
            for(int id=0;id<root.get("materials").getAsInt();id++){
                boolean actual=NativeScreenPickerPolicy.selectable(id,input);var entry=com.vandorlabs.neoforge.ModernFinishCatalog.option(id);
                if(actual!=expected.contains(id))throw new AssertionError("Source picker differs input="+input+" choice="+id+" expected="+expected.contains(id)+" actual="+actual+" entry="+entry);
                checks++;if(actual){accepted++;if(entry.category().startsWith("Half Displays/"))half++;}
            }
            if(half!=123)throw new AssertionError("Every half display must be available for input="+input+", got "+half);
        }
        var split=new java.util.HashSet<Integer>();for(var id:root.getAsJsonArray("split"))split.add(id.getAsInt());
        for(int id=0;id<root.get("materials").getAsInt();id++)if(NativeScreenPickerPolicy.split(id)!=split.contains(id))throw new AssertionError("Source split picker differs "+id);
        System.out.println("Native Screen picker policy PASS actual Source choices="+checks+" accepted="+accepted+" halfDisplays=123 splitChoices="+split.size());
    }
}
