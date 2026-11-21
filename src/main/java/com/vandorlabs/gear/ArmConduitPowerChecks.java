package com.vandorlabs.gear;

import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.ports.PowerPortTile;
import com.vandorlabs.ports.ResourcePorts;
import com.vandorlabs.tiles.DeferredTileLoad;
import com.vandorlabs.tiles.TileEntityConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
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

import java.util.ArrayList;
import java.util.List;

/**
 * A Dish carried on an Extender in Power mode, its centre Power Port on the arm tip, fed by an
 * energy conduit from another mod under the arm base. Every conduit must keep powering the Dish
 * (scanning, the rotation players see) after each right-click retraction and extension and
 * through Idle and Scanning toggles. Conduits differ in when they decide whether a neighbour
 * accepts energy: Thermal Dynamics caches canReceive at connection time, Mekanism caches
 * capability presence, Galacticraft re-reads neighbours every network tick.
 */
@SideOnly(Side.CLIENT)
public final class ArmConduitPowerChecks {
    private static final BlockPos ROOT=ArmCoreFixture.ROOT,CONDUIT=ROOT.down(),SOURCE=ROOT.down(2);
    private static final int CYCLES=2,SCAN_WITHIN=100,HOLD=100;
    private static final class Conduit {
        final String name,id;final int meta;
        Conduit(String name,String id,int meta){this.name=name;this.id=id;this.meta=meta;}
    }
    private static final Conduit[] CONDUITS={
            new Conduit("Thermal Dynamics Hardened Fluxduct","thermaldynamics:duct_0",1),
            new Conduit("Mekanism Universal Cable","mekanism:transmitter",0),
            new Conduit("Galacticraft Aluminum Wire","galacticraftcore:aluminum_wire",0)};
    private static int conduit,step,ticks,cycle,held,clientWait;
    private static final List<String> failures=new ArrayList<>();
    private static boolean registered;
    private static volatile boolean complete,clientChecked;
    private static volatile BlockPos clientOwner;
    private static volatile Throwable failure;
    private ArmConduitPowerChecks(){}

    public static boolean tick(Minecraft mc){
        mc.gameSettings.pauseOnLostFocus=false;
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        if(!registered){MinecraftForge.EVENT_BUS.register(new Observer(mc.getIntegratedServer()));registered=true;}
        if(failure!=null)throw new IllegalStateException("Arm conduit power server check",failure);
        BlockPos owner=clientOwner;
        if(owner!=null&&!clientChecked){
            TileEntity tile=mc.world.getTileEntity(owner);
            if(tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).observerScanning())clientChecked=true;
        }
        if(complete&&!clientChecked&&++clientWait>100)throw new IllegalStateException("arm-conduit-power server scanning but client Dish at "+owner+" is not scanning");
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
    /** Thrown for one conduit; the run records it and continues with the next conduit. */
    private static final class ConduitFailure extends RuntimeException {ConduitFailure(String message){super(message);}}
    private static void require(boolean value,String message,WorldServer world){
        if(!value)throw new ConduitFailure(CONDUITS[conduit].name+": step="+step+" cycle="+cycle+" tick="+ticks+" "+message+" "+state(world));
    }
    private static GearTileEntity arm(WorldServer world){
        TileEntity raw=world.getTileEntity(ROOT);return raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;
    }
    private static boolean settled(WorldServer world,GearTileEntity arm){
        // A freshly built arm adopts the Dish as its payload on its first move.
        return arm!=null&&arm.progress()==(arm.target()?1:0)&&!ArmTransactions.locked(world,ROOT)&&(arm.payload()==null||arm.payload().materialized());
    }
    private static TileEntityConstructor dish(WorldServer world){
        for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-4,0,-4),ROOT.add(4,8,4))){
            TileEntity tile=world.getTileEntity(at);
            if(tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).observer())return (TileEntityConstructor)tile;
        }
        return null;
    }
    private static String state(WorldServer world){
        GearTileEntity arm=arm(world);TileEntityConstructor dish=dish(world);TileEntity base=world.getTileEntity(ROOT);
        net.minecraftforge.energy.IEnergyStorage energy=base==null?null:base.getCapability(CapabilityEnergy.ENERGY,EnumFacing.DOWN);
        return "arm="+(arm==null?"missing":"progress="+arm.progress()+" target="+arm.target()+" endpoint="+arm.resourceEndpointAvailable()+" error="+arm.error())
                +" baseCap="+(energy==null?"none":"canReceive="+energy.canReceive())+" conduit="+world.getBlockState(CONDUIT)
                +" dish="+(dish==null?"missing":dish.getPos()+" powered="+dish.observerPowered()+" scanEnabled="+dish.observerScanEnabled()+" scanning="+dish.observerScanning());
    }
    private static void refill(WorldServer world){
        TileEntity source=world.getTileEntity(SOURCE);
        if(source instanceof PowerPortTile)source.getCapability(CapabilityEnergy.ENERGY,EnumFacing.DOWN).receiveEnergy(Integer.MAX_VALUE,false);
    }
    /** The server's own right-click handler, as a player clicking the arm base. */
    private static void rightClick(WorldServer world,EntityPlayerMP player){
        player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        player.interactionManager.processRightClickBlock(player,world,ItemStack.EMPTY,EnumHand.MAIN_HAND,ROOT,EnumFacing.NORTH,.5F,.5F,.5F);
    }
    private static boolean run(WorldServer world,EntityPlayerMP player){
        try{return advance(world,player);}
        catch(ConduitFailure failed){
            failures.add(failed.getMessage());System.out.println("[vandorlabs] arm-conduit-power FAIL "+failed.getMessage());
            return next(world);
        }
    }
    private static boolean next(WorldServer world){
        if(++conduit<CONDUITS.length){step=0;ticks=0;cycle=0;return false;}
        if(!failures.isEmpty())throw new IllegalStateException("arm-conduit-power "+failures.size()+" conduit(s) failed: "+String.join(" | ",failures));
        clientOwner=dish(world).getPos();
        System.out.println("[vandorlabs] arm-conduit-power PASS Thermal/Mekanism/Galacticraft conduits keep the arm-carried Dish scanning through right-click retract/extend and idle toggle x"+CYCLES);
        return true;
    }
    private static boolean advance(WorldServer world,EntityPlayerMP player){
        ++ticks;
        if(step==0){
            ArmCoreFixture.setup(world,player,0,"standalone_dish_core");
            ArmCoreFixture.emptyHands(player,net.minecraft.world.GameType.CREATIVE);
            player.connection.setPlayerLocation(ROOT.getX()+.5,ROOT.getY()+1,ROOT.getZ()-5.5,0,10);
            GearTileEntity arm=arm(world);require(arm!=null,"arm installed",world);
            arm.operator(player);
            NBTTagCompound settings=arm.settings();settings.setInteger(ArmResource.CHOICE,ArmResource.POWER.ordinal());arm.configure(settings);
            require(arm.resourceTransfer()==ArmResource.POWER,"arm in Power mode",world);
            TileEntityConstructor dish=dish(world);
            require(dish!=null&&dish.formed()&&dish.observerPowerPort().equals(ROOT.up(2)),"Dish Power Port on the arm tip",world);
            // The arm's support becomes the conduit, fed from a Power Port below it.
            world.setBlockToAir(CONDUIT);
            require(world.setBlockState(SOURCE,ResourcePorts.POWER.getDefaultState(),3),"source Power Port",world);
            ((PowerPortTile)world.getTileEntity(SOURCE)).place(EnumFacing.DOWN);DeferredTileLoad.flush(world);
            Item item=Item.getByNameOrId(CONDUITS[conduit].id);require(item!=null,CONDUITS[conduit].id+" is installed",world);
            ItemStack stack=new ItemStack(item,1,CONDUITS[conduit].meta);player.setHeldItem(EnumHand.MAIN_HAND,stack);
            require(player.interactionManager.processRightClickBlock(player,world,stack,EnumHand.MAIN_HAND,SOURCE,EnumFacing.UP,.5F,1F,.5F)==EnumActionResult.SUCCESS,"conduit placed on the source",world);
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
            require(Item.getItemFromBlock(world.getBlockState(CONDUIT).getBlock())==item,"conduit under the arm base",world);
            refill(world);step=1;ticks=0;return false;
        }
        refill(world);
        GearTileEntity arm=arm(world);
        require(arm!=null,"arm installed",world);
        require(ticks<1200,"timeout",world);
        if(step==1){
            if(!settled(world,arm))return false;
            if(!dish(world).observerScanning()){require(ticks<SCAN_WITHIN,"conduit powers the Dish through the arm",world);return false;}
            System.out.println("[vandorlabs] arm-conduit-power "+CONDUITS[conduit].name+" powers the Dish at progress="+arm.progress());
            rightClick(world,player);step=2;ticks=0;return false;
        }
        if(step==2||step==4){
            if(!settled(world,arm))return false;
            step++;ticks=0;held=0;return false;
        }
        if(step==3||step==5){
            TileEntityConstructor dish=dish(world);
            String where=arm.progress()==1?"extended":"retracted";
            if(!dish.observerScanning()){
                require(held==0&&ticks<SCAN_WITHIN,"Dish keeps scanning once the arm is "+where+(held>0?" (stopped after "+held+" scanning ticks)":""),world);
                return false;
            }
            if(++held<HOLD)return false;
            System.out.println("[vandorlabs] arm-conduit-power "+CONDUITS[conduit].name+" cycle="+cycle+" "+where+" scanning");
            if(step==3){rightClick(world,player);step=4;ticks=0;return false;}
            dish.setObserverScanEnabled(false);step=6;ticks=0;return false;
        }
        if(step==6){
            TileEntityConstructor dish=dish(world);
            if(ticks==20){require(!dish.observerScanning(),"Idle stops scanning",world);dish.setObserverScanEnabled(true);}
            if(ticks<=20)return false;
            if(!dish.observerScanning()){require(ticks<20+SCAN_WITHIN,"Scanning resumes after toggling Idle",world);return false;}
            if(++cycle<CYCLES){rightClick(world,player);step=2;ticks=0;return false;}
            return next(world);
        }
        return false;
    }
}
