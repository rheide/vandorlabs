package com.vandorlabs.gear;

import com.vandorlabs.blocks.BlockLargeProgrammableDoor;
import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.tiles.TileEntityLargeProgrammableDoor;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;

/**
 * An Extender built through an open joined 2x1 vertical Large Door carries a two-block
 * programmable Surface through the doorway. The door group stays open while the arm runs
 * through it.
 */
@SideOnly(Side.CLIENT)
public final class VerticalDoorArmLiveChecks {
    private static final EnumFacing FACE=EnumFacing.WEST,WIDTH=FACE.rotateYCCW();
    private static final BlockPos ANCHOR=new BlockPos(40,85,8);
    /** Column at the middle row of the first module's far edge, next to the joined seam. */
    private static final BlockPos ROOT=ANCHOR.offset(WIDTH,2).up().west(2);
    private static final BlockPos DOORWAY=ROOT.east(2),TIP=ROOT.east(5);
    private static int step,ticks;
    private static TileEntityLargeProgrammableDoor root;
    private static BlockLargeProgrammableDoor door;
    private static Block surface;
    private static boolean registered,crossed;
    private static volatile boolean complete;
    private static volatile Throwable failure;
    private VerticalDoorArmLiveChecks(){}

    private static void require(boolean value,String message){
        if(!value)throw new IllegalStateException("vertical-door arm step="+step+" tick="+ticks+" "+message);
    }
    public static boolean tick(Minecraft mc){
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        if(!registered){MinecraftForge.EVENT_BUS.register(new Observer(mc.getIntegratedServer()));registered=true;}
        if(failure!=null)throw new IllegalStateException("Vertical Large Door arm server check",failure);
        return complete;
    }
    @SideOnly(Side.CLIENT)
    public static final class Observer {
        private final MinecraftServer server;
        private Observer(MinecraftServer server){this.server=server;}
        @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){
            if(event.phase!=TickEvent.Phase.END||complete||failure!=null)return;
            try{complete=run(server.getWorld(0),server.getPlayerList().getPlayers().get(0));}
            catch(RuntimeException|LinkageError error){failure=error;}
            if(complete||failure!=null)MinecraftForge.EVENT_BUS.unregister(this);
        }
    }
    private static boolean surfaceAt(WorldServer world,BlockPos at){
        return world.getBlockState(at).getBlock()==surface&&world.getBlockState(at.offset(WIDTH)).getBlock()==surface;
    }
    private static String row(WorldServer world){
        StringBuilder out=new StringBuilder();
        for(int x=-1;x<=6;x++)for(int y=0;y<2;y++){BlockPos at=ROOT.east(x).up(y);if(!world.isAirBlock(at))out.append(" ").append(x).append(",").append(y).append("=").append(world.getBlockState(at).getBlock().getRegistryName().getResourcePath());}
        return out.toString();
    }
    private static boolean run(WorldServer world,EntityPlayerMP player){
        GearTileEntity arm=world.getTileEntity(ROOT) instanceof GearTileEntity?((GearTileEntity)world.getTileEntity(ROOT)).loadedController():null;
        require(++ticks<1200,"motion timeout, arm="+(arm==null?"missing":arm.progress()+" "+arm.error()));
        if(step==0){
            player.setGameType(net.minecraft.world.GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();
            player.connection.setPlayerLocation(ANCHOR.getX()-3,ANCHOR.getY()+1,ANCHOR.getZ()-4,0,0);
            for(BlockPos at:BlockPos.getAllInBox(ANCHOR.add(-8,-1,-4),ANCHOR.add(8,6,10)))world.setBlockState(at,at.getY()==ANCHOR.getY()-1?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
            // Two joined vertical modules placed through the ordinary item path.
            door=(BlockLargeProgrammableDoor)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs","large_programmable_door"));
            ItemStack stack=new ItemStack(door,2);player.setHeldItem(EnumHand.MAIN_HAND,stack);
            float yaw=player.rotationYaw;player.rotationYaw=FACE.getOpposite().getHorizontalAngle();
            for(int x=0;x<2;x++){
                BlockPos at=ANCHOR.offset(WIDTH,3*x);
                require(stack.getItem().onItemUse(player,world,at.offset(WIDTH).down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"door module "+x);
                TileEntityLargeProgrammableDoor unit=door.root(world,at);require(unit!=null&&unit.getPos().equals(at),"module root "+x);
                if(x==0)unit.setJoin(true);
                door.joinChanged(world,unit);
            }
            player.rotationYaw=yaw;
            root=door.root(world,ANCHOR);
            require(root!=null&&!root.isHorizontal()&&root.joinedGroup().members.size()==2,"joined 2x1 vertical group");
            door.toggleDoor(world,ANCHOR,true);
            for(int z=0;z<6;z++)for(int y=0;y<3;y++){BlockPos at=ANCHOR.offset(WIDTH,z).up(y);require(BlockLargeProgrammableDoor.isOpenPassage(world,at,world.getBlockState(at)),"all six columns of the joined aperture are open z="+z+" y="+y);}
            // Two joined Extender columns on both sides of the open doorway, straddling the
            // seam between the door modules and carrying a two-block-wide Surface.
            surface=Block.getBlockFromName("vandorlabs:turntable_surface");
            for(int c=0;c<2;c++){
                BlockPos base=ROOT.offset(WIDTH,c);
                world.setBlockState(base.west(),Blocks.STONE.getDefaultState(),3);
                for(int n:new int[]{0,1,3,4})require(((ItemBlock)Item.getItemFromBlock(GearRegistry.GEAR_EXTENDER)).placeBlockAt(new ItemStack(GearRegistry.GEAR_EXTENDER),player,world,base.east(n),EnumFacing.EAST,.5F,.5F,.5F,GearRegistry.GEAR_EXTENDER.getDefaultState()),"Extender section "+c+"/"+n);
                world.setBlockState(TIP.offset(WIDTH,c),surface.getDefaultState(),3);
            }
            for(int c=0;c<2;c++){
                GearTileEntity member=(GearTileEntity)world.getTileEntity(ROOT.offset(WIDTH,c));member.operator(player);
                require(member.rebuild(),"column forms through the open doorway "+c+" "+member.error());
                net.minecraft.nbt.NBTTagCompound settings=member.settings();settings.setBoolean("JoinArms",true);member.configure(settings);
            }
            GearTileEntity built=((GearTileEntity)world.getTileEntity(ROOT)).loadedController();
            require(built!=null&&built.rebuild()&&built.columns().size()==2,"joined columns form through the doorway "+(built==null?"":built.error()));
            require(built.assembly().columnHeight()==5&&!built.members().contains(DOORWAY)&&!built.members().contains(DOORWAY.offset(WIDTH)),"door cells join the columns without becoming members height="+built.assembly().columnHeight());
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
            step=1;return false;
        }
        require(arm!=null,"arm owner remains installed");
        require(door.root(world,ANCHOR)==root&&BlockLargeProgrammableDoor.isOpenPassage(world,DOORWAY,world.getBlockState(DOORWAY)),"door group stays installed and open");
        if(step==1){
            if(!arm.isOperational())return false;
            require(ArmPassages.occupied(world,DOORWAY)&&door.armPassesThrough(world,root),"door knows the arm runs through it");
            door.toggleDoor(world,ANCHOR,false);
            require(world.getBlockState(ANCHOR).getValue(BlockVandorDoor.OPEN)&&world.getBlockState(ANCHOR.offset(WIDTH,3)).getValue(BlockVandorDoor.OPEN),"closing is refused while the arm runs through the group");
            require(!door.removedByPlayer(world.getBlockState(DOORWAY),world,DOORWAY,player,false)&&world.getBlockState(DOORWAY).getBlock()==door,"breaking is refused while the arm runs through the group");
            arm.operator(player);arm.target(false);step=2;return false;
        }
        if(step==2||step==3){
            if(arm.payload()!=null&&!arm.payload().materialized())
                for(AxisAlignedBB box:arm.payload().boxes(arm,arm.progress()))if(box.intersects(new AxisAlignedBB(DOORWAY)))crossed=true;
        }
        if(step==2){
            if(arm.progress()!=0||ArmTransactions.locked(world,ROOT)||arm.payload()==null||!arm.payload().materialized())return false;
            require(crossed&&arm.error().isEmpty(),"retraction carries the Surface back through the doorway error="+arm.error());
            require(surfaceAt(world,ROOT.east(1))&&!surfaceAt(world,TIP),"Surface lands behind the doorway row="+row(world));
            crossed=false;arm.operator(player);arm.target(true);step=3;return false;
        }
        if(arm.progress()!=1||ArmTransactions.locked(world,ROOT)||arm.payload()==null||!arm.payload().materialized())return false;
        require(crossed&&arm.error().isEmpty()&&surfaceAt(world,TIP),"extension carries the Surface out through the doorway error="+arm.error());
        require(world.getBlockState(DOORWAY).getBlock()==door&&arm.assembly().columnHeight()==5,"door cell and column are unchanged");
        System.out.println("[vandorlabs] vertical-door-arm PASS joined 2x1 aperture/column through door/held open/break refused/two-block Surface both ways");
        return true;
    }
}
