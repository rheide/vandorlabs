package com.vandorlabs.client;

import com.vandorlabs.container.ContainerProgrammableArmor;
import com.vandorlabs.items.*;
import com.vandorlabs.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;
import java.util.concurrent.Future;

/** Actual stand interactions and network edits for every slot at both vanilla stand sizes. */
final class ArmorStandRuntimeChecks {
    private static final ItemProgrammableArmor[] ITEMS={ModItems.PROGRAMMABLE_BOOTS,ModItems.PROGRAMMABLE_LEGGINGS,
            ModItems.PROGRAMMABLE_CHESTPLATE,ModItems.PROGRAMMABLE_HELMET};
    private static int stage,example,ticks,entity;
    private static Future<?> pending;
    private static EntityPlayerMP owner(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static EntityEquipmentSlot slot(){return ITEMS[example%4].armorType;}
    private static int choice(){return example%4==0?com.vandorlabs.tiles.ScreenHousingTextures.lightIndex(0)
            :example%4==1?com.vandorlabs.tiles.ScreenHousingTextures.screenIndex("porthole_off"):ArmorTextures.defaultChoice(slot());}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    static void tick(Minecraft mc) {
        try {
            if(pending!=null){if(!pending.isDone())return;pending.get();pending=null;}
            if(stage==0){
                mc.player.closeScreen();mc.player.movementInput.sneak=false;
                mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketEntityAction(mc.player,
                        net.minecraft.network.play.client.CPacketEntityAction.Action.STOP_SNEAKING));
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP player=owner(mc);player.setSneaking(false);player.setGameType(GameType.SURVIVAL);
                    if(entity!=0){net.minecraft.entity.Entity old=player.world.getEntityByID(entity);if(old!=null)old.setDead();}
                    EntityArmorStand stand=new EntityArmorStand(player.world,player.posX,player.posY,player.posZ+2);
                    stand.setNoGravity(true);
                    net.minecraft.nbt.NBTTagCompound data=new net.minecraft.nbt.NBTTagCompound();stand.writeEntityToNBT(data);
                    data.setBoolean("Small",example>=4);stand.readEntityFromNBT(data);
                    for(ItemProgrammableArmor item:ITEMS){
                        ItemStack stack=new ItemStack(item);stack.setItemDamage(11);stack.setStackDisplayName("Custom Uniform");
                        stack.addEnchantment(net.minecraft.init.Enchantments.PROTECTION,1);
                        ItemProgrammableArmor.setTexture(stack,1);stand.setItemStackToSlot(item.armorType,stack);
                    }
                    player.world.spawnEntity(stand);entity=stand.getEntityId();
                    player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));
                    player.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);player.sendContainerToPlayer(player.inventoryContainer);
                });stage=1;ticks=0;return;
            }
            if(++ticks<25)return;
            EntityArmorStand stand=(EntityArmorStand)mc.world.getEntityByID(entity);
            if(stage<4)require(stand!=null,"stand synchronized");
            if(stage==1){
                double[] height=example<4?new double[]{.25,.7,1.3,1.8}:new double[]{.2,.5,.7,.97};
                Vec3d point=stand.getPositionVector().addVector(0,height[example%4],-.1);
                require(ItemConfigurizer.armorSlot(stand,height[example%4])==slot(),"vanilla click region selects matching slot");
                require(mc.playerController.interactWithEntity(mc.player,stand,new RayTraceResult(stand,point),EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"real stand interaction consumed");
                stage=2;ticks=0;return;
            }
            if(stage==2){
                require(mc.currentScreen instanceof GuiProgrammableArmor,"mounted armor picker opened in survival");
                require(((ContainerProgrammableArmor)mc.player.openContainer).slot==slot(),"picker bound to clicked equipment");
                ((GuiProgrammableArmor)mc.currentScreen).select(choice());stage=3;ticks=0;return;
            }
            if(stage==3){
                for(ItemProgrammableArmor item:ITEMS){
                    ItemStack stack=stand.getItemStackFromSlot(item.armorType);
                    require(ItemProgrammableArmor.texture(stack)==(item.armorType==slot()?choice():1),"only clicked equipment changed and synchronized");
                    require(stack.getItemDamage()==11 && stack.isItemEnchanted() && "Custom Uniform".equals(stack.getDisplayName()),"equipment metadata preserved");
                }
                require(mc.player.getHeldItemMainhand().getItem()==ModItems.CONFIGURIZER,"tool retained");
                if(example==3)javax.imageio.ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),
                        "png",new java.io.File(System.getenv("VANDOR_LABS_REPRO_OUT"),"shot_armor_stand_picker.png"));
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP player=owner(mc);ContainerProgrammableArmor container=(ContainerProgrammableArmor)player.openContainer;
                    ItemStack armor=container.armor;
                    player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
                    require(!MessageProgrammableArmor.apply(player,container.windowId,2),"missing Configurizer rejected");
                    player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));
                    container.stand.setItemStackToSlot(container.slot,armor.copy());
                    require(!MessageProgrammableArmor.apply(player,container.windowId,2),"replaced equipment rejected");
                    container.stand.setItemStackToSlot(container.slot,armor);
                    double x=player.posX,y=player.posY,z=player.posZ;player.setPosition(x+100,y,z);
                    require(!MessageProgrammableArmor.apply(player,container.windowId,2),"distant stand rejected");
                    player.setPosition(x,y,z);container.stand.setDead();
                    require(!MessageProgrammableArmor.apply(player,container.windowId,2),"removed stand rejected");
                    player.closeScreen();player.sendContainerToPlayer(player.inventoryContainer);
                });stage=4;return;
            }
            if(stage==4){
                if(++example<8){stage=0;ticks=0;return;}
                System.out.println("[vandorlabs][reprolab] armor-stand-configuration PASS cases=8 (four slots, normal/small stands, survival Configurizer, equipment synchronization, metadata and stale target guards)");
                stage=5;mc.shutdown();
            }
        }catch(Exception e){throw new IllegalStateException("Armor stand checks example="+example+" stage="+stage,e);}
    }
}
