package com.vandorlabs.neoforge.screen;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.tiles.HousingFinishCatalog;
/** Released screen/input picker membership, separate from broad saved material validity. */
public final class NativeScreenPickerPolicy {
 private NativeScreenPickerPolicy(){}
 private static final java.util.Set<Integer> INPUTS=inputs();
 private static java.util.Set<Integer> inputs(){var result=new java.util.HashSet<Integer>();var catalog=NativeScreenCatalog.INSTANCE;for(String input:catalog.inputs())result.add(catalog.inputArtwork(input));return java.util.Set.copyOf(result);}
 public static boolean selectable(int choice,boolean input,boolean wide){if(!ModernFinishCatalog.validChoice(choice))return false;var e=ModernFinishCatalog.option(choice);if(!e.visible())return false;if(e.general())return true;boolean authored=choice>=HousingFinishCatalog.legacyCount()&&(input?INPUTS.contains(choice)||e.category().startsWith("Controls/"):e.category().startsWith("Displays")&&!INPUTS.contains(choice));return (authored||wide&&!input&&e.displaySelectable())&&NativeScreenCatalog.INSTANCE.bareChoice(choice)==choice;}
 public static boolean housing(int choice){return ModernFinishCatalog.validChoice(choice)&&ModernFinishCatalog.option(choice).visible()&&ModernFinishCatalog.option(choice).general();}
}
