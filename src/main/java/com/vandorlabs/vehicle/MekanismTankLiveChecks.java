package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fml.relauncher.*;

/** Native formed tank -> actual vehicle journal -> moved/rotated parked native tank. */
@SideOnly(Side.CLIENT)
public final class MekanismTankLiveChecks {
    private static final BlockPos ROOT=new BlockPos(2303,120,0);
    private static int stage,ticks,heading;
    private static BlockPos seat=ROOT.add(-1,0,0),parkOrigin;
    private static EntityGroundVehicle craft;
    private static VehicleStructure parked;
    private static Exception transferFailure;
    private static boolean done;
    private static IBlockState tank,glass,valve;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("mekanism-tank stage="+stage+" heading="+heading+" tick="+ticks+": "+why);}
    private static IBlockState variant(WorldServer world,String suffix){for(Block block:Block.REGISTRY)if(block.getRegistryName().getResourceDomain().equals("mekanism"))for(int meta=0;meta<16;meta++)try{IBlockState state=block.getStateFromMeta(meta);if(block.hasTileEntity(state)){TileEntity tile=block.createTileEntity(world,state);if(tile!=null&&tile.getClass().getName().equals("mekanism.common.tile.TileEntity"+suffix))return state;}}catch(RuntimeException|LinkageError ignored){}throw new IllegalStateException("Missing installed Mekanism "+suffix);}
    private static TileEntity nativeTank(WorldServer world){for(TileEntity tile:world.loadedTileEntityList)if(MekanismTankCompat.tank(tile)&&tile.getPos().getX()>2200)try{if(tile.getClass().getField("structure").get(tile)!=null)return tile;}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}return null;}
    private static int fluid(TileEntity tile)throws Exception{Object structure=tile.getClass().getField("structure").get(tile);if(structure==null)return -1;FluidStack stored=(FluidStack)structure.getClass().getField("fluidStored").get(structure);if(stored!=null)require(stored.getFluid()==FluidRegistry.WATER&&stored.tag!=null&&"native-tank-transport".equals(stored.tag.getString("TransportMarker")),"native fluid identity and tag retained");return stored==null?0:stored.amount;}
    private static void contents(VehicleStructure snapshot){String id=null;int nodes=0;for(VehicleStructure.Cell cell:snapshot.cells){NBTTagCompound data=cell.tileData();if(data==null||!data.hasKey("cachedID"))continue;FluidStack fluid=FluidStack.loadFluidStackFromNBT(data.getCompoundTag("cachedFluid"));require(fluid!=null&&fluid.getFluid()==FluidRegistry.WATER&&fluid.amount==5000&&fluid.tag!=null&&"native-tank-transport".equals(fluid.tag.getString("TransportMarker")),"snapshot retains 5000 mB water and its full fluid tag on every shared-cache member");String next=data.getString("cachedID");if(id==null)id=next;require(id.equals(next),"one shared tank inventory ID");NBTTagList items=data.getTagList("Items",10);boolean buckets=false;for(int i=0;i<items.tagCount();i++){ItemStack item=new ItemStack(items.getCompoundTagAt(i));buckets|=item.getItem()==Items.BUCKET&&item.getCount()==3;}require(buckets,"native output inventory retains three buckets");nodes++;}require(nodes==21,"twenty Dynamic Tank blocks plus one Dynamic Valve retained: "+nodes);}
    public static boolean tick(Minecraft mc)throws Exception{
        if(done)return true;
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        return mc.getIntegratedServer().callFromMainThread(()->run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0))).get();
    }
    @SuppressWarnings("unchecked") private static boolean run(WorldServer world,EntityPlayerMP actor)throws Exception{
        require(++ticks<900,"bounded native formation and journal completion");if(transferFailure!=null)throw transferFailure;
        if(stage==0&&"verify".equals(System.getProperty("vandorlabs.mekanismTankRestart",""))){
            for(net.minecraft.entity.Entity entity:world.loadedEntityList)if(entity instanceof EntityGroundVehicle&&((EntityGroundVehicle)entity).structure!=null&&entity.posX>2200){require(craft==null,"exactly one saved vehicle owner after a fresh JVM restart");craft=(EntityGroundVehicle)entity;}
            if(craft==null)return false;require(nativeTank(world)==null,"source world has no duplicate usable tank after restart");contents(craft.structure);stage=3;ticks=0;
        }
        if(stage==0){
            tank=variant(world,"DynamicTank");glass=variant(world,"StructuralGlass");valve=variant(world,"DynamicValve");actor.setGameType(GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.connection.setPlayerLocation(ROOT.getX()-3,126,-3,0,30);
            for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-5,-4,-5),ROOT.add(140,7,12))){world.getChunkFromBlockCoords(at);world.setBlockToAir(at);}
            for(int x=-1;x<=3;x++)for(int z=-1;z<=3;z++)world.setBlockState(ROOT.add(x,-1,z),Blocks.STONE.getDefaultState(),2);
            Block wheel=null,engine=null;for(Block block:Block.REGISTRY){if(block instanceof BlockWheel&&wheel==null)wheel=block;if(block instanceof BlockPropulsionLight&&!VehicleFlight.lift(block)&&engine==null)engine=block;}require(wheel!=null&&engine!=null,"vehicle supports and propulsion registered");
            for(BlockPos at:new BlockPos[]{ROOT.add(-1,-2,-1),ROOT.add(3,-2,-1),ROOT.add(3,-2,3)})world.setBlockState(at,wheel.getDefaultState(),2);
            for(int x=0;x<3;x++)for(int y=0;y<3;y++)for(int z=0;z<3;z++){int edge=(x==0||x==2?1:0)+(y==0||y==2?1:0)+(z==0||z==2?1:0);if(edge>0)world.setBlockState(ROOT.add(x,y,z),edge>=2?tank:x==2?valve:glass,2);}
            world.setBlockState(seat,ModBlocks.PILOT_SEAT.getDefaultState(),2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,seat,world.getBlockState(seat),actor,new ItemStack(ModBlocks.PILOT_SEAT));Block controller=Block.getBlockFromName("vandorlabs:vehicle_controller");world.setBlockState(ROOT.add(-1,0,1),controller.getDefaultState(),2);world.setBlockState(ROOT.add(-1,0,2),engine.getDefaultState(),2);stage=1;ticks=0;return false;
        }
        if(stage==1){TileEntity formed=nativeTank(world);if(formed==null)return false;TileEntity port=world.getTileEntity(ROOT.add(2,1,1));FluidStack filling=new FluidStack(FluidRegistry.WATER,5000);filling.tag=new NBTTagCompound();filling.tag.setString("TransportMarker","native-tank-transport");int accepted=(Integer)port.getClass().getMethod("fill",EnumFacing.class,FluidStack.class,boolean.class).invoke(port,EnumFacing.EAST,filling,true);require(accepted==5000,"native valve fills 5000 mB");Object structure=formed.getClass().getField("structure").get(formed);((java.util.List<ItemStack>)structure.getClass().getMethod("getInventory").invoke(structure)).set(1,new ItemStack(Items.BUCKET,3));stage=2;ticks=0;return false;}
        if(stage==2){if(ticks<10)return false;TileEntity formed=nativeTank(world);require(formed!=null&&fluid(formed)==5000,"native tank retains fluid before conversion");VehicleScan scan=new VehicleScan(world,seat);for(int i=0;i<200&&!scan.step(128);i++){}require(scan.result!=null&&scan.error==null,"actual vehicle scan accepts tank/glass/valve: "+scan.error);contents(scan.result);
            if(heading==0){VehicleStructure.Cell one=scan.result.cells.stream().filter(c->c.tileData()!=null&&c.tileData().hasKey("cachedID")).findFirst().get();java.util.List<VehicleStructure.Cell> cropped=java.util.Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,one.state,VehicleTileData.translated(one.tileData(),BlockPos.ORIGIN.subtract(one.pos))));boolean refused=false;try{VehicleStructure cut=VehicleStructure.display(cropped);VehicleRotation.validate(cut,new VehicleWorld(world,cut));}catch(IllegalArgumentException expected){refused=true;}require(refused,"partial tank rejected before mutation");}
            VehicleTransfer.submit(world,()->craft=VehicleTransfer.assemble(world,scan.result,scan.origin,actor),failure->transferFailure=failure);stage=3;ticks=0;return false;
        }
        if(stage==3){if(VehicleTransfer.busy(world)||craft==null)return false;require(nativeTank(world)==null,"captured tank has no remaining world owner");for(net.minecraft.entity.item.EntityItem drop:world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,new net.minecraft.util.math.AxisAlignedBB(ROOT).grow(160)))require(drop.getItem().getItem()!=Items.BUCKET,"assembly does not publish duplicate native inventory drops");contents(craft.rotatedSnapshot(0));NBTTagCompound save=craft.writeToNBT(new NBTTagCompound());EntityGroundVehicle reloaded=new EntityGroundVehicle(world);reloaded.readFromNBT(save);require(reloaded.structure!=null,"saved vehicle with Mekanism tank reloads");contents(reloaded.rotatedSnapshot(0));reloaded.signals.close();reloaded.setDead();
            if("save".equals(System.getProperty("vandorlabs.mekanismTankRestart",""))){world.getMinecraftServer().saveAllWorlds(false);done=true;System.out.println("[vandorlabs] mekanism-tank-save PASS filled tank assembled and saved for a fresh JVM restart fluid=5000 buckets=3");return true;}
            craft.rotationYaw=heading*90;parkOrigin=ROOT.add(24*(heading+1),-2,-1);craft.setPosition(parkOrigin.getX(),parkOrigin.getY(),parkOrigin.getZ());parked=VehicleRotation.plan(craft).structure;contents(parked);VehicleTransfer.submit(world,()->VehicleTransfer.park(world,craft,parkOrigin,actor,false),failure->transferFailure=failure);stage=4;ticks=0;return false;
        }
        if(VehicleTransfer.busy(world)||ticks<20)return false;TileEntity formed=nativeTank(world);if(formed==null)return false;require(fluid(formed)==5000,"parked native tank reforms with exactly 5000 mB");Object structure=formed.getClass().getField("structure").get(formed);java.util.List<ItemStack> inventory=(java.util.List<ItemStack>)structure.getClass().getMethod("getInventory").invoke(structure);require(inventory.get(1).getItem()==Items.BUCKET&&inventory.get(1).getCount()==3,"parked native output inventory retained once");seat=parkOrigin.add(parked.seat);System.out.println("[vandorlabs] mekanism-tank heading PASS turns="+heading+" native formation valve fill actual vehicle conversion saved entity reload journal park fluid=5000 buckets=3");craft=null;
        if(++heading<4){stage=2;ticks=0;return false;}done=true;System.out.println("[vandorlabs] mekanism-tank PASS four native headings Dynamic Tank Structural Glass Dynamic Valve 5000mB three buckets actual conversion movement saved reload parking reformation partial-shell refusal");return true;
    }
    private MekanismTankLiveChecks(){}
}
