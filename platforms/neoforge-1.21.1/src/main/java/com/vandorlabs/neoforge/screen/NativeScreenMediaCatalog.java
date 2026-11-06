package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import java.util.*;

/** Source media semantics remain independent of native menu labels and custom atlas playback. */
public final class NativeScreenMediaCatalog {
 public static final int SCREENS=1,ENGINEERING_CROP=2,UNPAIRED_INPUT_CROP=4;
 public record Input(String id,String canonical,int frames,int declaredFrames,double aspect,double crop,String staticTexture,String offTexture,String animationTexture,String sourceStationary,String sourceOff){
  public Input{Objects.requireNonNull(id);Objects.requireNonNull(canonical);Objects.requireNonNull(staticTexture);Objects.requireNonNull(offTexture);if(frames<1||frames>65535||declaredFrames<1||!Double.isFinite(aspect)||aspect<=0||(crop!=0&&crop!=1D/32)||frames>1&&animationTexture==null)throw new IllegalArgumentException("Invalid Screen input media");}
  public ModernFinishCatalog.Playback playback(){return new ModernFinishCatalog.Playback(staticTexture,offTexture,animationTexture,frames,aspect);}
 }
 private record Data(int[] flags,Input[] inputs){}
 private static final Data DATA=load();private static final Map<String,Input> INPUTS=inputs();
 private NativeScreenMediaCatalog(){}
 private static Data load(){var stream=NativeScreenMediaCatalog.class.getResourceAsStream("/assets/vandorlabs/data/screen_media_policy.json");if(stream==null)throw new IllegalStateException("Missing Screen media policy");try(var reader=new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)){var data=new com.google.gson.Gson().fromJson(reader,Data.class);if(data.flags.length!=ModernFinishCatalog.ENTRIES.size()||data.inputs.length>4096)throw new IllegalStateException("Screen media catalog alignment");for(int flag:data.flags)if((flag&~7)!=0)throw new IllegalStateException("Unknown Screen media flags");return data;}catch(java.io.IOException failure){throw new ExceptionInInitializerError(failure);}}
 private static Map<String,Input> inputs(){Map<String,Input> values=new HashMap<>();for(Input input:DATA.inputs)if(values.put(input.id,input)!=null)throw new IllegalStateException("Duplicate Screen input media");return Map.copyOf(values);}
 public static int flags(int choice){return choice>=0&&choice<DATA.flags.length?DATA.flags[choice]:0;}
 public static Input input(String id){var found=INPUTS.get(id);return found!=null?found:INPUTS.get(NativeScreenCatalog.INSTANCE.defaultInput());}
}
