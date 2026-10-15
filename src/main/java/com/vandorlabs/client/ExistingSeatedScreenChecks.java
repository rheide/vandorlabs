package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.entity.EntityChairSeat;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.redstone.RedstoneChannels;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.io.File;
import java.util.*;
import java.util.concurrent.Future;

/** Opt-in acceptance of the user's actual block-mode cockpit in a disposable saved-world copy. */
final class ExistingSeatedScreenChecks {
    private static final BlockPos SEAT=new BlockPos(-133,85,158),SCREEN=new BlockPos(-134,85,159);
    private static final int[] ROWS={0,0,2,2};
    private static int serverClicks;
    private static final class ClickProbe {
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
        public void clicked(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event){
            if(!SCREEN.equals(event.getPos()))return;
            net.minecraft.entity.player.EntityPlayer player=event.getEntityPlayer();Vec3d eye=player.getPositionEyes(1).subtract(new Vec3d(SCREEN));
            TileEntityAnimatedScreenSelector tile=screen(event.getWorld());int row=RedstoneScreenInteractions.hitRow(event.getWorld().getBlockState(SCREEN),tile,0,eye,eye.add(player.getLook(1).scale(5)),4);
            int packetRow=RedstoneScreenInteractions.hitRow(event.getWorld().getBlockState(SCREEN),tile,0,eye,eye.add(Vec3d.fromPitchYaw(player.rotationPitch,player.rotationYaw).scale(5)),4);
            if(!event.getWorld().isRemote)serverClicks++;
            System.out.println("[vandorlabs][reprolab] existing-seated-screen CLICK side="+(event.getWorld().isRemote?"client":"server")+" row="+row+" packetRow="+packetRow+" headYaw="+player.rotationYawHead+" eye="+player.getPositionEyes(1)+" yaw="+player.rotationYaw+" pitch="+player.rotationPitch+" sneak="+player.isSneaking()+" hand="+event.getHand()+" held="+event.getItemStack()+" canceled="+event.isCanceled()+" useBlock="+event.getUseBlock()+" carrier="+player.getRidingEntity());
        }
    }
    private static int stage,ticks,click;private static int originalToggle,originalSlider;private static Future<?> pending;
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("existing seated screen: "+text);}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static TileEntityAnimatedScreenSelector screen(net.minecraft.world.World world){return (TileEntityAnimatedScreenSelector)world.getTileEntity(SCREEN);}
    private static Vec3d target(net.minecraft.world.World world,int row,int segment){
        IBlockState state=world.getBlockState(SCREEN);TileEntityAnimatedScreenSelector tile=screen(world);ScreenSurface.Quad q=RedstoneScreenInteractions.surface(state,tile,0);
        double u=(row==2?RedstoneScreenContents.SLIDER_LEFT+(segment+.5)*(RedstoneScreenContents.SLIDER_RIGHT-RedstoneScreenContents.SLIDER_LEFT)/4D:109)/128D;
        double v=(RedstoneScreenInteractions.rowTop(state.getBlock(),0)+row*12+5D)/RedstoneScreenInteractions.displayHeight(state.getBlock(),0);
        Vec3d local=new Vec3d((q.topRight.x+(q.topLeft.x-q.topRight.x)*u)/16,(q.topRight.y+(q.bottomRight.y-q.topRight.y)*v)/16,(q.topRight.z+(q.bottomRight.z-q.topRight.z)*v)/16);
        return DiagonalScreenShape.world(local.x,local.y,local.z,RedstoneScreenInteractions.facing(state)).add(new Vec3d(SCREEN));
    }
    private static RayTraceResult trace(net.minecraft.world.World world,Vec3d eye,Vec3d target){return world.rayTraceBlocks(eye,eye.add(target.subtract(eye).normalize().scale(5)),false,true,false);}
    private static String description(RayTraceResult hit){return hit==null?"none":hit.getBlockPos()+" hit="+hit.hitVec;}
    private static void setup(Minecraft mc){
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ClickProbe());
        WorldServer w=world(mc);w.getChunkFromBlockCoords(SEAT);w.getChunkFromBlockCoords(SCREEN);
        require(w.getBlockState(SEAT).getBlock() instanceof BlockPilotSeat,"saved Pilot Seat remains at "+SEAT);
        require(w.getBlockState(SCREEN).getBlock() instanceof BlockProgrammableDiagonalScreen&&screen(w).hasRedstoneScreen(0),"saved diagonal Redstone screen remains at "+SCREEN);
        require(!((TileEntityPilotSeat)w.getTileEntity(SEAT)).isTaxiMode(),"seat stays in Block mode");
        RedstoneScreenContents rows=screen(w).redstoneScreen(0);require(rows.rows().size()==4&&rows.rows().get(2).slider,"saved Canopy/Gear/Thruster/Wings rows");
        originalToggle=RedstoneChannels.level(w,rows.rows().get(0).channels.first());originalSlider=rows.rows().get(2).selectedSegment();
        EntityPlayerMP player=actor(mc);player.dismountRidingEntity();player.setSneaking(false);player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player.capabilities.isFlying=false;player.sendPlayerAbilities();
        player.setPositionAndUpdate(SEAT.getX()+.5,SEAT.getY(),SEAT.getZ()+.5);
        w.getBlockState(SEAT).getBlock().onBlockActivated(w,SEAT,w.getBlockState(SEAT),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);
        require(player.getRidingEntity() instanceof EntityChairSeat,"actual saved Pilot Seat boards");player.getRidingEntity().updatePassenger(player);player.connection.setPlayerLocation(player.posX,player.posY,player.posZ,0,0);
        Vec3d eye=player.getPositionEyes(1),end=target(w,0,0);IBlockState upper=w.getBlockState(SEAT.up());
        require(upper.getBlock() instanceof BlockPilotSeat,"actual backrest reservation present");
        require(upper.getBoundingBox(w,SEAT.up()).offset(SEAT.up()).calculateIntercept(eye,end)!=null,"regression ray intersects the old broad own-seat pick proxy");
        for(int row=0;row<4;row++){RayTraceResult hit=trace(w,eye,target(w,row,row==2?2:0));require(hit!=null&&SCREEN.equals(hit.getBlockPos()),"actual seated server ray reaches row "+row+" instead of "+description(hit));}
        BlockPos front=SEAT.north().up();RayTraceResult wall=trace(w,eye,new Vec3d(front).addVector(.5,.5,.5));require(wall!=null&&front.equals(wall.getBlockPos()),"real solid cockpit wall still occludes");
        RayTraceResult other=ModBlocks.PILOT_SEAT.collisionRayTrace(upper,w,front,eye,new Vec3d(front).addVector(.5,.5,.5));require(other!=null,"another seat's pick proxy remains selectable");
        List<AxisAlignedBB> collision=new ArrayList<>();upper.addCollisionBoxToList(w,SEAT.up(),new AxisAlignedBB(SEAT.up()),collision,player,false);require(!collision.isEmpty(),"own-seat physical collision unchanged");
        System.out.println("[vandorlabs][reprolab] existing-seated-screen-world PASS exact-saved-seat diagonal-screen four-row-rays real-wall foreign-seat physical-collision");
    }
    static boolean tick(Minecraft mc,int elapsed,File output)throws Exception{
        require(++ticks<700,"timeout stage="+stage+" click="+click+" mount="+mc.player.getRidingEntity());
        if(pending!=null){if(!pending.isDone())return false;pending.get();pending=null;}
        if(stage==0){if(elapsed<40)return false;mc.gameSettings.thirdPersonView=0;mc.displayGuiScreen(null);pending=mc.getIntegratedServer().addScheduledTask(()->setup(mc));stage=1;ticks=0;return false;}
        if(stage==4)return true;
        if(!(mc.player.getRidingEntity() instanceof EntityChairSeat)||mc.world.getBlockState(SCREEN).getBlock()!=ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN)return false;
        if(stage==1&&ticks>15){
            int row=ROWS[click],segment=click==2?2:originalSlider;Vec3d eye=mc.player.getPositionEyes(1),point=target(mc.world,row,segment),look=point.subtract(eye).normalize();
            float yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z)),pitch=(float)-Math.toDegrees(Math.asin(MathHelper.clamp(look.y,-1,1)));
            mc.player.rotationYaw=mc.player.prevRotationYaw=yaw;mc.player.rotationPitch=mc.player.prevRotationPitch=pitch;mc.player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
            mc.entityRenderer.getMouseOver(1);OffsetTrapdoorSelection.updateTarget(1);RayTraceResult hit=mc.objectMouseOver;
            require(hit!=null&&hit.typeOfHit==RayTraceResult.Type.BLOCK&&SCREEN.equals(hit.getBlockPos()),"actual seated crosshair selects row "+row+" instead of "+description(hit));
            System.out.println("[vandorlabs][reprolab] existing-seated-screen BEFORE-CLICK row="+row+" target="+point+" actual="+description(hit)+" eye="+eye+" yaw="+yaw+" pitch="+pitch+" handActive="+mc.player.isHandActive()+" controller="+mc.playerController.getCurrentGameType()+" screen="+mc.currentScreen);
            mc.player.connection.sendPacket(new CPacketPlayer.Rotation(yaw,pitch,mc.player.onGround));net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(Minecraft.class,"rightClickMouse","func_147121_ag").invoke(mc);
            stage=2;ticks=0;return false;
        }
        if(stage==2&&ticks>12){final int operation=click;pending=mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer w=world(mc);EntityPlayerMP player=actor(mc);require(player.getRidingEntity() instanceof EntityChairSeat&&SEAT.equals(((EntityChairSeat)player.getRidingEntity()).getChairPos()),"actual screen click keeps actor in original Block-mode seat");
            RedstoneScreenContents contents=screen(w).redstoneScreen(0);int expected=operation==0?(originalToggle>0?0:15):operation==1?originalToggle:contents.rows().get(2).segmentValue(operation==2?2:originalSlider);
            int channel=contents.rows().get(ROWS[operation]).channels.first();Vec3d eye=player.getPositionEyes(1).subtract(new Vec3d(SCREEN));int actualRow=RedstoneScreenInteractions.hitRow(w.getBlockState(SCREEN),screen(w),0,eye,eye.add(player.getLook(1).scale(5)),4);System.out.println("[vandorlabs][reprolab] existing-seated-screen AFTER-CLICK calls="+serverClicks+" serverRow="+actualRow+" eye="+player.getPositionEyes(1)+" yaw="+player.rotationYaw+" pitch="+player.rotationPitch+" bank="+RedstoneChannels.level(w,channel));require(RedstoneChannels.level(w,channel)==expected,"actual right-click packet changes row "+ROWS[operation]+" channel "+channel+" expected="+expected+" actual="+RedstoneChannels.level(w,channel));
        });stage=3;ticks=0;return false;}
        if(stage==3&&ticks>5){javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_existing_seated_screen_"+click+".png"));if(++click<ROWS.length){stage=1;ticks=0;}else{stage=4;System.out.println("[vandorlabs][reprolab] existing-seated-screen PASS copied-world actual-Pilot-Seat own-backrest-selection real-crosshair right-click-packets Toggle-Slider four-rows restored-controls stays-seated screenshots");return true;}}
        return false;
    }
}
