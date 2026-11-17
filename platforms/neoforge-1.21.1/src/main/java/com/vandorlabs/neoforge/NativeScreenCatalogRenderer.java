package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.neoforge.builders.client.NativeBlockRecipePreview;
import com.vandorlabs.neoforge.screen.NativeScreenChipView;
import com.vandorlabs.render.ScreenSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import java.util.*;

/** Safe Catalog template preview, shared by integrated trays and joined side-mounted Screen readers. */
public final class NativeScreenCatalogRenderer {
 private static final Set<NativeScreenCatalogRenderer> INSTANCES=Collections.newSetFromMap(new WeakHashMap<>());
 private record Prepared(ItemStack item,NativeOverlayCanvas.Mesh caption,long bytes){}
 private final NativeOverlayCanvas canvas;private final Map<NativeScreenChipView,Prepared> prepared=new WeakHashMap<>();private long bytes;private final VisualClock clock=new VisualClock();
 public NativeScreenCatalogRenderer(Font font){canvas=new NativeOverlayCanvas(font);INSTANCES.add(this);}
 private Prepared prepared(NativeScreenChipView view){var found=prepared.get(view);if(found!=null)return found;var item=view.catalogItem();var mesh=canvas.build(t->{t.plain("CATALOG",7,8,0xc8eeff);t.fitted(view.catalogName(),7,24,306,0xe2edf5);});found=new Prepared(item,mesh,mesh.bytes()+18000);if(bytes+found.bytes>1024*1024){prepared.clear();bytes=0;}prepared.put(view,found);bytes+=found.bytes;return found;}
 public void render(NativeScreenChipView view,ScreenSurface.Quad content,Matrix4fc pixelToOwner,PoseStack ownerPoses,MultiBufferSource buffers,int light,int columns,int rows){if(!view.catalog())return;var value=prepared(view);int height=columns>0?NativeScreenJournalRenderer.joinedHeight(content,columns,rows):NativeScreenJournalRenderer.height(content);var ui=new Matrix4f();if(columns>0)NativeScreenJournalRenderer.joinedUiMatrix(content,columns,rows,height,ui);else NativeScreenJournalRenderer.uiMatrix(content,height,ui);var matrix=new Matrix4f(ownerPoses.last().pose()).mul(pixelToOwner).mul(ui);NativeOverlayCanvas.rectangle(NativeOverlayCanvas.solid(buffers),matrix,0,0,320,height,0,0xff09121c);NativeOverlayCanvas.draw(value.caption,new Matrix4f(matrix).translate(0,0,.04f),buffers,0xf000f0);
  var poses=new PoseStack();poses.last().pose().set(matrix);float size=Math.min(210,height-52)*.64f;poses.translate(160,(height+44)/2.0,.15);poses.scale(size,size,.16f);poses.mulPose(Axis.XP.rotationDegrees(18));poses.mulPose(Axis.YP.rotationDegrees((float)(clock.sample(System.nanoTime(),Minecraft.getInstance().isPaused())*1.5)));poses.translate(-.5,-.5,-.5);NativeBlockRecipePreview.render(value.item,poses,buffers,0xf000f0,OverlayTexture.NO_OVERLAY,1);
 }
 public static void clearAll(){for(var renderer:INSTANCES)renderer.clear();}
 public long retainedBytes(){return bytes;}public void clear(){prepared.clear();bytes=0;}
}
