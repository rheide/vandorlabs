package com.vandorlabs.neoforge;

import com.vandorlabs.render.HousingFacePlane;
import java.util.Arrays;
import net.minecraft.core.Direction;

/** Immutable plane snapshot safe for concurrent section compilation. */
public final class JoinedCubeFaces {
    public static final JoinedCubeFaces NONE=new JoinedCubeFaces(new HousingFacePlane[6]);
    private final HousingFacePlane[] faces;
    public JoinedCubeFaces(HousingFacePlane[] faces){if(faces.length!=6)throw new IllegalArgumentException("Six faces required");this.faces=faces.clone();}
    public HousingFacePlane face(Direction direction){return faces[direction.get3DDataValue()];}
    public HousingFacePlane[] copy(){return faces.clone();}
    @Override public boolean equals(Object value){
        if(!(value instanceof JoinedCubeFaces other))return false;
        for(int f=0;f<6;f++)for(int a=0;a<3;a++){
            HousingFacePlane x=faces[f],y=other.faces[f];
            if((x==null)!=(y==null))return false;
            if(x!=null&&(x.min(a)!=y.min(a)||x.size(a)!=y.size(a)))return false;
        }
        return true;
    }
    @Override public int hashCode(){int result=1;for(var face:faces)for(int a=0;a<3;a++)result=31*result+(face==null?0:31*face.min(a)+face.size(a));return result;}
}
