package com.vandorlabs.neoforge.segmented;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Immutable cell facts consumed by section workers. No world, tile, fluid or saved-data references. */
public record NativeSegmentedTerrainSnapshot(Direction facing,int texture,boolean header,boolean body,
                                               boolean top,boolean bottom,long revision,int column,int row) {
    public NativeSegmentedTerrainSnapshot(Direction facing,int texture,boolean header,boolean body,boolean top,boolean bottom,long revision){this(facing,texture,header,body,top,bottom,revision,0,0);}
    public static final ModelProperty<NativeSegmentedTerrainSnapshot> DATA=new ModelProperty<>();
    public static final NativeSegmentedTerrainSnapshot EMPTY=new NativeSegmentedTerrainSnapshot(Direction.NORTH,-1,false,false,false,false,0);
    public NativeSegmentedTerrainSnapshot {
        if(facing==null||facing.getAxis().isVertical()||revision<0||column<0||column>=32||row<0||row>=32)throw new IllegalArgumentException("Segmented terrain publication");
    }
    public static boolean eligible(NativeSegmentedVisual visual){
        if(visual.settings().direction!=0||visual.settings().position!=0||visual.settings().straight)return false;
        for(int row=0;row<visual.height();row++){
            if(visual.settings().treadPixels!=16)return false;int choice=visual.texture(row);if(choice>=ModernFinishCatalog.ENTRIES.size()||NativeSegmentedArtwork.complex(choice)||!visual.rows().get(row).nativePanel())return false;
            if(choice>=0){String category=ModernFinishCatalog.option(choice).category();int slash=category.indexOf('/');if(slash>=0)category=category.substring(0,slash);if(category.equals("Frame")||category.equals("Frames"))return false;}
        }
        return true;
    }
    public static NativeSegmentedTerrainSnapshot cell(NativeSegmentedVisual visual,int row,int texture,boolean body,long revision){
        if(row<0||row>=visual.height())throw new IllegalArgumentException("Segmented terrain row");
        boolean top=row==0&&visual.settings().direction==0&&visual.settings().position==0;
        return new NativeSegmentedTerrainSnapshot(visual.facing(),texture,top,body,top,row==visual.height()-1,revision,visual.column(),row);
    }
    public static NativeSegmentedTerrainSnapshot unstaged(Direction facing,int texture,int row,int height,NativeSegmentedDoorSettings settings){
        boolean top=row==0&&settings.direction==0&&settings.position==0;
        return new NativeSegmentedTerrainSnapshot(facing,texture,top,false,top,row==height-1,0,0,row);
    }
    /** Ephemeral client drawing state; none of these fields enters update tags or persistence. */
    public record ClientProjection(NativeSegmentedTerrainSnapshot terrain,boolean awaitingChunk,
                                   boolean suppressed,boolean headerTerrain,long revision){
        public static final ClientProjection EMPTY=new ClientProjection(NativeSegmentedTerrainSnapshot.EMPTY,false,false,false,0);
        public ClientProjection {if(terrain==null||revision<0)throw new IllegalArgumentException("Segmented client projection");}
    }
}
