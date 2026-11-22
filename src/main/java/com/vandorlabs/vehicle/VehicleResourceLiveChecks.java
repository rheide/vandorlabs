package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.gear.GearRegistry;
import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.ports.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import java.util.concurrent.Future;

/** Installed-mod networks moved through player placement, the real vehicle journal and parking. */
@SideOnly(Side.CLIENT)
public final class VehicleResourceLiveChecks {
    private static final BlockPos ROOT=new BlockPos(149,122,149), ARM=ROOT.add(6,1,4), LEVER=ROOT.east(12);
    private static final ChannelList STORAGE=ChannelList.of(512), DRIVE=ChannelList.of(513);
    private static final String[] DOMAINS={"thermaldynamics","mekanism","galacticraftcore"};
    private static final String[] POWER={"TileDuctEnergy$Basic","TileEntityUniversalCable","TileEntityAluminumWire"};
    private static final String[] FLUID={"TileDuctFluid$Basic$","TileEntityMechanicalPipe","TileEntityFluidPipe"};
    private static Future<Boolean> pending;
    private static int stage,ticks;
    private static EntityGroundVehicle craft;
    private static BlockPos origin,park;
    private static VehicleStructure parked;
    private static Exception failure;

    private VehicleResourceLiveChecks(){}
    public static boolean tick(Minecraft client)throws Exception{
        if(client.currentScreen!=null)client.displayGuiScreen(null);
        client.gameSettings.pauseOnLostFocus=false;
        if(pending!=null){
            if(!pending.isDone())return false;
            if(pending.get())return true;
            pending=null;
        }
        pending=client.getIntegratedServer().callFromMainThread(()->advance(client.getIntegratedServer().getWorld(0),client.getIntegratedServer().getPlayerList().getPlayers().get(0)));
        return false;
    }
    private static boolean advance(WorldServer world,EntityPlayerMP actor)throws Exception{
        require(++ticks<500,"fixture completes");
        if(failure!=null)throw failure;
        if(stage==0){
            actor.setGameType(GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();
            actor.connection.setPlayerLocation(ROOT.getX()+3.5,ROOT.getY()+5,ROOT.getZ()-3.5,0,12);
            for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-3,-3,-3),ROOT.add(40,6,15))){
                world.getChunkFromBlockCoords(at);world.setBlockToAir(at);
            }
            for(int x=0;x<=7;x++)for(int z=0;z<=12;z++)world.setBlockState(ROOT.add(x,-1,z),Blocks.STONE.getDefaultState(),2);
            Block wheel=null;for(Block block:Block.REGISTRY)if(block instanceof BlockWheel){wheel=block;break;}
            require(wheel!=null,"wheel available");
            for(BlockPos point:new BlockPos[]{ROOT.add(0,-2,0),ROOT.add(7,-2,0),ROOT.add(7,-2,12)})world.setBlockState(point,wheel.getDefaultState(),2);
            world.setBlockState(ROOT,ModBlocks.PILOT_SEAT.getDefaultState(),2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,ROOT,world.getBlockState(ROOT),actor,new ItemStack(ModBlocks.PILOT_SEAT));
            world.setBlockState(ROOT.east(),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);
            for(int i=0;i<3;i++){setup(world,i*4+1,DOMAINS[i],POWER[i],true);setup(world,i*4+3,DOMAINS[i],FLUID[i],false);}
            world.setBlockState(ARM.down(),ResourcePorts.MATTER.getDefaultState(),2);
            ((MatterPortTile)world.getTileEntity(ARM.down())).place(EnumFacing.DOWN);
            actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(GearRegistry.GEAR_EXTENDER,2));
            for(BlockPos support:new BlockPos[]{ARM.down(),ARM})require(actor.getHeldItemMainhand().onItemUse(actor,world,support,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==net.minecraft.util.EnumActionResult.SUCCESS,"placed arm section at "+support);
            world.setBlockState(ARM.up(2),Blocks.CHEST.getDefaultState(),2);
            Block switchBlock=null;for(Block block:Block.REGISTRY)if(block instanceof BlockVandorSwitch&&!((BlockVandorSwitch)block).isMomentary()){switchBlock=block;break;}
            require(switchBlock!=null,"wireless switch available");world.setBlockState(LEVER.down(),Blocks.STONE.getDefaultState(),2);world.setBlockState(LEVER,switchBlock.getDefaultState(),2);
            TileEntityRedstoneChannel source=(TileEntityRedstoneChannel)world.getTileEntity(LEVER);
            source.setRedstoneChannels(DRIVE);RedstoneFixtureTransmitters.equip(source);
            armSignal(world,true);
            next();return false;
        }
        if(stage==1){
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(ARM);
            if(ticks<35||arm==null||!arm.isOperational())return false;
            NBTTagCompound settings=arm.settings();settings.setInteger(ArmResource.CHOICE,ArmResource.ITEM.ordinal());settings.setInteger("Trigger",1);settings.setString("Channels",DRIVE.toString());settings.setDouble("ActiveExtension",1);
            arm.operator(actor);arm.configure(settings);
            TileEntityBattery battery=(TileEntityBattery)world.getTileEntity(point(4,1));
            TileEntityHexTank tank=(TileEntityHexTank)world.getTileEntity(point(1,3));
            TransmitterSlots.configure(battery.fullnessOutput,STORAGE,64,false);
            TransmitterSlots.configure(tank.fullnessOutput,ChannelList.of(514),64,false);
            require(battery.getRedstoneChannels().isEmpty(),"Battery receive bank remains independent");
            VehicleScan scan=new VehicleScan(world,ROOT);for(int i=0;i<300&&!scan.step(128);i++){}
            require(scan.result!=null,"source scan admitted six complete loaded networks "+scan.error);
            origin=scan.origin;
            VehicleTransfer.submit(world,()->craft=VehicleTransfer.assemble(world,scan.result,origin,actor),error->failure=error);
            next();return false;
        }
        if(stage==2){
            if(craft==null||VehicleTransfer.busy(world))return false;
            for(int i=0;i<3;i++){
                TileEntityBattery source=(TileEntityBattery)local(point(1,i*4+1));
                TileEntityHexTank tank=(TileEntityHexTank)local(point(1,i*4+3));
                require(source.getCapability(CapabilityEnergy.ENERGY,EnumFacing.WEST).receiveEnergy(6400,false)==6400,"charged carried source "+i);
                require(tank.fill(new FluidStack(FluidRegistry.WATER,1500),true)==1500,"filled carried source "+i);
            }
            require(RedstoneChannels.level(world,LEVER,514)>0||((TileEntityHexTank)local(point(1,3))).fullnessOutput.level()>0,"carried tank level tracks filled amount");
            next();return false;
        }
        if(stage==3){
            StringBuilder summary=new StringBuilder();boolean full=true;
            for(int i=0;i<3;i++){
                TileEntityBattery from=(TileEntityBattery)local(point(1,i*4+1)),to=(TileEntityBattery)local(point(4,i*4+1));
                TileEntityHexTank tank=(TileEntityHexTank)local(point(1,i*4+3));
                FluidStack fluid=((FluidPortTile)local(point(4,i*4+3))).stored();int amount=fluid==null?0:fluid.amount;
                require(from.stored()+to.stored()==6400,"conserved RF for "+DOMAINS[i]);
                require(tank.tank.getFluidAmount()+amount==1500,"conserved fluid for "+DOMAINS[i]);
                full&=to.stored()==6400&&amount==1500;
                summary.append(' ').append(DOMAINS[i]).append('=').append(to.stored()).append('/').append(amount);
            }
            if(!full){if(ticks>70)require(false,"carried transfer totals"+summary);return false;}
            require(RedstoneChannels.level(world,LEVER,512)==1,"carried battery publishes stored-level analog signal");
            MatterPortTile matter=(MatterPortTile)local(ARM.down());
            matter.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,EnumFacing.DOWN).insertItem(0,new ItemStack(Items.DIAMOND,7),false);
            next();return false;
        }
        GearTileEntity arm=(GearTileEntity)local(ARM);
        if(stage==4){
            TileEntityChest chest=(TileEntityChest)local(ARM.up(2));
            if(chest.getStackInSlot(0).getCount()!=7)return false;
            armSignal(world,false);next();return false;
        }
        if(stage==5){
            if(arm.progress()==1||arm.progress()==0)return false;
            require(arm.payload()!=null&&!arm.payload().materialized(),"channel stroke captured exactly one payload owner");
            MatterPortTile matter=(MatterPortTile)local(ARM.down());
            matter.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,EnumFacing.DOWN).insertItem(0,new ItemStack(Items.DIAMOND,7),false);
            next();return false;
        }
        if(stage==6){
            MatterPortTile matter=(MatterPortTile)local(ARM.down());
            if(arm.progress()>0){require(matter.stored(0).getCount()==7,"transfer paused while redstone stroke is moving");return false;}
            TileEntityChest chest=(TileEntityChest)arm.payload().view(arm).getTileEntity(BlockPos.ORIGIN);
            if(chest.getStackInSlot(0).getCount()!=14)return false;
            require(matter.stored(0).isEmpty(),"only the captured chest has the 14 diamonds");
            EntityGroundVehicle reloaded=new EntityGroundVehicle(world);reloaded.readFromNBT(craft.writeToNBT(new NBTTagCompound()));
            GearTileEntity restored=(GearTileEntity)reloaded.view.getTileEntity(ARM.subtract(origin));
            require(((TileEntityChest)restored.payload().view(restored).getTileEntity(BlockPos.ORIGIN)).getStackInSlot(0).getCount()==14,"save/load keeps the nested inventory once");
            reloaded.signals.close();reloaded.setDead();
            armSignal(world,true);next();return false;
        }
        if(stage==7){
            if(arm.progress()!=1)return false;
            craft.rotationYaw=90;park=ROOT.add(26,-2,0);craft.setPosition(park.getX(),park.getY(),park.getZ());parked=VehicleRotation.plan(craft).structure;
            VehicleTransfer.submit(world,()->VehicleTransfer.park(world,craft,park,actor,false),error->failure=error);next();return false;
        }
        if(VehicleTransfer.busy(world)||ticks<20)return false;
        for(VehicleStructure.Cell cell:parked.cells)if(TransferNetworkCompat.onboard(world.getTileEntity(park.add(cell.pos))))
            require(world.getTileEntity(park.add(cell.pos)).getWorld()==world,"parked conduit returns to the native world");
        require(craft.isDead,"parking retired the carried owner");
        System.out.println("[vandorlabs] vehicle-resources PASS installed Thermal Dynamics/Mekanism/Galacticraft RF and fluid, analog storage, redstone arm items pause/resume, reload and rotated parking");
        return true;
    }

    private static TileEntity local(BlockPos physical){return craft.view.getTileEntity(physical.subtract(origin));}
    private static void armSignal(WorldServer world,boolean powered){
        TileEntityRedstoneChannel tile=(TileEntityRedstoneChannel)world.getTileEntity(LEVER);
        tile.setLocalOn(powered);RedstoneChannels.latchChanged(tile,powered);
    }
    private static BlockPos point(int x,int z){return ROOT.add(x,0,z);}
    private static void setup(WorldServer world,int row,String domain,String type,boolean power){
        Block source=Block.getBlockFromName(power?"vandorlabs:battery_block":"vandorlabs:hexagonal_fuel_tank");
        IBlockState state=power?source.getDefaultState().withProperty(BlockBattery.FACING,EnumFacing.WEST):source.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.EAST);
        world.setBlockState(point(1,row),state,2);
        IBlockState conduit=variant(world,domain,type);
        world.setBlockState(point(2,row),conduit,2);world.setBlockState(point(3,row),conduit,2);
        world.setBlockState(point(4,row),power?state:ResourcePorts.FLUID.getDefaultState(),2);
        if(power){((TileEntityBattery)world.getTileEntity(point(1,row))).place(EnumFacing.WEST);((TileEntityBattery)world.getTileEntity(point(4,row))).place(EnumFacing.WEST);}
        else ((FluidPortTile)world.getTileEntity(point(4,row))).place(EnumFacing.WEST);
    }
    private static IBlockState variant(WorldServer world,String domain,String path){
        for(Block block:Block.REGISTRY)if(block.getRegistryName().getResourceDomain().equals(domain))for(int meta=0;meta<16;meta++){
            IBlockState state;try{state=block.getStateFromMeta(meta);}catch(RuntimeException invalid){continue;}
            if(!block.hasTileEntity(state))continue;
            TileEntity tile=block.createTileEntity(world,state);
            if(tile!=null&&tile.getClass().getName().contains(path))return state;
        }
        throw new IllegalStateException("Missing installed "+domain+" "+path);
    }
    private static void next(){stage++;ticks=0;}
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException("Vehicle resource stage "+stage+" tick "+ticks+": "+reason);}
}
