package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.drives.MaxwellRuntimeChecks;
import com.vandorlabs.gear.GearRegistry;
import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.machines.CoreLayout;
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
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import java.util.concurrent.Future;

/** Installed-mod networks moved through player placement, the real vehicle journal and parking. */
@SideOnly(Side.CLIENT)
public final class VehicleResourceLiveChecks {
    private static final BlockPos ROOT=new BlockPos(149,122,149), ARM=ROOT.add(6,1,4), LEVER=ROOT.east(20);
    private static final ChannelList STORAGE=ChannelList.of(512), DRIVE=ChannelList.of(513);
    private static final String[] DOMAINS={"thermaldynamics","mekanism","galacticraftcore"};
    private static final String[] POWER={"TileDuctEnergy$Basic","TileEntityUniversalCable","TileEntityAluminumWire"};
    private static final String[] FLUID={"TileDuctFluid$Basic$","TileEntityMechanicalPipe","TileEntityFluidPipe"};
    private static final ArmPipeRoute RF_ARM=new ArmPipeRoute(ROOT.add(8,1,7),true,3000);
    private static final ArmPipeRoute FLUID_ARM=new ArmPipeRoute(ROOT.add(8,1,11),false,700);
    private static Future<Boolean> pending;
    private static int stage,ticks;
    private static boolean configured;
    private static EntityGroundVehicle craft;
    private static BlockPos origin,park,engine,tank,observer,cell;
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
            for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-3,-3,-3),ROOT.add(48,9,25))){
                world.getChunkFromBlockCoords(at);world.setBlockToAir(at);
            }
            for(int x=0;x<=11;x++)for(int z=0;z<=22;z++)world.setBlockState(ROOT.add(x,-1,z),Blocks.STONE.getDefaultState(),2);
            Block wheel=null;for(Block block:Block.REGISTRY)if(block instanceof BlockWheel){wheel=block;break;}
            require(wheel!=null,"wheel available");
            for(BlockPos point:new BlockPos[]{ROOT.add(0,-2,0),ROOT.add(11,-2,0),ROOT.add(11,-2,22)})world.setBlockState(point,wheel.getDefaultState(),2);
            world.setBlockState(ROOT,ModBlocks.PILOT_SEAT.getDefaultState(),2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,ROOT,world.getBlockState(ROOT),actor,new ItemStack(ModBlocks.PILOT_SEAT));
            world.setBlockState(ROOT.east(),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);
            for(int i=0;i<3;i++){setup(world,i*4+1,DOMAINS[i],POWER[i],true);setup(world,i*4+3,DOMAINS[i],FLUID[i],false);}
            machines(world);
            world.setBlockState(ARM.down(),ResourcePorts.MATTER.getDefaultState(),2);
            ((MatterPortTile)world.getTileEntity(ARM.down())).place(EnumFacing.DOWN);
            placeArm(world,actor,ARM);
            world.setBlockState(ARM.up(2),Blocks.CHEST.getDefaultState(),2);
            RF_ARM.place(world,actor);FLUID_ARM.place(world,actor);
            Block switchBlock=null;for(Block block:Block.REGISTRY)if(block instanceof BlockVandorSwitch&&!((BlockVandorSwitch)block).isMomentary()){switchBlock=block;break;}
            require(switchBlock!=null,"wireless switch available");world.setBlockState(LEVER.down(),Blocks.STONE.getDefaultState(),2);world.setBlockState(LEVER,switchBlock.getDefaultState().withProperty(BlockVandorSwitch.FACING,EnumFacing.UP),2);
            TileEntityRedstoneChannel source=(TileEntityRedstoneChannel)world.getTileEntity(LEVER);
            source.setRedstoneChannels(DRIVE);RedstoneFixtureTransmitters.equip(source);
            armSignal(world,true);
            next();return false;
        }
        if(stage==1){
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(ARM);
            if(ticks<35||arm==null||!arm.isOperational())return false;
            if(!RF_ARM.ready(world)||!FLUID_ARM.ready(world))return false;
            if(!configured){
                configureArm(arm,actor,ArmResource.ITEM);
                RF_ARM.configure(world,actor);FLUID_ARM.configure(world,actor);
                TileEntityBattery battery=(TileEntityBattery)world.getTileEntity(point(4,1));
                TileEntityHexTank tank=(TileEntityHexTank)world.getTileEntity(point(1,3));
                TransmitterSlots.configure(battery.fullnessOutput,STORAGE,64,false);
                TransmitterSlots.configure(tank.fullnessOutput,ChannelList.of(514),64,false);
                require(battery.getRedstoneChannels().isEmpty(),"Battery receive bank remains independent");
                configured=true;ticks=0;return false;
            }
            VehicleScan scan=new VehicleScan(world,ROOT);scan.holdSource();for(int i=0;i<300&&!scan.step(128);i++){}
            require(scan.result!=null,"source scan admitted six complete loaded networks "+scan.error);
            origin=scan.origin;
            require(scan.result.at(LEVER.subtract(origin))==null,"test actuator stays off the vehicle's construction deck");
            require(scan.result.matches(world,origin),"source preimage remains exact before durable assembly "+mismatch(world,scan.result,origin));
            VehicleTransfer.submit(world,()->{craft=VehicleTransfer.assemble(world,scan.result,origin,actor);scan.releaseSource();},error->{
                scan.releaseSource();failure=new IllegalStateException(error.getMessage()+" "+mismatch(world,scan.result,origin),error);
            });
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
            require(((TileEntityHexTank)local(tank)).fill(new FluidStack(MaxwellRuntimeChecks.testFuel(),1500),true)==1500,"loaded the fuel tank for the installed engine");
            IEnergyStorage input=local(cell).getCapability(CapabilityEnergy.ENERGY,EnumFacing.EAST);
            require(input!=null,"installed Power Cell exposes native input");
            int rf=0;for(int i=0;i<100&&rf<6400;i++)rf+=input.receiveEnergy(6400-rf,false);
            require(rf==6400,"charged the installed RFTools Power Cell: "+rf);
            RF_ARM.seed();FLUID_ARM.seed();
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
            TileEntityConstructor drive=(TileEntityConstructor)local(engine),dish=(TileEntityConstructor)local(observer);
            require(drive.maxwellFuel()+((TileEntityHexTank)local(tank)).tank.getFluidAmount()==1500,"actual Maxwell inlet conserves its onboard supply");
            boolean throughArms=RF_ARM.check(craft,1,1)&&FLUID_ARM.check(craft,1,1);
            if(!full||!throughArms||drive.maxwellFuel()!=drive.maxwellCapacity()){if(ticks>95)require(false,"carried transfer totals"+summary+" Maxwell="+drive.maxwellFuel()+" RF arm="+RF_ARM.received(craft)+" fluid arm="+FLUID_ARM.received(craft));return false;}
            IEnergyStorage source=VehicleResources.endpoint(local(cell),EnumFacing.WEST,CapabilityEnergy.ENERGY);
            require(source!=null&&source.getEnergyStored()<6400&&source.getEnergyStored()>0&&dish.observerPowered()&&dish.observerScanning(),"carried RFTools Power Cell debits local RF and powers the real Survey Dish inlet");
            require(RedstoneChannels.level(world,LEVER,512)==1,"carried battery publishes stored-level analog signal");
            MatterPortTile matter=(MatterPortTile)local(ARM.down());
            matter.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,EnumFacing.DOWN).insertItem(0,new ItemStack(Items.DIAMOND,7),false);
            next();return false;
        }
        GearTileEntity arm=(GearTileEntity)local(ARM);
        if(stage==4){
            TileEntityChest chest=(TileEntityChest)local(ARM.up(2));
            require(chest!=null,"deployed endpoint chest remains in the craft grid before the commanded stroke, progress="+arm.progress()+" target="+arm.target());
            if(chest.getStackInSlot(0).getCount()!=7)return false;
            armSignal(world,false);next();return false;
        }
        if(stage==5){
            if(arm.progress()==1||arm.progress()==0)return false;
            require(arm.payload()!=null&&!arm.payload().materialized(),"channel stroke captured exactly one payload owner");
            if(RF_ARM.arm(craft).fullyDeployed()||FLUID_ARM.arm(craft).fullyDeployed())return false;
            MatterPortTile matter=(MatterPortTile)local(ARM.down());
            matter.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,EnumFacing.DOWN).insertItem(0,new ItemStack(Items.DIAMOND,7),false);
            RF_ARM.seed();FLUID_ARM.seed();
            next();return false;
        }
        if(stage==6){
            RF_ARM.requireSecondBatchHeld();FLUID_ARM.requireSecondBatchHeld();
            MatterPortTile matter=(MatterPortTile)local(ARM.down());
            if(arm.progress()>0){require(matter.stored(0).getCount()==7,"transfer paused while redstone stroke is moving");return false;}
            if(RF_ARM.arm(craft).progress()!=0||FLUID_ARM.arm(craft).progress()!=0)return false;
            TileEntityChest chest=(TileEntityChest)arm.payload().view(arm).getTileEntity(BlockPos.ORIGIN);
            if(chest.getStackInSlot(0).getCount()!=14)return false;
            require(matter.stored(0).isEmpty(),"only the captured chest has the 14 diamonds");
            EntityGroundVehicle reloaded=new EntityGroundVehicle(world);reloaded.readFromNBT(craft.writeToNBT(new NBTTagCompound()));
            GearTileEntity restored=(GearTileEntity)reloaded.view.getTileEntity(ARM.subtract(origin));
            require(((TileEntityChest)restored.payload().view(restored).getTileEntity(BlockPos.ORIGIN)).getStackInSlot(0).getCount()==14,"save/load keeps the nested inventory once");
            require(RF_ARM.check(reloaded,2,1)&&FLUID_ARM.check(reloaded,2,1),"reload keeps both second batches at their unique source owners");
            reloaded.signals.close();reloaded.setDead();
            armSignal(world,true);next();return false;
        }
        if(stage==7){
            if(RF_ARM.arm(craft).progress()!=1)RF_ARM.requireSecondBatchHeld();
            if(FLUID_ARM.arm(craft).progress()!=1)FLUID_ARM.requireSecondBatchHeld();
            if(arm.progress()!=1||!RF_ARM.check(craft,2,2)||!FLUID_ARM.check(craft,2,2)){
                if(ticks>95)require(false,"redeployment resumes both pipe bridges RF="+RF_ARM.received(craft)+" fluid="+FLUID_ARM.received(craft)+" arm="+arm.progress());
                return false;
            }
            craft.rotationYaw=90;park=ROOT.add(26,-2,0);craft.setPosition(park.getX(),park.getY(),park.getZ());parked=VehicleRotation.plan(craft).structure;
            VehicleTransfer.submit(world,()->VehicleTransfer.park(world,craft,park,actor,false),error->failure=error);next();return false;
        }
        if(VehicleTransfer.busy(world)||ticks<20)return false;
        int conduits=0,diamonds=0;
        for(VehicleStructure.Cell cell:parked.cells){
            TileEntity tile=world.getTileEntity(park.add(cell.pos));
            if(TransferNetworkCompat.onboard(tile)){require(tile.getWorld()==world,"parked conduit returns to the native world");conduits++;}
            if(tile instanceof TileEntityChest){ItemStack stack=((TileEntityChest)tile).getStackInSlot(0);if(stack.getItem()==Items.DIAMOND)diamonds+=stack.getCount();}
        }
        require(conduits==21&&diamonds==14,"parked native network and cargo match the carried owners: ducts="+conduits+" diamonds="+diamonds);
        RF_ARM.checkParked(world);FLUID_ARM.checkParked(world);
        require(craft.isDead,"parking retired the carried owner");
        System.out.println("[vandorlabs] vehicle-resources PASS installed Thermal Dynamics/Mekanism/Galacticraft RF and fluid, RFTools Power Cell feeds Survey Dish, Maxwell fuel, analog storage, RF and fluid pipe-to-pipe arms with blocked faces, redstone pause/resume, item ownership, reload and rotated parking");
        return true;
    }

    private static TileEntity local(BlockPos physical){return craft.view.getTileEntity(physical.subtract(origin));}

    @SideOnly(Side.CLIENT)
    private static final class ArmPipeRoute {
        final BlockPos root,inlet,outlet,source,destination,blocked;
        final boolean power;
        final int batch;
        ArmPipeRoute(BlockPos root,boolean power,int batch){
            this.root=root;this.power=power;this.batch=batch;
            inlet=root.down();outlet=root.up(2);source=inlet.east();destination=outlet.east();blocked=outlet.west();
        }
        void place(WorldServer world,EntityPlayerMP actor){
            IBlockState pipe=power?variant(world,DOMAINS[0],POWER[0]):variant(world,DOMAINS[1],FLUID[1]);
            world.setBlockState(inlet,pipe,2);
            placeArm(world,actor,root);
            world.setBlockState(outlet,pipe,2);
            if(power){
                Block battery=Block.getBlockFromName("vandorlabs:battery_block");
                for(BlockPos pos:new BlockPos[]{source,destination,blocked}){
                    EnumFacing face=pos.equals(destination)?EnumFacing.WEST:EnumFacing.EAST;
                    world.setBlockState(pos,battery.getDefaultState().withProperty(BlockBattery.FACING,face),2);
                    ((TileEntityBattery)world.getTileEntity(pos)).place(face);
                }
            }else{
                Block tank=Block.getBlockFromName("vandorlabs:hexagonal_fuel_tank");
                world.setBlockState(source,tank.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.EAST),2);
                for(BlockPos pos:new BlockPos[]{destination,blocked}){
                    world.setBlockState(pos,ResourcePorts.FLUID.getDefaultState(),2);
                    ((FluidPortTile)world.getTileEntity(pos)).place(pos.equals(destination)?EnumFacing.WEST:EnumFacing.EAST);
                }
            }
        }
        boolean ready(WorldServer world){TileEntity raw=world.getTileEntity(root);return raw instanceof GearTileEntity&&((GearTileEntity)raw).isOperational();}
        void configure(WorldServer world,EntityPlayerMP actor){
            configureArm((GearTileEntity)world.getTileEntity(root),actor,power?ArmResource.POWER:ArmResource.FLUID);
            TileEntity tip=world.getTileEntity(outlet);NBTTagCompound tipData=tip.writeToNBT(new NBTTagCompound());
            if(power){
                byte[] faces=new byte[6];faces[EnumFacing.WEST.getIndex()]=1;
                tipData.setByteArray("Connections",faces);
            }else{
                tipData.setInteger("connection"+EnumFacing.WEST.getIndex(),3);
                tipData.setInteger("connection"+EnumFacing.EAST.getIndex(),1);
                TileEntity base=world.getTileEntity(inlet);NBTTagCompound baseData=base.writeToNBT(new NBTTagCompound());
                baseData.setInteger("connection"+EnumFacing.EAST.getIndex(),2);base.readFromNBT(baseData);
            }
            tip.readFromNBT(tipData);
        }
        TileEntity tile(EntityGroundVehicle owner,BlockPos pos){return owner.view.getTileEntity(pos.subtract(origin));}
        GearTileEntity arm(EntityGroundVehicle owner){return (GearTileEntity)tile(owner,root);}
        void seed(){
            TileEntity supply=tile(craft,source);
            int accepted=power?supply.getCapability(CapabilityEnergy.ENERGY,EnumFacing.EAST).receiveEnergy(batch,false)
                    :((TileEntityHexTank)supply).fill(new FluidStack(FluidRegistry.WATER,batch),true);
            require(accepted==batch,"seeded pipe-to-pipe arm: "+root);
        }
        int stored(TileEntity tile){
            if(power)return ((TileEntityBattery)tile).stored();
            if(tile instanceof TileEntityHexTank)return ((TileEntityHexTank)tile).tank.getFluidAmount();
            FluidStack contents=((FluidPortTile)tile).stored();
            require(contents==null||contents.getFluid()==FluidRegistry.WATER,"fluid identity survives relay");
            return contents==null?0:contents.amount;
        }
        int received(EntityGroundVehicle owner){return stored(tile(owner,destination));}
        boolean check(EntityGroundVehicle owner,int supplied,int delivered){
            int moved=received(owner),waiting=stored(tile(owner,source)),decoy=stored(tile(owner,blocked));
            require(decoy==0,"blocked face does not transfer at "+root);
            require(moved+waiting==batch*supplied,"pipe-to-pipe arm preserves each resource across both owners at "+root);
            require(arm(owner).payload()==null,"tip pipe remains owned by the craft grid at "+root);
            return moved==batch*delivered;
        }
        void requireSecondBatchHeld(){require(check(craft,2,1),"settled retracted and moving arm hold the full second batch at "+root);}
        void checkParked(WorldServer world){
            require(stored(world.getTileEntity(parkedAt(destination)))==2*batch&&stored(world.getTileEntity(parkedAt(source)))==0
                    &&stored(world.getTileEntity(parkedAt(blocked)))==0,"parking keeps exact arm route totals at "+root);
            TileEntity nativeOutlet=world.getTileEntity(parkedAt(outlet));
            NBTTagCompound settings=nativeOutlet.writeToNBT(new NBTTagCompound());
            EnumFacing blocked=net.minecraft.util.Rotation.CLOCKWISE_90.rotate(EnumFacing.WEST);
            require(power?settings.getByteArray("Connections")[blocked.getIndex()]==1:settings.getInteger("connection"+blocked.getIndex())==3,
                    "parking rotates the installed native blocked face at "+root);
        }
        private BlockPos parkedAt(BlockPos physical){
            int width=(int)craft.structure.bounds.maxX,depth=(int)craft.structure.bounds.maxZ;
            BlockPos rotatedSeat=VehicleRotation.cell(craft.structure.seat,width,depth,1);
            return park.add(parked.seat.subtract(rotatedSeat)).add(VehicleRotation.cell(physical.subtract(origin),width,depth,1));
        }
    }
    private static void placeArm(WorldServer world,EntityPlayerMP actor,BlockPos root){
        actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(GearRegistry.GEAR_EXTENDER,2));
        for(BlockPos support:new BlockPos[]{root.down(),root})require(actor.getHeldItemMainhand().onItemUse(actor,world,support,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==net.minecraft.util.EnumActionResult.SUCCESS,"placed arm section at "+support);
    }
    private static void configureArm(GearTileEntity arm,EntityPlayerMP actor,ArmResource resource){
        NBTTagCompound settings=arm.settings();settings.setInteger(ArmResource.CHOICE,resource.ordinal());settings.setInteger("Trigger",2);settings.setString("Channels",DRIVE.toString());settings.setDouble("ActiveExtension",1);
        arm.operator(actor);arm.configure(settings);
        require(arm.target()&&arm.progress()==1,"arm starts deployed on its active deployment channel: "+resource);
    }
    private static void armSignal(WorldServer world,boolean powered){
        TileEntityRedstoneChannel tile=(TileEntityRedstoneChannel)world.getTileEntity(LEVER);
        require(tile!=null,"off-vehicle redstone actuator remains loaded");
        tile.setLocalOn(powered);RedstoneChannels.latchChanged(tile,powered);
    }
    private static BlockPos point(int x,int z){return ROOT.add(x,0,z);}
    private static void machines(WorldServer world){
        engine=ROOT.add(7,0,14);
        TileEntityConstructor maxwell=MaxwellRuntimeChecks.assemble(world,engine,3,EnumFacing.WEST,0,0);
        BlockPos inlet=maxwell.maxwellPort(EnumFacing.WEST);
        tank=inlet.west(3);
        Block fuelTank=Block.getBlockFromName("vandorlabs:hexagonal_fuel_tank");
        world.setBlockState(tank,fuelTank.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.EAST),2);
        IBlockState pipe=variant(world,DOMAINS[1],FLUID[1]);
        world.setBlockState(inlet.west(),pipe,2);world.setBlockState(inlet.west(2),pipe,2);

        observer=ROOT.add(1,1,19);
        BlockConstructorCore core=(BlockConstructorCore)Block.getBlockFromName("vandorlabs:standalone_dish_core");
        world.setBlockState(observer,core.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.NORTH),2);
        CoreLayout layout=new CoreLayout(core.width,core.height,core.depth,EnumFacing.NORTH.getIndex(),0,0);
        for(int y=0;y<core.height;y++)for(int x=0;x<core.width;x++)for(int z=0;z<core.depth;z++){
            BlockPos at=observer.add(layout.x(x,z),y,layout.z(x,z));
            if(!at.equals(observer)&&layout.frame(x,y,z))world.setBlockState(at,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),2);
        }
        TileEntityConstructor dish=(TileEntityConstructor)world.getTileEntity(observer);
        require(dish.assemble(),"assembled Survey Dish");
        BlockPos duct=dish.observerPowerPort().down();
        IBlockState flux=variant(world,DOMAINS[0],POWER[0]);
        for(int i=0;i<3;i++)world.setBlockState(duct.east(i),flux,2);
        cell=duct.east(3);
        world.setBlockState(cell,variant(world,"rftools","PowerCellNormalTileEntity"),2);
        TileEntity power=world.getTileEntity(cell);
        NBTTagCompound settings=power.writeToNBT(new NBTTagCompound());
        settings.setInteger("networkId",-1);settings.setByte("m"+EnumFacing.EAST.getIndex(),(byte)1);settings.setByte("m"+EnumFacing.WEST.getIndex(),(byte)2);
        power.readFromNBT(settings);
    }
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
    private static String mismatch(WorldServer world,VehicleStructure source,BlockPos origin){
        for(VehicleStructure.Cell cell:source.cells){
            BlockPos at=origin.add(cell.pos);
            TileEntity tile=world.getTileEntity(at);
            NBTTagCompound before=cell.tileData(),after=tile==null?null:VehicleTileData.translated(TransferNetworkCompat.write(tile),BlockPos.ORIGIN.subtract(origin));
            if(after!=null)after=BuildingBlockCompat.canonical(cell.state,after);
            if(world.getBlockState(at)!=cell.state||!java.util.Objects.equals(before,after))return at+" originally="+before+" actual="+after;
        }
        return "";
    }
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException("Vehicle resource stage "+stage+" tick "+ticks+": "+reason);}
}
