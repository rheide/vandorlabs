package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.screen.NativeScreenEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;

/** One renderer binding for the preserved entity ID, with each family's visibility contract. */
public final class NativeProgrammableRenderer implements BlockEntityRenderer<BlockEntity> {
 private final NativePanelPlaybackRenderer panels=new NativePanelPlaybackRenderer();
 private final NativeScreenBlockEntityRenderer screens;
 public NativeProgrammableRenderer(BlockEntityRendererProvider.Context context){screens=new NativeScreenBlockEntityRenderer(context);}
 @Override public void render(BlockEntity owner,float partialTick,PoseStack poses,MultiBufferSource buffers,int light,int overlay){if(owner instanceof NativeScreenEntity screen)screens.render(screen,partialTick,poses,buffers,light,overlay);else panels.render(owner,partialTick,poses,buffers,light,overlay);}
 @Override public AABB getRenderBoundingBox(BlockEntity owner){return owner instanceof NativeScreenEntity screen?screens.getRenderBoundingBox(screen):panels.getRenderBoundingBox(owner);}
 @Override public boolean shouldRender(BlockEntity owner,Vec3 camera){return owner instanceof NativeScreenEntity screen?screens.shouldRender(screen,camera):panels.shouldRender(owner,camera);}
}
