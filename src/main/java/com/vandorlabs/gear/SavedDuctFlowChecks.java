package com.vandorlabs.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
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
 * Reproduces a reported saved world: Thermal item ducts feed an Extender that carries a
 * chest. 64 items go into the input chest while the arm is moved up and down; every item
 * must arrive. Positions come from the reported world.
 */
@SideOnly(Side.CLIENT)
public final class SavedDuctFlowChecks {
    private static final BlockPos ARM=new BlockPos(-71,83,135),INPUT=new BlockPos(-75,82,135);
    private static final int ADDED=64,TOGGLES=10;
    private static int step,ticks,before,toggles,quiet;
    private static boolean registered;
    private static volatile boolean complete;
    private static volatile Throwable failure;
    private SavedDuctFlowChecks(){}
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
    private static int sponges(TileEntity tile){
        if(tile==null)return 0;IItemHandler slots=tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);if(slots==null)return 0;
        int total=0;for(int i=0;i<slots.getSlots();i++){ItemStack stack=slots.getStackInSlot(i);if(stack.getItem()==Item.getItemFromBlock(Blocks.SPONGE))total+=stack.getCount();}
        return total;
    }
    /** Sponges in loaded inventories around the build, excluding the arm's own sections. */
    private static int counted(WorldServer world){
        int total=0;
        for(BlockPos at:BlockPos.getAllInBox(ARM.add(-8,-3,-4),ARM.add(4,6,4))){TileEntity tile=world.getTileEntity(at);if(!(tile instanceof GearTileEntity))total+=sponges(tile);}
        return total;
    }
    private static boolean run(WorldServer world,EntityPlayerMP player){
        if(++ticks>12000)throw new IllegalStateException("saved-duct-flow timeout step="+step+" toggles="+toggles+" counted="+counted(world));
        if(step==0){
            player.connection.setPlayerLocation(ARM.getX()-2,ARM.getY()+2,ARM.getZ()-4,0,20);
            if(ticks<100)return false;
            GearTileEntity arm=((GearTileEntity)world.getTileEntity(ARM)).loadedController();
            if(arm==null||!arm.isOperational()||ArmTransactions.locked(world,ARM)||arm.progress()!=0&&arm.progress()!=1)return false;
            before=counted(world);
            IItemHandler input=world.getTileEntity(INPUT).getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);
            ItemStack rest=input.insertItem(0,new ItemStack(Blocks.SPONGE,ADDED),false);
            if(!rest.isEmpty())throw new IllegalStateException("input chest accepts the test stack");
            System.out.println("[vandorlabs] saved-duct-flow start sponges="+before+"+"+ADDED+" progress="+arm.progress()+" mode="+arm.resourceTransfer()+" out="+arm.resourceOut());
            step=1;ticks=0;return false;
        }
        GearTileEntity arm=((GearTileEntity)world.getTileEntity(ARM)).loadedController();
        if(step==1){
            if(ticks%40==0&&toggles<TOGGLES){arm.operator(player);arm.target(!arm.target());toggles++;}
            if(toggles<TOGGLES)return false;
            step=2;return false;
        }
        // Let the arm settle and the ducts drain completely.
        if(arm==null||ArmTransactions.locked(world,ARM)||arm.progress()!=0&&arm.progress()!=1||arm.payload()!=null&&!arm.payload().materialized()){quiet=0;return false;}
        if(sponges(world.getTileEntity(INPUT))>0||++quiet<400)return false;
        int after=counted(world);
        System.out.println("[vandorlabs] saved-duct-flow "+(after==before+ADDED?"PASS":"FAIL")+" before="+before+" added="+ADDED+" after="+after+" toggles="+toggles);
        if(after!=before+ADDED)throw new IllegalStateException("items lost or duplicated: expected "+(before+ADDED)+" found "+after);
        return true;
    }
}
