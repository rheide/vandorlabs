package com.vandorlabs.gear;

import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.navigation.NavigationSources;
import com.vandorlabs.ports.PowerPortTile;
import com.vandorlabs.ports.ResourcePorts;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.DeferredTileLoad;
import com.vandorlabs.tiles.TileEntityConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Real port output and ownership journals around a raised Survey Dish. */
@SideOnly(Side.CLIENT)
public final class ObserverArmPowerChecks {
    private static final BlockPos ROOT=ArmCoreFixture.ROOT;
    private static final ChannelList CHANNEL=ChannelList.of(47);
    private static int stage,ticks;
    private static boolean movedWithoutPower,prepared,dialogDone;
    private static IEnergyStorage detachedInlet;

    public static boolean tick(Minecraft client)throws Exception{
        if(stage==4)return true;
        client.gameSettings.pauseOnLostFocus=false;
        if(stage==1&&!dialogDone){dialogDone=com.vandorlabs.client.ObserverDialogLiveChecks.tick(client,ROOT.up(2));return false;}
        client.displayGuiScreen(null);
        if(++ticks>=700){
            net.minecraft.world.WorldServer world=client.getIntegratedServer().getWorld(0);StringBuilder state=new StringBuilder();GearTileEntity a=(GearTileEntity)world.getTileEntity(ROOT);
            state.append(" progress=").append(a==null?-1:a.progress()).append(" target=").append(a==null?"":String.valueOf(a.target())).append(" error=").append(a==null?"":a.error()).append(" payload=").append(a==null||a.payload()==null?"none":"materialized="+a.payload().materialized());
            if(a!=null&&a.payload()!=null&&a.payload().materialized())for(BlockPos at:a.payload().placedCells(a)){TileEntity c=world.getTileEntity(at);
                if(c instanceof TileEntityConstructor){TileEntityConstructor o=(TileEntityConstructor)c;BlockPos port=o.observerPowerPort();
                    state.append(" observer=").append(at).append(" port=").append(port).append(" below=").append(world.getBlockState(port.down()).getBlock().getRegistryName()).append(" powered=").append(o.observerPowered()).append(" activity=").append(o.activity());}}
            require(false,"expected phase completes"+state);
        }
        return client.getIntegratedServer().callFromMainThread(()->advance(
                client.getIntegratedServer().getWorld(0),client.getIntegratedServer().getPlayerList().getPlayers().get(0))).get();
    }

    private static boolean advance(WorldServer world,EntityPlayerMP actor){
        if(stage==0){
            ArmCoreFixture.setup(world,actor,0,"standalone_dish_core");
            ArmCoreFixture.emptyHands(actor,net.minecraft.world.GameType.CREATIVE);
            actor.connection.setPlayerLocation(ROOT.getX()+.5,ROOT.getY()+1,ROOT.getZ()-3.5,0,0);
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);
            arm.operator(actor);
            net.minecraft.nbt.NBTTagCompound settings=arm.settings();
            settings.setInteger(ArmResource.CHOICE,ArmResource.POWER.ordinal());
            arm.configure(settings);
            BlockPos port=ROOT.down();
            require(world.setBlockState(port,ResourcePorts.POWER.getDefaultState(),3),"replace the arm support with a Power Port");
            PowerPortTile source=(PowerPortTile)world.getTileEntity(port);
            source.place(EnumFacing.DOWN);
            DeferredTileLoad.flush(world);
            require(source.getCapability(CapabilityEnergy.ENERGY,EnumFacing.DOWN).receiveEnergy(20000,false)==20000,"filled output port");
            ArmTransactions.testFault=point->{if("prepared".equals(point))prepared=true;};
            next();
            return false;
        }
        GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);
        BlockPos display=ROOT.north(4);
        if(stage==1){
            if(NavigationSources.matching(world,display,CHANNEL).isEmpty())return false;
            TileEntity receiver=world.getTileEntity(ROOT.up(2));
            detachedInlet=receiver.getCapability(CapabilityEnergy.ENERGY,EnumFacing.DOWN);
            require(detachedInlet!=null&&detachedInlet.canReceive(),"actual contacted Dish part receives power");
            TileEntityConstructor observer=receiver instanceof TileEntityConstructor?(TileEntityConstructor)receiver:(TileEntityConstructor)world.getTileEntity(((com.vandorlabs.tiles.TileEntityConstructorPart)receiver).owner());
            observer.cycleActivity();
            require(observer.activity()==1&&observer.observerPowered()&&detachedInlet.receiveEnergy(20,true)==0&&detachedInlet.receiveEnergy(20,false)==0,"connected idle observer has no RF demand");
            observer.cycleActivity();
            arm.target(false);
            next();
            return false;
        }
        ArmPayload attachment=arm.payload();
        boolean locked=ArmTransactions.locked(world,ROOT);
        if(stage==2){
            if(attachment!=null&&!attachment.materialized()&&arm.progress()>0&&arm.progress()<1){
                require(!detachedInlet.canReceive()&&detachedInlet.receiveEnergy(20,false)==0,"cached receiver refuses while the source cell is in transit");
                require(NavigationSources.matching(world,display,CHANNEL).isEmpty()&&NavigationSources.noPower(world,display,CHANNEL),
                        "moving Dish appears as a linked observer without power");
                movedWithoutPower=true;
            }
            if(arm.progress()!=0||locked||attachment==null||!attachment.materialized()||NavigationSources.matching(world,display,CHANNEL).isEmpty())return false;
            require(movedWithoutPower&&prepared,"the complete stroke used real journals and resumed power at the retracted endpoint");
            arm.target(true);
            next();
            return false;
        }
        if(arm.progress()!=1||locked||attachment==null||!attachment.materialized()||NavigationSources.matching(world,display,CHANNEL).isEmpty())return false;
        int owners=0;boolean scanning=false;
        for(BlockPos at:attachment.placedCells(arm)){
            TileEntity cell=world.getTileEntity(at);
            if(cell instanceof TileEntityConstructor&&((TileEntityConstructor)cell).observerPowered()){owners++;scanning|=((TileEntityConstructor)cell).observerScanning();}
        }
        require(owners==1,"power returns to exactly one native observer");
        // Connected is not enough: RF must arrive again, which is what drives the rotation.
        if(!scanning)return false;
        ArmTransactions.testFault=null;
        next();
        System.out.println("[vandorlabs] arm-live PASS observer-power aligned-endpoints/motion-pause/restored-owner");
        return true;
    }

    private static void next(){stage++;ticks=0;}
    private static void require(boolean condition,String detail){
        if(!condition)throw new IllegalStateException("Observer arm power stage="+stage+" ticks="+ticks+": "+detail);
    }
    private ObserverArmPowerChecks(){}
}
