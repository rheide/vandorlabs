package com.vandorlabs.neoforge.wall;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Immutable material/geometry publication; section workers never consult a live wall entity. */
public final class WallRenderSnapshot {
    public static final ModelProperty<WallRenderSnapshot> DATA=new ModelProperty<>();
    public static final WallRenderSnapshot EMPTY=new WallRenderSnapshot(List.of(),List.of());
    /** Block-local world-oriented coordinates and normalized visible-image atlas coordinates. */
    public record Vertex(double x,double y,double z,double u,double v){}
    public record Face(List<Vertex> vertices,ResourceLocation texture,int argb,float nx,float ny,float nz,float opacity,boolean depthWrite){
        public Face(List<Vertex> vertices,ResourceLocation texture,int argb,float nx,float ny,float nz){this(vertices,texture,argb,nx,ny,nz,(argb>>>24)/255F,false);}
        public Face{vertices=List.copyOf(vertices);if(vertices.size()!=4)throw new IllegalArgumentException("Wall quad requires four vertices");}
    }
    public final List<Face> opaque,glass;
    public WallRenderSnapshot(List<Face> opaque,List<Face> glass){this.opaque=List.copyOf(opaque);this.glass=List.copyOf(glass);}
    @Override public boolean equals(Object other){return other instanceof WallRenderSnapshot value&&opaque.equals(value.opaque)&&glass.equals(value.glass);}
    @Override public int hashCode(){return java.util.Objects.hash(opaque,glass);}
}
