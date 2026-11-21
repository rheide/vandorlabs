package com.vandorlabs.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Reproduces a reported saved world: a Servo on a slow Thermal item duct moves items one by one
 * from a chest, across a vertical Extender in Item mode, into the chest the arm carries. While
 * items travel, the player right-clicks the arm at random moments: single clicks, quick double
 * and triple clicks that reverse it mid-move, and waits for it to settle. Whenever the arm is at
 * rest, every item in the build (inventories, items travelling in ducts, dropped items) must
 * still add up, so a loss fails at the move that caused it. The layout is the reported
 * world, rebuilt here from code: a chest, a Thermal item duct with a Servo facing it, two more
 * ducts and a duct whose Retriever faces up into the arm base, under a two-section Extender
 * carrying a chest.
 */
@SideOnly(Side.CLIENT)
public final class ArmDuctFlowChecks {
    private static final BlockPos ARM=new BlockPos(540,101,60),INPUT=ARM.add(-4,-1,0);
    private static final int ADDED=64,ROUNDS=3;
    private static final long SEED=Long.getLong("vandorlabs.armDuctFlowSeed",0x5EEDL);
    private static final Random RANDOM=new Random(SEED);
    private static int step,ticks,expected,round,clicks,wait,pending,quiet,lastTotal=-1;
    private static final List<String> history=new ArrayList<>();
    private static boolean registered;
    private static volatile boolean complete;
    private static volatile Throwable failure;
    private ArmDuctFlowChecks(){}
    public static boolean tick(Minecraft mc){
        if(!registered){MinecraftForge.EVENT_BUS.register(new Observer(mc.getIntegratedServer()));registered=true;}
        if(failure!=null)throw new IllegalStateException("Saved duct flow check",failure);
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
    /** Vanilla stacks save a string id; CoFH saves stuffed Servo and Retriever items with the numeric id. */
    private static boolean sponge(NBTTagCompound tag){
        if(!tag.hasKey("Count",99))return false;
        if(tag.hasKey("id",8))return "minecraft:sponge".equals(tag.getString("id"));
        return tag.hasKey("id",99)&&tag.getShort("id")==Item.getIdFromItem(Item.getItemFromBlock(Blocks.SPONGE));
    }
    /** Sponges anywhere in a serialized tile: Thermal keeps travelling items only in its own lists. */
    private static int sponges(NBTBase tag){
        int total=0;
        if(tag instanceof NBTTagCompound){
            NBTTagCompound compound=(NBTTagCompound)tag;
            if(sponge(compound))total+=compound.getInteger("Count");
            for(String key:compound.getKeySet())total+=sponges(compound.getTag(key));
        }else if(tag instanceof NBTTagList){
            NBTTagList list=(NBTTagList)tag;for(int i=0;i<list.tagCount();i++)total+=sponges(list.get(i));
        }
        return total;
    }
    private static int inventory(TileEntity tile){
        IItemHandler slots=tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);if(slots==null)return 0;
        int total=0;for(int i=0;i<slots.getSlots();i++){ItemStack stack=slots.getStackInSlot(i);if(stack.getItem()==Item.getItemFromBlock(Blocks.SPONGE))total+=stack.getCount();}
        return total;
    }
    private static int travelling,dropped;
    /** Every sponge around the build while the arm is at rest; the arm's own snapshot is excluded. */
    private static int counted(WorldServer world){
        int total=0;travelling=0;dropped=0;
        for(BlockPos at:BlockPos.getAllInBox(ARM.add(-8,-3,-4),ARM.add(4,6,4))){
            TileEntity tile=world.getTileEntity(at);
            if(tile==null||tile instanceof GearTileEntity)continue;
            if(tile.getClass().getName().startsWith("cofh.thermaldynamics.")){int n=sponges(tile.writeToNBT(new NBTTagCompound()));travelling+=n;total+=n;}
            else total+=inventory(tile);
        }
        for(EntityItem item:world.getEntitiesWithinAABB(EntityItem.class,new AxisAlignedBB(ARM.getX()-12,ARM.getY()-6,ARM.getZ()-8,ARM.getX()+8,ARM.getY()+10,ARM.getZ()+8)))
            if(item.getItem().getItem()==Item.getItemFromBlock(Blocks.SPONGE)){dropped+=item.getItem().getCount();total+=item.getItem().getCount();}
        return total;
    }
    private static GearTileEntity arm(WorldServer world){
        TileEntity raw=world.getTileEntity(ARM);return raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;
    }
    private static boolean atRest(WorldServer world,GearTileEntity arm){
        // A freshly built arm adopts the chest as its payload on its first move.
        return arm!=null&&arm.progress()==(arm.target()?1:0)&&!ArmTransactions.locked(world,ARM)&&(arm.payload()==null||arm.payload().materialized());
    }
    private static String armState(WorldServer world,GearTileEntity arm){
        return arm==null?"arm=missing":"progress="+arm.progress()+" target="+arm.target()+" payload="+(arm.payload()==null?"none":arm.payload().materialized()?"native":"captured")+" hold="+ArmTransactions.diagnostic(world,ARM)+" error="+arm.error();
    }
    private static String lastLedger="";
    /** Diagnostic: every store of the test items, logged whenever one changes. */
    private static void ledger(WorldServer world,GearTileEntity arm){
        int input=inventory(world.getTileEntity(INPUT)),chest=0,ducts=0,armNbt=0;
        for(BlockPos at:BlockPos.getAllInBox(ARM.add(-8,-3,-4),ARM.add(4,6,4))){
            TileEntity tile=world.getTileEntity(at);if(tile==null||at.equals(INPUT))continue;
            if(tile instanceof GearTileEntity)armNbt+=sponges(tile.writeToNBT(new NBTTagCompound()));
            else if(tile.getClass().getName().startsWith("cofh.thermaldynamics."))ducts+=sponges(tile.writeToNBT(new NBTTagCompound()));
            else chest+=inventory(tile)+("CopyFrozenTile".equals(tile.getClass().getSimpleName())?1000:0);
        }
        String line="input="+input+" ducts="+ducts+" chest="+chest+" armNbt="+armNbt+" "+armState(world,arm);
        if(!line.equals(lastLedger)){lastLedger=line;System.out.println("[vandorlabs] arm-duct-flow ledger t="+world.getTotalWorldTime()+" "+line);}
    }
    private static boolean built;
    /** Thermal duct with optional attachment, as in the reported world (Servo on the west face, Retriever on the top). */
    private static void duct(WorldServer world,BlockPos at,String attachment,int side,int level){
        net.minecraft.block.Block block=net.minecraft.block.Block.REGISTRY.getObject(new net.minecraft.util.ResourceLocation("thermaldynamics","duct_32"));
        if(block==null||block==Blocks.AIR)throw new IllegalStateException("Thermal Dynamics item duct missing");
        world.setBlockState(at,block.getStateFromMeta(0),3);
        TileEntity tile=world.getTileEntity(at);if(tile==null)throw new IllegalStateException("item duct tile missing at "+at);
        if(attachment==null)return;
        NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound()),levels=new NBTTagCompound(),entry=new NBTTagCompound();
        levels.setByte("Level0",(byte)level);levels.setByte("Level1",(byte)0);levels.setByte("Level2",(byte)1);levels.setByte("Level3",(byte)0);
        entry.setString("id","thermaldynamics:"+attachment);entry.setByte("side",(byte)side);entry.setByte("rsMode",(byte)0);entry.setTag("Levels",levels);
        entry.setByte("Flags",(byte)25);entry.setByte("power",(byte)1);entry.setByte("type",(byte)4);entry.setTag("Inventory",new NBTTagList());
        NBTTagList attachments=new NBTTagList();attachments.appendTag(entry);tag.setTag("Attachments",attachments);
        tile.readFromNBT(tag);tile.markDirty();world.notifyBlockUpdate(at,world.getBlockState(at),world.getBlockState(at),3);
    }
    private static void build(WorldServer world,EntityPlayerMP player){
        for(BlockPos at:BlockPos.getAllInBox(ARM.add(-8,-3,-4),ARM.add(4,6,4))){
            world.getChunkFromBlockCoords(at);world.removeTileEntity(at);
            world.setBlockState(at,at.getY()==ARM.getY()-2?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
        }
        world.setBlockState(INPUT,Blocks.CHEST.getDefaultState(),3);
        duct(world,INPUT.east(),"servo_item",EnumFacing.WEST.getIndex(),1);
        duct(world,INPUT.east(2),null,0,0);duct(world,INPUT.east(3),null,0,0);
        duct(world,ARM.down(),"retriever_item",EnumFacing.UP.getIndex(),64);
        player.setGameType(GameType.CREATIVE);player.inventory.currentItem=0;
        player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(GearRegistry.GEAR_EXTENDER,2));
        for(BlockPos support:new BlockPos[]{ARM.down(),ARM})
            if(player.getHeldItemMainhand().onItemUse(player,world,support,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)!=net.minecraft.util.EnumActionResult.SUCCESS)
                throw new IllegalStateException("arm-duct-flow Extender placement on "+support+" "+world.getBlockState(support));
        world.setBlockState(ARM.up(2),Blocks.CHEST.getDefaultState(),3);
        GearTileEntity arm=(GearTileEntity)world.getTileEntity(ARM);if(!arm.rebuild())throw new IllegalStateException("arm-duct-flow Extender forms");
        arm=arm.loadedController();arm.operator(player);
        NBTTagCompound settings=arm.settings();settings.setInteger(com.vandorlabs.gear.core.ArmResource.CHOICE,com.vandorlabs.gear.core.ArmResource.ITEM.ordinal());arm.configure(settings);
        player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        System.out.println("[vandorlabs] arm-duct-flow built reported layout at "+ARM);
    }
    private static void record(String event){history.add(event);if(history.size()>40)history.remove(0);}
    /** The server's own right-click handler, as a player clicking the arm base. */
    private static void rightClick(WorldServer world,EntityPlayerMP player,GearTileEntity arm){
        player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        player.interactionManager.processRightClickBlock(player,world,ItemStack.EMPTY,EnumHand.MAIN_HAND,ARM,EnumFacing.NORTH,.5F,.5F,.5F);
        clicks++;record("t="+world.getTotalWorldTime()+" click#"+clicks+" -> "+armState(world,arm(world)));
    }
    /** The next random pattern: single click, quick double or triple click, or wait for rest. */
    private static void schedule(){
        int pattern=RANDOM.nextInt(10);
        if(pattern<4){pending=1;wait=1+RANDOM.nextInt(80);}
        else if(pattern<6){pending=2;wait=0;}
        else if(pattern<8){pending=3;wait=0;}
        else{pending=-1;wait=RANDOM.nextInt(40);}
    }
    private static boolean run(WorldServer world,EntityPlayerMP player){
        ++ticks;
        if(step==0&&round==0&&!built){build(world,player);built=true;}
        if(step==0){
            player.setGameType(GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();
            player.connection.setPlayerLocation(ARM.getX()-2,ARM.getY()+2,ARM.getZ()-4,0,20);
            if(ticks<100)return false;
            if(ticks>2400)throw new IllegalStateException("arm-duct-flow arm never came to rest before round "+round+": "+armState(world,arm(world)));
            GearTileEntity arm=arm(world);
            if(!atRest(world,arm))return false;
            int before=counted(world);
            ItemStack rest=world.getTileEntity(INPUT).getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null).insertItem(0,new ItemStack(Blocks.SPONGE,ADDED),false);
            if(!rest.isEmpty())throw new IllegalStateException("input chest accepts the test stack");
            expected=before+ADDED;
            System.out.println("[vandorlabs] arm-duct-flow round="+round+" seed="+SEED+" start sponges="+before+"+"+ADDED+" "+armState(world,arm)+" mode="+arm.resourceTransfer());
            schedule();step=1;ticks=0;quiet=0;lastTotal=-1;return false;
        }
        GearTileEntity arm=arm(world);
        if(ticks>24000)throw new IllegalStateException("arm-duct-flow timeout round="+round+" clicks="+clicks+" "+armState(world,arm));
        boolean rest=atRest(world,arm);
        ledger(world,arm);
        if(rest){
            int total=counted(world);
            if(total!=lastTotal){
                record("t="+world.getTotalWorldTime()+" rest total="+total+" travelling="+travelling+" dropped="+dropped+" "+armState(world,arm));lastTotal=total;
                if(total!=expected)throw new IllegalStateException("arm-duct-flow round="+round+" items "+(total<expected?"lost":"duplicated")+": expected "+expected+" found "+total
                        +" (travelling="+travelling+" dropped="+dropped+") after "+clicks+" clicks seed="+SEED+"; recent: "+String.join(" | ",history));
            }
        }
        boolean inputEmpty=inventory(world.getTileEntity(INPUT))==0;
        if(step==1){
            if(wait>0){wait--;return false;}
            if(pending==-1){if(!rest)return false;schedule();return false;}
            if(pending>0){
                rightClick(world,player,arm);pending--;wait=pending>0?1+RANDOM.nextInt(15):0;
                if(pending==0){if(inputEmpty&&travelling==0&&rest){step=2;ticks=0;return false;}schedule();}
                return false;
            }
            return false;
        }
        // Let everything settle and the ducts drain before the final count.
        if(!rest||travelling>0||!inputEmpty){quiet=0;return false;}
        if(++quiet<200)return false;
        System.out.println("[vandorlabs] arm-duct-flow round="+round+" complete expected="+expected+" found="+lastTotal+" clicks="+clicks);
        if(++round<ROUNDS){step=0;ticks=0;return false;}
        System.out.println("[vandorlabs] arm-duct-flow PASS rounds="+ROUNDS+" items="+ADDED+" each clicks="+clicks+" seed="+SEED+" no loss at any rest");
        return true;
    }
}
