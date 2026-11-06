package com.vandorlabs.neoforge.light;
import com.vandorlabs.neoforge.ModernFinishCatalog;
/** Generated Source menu flags; raw choice aliases retain their released visibility and filtering. */
public final class NativeLightPickerPolicy {
 private record Data(int[] flags){}
 private static final int[] FLAGS=load();private NativeLightPickerPolicy(){}
 private static int[] load(){var stream=NativeLightPickerPolicy.class.getResourceAsStream("/assets/vandorlabs/data/light_material_policy.json");if(stream==null)throw new IllegalStateException("Missing native Light material policy");try(var reader=new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)){var flags=new com.google.gson.Gson().fromJson(reader,Data.class).flags;if(flags.length!=ModernFinishCatalog.ENTRIES.size())throw new IllegalStateException("Light material policy/catalog mismatch");for(int value:flags)if((value&~3)!=0||(value&1)!=0&&(value&2)==0)throw new IllegalStateException("Invalid Light material policy");return flags.clone();}catch(java.io.IOException failure){throw new ExceptionInInitializerError(failure);}}
 public static boolean selectable(int choice,boolean off){if(choice>=0&&choice<FLAGS.length)return (FLAGS[choice]&(off?2:1))!=0;return choice>=0&&ModernFinishCatalog.validChoice(choice)&&ModernFinishCatalog.option(choice).visible()&&ModernFinishCatalog.option(choice).general();}
}
