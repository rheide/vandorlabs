package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.UUID;

/** Real F5 input and vanilla orientCamera matrices catch a collapsed orbit arm. */
final class VehicleCameraRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(300,122,280),SEAT=BASE.add(3,1,3),CONTROL=BASE.add(2,1,3);
    private static int stage,ticks;private static UUID craftId;private static boolean viewBobbing;private static int oldView;
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("vehicle camera: "+message+" stage="+stage);}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static void f5(Minecraft mc){KeyBinding.onTick(mc.gameSettings.keyBindTogglePerspective.getKeyCode());}
    private static void look(Minecraft mc,float pitch){mc.player.prevRotationYaw=mc.player.rotationYaw=0;mc.player.prevRotationPitch=mc.player.rotationPitch=pitch;mc.player.rotationYawHead=mc.player.prevRotationYawHead=0;mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.Rotation(0,pitch,false));}
    private static Vec3d camera(Minecraft mc)throws Exception{
        int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try{GlStateManager.loadIdentity();net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(net.minecraft.client.renderer.EntityRenderer.class,"orientCamera","func_78467_g",float.class).invoke(mc.entityRenderer,1F);FloatBuffer data=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,data);Matrix4f matrix=new Matrix4f();matrix.load(data);Matrix4f inverse=Matrix4f.invert(matrix,null);require(inverse!=null,"camera matrix invertible");return new Vec3d(mc.player.posX+inverse.m30,mc.player.posY+inverse.m31,mc.player.posZ+inverse.m32);}
        finally{GlStateManager.popMatrix();GlStateManager.matrixMode(mode);}
    }
    private static void orbit(Minecraft mc,boolean front,boolean blocked)throws Exception{
        require(mc.gameSettings.thirdPersonView==(front?2:1),"real F5 selects expected rear/front view");Vec3d eye=mc.player.getPositionEyes(1),actual=camera(mc),away=Vec3d.fromPitchYaw(mc.player.rotationPitch,mc.player.rotationYaw).scale(front?1:-1);double along=actual.subtract(eye).dotProduct(away);double lateral=actual.subtract(eye.add(away.scale(along))).lengthVector();
        System.out.println("[vandorlabs][reprolab] camera-matrix stage="+stage+" front="+front+" blocked="+blocked+" eye="+eye+" camera="+actual+" arm="+along+" lateral="+lateral+" scope="+PlacedSeatCamera.diagnostic(mc));
        if(mc.player.getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat)require(PlacedSeatCamera.hasCraftScope()&&PlacedSeatCamera.supportCount()==3&&PlacedSeatCamera.owns(BASE.east(6)),"bulk chunk arrival completes all three supports and the far craft edge");
        require(lateral<.12,"camera follows the actual look axis instead of the rider head");if(blocked)require(along>1&&along<4,"foreign floating block inside hull bounds clips camera arm: "+along);else require(along>8,"third person is behind the craft instead of inside the pilot head: "+along);
    }
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(stage==12)return true;require(++ticks<300,"camera fixture timed out");
        if(stage==0){oldView=mc.gameSettings.thirdPersonView;viewBobbing=mc.gameSettings.viewBobbing;mc.gameSettings.thirdPersonView=0;mc.gameSettings.viewBobbing=false;mc.displayGuiScreen(null);mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer w=world(mc);EntityPlayerMP p=player(mc);p.dismountRidingEntity();p.setSneaking(false);p.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();
            for(BlockPos at:BlockPos.getAllInBox(BASE.add(-14,-2,-14),BASE.add(18,8,22)))w.setBlockToAir(at);
            for(BlockPos at:BlockPos.getAllInBox(BASE.add(-14,-2,-14),BASE.add(18,-2,22)))w.setBlockState(at,Blocks.STONE.getDefaultState(),2);
            for(BlockPos at:BlockPos.getAllInBox(BASE,BASE.add(6,0,8)))w.setBlockState(at,Blocks.IRON_BLOCK.getDefaultState(),2);
            Block gear=null;for(Block b:Block.REGISTRY)if(b instanceof BlockTelescopicLandingGear){gear=b;break;}require(gear!=null,"gear registered");for(BlockPos at:new BlockPos[]{BASE.down(),BASE.east(6).down(),BASE.south(8).down()}){w.setBlockState(at,gear.getDefaultState(),2);((TileEntityLandingGear)w.getTileEntity(at)).configure(0,0,0,0);}
            w.setBlockState(SEAT,ModBlocks.PILOT_SEAT.getDefaultState(),2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(w,SEAT,w.getBlockState(SEAT),p,new ItemStack(ModBlocks.PILOT_SEAT));((TileEntityPilotSeat)w.getTileEntity(SEAT)).setTaxiMode(false);
            w.setBlockState(CONTROL,Block.getBlockFromName("vandorlabs:vehicle_controller").getDefaultState(),2);w.setBlockState(BASE.add(4,1,3),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),2);
            // Both native third-person rays hit these connected cockpit windows.
            for(int z:new int[]{2,4})for(int y=1;y<=3;y++)w.setBlockState(BASE.add(3,y,z),Blocks.GLASS.getDefaultState(),2);
            p.setPositionAndUpdate(SEAT.getX()+.5,SEAT.getY(),SEAT.getZ()+.5);ModBlocks.PILOT_SEAT.onBlockActivated(w,SEAT,w.getBlockState(SEAT),p,EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,.5F);
        }).get();stage=1;ticks=0;return false;}
        if(ticks<20)return false;
        if(stage==1){if(ticks==20||ticks%40==0)System.out.println("[vandorlabs][reprolab] camera-client-arrival ticks="+ticks+" "+fixtureData(mc)+" scope="+PlacedSeatCamera.diagnostic(mc));if(!fixtureReady(mc))return false;require(mc.player.getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat,"placed seat mount arrives");look(mc,0);f5(mc);stage=2;ticks=0;return false;}
        if(stage==2){orbit(mc,false,false);shot(mc,output,"shot_camera_parked_rear.png");f5(mc);stage=3;ticks=0;return false;}
        if(stage==3){orbit(mc,true,false);shot(mc,output,"shot_camera_parked_front.png");f5(mc);stage=4;ticks=0;return false;}
        if(stage==4){require(mc.gameSettings.thirdPersonView==0,"real F5 returns to first person");require(camera(mc).distanceTo(mc.player.getPositionEyes(1))<.12,"placed first-person camera remains at the eye");RayTraceResult ray=mc.world.rayTraceBlocks(mc.player.getPositionEyes(1),mc.player.getPositionEyes(1).add(mc.player.getLook(1).scale(4)));require(ray!=null&&mc.world.getBlockState(ray.getBlockPos()).getBlock()==Blocks.GLASS,"first-person selection still hits the cockpit window");require(mc.player.getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat,"camera does not alter boarding");mc.getIntegratedServer().addScheduledTask(()->world(mc).setBlockState(BASE.add(3,3,0),Blocks.DIAMOND_BLOCK.getDefaultState(),2)).get();look(mc,20);f5(mc);stage=5;ticks=0;return false;}
        if(stage==5){orbit(mc,false,true);shot(mc,output,"shot_camera_parked_obstruction.png");mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer w=world(mc);w.setBlockToAir(BASE.add(3,3,0));EntityPlayerMP p=player(mc);p.dismountRidingEntity();VehicleScan scan=new VehicleScan(w,CONTROL);for(int n=0;n<100&&!scan.step(256);n++){}require(scan.result!=null,"camera fixture captures: "+scan.error);try{EntityGroundVehicle craft=VehicleTransfer.assemble(w,scan.result,scan.origin,p);craftId=craft.getUniqueID();require(craft.board(p),"captured pilot boards");}catch(Exception e){throw new IllegalStateException(e);}
        }).get();mc.gameSettings.thirdPersonView=0;stage=6;ticks=0;return false;}
        if(stage==6){require(mc.player.getRidingEntity() instanceof EntityGroundVehicle,"captured mount arrives");look(mc,0);f5(mc);stage=7;ticks=0;return false;}
        if(stage==7){orbit(mc,false,false);shot(mc,output,"shot_camera_captured_rear.png");f5(mc);stage=8;ticks=0;return false;}
        if(stage==8){orbit(mc,true,false);shot(mc,output,"shot_camera_captured_front.png");f5(mc);stage=9;ticks=0;return false;}
        if(stage==9){require(mc.gameSettings.thirdPersonView==0,"captured first person restored");require(camera(mc).distanceTo(mc.player.getPositionEyes(1))<.12,"captured first-person camera remains at the eye");mc.getIntegratedServer().addScheduledTask(()->world(mc).setBlockState(BASE.add(3,3,0),Blocks.DIAMOND_BLOCK.getDefaultState(),2)).get();look(mc,20);f5(mc);stage=10;ticks=0;return false;}
        if(stage==10){orbit(mc,false,true);shot(mc,output,"shot_camera_captured_obstruction.png");mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle c=(EntityGroundVehicle)world(mc).getEntityFromUuid(craftId);c.transferInProgress=true;player(mc).dismountRidingEntity();c.setDead();world(mc).setBlockToAir(BASE.add(3,3,0));}).get();stage=11;ticks=0;return false;}
        if(stage==11){mc.gameSettings.thirdPersonView=oldView;mc.gameSettings.viewBobbing=viewBobbing;stage=12;System.out.println("[vandorlabs][reprolab] vehicle-camera PASS actual-F5 orientCamera-matrix parked-captured rear-front own-cockpit-ignore foreign-interior-obstruction first-person-boarding-preserved screenshots");return true;}return false;
    }
    private static boolean fixtureReady(Minecraft mc){
        if(mc.world.getBlockState(BASE.east(6)).getBlock()!=Blocks.IRON_BLOCK)return false;
        for(BlockPos at:new BlockPos[]{BASE.down(),BASE.east(6).down(),BASE.south(8).down()})if(!(mc.world.getBlockState(at).getBlock() instanceof BlockLandingGear)||!(mc.world.getTileEntity(at) instanceof TileEntityLandingGear))return false;
        return true;
    }
    private static String fixtureData(Minecraft mc){StringBuilder out=new StringBuilder();for(BlockPos at:new BlockPos[]{BASE.east(6),BASE.down(),BASE.east(6).down(),BASE.south(8).down()})out.append(at).append("=").append(mc.world.getBlockState(at)).append(" tile=").append(mc.world.getTileEntity(at)).append(" chunkEmpty=").append(mc.world.getChunkFromBlockCoords(at).isEmpty()).append(';');return out.toString();}
    private static void shot(Minecraft mc,File output,String name)throws Exception{javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,name));}
}
