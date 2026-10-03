package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.imageio.ImageIO;

/** Opt-in documentation capture; ordinary clients never construct this helper. */
final class DocumentationDoorCapture {
    private final File output;
    private final BlockPos door;
    private final StringBuilder times=new StringBuilder("file\telapsed_ms\topen\n");
    private long start, nextFrame;
    private int frame;
    private boolean opening, closing, finished;

    DocumentationDoorCapture(File output, BlockPos door) {
        this.output=output;this.door=door;
    }
    boolean isFinished(){return finished;}

    /** Called after the real renderer finishes a frame, using its real clock. */
    void render(Minecraft mc) {
        if(finished || mc.world==null)return;
        long now=System.nanoTime();
        if(start==0){start=now;nextFrame=now;}
        double elapsed=(now-start)/1_000_000D;
        if(elapsed>=1000 && !opening){opening=true;toggle(mc,true);}
        if(elapsed>=2200 && !closing){closing=true;toggle(mc,false);}
        if(elapsed>=3400){finish(mc);return;}
        if(now<nextFrame)return;
        // Missed frames are not duplicated: the encoder uses actual timestamps.
        nextFrame=now+50_000_000L;
        String name=String.format(java.util.Locale.ROOT,"door-frame-%04d.png",frame++);
        try {
            boolean open=mc.world.getBlockState(door).getValue(BlockVandorDoor.OPEN);
            if(!ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,
                    mc.getFramebuffer()),"png",new File(output,name)))throw new IOException("PNG writer unavailable");
            times.append(name).append('\t').append(String.format(java.util.Locale.ROOT,"%.3f",elapsed))
                    .append('\t').append(open).append('\n');
        } catch(IOException e){throw new IllegalStateException("Door documentation capture failed",e);}
    }
    private void toggle(Minecraft mc, boolean open) {
        mc.getIntegratedServer().addScheduledTask(()-> {
            World world=mc.getIntegratedServer().getWorld(0);
            for(BlockPos part:new BlockPos[]{door,door.up()}) {
                net.minecraft.block.state.IBlockState state=world.getBlockState(part);
                if(!(state.getBlock() instanceof BlockVandorDoor))throw new IllegalStateException("Documentation door disappeared");
                world.setBlockState(part,state.withProperty(BlockVandorDoor.OPEN,open),3);
            }
        });
    }
    private void finish(Minecraft mc) {
        TileEntitySpaceDoor tile=(TileEntitySpaceDoor)mc.world.getTileEntity(door);
        if(frame<20 || tile==null || tile.getDesign()!=0 || !tile.isFramed()
                || mc.world.getBlockState(door).getValue(BlockVandorDoor.OPEN))
            throw new IllegalStateException("Incomplete documented Observation door cycle");
        try {Files.write(new File(output,"door-frames.tsv").toPath(),times.toString().getBytes(StandardCharsets.UTF_8));}
        catch(IOException e){throw new IllegalStateException(e);}
        finished=true;
        System.out.println("[vandorlabs][reprolab] documentation-door-capture PASS frames="+frame+" real server open/close, live renderer");
    }
}
