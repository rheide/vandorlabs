package com.vandorlabs.gear;

import com.vandorlabs.ports.PowerPortTile;
import com.vandorlabs.ports.ResourcePorts;
import com.vandorlabs.tiles.DeferredTileLoad;
import com.vandorlabs.tiles.TileEntityConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Reported workflow: a Thermal Dynamics flux duct under the Power Port of a Sentinel Array
 * carried by an Extender (the arm under another base cell). Raising the array disconnects
 * it; lowering it back onto the duct must resume scanning (the rotation the client shows),
 * also after toggling Idle and Scanning.
 */
@SideOnly(Side.CLIENT)
public final class ObserverDuctMotionChecks {
    private static final BlockPos ROOT=ArmCoreFixture.ROOT;
    /** Hardened Fluxduct: TDDucts.energyHardened has id OFFSET_ENERGY+1 on block duct_0. */
    private static final String DUCT="thermaldynamics:duct_0";private static final int DUCT_META=1;
    private static final int CYCLES=2;
    private static int step,ticks,cycle,clientWait;
    private static BlockPos duct,source;
    private static boolean registered;
    private static volatile boolean complete,clientChecked;
    private static volatile BlockPos clientOwner;
    private static volatile Throwable failure;
    private ObserverDuctMotionChecks(){}

    public static boolean tick(Minecraft mc){
        mc.gameSettings.pauseOnLostFocus=false;
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        if(!registered){MinecraftForge.EVENT_BUS.register(new Observer(mc.getIntegratedServer()));registered=true;}
        if(failure!=null)throw new IllegalStateException("Observer duct motion server check",failure);
        // The client must also show the scanning (rotating) state after the final reconnection.
        BlockPos owner=clientOwner;
        if(owner!=null&&!clientChecked){
            TileEntity tile=mc.world.getTileEntity(owner);
            if(tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).observerScanning())clientChecked=true;
        }
        if(complete&&!clientChecked&&++clientWait>100)throw new IllegalStateException("observer-duct-motion server scanning but client tile at "+owner+" is not scanning");
        return complete&&clientChecked;
    }
    @SideOnly(Side.CLIENT)
    public static final class Observer {
        private final MinecraftServer server;
        private Observer(MinecraftServer server){this.server=server;}
        @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){
            if(event.phase!=TickEvent.Phase.END||complete||failure!=null||server.getPlayerList().getPlayers().isEmpty())return;
            try{complete=run(server.getWorld(0),server.getPlayerList().getPlayers().get(0));}
            catch(RuntimeException|LinkageError error){failure=error;}
            if(complete||failure!=null)MinecraftForge.EVENT_BUS.unregister(this);
        }
    }
    private static void require(boolean value,String message){
        if(!value)throw new IllegalStateException("observer-duct-motion step="+step+" cycle="+cycle+" tick="+ticks+" "+message);
    }
    private static GearTileEntity arm(WorldServer world){
        TileEntity raw=world.getTileEntity(ROOT);return raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;
    }
    private static boolean settled(WorldServer world,GearTileEntity arm,double progress){
        return arm!=null&&arm.progress()==progress&&!ArmTransactions.locked(world,ROOT)&&arm.payload()!=null&&arm.payload().materialized();
    }
    /** The installed observer owner; moves replace the tile, so look it up each time. */
    private static TileEntityConstructor observer(WorldServer world){
        for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-4,-2,-4),ROOT.add(4,8,4))){
            TileEntity tile=world.getTileEntity(at);
            if(tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).observer())return (TileEntityConstructor)tile;
        }
        return null;
    }
    private static String state(WorldServer world){
        TileEntityConstructor o=observer(world);
        if(o==null)return "observer=missing";
        BlockPos port=o.observerPowerPort();TileEntity below=world.getTileEntity(port.down());
        return "owner="+o.getPos()+" port="+port+" below="+(below==null?"none":below.getClass().getSimpleName())+" powered="+o.observerPowered()+" scanning="+o.observerScanning()+" activity="+o.activity()
                +" sourceRF="+(source==null||world.getTileEntity(source)==null?-1:world.getTileEntity(source).getCapability(CapabilityEnergy.ENERGY,EnumFacing.DOWN).getEnergyStored());
    }
    private static void refill(WorldServer world){
        PowerPortTile port=(PowerPortTile)world.getTileEntity(source);
        port.getCapability(CapabilityEnergy.ENERGY,EnumFacing.DOWN).receiveEnergy(Integer.MAX_VALUE,false);
    }
    private static boolean run(WorldServer world,EntityPlayerMP player){
        ++ticks;
        if(step==0){
            ArmCoreFixture.centreObservers=false;
            try{ArmCoreFixture.setup(world,player,0,"standalone_antenna_core");}finally{ArmCoreFixture.centreObservers=true;}
            ArmCoreFixture.emptyHands(player,net.minecraft.world.GameType.CREATIVE);
            player.connection.setPlayerLocation(ROOT.getX()+.5,ROOT.getY()+2,ROOT.getZ()-5.5,0,10);
            TileEntityConstructor o=observer(world);
            require(o!=null&&o.formed()&&o.structureWidth()==2,"formed 2x2 Sentinel Array on the Extender");
            require(!(world.getTileEntity(o.observerPowerPort().down()) instanceof GearTileEntity),"arm sits under a non-port cell "+state(world));
            GearTileEntity arm=arm(world);require(arm!=null,"arm installed");
            arm.operator(player);arm.target(false);step=1;ticks=0;return false;
        }
        GearTileEntity arm=arm(world);
        require(arm!=null,"arm installed");
        require(ticks<600,"timeout arm progress="+arm.progress()+" target="+arm.target()+" error="+arm.error()+" "+state(world));
        if(step==1){
            if(!settled(world,arm,0))return false;
            // Lowered: put a real flux duct under the Power Port, fed by a Power Port below it.
            TileEntityConstructor o=observer(world);BlockPos port=o.observerPowerPort();
            duct=port.down();source=duct.down();
            require(world.isAirBlock(duct)&&world.isAirBlock(source),"space under the lowered Power Port "+state(world));
            require(world.setBlockState(source,ResourcePorts.POWER.getDefaultState(),3),"source Power Port");
            ((PowerPortTile)world.getTileEntity(source)).place(EnumFacing.DOWN);DeferredTileLoad.flush(world);
            Item item=Item.getByNameOrId(DUCT);require(item!=null,"Thermal Dynamics is installed (VANDOR_LABS_COMPAT_MODS)");
            player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(item,1,DUCT_META));
            require(player.getHeldItemMainhand().onItemUse(player,world,source,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1F,.5F)==EnumActionResult.SUCCESS,"flux duct placed on the source");
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
            require(Item.getItemFromBlock(world.getBlockState(duct).getBlock())==item,"flux duct under the Power Port");
            refill(world);step=2;ticks=0;return false;
        }
        if(step==2){
            // Baseline: the duct powers the lowered array.
            refill(world);
            TileEntityConstructor o=observer(world);
            if(!o.observerScanning()){require(ticks<100,"flux duct powers the lowered array "+state(world));return false;}
            arm.operator(player);arm.target(true);step=3;ticks=0;return false;
        }
        if(step==3){
            refill(world);
            if(!settled(world,arm,1))return false;
            require(world.getBlockState(duct).getBlock()!=net.minecraft.init.Blocks.AIR,"flux duct stays in place when the array rises");
            if(ticks<40)return false;
            require(!observer(world).observerScanning(),"raised array away from the duct has no power "+state(world));
            arm.operator(player);arm.target(false);step=4;ticks=0;return false;
        }
        if(step==4){
            refill(world);
            if(!settled(world,arm,0))return false;
            step=5;ticks=0;return false;
        }
        if(step==5){
            refill(world);
            TileEntityConstructor o=observer(world);
            if(!o.observerScanning()){require(ticks<100,"array lowered back onto the flux duct scans again "+state(world));return false;}
            System.out.println("[vandorlabs] observer-duct-motion reconnect cycle="+cycle+" after="+ticks+" ticks");
            o.setObserverScanEnabled(false);step=6;ticks=0;return false;
        }
        if(step==6){
            refill(world);
            TileEntityConstructor o=observer(world);
            if(ticks==20){require(!o.observerScanning(),"Idle stops scanning "+state(world));o.setObserverScanEnabled(true);}
            if(ticks<=20)return false;
            if(!o.observerScanning()){require(ticks<120,"Scanning resumes after toggling Idle "+state(world));return false;}
            if(++cycle<CYCLES){arm.operator(player);arm.target(true);step=3;ticks=0;return false;}
            clientOwner=o.getPos();
            System.out.println("[vandorlabs] observer-duct-motion PASS duct powers/raised unpowered/lowered reconnects/idle toggle x"+CYCLES);
            return true;
        }
        return false;
    }
}
