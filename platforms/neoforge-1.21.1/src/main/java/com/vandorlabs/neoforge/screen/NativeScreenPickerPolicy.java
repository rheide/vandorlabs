package com.vandorlabs.neoforge.screen;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.tiles.HousingFinishCatalog;
/** Released screen/input picker membership, separate from broad saved material validity. */
public final class NativeScreenPickerPolicy {
 private NativeScreenPickerPolicy(){}
 private static final java.util.Set<Integer> INPUTS=inputs();
 private static java.util.Set<Integer> inputs(){var result=new java.util.HashSet<Integer>();var catalog=NativeScreenCatalog.INSTANCE;for(String input:catalog.inputs())result.add(catalog.inputArtwork(input));return java.util.Set.copyOf(result);}
 public static boolean selectable(int choice,boolean input){if(!ModernFinishCatalog.validChoice(choice))return false;var e=ModernFinishCatalog.option(choice);if(!e.visible())return false;if(e.general())return true;boolean authored=choice>=HousingFinishCatalog.legacyCount()&&(input?INPUTS.contains(choice)||e.category().startsWith("Controls/"):e.displaySelectable());return authored&&NativeScreenCatalog.INSTANCE.bareChoice(choice)==choice;}
 private static final java.util.Map<Integer,String> SCREENS=names(false),INPUT_NAMES=names(true);
 private static java.util.Map<Integer,String> names(boolean input){var result=new java.util.HashMap<Integer,String>();var catalog=NativeScreenCatalog.INSTANCE;for(String name:input?catalog.inputs():catalog.screens()){int choice=input?catalog.inputArtwork(name):catalog.screenArtwork(name);if(choice!=0)result.putIfAbsent(choice,name);}return java.util.Map.copyOf(result);}
 public static String nativeId(int choice,boolean input){return (input?INPUT_NAMES:SCREENS).get(ModernFinishCatalog.storedChoice(choice));}
 public static boolean split(int choice){return (selectable(choice,false)||selectable(choice,true))&&NativeScreenCatalog.INSTANCE.bareChoice(choice)==choice;}
 public static boolean housing(int choice){return ModernFinishCatalog.validChoice(choice)&&ModernFinishCatalog.option(choice).visible()&&ModernFinishCatalog.option(choice).general();}
}
