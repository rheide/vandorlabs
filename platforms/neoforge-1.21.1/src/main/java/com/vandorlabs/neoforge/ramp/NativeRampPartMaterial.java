package com.vandorlabs.neoforge.ramp;

import java.util.Arrays;
import java.util.Objects;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable visual choices only; physical contents and owner snapshots never enter a part mesh. */
public record NativeRampPartMaterial(BlockState state,int mainTexture,int sideTexture,int layout,
        int rotation,boolean stairs,boolean facesEnabled,int[] faces) {
    public NativeRampPartMaterial {
        Objects.requireNonNull(state);Objects.requireNonNull(faces);
        if(faces.length!=6||layout< -1||layout>2||rotation<0||rotation>3)
            throw new IllegalArgumentException("Ramp material projection");
        faces=faces.clone();
    }
    @Override public int[] faces(){return faces.clone();}
    public int face(int index){return faces[index];}
    /** States are canonical registry values; array contents determine the visual cache key. */
    @Override public boolean equals(Object value){
        return value instanceof NativeRampPartMaterial other&&state==other.state
            &&mainTexture==other.mainTexture&&sideTexture==other.sideTexture&&layout==other.layout
            &&rotation==other.rotation&&stairs==other.stairs&&facesEnabled==other.facesEnabled
            &&Arrays.equals(faces,other.faces);
    }
    @Override public int hashCode(){return 31*Objects.hash(state,mainTexture,sideTexture,layout,rotation,stairs,facesEnabled)+Arrays.hashCode(faces);}
    public long retainedBytes(){return 128;}
}
