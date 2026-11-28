package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.blocks.BlockPilotSeat;
import com.vandorlabs.cockpit.CockpitAssembly;
import com.vandorlabs.entity.EntityChairSeat;
import com.vandorlabs.screen.VehicleInstrument;
import com.vandorlabs.tiles.TileEntityCanopy;
import com.vandorlabs.tiles.TileEntityCockpit;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.Future;

/**
 * A reported cockpit: a 3-wide Long Canopy sloping over all three rows (angle 3) with the small HUD, and a
 * side-rest Pilot Seat at height 3 under its rear row. The pilot sits through the real seat path and looks
 * ahead; the HUD must sit near the middle of the glass they see and never flash solid white.
 */
final class CanopyPilotHudChecks {
    private static final BlockPos CANOPY=new BlockPos(168,107,260),SEAT=CANOPY.add(0,-1,2),CONTROL=CANOPY.add(1,-1,0);
    private static final int CARRIED_SAMPLES=200;
    private static net.minecraft.util.math.Vec3d eye;
    private static final int FLASH_SAMPLES=120;
    private static int stage,ticks,samples,worstFrame=-1;
    private static double worstWhite;
    private static Future<?> pending;

    private CanopyPilotHudChecks(){}

    static boolean tick(Minecraft mc,File output)throws Exception{
        if(mc.player==null||mc.world==null||mc.getIntegratedServer()==null)return false;
        require(++ticks<600,"timeout riding="+mc.player.getRidingEntity()+" eye="+mc.player.getPositionEyes(1));
        if(pending!=null){if(!pending.isDone())return false;pending.get();pending=null;}
        if(stage==0){
            mc.gameSettings.hideGUI=true;mc.gameSettings.fovSetting=70;mc.gameSettings.pauseOnLostFocus=false;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            pending=mc.getIntegratedServer().addScheduledTask(()->build(mc));
            next(1);return false;
        }
        if(stage==1){
            // Seat the pilot through the ordinary empty-handed activation, looking up so no control is pressed.
            if(ticks<20)return false;
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=player(mc);p.rotationPitch=-85;p.rotationYaw=EnumFacing.NORTH.getHorizontalAngle();
                p.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);p.setSneaking(false);
                require(CockpitAssembly.activate(p.world,SEAT,p,EnumHand.MAIN_HAND),"seat activation handled");
            });
            next(2);return false;
        }
        if(stage==2){
            if(!(mc.player.getRidingEntity() instanceof EntityChairSeat))return false;
            mc.player.rotationYaw=mc.player.prevRotationYaw=EnumFacing.NORTH.getHorizontalAngle();
            mc.player.rotationPitch=mc.player.prevRotationPitch=0;
            TileEntityCanopy tile=TileEntityCanopy.at(mc.world,CANOPY);
            if(tile!=null&&tile.owner().width()!=3)return false;
            if(ticks<30||tile==null||!CanopyDisplayRenderer.captured(tile.owner(),VehicleInstrument.HUD,false))return false;
            if(samples<FLASH_SAMPLES){whiteShare(mc);samples++;return false;}
            ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"canopy_pilot_hud-seated.png"));
            double[] hud=hudCentroid(mc);
            System.out.println(String.format(Locale.ROOT,"[vandorlabs] canopy-pilot-hud measured eye=%s HUD pixels=%d centroid=%.3f,%.3f worst white=%.4f at frame %d of %d",
                    mc.player.getPositionEyes(1),(int)hud[2],hud[0],hud[1],worstWhite,worstFrame,samples));
            require(worstWhite<.08,"HUD glass never flashes solid white: worst share="+worstWhite+" at frame "+worstFrame);
            require(hud[2]>150,"HUD symbology visible from the seat: "+(int)hud[2]+" pixels");
            require(Math.abs(hud[0]-.5)<.12,"HUD centred across the view: x="+hud[0]);
            // Screen y grows downward. Before the fix the whole HUD sat in the band at the nose (y=0.62-0.66,
            // centroid skewed by side panes). It must sit on the forward sightline, without readouts magnified overhead.
            require(hud[1]>.44&&hud[1]<.60,"HUD sits on the pilot's forward sightline, not low at the nose: y="+hud[1]);
            require(hud[3]<.03,"no HUD readout is magnified across the glass just above the pilot's head: share above y=0.35 is "+hud[3]);
            System.out.println("[vandorlabs] canopy-pilot-hud parked PASS seated Long Canopy small HUD at y="+String.format(Locale.ROOT,"%.3f",hud[1])+" with no white flash over "+samples+" frames");
            eye=mc.player.getPositionEyes(1).subtract(new net.minecraft.util.math.Vec3d(tile.owner().getPos()));samples=0;worstWhite=0;worstFrame=-1;
            // Carry the cockpit in Taxi so the canopy renders through the vehicle renderer beside the cockpit tiles.
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=player(mc);p.dismountRidingEntity();
                com.vandorlabs.vehicle.VehicleService.screenMode(p,CONTROL,null,1);
            });
            next(3);return false;
        }
        if(stage==3){
            com.vandorlabs.vehicle.EntityGroundVehicle craft=craft(mc);
            if(ticks<40||craft==null||craft.structure==null||!craft.stopped())return false;
            // Board the carried Pilot Seat as a pilot would, so the eye follows the craft's own seat.
            pending=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=player(mc);
                com.vandorlabs.vehicle.EntityGroundVehicle actual=null;
                for(net.minecraft.entity.Entity e:p.world.loadedEntityList)
                    if(e instanceof com.vandorlabs.vehicle.EntityGroundVehicle&&!e.isDead&&e.getDistanceSq(CANOPY)<900)actual=(com.vandorlabs.vehicle.EntityGroundVehicle)e;
                require(actual!=null&&actual.board(p),"board the carried Pilot Seat");
            });
            next(4);return false;
        }
        if(stage==4){
            if(ticks<40||mc.player.getRidingEntity()!=craft(mc)){require(ticks<200,"pilot rides the carried craft: "+mc.player.getRidingEntity());return false;}
            if(samples==0)System.out.println("[vandorlabs] canopy-pilot-hud carried seated eye="+mc.player.getPositionEyes(1));
            if(samples<CARRIED_SAMPLES){
                // Small head movements while the instruments refresh, as in flight.
                mc.player.rotationYaw=mc.player.prevRotationYaw=EnumFacing.NORTH.getHorizontalAngle()+(float)(12*Math.sin(samples/9.0));
                mc.player.rotationPitch=mc.player.prevRotationPitch=(float)(6*Math.sin(samples/13.0));
                whiteShare(mc);samples++;return false;
            }
            mc.player.rotationYaw=mc.player.prevRotationYaw=EnumFacing.NORTH.getHorizontalAngle();mc.player.rotationPitch=mc.player.prevRotationPitch=0;
            if(ticks<CARRIED_SAMPLES+50)return false;
            ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"canopy_pilot_hud-carried.png"));
            double[] hud=hudCentroid(mc);
            System.out.println(String.format(Locale.ROOT,"[vandorlabs] canopy-pilot-hud carried HUD pixels=%d centroid=%.3f,%.3f worst white=%.4f at frame %d of %d",
                    (int)hud[2],hud[0],hud[1],worstWhite,worstFrame,samples));
            require(worstWhite<.08,"carried HUD glass never flashes solid white: worst share="+worstWhite+" at frame "+worstFrame);
            require(hud[2]>150&&hud[1]>.44&&hud[1]<.60&&hud[3]<.03,"carried HUD keeps the seated placement: pixels="+(int)hud[2]+" y="+hud[1]+" high="+hud[3]);
            System.out.println("[vandorlabs] canopy-pilot-hud PASS parked and carried Long Canopy small HUD on the pilot's sightline, no white flash");
            return true;
        }
        return false;
    }

    private static void build(Minecraft mc){
        EntityPlayerMP p=player(mc);WorldServer world=p.getServerWorld();
        world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");world.setWorldTime(6000);world.getWorldInfo().setRaining(false);
        for(BlockPos at:BlockPos.getAllInBox(CANOPY.add(-6,-4,-8),CANOPY.add(6,6,8))){world.getChunkFromBlockCoords(at);world.removeTileEntity(at);world.setBlockToAir(at);}
        // Ground below the wheels; a 3 x 3 deck carries the seat, the Vehicle Controller and three wheels.
        for(BlockPos at:BlockPos.getAllInBox(CANOPY.add(-6,-4,-8),CANOPY.add(6,-4,8)))world.setBlockState(at,Blocks.STONE.getDefaultState(),2);
        for(BlockPos at:BlockPos.getAllInBox(CANOPY.add(-1,-2,0),CANOPY.add(1,-2,2)))world.setBlockState(at,Blocks.STONE.getDefaultState(),2);
        Block wheel=null;for(Block candidate:Block.REGISTRY)if(candidate instanceof com.vandorlabs.blocks.BlockWheel){wheel=candidate;break;}
        require(wheel!=null,"wheel available");
        for(BlockPos at:new BlockPos[]{CANOPY.add(-1,-3,0),CANOPY.add(1,-3,0),CANOPY.add(1,-3,2)})world.setBlockState(at,wheel.getDefaultState(),2);
        p.setGameType(GameType.CREATIVE);p.capabilities.isFlying=true;p.sendPlayerAbilities();
        p.connection.setPlayerLocation(SEAT.getX()+.5,SEAT.getY(),SEAT.getZ()+2.5,EnumFacing.NORTH.getHorizontalAngle(),0);
        // Pilot Seat first, facing the nose, at the reported height 3.
        Block seat=Block.getBlockFromName("vandorlabs:chair_siderests");
        world.setBlockState(SEAT,seat.getDefaultState().withProperty(BlockPilotSeat.FACING,EnumFacing.NORTH),2);
        CockpitAssembly.install(world,SEAT,p);
        TileEntityCockpit cockpit=(TileEntityCockpit)world.getTileEntity(SEAT);
        cockpit.setTaxiMode(false);cockpit.setHeight(3);
        world.setBlockState(CONTROL,com.vandorlabs.blocks.ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);
        com.vandorlabs.blocks.ModBlocks.VEHICLE_CONTROLLER.onBlockPlacedBy(world,CONTROL,world.getBlockState(CONTROL),p,new ItemStack(com.vandorlabs.blocks.ModBlocks.VEHICLE_CONTROLLER));
        // Three joined roots give the reported 3-wide canopy; the seat sits under its middle column.
        BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:long_canopy");
        for(BlockPos root:new BlockPos[]{CANOPY.west(),CANOPY,CANOPY.east()}){
            require(world.setBlockState(root,block.getDefaultState().withProperty(BlockCanopy.FACING,EnumFacing.NORTH),3),"place Long Canopy root "+root);
            block.onBlockPlacedBy(world,root,world.getBlockState(root),p,new ItemStack(block));
        }
        TileEntityCanopy canopy=TileEntityCanopy.at(world,CANOPY).owner();
        require(canopy.facing()==EnumFacing.NORTH&&canopy.width()==3,"3-wide canopy nose faces north, seat under its rear row: width="+canopy.width());
        NBTTagCompound settings=canopy.writeToNBT(new NBTTagCompound());
        settings.setInteger("CanopyAngle",3);settings.setInteger("CanopyMaterial",VehicleInstrument.HUD.choice);
        settings.setInteger("CanopyShade",12);settings.setInteger("CanopyHinge",2);
        settings.setInteger("CanopySideMaterial",-1);settings.setInteger("CanopySideShade",12);settings.setInteger("CanopySideTint",0);settings.setInteger("CanopySideTextureLayout",0);
        canopy.readFromNBT(settings);canopy.markDirty();
        world.notifyBlockUpdate(canopy.getPos(),world.getBlockState(canopy.getPos()),world.getBlockState(canopy.getPos()),3);
        require(canopy.large.angle==3&&canopy.large.material==VehicleInstrument.HUD.choice&&canopy.large.material(true)==-1,"reported canopy settings applied");
    }

    /** Centroid of HUD green in screen fractions (x, y from the top), the pixel count and the share in the top 35%. */
    private static double[] hudCentroid(Minecraft mc){
        int w=mc.displayWidth,h=mc.displayHeight;ByteBuffer pixels=read(mc,0,0,w,h);
        double sx=0,sy=0;int n=0,high=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=(y*w+x)*4,r=pixels.get(i)&255,g=pixels.get(i+1)&255,b=pixels.get(i+2)&255;
            if(g>=200&&g-r>=50&&g-b>=30&&r>=80&&b>=120){int top=h-1-y;sx+=x;sy+=top;n++;if(top<h*.35)high++;}
        }
        return n==0?new double[]{-1,-1,0,0}:new double[]{sx/n/w,sy/n/h,n,high/(double)n};
    }
    /** Share of near-white pixels in the central half of the last rendered frame. */
    private static void whiteShare(Minecraft mc){
        int w=mc.displayWidth/2,h=mc.displayHeight/2;ByteBuffer pixels=read(mc,mc.displayWidth/4,mc.displayHeight/4,w,h);
        int white=0;
        for(int i=0;i<w*h;i++){int r=pixels.get(i*4)&255,g=pixels.get(i*4+1)&255,b=pixels.get(i*4+2)&255;if(r>=248&&g>=248&&b>=248)white++;}
        double share=white/(double)(w*h);
        if(share>worstWhite){worstWhite=share;worstFrame=samples;}
    }
    private static ByteBuffer read(Minecraft mc,int x,int y,int w,int h){
        Framebuffer main=mc.getFramebuffer();ByteBuffer pixels=BufferUtils.createByteBuffer(w*h*4);
        int previous=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        if(net.minecraft.client.renderer.OpenGlHelper.isFramebufferEnabled())main.bindFramebuffer(false);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);
        GL11.glReadPixels(x,y,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        net.minecraft.client.renderer.OpenGlHelper.glBindFramebuffer(net.minecraft.client.renderer.OpenGlHelper.GL_FRAMEBUFFER,previous);
        return pixels;
    }
    private static com.vandorlabs.vehicle.EntityGroundVehicle craft(Minecraft mc){
        for(net.minecraft.entity.Entity e:mc.world.loadedEntityList)
            if(e instanceof com.vandorlabs.vehicle.EntityGroundVehicle&&!e.isDead&&e.getDistanceSq(CANOPY)<900)return (com.vandorlabs.vehicle.EntityGroundVehicle)e;
        return null;
    }
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static void next(int value){stage=value;ticks=0;}
    private static void require(boolean ok,String message){
        if(!ok)throw new IllegalStateException("canopy-pilot-hud stage="+stage+" ticks="+ticks+": "+message);
    }
}
