package com.vandorlabs.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.*;

/** The client keeps rendering while the independent integrated-server tick is deliberately delayed. */
@SideOnly(Side.CLIENT)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class ArmRiderRuntimeChecks {
    private static final BlockPos ROOT=new BlockPos(1807,100,0);
    private static volatile WorldServer world;
    private static volatile boolean start,done;
    private static volatile Throwable failure;
    private static volatile int stage;
    private static int fixture=Integer.getInteger("vandorlabs.armRiderStart",0),ticks,teleport,frames,smoothed;
    private static long deadline;
    private static EntityPlayerMP rider;
    private static GearTileEntity arm;
    private static double firstYaw,maxVisualGap;private static String visualDetail;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("arm-rider fixture="+fixture+" stage="+stage+": "+why);}
    private static int teleports(){return net.minecraftforge.fml.common.ObfuscationReflectionHelper.getPrivateValue(net.minecraft.network.NetHandlerPlayServer.class,rider.connection,"field_184363_z");}
    public static boolean tick(Minecraft mc)throws Exception{
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        if(fixture>=8)return true;if(failure!=null)throw new IllegalStateException("Independent rider fixture",failure);
        if(world==null){
            if(fixture==0)packets(mc);
            mc.displayGuiScreen(null);deadline=System.nanoTime()+45000000000L;frames=smoothed=0;maxVisualGap=0;visualDetail="";stage=0;ticks=0;start=done=false;
            mc.getIntegratedServer().callFromMainThread(()->{
                WorldServer w=mc.getIntegratedServer().getWorld(0);rider=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);rider.setGameType(GameType.CREATIVE);rider.capabilities.isFlying=true;rider.sendPlayerAbilities();
                for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-5,-1,-5),ROOT.add(5,8,5)))w.setBlockState(at,at.getY()==99?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
                GearBlock block=new GearBlock[]{GearRegistry.GEAR_EXTENDER,GearRegistry.LARGE_GEAR_EXTENDER,GearRegistry.GEAR_SWING_ARM,GearRegistry.LARGE_GEAR_SWING_ARM}[fixture%4];
                for(int n=0;n<3;n++){BlockPos at=ROOT.up(n);w.setBlockState(at,block.getDefaultState(),2);GearTileEntity tile=(GearTileEntity)w.getTileEntity(at);tile.setMountDirection(EnumFacing.UP);tile.operator(rider);}
                arm=(GearTileEntity)w.getTileEntity(ROOT);require(arm.rebuild(),"supported three-section arm forms");w.setBlockState(ROOT.up(3),Blocks.STONE.getDefaultState(),2);
                rider.setGameType(GameType.SURVIVAL);rider.setHealth(20);rider.fallDistance=0;rider.motionX=rider.motionY=rider.motionZ=0;rider.capabilities.isFlying=false;rider.sendPlayerAbilities();rider.connection.setPlayerLocation(ROOT.getX()+.5,104,.5,17,0);firstYaw=17;teleport=teleports();world=w;return null;
            }).get();return false;
        }
        require(System.nanoTime()<deadline,"bounded rider motion");
        if(!start){if(mc.world.getBlockState(ROOT.up(3)).getBlock()!=Blocks.STONE)return false;if(mc.playerController.getCurrentGameType()!=GameType.SURVIVAL||mc.player.capabilities.isFlying||mc.player.getDistanceSq(ROOT.getX()+.5,104,.5)>.1)return false;mc.player.fallDistance=0;start=true;}
        if(!done)return false;
        require(maxVisualGap<.2,"rendered rider stays attached to rendered platform, max gap="+maxVisualGap+" "+visualDetail);require(frames>12,"client rendered throughout transport");require(smoothed>4,"support correction interpolates between packets");
        System.out.println("[vandorlabs] arm-rider case PASS fixture="+fixture+" frames="+frames+" smoothFrames="+smoothed+" delay="+(fixture>=4?150:0)+"ms maxVisualGap="+maxVisualGap+" zero teleport handshakes");
        world=null;fixture++;if(fixture==8){System.out.println("[vandorlabs] arm-rider PASS regular Large extender rotating standing player reversal normal delayed server independent rendering camera retained");return true;}return false;
    }
    @SubscribeEvent public static void server(TickEvent.WorldTickEvent event){
        if(world==null||event.world!=world||!start||done||failure!=null)return;
        try{
            if(event.phase==TickEvent.Phase.START){if(fixture>=4)Thread.sleep(150);return;}
            require(++ticks<240,"bounded server stroke: "+arm.error());
            if(stage==0){if(ticks<10)return;System.out.println("[vandorlabs] arm-rider starting feet="+rider.posY+" stone="+world.getBlockState(ROOT.up(3))+" progress="+arm.progress());teleport=teleports();arm.target(false);stage=1;return;}
            require(rider.getHealth()>0,"rider survives");require(teleports()==teleport,"support motion never teleports or rubber-bands the player before="+teleport+" now="+teleports()+" progress="+arm.progress()+" feet="+rider.posY+" error="+arm.error()+" support="+com.vandorlabs.motion.PlatformRiders.diagnostic(rider));require(Math.abs(rider.rotationYaw-firstYaw)<.01,"camera direction retained");
            if(arm.payload()!=null&&!ArmTransactions.locked(world,ROOT)){
                Vec3d top=arm.payload().transform(arm,arm.progress(),.5,1,.5).add(new Vec3d(ROOT));
                require(Math.abs(rider.getEntityBoundingBox().minY-top.y)<.55,"rider remains on support feet="+rider.getEntityBoundingBox().minY+" top="+top.y+" progress="+arm.progress());
            }
            if(stage==1&&arm.progress()<.5){arm.target(true);stage=2;}
            if(stage==2&&arm.progress()==1&&!ArmTransactions.locked(world,ROOT)){require(world.getBlockState(ROOT.up(3)).getBlock()==Blocks.STONE,"platform restores under rider");done=true;}
        }catch(Throwable problem){failure=problem;}
    }
    @SubscribeEvent public static void render(net.minecraftforge.client.event.RenderWorldLastEvent event){if(world==null||!start||done)return;frames++;if(com.vandorlabs.client.PlatformRiderVisuals.activeOffset()>1e-5)smoothed++;
        Minecraft mc=Minecraft.getMinecraft();net.minecraft.tileentity.TileEntity raw=mc.world.getTileEntity(ROOT);if(raw instanceof GearTileEntity){GearTileEntity client=(GearTileEntity)raw;if(client.payload()!=null&&!client.payload().materialized()){double progress=com.vandorlabs.gear.client.GearRenderer.renderedProgress(client,event.getPartialTicks());Vec3d top=client.payload().transform(client,progress,.5,1,.5).add(new Vec3d(ROOT));double feet=mc.player.lastTickPosY+(mc.player.posY-mc.player.lastTickPosY)*event.getPartialTicks();double gap=Math.abs(feet-top.y);if(gap>maxVisualGap){maxVisualGap=gap;visualDetail="authority="+client.progress()+" visual="+progress+" feet="+feet+" physical="+mc.player.getEntityBoundingBox().minY+" top="+top.y+" offset="+com.vandorlabs.client.PlatformRiderVisuals.activeOffset();}}}}
    private static void packets(Minecraft mc){
        net.minecraft.client.entity.EntityOtherPlayerMP player=new net.minecraft.client.entity.EntityOtherPlayerMP(mc.world,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"PlatformCheck"));
        player.setPosition(0,100,0);player.motionY=0;player.noClip=false;player.capabilities.isFlying=false;
        com.vandorlabs.network.MessagePlatformMotion original=new com.vandorlabs.network.MessagePlatformMotion(player,100,100.125).withHorizontal(.1,0);
        io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();original.toBytes(bytes);require(bytes.readableBytes()==60,"sequenced support packet is 60 bytes (previously 52)");
        com.vandorlabs.network.MessagePlatformMotion decoded=new com.vandorlabs.network.MessagePlatformMotion();decoded.fromBytes(bytes);bytes.release();require(decoded.apply(player)&&Math.abs(player.posY-100.125)<1e-9&&Math.abs(player.posX-.1)<1e-9,"packet round trip preserves displacement");
        require(!decoded.apply(player),"duplicate displacement is ignored");
        com.vandorlabs.network.MessagePlatformMotion newer=new com.vandorlabs.network.MessagePlatformMotion(player,100.125,100.25);require(newer.apply(player)&&!original.apply(player),"older displacement is ignored");
        for(int length:new int[]{36,52}){bytes=io.netty.buffer.Unpooled.buffer();bytes.writeInt(player.dimension).writeDouble(player.posX).writeDouble(player.posZ).writeDouble(player.posY).writeDouble(player.posY+.125);if(length==52)bytes.writeDouble(0).writeDouble(0);decoded=new com.vandorlabs.network.MessagePlatformMotion();decoded.fromBytes(bytes);bytes.release();require(decoded.apply(player),"legacy packet length "+length);}
        for(int length:new int[]{0,35,37,51,53,59,61,4096}){bytes=io.netty.buffer.Unpooled.buffer();bytes.writeZero(length);boolean refused=false;try{new com.vandorlabs.network.MessagePlatformMotion().fromBytes(bytes);}catch(IllegalArgumentException expected){refused=true;}finally{bytes.release();}require(refused,"invalid packet length "+length);}
        require(!new com.vandorlabs.network.MessagePlatformMotion(player,player.posY,Double.NaN).apply(player),"nonfinite correction refused");
        System.out.println("[vandorlabs] platform-motion-packets PASS 52->60 bytes round-trip legacy36/52 duplicate reordered invalid lengths nonfinite");
    }
    private ArmRiderRuntimeChecks(){}
}
