package com.vandorlabs.neoforge.fluid;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Immutable primitive render input. No world, host, sprite or client singleton references. */
public final class NativeFluidRenderSnapshot {
 public static final ModelProperty<NativeFluidRenderSnapshot> DATA=new ModelProperty<>();
 public static final NativeFluidRenderSnapshot EMPTY=new NativeFluidRenderSnapshot(List.of(),null);
 public record Face(List<Vec3> vertices,ResourceLocation texture,int argb,int packedLight,int facing,float nx,float ny,float nz,int axis){public Face{vertices=List.copyOf(vertices);if(vertices.size()!=4)throw new IllegalArgumentException("Fluid quad must have four vertices");}}
 /** Shares the already immutable volume/slots; never creates a second physics graph. */
 public record Contact(NativeFluidVolume volume,NativeFluidSlots.Snapshot fluids){}
 public final List<Face> faces;public final Contact contact;
 public NativeFluidRenderSnapshot(List<Face> faces,Contact contact){this.faces=List.copyOf(faces);this.contact=contact;}
}
