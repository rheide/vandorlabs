package com.vandorlabs.gear;

import com.vandorlabs.blocks.BlockLargeProgrammableDoor;
import com.vandorlabs.blocks.BlockTurntableSurface;
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
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Reported workflow: a vertical Extender carries a 3x3 Turntable Surface. With the arm
 * retracted, a horizontal 3x3 Large Door (no frame, no panel) is laid across the column above
 * the Surface and opened. Extending must carry the whole Surface through the open door, past
 * the leaves' remaining edges, and retracting must bring it back.
 */
@SideOnly(Side.CLIENT)
public final class DoorSurfaceArmLiveChecks {
    private static final BlockPos FLOOR=new BlockPos(70,85,40);
    private static final BlockPos ROOT=FLOOR.up(),TOP=ROOT.up(5),HATCH=ROOT.up(3),WALL=HATCH.west(2);
    private static int step,ticks;
    private static BlockPos retracted;
    private static BlockLargeProgrammableDoor door;
    private static TileEntityLargeProgrammableDoor root;
    private static boolean registered;
    private static volatile boolean complete;
    private static volatile Throwable failure;
    private DoorSurfaceArmLiveChecks(){}
    private static void require(boolean value,String message,WorldServer world){
        if(!value)throw new IllegalStateException("door-surface-arm step="+step+" tick="+ticks+" "+message+" "+state(world));
    }
    public static boolean tick(Minecraft mc){
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        if(!registered){MinecraftForge.EVENT_BUS.register(new Observer(mc.getIntegratedServer()));registered=true;}
        if(failure!=null)throw new IllegalStateException("Door surface arm server check",failure);
        return complete;
    }
    @SideOnly(Side.CLIENT)
    public static final class Observer {
        private final MinecraftServer server;
        private Observer(MinecraftServer server){this.server=server;}
        @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){
            if(event.phase!=TickEvent.Phase.END||complete||failure!=null||server.getPlayerList().getPlayers().isEmpty())return;
            try{complete=run(server.getWorld(0),server.getPlayerList().getPlayers().get(0));}
            catch(RuntimeException|LinkageError error){failure=error;}
            if(complete||failure!=null)MinecraftForge.EVENT_BUS.unregister(this);
        }
    }
    private static GearTileEntity arm(WorldServer world){
        return world.getTileEntity(ROOT) instanceof GearTileEntity?((GearTileEntity)world.getTileEntity(ROOT)).loadedController():null;
    }
    private static boolean settled(WorldServer world,GearTileEntity arm,double progress){
        return arm!=null&&arm.progress()==progress&&!ArmTransactions.locked(world,ROOT)&&arm.payload()!=null&&arm.payload().materialized();
    }
    private static String state(WorldServer world){
        GearTileEntity arm=arm(world);
        return "arm="+(arm==null?"missing":"progress="+arm.progress()+" target="+arm.target()+" error="+arm.error()+" payload="+(arm.payload()==null?"none":arm.payload().materialized()?"native":"captured"))
                +(root==null?"":" door open="+world.getBlockState(root.getPos()).getValue(BlockVandorDoor.OPEN)+" framed="+root.isFramed()+" panel="+root.hasPanel());
    }
    private static boolean surfaceAt(WorldServer world,BlockPos centre){
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(!(world.getBlockState(centre.add(x,0,z)).getBlock() instanceof BlockTurntableSurface))return false;
        return true;
    }
    private static boolean run(WorldServer world,EntityPlayerMP player){
        GearTileEntity arm=arm(world);
        require(++ticks<2400,"bounded fixture",world);
        if(step==0){
            player.setGameType(net.minecraft.world.GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();
            player.connection.setPlayerLocation(FLOOR.getX()-5,FLOOR.getY()+4,FLOOR.getZ()-6,-30,25);
            for(BlockPos at:BlockPos.getAllInBox(FLOOR.add(-6,0,-6),FLOOR.add(6,9,6)))world.setBlockState(at,at.getY()==FLOOR.getY()?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
            for(int n=0;n<5;n++)require(((ItemBlock)Item.getItemFromBlock(GearRegistry.GEAR_EXTENDER)).placeBlockAt(new ItemStack(GearRegistry.GEAR_EXTENDER),player,world,ROOT.up(n),EnumFacing.UP,.5F,.5F,.5F,GearRegistry.GEAR_EXTENDER.getDefaultState()),"Extender section "+n,world);
            Block surface=Block.getBlockFromName("vandorlabs:turntable_surface");
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.setBlockState(TOP.add(x,0,z),surface.getDefaultState().withProperty(BlockTurntableSurface.FACING,EnumFacing.UP),3);
            GearTileEntity built=(GearTileEntity)world.getTileEntity(ROOT);built.operator(player);require(built.rebuild(),"Extender forms "+built.error(),world);
            step=1;return false;
        }
        require(arm!=null,"arm owner remains installed",world);
        if(step==1){
            if(!arm.isOperational())return false;
            arm.operator(player);arm.target(false);step=2;return false;
        }
        if(step==2){
            if(!settled(world,arm,0))return false;
            require(arm.payload().placedCells(arm).size()==9,"the arm carries the whole 3x3 Surface, cells="+arm.payload().placedCells(arm).size(),world);
            for(int y=1;y<3&&retracted==null;y++)if(surfaceAt(world,ROOT.up(y)))retracted=ROOT.up(y);
            require(retracted!=null,"retracted Surface sits below the hatch row",world);
            for(int z=-1;z<=1;z++)world.setBlockState(WALL.add(0,0,z),Blocks.STONE.getDefaultState(),3);
            door=(BlockLargeProgrammableDoor)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs","large_programmable_door"));
            ItemStack stack=new ItemStack(door);player.setHeldItem(EnumHand.MAIN_HAND,stack);
            require(stack.getItem().onItemUse(player,world,WALL,EnumHand.MAIN_HAND,EnumFacing.EAST,1,.5F,.5F)==EnumActionResult.SUCCESS,"horizontal door placed above the retracted Surface",world);
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
            root=door.root(world,HATCH);
            require(root!=null&&root.isHorizontal()&&door.complete(world,root),"horizontal door spans the column",world);
            // The reporter disabled the frame and the door panel.
            net.minecraft.nbt.NBTTagCompound settings=root.itemSettings();settings.setBoolean("SpaceFramed",false);settings.setBoolean("SpaceDoorPanel",false);root.applyItemSettings(settings);
            require(!root.isFramed()&&!root.hasPanel(),"door has no frame and no panel",world);
            door.toggleDoor(world,root.getPos(),true);
            require(world.getBlockState(root.getPos()).getValue(BlockVandorDoor.OPEN),"door opens",world);
            step=3;ticks=0;return false;
        }
        if(step==3){
            if(!arm.isOperational()||ticks<5)return false;
            arm.operator(player);arm.target(true);step=4;ticks=0;return false;
        }
        if(step==4){
            require(!arm.error().toLowerCase().contains("block"),"open door lets the 3x3 Surface through",world);
            if(!settled(world,arm,1)){require(ticks<300,"Surface reaches the top through the open door",world);return false;}
            require(surfaceAt(world,TOP),"whole 3x3 Surface above the door",world);
            arm.operator(player);arm.target(false);step=5;ticks=0;return false;
        }
        if(step==5){
            require(!arm.error().toLowerCase().contains("block"),"open door lets the 3x3 Surface back down",world);
            if(!settled(world,arm,0)){require(ticks<300,"Surface returns through the open door",world);return false;}
            require(surfaceAt(world,retracted),"whole 3x3 Surface back below the door",world);
            door.toggleDoor(world,root.getPos(),false);
            require(!world.getBlockState(root.getPos()).getValue(BlockVandorDoor.OPEN),"door closes once the Surface is clear",world);
            System.out.println("[vandorlabs] door-surface-arm PASS 3x3 Surface passes an open unframed panel-less horizontal door both ways/door closes after");
            return true;
        }
        return false;
    }
}
