package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.BlockWheel;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.drives.MaxwellRuntimeChecks;
import com.vandorlabs.tiles.DeferredTileLoad;
import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.tiles.TileEntityHexTank;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.List;

/**
 * Physical-server check: a fuelled Thermal Fluiduct with a Servo between a Hex Tank and a Maxwell inlet.
 * Client classes are absent here, so Thermal Dynamics members must be resolved one by one; resolving every
 * public signature of a duct tile fails on its client-only icon accessor.
 */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class ThermalTransferServerChecks {
    private static final BlockPos ROOT=new BlockPos(2,100,2),CONTROL=ROOT.east();
    private static int stage,ticks;
    private static BlockPos engine,tank;
    private static BlockPos[] ducts;

    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent event){
        if(!Boolean.getBoolean("vandorlabs.thermalTransferChecks")||event.phase!=TickEvent.Phase.END||!(event.world instanceof WorldServer)
                ||event.world.provider.getDimension()!=0||stage==2)return;
        WorldServer world=(WorldServer)event.world;
        require(++ticks<400,"bounded fixture");
        if(stage==0){build(world);stage=1;ticks=0;return;}
        TileEntityConstructor drive=(TileEntityConstructor)world.getTileEntity(engine);
        int stored=((TileEntityHexTank)world.getTileEntity(tank)).tank.getFluidAmount(),inTransit=8000-stored-drive.maxwellFuel();
        if(ticks<60||drive.maxwellFuel()<drive.maxwellCapacity()||inTransit<=0){
            require(ticks<300,"Servo moves fuel through the Fluiduct: tank="+stored+" drive="+drive.maxwellFuel()+" ducts="+inTransit);
            return;
        }
        for(BlockPos duct:ducts)require(!TransferNetworkCompat.write(world.getTileEntity(duct)).hasNoTags(),"native duct save share at "+duct);
        List<ItemStack> materials=TransferNetworkCompat.thermalCopyMaterials(world.getTileEntity(ducts[1]));
        require(materials.size()==2&&materials.get(1).getItem()==Item.getByNameOrId("thermaldynamics:servo"),"duct and fitted Servo materials: "+materials);
        VehicleScan scan=new VehicleScan(world,CONTROL);scan.holdSource();scan.measureWeight();
        try{for(int i=0;i<400&&!scan.step(256);i++){}}finally{scan.releaseSource();}
        require(scan.error==null&&scan.result!=null,"vehicle scan admits the fuelled Fluiduct: "+scan.error);
        int carried=0;
        for(VehicleStructure.Cell cell:scan.result.cells)if(TransferNetworkCompat.onboard(world.getTileEntity(scan.origin.add(cell.pos))))carried++;
        require(carried==2,"both Fluiducts are captured: "+carried);
        stage=2;
        System.out.println("[vandorlabs] thermal-transfer PASS physical server Fluiduct save share, Servo materials and vehicle scan with fuel in transit="+inTransit);
    }

    private static void build(WorldServer world){
        FakePlayer actor=FakePlayerFactory.getMinecraft(world);
        for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-3,-4,-3),ROOT.add(14,6,25))){world.getChunkFromBlockCoords(at);world.removeTileEntity(at);world.setBlockToAir(at);}
        for(int x=-2;x<=13;x++)for(int z=-2;z<=24;z++)world.setBlockState(ROOT.add(x,-3,z),Blocks.STONE.getDefaultState(),2);
        for(int x=0;x<=11;x++)for(int z=0;z<=22;z++)world.setBlockState(ROOT.add(x,-1,z),Blocks.STONE.getDefaultState(),2);
        Block wheel=null;for(Block block:Block.REGISTRY)if(block instanceof BlockWheel){wheel=block;break;}
        require(wheel!=null,"wheel available");
        for(BlockPos point:new BlockPos[]{ROOT.add(0,-2,0),ROOT.add(11,-2,0),ROOT.add(11,-2,22)})world.setBlockState(point,wheel.getDefaultState(),2);
        world.setBlockState(ROOT,ModBlocks.PILOT_SEAT.getDefaultState(),2);
        ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,ROOT,world.getBlockState(ROOT),actor,new ItemStack(ModBlocks.PILOT_SEAT));
        world.setBlockState(CONTROL,ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);
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
        Item servo=Item.getByNameOrId("thermaldynamics:servo");
        require(servo!=null,"Thermal Servo installed");
        actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(servo));
        require(servo.onItemUse(actor,world,tank,EnumHand.MAIN_HAND,EnumFacing.EAST,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"Servo fitted on the tank side of the Fluiduct");
        actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        world.setBlockState(ducts[1].up(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);
        Fluid refined=FluidRegistry.getFluid("refined_fuel");
        Fluid fuel=refined!=null?refined:MaxwellRuntimeChecks.testFuel();
        require(((TileEntityHexTank)world.getTileEntity(tank)).fill(new FluidStack(fuel,8000),true)==8000,"filled the Hex Tank");
        DeferredTileLoad.flush(world);
    }

    private static void require(boolean ok,String message){
        if(!ok)throw new IllegalStateException("thermal-transfer stage="+stage+" ticks="+ticks+": "+message);
    }
    private ThermalTransferServerChecks(){}
}
