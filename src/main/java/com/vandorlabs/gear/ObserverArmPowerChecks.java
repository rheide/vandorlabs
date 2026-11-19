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
    private static boolean movedWithoutPower,prepared;
    private static IEnergyStorage detachedInlet;

    public static boolean tick(Minecraft client)throws Exception{
        client.gameSettings.pauseOnLostFocus=false;
        client.displayGuiScreen(null);
        require(++ticks<700,"expected phase completes");
        return client.getIntegratedServer().callFromMainThread(()->advance(
                client.getIntegratedServer().getWorld(0),client.getIntegratedServer().getPlayerList().getPlayers().get(0))).get();
    }

    private static boolean advance(WorldServer world,EntityPlayerMP actor){
        if(stage==0){
            ArmCoreFixture.setup(world,actor,0,"standalone_dish_core");
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
        int owners=0;
        for(BlockPos at:attachment.placedCells(arm)){
            TileEntity cell=world.getTileEntity(at);
            if(cell instanceof TileEntityConstructor&&((TileEntityConstructor)cell).observerPowered())owners++;
        }
        require(owners==1,"power returns to exactly one native observer");
        ArmTransactions.testFault=null;
        System.out.println("[vandorlabs] arm-live PASS observer-power aligned-endpoints/motion-pause/restored-owner");
        return true;
    }

    private static void next(){stage++;ticks=0;}
    private static void require(boolean condition,String detail){
        if(!condition)throw new IllegalStateException("Observer arm power stage="+stage+" ticks="+ticks+": "+detail);
    }
    private ObserverArmPowerChecks(){}
}
