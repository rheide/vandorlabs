package com.vandorlabs.neoforge.mixin;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(LevelRenderer.class)
public interface SegmentedLevelRendererAccessor {
 @Accessor("viewArea") ViewArea vandorlabs$segmentedViewArea();
 @Accessor("sectionRenderDispatcher") SectionRenderDispatcher vandorlabs$segmentedDispatcher();
}
