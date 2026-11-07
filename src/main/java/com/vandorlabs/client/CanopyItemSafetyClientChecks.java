package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.*;

/** Narrow actual Survival packet path; does not enter carried or unrelated canopy matrices. */
@SideOnly(Side.CLIENT)
final class CanopyItemSafetyClientChecks {
    private static final BlockPos CLICKED=new BlockPos(40,30,40),FOREIGN=CLICKED.up().east(2),FRONT=CLICKED.up().west().north();
    private static int fixture,stage,ticks,delay;private static volatile int serverUses;private static Future<?> pending;private static boolean registered,done;
    private static final Observer OBSERVER=new Observer();
    private static BlockCanopy block(){return (BlockCanopy)net.minecraft.block.Block.getBlockFromName("vandorlabs:"+(fixture==0?"regular_canopy_glass":"front_canopy_glass"));}
    private static ItemStack stack(){ItemStack result=new ItemStack(block(),2);NBTTagCompound tag=new NBTTagCompound(),generic=new NBTTagCompound();if(fixture==0){generic.setIntArray("CanopyFootprint",new int[]{0,0,0,2,0,0});tag.setString("ProofMarker","preserve-source-input");}else{generic.setInteger("CanopyDepth",42);tag.setInteger("CanopyDepth",17);}tag.setTag("BlockEntityTag",generic);result.setTagCompound(tag);return result;}
    private static void need(boolean value,String text){if(!value)throw new IllegalStateException("canopy-item-placement fixture="+fixture+" stage="+stage+" "+text);}
    public static final class Observer {@SubscribeEvent public void used(PlayerInteractEvent.RightClickBlock event){if(!event.getWorld().isRemote&&event.getPos().equals(CLICKED)&&event.getEntityPlayer().getHeldItem(event.getHand()).getItem()==Item.getItemFromBlock(block()))serverUses++;}}
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static void setup(Minecraft mc){EntityPlayerMP player=actor(mc);World world=player.world;player.closeScreen();if(fixture==0)for(BlockPos pos:BlockPos.getAllInBox(CLICKED.add(-4,-2,-4),CLICKED.add(5,6,5)))world.setBlockToAir(pos);world.setBlockState(CLICKED,Blocks.STONE.getDefaultState(),3);world.setBlockState(FOREIGN,Blocks.STONE.getDefaultState(),3);player.interactionManager.setGameType(GameType.SURVIVAL);player.inventory.currentItem=0;player.setHeldItem(EnumHand.MAIN_HAND,stack());player.setPositionAndUpdate(40.5,31,43.5);player.rotationYaw=player.prevRotationYaw=180;player.rotationPitch=player.prevRotationPitch=(float)Math.toDegrees(Math.atan2(player.getEyeHeight(),3));player.capabilities.allowFlying=player.capabilities.isFlying=true;player.sendPlayerAbilities();player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();player.connection.sendPacket(new SPacketHeldItemChange(0));serverUses=0;}
    static void tick(Minecraft mc)throws Exception {
        if(done)return;if(++ticks>1200)throw new IllegalStateException("canopy-item-placement timeout fixture="+fixture+" stage="+stage+" clientMode="+mc.playerController.getCurrentGameType()+" selected="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPositionVector());
        if(pending!=null){if(!pending.isDone())return;pending.get();pending=null;}
        if(stage==0){if(!registered){MinecraftForge.EVENT_BUS.register(OBSERVER);registered=true;}mc.displayGuiScreen(null);mc.gameSettings.thirdPersonView=0;pending=mc.getIntegratedServer().addScheduledTask(()->setup(mc));stage=1;return;}
        if(stage==1){
            if(mc.playerController.getCurrentGameType()!=GameType.SURVIVAL)mc.playerController.setGameType(GameType.SURVIVAL);mc.player.capabilities.allowFlying=mc.player.capabilities.isFlying=true;mc.player.motionY=0;mc.player.inventory.currentItem=0;mc.playerController.updateController();ItemStack held=mc.player.getHeldItemMainhand();
            if(mc.player.capabilities.isCreativeMode||mc.playerController.getCurrentGameType()!=GameType.SURVIVAL||held.getCount()!=2||held.getItem()!=Item.getItemFromBlock(block())||!ItemStack.areItemStackTagsEqual(held,stack())||mc.player.getDistanceSq(40.5,31,43.5)>.01||mc.world.getBlockState(CLICKED).getBlock()!=Blocks.STONE||mc.world.getBlockState(FOREIGN).getBlock()!=Blocks.STONE)return;
            mc.player.rotationYaw=mc.player.prevRotationYaw=180;mc.player.rotationPitch=mc.player.prevRotationPitch=(float)Math.toDegrees(Math.atan2(mc.player.getEyeHeight(),3));mc.player.prevPosX=mc.player.lastTickPosX=mc.player.posX;mc.player.prevPosY=mc.player.lastTickPosY=mc.player.posY;mc.player.prevPosZ=mc.player.lastTickPosZ=mc.player.posZ;mc.entityRenderer.getMouseOver(1);
            need(mc.objectMouseOver!=null&&mc.objectMouseOver.typeOfHit==RayTraceResult.Type.BLOCK&&mc.objectMouseOver.getBlockPos().equals(CLICKED)&&mc.objectMouseOver.sideHit==EnumFacing.UP,"actual visible support ray "+mc.objectMouseOver);
            mc.player.connection.sendPacket(new CPacketPlayer.PositionRotation(mc.player.posX,mc.player.posY,mc.player.posZ,mc.player.rotationYaw,mc.player.rotationPitch,false));
            EnumActionResult action=mc.playerController.processRightClickBlock(mc.player,mc.world,CLICKED,EnumFacing.UP,mc.objectMouseOver.hitVec,EnumHand.MAIN_HAND);need(action==(fixture==0?EnumActionResult.FAIL:EnumActionResult.SUCCESS),"client prediction result="+action);stage=2;delay=0;return;
        }
        if(stage==2){if(++delay<20)return;pending=mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP player=actor(mc);need(player.interactionManager.getGameType()==GameType.SURVIVAL&&!player.capabilities.isCreativeMode&&player.inventory.currentItem==0,"server mode/slot");need(serverUses>0,"actual server right-click packet observed");need(player.world.getBlockState(CLICKED).getBlock()==Blocks.STONE&&player.world.getBlockState(FOREIGN).getBlock()==Blocks.STONE,"clicked support and foreign owner preserved");ItemStack held=player.getHeldItemMainhand();need(held.getItem()==Item.getItemFromBlock(block())&&ItemStack.areItemStackTagsEqual(held,stack())&&held.getCount()==(fixture==0?2:1),"server inventory count/NBT="+held);if(fixture==0)need(player.world.isAirBlock(CLICKED.up())&&TileEntityCanopy.at(player.world,CLICKED.up())==null,"refusal publishes no root");else{TileEntityCanopy root=TileEntityCanopy.at(player.world,FRONT);need(root!=null&&root.large.depth==17&&root.centeredPlacement&&root.hollowPlacement,"canonical partial generic/outer precedence");}});stage=3;return;}
        if(stage==3){ItemStack held=mc.player.getHeldItemMainhand();if(held.getCount()!=(fixture==0?2:1)||held.getItem()!=Item.getItemFromBlock(block())||!ItemStack.areItemStackTagsEqual(held,stack()))return;if(fixture==1){TileEntityCanopy root=TileEntityCanopy.at(mc.world,FRONT);if(root==null||root.large.depth!=17)return;}need(mc.world.getBlockState(FOREIGN).getBlock()==Blocks.STONE,"client foreign owner");fixture++;stage=0;ticks=0;if(fixture==2){done=true;MinecraftForge.EVENT_BUS.unregister(OBSERVER);System.out.println("[vandorlabs][reprolab] canopy-item-placement PASS cases=2 actual-Survival-packets foreign-and-support-preserved unchanged-refused-stack generic-outer-precedence");mc.shutdown();}}
    }
}
