package com.vandorlabs.client;

import com.vandorlabs.gear.*;
import java.io.*;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Real server-ticked joined mechanisms, captured independently of release regressions. */
final class JoinedArmAnimationDocumentation {
    private static final String[] NAMES={"extender-platform","sideways-extender-platform","rotating-platform"};
    private static final BlockPos ROOT=new BlockPos(32,80,32);
    private static int scene=-1,settle,phase,frame;
    private static long started,nextFrame,endpoint;
    private static File folder;
    private static PrintWriter manifest;
    private static double authority;
    private static boolean recording;
    private static java.util.concurrent.Future<double[]> pendingState;
    private static BlockPos root(){return scene==1?ROOT.up(2):ROOT;}
    private static EnumFacing face(){return scene==1?EnumFacing.EAST:EnumFacing.UP;}
    private static BlockPos at(int x,int y,int z){return root().add(ArmOrientation.vector(face(),new BlockPos(x,y,z)));}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("joined-arm animation "+scene+" phase="+phase+": "+why);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(scene==NAMES.length)return true;
        if(scene<0||phase==4){
            if(++scene==NAMES.length){System.out.println("[vandorlabs] joined-arm-docs PASS six construction captures and three real joined motion cycles");return true;}
            folder=new File(output,NAMES[scene]);require(folder.mkdirs(),"new isolated frame directory");settle=phase=frame=0;endpoint=0;
            mc.getIntegratedServer().callFromMainThread(()->{setup(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));return null;}).get();
            mc.displayGuiScreen(null);mc.gameSettings.hideGUI=true;mc.gameSettings.thirdPersonView=0;return false;
        }
        if(pendingState==null){pendingState=mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);GearTileEntity member=(GearTileEntity)world.getTileEntity(root());GearTileEntity arm=member==null?null:member.loadedController();if(!recording&&(arm==null||!arm.isOperational()||arm.columns().size()!=(scene==2?3:4)))return new double[]{-1,1,0};
            require(arm!=null&&arm.isOperational(),"operational controller");
            require(arm.columns().size()==(scene==2?3:4),"all documented columns joined");
            if(recording&&phase==0&&System.nanoTime()-started>800000000L){arm.target(false);phase=1;}
            return new double[]{arm.progress(),ArmTransactions.locked(world,arm.getPos())?1:0,arm.payload()!=null&&arm.payload().materialized()?1:0};
        });return false;}
        if(!pendingState.isDone())return false;double[] state=pendingState.get();pendingState=null;authority=state[0];
        if(!recording){require(++settle<400,"bounded formation and client synchronization");if(settle<50)return false;GearTileEntity raw=(GearTileEntity)mc.world.getTileEntity(root());if(raw==null||raw.loadedController()==null||raw.loadedController().columns().size()!=(scene==2?3:4))return false;
            manifest=new PrintWriter(new File(folder,"frames.tsv"),"UTF-8");manifest.println("file\telapsed_ms\tserver_progress\tclient_progress");started=System.nanoTime();nextFrame=started;recording=true;return false;}
        require(System.nanoTime()-started<20000000000L,"bounded full motion cycle");
        GearTileEntity client=((GearTileEntity)mc.world.getTileEntity(root())).loadedController();
        double visual=com.vandorlabs.gear.client.GearRenderer.renderedProgress(client,mc.getRenderPartialTicks());
        if(phase==1&&visual==0&&authority==0&&state[1]==0&&(scene==2||state[2]==1)){
            if(endpoint==0)endpoint=System.nanoTime();if(System.nanoTime()-endpoint>700000000L){mc.getIntegratedServer().callFromMainThread(()->{((GearTileEntity)mc.getIntegratedServer().getWorld(0).getTileEntity(root())).loadedController().target(true);return null;}).get();phase=2;endpoint=0;}
        }
        if(phase==2&&visual==1&&authority==1&&state[1]==0&&state[2]==1){if(endpoint==0)endpoint=System.nanoTime();if(System.nanoTime()-endpoint>700000000L){recording=false;manifest.close();require(frame>=40,"enough real frames");System.out.println("[vandorlabs] joined-arm-animation PASS "+NAMES[scene]+" frames="+frame+" full joined retraction reversal native endpoints");phase=4;}}
        return false;
    }
    private static void setup(WorldServer world,EntityPlayerMP player){
        for(BlockPos p:BlockPos.getAllInBox(ROOT.add(-11,-1,-11),ROOT.add(15,10,14))){world.removeTileEntity(p);world.setBlockState(p,p.getY()==79?Blocks.STONEBRICK.getDefaultState():Blocks.AIR.getDefaultState(),2);}
        if(scene==1)for(BlockPos p:BlockPos.getAllInBox(ROOT.add(-1,0,-1),ROOT.add(-1,7,5)))world.setBlockState(p,Blocks.STONEBRICK.getDefaultState(),2);
        GearBlock block=scene==2?GearRegistry.LARGE_GEAR_SWING_ARM:GearRegistry.GEAR_EXTENDER;
        int[][] anchors=scene==2?new int[][]{{0,0},{3,0},{6,0}}:new int[][]{{0,0},{2,0},{0,3},{2,3}};
        player.setGameType(GameType.CREATIVE);player.capabilities.allowFlying=true;player.capabilities.isFlying=true;player.sendPlayerAbilities();
        for(int[] c:anchors){world.setBlockState(at(c[0],1,c[1]),Blocks.STONEBRICK.getDefaultState(),2);player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block,2));
            for(int n=0;n<2;n++)require(player.getHeldItemMainhand().onItemUse(player,world,at(c[0],1-n,c[1]),EnumHand.MAIN_HAND,face(),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"normal supported placement");
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(at(c[0],0,c[1]));require(arm.rebuild(),"column formation");NBTTagCompound settings=arm.settings();settings.setBoolean("JoinArms",true);settings.setInteger("JoinRange",3);settings.setInteger("Direction",0);settings.setBoolean("RotatePayload",false);settings.setBoolean("CarryLoad",scene==0);settings.setInteger("LoadHeight",2);arm.configure(settings);
        }
        for(int x=0;x<=(scene==2?6:2);x++)for(int z=0;z<=(scene==2?0:3);z++)if(scene==2||x==0||x==2||z==0||z==3)world.setBlockState(at(x,-2,z),Blocks.CONCRETE.getDefaultState().withProperty(net.minecraft.block.BlockColored.COLOR,net.minecraft.item.EnumDyeColor.LIGHT_BLUE),2);
        if(scene==0)world.setBlockState(at(1,-2,0).up(),Blocks.CHEST.getDefaultState(),2);
        Vec3d target=new Vec3d(root()).addVector(scene==2?3.5:1.5,1.4,scene==2?1:1.8);
        Vec3d eye=target.addVector(scene==1?8:-7,4.5,-9);Vec3d d=target.subtract(eye);
        player.connection.setPlayerLocation(eye.x,eye.y-player.getEyeHeight(),eye.z,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player.motionX=player.motionY=player.motionZ=0;
    }
    static void render(Minecraft mc){
        if(!recording||System.nanoTime()<nextFrame)return;
        try{long now=System.nanoTime();String name=String.format(Locale.ROOT,"frame-%04d.png",frame++);javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(folder,name));
            GearTileEntity arm=((GearTileEntity)mc.world.getTileEntity(root())).loadedController();manifest.printf(Locale.ROOT,"%s\t%.3f\t%.6f\t%.6f%n",name,(now-started)/1000000D,authority,com.vandorlabs.gear.client.GearRenderer.renderedProgress(arm,mc.getRenderPartialTicks()));manifest.flush();nextFrame=now+50000000L;
        }catch(IOException failure){throw new IllegalStateException(failure);}
    }
}
