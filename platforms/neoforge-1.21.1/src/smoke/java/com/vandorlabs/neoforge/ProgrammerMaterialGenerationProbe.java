package com.vandorlabs.neoforge;
import com.vandorlabs.neoforge.programmer.client.NativeProgrammerResources;
import net.minecraft.client.Minecraft;
/** Actual private-world glyph retirement while only the panel material generation changes. */
public final class ProgrammerMaterialGenerationProbe {
 private static java.lang.reflect.Field active,journal;private static Object original;private static NativeProgrammerBlockRenderer renderer;private static NativeProgrammerJournalRenderer before,replacement;private static Object casing;
 private static void need(boolean ok,String why){if(!ok)throw new AssertionError("Programmer material generation: "+why);}
 private static NativeProgrammerJournalRenderer journal(NativeProgrammerBlockRenderer owner)throws Exception{if(journal==null){journal=NativeProgrammerBlockRenderer.class.getDeclaredField("journal");journal.setAccessible(true);}return (NativeProgrammerJournalRenderer)journal.get(owner);}
 public static void start(NativeProgrammerBlockRenderer owner)throws Exception{renderer=owner;before=journal(owner);need(before!=null&&before.retainedBytes()>0,"warm actual placed journal");casing=NativeProgrammerResources.currentBake();active=NativePanelPlaybackRenderer.class.getDeclaredField("active");active.setAccessible(true);original=active.get(null);var method=original.getClass().getDeclaredMethod("generation");method.setAccessible(true);long generation=(long)method.invoke(original);var old=NativePanelPlaybackRenderer.currentBake();var mc=Minecraft.getInstance();var next=new NativeFlatWallModel.Bake(old.surfaces,material->mc.getTextureAtlas(material.atlasLocation()).apply(material.texture()));var ctor=original.getClass().getDeclaredConstructor(long.class,NativeFlatWallModel.Bake.class);ctor.setAccessible(true);active.set(null,ctor.newInstance(generation+1,next));}
 public static void changed()throws Exception{replacement=journal(renderer);need(NativeProgrammerResources.currentBake()==casing,"casing generation unchanged");need(before.retainedBytes()==0&&replacement!=null&&replacement!=before&&replacement.retainedBytes()>0,"independent panel generation retires and rebuilds actual glyph cache");}
 public static void restore()throws Exception{active.set(null,original);}
 public static void restored()throws Exception{need(replacement.retainedBytes()==0&&journal(renderer)!=replacement,"restored panel generation retires replacement glyphs");}
 public static Object current(NativeProgrammerBlockRenderer owner)throws Exception{return journal(owner);}
 public static void retired(Object previous){need(previous instanceof NativeProgrammerJournalRenderer&&((NativeProgrammerJournalRenderer)previous).retainedBytes()==0,"reload retired prior glyph tables");}
 public static void unloaded(NativeProgrammerBlockRenderer owner)throws Exception{need(journal(owner)==null,"actual world unload cleared glyph binding");}
}
