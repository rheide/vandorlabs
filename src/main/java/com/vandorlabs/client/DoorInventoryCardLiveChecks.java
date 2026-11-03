package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.items.ModItems;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;

/** Real Survival interaction packets with the credential outside the selected hotbar slot. */
final class DoorInventoryCardLiveChecks {
    private static final BlockPos POS=new BlockPos(240,90,220);
    private static int kind,stage,wait;
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("door-inventory-card kind="+kind+" stage="+stage+": "+why);}
    static boolean tick(Minecraft mc)throws Exception{
        if(kind==2)return true;
        require(++wait<40,"timeout client-mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" card="+mc.player.inventory.getStackInSlot(20)+" pos="+mc.player.getPosition()+" server="+mc.getIntegratedServer().callFromMainThread(()->actor(mc).getHeldItemMainhand()+" slot="+actor(mc).inventory.currentItem+" card="+actor(mc).inventory.getStackInSlot(20)).get());
        if(stage==0){mc.player.closeScreen();mc.playerController.setGameType(GameType.SURVIVAL);mc.player.inventory.currentItem=0;stage++;wait=0;return false;}
        if(stage==1){
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=actor(mc);for(BlockPos at:BlockPos.getAllInBox(POS.add(-1,-1,-1),POS.add(4,-1,3)))p.world.setBlockState(at,net.minecraft.init.Blocks.STONE.getDefaultState(),2);p.setGameType(GameType.SURVIVAL);p.inventory.clear();p.inventory.currentItem=0;p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(Items.STICK));
                p.inventory.setInventorySlotContents(14,new ItemStack(ModItems.ACCESS_CARD,2,4));ItemStack valid=new ItemStack(ModItems.ACCESS_CARD,2,3);valid.setStackDisplayName("Inventory credential");p.inventory.setInventorySlotContents(20,valid);
                Block block=Block.getBlockFromName(kind==0?"vandorlabs:programmable_door":"vandorlabs:large_programmable_door");IBlockState state=block.getDefaultState().withProperty(BlockDoor.FACING,EnumFacing.SOUTH);
                for(int x=0;x<(kind==0?1:3);x++)for(int y=0;y<(kind==0?2:3);y++){BlockPos at=POS.add(x,y,0);p.world.setBlockState(at,state.withProperty(BlockDoor.HALF,y==0?BlockDoor.EnumDoorHalf.LOWER:BlockDoor.EnumDoorHalf.UPPER),2);TileEntitySpaceDoor t=(TileEntitySpaceDoor)p.world.getTileEntity(at);if(kind==1)((TileEntityLargeProgrammableDoor)t).assign(POS);t.configureSecurity(2,8);p.world.notifyBlockUpdate(at,p.world.getBlockState(at),p.world.getBlockState(at),2);}
                p.setSneaking(false);p.connection.setPlayerLocation(POS.getX()+.5,POS.getY(),POS.getZ()+2,180,0);p.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(0));p.connection.sendPacket(new net.minecraft.network.play.server.SPacketWindowItems(0,p.inventoryContainer.getInventory()));p.inventoryContainer.detectAndSendChanges();
            }).get();stage++;wait=0;return false;
        }
        if(stage==2){
            if(mc.playerController.getCurrentGameType()!=GameType.SURVIVAL||mc.player.inventory.currentItem!=0||mc.player.getHeldItemMainhand().getItem()!=Items.STICK||mc.player.inventory.getStackInSlot(20).getCount()!=2||mc.player.getDistanceSq(POS.getX()+.5,POS.getY(),POS.getZ()+2)>.05||!(mc.world.getTileEntity(POS) instanceof TileEntitySpaceDoor))return false;
            require(mc.playerController.processRightClickBlock(mc.player,mc.world,POS,EnumFacing.SOUTH,new Vec3d(POS).addVector(.5,.5,.5),EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"leaf click refused");stage++;wait=0;return false;
        }
        if(stage==3){
            if(((TileEntitySpaceDoor)mc.world.getTileEntity(POS)).keyReader().isEmpty())return false;
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=actor(mc);TileEntitySpaceDoor t=(TileEntitySpaceDoor)p.world.getTileEntity(POS);require(t.keyReader().getStackInSlot(0).getDisplayName().equals("Inventory credential"),"exact named credential retained");require(p.inventory.getStackInSlot(20).getCount()==1&&p.inventory.getStackInSlot(14).getCount()==2&&p.getHeldItemMainhand().getItem()==Items.STICK,"transfer must consume exactly one matching card and preserve held/wrong-company items");
                for(int x=0;x<(kind==0?1:3);x++)for(int y=0;y<(kind==0?2:3);y++)require(p.world.getBlockState(POS.add(x,y,0)).getValue(BlockDoor.OPEN),"every door cell opened");
                t.keyReader().takeForDrop();t.configureSecurity(0,127);for(int x=0;x<3;x++)for(int y=0;y<3;y++)p.world.setBlockToAir(POS.add(x,y,0));p.inventory.clear();
            }).get();kind++;stage=wait=0;if(kind==2){mc.getIntegratedServer().addScheduledTask(()->DoorSecurityRuntimeChecks.run(actor(mc).getServerWorld())).get();System.out.println("[vandorlabs][reprolab] door-inventory-card PASS normal/large real-Survival-packets named-card outside-hotbar exact-one consumed wrong-company/held preserved all-cells-open");return true;}
        }
        return false;
    }
    private DoorInventoryCardLiveChecks(){}
}
