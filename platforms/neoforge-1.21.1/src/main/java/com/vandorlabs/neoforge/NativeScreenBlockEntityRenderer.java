package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.screen.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

/** Unregistered normal/ROWS bridge. Worlds, power and layout are prepared only on the client thread. */
public final class NativeScreenBlockEntityRenderer implements BlockEntityRenderer<NativeScreenEntity> {
 private final net.minecraft.client.gui.Font font;private NativeFlatWallModel.Bake source;private NativeScreenRenderer.Bake bake;private NativeScreenRowsRenderer rows;
 private record Bounds(int meta,boolean small,int wall,boolean ceiling,int ceilingPosition,boolean inert,AABB box){boolean matches(NativeScreenEntity tile,int meta){var settings=tile.screenSettings();return this.meta==meta&&small==settings.scalar().smallInput&&wall==settings.scalar().wallPosition&&ceiling==settings.ceilingMounted()&&ceilingPosition==settings.ceilingPosition()&&inert==tile.inert();}}
 private Map<NativeScreenEntity,Bounds> bounds=new WeakHashMap<>();private Map<net.minecraft.world.level.block.state.BlockState,com.vandorlabs.screen.ScreenFamily.State> states=new IdentityHashMap<>();
 private com.vandorlabs.screen.ScreenFamily.State state(NativeScreenBlock block,net.minecraft.world.level.block.state.BlockState raw){var found=states.get(raw);if(found==null){found=block.screenState(raw);if(states.size()>=4096)states=new IdentityHashMap<>();states.put(raw,found);}return found;}
 public NativeScreenBlockEntityRenderer(BlockEntityRendererProvider.Context context){font=context.getFont();}
 private boolean prepare(){var next=NativePanelPlaybackRenderer.currentBake();if(next==null)return false;if(source!=next){source=next;bake=new NativeScreenRenderer.Bake(next,Minecraft.getInstance().getResourceManager());rows=new NativeScreenRowsRenderer(font);NativeScreenRenderGroups.clear();NativeScreenRenderQueries.clear();}NativeScreenRenderGroups.bind();return true;}
 /** Same stored owner block/sky components as World.getCombinedLight(pos,0), without own-emission injection. */
 public static int ownerLight(NativeScreenEntity owner){if(!Minecraft.getInstance().isSameThread())throw new IllegalStateException("Screen owner light off client thread");var level=owner.getLevel();return level==null?0:LightTexture.pack(level.getBrightness(LightLayer.BLOCK,owner.getBlockPos()),level.getBrightness(LightLayer.SKY,owner.getBlockPos()));}
 @Override public void render(NativeScreenEntity owner,float partialTick,PoseStack poses,MultiBufferSource buffers,int light,int overlay){if(!NativeScreenRenderGroups.installed(owner)||!(owner.getBlockState().getBlock() instanceof NativeScreenBlock block)||!prepare())return;var visual=NativeScreenRenderQueries.prepare(owner);var projection=NativeScreenRenderQueries.projection(owner,visual);visual=owner.visual();NativeScreenRenderer.renderAt(bake,visual,state(block,owner.getBlockState()),projection,owner.getLevel(),owner.getBlockPos(),poses,buffers,ownerLight(owner),overlay,owner.getLevel().getGameTime(),rows);}
 @Override public AABB getRenderBoundingBox(NativeScreenEntity owner){if(!NativeScreenRenderGroups.installed(owner))return new AABB(owner.getBlockPos());var state=owner.getBlockState();int meta=state.getBlock() instanceof NativeScreenBlock block?block.family().encode(state(block,state)):-1;var previous=bounds.get(owner);if(previous!=null&&previous.matches(owner,meta))return previous.box;var pos=owner.getBlockPos();var settings=owner.screenSettings();AABB box;if(owner.inert()||meta<0)box=new AABB(pos);else if(settings.ceilingMounted())box=new AABB(pos.getX()-1,pos.getY(),pos.getZ()-1,pos.getX()+2,pos.getY()+1,pos.getZ()+2);else box=((NativeScreenBlock)state.getBlock()).geometry(state,owner.getLevel(),pos).bounds().move(pos);if(bounds.size()>=8192)bounds=new WeakHashMap<>();bounds.put(owner,new Bounds(meta,settings.scalar().smallInput,settings.scalar().wallPosition,settings.ceilingMounted(),settings.ceilingPosition(),owner.inert(),box));return box;}
 public long meshBuilds(){return bake==null?0:bake.meshBuilds();}public long retainedBytes(){return (bake==null?0:bake.retainedBytes())+(rows==null?0:rows.retainedBytes())+bounds.size()*192L+states.size()*128L;}
 public void clear(){if(bake!=null)bake.clear();if(rows!=null)rows.clear();bounds=new WeakHashMap<>();states=new IdentityHashMap<>();NativeScreenRenderGroups.clear();NativeScreenRenderQueries.clear();}
}
