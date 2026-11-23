package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.drives.MaxwellRuntimeChecks;
import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.tiles.TileEntityHexTank;
import java.util.concurrent.Future;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.*;

/** Measure all three fluid owners while a real redstone-controlled Thermal servo refuels a drive. */
final class MaxwellServoChecks {
    private static final EnumFacing SIDE=EnumFacing.NORTH;
    private static TileEntityConstructor engine;
    private static TileEntityHexTank tank;
    private static BlockPos servo;
    private static long start;
    private static int stable;
    private static boolean longerNetwork;
    private static Future<Boolean> step;
    static boolean tick(Minecraft mc)throws Exception {
        if(step==null){
            step=mc.getIntegratedServer().callFromMainThread(()->sample(mc.getIntegratedServer().getPlayerList().getPlayers().get(0)));
            return false;
        }
        if(!step.isDone())return false;
        boolean finished=step.get();step=null;
        if(finished)System.out.println("[vandorlabs][reprolab] utility26-focused PASS redstone servo refuelling");
        return finished;
    }
    private static boolean sample(EntityPlayerMP player)throws Exception{
        WorldServer world=player.getServerWorld();
        if(engine==null){
            BlockPos root=player.getPosition().up(8);
            engine=MaxwellRuntimeChecks.assemble(world,root,3,SIDE,0,0);
            BlockPos inlet=engine.maxwellPort(SIDE);
            servo=inlet.offset(SIDE,2);
            BlockPos storage=servo.offset(SIDE);
            Block hex=Block.getBlockFromName("vandorlabs:hexagonal_fuel_tank");
            world.setBlockState(storage,hex.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,SIDE),3);
            tank=(TileEntityHexTank)world.getTileEntity(storage);
            Fluid fuel=FluidRegistry.getFluid("refined_fuel");
            if(fuel==null)throw new IllegalStateException("Install Thermal Foundation refined fuel");
            tank.fill(new FluidStack(fuel,16000),true);
            placeDuct(world,player,inlet.offset(SIDE));
            placeDuct(world,player,servo);
            ItemStack item=new ItemStack(Item.getByNameOrId("thermaldynamics:servo"));
            player.setHeldItem(EnumHand.MAIN_HAND,item);
            if(item.onItemUse(player,world,storage,EnumHand.MAIN_HAND,SIDE.getOpposite(),.5f,.5f,.5f)!=EnumActionResult.SUCCESS)
                throw new IllegalStateException("Install Basic Servo on tank side of fluiduct");
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
            world.setBlockState(servo.up(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);
            start=world.getTotalWorldTime();
            return false;
        }
        Object unit=CopyThermalLiveChecks.ductUnit(world.getTileEntity(servo),"fluid");
        Object grid=unit.getClass().getMethod("getGrid").invoke(unit);
        if(grid==null&&world.getTotalWorldTime()-start<20)return false;
        IFluidTank buffer=(IFluidTank)grid.getClass().getField("myTank").get(grid);
        int stored=tank.tank.getFluidAmount(),inDrive=engine.maxwellFuel(),inDuct=buffer.getFluidAmount();
        String state="tank="+stored+" drive="+inDrive+" duct="+inDuct+" capacity="+buffer.getCapacity();
        if(stored+inDrive+inDuct!=16000)throw new IllegalStateException("Lost fluid: "+state);
        int expectedDuct=longerNetwork?15000:6000;
        if(inDrive==1000&&inDuct==expectedDuct&&stored==15000-expectedDuct){
            if(++stable<15)return false;
            System.out.println("[vandorlabs] Maxwell servo PASS "+state+" total=16000");
            if(longerNetwork)return true;
            for(int i=1;i<=3;i++)placeDuct(world,player,servo.east(i));
            longerNetwork=true;stable=0;start=world.getTotalWorldTime();
            return false;
        }
        stable=0;
        if(world.getTotalWorldTime()-start>320)throw new IllegalStateException("Servo transfer did not settle: "+state);
        return false;
    }
    private static void placeDuct(WorldServer world,EntityPlayerMP player,BlockPos at){
        Block duct=Block.getBlockFromName("thermaldynamics:duct_16");
        world.setBlockState(at,duct.getStateFromMeta(0),3);
        duct.onBlockPlacedBy(world,at,world.getBlockState(at),player,new ItemStack(duct));
    }
    private MaxwellServoChecks(){}
}
