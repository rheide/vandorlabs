package com.vandorlabs.neoforge.fluid;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Immutable primitive chunk-render input; this class has no client Minecraft dependencies. */
public final class NativeFluidRenderSnapshot {
    public static final ModelProperty<NativeFluidRenderSnapshot> DATA=new ModelProperty<>();
    public static final NativeFluidRenderSnapshot EMPTY=new NativeFluidRenderSnapshot(List.of());
    public record Vertex(double x,double y,double z,double u,double v){}
    public record Face(List<Vertex> vertices,ResourceLocation texture,int argb,int packedLight,int facing,float nx,float ny,float nz){public Face(List<Vertex> vertices,ResourceLocation texture,int argb,int packedLight,int facing){this(vertices,texture,argb,packedLight,facing,net.minecraft.core.Direction.from3DDataValue(facing).getStepX(),net.minecraft.core.Direction.from3DDataValue(facing).getStepY(),net.minecraft.core.Direction.from3DDataValue(facing).getStepZ());}public Face{vertices=List.copyOf(vertices);if(vertices.size()!=4)throw new IllegalArgumentException("Fluid quad requires four vertices");}}
    public record Cell(double minX,double minY,double minZ,double maxX,double maxY,double maxZ,int side){}
    public static final class Contact {
        public final List<Cell> cells;private final NativeContainedFluids.Snapshot fluids;
        private final net.minecraft.nbt.CompoundTag first,second;
        public Contact(com.vandorlabs.fluids.FluidVolume volume,NativeContainedFluids.Snapshot fluids){this.fluids=fluids;java.util.ArrayList<Cell> cells=new java.util.ArrayList<>();for(var cell:volume.cells){var b=cell.box;cells.add(new Cell(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ,cell.side));}this.cells=List.copyOf(cells);first=fluids.side(0).raw();second=fluids.side(1).raw();}
        public net.neoforged.neoforge.fluids.FluidStack fluid(int side){return fluids.side(side).fluid();}
        @Override public boolean equals(Object other){return other instanceof Contact contact&&cells.equals(contact.cells)&&java.util.Objects.equals(first,contact.first)&&java.util.Objects.equals(second,contact.second);}
        @Override public int hashCode(){return java.util.Objects.hash(cells,first,second);}
    }
    public final List<Face> faces;public final Contact contact;
    public NativeFluidRenderSnapshot(List<Face> faces){this(faces,null);}
    public NativeFluidRenderSnapshot(List<Face> faces,Contact contact){this.faces=List.copyOf(faces);this.contact=contact;}
    @Override public boolean equals(Object other){return other instanceof NativeFluidRenderSnapshot snapshot&&faces.equals(snapshot.faces)&&java.util.Objects.equals(contact,snapshot.contact);}
    @Override public int hashCode(){return java.util.Objects.hash(faces,contact);}
    /** Published on the client main thread after owner loading or fluid/geometry changes. */
    public static final class Remove extends Event {public final ContainedFluidHost host;public Remove(ContainedFluidHost host){this.host=host;}}
    public static final class Update extends Event {public final ContainedFluidHost host;public Update(ContainedFluidHost host){this.host=host;}}
}
