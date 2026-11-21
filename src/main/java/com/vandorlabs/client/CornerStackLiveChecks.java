package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableCornerGlass;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reported workflow: Programmable Glass walls continued by Programmable Corner Glass, two blocks
 * high, including two corners side by side with a corner placed on top of each. Every corner
 * must be visible. For each corner the camera looks at it, a screenshot is compared with one
 * taken after removing only that block, and the difference must be clearly visible. Lower
 * corners, already known to render, are measured the same way as controls. All blocks are
 * placed through the real item path.
 */
public final class CornerStackLiveChecks {
    private static final BlockPos ORIGIN=new BlockPos(60,90,-40);
    private static final String GLASS="vandorlabs:programmable_glass",CORNER="vandorlabs:programmable_corner_glass";
    /** Mean absolute RGB change over the centre crop that counts as visible. */
    private static final double VISIBLE=3.0;
    private static final class Target {
        final String name;final BlockPos pos;final boolean control;double difference=-1;
        Target(String name,BlockPos pos,boolean control){this.name=name;this.pos=pos;this.control=control;}
    }
    private static volatile List<Target> targets;
    /** Render calls per corner while it is measured: "pass:pane/frame" summaries. */
    private static final java.util.Map<BlockPos,java.util.Set<String>> calls=new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile Throwable failure;
    private static volatile boolean removed;
    private static int index,ticks;
    private static BufferedImage before;
    private static boolean requested;
    private CornerStackLiveChecks(){}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("corner-stack "+message);}

    public static boolean tick(Minecraft mc,File output)throws Exception{
        mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.hideGUI=true;mc.gameSettings.thirdPersonView=0;
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        MinecraftServer server=mc.getIntegratedServer();
        if(failure!=null)throw new IllegalStateException("corner-stack server task",failure);
        if(CornerGlassRenderer.observer==null)CornerGlassRenderer.observer=(tile,pass,pane,frame,x,y,z)->calls.computeIfAbsent(tile.getPos().toImmutable(),k->java.util.concurrent.ConcurrentHashMap.newKeySet()).add(pass+":"+(pane?"pane":"-")+"/"+(frame?"frame":"-")+"@"+String.format("%.2f,%.2f,%.2f",x,y,z));
        if(!requested){
            if(mc.player==null||++ticks<40)return false;
            requested=true;
            server.addScheduledTask(()->{try{targets=build(server.getWorld(0),server.getPlayerList().getPlayers().get(0));}catch(RuntimeException|LinkageError error){failure=error;}});
            return false;
        }
        List<Target> all=targets;if(all==null)return false;
        if(index>=all.size())return finish(all);
        Target target=all.get(index);
        if(ticks==0){
            // Stand three blocks south of the corner, looking straight at its centre.
            double x=target.pos.getX()+.5,y=target.pos.getY()+.5,z=target.pos.getZ()+3.5;
            server.addScheduledTask(()->server.getPlayerList().getPlayers().get(0).connection.setPlayerLocation(x,y-mc.player.getEyeHeight(),z,180,0));
            removed=false;before=null;
        }
        ticks++;
        // Hold the camera on the target from the client side too; the server teleport alone drifts.
        double cx=target.pos.getX()+.5,cy=target.pos.getY()+.5-mc.player.getEyeHeight(),cz=target.pos.getZ()+3.5;
        mc.player.capabilities.isFlying=true;mc.player.motionX=mc.player.motionY=mc.player.motionZ=0;
        mc.player.setLocationAndAngles(cx,cy,cz,180,0);
        mc.player.prevPosX=mc.player.lastTickPosX=cx;mc.player.prevPosY=mc.player.lastTickPosY=cy;mc.player.prevPosZ=mc.player.lastTickPosZ=cz;
        mc.player.rotationYaw=mc.player.prevRotationYaw=180;mc.player.rotationPitch=mc.player.prevRotationPitch=0;
        mc.player.rotationYawHead=mc.player.prevRotationYawHead=180;
        if(before==null){
            // The first view after building also waits for the new chunks to render.
            int settle=index==0?160:40;
            if(ticks==settle-2)calls.clear();
            if(ticks<settle)return false;
            double off=mc.player.getDistance(cx,cy,cz);
            if(off>.3){require(ticks<400,target.name+" camera not in place: player at "+mc.player.getPositionVector()+" wanted "+cx+","+cy+","+cz);return false;}
            if(!(mc.world.getBlockState(target.pos).getBlock() instanceof BlockProgrammableCornerGlass)){require(ticks<400,target.name+" never reached the client");return false;}
            before=ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            compiledAtCapture="globalCorners="+CornerGlassRefresh.tracked();
            ImageIO.write(before,"png",new File(output,"corner-stack-"+index+"-"+target.name+".png"));
            BlockPos at=target.pos;
            server.addScheduledTask(()->{server.getWorld(0).setBlockToAir(at);removed=true;});
            ticks=1;return false;
        }
        if(!removed||mc.world.getBlockState(target.pos).getBlock()!=Blocks.AIR||++ticks<40)return false;
        BufferedImage after=ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        target.difference=difference(before,after);
        System.out.println("[vandorlabs] corner-stack "+target.name+" at "+target.pos+" visible difference="+String.format("%.2f",target.difference)+(target.control?" (control)":"")+" renderCalls="+calls.get(target.pos)+" "+compiledAtCapture+" camera="+String.format("%.2f,%.2f,%.2f yaw=%.0f",mc.player.posX,mc.player.posY,mc.player.posZ,mc.player.rotationYaw));
        index++;ticks=0;return false;
    }
    private static String compiledAtCapture="";
    private static boolean finish(List<Target> all){
        List<String> invisible=new ArrayList<>();
        for(Target target:all)if(target.difference<VISIBLE)invisible.add(target.name+(target.control?" (control)":"")+" difference="+String.format("%.2f",target.difference));
        require(invisible.isEmpty(),"corners not visible: "+String.join(" | ",invisible));
        System.out.println("[vandorlabs] corner-stack PASS "+all.size()+" corners visible including corners stacked on corners");
        return true;
    }
    /**
     * Mean absolute RGB difference over the middle of the target block's own image. From three
     * blocks away a block spans about a third of the frame height, so a square a sixth of the
     * height tall stays inside it and away from neighbours whose joined edges change too.
     */
    private static double difference(BufferedImage a,BufferedImage b){
        int w=a.getWidth(),h=a.getHeight(),half=h/12;long total=0;int samples=0;
        for(int y=h/2-half;y<h/2+half;y+=2)for(int x=w/2-half;x<w/2+half;x+=2){
            int p=a.getRGB(x,y),q=b.getRGB(x,y);
            total+=Math.abs((p>>16&255)-(q>>16&255))+Math.abs((p>>8&255)-(q>>8&255))+Math.abs((p&255)-(q&255));samples+=3;
        }
        return (double)total/samples;
    }
    private static void place(WorldServer world,EntityPlayerMP player,Block block,BlockPos support,EnumFacing face,float hitX,float hitY,float hitZ,BlockPos expect,String what){
        require(LiveGallery.place(world,player,new ItemStack(block),support,face,hitX,hitY,hitZ),what+" placed");
        require(world.getBlockState(expect).getBlock()==block,what+" at "+expect+" got "+world.getBlockState(expect));
    }
    /**
     * One fixture: a 2x2 glass wall along x ending at {@code min.x+1}, viewed from the south, with
     * Near (hit .15), Middle (.5) or Far (.85) depth chosen by where the floor click lands.
     */
    private static void fixture(WorldServer world,EntityPlayerMP player,BlockPos min,float depth,String label,List<Target> out){
        Block glass=Block.getBlockFromName(GLASS),corner=Block.getBlockFromName(CORNER);
        for(int x=0;x<=1;x++){
            place(world,player,glass,min.add(x,-1,0),EnumFacing.UP,.5F,1,depth,min.add(x,0,0),label+" lower glass");
            place(world,player,glass,min.add(x,0,0),EnumFacing.UP,.5F,1,depth,min.add(x,1,0),label+" upper glass");
        }
        BlockPos first=min.add(2,0,0),second=min.add(3,0,0);
        // Two corners side by side beyond the wall's east end, as reported.
        place(world,player,corner,min.add(1,0,0),EnumFacing.EAST,1,.5F,depth,first,label+" first lower corner from the glass edge");
        place(world,player,corner,first,EnumFacing.EAST,1,.5F,depth,second,label+" second lower corner beside the first");
        // Upper corners: on top of each lower corner.
        place(world,player,corner,first,EnumFacing.UP,.5F,1,.5F,first.up(),label+" first upper corner on top of the lower corner");
        place(world,player,corner,second,EnumFacing.UP,.5F,1,.5F,second.up(),label+" second upper corner on top of the lower corner");
        // Measure the uppers before removing the lower corners they stand on.
        out.add(new Target(label+"-first-upper",first.up(),false));out.add(new Target(label+"-second-upper",second.up(),false));
        out.add(new Target(label+"-first-lower",first,true));out.add(new Target(label+"-second-lower",second,true));
    }
    private static List<Target> build(WorldServer world,EntityPlayerMP player){
        player.setGameType(net.minecraft.world.GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();
        world.setWorldTime(6000);
        for(BlockPos at:BlockPos.getAllInBox(ORIGIN.add(-4,-1,-4),ORIGIN.add(30,5,8))){
            // A floor, and a terracotta wall two blocks behind the glass for contrast.
            boolean floor=at.getY()==ORIGIN.getY()-1,wall=at.getZ()==ORIGIN.getZ()-2;
            world.setBlockState(at,floor?Blocks.QUARTZ_BLOCK.getDefaultState():wall?Blocks.HARDENED_CLAY.getDefaultState():Blocks.AIR.getDefaultState(),2);
        }
        // Face north toward the wall while placing, as the player building it.
        player.connection.setPlayerLocation(ORIGIN.getX()+2,ORIGIN.getY(),ORIGIN.getZ()+5,180,10);player.rotationYaw=180;
        List<Target> result=new ArrayList<>();
        float[] depths={.5F,.15F,.85F};String[] labels={"middle","near","far"};
        for(int i=0;i<depths.length;i++)fixture(world,player,ORIGIN.add(i*9,0,0),depths[i],labels[i],result);
        // Upper corner placed from the upper glass edge rather than on top of a corner.
        BlockPos min=ORIGIN.add(27,0,0);Block glass=Block.getBlockFromName(GLASS),corner=Block.getBlockFromName(CORNER);
        place(world,player,glass,min.down(),EnumFacing.UP,.5F,1,.5F,min,"edge lower glass");
        place(world,player,glass,min,EnumFacing.UP,.5F,1,.5F,min.up(),"edge upper glass");
        place(world,player,corner,min,EnumFacing.EAST,1,.5F,.5F,min.east(),"edge lower corner");
        place(world,player,corner,min.up(),EnumFacing.EAST,1,.5F,.5F,min.east().up(),"edge upper corner from the upper glass");
        result.add(new Target("edge-upper",min.east().up(),false));
        player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        for(Target target:result)System.out.println("[vandorlabs] corner-stack built "+target.name+" "+world.getBlockState(target.pos)+" join="+((TileEntityProgrammableGlass)world.getTileEntity(target.pos)).isJoin()+" framed="+((TileEntityProgrammableGlass)world.getTileEntity(target.pos)).isFramed());
        return result;
    }
}
