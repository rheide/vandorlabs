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
 * The reported workflow: build a vertical Extender carrying a chest, retract it, then place a
 * horizontal Large Door over its hidden sections. A closed door blocks extension; an open
 * door lets the arm through; the door closes again once the arm is retracted, and removing the
 * door restores the arm section. Also joins doors by clicking their edges into a 2x2 group.
 */
@SideOnly(Side.CLIENT)
public final class HatchArmLiveChecks {
    private static final BlockPos FLOOR=new BlockPos(70,85,8);
    /** Five sections above the floor; the chest sits on top. */
    private static final BlockPos ROOT=FLOOR.up(),CHEST=ROOT.up(5);
    /** The hatch row crossing the column, three sections up. */
    private static final BlockPos HATCH=ROOT.up(3);
    private static final BlockPos WALL=HATCH.west(2);
    private static int step,ticks;
    private static BlockLargeProgrammableDoor door;
    private static TileEntityLargeProgrammableDoor root;
    private static boolean registered;
    private static volatile boolean complete;
    private static volatile Throwable failure;
    private HatchArmLiveChecks(){}
    private static void require(boolean value,String message){
        if(!value)throw new IllegalStateException("hatch-arm step="+step+" tick="+ticks+" "+message);
    }
    public static boolean tick(Minecraft mc){
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        if(!registered){MinecraftForge.EVENT_BUS.register(new Observer(mc.getIntegratedServer()));registered=true;}
        if(failure!=null)throw new IllegalStateException("Hatch arm server check",failure);
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
    private static GearTileEntity arm(WorldServer world){
        return world.getTileEntity(ROOT) instanceof GearTileEntity?((GearTileEntity)world.getTileEntity(ROOT)).loadedController():null;
    }
    private static boolean settled(WorldServer world,GearTileEntity arm,double progress){
        return arm!=null&&arm.progress()==progress&&!ArmTransactions.locked(world,ROOT)&&arm.payload()!=null&&arm.payload().materialized();
    }
    private static boolean open(WorldServer world){return world.getBlockState(root.getPos()).getValue(BlockVandorDoor.OPEN);}
    private static boolean run(WorldServer world,EntityPlayerMP player){
        GearTileEntity arm=arm(world);
        require(++ticks<2400,"bounded fixture arm="+(arm==null?"missing":arm.progress()+" target="+arm.target()+" error="+arm.error()));
        if(step==0){
            player.setGameType(net.minecraft.world.GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();
            player.connection.setPlayerLocation(FLOOR.getX()-4,FLOOR.getY()+3,FLOOR.getZ()-6,0,0);
            for(BlockPos at:BlockPos.getAllInBox(FLOOR.add(-6,0,-6),FLOOR.add(6,9,14)))world.setBlockState(at,at.getY()==FLOOR.getY()?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
            for(int n=0;n<5;n++)require(((ItemBlock)Item.getItemFromBlock(GearRegistry.GEAR_EXTENDER)).placeBlockAt(new ItemStack(GearRegistry.GEAR_EXTENDER),player,world,ROOT.up(n),EnumFacing.UP,.5F,.5F,.5F,GearRegistry.GEAR_EXTENDER.getDefaultState()),"Extender section "+n);
            world.setBlockState(CHEST,Blocks.CHEST.getDefaultState(),3);
            GearTileEntity built=(GearTileEntity)world.getTileEntity(ROOT);built.operator(player);require(built.rebuild(),"Extender forms "+built.error());
            step=1;return false;
        }
        require(arm!=null,"arm owner remains installed");
        if(step==1){
            if(!arm.isOperational())return false;
            arm.operator(player);arm.target(false);step=2;return false;
        }
        if(step==2){
            if(!settled(world,arm,0))return false;
            require(world.getBlockState(HATCH).getBlock() instanceof GearBlock&&!arm.occupiesNow(HATCH),"retracted arm leaves a hidden section in the hatch row");
            // A wall beside the column supports a horizontal door laid across it.
            for(int z=-1;z<=1;z++)world.setBlockState(WALL.add(0,0,z),Blocks.STONE.getDefaultState(),3);
            door=(BlockLargeProgrammableDoor)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs","large_programmable_door"));
            ItemStack stack=new ItemStack(door);player.setHeldItem(EnumHand.MAIN_HAND,stack);
            require(stack.getItem().onItemUse(player,world,WALL,EnumHand.MAIN_HAND,EnumFacing.EAST,1,.5F,.5F)==EnumActionResult.SUCCESS,"horizontal door placed over the retracted arm");
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
            root=door.root(world,HATCH);
            require(root!=null&&root.isHorizontal()&&door.complete(world,root)&&!open(world),"closed horizontal door spans the column");
            step=3;ticks=0;return false;
        }
        if(step==3){
            if(!arm.isOperational()||ticks<5)return false;
            require(arm.assembly().columnHeight()==5&&!arm.members().contains(HATCH),"door cell keeps the column without becoming a member");
            arm.operator(player);arm.target(true);step=4;return false;
        }
        if(step==4){
            if(!arm.error().contains("blocked")&&!arm.error().contains("Blocked")){require(ticks<300,"closed door blocks extension error="+arm.error()+" progress="+arm.progress());return false;}
            require(world.getBlockState(HATCH).getBlock()==door&&!open(world),"blocked arm leaves the closed door intact");
            door.toggleDoor(world,root.getPos(),true);require(open(world),"open the door");
            arm.operator(player);arm.target(true);step=5;return false;
        }
        if(step==5){
            if(!settled(world,arm,1))return false;
            require(world.getBlockState(CHEST).getBlock()==Blocks.CHEST,"chest extends through the open door");
            require(ArmPassages.occupied(world,HATCH),"extended arm reaches into the door cell");
            door.toggleDoor(world,root.getPos(),false);require(open(world),"door refuses to close on the extended arm");
            require(!door.removedByPlayer(world.getBlockState(HATCH),world,HATCH,player,false),"door refuses breaking on the extended arm");
            arm.operator(player);arm.target(false);step=6;return false;
        }
        if(step==6){
            if(!settled(world,arm,0))return false;
            door.toggleDoor(world,root.getPos(),false);require(!open(world),"door closes once the arm is retracted");
            // Remove the door; the displaced arm section comes back on the next tick.
            world.setBlockToAir(root.getPos());step=7;ticks=0;return false;
        }
        if(step==7){
            if(ticks<5)return false;
            require(world.getBlockState(HATCH).getBlock() instanceof GearBlock,"removing the door restores the arm section");
            if(!arm.isOperational()||arm.assembly().columnHeight()!=5)return false;
            arm.operator(player);arm.target(true);step=8;return false;
        }
        if(step==8){
            if(!settled(world,arm,1))return false;
            require(world.getBlockState(CHEST).getBlock()==Blocks.CHEST,"restored arm extends normally");
            joinByEdges(world,player);
            System.out.println("[vandorlabs] hatch-arm PASS placed over retracted sections/closed blocks/open passes/held open while extended/closes when retracted/section restored/edge clicks join 2x2");
            return true;
        }
        return false;
    }
    /** Build a 2x2 group by clicking door edges, like a player holding Large Doors. */
    private static void joinByEdges(WorldServer world,EntityPlayerMP player){
        BlockPos origin=FLOOR.add(-6,1,10);EnumFacing front=EnumFacing.SOUTH,width=front.rotateYCCW();
        for(int x=-1;x<8;x++)world.setBlockState(origin.offset(width,x).down(),Blocks.STONE.getDefaultState(),3);
        ItemStack stack=new ItemStack(door,4);player.setHeldItem(EnumHand.MAIN_HAND,stack);
        float yaw=player.rotationYaw;player.rotationYaw=front.getOpposite().getHorizontalAngle();
        require(stack.getItem().onItemUse(player,world,origin.offset(width).down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"first door");
        TileEntityLargeProgrammableDoor first=door.root(world,origin);require(first!=null,"first door root");
        // Edge clicks: beside, above, then above the second.
        require(stack.getItem().onItemUse(player,world,origin.offset(width,2).up(),EnumHand.MAIN_HAND,width,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"edge click places the second door beside");
        require(stack.getItem().onItemUse(player,world,origin.offset(width).up(2),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"edge click places the third door above");
        require(stack.getItem().onItemUse(player,world,origin.offset(width,4).up(2),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"edge click places the fourth door above the second");
        player.rotationYaw=yaw;player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        com.vandorlabs.render.LargeDoorJoin.Group<TileEntityLargeProgrammableDoor> group=first.joinedGroup();
        require(group.columns==2&&group.rows==2&&group.members.size()==4,"four edge-placed doors join as 2x2 columns="+group.columns+" rows="+group.rows);
        for(TileEntityLargeProgrammableDoor member:group.members)require(member.joinedGroup().anchor==group.anchor,"every member resolves the same group");
    }
}
