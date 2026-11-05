package com.vandorlabs.neoforge.fluid;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Immutable primitive render input. No world, host, sprite or client singleton references. */
public final class NativeFluidRenderSnapshot {
 public static final ModelProperty<NativeFluidRenderSnapshot> DATA=new ModelProperty<>();
 public static final NativeFluidRenderSnapshot EMPTY=new NativeFluidRenderSnapshot(List.of(),null);
 /** World light is supplied by the current chunk mesh; retain only local emission. */
 public static int emissionLight(int level){return Math.max(0,Math.min(15,level))<<4;}
 public record Face(List<Vec3> vertices,ResourceLocation texture,int argb,int packedLight,int facing,float nx,float ny,float nz,int axis){public Face{vertices=List.copyOf(vertices);if(vertices.size()!=4)throw new IllegalArgumentException("Fluid quad must have four vertices");}}
 /** Shares the immutable volume, retaining only precomputed native fluid identities; no NBT on workers. */
 public static final class Contact {
  private final NativeFluidVolume volume;private final long revision;private final net.minecraft.world.level.material.Fluid first,second;
  public Contact(NativeFluidVolume volume,long revision,net.neoforged.neoforge.fluids.FluidStack first,net.neoforged.neoforge.fluids.FluidStack second){this.volume=volume;this.revision=revision;this.first=nativeIdentity(first);this.second=nativeIdentity(second);}
  private static net.minecraft.world.level.material.Fluid nativeIdentity(net.neoforged.neoforge.fluids.FluidStack stack){return !stack.isEmpty()&&net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(stack,new net.neoforged.neoforge.fluids.FluidStack(stack.getFluid(),1000))?stack.getFluid():null;}
  public NativeFluidVolume volume(){return volume;}public long revision(){return revision;}
  public boolean matches(int side,net.minecraft.world.level.material.Fluid fluid){if(fluid instanceof net.minecraft.world.level.material.FlowingFluid flowing)fluid=flowing.getSource();return (side==0?first:second)==fluid;}
 }
 public final List<Face> faces;public final Contact contact;
 public NativeFluidRenderSnapshot(List<Face> faces,Contact contact){this.faces=List.copyOf(faces);this.contact=contact;}
}
