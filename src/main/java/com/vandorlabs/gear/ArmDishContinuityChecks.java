package com.vandorlabs.gear;

import com.vandorlabs.client.constructors.ArmDishContinuityObserver;
import com.vandorlabs.gear.client.ArmCoreTransitionObserver;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.VehicleStructure;
import java.io.File;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** One active Dish and one Extender, observing actual first capture and materialization. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class ArmDishContinuityChecks {
    private static final BlockPos ROOT=ArmCoreFixture.ROOT;
    private static final java.lang.reflect.Field TELEPORT=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.network.NetHandlerPlayServer.class,"targetPos","field_184362_y");
    private static int stage,ticks,checks,firstEndpointDraws;private static boolean clicked;private static Vec3d feet;
    private static String state="starting";private static ArmDishContinuityObserver observer;
    private static void need(boolean value,String why){checks++;if(!value)throw new IllegalStateException("Dish continuity stage="+stage+" tick="+ticks+" "+state+": "+why);}
    private static GearTileEntity arm(World world){TileEntity raw=world.getTileEntity(ROOT);return raw instanceof GearTileEntity?(GearTileEntity)raw:null;}
    private static boolean pending(EntityPlayerMP actor){try{return TELEPORT.get(actor.connection)!=null;}catch(IllegalAccessException error){throw new IllegalStateException(error);}}
    public static boolean tick(Minecraft mc,File output)throws Exception{
        mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.viewBobbing=false;mc.gameSettings.fovSetting=100;mc.displayGuiScreen(null);
        need(++ticks<500,"bounded ordinary player/async journal path clientMode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" position="+mc.player.getPositionVector());
        if(stage==0){mc.getIntegratedServer().callFromMainThread(()->{setup(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));return null;}).get();stage=1;ticks=0;return false;}
        if(stage==1){
            GearTileEntity client=arm(mc.world);if(client==null||client.mountDirection()!=EnumFacing.UP||!client.isOperational()||client.assembly().columnHeight()!=2)return false;
            boolean synchronizedState=mc.getIntegratedServer().callFromMainThread(()->{EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);return !pending(p)&&p.interactionManager.getGameType()==GameType.CREATIVE&&p.inventory.currentItem==0&&p.getHeldItemMainhand().isEmpty();}).get();
            if(!synchronizedState||mc.playerController.getCurrentGameType()!=GameType.CREATIVE||mc.player.inventory.currentItem!=0||!mc.player.getHeldItemMainhand().isEmpty()||!mc.player.capabilities.isFlying||mc.player.getDistanceSq(feet.x,feet.y,feet.z)>.02)return false;
            com.vandorlabs.client.constructors.MachineMotionChecks.handoff(mc);Set<BlockPos> cells=new HashSet<>();for(BlockPos p:BlockPos.getAllInBox(ROOT.add(-4,-4,-4),ROOT.add(4,7,4)))cells.add(p.toImmutable());observer=new ArmDishContinuityObserver(output);ArmCoreTransitionObserver.installMachine(output,ROOT,cells,observer);ArmCoreTransitionObserver.start();stage=2;ticks=0;return false;
        }
        if(stage==2){if(!observer.warm())return false;RayTraceResult hit=GearInteractions.trace(mc.world,mc.player.getPositionEyes(1),mc.player.getPositionEyes(1).add(mc.player.getLook(1).scale(5.5)));need(hit!=null&&hit.getBlockPos().equals(ROOT),"ordinary mounting-base ray synchronized before activation");mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock(ROOT,hit.sideHit,EnumHand.MAIN_HAND,.5F,.5F,.5F));clicked=true;stage=3;ticks=0;return false;}
        boolean complete=mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);GearTileEntity root=arm(world);need(root!=null,"physical Extender survives");state="phase="+root.progress()+" target="+root.target()+" payload="+(root.payload()==null?"none":root.payload().materialized()?"native":"captured")+" lock="+ArmTransactions.locked(world,ROOT)+" error="+root.error();
            if(root.payload()!=null){NavigationArmChecks.verify(root);need(root.payload().blocks.cells.size()==64,"whole scaled Dish64 single snapshot");ArmCorePayload.validate(root.payload().blocks);int owner=0;for(VehicleStructure.Cell cell:root.payload().blocks.cells)if(cell.state.getBlock() instanceof com.vandorlabs.blocks.BlockConstructorCore)owner++;need(owner==1,"one captured chip/machine owner");}
            double endpoint=stage==4?1:0;if(!clicked||root.progress()!=endpoint||root.target()!=(stage==4)||root.payload()==null||!root.payload().materialized()||ArmTransactions.locked(world,ROOT))return false;
            int owners=0;for(BlockPos at:root.payload().placedCells(root))if(world.getTileEntity(at) instanceof TileEntityConstructor){TileEntityConstructor core=(TileEntityConstructor)world.getTileEntity(at);owners++;need(core.formed()&&core.activity()==2&&core.getStackInSlot(0).getItem()==com.vandorlabs.items.ModItems.DATA_CHIP,"active native Dish and original chip restored");}need(owners==1,"one materialized native owner");need(world.getBlockState(ROOT.down()).getBlock()==com.vandorlabs.ports.ResourcePorts.POWER,"mount support (the feeding Power Port) retained");return true;
        }).get();
        if(complete&&observer.restored()>=firstEndpointDraws+3){
            if(observer.failure()!=null){ArmCoreTransitionObserver.finish();observer.close();need(false,observer.failure());}
            if(stage==3){
                // Exact saved root round trip and repeated normal updates precede the actual reversal packet.
                mc.getIntegratedServer().callFromMainThread(()->{WorldServer world=mc.getIntegratedServer().getWorld(0);GearTileEntity root=arm(world);net.minecraft.nbt.NBTTagCompound saved=root.writeToNBT(new net.minecraft.nbt.NBTTagCompound());root.readFromNBT(saved);root.operator(mc.getIntegratedServer().getPlayerList().getPlayers().get(0));for(int i=0;i<2;i++)world.notifyBlockUpdate(ROOT,world.getBlockState(ROOT),world.getBlockState(ROOT),2);return null;}).get();
                GearTileEntity client=arm(mc.world);if(client==null||client.progress()!=0||client.payload()==null||!client.payload().materialized())return false;
                mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock(ROOT,EnumFacing.NORTH,EnumHand.MAIN_HAND,.5F,.5F,.5F));firstEndpointDraws=observer.restored();stage=4;ticks=0;return false;
            }
            ArmCoreTransitionObserver.finish();observer.close();need(observer.captured()>0&&observer.restored()>0,"positive actual rotor submissions through capture/reversal/both native endpoints");System.out.println("[vandorlabs][dish-continuity] PASS checks="+checks+" actualRotor capture/nativeEndpoints reversal/rootReload/repeatedUpdates singleChip realJournal");return true;
        }
        return false;
    }
    private static void setup(WorldServer world,EntityPlayerMP actor){
        ArmCoreFixture.setup(world,actor,0,"standalone_dish_core",true,4);
        // Dishes rotate only while RF arrives through the centre Power Port, which sits on the
        // arm tip: carry RF through the arm from a filled Power Port under its root.
        GearTileEntity powered=(GearTileEntity)world.getTileEntity(ROOT);powered.operator(actor);
        net.minecraft.nbt.NBTTagCompound resource=powered.settings();resource.setInteger(com.vandorlabs.gear.core.ArmResource.CHOICE,com.vandorlabs.gear.core.ArmResource.POWER.ordinal());powered.configure(resource);
        need(world.setBlockState(ROOT.down(),com.vandorlabs.ports.ResourcePorts.POWER.getDefaultState(),3),"Power Port under the arm");
        com.vandorlabs.ports.PowerPortTile source=(com.vandorlabs.ports.PowerPortTile)world.getTileEntity(ROOT.down());source.place(EnumFacing.DOWN);com.vandorlabs.tiles.DeferredTileLoad.flush(world);
        need(source.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,EnumFacing.DOWN).receiveEnergy(Integer.MAX_VALUE,false)>=100000,"filled Power Port");actor.setGameType(GameType.CREATIVE);actor.inventory.currentItem=0;actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);actor.setSneaking(false);actor.capabilities.allowFlying=true;actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.inventoryContainer.detectAndSendChanges();actor.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(0));
        Vec3d eye=new Vec3d(ROOT).addVector(.5,1.5,-4),aim=new Vec3d(ROOT).addVector(.5,.5,.5),d=aim.subtract(eye);feet=eye.addVector(0,-actor.getEyeHeight(),0);actor.connection.setPlayerLocation(feet.x,feet.y,feet.z,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));actor.motionX=actor.motionY=actor.motionZ=0;
    }
    private ArmDishContinuityChecks(){}
}
