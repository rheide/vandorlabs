package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.container.ContainerTokenSlot;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Real player inventory returns, scheduled toggle deadlines and GUI insertion routes. */
final class TokenSlotToggleRuntimeCheck {
    private static boolean done;
    private final WorldServer world;
    private final EntityPlayerMP player;
    private final BlockPos pos=new BlockPos(288,32,142);
    private final NBTTagList inventory;
    private final ItemStack cursor;
    private final TileEntityTokenSlot tile;
    private final long started;
    private int stage;
    private TokenSlotToggleRuntimeCheck(WorldServer world,EntityPlayerMP player){
        this.world=world;this.player=player;inventory=player.inventory.writeToNBT(new NBTTagList());cursor=player.inventory.getItemStack().copy();
        world.setBlockState(pos.south(),Blocks.STONE.getDefaultState(),2);world.setBlockState(pos,net.minecraft.block.Block.getBlockFromName("vandorlabs:data_card_slot").getDefaultState(),2);
        tile=(TileEntityTokenSlot)world.getTileEntity(pos);tile.configure(false,8,2);tile.setRedstoneChannels(ChannelList.of(64213));
        player.inventory.clear();player.inventory.setItemStack(ItemStack.EMPTY);player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.DATA_CARD,2,4));
        use(()->block().onBlockActivated(world,pos,world.getBlockState(pos),player,EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,.5F));
        require(tile.isEmpty()&&player.getHeldItemMainhand().getCount()==2,"denied token untouched");
        player.setHeldItem(EnumHand.MAIN_HAND,card(2));
        use(()->block().onBlockActivated(world,pos,world.getBlockState(pos),player,EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,.5F));
        require(tile.returning()&&count()==1,"one borrowed item");started=world.getTotalWorldTime();
    }
    private static ItemStack card(int count){ItemStack item=new ItemStack(ModItems.DATA_CARD,count,3);item.setStackDisplayName("Toggle return fixture");return item;}
    private BlockTokenSlot block(){return (BlockTokenSlot)world.getBlockState(pos).getBlock();}
    private int count(){int n=0;for(int i=0;i<player.inventory.getSizeInventory();i++){ItemStack item=player.inventory.getStackInSlot(i);if(ItemStack.areItemsEqual(item,card(1))&&ItemStack.areItemStackTagsEqual(item,card(1)))n+=item.getCount();}return n;}
    private void use(Runnable work){double x=player.posX,y=player.posY,z=player.posZ;player.setPosition(pos.getX()+.5,pos.getY(),pos.getZ()-1);try{work.run();}finally{player.setPosition(x,y,z);}}
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException("token toggle: "+reason);}
    static void start(WorldServer world,EntityPlayerMP player){done=false;FMLCommonHandler.instance().bus().register(new TokenSlotToggleRuntimeCheck(world,player));}
    static boolean done(){return done;}
    @SubscribeEvent public void tick(TickEvent.WorldTickEvent event){
        if(event.world!=world||event.phase!=TickEvent.Phase.END)return;long age=world.getTotalWorldTime()-started;
        if(stage==0&&age>=10){require(tile.getOutputLevel()==15&&RedstoneChannels.level(world,64213)==15,"scan powers block and channel");tile.readFromNBT(tile.writeToNBT(new NBTTagCompound()));tile.onLoad();stage++;}
        if(stage==1&&age>=24){
            require(tile.isEmpty()&&count()==2&&tile.getOutputLevel()==15,"return preserves item data and latched output");
            player.getHeldItemMainhand().shrink(1);player.inventory.setItemStack(card(1));
            use(()->new ContainerTokenSlot(player.inventory,tile).slotClick(0,0,ClickType.PICKUP,player));
            require(player.inventory.getItemStack().isEmpty()&&tile.returning(),"GUI carried-item insertion");stage++;
        }
        if(stage==2&&age>=48){
            require(tile.isEmpty()&&count()==2&&tile.getOutputLevel()==0,"second insertion switches off and returns");
            player.inventory.clear();for(int i=0;i<36;i++)player.inventory.setInventorySlotContents(i,new ItemStack(Blocks.COBBLESTONE,64));player.setHeldItem(EnumHand.MAIN_HAND,card(1));
            use(()->tile.insertToggle(player,player.getHeldItemMainhand()));player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(Blocks.COBBLESTONE,64));stage++;
        }
        if(stage==3&&age>=72){
            require(tile.returning()&&count()==0&&tile.getOutputLevel()==15,"full inventory retains item in mount");
            require(tile.removeStackFromSlot(0).isEmpty(),"another player or hopper cannot take pending item");player.inventory.setInventorySlotContents(1,ItemStack.EMPTY);stage++;
        }
        if(stage==4&&age>=96){
            require(tile.isEmpty()&&count()==1&&tile.getOutputLevel()==15,"full-inventory retry returns exactly once");
            use(()->new ContainerTokenSlot(player.inventory,tile).transferStackInSlot(player,36));require(tile.returning()&&count()==0,"shift-click inserts one owned token");stage++;
        }
        if(stage==5&&age>=120){
            require(tile.isEmpty()&&count()==1&&tile.getOutputLevel()==0&&RedstoneChannels.level(world,64213)==0,"shift-click toggles off and returns once");
            player.inventory.clear();for(int i=0;i<36;i++)player.inventory.setInventorySlotContents(i,new ItemStack(Blocks.COBBLESTONE,64));player.setHeldItem(EnumHand.OFF_HAND,card(2));
            use(()->block().onBlockActivated(world,pos,world.getBlockState(pos),player,EnumHand.OFF_HAND,EnumFacing.NORTH,.5F,.5F,.5F));require(tile.returning()&&count()==1,"offhand insertion borrows one item");stage++;
        }
        if(stage==6&&age>=144){
            require(tile.isEmpty()&&count()==2&&player.getHeldItemOffhand().getCount()==2&&tile.getOutputLevel()==15,"return merges into offhand when main inventory is full");
            world.setBlockToAir(pos);world.setBlockToAir(pos.south());player.inventory.readFromNBT(inventory);player.inventory.setItemStack(cursor);player.inventoryContainer.detectAndSendChanges();
            FMLCommonHandler.instance().bus().unregister(this);done=true;
            System.out.println("[vandorlabs][reprolab] token-toggle-return PASS real-ticks inventory-NBT channels two-toggle reload denied-token full-inventory-retry carried-item shift-click offhand-merge");
        }
    }
}
