package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.canopy.LargeCanopySettings;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.io.File;
import java.util.concurrent.Future;

/** Real latch, physical neighbor notifications and authoritative client animation endpoints. */
public final class CanopyPowerLoopRuntimeChecks {
    private static final BlockPos ROOT=new BlockPos(1102,91,258),HELPER=ROOT.east(2),LEVER=HELPER.north();
    private static final int CHANNEL=1801;
    private static int stage=-1,ticks,hold,stageTicks;
    private static Future<?> diagnostic;
    private static Future<?> pending;
    private static void require(boolean value,String what){if(!value)throw new IllegalStateException("canopy power loop: "+what);}
    private static TileEntityCanopy canopy(World world){return TileEntityCanopy.at(world,ROOT);}
    private static void clear(World world){for(BlockPos p:BlockPos.getAllInBox(ROOT.add(-3,-2,-5),ROOT.add(9,7,8))){world.getChunkFromBlockCoords(p);world.setBlockToAir(p);}}
    private static void setup(World world){
        clear(world);
        BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:large_front_canopy_glass");
        LargeCanopySettings settings=new LargeCanopySettings();settings.depth=24;settings.scale=50;settings.hinge=2;settings.openable=true;settings.redstone=true;settings.channels=ChannelList.of(CHANNEL);
        ItemStack item=new ItemStack(block);NBTTagCompound data=new NBTTagCompound();settings.write(data);item.setTagCompound(data);
        IBlockState state=block.getDefaultState().withProperty(BlockCanopy.FACING,EnumFacing.NORTH);
        world.setBlockState(ROOT,state,3);block.onBlockPlacedBy(world,ROOT,state,null,item);
        require(canopy(world)!=null&&canopy(world).cells().contains(HELPER),"release fixture owns physical neighbor cell");
        require(world.isAirBlock(LEVER),"lever lies outside canopy footprint");
        for(BlockPos floor:BlockPos.getAllInBox(ROOT.add(-2,-1,-4),ROOT.add(3,-1,-1)))world.setBlockState(floor,Blocks.STONE.getDefaultState(),3);
        world.setBlockState(LEVER.down(),Blocks.STONE.getDefaultState(),3);
        BlockIndustrialLever lever=(BlockIndustrialLever)Block.getBlockFromName("vandorlabs:industrial_power_lever");
        world.setBlockState(LEVER,lever.getDefaultState().withProperty(BlockIndustrialLever.FLOOR,true),3);
        ((TileEntityRedstoneChannel)world.getTileEntity(LEVER)).setRedstoneChannels(ChannelList.of(CHANNEL));
    }
    private static void closedOwnership(World world){
        TileEntityCanopy root=canopy(world);require(root!=null&&!root.hasReleasedCells(),"closed endpoint has no released helpers");
        for(BlockPos cell:root.cells()){
            net.minecraft.tileentity.TileEntity tile=world.getTileEntity(cell);
            require(tile instanceof TileEntityCanopy && ROOT.equals(((TileEntityCanopy)tile).anchor),"closed endpoint helper is initialized and belongs to exact root: "+cell+" tile="+tile);
        }
    }
    private static void lever(World world,EntityPlayerMP actor,boolean high){
        IBlockState state=world.getBlockState(LEVER);
        if(state.getValue(BlockIndustrialLever.POWERED)!=high)((BlockIndustrialLever)state.getBlock()).onBlockActivated(world,LEVER,state,actor,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);
        require(((TileEntityRedstoneChannel)world.getTileEntity(LEVER)).isLocalOn()==high,"actual lever latch matches requested state");
        require((RedstoneChannels.level(world,CHANNEL)>0)==high,"actual channel bank has requested level");
    }
    /** Server coverage intentionally sends real block callbacks, not a synthetic channel source. */
    public static void run(World world,EntityPlayerMP actor){
        long time=world.getTotalWorldTime();boolean sneak=actor.isSneaking();actor.setSneaking(false);
        try{
            setup(world);TileEntityCanopy tile=canopy(world);
            for(int cycle=0;cycle<3;cycle++){
                lever(world,actor,true);advance(world,tile,24,true);
                require(tile.progress==1&&world.isAirBlock(HELPER),"powered canopy releases its helper");
                lever(world,actor,false);advance(world,tile,80,false);
                require(tile.progress==0&&!tile.targetOpen,"real lever OFF stays closed through helper restoration and callbacks");
            }
            lever(world,actor,true);advance(world,tile,24,true);
            tile.toggle();advance(world,tile,80,false);
            require(tile.progress==0&&!tile.targetOpen&&RedstoneChannels.level(world,CHANNEL)==15,"manual close survives unchanged HIGH input");
            tile.readFromNBT(tile.writeToNBT(new NBTTagCompound()));tile.refreshPower();
            advance(world,tile,24,false);require(tile.progress==0,"manual override persists across NBT reload with same input");
            lever(world,actor,false);advance(world,tile,8,false);lever(world,actor,true);advance(world,tile,24,true);
            tile.large.activateOnPower=false;tile.refreshPower();advance(world,tile,24,false);require(tile.progress==0,"inversion change immediately reapplies current input");
            lever(world,actor,false);advance(world,tile,24,true);require(tile.progress==1,"inverted OFF opens normally");
            System.out.println("[vandorlabs][reprolab] canopy-power-loop-world PASS actual-lever physical-neighbor three-OFF-cycles manual-HIGH-close reload inversion");
        }finally{clear(world);world.setTotalWorldTime(time);actor.setSneaking(sneak);}
    }
    private static void advance(World world,TileEntityCanopy tile,int count,boolean opening){
        float prior=tile.progress;
        for(int n=0;n<count;n++){
            world.setTotalWorldTime(world.getTotalWorldTime()+1);tile.refreshPower();tile.update();
            require(tile.targetOpen==opening,"unchanged input must not reverse target at tick "+n);
            require(opening?tile.progress>=prior:tile.progress<=prior,"server progress never restarts during one command");prior=tile.progress;
        }
    }
    private static void task(Minecraft mc,java.util.function.BiConsumer<World,EntityPlayerMP> action){
        pending=mc.getIntegratedServer().addScheduledTask(()->action.accept(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID())));
    }
    /** Returns true only after real server ticks and packets have completed all close cycles. */
    public static boolean tick(Minecraft mc,File output)throws Exception{
        if(stage==-1){stage=0;ticks=hold=0;task(mc,(world,actor)->{run(world,actor);setup(world);actor.setSneaking(false);actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.connection.setPlayerLocation(ROOT.getX()+.5,ROOT.getY(),ROOT.getZ()-2,0,0);lever(world,actor,true);});return false;}
        require(++ticks<2200,"normal-world client fixture timed out at stage "+stage);
        if(pending!=null){if(!pending.isDone())return false;pending.get();pending=null;}
        if(stage==7){System.out.println("[vandorlabs][reprolab] canopy-power-loop-client PASS actual-lever-OFF actual-right-click-HIGH blocked-close foreign-removal authoritative-packets stable-endpoints normal-world-ticks");stage=8;return true;}
        TileEntityCanopy tile=canopy(mc.world);if(tile==null)return false;
        if(stage==1){
            if(++stageTicks==180){
                System.out.println("[vandorlabs][reprolab] canopy-loop diagnostic CLIENT progress="+tile.progress+" target="+tile.targetOpen+" wait="+tile.waitingForReservations+" released="+tile.hasReleasedCells()+" pos="+mc.player.getPositionVector()+" paused="+mc.isGamePaused()+" screen="+mc.currentScreen);
                diagnostic=mc.getIntegratedServer().addScheduledTask(()->{
                    World w=mc.getIntegratedServer().getWorld(0);TileEntityCanopy root=canopy(w);
                    System.out.println("[vandorlabs][reprolab] canopy-loop diagnostic SERVER root="+(root==null?"missing":("progress="+root.progress+" target="+root.targetOpen+" wait="+root.waitingForReservations+" released="+root.hasReleasedCells()+" draws="+root.draws()+" invalid="+root.isInvalid()+" local="+root.hasLocalRedstoneSignal()+" NBT="+root.writeToNBT(new NBTTagCompound())))+" bank="+RedstoneChannels.level(w,CHANNEL)+" lever="+w.getBlockState(LEVER)+" helper="+w.getBlockState(HELPER)+" time="+w.getTotalWorldTime());
                });
            }
            if(stageTicks>=200){if(diagnostic!=null&&!diagnostic.isDone())return false;if(diagnostic!=null)diagnostic.get();require(false,"OFF close did not settle: client progress="+tile.progress+" target="+tile.targetOpen+" wait="+tile.waitingForReservations+" released="+tile.hasReleasedCells()+" pos="+mc.player.getPositionVector());}
        }

        if(stage==0&&tile.targetOpen&&tile.progress==1){task(mc,(w,p)->lever(w,p,false));stage=1;hold=0;stageTicks=0;}
        else if(stage==1){if(tile.progress==0&&!tile.targetOpen){if(++hold>=50){task(mc,(w,p)->{require(canopy(w).progress==0&&!canopy(w).targetOpen,"authoritative OFF endpoint remains closed");closedOwnership(w);lever(w,p,true);});stage=2;hold=0;}}else if(hold>0)require(false,"client OFF endpoint restarted after closing");}
        else if(stage==2&&tile.targetOpen&&tile.progress==1){
            mc.player.setSneaking(false);mc.player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);require(mc.playerController.processRightClickBlock(mc.player,mc.world,ROOT,EnumFacing.UP,new Vec3d(ROOT).addVector(.5,.5,.5),EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"actual client right-click accepted by canopy");stage=3;hold=0;
        }
        else if(stage==3){if(tile.progress==0&&!tile.targetOpen){if(++hold>=50){task(mc,(w,p)->{require(canopy(w).progress==0&&!canopy(w).targetOpen,"authoritative manual HIGH close remains closed");closedOwnership(w);lever(w,p,false);lever(w,p,true);});stage=4;hold=0;}}else if(hold>0)require(false,"client manual close endpoint restarted");}
        else if(stage==4&&tile.targetOpen&&tile.progress==1){task(mc,(w,p)->{require(w.isAirBlock(HELPER),"open helper available for foreign obstruction");w.setBlockState(HELPER,Blocks.GOLD_BLOCK.getDefaultState(),3);lever(w,p,false);});stage=5;hold=0;}
        else if(stage==5&&!tile.targetOpen&&tile.waitingForReservations){require(tile.progress==1,"blocked close stays visibly open");if(++hold>=30){task(mc,(w,p)->{require(w.getBlockState(HELPER).getBlock()==Blocks.GOLD_BLOCK,"foreign obstruction preserved");w.setBlockToAir(HELPER);});stage=6;hold=0;}}
        else if(stage==6){if(tile.progress==0&&!tile.targetOpen){if(++hold>=50){task(mc,(w,p)->{require(canopy(w).progress==0&&!canopy(w).targetOpen,"authoritative obstacle-release endpoint remains closed");closedOwnership(w);clear(w);});stage=7;}}else if(hold>0)require(false,"client released-obstacle close restarted");}
        return stage==8;
    }
}
