package com.vandorlabs.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vandorlabs.blocks.*;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Record real four-detent clicks and their channel-powered consumer for the block guide. */
final class SignalControlAnimationCapture {
    private static final BlockPos CONSUMER=new BlockPos(159,6,-18);
    private static final int DURATION_MS=6000,STEP_MS=1200;
    private final JsonObject spec;
    private final File output;
    private final List<BufferedImage> frames=new ArrayList<>();
    private final StringBuilder times=new StringBuilder("file\telapsed_ms\tcontrol_level\tconsumer_level\tparticles\n");
    private long start,nextFrame;
    private int phase;
    private boolean finished;

    SignalControlAnimationCapture(File output,JsonObject spec){this.output=output;this.spec=spec;}
    boolean isFinished(){return finished;}
    private boolean thruster(){return spec.get("consumer").getAsString().equals("thruster");}
    private BlockPos controlPos(){return new BlockPos(161,thruster()?5:6,-18);}
    private static Block block(String id){
        Block block=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",id));
        if(block==Blocks.AIR)throw new IllegalStateException("Missing signal documentation block "+id);
        return block;
    }
    static void build(World world,String shot){
        if(world.isRemote)return;
        boolean thruster=shot.endsWith("thruster");
        BlockPos control=new BlockPos(161,thruster?5:6,-18);
        BlockPos.getAllInBox(new BlockPos(156,4,-24),new BlockPos(164,4,-16))
                .forEach(pos->world.setBlockState(pos,Blocks.STONEBRICK.getDefaultState(),3));
        BlockPos.getAllInBox(new BlockPos(156,5,-17),new BlockPos(164,8,-17))
                .forEach(pos->world.setBlockState(pos,Blocks.STONEBRICK.getDefaultState(),3));
        // Fill light keeps the control readable while the consumer lights its own surroundings.
        world.setBlockState(new BlockPos(161,9,-18),Blocks.SEA_LANTERN.getDefaultState(),3);
        world.setWorldTime(18000);
        world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");
        EntityPlayerMP owner=(EntityPlayerMP)world.playerEntities.get(0);
        EnumFacing mount=thruster?EnumFacing.UP:EnumFacing.NORTH;
        Block controller=block(thruster?"thruster_lever":"wall_slider");
        ItemStack stack=new ItemStack(controller);
        IBlockState placed=controller.getStateForPlacement(world,control,mount,.5F,.5F,.5F,0,owner,EnumHand.MAIN_HAND);
        if(!((ItemBlock)stack.getItem()).placeBlockAt(stack,owner,world,control,mount,.5F,.5F,.5F,placed))
            throw new IllegalStateException("Signal documentation placement failed");
        ChannelList channels=ChannelList.of(thruster?32101:32102);
        ((TileEntitySignalControl)world.getTileEntity(control)).setRedstoneChannels(channels);
        if(thruster){
            world.setBlockState(CONSUMER,block("rocket_thruster").getDefaultState().withProperty(BlockPropulsionLight.FACING,EnumFacing.NORTH),3);
            TileEntityRedstoneLight tile=(TileEntityRedstoneLight)world.getTileEntity(CONSUMER);
            tile.setJoin(false);tile.setRedstoneChannels(channels);tile.setParticleLevel(2);tile.configureSignalBrightness(true,10);
        }else{
            world.setBlockState(CONSUMER,ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.NORTH),3);
            TileEntityProgrammableLight tile=(TileEntityProgrammableLight)world.getTileEntity(CONSUMER);
            tile.configure(0,15,false,channels,ScreenHousingTextures.INDUSTRIAL_BLOCK,SpaceDoorData.TRIGGER_DISABLED);
            tile.configureSignalBrightness(true,0);
        }
    }
    void prepare(World world){
        ((TileEntitySignalControl)world.getTileEntity(controlPos())).setStep(0);
        verify(world,0);
        world.checkLightFor(net.minecraft.world.EnumSkyBlock.BLOCK,new BlockPos(161,9,-18));
        for(net.minecraft.entity.Entity entity:new ArrayList<>(world.loadedEntityList))
            if(entity instanceof net.minecraft.entity.item.EntityItem)entity.setDead();
    }
    private int consumerLevel(World world){
        TileEntity tile=world.getTileEntity(CONSUMER);
        return thruster()?((TileEntityRedstoneLight)tile).getBrightness():((TileEntityProgrammableLight)tile).getLightLevel();
    }
    private void verify(World world,int expected){
        TileEntitySignalControl control=(TileEntitySignalControl)world.getTileEntity(controlPos());
        if(control.getOutputLevel()!=expected || consumerLevel(world)!=expected)
            throw new IllegalStateException("Signal documentation consumer did not follow channel level "+expected);
        if(thruster() && ((TileEntityRedstoneLight)world.getTileEntity(CONSUMER)).isParticleStreamSelected()!=(expected>=10))
            throw new IllegalStateException("Signal documentation particle threshold mismatch");
    }
    private void click(World world,int step){
        EntityPlayerMP owner=(EntityPlayerMP)world.playerEntities.get(0);owner.setSneaking(false);
        IBlockState state=world.getBlockState(controlPos());
        state.getBlock().onBlockActivated(world,controlPos(),state,owner,EnumHand.MAIN_HAND,thruster()?EnumFacing.UP:EnumFacing.NORTH,.5F,.5F,.5F);
        verify(world,new int[]{0,5,10,15}[step%4]);
    }
    void render(Minecraft mc){
        if(finished || mc.world==null)return;
        long now=System.nanoTime();if(start==0){start=now;nextFrame=now;}
        double elapsed=(now-start)/1_000_000D;
        if(phase<4 && elapsed>=(phase+1)*STEP_MS){
            final int next=++phase;
            mc.getIntegratedServer().addScheduledTask(()->click(mc.getIntegratedServer().getWorld(0),next));
        }
        if(elapsed>=DURATION_MS){finish(mc);return;}
        if(now<nextFrame)return;nextFrame=now+50_000_000L;
        BufferedImage screenshot=ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        int left=Math.round(mc.displayWidth*350F/1280),top=Math.round(mc.displayHeight*150F/720);
        int width=Math.round(mc.displayWidth*600F/1280),height=Math.round(mc.displayHeight*500F/720);
        BufferedImage cropped=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics=cropped.createGraphics();
        graphics.drawImage(screenshot,0,0,width,height,left,top,left+width,top+height,null);graphics.dispose();
        String name=String.format(Locale.ROOT,"frame-%04d.png",frames.size());frames.add(cropped);
        int level=((TileEntitySignalControl)mc.world.getTileEntity(controlPos())).getOutputLevel();
        boolean particles=thruster() && ((TileEntityRedstoneLight)mc.world.getTileEntity(CONSUMER)).isParticleStreamSelected();
        times.append(name).append('\t').append(String.format(Locale.ROOT,"%.3f",elapsed)).append('\t').append(level).append('\t').append(consumerLevel(mc.world)).append('\t').append(particles).append('\n');
    }
    private void finish(Minecraft mc){
        if(frames.size()<60 || phase!=4)throw new IllegalStateException("Incomplete signal documentation cycle");
        verify(mc.getIntegratedServer().getWorld(0),0);
        JsonObject metadata=new JsonParser().parse(spec.toString()).getAsJsonObject();metadata.addProperty("duration_ms",DURATION_MS);
        metadata.addProperty("width",frames.get(0).getWidth());metadata.addProperty("height",frames.get(0).getHeight());
        try{
            for(int i=0;i<frames.size();i++)ImageIO.write(frames.get(i),"png",new File(output,String.format(Locale.ROOT,"frame-%04d.png",i)));
            Files.write(new File(output,"frames.tsv").toPath(),times.toString().getBytes(StandardCharsets.UTF_8));
            Files.write(new File(output,"capture.json").toPath(),metadata.toString().getBytes(StandardCharsets.UTF_8));
        }catch(IOException e){throw new IllegalStateException(e);}
        System.out.println("[vandorlabs][reprolab] documentation-animation PASS "+spec.get("id").getAsString()+" frames="+frames.size()+" levels=0,5,10,15,0");
        frames.clear();finished=true;
    }
}
