package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.BlockWheel;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.drives.MaxwellRuntimeChecks;
import com.vandorlabs.tiles.DeferredTileLoad;
import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.tiles.TileEntityHexTank;
import com.vandorlabs.tiles.TileEntityVehicleController;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.util.concurrent.Future;

/**
 * A fuelled Thermal Fluiduct between a carried Hex Tank and a Maxwell Drive inlet, converted through the
 * Vehicle Status mode request, parked and converted again.
 */
@SideOnly(Side.CLIENT)
public final class VehicleDuctFuelLiveChecks {
    private static final BlockPos ROOT=new BlockPos(229,122,149),CONTROL=ROOT.east();
    private static Future<Boolean> pending;
    private static int stage,ticks,round;
    private static BlockPos engine,tank;
    private static BlockPos[] ducts;
    private static EntityGroundVehicle craft;
    private static int totalFuel;

    private VehicleDuctFuelLiveChecks(){}

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

    private static boolean advance(WorldServer world,EntityPlayerMP actor){
        ticks++;
        require(ticks<400,"stage completes in time");
        if(stage==0){
            require(net.minecraftforge.fml.common.Loader.isModLoaded("thermaldynamics"),"Thermal Dynamics installed for the reported Fluiduct");
            actor.setGameType(GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();
            actor.connection.setPlayerLocation(ROOT.getX()+3.5,ROOT.getY()+3,ROOT.getZ()-2.5,0,12);
            for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-4,-4,-4),ROOT.add(44,8,26))){world.getChunkFromBlockCoords(at);world.removeTileEntity(at);world.setBlockToAir(at);}
            for(int x=-3;x<=14;x++)for(int z=-3;z<=25;z++)world.setBlockState(ROOT.add(x,-3,z),Blocks.STONE.getDefaultState(),2);
            for(int x=0;x<=11;x++)for(int z=0;z<=22;z++)world.setBlockState(ROOT.add(x,-1,z),Blocks.STONE.getDefaultState(),2);
            Block wheel=null;for(Block block:Block.REGISTRY)if(block instanceof BlockWheel){wheel=block;break;}
            require(wheel!=null,"wheel available");
            for(BlockPos point:new BlockPos[]{ROOT.add(0,-2,0),ROOT.add(11,-2,0),ROOT.add(11,-2,22)})world.setBlockState(point,wheel.getDefaultState(),2);
            world.setBlockState(ROOT,ModBlocks.PILOT_SEAT.getDefaultState(),2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,ROOT,world.getBlockState(ROOT),actor,new ItemStack(ModBlocks.PILOT_SEAT));
            world.setBlockState(CONTROL,ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);ModBlocks.VEHICLE_CONTROLLER.onBlockPlacedBy(world,CONTROL,world.getBlockState(CONTROL),actor,new ItemStack(ModBlocks.VEHICLE_CONTROLLER));
            engine=ROOT.add(7,0,14);
            TileEntityConstructor drive=MaxwellRuntimeChecks.assemble(world,engine,3,EnumFacing.WEST,0,0);
            BlockPos inlet=drive.maxwellPort(EnumFacing.WEST);
            tank=inlet.west(3);
            Block fuelTank=Block.getBlockFromName("vandorlabs:hexagonal_fuel_tank");
            world.setBlockState(tank,fuelTank.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.EAST),2);
            Block fluiduct=Block.getBlockFromName("thermaldynamics:duct_16");
            require(fluiduct!=null&&fluiduct!=Blocks.AIR,"Thermal Fluiduct installed");
            ducts=new BlockPos[]{inlet.west(),inlet.west(2)};
            for(BlockPos duct:ducts){
                world.setBlockState(duct,fluiduct.getStateFromMeta(0),3);
                fluiduct.onBlockPlacedBy(world,duct,world.getBlockState(duct),actor,new ItemStack(fluiduct));
            }
            // A Hex Tank is passive: as on a player's craft, a Servo on the tank side pulls fuel into the duct.
            net.minecraft.item.Item servo=net.minecraft.item.Item.getByNameOrId("thermaldynamics:servo");
            require(servo!=null,"Thermal Servo item installed");
            actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new ItemStack(servo));
            require(servo.onItemUse(actor,world,tank,net.minecraft.util.EnumHand.MAIN_HAND,EnumFacing.EAST,.5F,.5F,.5F)==net.minecraft.util.EnumActionResult.SUCCESS,"ordinary Servo placement on the tank side of the Fluiduct");
            actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,ItemStack.EMPTY);
            // A Basic Servo runs on a redstone signal; the block rides along on the craft.
            world.setBlockState(ducts[1].up(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);
            totalFuel=8000;
            require(((TileEntityHexTank)world.getTileEntity(tank)).fill(new FluidStack(fuel(),totalFuel),true)==totalFuel,"filled the carried Hex Tank with fuel");
            DeferredTileLoad.flush(world);
            next();return false;
        }
        if(stage==1){
            // Let the native grid form and carry fuel, as on a player's craft.
            TileEntityConstructor drive=(TileEntityConstructor)world.getTileEntity(engine);
            int stored=((TileEntityHexTank)world.getTileEntity(tank)).tank.getFluidAmount();
            String flow="tank="+stored+" drive="+drive.maxwellFuel()+"/"+drive.maxwellCapacity()+" ducts="+fuelInTransit(world)+" fuel="+fuel().getName();
            if(ticks%40==0)System.out.println("[vandorlabs] vehicle-duct-fuel flow "+flow);
            require(ticks<240,"fuel reaches the Maxwell inlet through the Fluiduct: "+flow);
            if(ticks<60||drive.maxwellFuel()<drive.maxwellCapacity()||fuelInTransit(world)==0)return false;
            VehicleScan scan=new VehicleScan(world,CONTROL);scan.holdSource();scan.measureWeight();
            try{for(int i=0;i<400&&!scan.step(256);i++){}}finally{scan.releaseSource();}
            require(scan.error==null&&scan.result!=null,"round "+round+" scan admits the fuelled Fluiduct between the Hex Tank and the Maxwell inlet: "+scan.error);
            VehicleService.screenMode(actor,CONTROL,null,1);
            next();return false;
        }
        if(stage==2){
            TileEntity raw=world.getTileEntity(CONTROL);
            if(ticks<10||raw instanceof TileEntityVehicleController&&((TileEntityVehicleController)raw).converting||VehicleTransfer.pending(world)||VehicleTransfer.busy(world))return false;
            craft=null;
            for(Entity e:world.loadedEntityList)if(e instanceof EntityGroundVehicle&&!e.isDead&&e.getDistanceSq(ROOT)<900)craft=(EntityGroundVehicle)e;
            require(craft!=null&&craft.structure!=null,"round "+round+" Vehicle Status Taxi request converts the craft with its Fluiduct");
            require(world.isAirBlock(ducts[0])&&world.isAirBlock(tank),"round "+round+" Fluiduct and tank moved onto the craft");
            int carried=0;
            for(VehicleStructure.Cell cell:craft.structure.cells){
                TileEntity tile=craft.view.getTileEntity(cell.pos);
                if(tile instanceof TileEntityHexTank)carried+=((TileEntityHexTank)tile).tank.getFluidAmount();
                if(tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).maxwell())carried+=((TileEntityConstructor)tile).maxwellFuel();
            }
            require(carried>0&&carried<=totalFuel,"round "+round+" carried fuel stays bounded by the loaded amount: "+carried);
            next();return false;
        }
        if(stage==3){
            if(ticks<30||!craft.onGround||!craft.stopped())return false;
            VehicleService.screenMode(actor,craft.structure.controller().pos,craft,0);
            next();return false;
        }
        if(stage==4){
            if(!craft.isDead||VehicleTransfer.pending(world)||VehicleTransfer.busy(world)||ticks<10)return false;
            require(world.getTileEntity(CONTROL) instanceof TileEntityVehicleController,"parked controller returns to its place");
            for(BlockPos duct:ducts)require(TransferNetworkCompat.onboard(world.getTileEntity(duct)),"parked Fluiduct returns to the native world at "+duct);
            if(round==0){round=1;stage=1;ticks=0;return false;}
            System.out.println("[vandorlabs] vehicle-duct-fuel PASS fuelled Thermal Fluiduct between Hex Tank and Maxwell inlet converts to Taxi, parks and converts again");
            return true;
        }
        return false;
    }

    /** Fuel held in the Fluiduct grid's shared buffer, by conservation against the tank and the drive. */
    private static int fuelInTransit(WorldServer world){
        int stored=((TileEntityHexTank)world.getTileEntity(tank)).tank.getFluidAmount();
        return totalFuel-stored-((TileEntityConstructor)world.getTileEntity(engine)).maxwellFuel();
    }
    private static Fluid fuel(){Fluid refined=FluidRegistry.getFluid("refined_fuel");return refined!=null?refined:MaxwellRuntimeChecks.testFuel();}
    private static void next(){stage++;ticks=0;}
    private static void require(boolean ok,String message){
        if(!ok)throw new IllegalStateException("vehicle-duct-fuel stage="+stage+" round="+round+" ticks="+ticks+": "+message);
    }
}
