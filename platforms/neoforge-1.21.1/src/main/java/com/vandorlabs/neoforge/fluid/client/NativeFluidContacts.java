package com.vandorlabs.neoforge.fluid.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vandorlabs.fluids.FluidSurfaceMesh;
import com.vandorlabs.fluids.FluidVolume;
import com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;
import net.neoforged.neoforge.fluids.FluidStack;

/** Vanilla liquid boundary adaptation over immutable section model data, never the live level. */
public final class NativeFluidContacts {
    private static final ThreadLocal<ArrayDeque<Context>> CONTEXT=ThreadLocal.withInitial(ArrayDeque::new);
    private NativeFluidContacts(){}
    private static Fluid source(Fluid fluid){return fluid instanceof FlowingFluid flowing?flowing.getSource():fluid;}
    private static boolean same(FluidStack stored,Fluid fluid){return !stored.isEmpty()&&FluidStack.isSameFluidSameComponents(stored,new FluidStack(source(fluid),1000));}
    private static NativeFluidRenderSnapshot.Contact contact(BlockAndTintGetter access,BlockPos pos,Fluid fluid){var data=access.getModelData(pos).get(NativeFluidRenderSnapshot.DATA);if(data==null||data.contact==null||data.contact.cells.isEmpty())return null;return same(data.contact.fluid(0),fluid)||same(data.contact.fluid(1),fluid)?data.contact:null;}
    public static VertexConsumer begin(BlockAndTintGetter access,BlockPos pos,VertexConsumer original,FluidState state){if(!(state.is(FluidTags.WATER)||state.is(FluidTags.LAVA))||!NativeFluidClient.potentialContact(pos))return original;Context context=new Context(access,pos.immutable(),state);CONTEXT.get().push(context);return new ClippedConsumer(original,context);}
    public static void end(VertexConsumer consumer){if(consumer instanceof ClippedConsumer clipped){clipped.finish();ArrayDeque<Context> contexts=CONTEXT.get();if(contexts.isEmpty()||contexts.pop()!=clipped.context)throw new IllegalStateException("Mismatched liquid render context");if(contexts.isEmpty())CONTEXT.remove();}}
    private static Context context(BlockAndTintGetter access){var contexts=CONTEXT.get();return contexts.isEmpty()||contexts.peek().access!=access?null:contexts.peek();}
    public static Float height(BlockAndTintGetter access,Fluid fluid,BlockPos pos,FluidState original){Context context=context(access);if(context==null)return null;boolean own=contact(access,pos,fluid)!=null;if(!own&&!fluid.isSame(original.getType()))return null;BlockPos above=pos.above();if(contact(access,above,fluid)!=null||fluid.isSame(access.getFluidState(above).getType()))return 1F;return own?source(fluid).defaultFluidState().getOwnHeight():null;}
    public static Float corner(BlockAndTintGetter access,Fluid fluid,BlockPos diagonal){Context context=context(access);if(context==null)return null;int x=diagonal.getX()<context.pos.getX()?0:1,z=diagonal.getZ()<context.pos.getZ()?0:1;List<FluidSurfaceMesh.Column> columns=new ArrayList<>();for(int dx=0;dx<2;dx++)for(int dz=0;dz<2;dz++){BlockPos at=context.pos.offset(x-dx,0,z-dz);var state=access.getBlockState(at);var actual=state.getFluidState();boolean above=contact(access,at.above(),fluid)!=null||fluid.isSame(access.getFluidState(at.above()).getType());boolean contained=contact(access,at,fluid)!=null,same=contained||fluid.isSame(actual.getType());int level=contained?0:actual.getType() instanceof FlowingFluid&&actual.getValue(FlowingFluid.FALLING)?8:8-actual.getAmount();columns.add(new FluidSurfaceMesh.Column(above,same,level,state.isSolid()));}return (float)FluidSurfaceMesh.cornerHeight(columns);}
    private static final class Context {
        final BlockAndTintGetter access;final BlockPos pos;final FluidState state;final NativeFluidRenderSnapshot.Contact[] neighbors=new NativeFluidRenderSnapshot.Contact[6];final TextureAtlasSprite still;
        Context(BlockAndTintGetter access,BlockPos pos,FluidState state){this.access=access;this.pos=pos;this.state=state;for(Direction face:Direction.values())neighbors[face.get3DDataValue()]=contact(access,pos.relative(face),state.getType());still=FluidSpriteCache.getFluidSprites(access,pos,state)[0];}
        List<List<double[]>> clip(List<double[]> original){int boundary=-1;for(int face=0;face<6;face++){int axis=face<2?1:face<4?2:0;double edge=(face&1)==1?1:0;boolean on=true;for(double[] p:original)on&=Math.abs(p[axis]-edge)<.002;if(on){boundary=face;break;}}if(boundary<0||neighbors[boundary]==null)return List.of(original);var neighbor=neighbors[boundary];int axis=boundary<2?1:boundary<4?2:0;Direction face=Direction.from3DDataValue(boundary);
            for(double[] p:original){double u=axis==0?p[2]:p[0],v=axis==1?p[2]:1-p[1];p[3]=still.getU((float)u);p[4]=still.getV((float)v);p[16]=p[axis];p[axis]=(boundary&1)==1?1:0;}
            List<List<double[]>> retained=new ArrayList<>();for(var cell:neighbor.cells)if(!same(neighbor.fluid(cell.side()),state.getType())){List<double[]> piece=original;for(double[] plane:FluidSurfaceMesh.planes(new FluidVolume.Box(cell.minX(),cell.minY(),cell.minZ(),cell.maxX(),cell.maxY(),cell.maxZ()))){double limit=plane[3]+plane[0]*face.getStepX()+plane[1]*face.getStepY()+plane[2]*face.getStepZ();piece=com.vandorlabs.render.DiagonalMeshClip.plane(piece,plane[0],plane[1],plane[2],limit);if(FluidSurfaceMesh.area(piece)<1e-10)break;}if(FluidSurfaceMesh.area(piece)>1e-10)retained.add(piece);}
            for(List<double[]> piece:retained)for(double[] p:piece)p[axis]=p[16];return retained;
        }
    }
    private static final class ClippedConsumer implements VertexConsumer {
        final VertexConsumer original;final Context context;final List<double[]> quad=new ArrayList<>(4);double[] current;boolean overlay;
        ClippedConsumer(VertexConsumer original,Context context){this.original=original;this.context=context;}
        private void commit(){if(current==null)return;quad.add(current);current=null;if(quad.size()==4){for(var polygon:context.clip(new ArrayList<>(quad))){if(polygon.size()==4)for(var point:polygon)emit(point);else for(int i=1;i+1<polygon.size();i++){emit(polygon.get(0));emit(polygon.get(i));emit(polygon.get(i+1));emit(polygon.get(i+1));}}quad.clear();}}
        void finish(){commit();if(!quad.isEmpty())throw new IllegalStateException("Incomplete native liquid quad");}
        private void emit(double[] p){VertexConsumer target=original.addVertex((float)(p[0]+(context.pos.getX()&15)),(float)(p[1]+(context.pos.getY()&15)),(float)(p[2]+(context.pos.getZ()&15))).setColor((int)Math.round(p[5]),(int)Math.round(p[6]),(int)Math.round(p[7]),(int)Math.round(p[8])).setUv((float)p[3],(float)p[4]);if(overlay)target.setUv1((int)Math.round(p[9]),(int)Math.round(p[10]));target.setUv2((int)Math.round(p[11]),(int)Math.round(p[12])).setNormal((float)p[13],(float)p[14],(float)p[15]);}
        @Override public VertexConsumer addVertex(float x,float y,float z){commit();current=new double[17];current[0]=x-(context.pos.getX()&15);current[1]=y-(context.pos.getY()&15);current[2]=z-(context.pos.getZ()&15);for(int i=5;i<=8;i++)current[i]=255;return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){current[5]=r;current[6]=g;current[7]=b;current[8]=a;return this;}
        @Override public VertexConsumer setUv(float u,float v){current[3]=u;current[4]=v;return this;}
        @Override public VertexConsumer setUv1(int u,int v){current[9]=u;current[10]=v;overlay=true;return this;}
        @Override public VertexConsumer setUv2(int u,int v){current[11]=u;current[12]=v;return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){current[13]=x;current[14]=y;current[15]=z;return this;}
    }
}
