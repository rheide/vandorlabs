package com.vandorlabs.client;

import com.vandorlabs.items.*;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;
import java.util.concurrent.Future;

/** Survival interactions plus every native source/destination combination and crafting preservation. */
final class DuplifierArmorRuntimeChecks {
    private static final ItemProgrammableArmor[] ITEMS={ModItems.PROGRAMMABLE_BOOTS,ModItems.PROGRAMMABLE_LEGGINGS,ModItems.PROGRAMMABLE_CHESTPLATE,ModItems.PROGRAMMABLE_HELMET};
    private static int stage,example,ticks,entity;
    private static boolean matrix;
    private static Future<?> pending;
    private static final java.util.Map<EntityEquipmentSlot,ItemStack> originalEquipment=new java.util.EnumMap<>(EntityEquipmentSlot.class);
    private static EntityPlayerMP owner(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static EntityEquipmentSlot sourceSlot(){return example==2?EntityEquipmentSlot.CHEST:example==3?EntityEquipmentSlot.HEAD:EntityEquipmentSlot.LEGS;}
    private static EntityEquipmentSlot targetSlot(){return example==0?EntityEquipmentSlot.HEAD:example==1?EntityEquipmentSlot.CHEST:example==2?EntityEquipmentSlot.LEGS:EntityEquipmentSlot.FEET;}
    private static int nativeChoice(String role,EntityEquipmentSlot slot){for(ArmorTextures.Entry e:ArmorTextures.ALL)if(e.slot==slot && e.name.startsWith(role+"_"))return e.choice;throw new IllegalStateException(role);}
    private static void require(boolean pass,String what){if(!pass)throw new IllegalStateException(what);}
    private static ItemStack armor(ItemProgrammableArmor item){ItemStack stack=new ItemStack(item);stack.setItemDamage(13);stack.setStackDisplayName("Custom Uniform");stack.addEnchantment(net.minecraft.init.Enchantments.PROTECTION,2);stack.setTagInfo("Keep",new net.minecraft.nbt.NBTTagInt(789));ItemProgrammableArmor.setTexture(stack,1);return stack;}
    private static ItemStack metadata(ItemStack stack){ItemStack copy=stack.copy();NBTTagCompound tag=copy.getTagCompound();if(tag!=null){tag.removeTag(ItemProgrammableArmor.TEXTURE_TAG);tag.removeTag(ItemProgrammableArmor.SAMPLE_TAG);}return copy;}
    private static void sameMetadata(ItemStack before,ItemStack after){require(ItemStack.areItemStacksEqual(metadata(before),metadata(after)),"only appearance changes");}
    private static void checkMatrix(EntityPlayerMP player){
        RecipeCopiedSettings recipe=new RecipeCopiedSettings();int count=0;
        for(ArmorTextures.Entry source:ArmorTextures.ALL)for(ItemProgrammableArmor item:ITEMS){
            ItemStack from=new ItemStack(ITEMS[source.slot.getIndex()]);ItemProgrammableArmor.setTexture(from,source.choice);
            ItemStack tool=new ItemStack(ModItems.DUPLIFIER);require(DuplifierArmorTextures.copyFrom(from,tool)!=null,"capture native role");
            ItemStack target=armor(item);ItemProgrammableArmor.setSample(target,"minecraft:blocks/log_oak");ItemStack before=target.copy();
            ArmorTextures.Entry expected=ArmorTextures.forSlot(source.choice,item.armorType);
            require(expected!=null && DuplifierArmorTextures.applyTo(target,tool),"native role maps to every slot");
            require(ItemProgrammableArmor.texture(target)==expected.choice && ItemProgrammableArmor.sample(target)==null,"native copy clears old sample");sameMetadata(before,target);
            InventoryCrafting grid=new InventoryCrafting(player.inventoryContainer,2,2);grid.setInventorySlotContents(0,tool);grid.setInventorySlotContents(1,before);
            ItemStack output=recipe.getCraftingResult(grid);require(!output.isEmpty() && ItemProgrammableArmor.texture(output)==expected.choice,"armor crafting applies corresponding role");
            sameMetadata(before,output);require(ItemStack.areItemStacksEqual(before,grid.getStackInSlot(1)),"recipe preview preserves input");
            require(ItemStack.areItemStacksEqual(tool,recipe.getRemainingItems(grid).get(0)),"crafting retains full Duplifier snapshot and options");
            ItemStack saved=new ItemStack(tool.writeToNBT(new NBTTagCompound()));require(DuplifierArmorTextures.applyTo(armor(item),saved),"saved clipboard round trip");
            count++;
        }
        require(count==128,"all eight roles across all source/destination slots");
        for(int choice:new int[]{0,2,ScreenHousingTextures.lightIndex(0),ScreenHousingTextures.screenIndex("porthole_off"),com.vandorlabs.tiles.CustomBlockMaterials.choice(new ItemStack(net.minecraft.init.Blocks.BRICK_BLOCK)),ScreenHousingTextures.doorIndex(7,1)})for(ItemProgrammableArmor item:ITEMS){
            ItemStack source=armor(ModItems.PROGRAMMABLE_LEGGINGS),tool=new ItemStack(ModItems.DUPLIFIER),target=armor(item);ItemProgrammableArmor.setTexture(source,choice);
            DuplifierArmorTextures.copyFrom(source,tool);ItemProgrammableArmor.setTexture(source,1);
            require(DuplifierArmorTextures.applyTo(target,tool) && ItemProgrammableArmor.texture(target)==choice,"generic texture copied unchanged and snapshot independent");
        }
        ItemStack source=armor(ModItems.PROGRAMMABLE_LEGGINGS),tool=new ItemStack(ModItems.DUPLIFIER);ItemProgrammableArmor.setSample(source,"minecraft:blocks/log_oak_top");DuplifierArmorTextures.copyFrom(source,tool);
        for(ItemProgrammableArmor item:ITEMS){ItemStack target=armor(item),before=target.copy();require(DuplifierArmorTextures.applyTo(target,tool) && "minecraft:blocks/log_oak_top".equals(ItemProgrammableArmor.sample(target)),"sampled texture copied unchanged");sameMetadata(before,target);}
        int option=DuplifierApplyOptions.OPTIONS.length-1;require(DuplifierApplyOptions.OPTIONS[option].key.equals(DuplifierArmorTextures.KEY),"new option appended without shifting old bits");
        tool.setTagInfo(DuplifierApplyOptions.TAG,new net.minecraft.nbt.NBTTagLong(0));require(DuplifierApplyOptions.enabled(DuplifierApplyOptions.mask(tool),option) && !DuplifierApplyOptions.enabled(DuplifierApplyOptions.mask(tool),0),"old unversioned masks enable armor and preserve previous disabled choices");
        DuplifierApplyOptions.setMask(tool,DuplifierApplyOptions.ALL & ~(1L<<option));ItemStack target=armor(ModItems.PROGRAMMABLE_HELMET),before=target.copy();require(!DuplifierArmorTextures.applyTo(target,tool) && ItemStack.areItemStacksEqual(before,target),"disabled armor option preserves target");
        ItemDuplifier.clearCopyState(tool);require(!ItemDuplifier.hasCopy(tool) && !DuplifierArmorTextures.applyTo(target,tool),"clear removes armor snapshot");
        NBTTagCompound block=new NBTTagCompound();block.setInteger(ProgrammableSettings.WALL_TEXTURE,2);require(!DuplifierArmorTextures.apply(target,block),"block settings cannot change armor");
        NBTTagCompound malformed=new NBTTagCompound(),appearance=new NBTTagCompound();appearance.setString("sample","../invalid");malformed.setTag(DuplifierArmorTextures.KEY,appearance);require(!DuplifierArmorTextures.apply(target,malformed) && ItemStack.areItemStacksEqual(before,target),"malformed snapshots preserve armor");
        DuplifierRuntimeChecks.run(player);
        System.out.println("[vandorlabs][reprolab] armor-duplifier-matrix PASS native=128 (all role mappings, generic/light/sample textures, metadata, crafting, clipboard NBT, masks and existing block copies)");
    }
    private static void sneak(Minecraft mc,boolean value){mc.player.movementInput.sneak=value;mc.player.connection.sendPacket(new CPacketEntityAction(mc.player,value?CPacketEntityAction.Action.START_SNEAKING:CPacketEntityAction.Action.STOP_SNEAKING));}
    private static void click(Minecraft mc,EntityEquipmentSlot slot){
        if(example>=4){require(mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"offhand air action consumed");return;}
        EntityArmorStand stand=(EntityArmorStand)mc.world.getEntityByID(entity);require(stand!=null,"stand tracked");
        double[] heights=example%2==0?new double[]{.25,.7,1.3,1.8}:new double[]{.2,.5,.7,.97};
        Vec3d point=stand.getPositionVector().addVector(0,heights[slot.getIndex()],-.1);
        require(mc.playerController.interactWithEntity(mc.player,stand,new RayTraceResult(stand,point),EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"mounted armor action consumed");
    }
    static void tick(Minecraft mc){
        try{
            if(pending!=null){if(!pending.isDone())return;pending.get();pending=null;}
            if(stage==0){
                mc.player.closeScreen();sneak(mc,false);
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP player=owner(mc);player.setGameType(GameType.SURVIVAL);player.setSneaking(false);player.setNoGravity(true);
                    if(!matrix){checkMatrix(player);matrix=true;}
                    if(entity!=0){net.minecraft.entity.Entity old=player.world.getEntityByID(entity);if(old!=null)old.setDead();}
                    ItemStack tool=new ItemStack(ModItems.DUPLIFIER);DuplifierApplyOptions.setConnected(tool,true);player.setHeldItem(EnumHand.MAIN_HAND,tool);player.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);
                    EntityArmorStand stand=new EntityArmorStand(player.world,player.posX,player.posY,player.posZ+2);stand.setNoGravity(true);
                    NBTTagCompound data=new NBTTagCompound();stand.writeEntityToNBT(data);data.setBoolean("Small",example%2==1);stand.readEntityFromNBT(data);
                    for(ItemProgrammableArmor item:ITEMS)stand.setItemStackToSlot(item.armorType,armor(item));
                    ItemStack source=stand.getItemStackFromSlot(sourceSlot());
                    if(example==0)ItemProgrammableArmor.setTexture(source,nativeChoice("hazmat",sourceSlot()));
                    else if(example==1)ItemProgrammableArmor.setTexture(source,ScreenHousingTextures.screenIndex("porthole_off"));
                    else if(example==2 || example==5)ItemProgrammableArmor.setSample(source,"minecraft:blocks/log_oak_top");
                    else ItemProgrammableArmor.setTexture(source,ArmorTextures.defaultChoice(sourceSlot()));
                    if(example>=4)player.setHeldItem(EnumHand.OFF_HAND,source.copy());
                    else {player.world.spawnEntity(stand);entity=stand.getEntityId();}
                    player.sendContainerToPlayer(player.inventoryContainer);
                });stage=1;ticks=0;return;
            }
            if(++ticks<20)return;
            if(stage==1){
                originalEquipment.clear();
                if(example<4){EntityArmorStand stand=(EntityArmorStand)mc.world.getEntityByID(entity);require(stand!=null,"stand equipment synchronized");for(ItemProgrammableArmor item:ITEMS)originalEquipment.put(item.armorType,stand.getItemStackFromSlot(item.armorType).copy());}
                sneak(mc,true);click(mc,sourceSlot());stage=2;ticks=0;return;
            }
            if(stage==2){
                require(ItemDuplifier.hasCopy(mc.player.getHeldItemMainhand()),"armor snapshot synchronized to tool");require(mc.currentScreen==null,"copy does not open settings");
                if(example>=4){
                    pending=mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP player=owner(mc);player.setHeldItem(EnumHand.OFF_HAND,armor(ModItems.PROGRAMMABLE_HELMET));player.sendContainerToPlayer(player.inventoryContainer);});stage=6;ticks=0;return;
                }
                sneak(mc,false);click(mc,targetSlot());stage=3;ticks=0;return;
            }
            if(stage==6){sneak(mc,false);click(mc,EntityEquipmentSlot.HEAD);stage=3;ticks=0;return;}
            if(stage==3){
                ItemStack target=example>=4?mc.player.getHeldItemOffhand():((EntityArmorStand)mc.world.getEntityByID(entity)).getItemStackFromSlot(targetSlot());
                if(example==2 || example==5)require("minecraft:blocks/log_oak_top".equals(ItemProgrammableArmor.sample(target)),"sample copied and client synchronized");
                else {int choice=example==0?nativeChoice("hazmat",targetSlot()):example==1?ScreenHousingTextures.screenIndex("porthole_off"):ArmorTextures.defaultChoice(example>=4?EntityEquipmentSlot.HEAD:targetSlot());require(ItemProgrammableArmor.texture(target)==choice,"native role/generic material mapped and synchronized: expected="+choice+" actual="+ItemProgrammableArmor.texture(target)+" item="+target.getDisplayName());}
                if(example<4){EntityArmorStand stand=(EntityArmorStand)mc.world.getEntityByID(entity);for(ItemProgrammableArmor item:ITEMS)if(item.armorType!=targetSlot())require(ItemStack.areItemStacksEqual(originalEquipment.get(item.armorType),stand.getItemStackFromSlot(item.armorType)),"source and other mounted pieces unchanged");}
                require(target.getItemDamage()==13 && target.isItemEnchanted() && "Custom Uniform".equals(target.getDisplayName()) && target.getTagCompound().getInteger("Keep")==789,"target metadata retained");
                require(mc.player.getHeldItemMainhand().getItem()==ModItems.DUPLIFIER && mc.currentScreen==null,"tool retained without armor equip or dialog");
                if(++example<6){stage=0;ticks=0;return;}
                pending=mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP player=owner(mc);player.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);player.sendContainerToPlayer(player.inventoryContainer);});stage=4;ticks=0;return;
            }
            if(stage==4){require(mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"options air action");stage=5;ticks=0;return;}
            if(stage==5){
                require(mc.currentScreen instanceof GuiDuplifier,"existing tool options still available");
                DuplifierRuntimeChecks.checkDialog((GuiDuplifier)mc.currentScreen);
                javax.imageio.ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new java.io.File(System.getenv("VANDOR_LABS_REPRO_OUT"),"shot_armor_duplifier_options.png"));
                ((GuiDuplifier)mc.currentScreen).actionPerformed(new GuiButton(100+DuplifierApplyOptions.OPTIONS.length-1,0,0,"Armor Texture"));stage=7;ticks=0;return;
            }
            if(stage==7){
                pending=mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP player=owner(mc);ItemStack target=armor(ModItems.PROGRAMMABLE_CHESTPLATE),before=target.copy();require(!DuplifierArmorTextures.applyTo(target,player.getHeldItemMainhand()) && ItemStack.areItemStacksEqual(target,before),"real options packet disables armor application");});stage=8;return;
            }
            if(stage==8){System.out.println("[vandorlabs][reprolab] armor-duplifier-interactions PASS cases=6 (normal/small stands, source leggings to target helmet, generic/light/sample textures, survival offhand, synchronized clipboard/equipment and options GUI)");stage=9;}
            if(stage==9)ArmorCustomTextureRuntimeChecks.tick(mc);
        }catch(Exception e){throw new IllegalStateException("Duplifier armor checks example="+example+" stage="+stage,e);}
    }
}
