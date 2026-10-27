package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Standalone reactor glass is building material; native fusion ownership remains excluded. */
public final class MekanismReactorGlassChecks {
    private static final String GLASS="mekanism.generators.common.tile.reactor.TileEntityReactorGlass";
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("reactor-glass: "+why);}
    private static IBlockState glass(WorldServer world){
        for(Block block:Block.REGISTRY)if(block.getRegistryName().getResourceDomain().equals("mekanismgenerators"))for(int meta=0;meta<16;meta++)try{
            IBlockState state=block.getStateFromMeta(meta);TileEntity tile=block.hasTileEntity(state)?block.createTileEntity(world,state):null;
            if(tile!=null&&tile.getClass().getName().equals(GLASS))return state;
        }catch(RuntimeException invalidMetadata){}
        throw new IllegalStateException("Installed Mekanism reactor glass not found");
    }
    public static VehicleStructure run(WorldServer world,EntityPlayerMP actor)throws Exception{
        VehicleStructure last=null;
        IBlockState glass=glass(world);Block wheel=null,engine=null;
        for(Block block:Block.REGISTRY){if(block instanceof BlockWheel&&wheel==null)wheel=block;if(block instanceof BlockPropulsionLight&&!VehicleFlight.lift(block)&&engine==null)engine=block;}
        require(wheel!=null&&engine!=null,"native vehicle fixtures");
        for(int turns=0;turns<4;turns++){
            BlockPos root=new BlockPos(2400+turns*16,120,64),seat=root.up(),pane=root.add(2,1,0);
            for(BlockPos p:BlockPos.getAllInBox(root.add(-3,-2,-3),root.add(7,6,7))){world.getChunkFromBlockCoords(p);world.setBlockState(p,p.getY()==root.getY()-2?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);}
            for(int x=0;x<3;x++)for(int z=0;z<3;z++)world.setBlockState(root.add(x,0,z),Blocks.STONE.getDefaultState(),2);
            for(BlockPos p:Arrays.asList(root.down(),root.add(2,-1,0),root.add(2,-1,2)))world.setBlockState(p,wheel.getDefaultState(),2);
            world.setBlockState(seat,ModBlocks.PILOT_SEAT.getDefaultState(),2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,seat,world.getBlockState(seat),actor,new ItemStack(ModBlocks.PILOT_SEAT));
            world.setBlockState(root.add(1,1,0),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);world.setBlockState(root.add(0,1,1),engine.getDefaultState(),2);
            world.setBlockState(pane,glass,2);world.setBlockState(pane.south(),glass,2);
            VehicleScan scan=new VehicleScan(world,seat);for(int n=0;n<1000&&!scan.step(128);n++);
            require(scan.result!=null&&scan.error==null,"ordinary standalone glass scan: "+scan.error);
            int count=0;VehicleWorld view=new VehicleWorld(world,scan.result);
            for(VehicleStructure.Cell cell:scan.result.cells)if(view.getTileEntity(cell.pos)!=null&&view.getTileEntity(cell.pos).getClass().getName().equals(GLASS)){
                count++;NBTTagCompound extra=cell.tileData();extra.setInteger("UnknownImportantData",42);boolean refused=false;
                try{MovingBlockSupport.requireExactTileData(new VehicleStructure.Cell(cell.pos,cell.state,extra),view.getTileEntity(cell.pos));}catch(IllegalArgumentException expected){refused=true;}
                require(refused,"unknown glass data remains refused");
            }
            require(count==2,"adjacent standalone glass both captured");
            if(turns==0){
                TileEntity live=world.getTileEntity(pane);require(scan.result.matches(world,scan.origin)&&scan.result.matchesExact(world,scan.origin),"fresh standalone preview matches the world");java.lang.reflect.Field link=live.getClass().getField("fusionReactor");Object reactor=link.getType().getConstructors()[0].newInstance(new Object[]{null});link.set(live,reactor);
                try{require(!scan.result.matches(world,scan.origin)&&!scan.result.matchesExact(world,scan.origin),"a new transient reactor link invalidates an existing preview");boolean refused=false;try{VehicleTransfer.assemble(world,scan.result,scan.origin,actor);}catch(IllegalArgumentException expected){refused=expected.getMessage().contains("Detach Reactor Glass");}require(refused,"final transfer rechecks the live reactor link");VehicleScan linked=new VehicleScan(world,seat);for(int n=0;n<1000&&!linked.step(128);n++);require(linked.result==null&&linked.error!=null&&linked.error.contains("Detach Reactor Glass"),"linked reactor member refused before changing the source: "+linked.error);require(world.getTileEntity(pane)==live,"linked refusal preserves source ownership");}finally{link.set(live,null);}
            }
            VehicleStructure cycle=scan.result;for(int n=0;n<4;n++){cycle=VehicleRotation.rotate(cycle,new VehicleWorld(world,cycle),1);MovingBlockSupport.validate(cycle,new VehicleWorld(world,cycle));}
            require(cycle.write().equals(scan.result.write()),"four rotations preserve all glass data");
            EntityGroundVehicle craft=VehicleTransfer.assemble(world,scan.result,scan.origin,actor);require(!craft.hover,"ordinary Taxi mode");
            EntityGroundVehicle reloaded=new EntityGroundVehicle(world);reloaded.readFromNBT(craft.writeToNBT(new NBTTagCompound()));require(reloaded.structure!=null&&reloaded.structure.write().equals(craft.structure.write()),"saved vehicle reload preserves glass");reloaded.signals.close();reloaded.setDead();
            craft.rotationYaw=turns*90;VehicleRotation.Plan plan=VehicleRotation.plan(craft);VehicleTransfer.park(world,craft,plan.origin,actor,false);
            last=plan.structure;
            int nativeGlass=0;for(VehicleStructure.Cell cell:plan.structure.cells)if(cell.state.getBlock()==glass.getBlock()&&world.getTileEntity(plan.origin.add(cell.pos))!=null&&world.getTileEntity(plan.origin.add(cell.pos)).getClass().getName().equals(GLASS)){nativeGlass++;require(world.getBlockState(plan.origin.add(cell.pos))==cell.state,"native parked glass state");}
            require(nativeGlass==2&&craft.isDead&&!VehicleTransfer.pending(world),"one native owner after parking at heading "+turns);
            VehicleScan again=new VehicleScan(world,plan.origin.add(plan.structure.seat));for(int n=0;n<1000&&!again.step(128);n++);require(again.result!=null&&again.error==null,"parked glass can be recaptured: "+again.error);
        }
        System.out.println("[vandorlabs] reactor-glass PASS standalone adjacent glass four parked headings vehicle save/reload recapture linked-reactor refusal unknown-data refusal native ownership");
        return last;
    }
    private MekanismReactorGlassChecks(){}
}
