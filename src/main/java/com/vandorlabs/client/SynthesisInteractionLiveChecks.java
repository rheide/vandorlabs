package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.rack.ModuleBatteryData;
import com.vandorlabs.container.ContainerSynthesisTower;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;
import net.minecraftforge.fml.relauncher.*;

/** Real Survival packets after rendering, with both sides' interaction state synchronized. */
@SideOnly(Side.CLIENT)
final class SynthesisInteractionLiveChecks {
    private static final BlockPos ROOT=new BlockPos(180,220,180);
    private static int action,stage,ticks;
    private static volatile String serverStatus="setup";
    private static Vec3d eye;
    private static ItemStack expected=ItemStack.EMPTY;
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static TileEntityConstructor tile(EntityPlayerMP p){return (TileEntityConstructor)p.world.getTileEntity(ROOT);}
    private static void server(Minecraft mc,Runnable task)throws Exception{mc.getIntegratedServer().addScheduledTask(task).get();}
    static boolean tick(Minecraft mc)throws Exception{
        if(action==7){System.out.println("[vandorlabs] synthesis-tower PASS real Survival packets ordinary/Configurizer dialog33 mounted battery exact charge chip insertion/extraction completed core exactly once");return true;}
        if(++ticks>200)throw new IllegalStateException("Synthesis interaction timeout action="+action+" stage="+stage+" mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPositionVector()+" expected="+expected+" server="+serverStatus);
        if(stage==0){
            mc.player.closeScreen();mc.player.setSneaking(false);mc.playerController.setGameType(GameType.SURVIVAL);mc.player.inventory.currentItem=0;mc.getConnection().sendPacket(new CPacketHeldItemChange(0));
            Vec3d target=target();eye=target.addVector(0,0,-2.7);
            expected=action==1?new ItemStack(ModItems.CONFIGURIZER):action==2?new ItemStack(ModItems.BATTERY_MODULE):action==4?new ItemStack(ModItems.DATA_CHIP):ItemStack.EMPTY;
            if(action==2)ModuleBatteryData.receive(expected,4321,false);
            server(mc,()->{
                EntityPlayerMP p=player(mc);TileEntityConstructor t=tile(p);p.closeScreen();p.setSneaking(false);p.interactionManager.setGameType(GameType.SURVIVAL);p.inventory.clear();p.inventory.currentItem=0;p.setHeldItem(EnumHand.MAIN_HAND,expected.copy());p.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();
                if(action==0){for(int slot=0;slot<3;slot++)t.synthesisInventory().setStackInSlot(slot,ItemStack.EMPTY);t.clear();NBTTagCompound saved=t.writeToNBT(new NBTTagCompound());saved.getCompoundTag("Synthesis").setBoolean("Active",false);saved.getCompoundTag("Synthesis").setInteger("Work",0);t.readFromNBT(saved);t.configureSynthesisRedstone(1,com.vandorlabs.redstone.ChannelList.EMPTY);}
                if(action==6)t.synthesisInventory().setStackInSlot(1,com.vandorlabs.machines.SynthesisRuntime.result(0));
                p.connection.setPlayerLocation(eye.x,eye.y-p.getEyeHeight(),eye.z,0,0);p.inventoryContainer.detectAndSendChanges();p.world.notifyBlockUpdate(ROOT,p.world.getBlockState(ROOT),p.world.getBlockState(ROOT),3);
            });stage=1;ticks=0;return false;
        }
        if(stage==1){
            if(ticks<10||mc.playerController.getCurrentGameType()!=GameType.SURVIVAL||mc.player.inventory.currentItem!=0||!ItemStack.areItemStacksEqual(mc.player.getHeldItemMainhand(),expected)||mc.player.getPositionEyes(1).squareDistanceTo(eye)>.025||Math.abs(mc.player.rotationYaw)>.1||Math.abs(mc.player.rotationPitch)>.1)return false;
            boolean synced=mc.getIntegratedServer().callFromMainThread(()->{EntityPlayerMP p=player(mc);serverStatus="mode="+p.interactionManager.getGameType()+" slot="+p.inventory.currentItem+" held="+p.getHeldItemMainhand()+" pos="+p.getPositionVector();return p.interactionManager.getGameType()==GameType.SURVIVAL&&p.inventory.currentItem==0&&ItemStack.areItemStacksEqual(p.getHeldItemMainhand(),expected)&&p.getPositionEyes(1).squareDistanceTo(eye)<.025&&Math.abs(p.rotationYaw)<.1&&Math.abs(p.rotationPitch)<.1;}).get();if(!synced)return false;
            Vec3d hit=target();BlockPos clicked=new BlockPos(hit.x,hit.y,ROOT.getZ());
            require(mc.playerController.processRightClickBlock(mc.player,mc.world,clicked,EnumFacing.NORTH,new Vec3d(hit.x,hit.y,ROOT.getZ()),EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"real block packet accepted");stage=2;ticks=0;return false;
        }
        if(ticks<5)return false;
        if(action<2){if(!(mc.currentScreen instanceof GuiSynthesisTower))return false;boolean valid=mc.getIntegratedServer().callFromMainThread(()->player(mc).openContainer instanceof ContainerSynthesisTower).get();if(!valid)return false;}
        else{
            boolean valid=mc.getIntegratedServer().callFromMainThread(()->{
                EntityPlayerMP p=player(mc);TileEntityConstructor t=tile(p);serverStatus="mode="+p.interactionManager.getGameType()+" slot="+p.inventory.currentItem+" held="+p.getHeldItemMainhand()+" pos="+p.getPositionVector()+" battery="+t.batteryEnergy()+" chip="+t.getStackInSlot(0)+" output="+t.synthesisInventory().getStackInSlot(1);
                if(action==2)return p.getHeldItemMainhand().isEmpty()&&ModuleBatteryData.energy(t.synthesisInventory().getStackInSlot(2))==4321&&count(p,ModItems.BATTERY_MODULE)==0;
                if(action==3)return t.synthesisInventory().getStackInSlot(2).isEmpty()&&ModuleBatteryData.energy(p.getHeldItemMainhand())==4321&&count(p,ModItems.BATTERY_MODULE)==1;
                if(action==4)return p.getHeldItemMainhand().isEmpty()&&t.getStackInSlot(0).getItem()==ModItems.DATA_CHIP&&count(p,ModItems.DATA_CHIP)==0;
                if(action==5)return t.getStackInSlot(0).isEmpty()&&count(p,ModItems.DATA_CHIP)==1;
                return t.synthesisInventory().getStackInSlot(1).isEmpty()&&count(p,com.vandorlabs.machines.SynthesisRuntime.result(0).getItem())==1;
            }).get();if(!valid)return false;
        }
        action++;stage=0;ticks=0;return false;
    }
    private static int count(EntityPlayerMP p,net.minecraft.item.Item item){int count=0;for(int slot=0;slot<p.inventory.getSizeInventory();slot++){ItemStack stack=p.inventory.getStackInSlot(slot);if(stack.getItem()==item)count+=stack.getCount();}return count;}
    private static Vec3d target(){return action==2||action==3?new Vec3d(180.67,220.56,180):action==4||action==5?new Vec3d(180.68,221.5,180.35):action==6?new Vec3d(181.5,222.625,181.125):new Vec3d(181.2,221.5,180);}
    private static void require(boolean valid,String message){if(!valid)throw new IllegalStateException("Synthesis interaction action="+action+": "+message);}
}
