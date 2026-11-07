package com.vandorlabs.neoforge;
import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.programmer.NativeProgrammerJournalView;
import com.vandorlabs.neoforge.programmer.client.NativeProgrammerJournalFrame;
import com.vandorlabs.programmer.JournalOverlay;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
/** Programmer's prepared Input/Output journal, relative to the oriented model minimum origin. */
public final class NativeProgrammerJournalRenderer {
 private final NativeScreenJournalRenderer canvas;
 public NativeProgrammerJournalRenderer(Font font,NativeFlatWallModel.Bake materials){canvas=new NativeScreenJournalRenderer(font,materials);}
 public void render(NativeProgrammerJournalView view,Direction facing,PoseStack poses,MultiBufferSource buffers){if(view!=null)canvas.render(view.overlay(),JournalOverlay.MIN_HEIGHT,NativeProgrammerJournalFrame.matrix(facing),poses,buffers,0x00F000F0);}
 public long retainedBytes(){return canvas.retainedBytes();}
 public long builds(){return canvas.builds();}
 public void clear(){canvas.clear();}
}
