package com.vandorlabs.neoforge.fluid.client;

import com.vandorlabs.neoforge.fluid.NativeFluidSurface;
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
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
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
    private static int cameraMaterial;
    private static volatile it.unimi.dsi.fastutil.longs.LongSet contacts=it.unimi.dsi.fastutil.longs.LongSets.EMPTY_SET;
    private NativeFluidClient(){}
    @SubscribeEvent public static void update(NativeFluidEvents.Updated event){Minecraft mc=Minecraft.getInstance();if(!mc.isSameThread())throw new IllegalStateException("Contained fluid snapshots require the client main thread");if(mc.level==null||event.host.fluidOwner().getLevel()!=mc.level||!NativeFluidWorld.installed(event.host))return;var slots=event.host.containedFluids().snapshot();if(slots.side(0).fluidType()!=null||slots.side(1).fluidType()!=null)HOSTS.put(event.host,Boolean.TRUE);else HOSTS.remove(event.host);contactsDirty=true;boolean fluidChanged=publish(event.host);if(event.modelChanged&&!fluidChanged){event.host.fluidOwner().requestModelDataUpdate();dirty(event.host);}}
    @SubscribeEvent public static void remove(NativeFluidEvents.Removed event){HOSTS.remove(event.host);contactsDirty=true;}
    private static boolean contactsDirty;
    private static void refreshContacts(){if(!contactsDirty)return;contactsDirty=false;Minecraft mc=Minecraft.getInstance();var next=new it.unimi.dsi.fastutil.longs.LongOpenHashSet();for(var host:HOSTS.keySet())if(host.fluidOwner().getLevel()==mc.level&&NativeFluidWorld.installed(host)&&(host.fluidSurfaceRegions()!=null||!host.fluidVolumeSnapshot().cells().isEmpty())&&(host.containedFluids().snapshot().side(0).fluidType()!=null||host.containedFluids().snapshot().side(1).fluidType()!=null))next.add(host.fluidOwner().getBlockPos().asLong());contacts=it.unimi.dsi.fastutil.longs.LongSets.unmodifiable(next);}
    public static boolean hasContacts(){return !contacts.isEmpty();}
    public static boolean potentialContact(BlockPos pos){var current=contacts;if(current.isEmpty())return false;for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)if(current.contains(BlockPos.asLong(pos.getX()+x,pos.getY()+y,pos.getZ()+z)))return true;return false;}
    @SubscribeEvent public static void unloaded(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel){HOSTS.clear();NativeFluidModel.clear();contacts=it.unimi.dsi.fastutil.longs.LongSets.EMPTY_SET;contactsDirty=false;cameraMaterial=0;}}
    public static void reload(){NativeFluidModel.clear();for(var host:List.copyOf(HOSTS.keySet()))if(NativeFluidWorld.installed(host))publish(host);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){Minecraft mc=Minecraft.getInstance();if(mc.level==null){if(!HOSTS.isEmpty()){HOSTS.clear();NativeFluidModel.clear();}contacts=it.unimi.dsi.fastutil.longs.LongSets.EMPTY_SET;cameraMaterial=0;return;}refreshContacts();if(HOSTS.isEmpty()){cameraMaterial=0;return;}int next=cameraMaterial(mc);if(next!=cameraMaterial){cameraMaterial=next;for(var host:List.copyOf(HOSTS.keySet()))if(NativeFluidWorld.installed(host))publish(host);}}
    private static int kind(FluidStack fluid){return fluid.is(FluidTags.WATER)?1:fluid.is(FluidTags.LAVA)?2:0;}
    private static int cameraMaterial(Minecraft mc){var camera=mc.gameRenderer.getMainCamera();var point=camera.getPosition();FluidStack contained=NativeFluidWorld.atPoint(mc.level,point);if(!contained.isEmpty()){int kind=kind(contained);if(kind!=0&&point.y-Math.floor(point.y)>height(mc,BlockPos.containing(point),contained,point.x-Math.floor(point.x),point.z-Math.floor(point.z)))return 0;return kind;}BlockPos cameraPos=BlockPos.containing(point);return NativeFluidWorld.loaded(mc.level,cameraPos)?kind(NativeFluidWorld.fromState(mc.level.getFluidState(cameraPos))):0;}
    private static void dirty(ContainedFluidHost host){
        // The state-based overload skips same-state ModelData changes. Mark actual render sections directly.
        var pos=host.fluidOwner().getBlockPos();Minecraft.getInstance().levelRenderer.setBlocksDirty(pos.getX()-1,pos.getY()-1,pos.getZ()-1,pos.getX()+1,pos.getY()+1,pos.getZ()+1);
    }
    private static boolean publish(ContainedFluidHost host){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null||host.fluidOwner().getLevel()!=mc.level)return false;BlockPos pos=host.fluidOwner().getBlockPos();var snapshot=host.containedFluids().snapshot();var volume=host.fluidVolumeSnapshot();var regions=host.fluidSurfaceRegions();List<NativeFluidRenderSnapshot.Face> faces=new ArrayList<>();FluidStack[] resolved={snapshot.side(0).fluid(),snapshot.side(1).fluid()};
        for(int side=0;side<2;side++){FluidStack fluid=resolved[side];if(fluid.isEmpty())continue;NativeFluidSurface.Neighbor[] neighbors=new NativeFluidSurface.Neighbor[6];
            for(Direction direction:Direction.values()){BlockPos next=pos.relative(direction);if(!NativeFluidWorld.loaded(mc.level,next))continue;var state=mc.level.getBlockState(next);boolean nativeSame=NativeFluidWorld.fromState(state.getFluidState()).is(fluid.getFluid());boolean hidden=nativeSame||state.isSolidRender(mc.level,next);
                if(mc.level.getBlockEntity(next) instanceof ContainedFluidHost other){var data=other.containedFluids().snapshot();neighbors[direction.get3DDataValue()]=new NativeFluidSurface.Neighbor(other.fluidVolumeSnapshot(),other.fluidSurfaceRegions(),FluidStack.isSameFluidSameComponents(fluid,data.side(0).fluid()),FluidStack.isSameFluidSameComponents(fluid,data.side(1).fluid()),hidden);}else neighbors[direction.get3DDataValue()]=new NativeFluidSurface.Neighbor(null,false,false,hidden);}
            var extension=IClientFluidTypeExtensions.of(fluid.getFluidType());ResourceLocation texture=extension.getStillTexture(fluid);if(texture==null)texture=MissingTextureAtlasSprite.getLocation();int color=fluid.is(net.minecraft.world.level.material.Fluids.WATER)?0xFF000000|BiomeColors.getAverageWaterColor(mc.level,pos):extension.getTintColor(fluid);
            double a=corner(mc,pos,fluid,0,0),b=corner(mc,pos,fluid,0,1),c=corner(mc,pos,fluid,1,1),d=corner(mc,pos,fluid,1,0);var surfaces=NativeFluidSurface.surfaces(volume,regions,side,neighbors,kind(fluid)==0?null:(x,z)->NativeFluidSurface.height(a,b,c,d,x,z));
            int light=NativeFluidRenderSnapshot.emissionLight(fluid.getFluidType().getLightLevel(fluid));for(var surface:surfaces){float shade=(float)(surface.nx*surface.nx*.6+surface.ny*surface.ny*(surface.ny<0?.5:1)+surface.nz*surface.nz*.8);int shaded=(color&0xFF000000)|((int)((color>>16&255)*shade)<<16)|((int)((color>>8&255)*shade)<<8)|(int)((color&255)*shade);emit(faces,surface,texture,shaded,light,false);if(surface.boundary==1||surface.boundary>=2&&kind(fluid)!=0&&kind(fluid)==cameraMaterial)emit(faces,surface,texture,shaded,light,true);}
        }
        var previous=host.fluidRenderSnapshot();
        if(previous.faces.equals(faces))faces=previous.faces;
        host.publishFluidRenderSnapshot(faces.isEmpty()&&resolved[0].isEmpty()&&resolved[1].isEmpty()?NativeFluidRenderSnapshot.EMPTY:new NativeFluidRenderSnapshot(faces,new NativeFluidRenderSnapshot.Contact(volume,regions,snapshot.revision,resolved[0],resolved[1])));
        var next=host.fluidRenderSnapshot();boolean changed=previous.faces!=next.faces||!sameContact(previous.contact,next.contact);if(changed){host.fluidOwner().requestModelDataUpdate();dirty(host);}return changed;
    }
    private static boolean sameContact(NativeFluidRenderSnapshot.Contact a,NativeFluidRenderSnapshot.Contact b){return a==b||a!=null&&b!=null&&a.volume()==b.volume()&&a.regions()==b.regions()&&a.revision()==b.revision();}
    private static void emit(List<NativeFluidRenderSnapshot.Face> faces,NativeFluidSurface.Surface surface,ResourceLocation texture,int color,int light,boolean reverse){List<NativeFluidSurface.Vertex> points=surface.vertices;int facing=Direction.getNearest((float)(reverse?-surface.nx:surface.nx),(float)(reverse?-surface.ny:surface.ny),(float)(reverse?-surface.nz:surface.nz)).get3DDataValue();if(points.size()==4){List<net.minecraft.world.phys.Vec3> quad=new ArrayList<>();for(int i=0;i<4;i++)quad.add(vertex(points.get(reverse?3-i:i),surface.axis));faces.add(new NativeFluidRenderSnapshot.Face(quad,texture,color,light,facing,(float)(reverse?-surface.nx:surface.nx),(float)(reverse?-surface.ny:surface.ny),(float)(reverse?-surface.nz:surface.nz),surface.axis));}else for(int i=1;i+1<points.size();i++){var a=vertex(points.get(0),surface.axis);var b=vertex(points.get(reverse?i+1:i),surface.axis);var c=vertex(points.get(reverse?i:i+1),surface.axis);faces.add(new NativeFluidRenderSnapshot.Face(List.of(a,b,c,c),texture,color,light,facing,(float)(reverse?-surface.nx:surface.nx),(float)(reverse?-surface.ny:surface.ny),(float)(reverse?-surface.nz:surface.nz),surface.axis));}}
    private static net.minecraft.world.phys.Vec3 vertex(NativeFluidSurface.Vertex point,int axis){return new net.minecraft.world.phys.Vec3(point.x,point.y,point.z);}
    private static boolean hasFluid(Minecraft mc,BlockPos pos,FluidStack fluid){if(!NativeFluidWorld.loaded(mc.level,pos))return false;if(mc.level.getBlockEntity(pos) instanceof ContainedFluidHost host)for(int side=0;side<2;side++)if(host.containedFluids().get(side).is(fluid.getFluid()))return true;return NativeFluidWorld.fromState(mc.level.getFluidState(pos)).is(fluid.getFluid());}
    private static double corner(Minecraft mc,BlockPos pos,FluidStack fluid,int x,int z){List<NativeFluidSurface.Column> columns=new ArrayList<>();for(int dx=0;dx<2;dx++)for(int dz=0;dz<2;dz++){BlockPos at=pos.offset(x-dx,0,z-dz);boolean above=hasFluid(mc,at.above(),fluid),same=hasFluid(mc,at,fluid);int level=0;if(same&&NativeFluidWorld.loaded(mc.level,at)&&!(mc.level.getBlockEntity(at) instanceof ContainedFluidHost)){var state=mc.level.getFluidState(at);level=state.getType() instanceof FlowingFluid&&state.getValue(FlowingFluid.FALLING)?8:8-state.getAmount();}boolean solid=NativeFluidWorld.loaded(mc.level,at)&&mc.level.getBlockState(at).blocksMotion();columns.add(new NativeFluidSurface.Column(above,same,level,solid));}return NativeFluidSurface.cornerHeight(columns);}
    private static double height(Minecraft mc,BlockPos pos,FluidStack fluid,double x,double z){return NativeFluidSurface.height(corner(mc,pos,fluid,0,0),corner(mc,pos,fluid,0,1),corner(mc,pos,fluid,1,1),corner(mc,pos,fluid,1,0),x,z);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void fogColor(ViewportEvent.ComputeFogColor event){var mc=Minecraft.getInstance();if(mc.level==null)return;FluidStack fluid=NativeFluidWorld.atPoint(mc.level,event.getCamera().getPosition());if(fluid.isEmpty())return;Vector3f color=IClientFluidTypeExtensions.of(fluid.getFluidType()).modifyFogColor(event.getCamera(),(float)event.getPartialTick(),mc.level,mc.options.getEffectiveRenderDistance(),mc.gameRenderer.getDarkenWorldAmount((float)event.getPartialTick()),new Vector3f(event.getRed(),event.getGreen(),event.getBlue()));event.setRed(color.x);event.setGreen(color.y);event.setBlue(color.z);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void fogRender(ViewportEvent.RenderFog event){var mc=Minecraft.getInstance();if(mc.level==null)return;FluidStack fluid=NativeFluidWorld.atPoint(mc.level,event.getCamera().getPosition());if(!fluid.isEmpty())IClientFluidTypeExtensions.of(fluid.getFluidType()).modifyFogRender(event.getCamera(),event.getMode(),mc.options.getEffectiveRenderDistance()*16F,(float)event.getPartialTick(),event.getNearPlaneDistance(),event.getFarPlaneDistance(),event.getFogShape());}
}
