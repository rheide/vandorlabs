package com.vandorlabs.gear;

import com.vandorlabs.tiles.TileEntityConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Reproduces a reported saved world: an RFTools creative cell feeds hardened flux ducts; the
 * duct under a vertical Large Extender powers a Dish carried on the arm through the arm's
 * Power mode. The Dish must scan (rotate) right after the world loads, after the duct under
 * the arm is mined and placed again, and after every retraction and extension made by
 * right-clicking the arm, also through Idle and Scanning toggles. Positions come from the
 * reported world.
 */
@SideOnly(Side.CLIENT)
public final class SavedDishArmChecks {
    private static final BlockPos ARM=new BlockPos(-71,83,129),DUCT=ARM.down(),DUCT_NEIGHBOUR=DUCT.west();
    private static final String DUCT_ID="thermaldynamics:duct_0";private static final int DUCT_META=1;
    private static final int CYCLES=3,SCAN_WITHIN=100,HOLD=100;
    private static int step,ticks,cycle,held,clientWait;
    private static boolean registered;
    /** Recorded rather than thrown, so the reported motion workflow still runs after a load failure. */
    private static String loadFailure;
    private static volatile boolean complete,clientChecked;
    private static volatile BlockPos clientOwner;
    private static volatile Throwable failure;
    private SavedDishArmChecks(){}

    public static boolean tick(Minecraft mc){
        mc.gameSettings.pauseOnLostFocus=false;
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        if(!registered){MinecraftForge.EVENT_BUS.register(new Observer(mc.getIntegratedServer()));registered=true;}
        if(failure!=null)throw new IllegalStateException("Saved dish arm check",failure);
        BlockPos owner=clientOwner;
        if(owner!=null&&!clientChecked){
            TileEntity tile=mc.world.getTileEntity(owner);
            if(tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).observerScanning())clientChecked=true;
        }
        if(complete&&!clientChecked&&++clientWait>100)throw new IllegalStateException("saved-dish-arm server scanning but client Dish at "+owner+" is not scanning");
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
    private static void require(boolean value,String message,WorldServer world){
        if(!value)throw new IllegalStateException("saved-dish-arm step="+step+" cycle="+cycle+" tick="+ticks+" "+message+" "+state(world));
    }
    private static GearTileEntity arm(WorldServer world){
        TileEntity raw=world.getTileEntity(ARM);return raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;
    }
    private static boolean settled(WorldServer world,GearTileEntity arm,double progress){
        return arm!=null&&arm.progress()==progress&&arm.target()==(progress==1)&&!ArmTransactions.locked(world,ARM)&&arm.payload()!=null&&arm.payload().materialized();
    }
    private static TileEntityConstructor dish(WorldServer world){
        for(BlockPos at:BlockPos.getAllInBox(ARM.add(-3,0,-3),ARM.add(3,8,3))){
            TileEntity tile=world.getTileEntity(at);
            if(tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).observer())return (TileEntityConstructor)tile;
        }
        return null;
    }
    /** What the duct below the arm cached for its upward side: Thermal sends only to a cached receiver. */
    private static String ductCache(WorldServer world){
        TileEntity duct=world.getTileEntity(DUCT);
        if(duct==null)return "none";
        try{
            for(Object unit:(Iterable<?>)duct.getClass().getMethod("getDuctUnits").invoke(duct)){
                java.lang.reflect.Field cache=unit.getClass().getField("tileCache");
                Object up=((Object[])cache.get(unit))[EnumFacing.UP.getIndex()];
                if(up==null)return "up=none";
                java.lang.reflect.Field receiver=up.getClass().getField("receiver");
                return "up="+(receiver.get(up)==null?"provider-only":"receiver");
            }
        }catch(ReflectiveOperationException|ClassCastException error){return "unreadable:"+error;}
        return "no-units";
    }
    private static String state(WorldServer world){
        GearTileEntity arm=arm(world);TileEntityConstructor dish=dish(world);
        TileEntity base=world.getTileEntity(ARM);
        IEnergyStorage energy=base==null?null:base.getCapability(CapabilityEnergy.ENERGY,EnumFacing.DOWN);
        return "arm="+(arm==null?"missing":"progress="+arm.progress()+" target="+arm.target()+" endpoint="+arm.resourceEndpointAvailable()+" mode="+arm.resourceTransfer()+" error="+arm.error())
                +" baseCap="+(energy==null?"none":"canReceive="+energy.canReceive())+" duct="+ductCache(world)
                +" dish="+(dish==null?"missing":dish.getPos()+" powered="+dish.observerPowered()+" scanEnabled="+dish.observerScanEnabled()+" scanning="+dish.observerScanning());
    }
    /** The server's own right-click handler, as a player clicking the arm base. */
    private static void rightClick(WorldServer world,EntityPlayerMP player){
        player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        player.interactionManager.processRightClickBlock(player,world,ItemStack.EMPTY,EnumHand.MAIN_HAND,ARM,EnumFacing.NORTH,.5F,.5F,.5F);
    }
    private static boolean run(WorldServer world,EntityPlayerMP player){
        ++ticks;
        if(step==0){
            player.setGameType(GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();
            player.connection.setPlayerLocation(ARM.getX()+.5,ARM.getY()+1,ARM.getZ()-4.5,0,15);
            if(ticks<200)return false;
            GearTileEntity arm=arm(world);
            require(arm!=null&&settled(world,arm,1),"the saved arm is extended and settled after load",world);
            require(arm.resourceTransfer()==com.vandorlabs.gear.core.ArmResource.POWER,"the saved arm is in Power mode",world);
            require(Item.getItemFromBlock(world.getBlockState(DUCT).getBlock())==Item.getByNameOrId(DUCT_ID),"flux duct under the arm",world);
            TileEntityConstructor dish=dish(world);
            require(dish!=null&&dish.formed()&&dish.observerScanEnabled(),"formed Dish set to Scanning",world);
            System.out.println("[vandorlabs] saved-dish-arm loaded "+state(world));
            step=1;ticks=0;return false;
        }
        GearTileEntity arm=arm(world);
        require(arm!=null,"arm installed",world);
        require(ticks<1200,"timeout",world);
        if(step==1){
            // A freshly loaded world must power the Dish without touching the duct.
            if(!dish(world).observerScanning()&&ticks<SCAN_WITHIN)return false;
            if(dish(world).observerScanning())System.out.println("[vandorlabs] saved-dish-arm scanning after load in "+ticks+" ticks");
            else{loadFailure="Dish does not scan within "+SCAN_WITHIN+" ticks of the world loading: "+state(world);System.out.println("[vandorlabs] saved-dish-arm FAIL-LOAD "+loadFailure);}
            // Mine the duct under the arm and place it again, as reported.
            require(player.interactionManager.tryHarvestBlock(DUCT)&&world.isAirBlock(DUCT),"duct under the arm mined",world);
            ItemStack duct=new ItemStack(Item.getByNameOrId(DUCT_ID),1,DUCT_META);player.setHeldItem(EnumHand.MAIN_HAND,duct);
            require(player.interactionManager.processRightClickBlock(player,world,duct,EnumHand.MAIN_HAND,DUCT_NEIGHBOUR,EnumFacing.EAST,1F,.5F,.5F)==EnumActionResult.SUCCESS,"duct placed again",world);
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
            require(Item.getItemFromBlock(world.getBlockState(DUCT).getBlock())==Item.getByNameOrId(DUCT_ID),"flux duct back under the arm",world);
            step=2;ticks=0;return false;
        }
        if(step==2){
            if(!dish(world).observerScanning()){require(ticks<SCAN_WITHIN,"Dish scans after the duct is placed again",world);return false;}
            rightClick(world,player);require(!arm(world).target(),"right-click retracts the arm",world);step=3;ticks=0;return false;
        }
        if(step==3||step==5){
            boolean extended=step==5;
            if(!settled(world,arm,extended?1:0))return false;
            step++;ticks=0;held=0;return false;
        }
        if(step==4||step==6){
            boolean extended=step==6;
            TileEntityConstructor dish=dish(world);
            if(!dish.observerScanning()){
                require(held==0&&ticks<SCAN_WITHIN,"Dish keeps scanning once the arm is "+(extended?"extended":"retracted")+(held>0?" (stopped after "+held+" scanning ticks)":""),world);
                return false;
            }
            if(++held<HOLD)return false;
            System.out.println("[vandorlabs] saved-dish-arm cycle="+cycle+" "+(extended?"extended":"retracted")+" scanning");
            if(!extended){rightClick(world,player);require(arm(world).target(),"right-click extends the arm",world);step=5;ticks=0;return false;}
            dish.setObserverScanEnabled(false);step=7;ticks=0;return false;
        }
        if(step==7){
            TileEntityConstructor dish=dish(world);
            if(ticks==20){require(!dish.observerScanning(),"Idle stops scanning",world);dish.setObserverScanEnabled(true);}
            if(ticks<=20)return false;
            if(!dish.observerScanning()){require(ticks<20+SCAN_WITHIN,"Scanning resumes after toggling Idle",world);return false;}
            if(++cycle<CYCLES){rightClick(world,player);require(!arm(world).target(),"right-click retracts the arm",world);step=3;ticks=0;return false;}
            if(loadFailure!=null)throw new IllegalStateException("saved-dish-arm motion passed but "+loadFailure);
            clientOwner=dish.getPos();
            System.out.println("[vandorlabs] saved-dish-arm PASS loaded scanning/duct replaced/right-click retract and extend keep scanning/idle toggle x"+CYCLES);
            return true;
        }
        return false;
    }
}
