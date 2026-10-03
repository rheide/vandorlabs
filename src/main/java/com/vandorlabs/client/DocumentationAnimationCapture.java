package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.tiles.*;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import javax.imageio.ImageIO;

/** Documentation-only recorder: framebuffer reads during motion, PNG writes afterward. */
final class DocumentationAnimationCapture {
    static final int X=160,Y=4; // scope uses a generous bound around the gallery
    final JsonObject spec;
    private final File output;
    private final List<BufferedImage> frames=new ArrayList<>();
    private final StringBuilder times=new StringBuilder("file\telapsed_ms\topen\n");
    private long start,nextFrame;
    private int phase;
    private boolean finished;
    private final int motionMs;

    DocumentationAnimationCapture(File root,JsonObject spec) {
        this.spec=spec;output=new File(root,spec.get("id").getAsString());
        if(!output.mkdirs() && !output.isDirectory())throw new IllegalStateException("Capture directory unavailable");
        motionMs=kind().equals("ramp")?2000:1000;
    }
    String kind(){return spec.get("kind").getAsString();}
    boolean isFinished(){return finished;}
    static List<JsonObject> scenes(boolean suite) {
        List<JsonObject> result=new ArrayList<>();
        String selection=System.getProperty("vandorlabs.documentationAnimationFilter","");
        Set<String> requested=new HashSet<>(Arrays.asList(selection.split(",")));
        try(InputStream stream=DocumentationAnimationCapture.class.getResourceAsStream("/assets/vandorlabs/data/documentation_animations.json")) {
            for(JsonElement value:new JsonParser().parse(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonArray()) {
                JsonObject entry=value.getAsJsonObject();String id=entry.get("id").getAsString();
                if((suite || id.equals("door-rotating")) && (selection.isEmpty() || requested.contains(id)))result.add(entry);
            }
        } catch(IOException e){throw new IllegalStateException(e);}
        if(result.isEmpty())throw new IllegalStateException("No documentation animation matches the filter");
        return result;
    }
    private static boolean inStage(TileEntity tile) {
        BlockPos p=tile.getPos();return Math.abs(p.getX()-X)<=12 && p.getY()>=2 && p.getY()<18 && p.getZ()>=-26 && p.getZ()<=-8;
    }
    private List<TileEntity> members(World world) {
        List<TileEntity> result=new ArrayList<>();
        for(TileEntity tile:new ArrayList<>(world.loadedTileEntityList))if(!tile.isInvalid() && inStage(tile) && world.getTileEntity(tile.getPos())==tile) {
            if(kind().equals("door") && tile instanceof TileEntitySpaceDoor
                    && world.getBlockState(tile.getPos()).getValue(BlockVandorDoor.HALF)==net.minecraft.block.BlockDoor.EnumDoorHalf.LOWER
                    || kind().equals("trapdoor") && tile instanceof TileEntityProgrammableTrapdoor
                    || kind().equals("ramp") && tile instanceof TileEntityRampController)result.add(tile);
        }
        if(result.isEmpty())throw new IllegalStateException("Documentation fixture has no "+kind()+" tiles: "+spec.get("id"));
        return result;
    }
    /** Start at the closed endpoint even for gallery fixtures built deployed. */
    void prepare(World world) {
        for(TileEntity member:members(world))if(member instanceof TileEntityProgrammableTrapdoor) {
            TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)member;
            if(spec.has("overSurface"))leaf.setSlideOverSurface(spec.get("overSurface").getAsBoolean());
            if(spec.has("intoWall"))leaf.setSlideIntoWall(spec.get("intoWall").getAsBoolean());
        }
        toggle(world,false);
        if(kind().equals("ramp"))for(TileEntity member:members(world)) {
            TileEntityRampController controller=(TileEntityRampController)member;
            ControllerRuntimeChecks.elapsed(controller,controller.durationTicks()+1);
        }
    }
    private void toggle(World world,boolean open) {
        Set<BlockPos> visited=new HashSet<>();
        for(TileEntity member:members(world)) {
            if(member instanceof TileEntitySpaceDoor) {
                for(BlockPos part:new BlockPos[]{member.getPos(),member.getPos().up()})
                    world.setBlockState(part,world.getBlockState(part).withProperty(BlockVandorDoor.OPEN,open),3);
            } else if(member instanceof TileEntityProgrammableTrapdoor) {
                TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)member;
                if(!visited.add(leaf.getPos()))continue;
                for(TileEntityProgrammableTrapdoor other:leaf.group())visited.add(other.getPos());
                leaf.requestOpen(open);
            } else {
                TileEntityRampController controller=(TileEntityRampController)member;
                BlockPos power=controller.getPos().north();
                world.setBlockState(power,open?net.minecraft.init.Blocks.REDSTONE_BLOCK.getDefaultState():net.minecraft.init.Blocks.AIR.getDefaultState(),3);
                controller.updatePower();
                if(controller.isOpen()!=open)throw new IllegalStateException("Ramp signal did not switch: "+controller.status);
            }
        }
    }
    private boolean active(World world) {
        boolean any=false,all=true;
        for(TileEntity member:members(world)) {
            boolean value=member instanceof TileEntityRampController?((TileEntityRampController)member).isOpen()
                    :world.getBlockState(member.getPos()).getValue(member instanceof TileEntityProgrammableTrapdoor?BlockTrapDoor.OPEN:BlockVandorDoor.OPEN);
            any|=value;all&=value;
        }
        if(any!=all)throw new IllegalStateException("Documentation group not synchronized");
        return all;
    }
    /** Called after actual rendering; image encoding cannot stall the recorded cycle. */
    void render(Minecraft mc) {
        if(finished || mc.world==null)return;
        long now=System.nanoTime();if(start==0){start=now;nextFrame=now;}
        double elapsed=(now-start)/1_000_000D;
        int closeAt=600+motionMs,endAt=closeAt+motionMs;
        if(elapsed>=600 && phase==0){phase=1;mc.getIntegratedServer().addScheduledTask(()->toggle(mc.getIntegratedServer().getWorld(0),true));}
        if(elapsed>=closeAt && phase==1){phase=2;mc.getIntegratedServer().addScheduledTask(()->toggle(mc.getIntegratedServer().getWorld(0),false));}
        if(elapsed>=endAt){finish(mc,endAt);return;}
        if(now<nextFrame)return;
        nextFrame+=50_000_000L;
        if(nextFrame<now)nextFrame=now+50_000_000L;
        BufferedImage screenshot=ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        // Same logical 600x500 crop as the prototype, at 70% client resolution.
        int left=Math.round(mc.displayWidth*350F/1280),top=Math.round(mc.displayHeight*150F/720);
        int width=Math.round(mc.displayWidth*600F/1280),height=Math.round(mc.displayHeight*500F/720);
        BufferedImage cropped=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics=cropped.createGraphics();
        graphics.drawImage(screenshot,0,0,width,height,left,top,left+width,top+height,null);graphics.dispose();
        String name=String.format(Locale.ROOT,"frame-%04d.png",frames.size());frames.add(cropped);
        times.append(name).append('\t').append(String.format(Locale.ROOT,"%.3f",elapsed)).append('\t').append(active(mc.world)).append('\n');
    }
    private void finish(Minecraft mc,int duration) {
        if(frames.size()<25 || active(mc.world))throw new IllegalStateException("Incomplete documentation cycle");
        if(kind().equals("ramp"))for(TileEntity tile:members(mc.world))
            if(((TileEntityRampController)tile).isMoving())throw new IllegalStateException("Ramp did not finish retracting");
        JsonObject metadata=new JsonParser().parse(spec.toString()).getAsJsonObject();metadata.addProperty("duration_ms",duration);
        metadata.addProperty("width",frames.get(0).getWidth());metadata.addProperty("height",frames.get(0).getHeight());
        try {
            for(int i=0;i<frames.size();i++)ImageIO.write(frames.get(i),"png",new File(output,String.format(Locale.ROOT,"frame-%04d.png",i)));
            Files.write(new File(output,"frames.tsv").toPath(),times.toString().getBytes(StandardCharsets.UTF_8));
            Files.write(new File(output,"capture.json").toPath(),metadata.toString().getBytes(StandardCharsets.UTF_8));
        } catch(IOException e){throw new IllegalStateException(e);}
        System.out.println("[vandorlabs][reprolab] documentation-animation PASS "+spec.get("id").getAsString()+" frames="+frames.size());
        frames.clear();finished=true;
    }
}
