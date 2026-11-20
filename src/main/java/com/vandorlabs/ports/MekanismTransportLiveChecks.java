package com.vandorlabs.ports;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.items.IItemHandler;

/** Real native path finding and world ticks; the bridge never stores a second stack. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class MekanismTransportLiveChecks {
    private static final BlockPos SOURCE=new BlockPos(8,100,8),PIPE=SOURCE.east(),DEST=SOURCE.east(3);
    private static int stage,ticks;private static IItemHandler handler;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Mekanism item transit stage="+stage+" tick="+ticks+": "+why);}
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent event){
        if(!Boolean.getBoolean("vandorlabs.armNetworkTransit")||event.phase!=TickEvent.Phase.END||!(event.world instanceof WorldServer)||event.world.provider.getDimension()!=0||stage==4)return;
        WorldServer world=(WorldServer)event.world;require(++ticks<400,"bounded native item delivery");
        if(stage==0){
            for(BlockPos pos:BlockPos.getAllInBox(SOURCE.add(-2,-1,-2),DEST.add(2,2,2))){world.getChunkFromBlockCoords(pos);world.setBlockToAir(pos);}
            world.setBlockState(SOURCE,Blocks.CHEST.getDefaultState(),3);world.setBlockState(DEST,Blocks.CHEST.getDefaultState(),3);
            IBlockState selected=null;scan:for(Block block:Block.REGISTRY)if(block.getRegistryName().getResourceDomain().equals("mekanism"))for(int meta=0;meta<16;meta++)try{IBlockState state=block.getStateFromMeta(meta);if(!block.hasTileEntity(state))continue;TileEntity tile=block.createTileEntity(world,state);if(tile!=null&&tile.getClass().getName().equals("mekanism.common.tile.transmitter.TileEntityLogisticalTransporter")){selected=state;break scan;}}catch(RuntimeException invalid){}
            require(selected!=null,"installed logistical transporter");world.setBlockState(PIPE,selected,3);world.setBlockState(PIPE.east(),selected,3);
            ((net.minecraft.inventory.IInventory)world.getTileEntity(SOURCE)).setInventorySlotContents(0,new ItemStack(Items.DIAMOND,7));stage=1;ticks=0;return;
        }
        if(stage==1){
            if(ticks<25)return;handler=ResourcePortConnections.itemHandler(world.getTileEntity(PIPE),EnumFacing.WEST,SOURCE);if(handler==null||handler.getSlots()==0)return;
            ItemStack offered=new ItemStack(Items.DIAMOND,7),remainder=handler.insertItem(0,offered,true);if(!remainder.isEmpty())return;
            require(offered.getCount()==7&&((net.minecraft.inventory.IInventory)world.getTileEntity(SOURCE)).getStackInSlot(0).getCount()==7,"simulation retains native source stack");
            require(inventory(world)==0&&stacks(world)==0,"simulation publishes neither a destination stack nor a traveling stack");
            require(handler.insertItem(0,offered,false).isEmpty()&&offered.getCount()==7,"commit accepts one transit without mutating caller's offer");
            ((net.minecraft.inventory.IInventory)world.getTileEntity(SOURCE)).setInventorySlotContents(0,ItemStack.EMPTY);require(handler.getStackInSlot(0).isEmpty()&&handler.extractItem(0,7,false).isEmpty(),"bridge exposes no independently owned inventory");stage=2;ticks=0;return;
        }
        if(stage==2){
            if(inventory(world)!=7||stacks(world)!=0)return;require(((net.minecraft.inventory.IInventory)world.getTileEntity(SOURCE)).isEmpty(),"exactly one usable native inventory owner after delivery");stage=3;ticks=0;return;
        }
        if(ticks<10)return;require(inventory(world)==7&&stacks(world)==0,"delivery does not repeat across native ticks");world.setBlockToAir(PIPE);
        require(handler.insertItem(0,new ItemStack(Items.DIAMOND,3),false).getCount()==3,"stale handler refuses after native pipe removal");stage=4;
        System.out.println("[vandorlabs] arm-network-transit PASS native Mekanism route actual source position simulation no traveling stack commit seven diamonds native delivery exactly once stale handler refusal");
    }
    private static int inventory(WorldServer world){net.minecraft.inventory.IInventory inv=(net.minecraft.inventory.IInventory)world.getTileEntity(DEST);int count=0;for(int i=0;i<inv.getSizeInventory();i++)if(inv.getStackInSlot(i).getItem()==Items.DIAMOND)count+=inv.getStackInSlot(i).getCount();return count;}
    private static int stacks(WorldServer world){int count=0;for(BlockPos pos:new BlockPos[]{PIPE,PIPE.east()}){TileEntity tile=world.getTileEntity(pos);if(tile!=null)count+=tile.writeToNBT(new NBTTagCompound()).getTagList("stacks",10).tagCount();}return count;}
    private MekanismTransportLiveChecks(){}
}
