package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.ui.*;

/** Catalog/host adapter around the shared plain-wall pane state; no native widget ownership. */
public final class NativeWallDialog {
    private NativeWallDialog(){}
    public static int defaultScreen(){for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)if(ModernFinishCatalog.ENTRIES.get(i).displaySelectable())return i;return 0;}
    public static PlainWallDialog.State editor(NativeWallEntity.WallAppearance value){var base=value.base();return new PlainWallDialog.State(new SlabStairDialog.State(base.housing(),base.sideTexture(),base.stairs().tread,base.stairs().roll),value.walls(),value.frames(),defaultScreen());}
    public static ProgrammableShapeEntity.Appearance appearance(PlainWallDialog.State value){var base=value.base;return new ProgrammableShapeEntity.Appearance(base.housing,base.sideTexture,new StairState(base.tread,base.roll));}
    public static boolean surfaceOption(int choice){if(!NativeWallEntity.validSurface(choice))return false;var option=ModernFinishCatalog.option(choice);return option.general()||NativeWallEntity.validFrame(choice)&&(option.frame()==null||option.frame().aspect()<=1.01);}
}
