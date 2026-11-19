package com.vandorlabs.gear;

import com.vandorlabs.blocks.BlockLargeProgrammableDoor;
import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.tiles.TileEntityLargeProgrammableDoor;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;

/** A wall-mounted joined platform sweeps through a horizontal hatch under real world ticks. */
@SideOnly(Side.CLIENT)
public final class HorizontalDoorArmLiveChecks {
    private static final BlockPos ROOT=new BlockPos(8,85,8);
    private static final BlockPos HATCH=ROOT.add(3,2,0);
    private static final BlockPos SUPPORT=ROOT.add(5,2,0);
    private static final EnumFacing MOUNT=EnumFacing.EAST;
    private static final EnumFacing HATCH_FACING=EnumFacing.WEST;
    private static int step,ticks;
    private static double refusedPhase;
    private static TileEntityLargeProgrammableDoor hatch;
    private static BlockLargeProgrammableDoor door;
    private static AxisAlignedBB aperture;
    private static double leafPlane;
    private static boolean outboundCrossing,returnCrossing,registered;
    private static volatile boolean complete;
    private static volatile Throwable failure;
    private HorizontalDoorArmLiveChecks(){}

    private static void require(boolean value,String message){
        if(!value)throw new IllegalStateException("horizontal-door arm step="+step+" tick="+ticks+" "+message);
    }
    public static boolean tick(Minecraft mc){
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        if(!registered){
            MinecraftForge.EVENT_BUS.register(new Observer(mc.getIntegratedServer()));
            registered=true;
        }
        if(failure!=null)throw new IllegalStateException("Horizontal Large Door arm server check",failure);
        return complete;
    }
    /** Observe every world step even when the software-rendered client skips frames. */
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public static final class Observer {
        private final MinecraftServer server;
        private Observer(MinecraftServer server){this.server=server;}
        @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){
            if(event.phase!=TickEvent.Phase.END||complete||failure!=null)return;
            try{
                complete=run(server.getWorld(0),server.getPlayerList().getPlayers().get(0));
            }catch(RuntimeException|LinkageError error){
                failure=error;
            }
            if(complete||failure!=null)MinecraftForge.EVENT_BUS.unregister(this);
        }
    }
    private static boolean run(WorldServer world,EntityPlayerMP player){
        GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);
        require(++ticks<450,"motion timeout, arm="+(arm==null?"missing":arm.progress()+" "+arm.error()));
        if(step==0){
            player.setGameType(net.minecraft.world.GameType.CREATIVE);
            player.capabilities.isFlying=true;player.sendPlayerAbilities();
            player.connection.setPlayerLocation(ROOT.getX()+2,ROOT.getY()+1,ROOT.getZ()-3,0,0);
            for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-3,-3,-4),ROOT.add(9,7,5)))world.setBlockToAir(at);
            for(int z=0;z<2;z++){
                BlockPos root=ROOT.south(z);
                world.setBlockState(root.west(),Blocks.STONE.getDefaultState(),3);
                ItemStack sections=new ItemStack(GearRegistry.GEAR_SWING_ARM,4);
                NBTTagCompound settings=new GearTileEntity().settings();
                settings.setBoolean("JoinArms",true);
                settings.setInteger("JoinRange",3);
                settings.setInteger("Direction",1);
                settings.setInteger("KinkDirection",1);
                settings.setBoolean("MiddleHinge",false);
                settings.setInteger("HingeMode",0);
                sections.setTagInfo("ModularGearSettings",settings);
                player.setHeldItem(EnumHand.MAIN_HAND,sections);
                for(int x=0;x<4;x++)place(world,player,root.east(x-1),MOUNT,"Rotating Arm section");
                player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(Blocks.DIAMOND_BLOCK));
                place(world,player,root.east(3),MOUNT,"joined platform payload");
            }

            for(int z=-1;z<=1;z++)world.setBlockState(SUPPORT.south(z),Blocks.STONE.getDefaultState(),3);
            door=(BlockLargeProgrammableDoor)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs","large_programmable_door"));
            player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(door));
            place(world,player,SUPPORT,HATCH_FACING,"horizontal Large Door");
            hatch=door.root(world,HATCH);
            require(hatch!=null&&hatch.isHorizontal(),"door root");
            hatch.configure(23,1,false,2,true,true,false,0,false);
            hatch.setPlacementDepth(0);
            leafPlane=world.getBlockState(HATCH).getBoundingBox(world,HATCH).minY+HATCH.getY()+1/16.0;
            aperture=new AxisAlignedBB(ROOT.getX()+2,leafPlane-.0001,ROOT.getZ()-1,
                    ROOT.getX()+5,leafPlane+.0001,ROOT.getZ()+2);
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
            step=1;
            return false;
        }
        require(arm!=null&&door.root(world,HATCH)==hatch&&door.complete(world,hatch),"door and arm owners remain installed");
        if(step==1){
            if(!arm.isOperational()||arm.columns().size()!=2)return false;
            require(arm.assembly().columnHeight()==4&&arm.mountDirection()==MOUNT,"joined wall-mounted geometry");
            click(world,player,ROOT,EnumFacing.WEST);step=2;return false;
        }
        if(step==2){
            if(!arm.error().contains("Motion blocked"))return false;
            require(arm.progress()>0&&arm.progress()<1&&!world.getBlockState(HATCH).getValue(BlockVandorDoor.OPEN),
                    "closed door blocks an in-progress stroke");
            refusedPhase=arm.progress();
            require(arm.payload()!=null&&!arm.payload().materialized()&&arm.payload().blocks.cells.size()==2,"blocked arm retains the entire captured surface");
            click(world,player,HATCH,EnumFacing.UP);
            require(world.getBlockState(HATCH).getValue(BlockVandorDoor.OPEN),"open hatch by its member");
            step=3;return false;
        }
        if(step==3||step==4){
            if(arm.payload()!=null&&!arm.payload().materialized()){
                boolean crossing=false;
                for(AxisAlignedBB box:arm.payload().boxes(arm,arm.progress()))if(box.intersects(aperture))crossing=true;
                if(step==3)outboundCrossing|=crossing;else returnCrossing|=crossing;
                require(world.isAirBlock(ROOT.east(4))&&world.isAirBlock(ROOT.east(4).south()),"captured surface has no duplicate native source");
            }
        }
        if(step==3){
            if(arm.progress()!=0||ArmTransactions.locked(world,ROOT))return false;
            require(outboundCrossing&&refusedPhase>arm.progress()&&arm.payload()!=null&&arm.payload().blocks.cells.size()==2,
                    "opened hatch passes the complete surface; crossing="+outboundCrossing+" phase="+arm.progress()+" refused="+refusedPhase
                            +" payload="+(arm.payload()==null?"missing":arm.payload().blocks.cells.size()+" materialized="+arm.payload().materialized())+" error="+arm.error());
            click(world,player,ROOT,EnumFacing.WEST);step=4;return false;
        }
        if(arm.progress()!=1||ArmTransactions.locked(world,ROOT)||arm.payload()==null||!arm.payload().materialized())return false;
        require(returnCrossing&&arm.payload().placedCells(arm).size()==2&&arm.payload().matchesPlaced(arm),"reverse restores each original surface once");
        for(int z=0;z<2;z++)require(world.getBlockState(ROOT.east(4).south(z)).getBlock()==Blocks.DIAMOND_BLOCK
                &&world.getBlockState(ROOT.west().south(z)).getBlock()==Blocks.STONE,"source surface and wall support restored");
        System.out.println("[vandorlabs] horizontal-door-arm PASS closed obstruction/open passage/joined surface/real world ticks/journals/reversal");
        return true;
    }
    private static void place(WorldServer world,EntityPlayerMP player,BlockPos support,EnumFacing face,String kind){
        require(player.getHeldItemMainhand().onItemUse(player,world,support,EnumHand.MAIN_HAND,face,.5F,.5F,.5F)==EnumActionResult.SUCCESS,
                "normal item placement of "+kind+" against "+support);
    }
    private static void click(WorldServer world,EntityPlayerMP player,BlockPos pos,EnumFacing face){
        require(world.getBlockState(pos).getBlock().onBlockActivated(world,pos,world.getBlockState(pos),player,EnumHand.MAIN_HAND,face,.5F,.5F,.5F),
                "normal block activation at "+pos);
    }
}
