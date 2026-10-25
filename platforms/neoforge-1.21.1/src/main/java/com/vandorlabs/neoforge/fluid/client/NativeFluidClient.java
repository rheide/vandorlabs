package com.vandorlabs.neoforge.fluid.client;

import com.vandorlabs.fluids.FluidSurfaceMesh;
import com.vandorlabs.neoforge.fluid.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector3f;

/** Main-thread world sampling publishes immutable data; section workers only map atlas UVs. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeFluidClient {
    private static final Map<ContainedFluidHost,Boolean> HOSTS=new WeakHashMap<>();
    private static final Map<NativeFluidRenderSnapshot,List<BakedQuad>> QUADS=Collections.synchronizedMap(new LinkedHashMap<>(128,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<NativeFluidRenderSnapshot,List<BakedQuad>> entry){return size()>128;}});
    private static int cameraMaterial;
    private static volatile it.unimi.dsi.fastutil.longs.LongSet contacts=it.unimi.dsi.fastutil.longs.LongSets.EMPTY_SET;
    private NativeFluidClient(){}
    @SubscribeEvent public static void update(NativeFluidRenderSnapshot.Update event){Minecraft mc=Minecraft.getInstance();if(!mc.isSameThread())throw new IllegalStateException("Contained fluid snapshots require the client main thread");if(mc.level==null||event.host.fluidOwner().getLevel()!=mc.level||!NativeFluidWorld.installed(event.host))return;HOSTS.put(event.host,Boolean.TRUE);refreshContacts();if(!publish(event.host))dirty(event.host);}
    @SubscribeEvent public static void remove(NativeFluidRenderSnapshot.Remove event){HOSTS.remove(event.host);refreshContacts();}
    private static void refreshContacts(){Minecraft mc=Minecraft.getInstance();var next=new it.unimi.dsi.fastutil.longs.LongOpenHashSet();for(var host:HOSTS.keySet())if(host.fluidOwner().getLevel()==mc.level&&NativeFluidWorld.installed(host)&&!host.fluidVolumeSnapshot().cells.isEmpty()&&(!host.containedFluids().get(0).isEmpty()||!host.containedFluids().get(1).isEmpty()))next.add(host.fluidOwner().getBlockPos().asLong());contacts=it.unimi.dsi.fastutil.longs.LongSets.unmodifiable(next);}
    public static boolean potentialContact(BlockPos pos){var current=contacts;if(current.isEmpty())return false;for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)if(current.contains(BlockPos.asLong(pos.getX()+x,pos.getY()+y,pos.getZ()+z)))return true;return false;}
    public static void reload(){QUADS.clear();for(var host:List.copyOf(HOSTS.keySet()))if(NativeFluidWorld.installed(host))publish(host);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){Minecraft mc=Minecraft.getInstance();if(mc.level==null){HOSTS.clear();contacts=it.unimi.dsi.fastutil.longs.LongSets.EMPTY_SET;cameraMaterial=0;return;}int next=cameraMaterial(mc);if(next!=cameraMaterial){cameraMaterial=next;for(var host:List.copyOf(HOSTS.keySet()))if(NativeFluidWorld.installed(host))publish(host);}}
    private static int kind(FluidStack fluid){return fluid.is(FluidTags.WATER)?1:fluid.is(FluidTags.LAVA)?2:0;}
    private static int cameraMaterial(Minecraft mc){var camera=mc.gameRenderer.getMainCamera();var point=camera.getPosition();FluidStack contained=NativeFluidWorld.atPoint(mc.level,point);if(!contained.isEmpty()){int kind=kind(contained);if(kind!=0&&point.y-Math.floor(point.y)>height(mc,BlockPos.containing(point),contained,point.x-Math.floor(point.x),point.z-Math.floor(point.z)))return 0;return kind;}return kind(NativeFluidWorld.fromState(mc.level.getFluidState(BlockPos.containing(point))));}
    private static void dirty(ContainedFluidHost host){
        // The state-based overload skips same-state ModelData changes. Mark actual render sections directly.
        var pos=host.fluidOwner().getBlockPos();Minecraft.getInstance().levelRenderer.setBlocksDirty(pos.getX()-1,pos.getY()-1,pos.getZ()-1,pos.getX()+1,pos.getY()+1,pos.getZ()+1);
    }
    private static boolean publish(ContainedFluidHost host){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null||host.fluidOwner().getLevel()!=mc.level)return false;BlockPos pos=host.fluidOwner().getBlockPos();var snapshot=host.containedFluids().snapshot();List<NativeFluidRenderSnapshot.Face> faces=new ArrayList<>();
        for(int side=0;side<2;side++){FluidStack fluid=snapshot.side(side).fluid();if(fluid.isEmpty())continue;FluidSurfaceMesh.Neighbor[] neighbors=new FluidSurfaceMesh.Neighbor[6];
            for(Direction direction:Direction.values()){BlockPos next=pos.relative(direction);if(!NativeFluidWorld.loaded(mc.level,next))continue;var state=mc.level.getBlockState(next);boolean nativeSame=NativeFluidWorld.fromState(state.getFluidState()).is(fluid.getFluid());boolean hidden=nativeSame||state.isSolidRender(mc.level,next);
                if(mc.level.getBlockEntity(next) instanceof ContainedFluidHost other){var data=other.containedFluids().snapshot();neighbors[direction.get3DDataValue()]=new FluidSurfaceMesh.Neighbor(other.fluidVolumeSnapshot(),FluidStack.isSameFluidSameComponents(fluid,data.side(0).fluid()),FluidStack.isSameFluidSameComponents(fluid,data.side(1).fluid()),hidden);}else neighbors[direction.get3DDataValue()]=new FluidSurfaceMesh.Neighbor(null,false,false,hidden);}
            var extension=IClientFluidTypeExtensions.of(fluid.getFluidType());ResourceLocation texture=extension.getStillTexture(fluid);if(texture==null)texture=MissingTextureAtlasSprite.getLocation();int color=fluid.is(net.minecraft.world.level.material.Fluids.WATER)?0xFF000000|BiomeColors.getAverageWaterColor(mc.level,pos):extension.getTintColor(fluid);
            var surfaces=FluidSurfaceMesh.surfaces(host.fluidVolumeSnapshot(),side,neighbors,kind(fluid)==0?null:(x,z)->height(mc,pos,fluid,x,z));
            for(var surface:surfaces){float shade=(float)(surface.nx*surface.nx*.6+surface.ny*surface.ny*(surface.ny<0?.5:1)+surface.nz*surface.nz*.8);int shaded=(color&0xFF000000)|((int)((color>>16&255)*shade)<<16)|((int)((color>>8&255)*shade)<<8)|(int)((color&255)*shade);BlockPos sample=surface.boundary<0||surface.boundary==1?pos:pos.relative(Direction.from3DDataValue(surface.boundary));if(!NativeFluidWorld.loaded(mc.level,sample))sample=pos;int light=LightTexture.pack(mc.level.getBrightness(LightLayer.BLOCK,sample),mc.level.getBrightness(LightLayer.SKY,sample));BlockPos above=sample.above();if(NativeFluidWorld.loaded(mc.level,above))light=com.vandorlabs.render.HousingLightPolicy.max(light,LightTexture.pack(mc.level.getBrightness(LightLayer.BLOCK,above),mc.level.getBrightness(LightLayer.SKY,above)));light=com.vandorlabs.render.HousingLightPolicy.max(light,LightTexture.pack(fluid.getFluidType().getLightLevel(fluid),0));emit(faces,surface,texture,shaded,light,false);if(surface.boundary==1||surface.boundary>=2&&kind(fluid)!=0&&kind(fluid)==cameraMaterial)emit(faces,surface,texture,shaded,light,true);}
        }
        var previous=host.containedFluids().renderSnapshot();
        host.containedFluids().publishRenderSnapshot(faces.isEmpty()&&snapshot.side(0).fluid().isEmpty()&&snapshot.side(1).fluid().isEmpty()?NativeFluidRenderSnapshot.EMPTY:new NativeFluidRenderSnapshot(faces,new NativeFluidRenderSnapshot.Contact(host.fluidVolumeSnapshot(),snapshot)));
        boolean changed=previous!=host.containedFluids().renderSnapshot();if(changed)dirty(host);return changed;
    }
    private static void emit(List<NativeFluidRenderSnapshot.Face> faces,FluidSurfaceMesh.Surface surface,ResourceLocation texture,int color,int light,boolean reverse){List<FluidSurfaceMesh.Vertex> points=surface.vertices;int facing=Direction.getNearest((float)(reverse?-surface.nx:surface.nx),(float)(reverse?-surface.ny:surface.ny),(float)(reverse?-surface.nz:surface.nz)).get3DDataValue();if(points.size()==4){List<NativeFluidRenderSnapshot.Vertex> quad=new ArrayList<>();for(int i=0;i<4;i++)quad.add(vertex(points.get(reverse?3-i:i),surface.axis));faces.add(new NativeFluidRenderSnapshot.Face(quad,texture,color,light,facing,(float)(reverse?-surface.nx:surface.nx),(float)(reverse?-surface.ny:surface.ny),(float)(reverse?-surface.nz:surface.nz)));}else for(int i=1;i+1<points.size();i++){var a=vertex(points.get(0),surface.axis);var b=vertex(points.get(reverse?i+1:i),surface.axis);var c=vertex(points.get(reverse?i:i+1),surface.axis);faces.add(new NativeFluidRenderSnapshot.Face(List.of(a,b,c,c),texture,color,light,facing,(float)(reverse?-surface.nx:surface.nx),(float)(reverse?-surface.ny:surface.ny),(float)(reverse?-surface.nz:surface.nz)));}}
    private static NativeFluidRenderSnapshot.Vertex vertex(FluidSurfaceMesh.Vertex point,int axis){return new NativeFluidRenderSnapshot.Vertex(point.x,point.y,point.z,axis==0?point.z:point.x,axis==1?point.z:1-point.y);}
    private static boolean hasFluid(Minecraft mc,BlockPos pos,FluidStack fluid){if(!NativeFluidWorld.loaded(mc.level,pos))return false;if(mc.level.getBlockEntity(pos) instanceof ContainedFluidHost host)for(int side=0;side<2;side++)if(host.containedFluids().get(side).is(fluid.getFluid()))return true;return NativeFluidWorld.fromState(mc.level.getFluidState(pos)).is(fluid.getFluid());}
    private static double corner(Minecraft mc,BlockPos pos,FluidStack fluid,int x,int z){List<FluidSurfaceMesh.Column> columns=new ArrayList<>();for(int dx=0;dx<2;dx++)for(int dz=0;dz<2;dz++){BlockPos at=pos.offset(x-dx,0,z-dz);boolean above=hasFluid(mc,at.above(),fluid),same=hasFluid(mc,at,fluid);int level=0;if(same&&NativeFluidWorld.loaded(mc.level,at)&&!(mc.level.getBlockEntity(at) instanceof ContainedFluidHost)){var state=mc.level.getFluidState(at);level=state.getType() instanceof FlowingFluid&&state.getValue(FlowingFluid.FALLING)?8:8-state.getAmount();}boolean solid=NativeFluidWorld.loaded(mc.level,at)&&mc.level.getBlockState(at).blocksMotion();columns.add(new FluidSurfaceMesh.Column(above,same,level,solid));}return FluidSurfaceMesh.cornerHeight(columns);}
    private static double height(Minecraft mc,BlockPos pos,FluidStack fluid,double x,double z){return FluidSurfaceMesh.height(corner(mc,pos,fluid,0,0),corner(mc,pos,fluid,0,1),corner(mc,pos,fluid,1,1),corner(mc,pos,fluid,1,0),x,z);}
    public static List<BakedQuad> quads(NativeFluidRenderSnapshot snapshot){return QUADS.computeIfAbsent(snapshot,value->{List<BakedQuad> result=new ArrayList<>();var atlas=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);for(var face:value.faces){var sprite=atlas.apply(face.texture());int[] data=new int[32];Direction normal=Direction.from3DDataValue(face.facing());int packedNormal=((int)Math.round(face.nx()*127)&255)|(((int)Math.round(face.ny()*127)&255)<<8)|(((int)Math.round(face.nz()*127)&255)<<16);int argb=face.argb(),abgr=(argb&0xFF00FF00)|((argb>>16)&255)|((argb&255)<<16);for(int i=0;i<4;i++){var vertex=face.vertices().get(i);int at=i*8;data[at]=Float.floatToRawIntBits((float)vertex.x());data[at+1]=Float.floatToRawIntBits((float)vertex.y());data[at+2]=Float.floatToRawIntBits((float)vertex.z());data[at+3]=abgr;data[at+4]=Float.floatToRawIntBits(sprite.getU((float)vertex.u()));data[at+5]=Float.floatToRawIntBits(sprite.getV((float)vertex.v()));data[at+6]=face.packedLight();data[at+7]=packedNormal;}result.add(new BakedQuad(data,-1,normal,sprite,false,false));}return List.copyOf(result);});}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void fogColor(ViewportEvent.ComputeFogColor event){var mc=Minecraft.getInstance();if(mc.level==null)return;FluidStack fluid=NativeFluidWorld.atPoint(mc.level,event.getCamera().getPosition());if(fluid.isEmpty())return;Vector3f color=IClientFluidTypeExtensions.of(fluid.getFluidType()).modifyFogColor(event.getCamera(),(float)event.getPartialTick(),mc.level,mc.options.getEffectiveRenderDistance(),mc.gameRenderer.getDarkenWorldAmount((float)event.getPartialTick()),new Vector3f(event.getRed(),event.getGreen(),event.getBlue()));event.setRed(color.x);event.setGreen(color.y);event.setBlue(color.z);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void fogRender(ViewportEvent.RenderFog event){var mc=Minecraft.getInstance();if(mc.level==null)return;FluidStack fluid=NativeFluidWorld.atPoint(mc.level,event.getCamera().getPosition());if(!fluid.isEmpty())IClientFluidTypeExtensions.of(fluid.getFluidType()).modifyFogRender(event.getCamera(),event.getMode(),mc.options.getEffectiveRenderDistance()*16F,(float)event.getPartialTick(),event.getNearPlaneDistance(),event.getFarPlaneDistance(),event.getFogShape());}
}
