package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.screen.*;
import com.vandorlabs.persistence.FrameSettings;
import com.vandorlabs.screen.*;
import java.util.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManager;

/** Inactive normal-media backend. Callers prepare coherent power and layouts on the client thread. */
public final class NativeScreenRenderer {
 private NativeScreenRenderer(){}
 private static final long WORLD_BUDGET=4L*1024*1024;
 private static final class Draw {
  final NativeScreenSettings settings;final FrameSettings frames;final ScreenAppearance appearance;final ScreenFamily family;final int meta;final boolean rows0,rows1;final NativeScreenRenderProjection projection;final NativeScreenDraw mesh;final long bytes;
  Draw(NativeScreenMaterials materials,NativeScreenEntity.Visual visual,ScreenFamily.State state,NativeScreenRenderProjection projection){settings=visual.settings();frames=visual.frames();appearance=visual.appearance();family=visual.family();meta=family.encode(state);rows0=visual.controls().get(0).enabled();rows1=visual.controls().get(1).enabled();this.projection=projection;mesh=NativeScreenDraw.build(materials,NativeScreenRenderPlan.of(visual,state),projection);var scalar=settings.scalar();bytes=mesh.retainedBytes()+2048L+2L*(scalar.selectedScreen.length()+scalar.inputPanel.length()+scalar.secondaryInputPanel.length());}
  boolean matches(NativeScreenEntity.Visual visual,ScreenFamily.State state,NativeScreenRenderProjection projection){return settings==visual.settings()&&frames==visual.frames()&&family==visual.family()&&meta==family.encode(state)&&rows0==visual.controls().get(0).enabled()&&rows1==visual.controls().get(1).enabled()&&this.projection.equals(projection)&&sameAppearance(appearance,visual.appearance());}
 }
 private static boolean sameAppearance(ScreenAppearance a,ScreenAppearance b){return a==b||a.activationMode==b.activationMode&&a.state==b.state&&a.sharedArtwork==b.sharedArtwork&&a.primaryArtwork==b.primaryArtwork&&a.effectiveMode==b.effectiveMode&&a.animationSpeed==b.animationSpeed&&a.split==b.split&&a.overrides==b.overrides&&a.primarySurface==b.primarySurface&&a.primaryMode==b.primaryMode&&a.primarySpeed==b.primarySpeed&&a.secondarySurface==b.secondarySurface&&a.secondaryMode==b.secondaryMode&&a.secondarySpeed==b.secondarySpeed&&a.light==b.light;}
 private static final class WorldCache {final LinkedHashMap<BlockPos,Draw> draws=new LinkedHashMap<>(32,.75F,true);long bytes;}
 public static final class Bake {
  private final NativeScreenMaterials materials;private final Map<Object,WorldCache> worlds=new WeakHashMap<>();private long builds;
  Bake(NativeFlatWallModel.Bake source,ResourceManager resources){materials=new NativeScreenMaterials(source,resources);}
  private WorldCache world(Object key){Objects.requireNonNull(key,"Screen cache world identity");var found=worlds.get(key);if(found==null){if(worlds.size()>=2)worlds.clear();found=new WorldCache();worlds.put(key,found);}return found;}
  NativeScreenDraw draw(NativeScreenEntity.Visual visual,ScreenFamily.State state,NativeScreenRenderProjection projection,Object world,BlockPos owner){com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();var cache=world(world);var previous=cache.draws.get(owner);if(previous!=null&&previous.matches(visual,state,projection))return previous.mesh;var next=new Draw(materials,visual,state,projection);builds++;if(previous!=null){cache.draws.remove(owner);cache.bytes-=previous.bytes;}if(next.bytes<=WORLD_BUDGET){cache.draws.put(owner.immutable(),next);cache.bytes+=next.bytes;while(cache.bytes>WORLD_BUDGET){var iterator=cache.draws.values().iterator();cache.bytes-=iterator.next().bytes;iterator.remove();}}return next.mesh;}
  public long retainedBytes(){long bytes=0;for(var cache:worlds.values())bytes+=cache.bytes;return bytes;}
  public long meshBuilds(){return builds;}
  public void clear(){com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();worlds.clear();}
 }
 public static void renderAt(Bake bake,NativeScreenEntity.Visual visual,ScreenFamily.State state,NativeScreenRenderProjection projection,Object world,BlockPos owner,PoseStack poses,MultiBufferSource buffers,int ownerLight,int overlay){if(visual.inert())return;bake.draw(visual,state,projection,world,owner).emit(poses,buffers,ownerLight,overlay);}
}
