package com.vandorlabs.gear;

import com.vandorlabs.gear.core.ArmResource;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * A hopper-like feeder pushes 64 items through an Arm into a chest it carries while the arm
 * is repeatedly reversed under real world ticks and journals. Every accepted item must end up
 * in the chest exactly once.
 */
@SideOnly(Side.CLIENT)
public final class ArmItemFlowLiveChecks {
    private static final BlockPos ROOT=new BlockPos(60,100,0),CHEST=ROOT.up(2);
    private static final int OFFERED=64;
    private static int step,ticks,offered,accepted,reversals,fixture;
    /** Rotating arm with single items, then an Extender with three-item stacks. */
    private static final int FIXTURES=2;
    private static GearBlock armBlock(){return fixture==0?GearRegistry.GEAR_SWING_ARM:GearRegistry.GEAR_EXTENDER;}
    private static int batch(){return fixture==0?1:3;}
    private static boolean registered;
    private static volatile boolean complete;
    private static volatile Throwable failure;
    private ArmItemFlowLiveChecks(){}
    private static void require(boolean value,String message){
        if(!value)throw new IllegalStateException("arm-item-flow fixture="+fixture+" step="+step+" tick="+ticks+" offered="+offered+" accepted="+accepted+" reversals="+reversals+" "+message);
    }
    public static boolean tick(Minecraft mc){
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        if(!registered){MinecraftForge.EVENT_BUS.register(new Observer(mc.getIntegratedServer()));registered=true;}
        if(failure!=null)throw new IllegalStateException("Arm item flow server check",failure);
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
    private static int chestCount(WorldServer world){
        TileEntity chest=world.getTileEntity(CHEST);
        if(!(chest instanceof net.minecraft.tileentity.TileEntityChest))return -1;
        int total=0;IItemHandler slots=chest.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);
        for(int i=0;i<slots.getSlots();i++){ItemStack stack=slots.getStackInSlot(i);if(stack.getItem()==Item.getItemFromBlock(Blocks.SPONGE))total+=stack.getCount();}
        return total;
    }
    /** One pipe transfer: simulate per slot, then commit to the first slot that accepts any. */
    private static void feed(GearTileEntity arm){
        IItemHandler input=arm.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,arm.mountDirection().getOpposite());
        if(input==null)return;
        ItemStack offer=new ItemStack(Blocks.SPONGE,Math.min(batch(),OFFERED-offered));
        for(int slot=0;slot<input.getSlots();slot++){
            ItemStack simulated=input.insertItem(slot,offer.copy(),true);
            if(simulated.getCount()>=offer.getCount())continue;
            ItemStack remainder=input.insertItem(slot,offer.copy(),false);
            offered+=offer.getCount();accepted+=offer.getCount()-remainder.getCount();
            return;
        }
    }
    private static boolean run(WorldServer world,EntityPlayerMP player){
        if(++ticks>=4000){GearTileEntity a=world.getTileEntity(ROOT) instanceof GearTileEntity?((GearTileEntity)world.getTileEntity(ROOT)).loadedController():null;
            require(false,"bounded fixture arm="+(a==null?"missing":"progress="+a.progress()+" target="+a.target()+" locked="+ArmTransactions.locked(world,ROOT)+" payload="+(a.payload()==null?"none":"materialized="+a.payload().materialized())+" error="+a.error())+" chest="+chestCount(world));}
        if(step==0){
            for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-4,-1,-4),ROOT.add(4,5,4)))world.setBlockState(at,at.getY()==ROOT.getY()-1?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
            player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(armBlock(),2));
            for(BlockPos support:new BlockPos[]{ROOT.down(),ROOT})require(player.getHeldItemMainhand().onItemUse(player,world,support,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"rotating arm placement");
            player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(Blocks.CHEST));
            require(player.getHeldItemMainhand().onItemUse(player,world,ROOT.up(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"chest on the arm tip");
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);require(arm.rebuild(),"arm forms "+arm.error());arm.operator(player);
            NBTTagCompound settings=arm.settings();settings.setInteger(ArmResource.CHOICE,ArmResource.ITEM.ordinal());arm.configure(settings);
            step=1;ticks=0;return false;
        }
        GearTileEntity arm=((GearTileEntity)world.getTileEntity(ROOT)).loadedController();
        require(arm!=null,"arm owner");
        if(step==1){
            if(offered<OFFERED)feed(arm);
            if(ticks%25==0&&reversals<12){arm.operator(player);arm.target(!arm.target());reversals++;}
            if(offered<OFFERED||reversals<12)return false;
            arm.operator(player);arm.target(true);step=2;return false;
        }
        // A rotating arm's native, upright pose is its deployed end.
        if(arm.progress()!=1||ArmTransactions.locked(world,ROOT)||arm.payload()==null||!arm.payload().materialized())return false;
        int stored=chestCount(world);
        require(stored==accepted,"chest holds every accepted item exactly once: stored="+stored);
        System.out.println("[vandorlabs] arm-item-flow case PASS fixture="+fixture+" accepted="+accepted+" of "+offered+" through "+reversals+" reversals");
        if(++fixture<FIXTURES){step=0;ticks=0;offered=0;accepted=0;reversals=0;return false;}
        System.out.println("[vandorlabs] arm-item-flow PASS");
        return true;
    }
}
