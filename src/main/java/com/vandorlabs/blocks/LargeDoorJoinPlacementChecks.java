package com.vandorlabs.blocks;

import com.vandorlabs.render.LargeDoorJoin;
import com.vandorlabs.tiles.TileEntityLargeProgrammableDoor;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Reported workflow: Large Doors with Join: On placed one by one as hatches in a 6x6 floor
 * opening, the first two by clicking the west wall and the other two by clicking the east wall.
 * Every order where each door lands beside an existing one must end as one 2x2 door, and every
 * click must place its door the first time. Sneak-right-clicking the left or right half of a
 * joined door's top face places the next door on that side. Clicks go through the real client
 * controller, so client-side placement checks and prediction are exercised as for a player.
 */
@SideOnly(Side.CLIENT)
public final class LargeDoorJoinPlacementChecks {
    private static final BlockPos ORIGIN=new BlockPos(110,85,40);
    /** Door slots in the 6x6 opening: A/B west wall north/south, C/D east wall north/south. */
    private static final String[] ORDERS={"ABCD","ACBD","ACDB","CDAB","CADB","ABCD","ABCD"};
    /** Where on the wall face each click lands; placement reads the hit height for panel depth. */
    private static final float[] HIT_Y={.5F,.5F,.5F,.5F,.5F,.1F,.9F};
    private static final List<String> failures=new ArrayList<>();
    private static int scenario,index,ticks,doorsBefore;
    private static String stage="setup";
    private static ItemStack stack;
    private static boolean registered;
    private static volatile boolean complete;
    private static volatile Throwable failure;
    /** A click requested by the server thread and performed by the client thread. */
    private static final class Click {
        final BlockPos pos;final EnumFacing face;final Vec3d hit;final boolean sneak;
        volatile EnumActionResult result;volatile boolean done;int waited,settle;
        Click(BlockPos pos,EnumFacing face,Vec3d hit,boolean sneak){this.pos=pos;this.face=face;this.hit=hit;this.sneak=sneak;}
    }
    private static volatile Click click;
    private LargeDoorJoinPlacementChecks(){}

    public static boolean tick(Minecraft mc){
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        mc.gameSettings.pauseOnLostFocus=false;
        if(!registered){MinecraftForge.EVENT_BUS.register(new Observer(mc.getIntegratedServer()));registered=true;}
        if(failure!=null)throw new IllegalStateException("Large Door join placement check",failure);
        Click pending=click;
        if(pending!=null&&!pending.done&&mc.player!=null){
            // Click only once the client holds the synchronized door stack and sneaks as requested.
            mc.player.inventory.currentItem=0;
            if(mc.player.isSneaking()!=pending.sneak){
                mc.player.setSneaking(pending.sneak);
                mc.player.connection.sendPacket(new CPacketEntityAction(mc.player,pending.sneak?CPacketEntityAction.Action.START_SNEAKING:CPacketEntityAction.Action.STOP_SNEAKING));
            }
            if(mc.player.getHeldItemMainhand().getItem()!=net.minecraft.item.Item.getItemFromBlock(door())){
                if(++pending.waited>200)throw new IllegalStateException("door-join client never held the door stack: held="+mc.player.getHeldItemMainhand());
                return false;
            }
            if(++pending.waited<4)return false;
            pending.result=mc.playerController.processRightClickBlock(mc.player,mc.world,pending.pos,pending.face,pending.hit,EnumHand.MAIN_HAND);
            if(pending.sneak){mc.player.setSneaking(false);mc.player.connection.sendPacket(new CPacketEntityAction(mc.player,CPacketEntityAction.Action.STOP_SNEAKING));}
            pending.done=true;
        }
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
    private static BlockLargeProgrammableDoor door(){
        return (BlockLargeProgrammableDoor)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs","large_programmable_door"));
    }
    /** A stone floor with an opening; the opening's cells are air at floor level. */
    private static void floor(WorldServer world,BlockPos min,int minZ,int sizeX,int sizeZ){
        for(BlockPos at:BlockPos.getAllInBox(min.add(-4,-1,minZ-4),min.add(sizeX+4,5,minZ+sizeZ+4))){
            boolean hole=at.getX()>=min.getX()&&at.getX()<min.getX()+sizeX&&at.getZ()>=min.getZ()+minZ&&at.getZ()<min.getZ()+minZ+sizeZ;
            world.setBlockState(at,at.getY()==min.getY()&&!hole?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
        }
    }
    /** A Large Door stack set to Join: On, as a player holds it after configuring a door. */
    private static ItemStack joiningStack(WorldServer world,EntityPlayerMP player){
        BlockPos spare=ORIGIN.add(-20,0,0);floor(world,spare,0,3,3);
        ItemStack plain=new ItemStack(door());player.setHeldItem(EnumHand.MAIN_HAND,plain);
        if(plain.getItem().onItemUse(player,world,spare.add(-1,0,1),EnumHand.MAIN_HAND,EnumFacing.EAST,1F,.5F,.5F)!=EnumActionResult.SUCCESS)
            throw new IllegalStateException("door-join template door placed");
        TileEntityLargeProgrammableDoor template=door().root(world,spare.add(0,0,1));
        NBTTagCompound settings=template.itemSettings().copy();settings.setBoolean(LargeDoorJoin.TAG,true);
        world.setBlockToAir(template.getPos());floor(world,spare,0,3,3);
        ItemStack result=new ItemStack(door(),32);result.getOrCreateSubCompound("SpaceDoorSettings").merge(settings);
        return result;
    }
    private static void hold(EntityPlayerMP player){
        player.inventory.currentItem=0;player.inventory.setInventorySlotContents(0,stack.copy());
        player.inventoryContainer.detectAndSendChanges();
    }
    private static BlockPos min(){return scenario<ORDERS.length?ORIGIN.add(scenario*12,0,0):ORIGIN.add((scenario-ORDERS.length)*12,0,30);}
    private static int doors(WorldServer world){
        int count=0;BlockPos min=min();
        for(BlockPos at:BlockPos.getAllInBox(min.add(-4,0,-8),min.add(10,0,14)))if(world.getBlockState(at).getBlock() instanceof BlockLargeProgrammableDoor)count++;
        return count;
    }
    private static String describe(WorldServer world){
        StringBuilder out=new StringBuilder();BlockPos min=min();
        for(BlockPos at:BlockPos.getAllInBox(min.add(-4,0,-8),min.add(10,0,14))){
            if(!(world.getTileEntity(at) instanceof TileEntityLargeProgrammableDoor))continue;
            TileEntityLargeProgrammableDoor root=door().root(world,at);
            if(root==null||!root.getPos().equals(at))continue;
            LargeDoorJoin.Group<TileEntityLargeProgrammableDoor> group=root.joinedGroup();
            out.append(" root").append(at).append(" facing=").append(world.getBlockState(at).getValue(BlockVandorDoor.FACING))
                    .append(" joins=").append(root.joins()).append(" group=").append(group.columns).append("x").append(group.rows);
        }
        return out.toString();
    }
    private static void request(EntityPlayerMP player,BlockPos pos,EnumFacing face,Vec3d hit,boolean sneak,Vec3d stand,float yaw,float pitch){
        hold(player);player.connection.setPlayerLocation(stand.x,stand.y,stand.z,yaw,pitch);
        click=new Click(pos,face,hit,sneak);
    }
    /** Request the wall click for one slot of the 6x6 opening. */
    private static void requestSlot(EntityPlayerMP player,char slot){
        BlockPos min=min();boolean west=slot=='A'||slot=='B',north=slot=='A'||slot=='C';
        BlockPos wall=west?min.add(-1,0,north?1:4):min.add(6,0,north?1:4);
        EnumFacing face=west?EnumFacing.EAST:EnumFacing.WEST;
        Vec3d hit=new Vec3d(wall.getX()+(west?1:0),wall.getY()+HIT_Y[Math.min(scenario,HIT_Y.length-1)],wall.getZ()+.5);
        request(player,wall,face,hit,false,new Vec3d(min.getX()+3,min.getY()+3,min.getZ()+2.5),west?90:-90,50);
    }
    private static boolean run(WorldServer world,EntityPlayerMP player){
        ++ticks;
        if(stage.equals("setup")){
            if(ticks<20)return false;
            if(scenario>=ORDERS.length+3){
                if(!failures.isEmpty())throw new IllegalStateException("door-join "+failures.size()+" failure(s): "+String.join(" | ",failures));
                System.out.println("[vandorlabs] door-join PASS orders="+String.join(",",ORDERS)+" first-click placements form one 2x2 door/sneak-click left and right halves place on that side");
                return true;
            }
            player.setGameType(GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();
            BlockPos min=min();
            if(scenario<ORDERS.length)floor(world,min,0,6,6);else floor(world,min,-3,6,12);
            stack=joiningStack(world,player);index=0;stage="click";doorsBefore=doors(world);ticks=0;
            requestSlot(player,scenario<ORDERS.length?ORDERS[scenario].charAt(0):'A');
            return false;
        }
        Click pending=click;
        if(stage.equals("click")){
            if(pending==null||!pending.done||++pending.settle<8)return false;
            int now=doors(world);
            String label=scenario<ORDERS.length?ORDERS[scenario]+"@"+HIT_Y[scenario]+" door "+(index+1)+"="+ORDERS[scenario].charAt(index):"sneak setup door "+(index+1);
            if(now!=doorsBefore+9){failures.add(label+": first click placed "+(now-doorsBefore)+" door cells (client result="+pending.result+");"+describe(world));next();return false;}
            doorsBefore=now;index++;click=null;
            if(scenario<ORDERS.length){
                if(index<4){requestSlot(player,ORDERS[scenario].charAt(index));return false;}
                checkJoined(world);next();return false;
            }
            // Sneak scenarios: a joined pair along the opening (A then B), then one sneak-click.
            if(index==1){requestSlot(player,'B');return false;}
            BlockPos min=min();
            if(index==2&&door().root(world,min).joinedGroup().members.size()!=2){failures.add("sneak setup pair joined;"+describe(world));next();return false;}
            if(scenario==ORDERS.length+2){
                // Build the second row by sneak-clicking near each door's far (east) edge.
                if(index==4){checkJoined(world);next();return false;}
                BlockPos clicked=index==2?min.add(2,0,1):min.add(2,0,4);
                request(player,clicked,EnumFacing.UP,new Vec3d(clicked.getX()+.95,clicked.getY()+1,clicked.getZ()+.5),true,new Vec3d(min.getX()-3,min.getY()+2,min.getZ()+3),-90,40);
                return false;
            }
            boolean left=scenario==ORDERS.length;
            // The player stands west of the pair looking east: north is the left half.
            BlockPos clicked=left?min.add(1,0,1):min.add(1,0,4);
            request(player,clicked,EnumFacing.UP,new Vec3d(clicked.getX()+.5,clicked.getY()+1,clicked.getZ()+.5),true,new Vec3d(min.getX()-3,min.getY()+2,min.getZ()+3),-90,40);
            stage="sneak";return false;
        }
        if(stage.equals("sneak")){
            if(pending==null||!pending.done||++pending.settle<8)return false;
            boolean left=scenario==ORDERS.length;BlockPos min=min();
            BlockPos expected=left?min.add(0,0,-3):min.add(0,0,6),wrong=left?min.add(0,0,6):min.add(0,0,-3);
            TileEntityLargeProgrammableDoor placed=door().root(world,expected),other=door().root(world,wrong);
            String where="client result="+pending.result+" expectedSide="+(placed==null?"empty":"door")+" otherSide="+(other==null?"empty":"door")+" new cells="+(doors(world)-doorsBefore)+";"+describe(world);
            System.out.println("[vandorlabs] door-join sneak "+(left?"left":"right")+" half: "+where);
            // Groups are at most 2x2, so a third door in a line stands alone.
            if(placed==null||other!=null)failures.add("sneak-click on the "+(left?"left":"right")+" half places the next door on that side: "+where);
            next();return false;
        }
        return false;
    }
    private static void checkJoined(WorldServer world){
        BlockPos min=min();
        TileEntityLargeProgrammableDoor first=door().root(world,min);
        LargeDoorJoin.Group<TileEntityLargeProgrammableDoor> group=first==null?null:first.joinedGroup();
        boolean joined=group!=null&&group.columns==2&&group.rows==2&&group.members.size()==4;
        if(joined){
            for(BlockPos at:new BlockPos[]{min.add(0,0,3),min.add(3,0,0),min.add(3,0,3)})joined&=door().root(world,at).joinedGroup().anchor==group.anchor;
            door().toggleDoor(world,group.anchor.getPos(),true);
            for(TileEntityLargeProgrammableDoor member:group.members)joined&=world.getBlockState(member.getPos()).getValue(BlockVandorDoor.OPEN);
        }
        String name=scenario<ORDERS.length?ORDERS[scenario]+"@"+HIT_Y[scenario]:"sneak-built second row";
        System.out.println("[vandorlabs] door-join order="+name+" "+(joined?"joined 2x2":"NOT joined")+describe(world));
        if(!joined)failures.add(name+": four Join On doors did not form one 2x2 door that opens together;"+describe(world));
    }
    private static void next(){scenario++;stage="setup";ticks=0;click=null;}
}
