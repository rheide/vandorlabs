package com.vandorlabs.client;

import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.container.ContainerProgrammableArmor;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.Future;

/** Select actual inventory samples through the armor Custom row and reopen its bound server dialog. */
final class ArmorCustomTextureRuntimeChecks {
    private static final ItemProgrammableArmor[] ITEMS={ModItems.PROGRAMMABLE_HELMET,ModItems.PROGRAMMABLE_LEGGINGS,ModItems.PROGRAMMABLE_CHESTPLATE,ModItems.PROGRAMMABLE_BOOTS};
    private static int stage,example,ticks,entity,window;
    private static Future<?> pending;
    private static EntityPlayerMP owner(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static boolean mounted(){return example%2==0;}
    private static ItemStack sample(){
        if(example==0)return new ItemStack(net.minecraft.init.Blocks.BRICK_BLOCK,12);
        if(example==1)return new ItemStack(net.minecraft.init.Blocks.PLANKS,12,2);
        if(example==3)return new ItemStack(net.minecraft.init.Items.IRON_DOOR,12);
        ItemStack stack=new ItemStack(net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_door"),12);
        NBTTagCompound settings=new NBTTagCompound();settings.setInteger("SpaceDesign",7);settings.setInteger("SpaceDetail",1);stack.setTagInfo("SpaceDoorSettings",settings);return stack;
    }
    private static int choice(){return CustomBlockMaterials.choice(sample());}
    private static void require(boolean pass,String message){if(!pass)throw new IllegalStateException(message);}
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static HousingTextureList list(Minecraft mc)throws Exception{return (HousingTextureList)field(GuiProgrammableArmor.class,"textures").get(mc.currentScreen);}
    private static void checkSelected(Minecraft mc)throws Exception {
        HousingTextureList list=list(mc);require(list.selected()==choice(),"custom armor selection retained after reopen");
        require(field(HousingTextureList.class,"selected").getInt(list)==-100000,"custom sample represented by Custom row");
        List<?> rows=(List<?>)field(HousingTextureList.class,"rows").get(list);int row=rows.indexOf(-100000),scroll=field(HousingTextureList.class,"scroll").getInt(list),count=field(HousingTextureList.class,"count").getInt(list);
        require(row>=scroll && row<scroll+count,"selected Custom row is visible");
    }
    private static ItemStack armor(Minecraft mc){return mounted()?((EntityArmorStand)mc.world.getEntityByID(entity)).getItemStackFromSlot(ITEMS[example].armorType):mc.player.getHeldItemOffhand();}
    private static void open(Minecraft mc){
        if(mounted()){
            EntityArmorStand stand=(EntityArmorStand)mc.world.getEntityByID(entity);double height=example==0?1.8:1.3;
            require(mc.playerController.interactWithEntity(mc.player,stand,new RayTraceResult(stand,stand.getPositionVector().addVector(0,height,-.1)),EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"Configurator opens mounted armor");
        }else{
            mc.player.movementInput.sneak=true;mc.player.connection.sendPacket(new CPacketEntityAction(mc.player,CPacketEntityAction.Action.START_SNEAKING));
            require(mc.playerController.processRightClick(mc.player,mc.world,EnumHand.OFF_HAND)==EnumActionResult.SUCCESS,"survival offhand armor picker");
        }
    }
    static void tick(Minecraft mc){
        try{
            if(pending!=null){if(!pending.isDone())return;pending.get();pending=null;}
            if(stage==0){
                mc.player.closeScreen();mc.player.movementInput.sneak=false;mc.player.connection.sendPacket(new CPacketEntityAction(mc.player,CPacketEntityAction.Action.STOP_SNEAKING));
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP player=owner(mc);player.setGameType(GameType.SURVIVAL);player.setSneaking(false);player.setNoGravity(true);player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));player.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);
                    player.inventory.setInventorySlotContents(9,sample());
                    if(entity!=0){net.minecraft.entity.Entity old=player.world.getEntityByID(entity);if(old!=null)old.setDead();}
                    ItemStack armor=new ItemStack(ITEMS[example]);armor.setItemDamage(17);armor.setStackDisplayName("Custom Uniform");armor.addEnchantment(net.minecraft.init.Enchantments.PROTECTION,2);armor.setTagInfo("Keep",new net.minecraft.nbt.NBTTagInt(234));
                    if(mounted()){EntityArmorStand stand=new EntityArmorStand(player.world,player.posX,player.posY,player.posZ+2);stand.setNoGravity(true);stand.setItemStackToSlot(ITEMS[example].armorType,armor);player.world.spawnEntity(stand);entity=stand.getEntityId();}
                    else player.setHeldItem(EnumHand.OFF_HAND,armor);
                    player.sendContainerToPlayer(player.inventoryContainer);
                });stage=1;ticks=0;return;
            }
            if(++ticks<20)return;
            if(stage==1){open(mc);stage=2;ticks=0;return;}
            if(stage==2){
                require(mc.currentScreen instanceof GuiProgrammableArmor,"authorized armor dialog opened");window=mc.player.openContainer.windowId;
                HousingTextureList list=list(mc);List<?> rows=(List<?>)field(HousingTextureList.class,"rows").get(list);int count=field(HousingTextureList.class,"count").getInt(list),row=rows.indexOf(-100000);
                require(row>=0,"Custom entry present alongside native Armor choices");int scroll=Math.max(0,rows.size()-count);field(HousingTextureList.class,"scroll").setInt(list,scroll);
                list.click(field(HousingTextureList.class,"x").getInt(list)+8,field(HousingTextureList.class,"y").getInt(list)+(row-scroll)*22+8,0);stage=3;ticks=0;return;
            }
            if(stage==3){
                require(mc.currentScreen instanceof GuiCustomTexture,"existing custom inventory sample dialog opened");
                GuiCustomTexture gui=(GuiCustomTexture)mc.currentScreen;int left=(gui.width-252)/2,top=(gui.height-198)/2;
                gui.mouseClicked(left+47,top+81,0);gui.mouseReleased(left+126,top+45,0);
                require(mc.currentScreen instanceof GuiProgrammableArmor && mc.player.openContainer.windowId==window,"sample dialog returns to authorized armor container");checkSelected(mc);stage=4;ticks=0;return;
            }
            if(stage==4){
                ItemStack armor=armor(mc);require(ItemProgrammableArmor.texture(armor)==choice(),"selected Custom armor synchronized");
                require(armor.getItemDamage()==17 && armor.isItemEnchanted() && "Custom Uniform".equals(armor.getDisplayName()) && armor.getTagCompound().getInteger("Keep")==234,"custom sample preserves armor metadata");
                require(ItemStack.areItemStacksEqual(sample(),mc.player.inventory.getStackInSlot(9)),"sample inventory stack not consumed or changed");
                String sprite=ScreenHousingTextures.fullTexture(choice());
                for(net.minecraft.client.renderer.block.model.BakedQuad q:mc.getRenderItem().getItemModelWithOverrides(armor,mc.world,mc.player).getQuads(null,null,0))require(sprite.equals(q.getSprite().getIconName()),"Custom inventory silhouette uses correct texture");
                String worn=ITEMS[example].getArmorTexture(armor,mc.player,ITEMS[example].armorType,null);require(mc.getTextureManager().getTexture(new ResourceLocation(worn)) instanceof net.minecraft.client.renderer.texture.DynamicTexture,"Custom worn texture generated");
                mc.player.closeScreen();pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP player=owner(mc);ItemStack stack=mounted()?((EntityArmorStand)player.world.getEntityByID(entity)).getItemStackFromSlot(ITEMS[example].armorType):player.getHeldItemOffhand();
                    require(ItemProgrammableArmor.texture(stack)==choice(),"server accepted custom sample through actual armor packet");
                    require(ItemStack.areItemStacksEqual(sample(),player.inventory.getStackInSlot(9)),"server inventory sample unchanged");
                    require(ItemProgrammableArmor.texture(new ItemStack(stack.writeToNBT(new NBTTagCompound())))==choice(),"Custom armor choice persists in NBT");
                });stage=7;ticks=0;return;
            }
            if(stage==7){
                mc.getToastGui().clear();open(mc);stage=5;ticks=0;return;
            }
            if(stage==5){
                if(!(mc.currentScreen instanceof GuiProgrammableArmor)){
                    EntityPlayerMP player=owner(mc);net.minecraft.entity.Entity stand=player.world.getEntityByID(entity);
                    throw new IllegalStateException("armor picker reopened screen="+mc.currentScreen+" main="+player.getHeldItemMainhand()+" container="+player.openContainer+" stand="+stand+" distance="+(stand==null?-1:player.getDistanceSq(stand))+" clientMain="+mc.player.getHeldItemMainhand());
                }checkSelected(mc);
                if(example==0)javax.imageio.ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new java.io.File(System.getenv("VANDOR_LABS_REPRO_OUT"),"shot_armor_custom_picker.png"));
                if(++example<4){stage=0;ticks=0;return;}
                System.out.println("[vandorlabs][reprolab] armor-custom-picker PASS cases=4 (mounted/offhand survival dialogs, block metadata, vanilla/programmed doors, non-consuming inventory drag, authorized packets, reopen selection, NBT, icons and worn textures)");stage=6;mc.shutdown();
            }
        }catch(Exception e){throw new IllegalStateException("Armor Custom checks example="+example+" stage="+stage,e);}
    }
}
