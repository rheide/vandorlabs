package com.vandorlabs.client;

import com.vandorlabs.items.*;
import com.vandorlabs.network.*;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.world.GameType;
import java.io.File;
import java.util.*;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;

/** Exercise every native design through actual picker packets, slot guards and worn rendering. */
final class RoleArmorRuntimeChecks {
    private static final ItemProgrammableArmor[] ITEMS={ModItems.PROGRAMMABLE_HELMET,ModItems.PROGRAMMABLE_CHESTPLATE,
            ModItems.PROGRAMMABLE_LEGGINGS,ModItems.PROGRAMMABLE_BOOTS};
    private static int stage,piece,design,preview,ticks,icons;
    private static Future<?> pending;
    private static List<ArmorTextures.Entry> choices;
    private static EntityPlayerMP owner(Minecraft mc) {return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static List<ArmorTextures.Entry> choices(ItemProgrammableArmor item) {
        List<ArmorTextures.Entry> result=new ArrayList<>();
        for(ArmorTextures.Entry entry:ArmorTextures.ALL)if(entry.slot==item.armorType)result.add(entry);
        return result;
    }
    static void tick(Minecraft mc,File output) {
        try {
            if(pending!=null) {if(!pending.isDone())return;pending.get();pending=null;}
            if(stage==0) {
                choices=choices(ITEMS[piece]);design=0;
                require(choices.size()==7,"seven role choices per piece");
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP player=owner(mc);player.setGameType(GameType.CREATIVE);
                    player.inventory.currentItem=0;
                    ItemStack stack=new ItemStack(ITEMS[piece]);stack.setItemDamage(23);
                    NBTTagCompound tag=new NBTTagCompound();tag.setString("RoleTest","preserve");stack.setTagCompound(tag);
                    player.setHeldItem(EnumHand.MAIN_HAND,stack);
                    player.setItemStackToSlot(ITEMS[piece].armorType,ItemStack.EMPTY);
                    player.sendContainerToPlayer(player.inventoryContainer);
                });stage=1;ticks=0;return;
            }
            if(++ticks<20)return;
            if(stage==1) {
                require(mc.player.getItemStackFromSlot(ITEMS[piece].armorType).isEmpty(),"empty equipment slot fixture synchronized");
                mc.player.movementInput.sneak=true;
                mc.player.connection.sendPacket(new CPacketEntityAction(mc.player,CPacketEntityAction.Action.START_SNEAKING));
                require(mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"role picker open action");
                stage=2;ticks=0;return;
            }
            if(stage==2) {
                require(mc.currentScreen instanceof GuiProgrammableArmor,"role picker opened by server");
                checkFilter((GuiProgrammableArmor)mc.currentScreen,mc.player.getHeldItemMainhand());
                ArmorTextures.Entry wrong=null;
                for(ArmorTextures.Entry entry:ArmorTextures.ALL)if(entry.slot!=ITEMS[piece].armorType) {wrong=entry;break;}
                require(wrong!=null,"wrong-slot test choice exists");
                PacketHandler.INSTANCE.sendToServer(new MessageProgrammableArmor(mc.player.openContainer.windowId,wrong.choice));
                stage=3;ticks=0;return;
            }
            if(stage==3) {
                require(ItemProgrammableArmor.texture(mc.player.getHeldItemMainhand())==0,"server rejects real wrong-slot packet");
                ((GuiProgrammableArmor)mc.currentScreen).select(choices.get(design).choice);
                stage=4;ticks=0;return;
            }
            if(stage==4) {
                ArmorTextures.Entry entry=choices.get(design);
                ItemStack stack=mc.player.getHeldItemMainhand();
                require(ItemProgrammableArmor.texture(stack)==entry.choice,"native choice packet synchronized");
                require(ITEMS[piece].getArmorTexture(stack,mc.player,ITEMS[piece].armorType,null).equals(entry.worn),"exact corresponding worn atlas");
                try(java.io.InputStream stream=mc.getResourceManager().getResource(new ResourceLocation(entry.worn)).getInputStream()) {
                    java.awt.image.BufferedImage image=ImageIO.read(stream);
                    require(image!=null && image.getWidth()==64 && image.getHeight()==32,"native atlas decoded");
                }
                checkIcon(mc,stack,entry);
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    ItemStack saved=owner(mc).getHeldItemMainhand();
                    require(ItemProgrammableArmor.texture(saved)==entry.choice && saved.getItemDamage()==23
                            && "preserve".equals(saved.getTagCompound().getString("RoleTest")),"native edit preserves unrelated NBT");
                    ItemStack restored=new ItemStack(saved.writeToNBT(new NBTTagCompound()));
                    require(ItemStack.areItemStacksEqual(saved,restored),"native selection saves and reloads");
                });
                if(design==2)shot(mc,output,"armor_role_picker_"+ITEMS[piece].armorType.getName());
                if(++design<choices.size()) {
                    ((GuiProgrammableArmor)mc.currentScreen).select(choices.get(design).choice);ticks=0;return;
                }
                mc.player.closeScreen();mc.player.movementInput.sneak=false;
                mc.player.connection.sendPacket(new CPacketEntityAction(mc.player,CPacketEntityAction.Action.STOP_SNEAKING));
                stage=5;ticks=0;return;
            }
            if(stage==5) {
                require(mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"role armor equips normally");
                stage=6;ticks=0;return;
            }
            if(stage==6) {
                ItemStack equipped=mc.player.getItemStackFromSlot(ITEMS[piece].armorType);
                require(equipped.getItem()==ITEMS[piece] && ItemProgrammableArmor.texture(equipped)==choices.get(choices.size()-1).choice,"role equipment sync");
                if(++piece<ITEMS.length) {stage=0;ticks=0;return;}
                require(icons==28,"every native inventory variant checked");
                stage=7;ticks=0;return;
            }
            if(stage==7) {
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP player=owner(mc);
                    player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);
                    for(ItemProgrammableArmor item:ITEMS) {
                        ItemStack stack=new ItemStack(item);ItemProgrammableArmor.setTexture(stack,choices(item).get(preview).choice);
                        player.setItemStackToSlot(item.armorType,stack);
                    }
                    player.sendContainerToPlayer(player.inventoryContainer);
                });
                mc.displayGuiScreen(new Preview());stage=8;ticks=0;return;
            }
            if(stage==8) {
                for(ItemProgrammableArmor item:ITEMS)require(ItemProgrammableArmor.texture(mc.player.getItemStackFromSlot(item.armorType))==choices(item).get(preview).choice,"complete role preview synchronized");
                shot(mc,output,"armor_role_set_"+choices(ITEMS[0]).get(preview).name.replace("_helmet",""));
                if(++preview<7) {stage=7;ticks=0;return;}
                System.out.println("[vandorlabs][reprolab] role-armor PASS choices=28 icons=28 sets=7 (slot-filtered menus, wrong-slot packets rejected, native atlas mapping, NBT persistence, equip and padded icons)");
                stage=9;mc.shutdown();
            }
        } catch(Exception e) {throw new IllegalStateException("Role armor check piece="+piece+" design="+design+" stage="+stage,e);}
    }
    @SuppressWarnings("unchecked") private static Map<Integer,HousingTextureList.Option> options(HousingTextureList list) throws Exception {
        java.lang.reflect.Field field=HousingTextureList.class.getDeclaredField("options");field.setAccessible(true);
        return (Map<Integer,HousingTextureList.Option>)field.get(list);
    }
    private static void checkFilter(GuiProgrammableArmor gui,ItemStack stack) throws Exception {
        java.lang.reflect.Field field=GuiProgrammableArmor.class.getDeclaredField("textures");field.setAccessible(true);
        Map<Integer,HousingTextureList.Option> entries=options((HousingTextureList)field.get(gui));
        int nativeCount=0;
        for(HousingTextureList.Option option:entries.values()) {
            require(ItemProgrammableArmor.validTexture(stack,option.choice),"every listed choice valid for piece");
            if("Armor".equals(option.category)) {
                require(ArmorTextures.fits(option.choice,ITEMS[piece].armorType),"Armor menu contains only correct slot");nativeCount++;
            }
        }
        require(nativeCount==7 && entries.size()>nativeCount,"seven native designs plus existing block materials");
        Map<Integer,HousingTextureList.Option> blocks=options(new HousingTextureList(0,0,250,0));
        for(HousingTextureList.Option option:blocks.values())require(!"Armor".equals(option.category) && ArmorTextures.entry(option.choice)==null,"Armor category absent from block picker");
        for(ArmorTextures.Entry entry:ArmorTextures.ALL) {
            require(entries.containsKey(entry.choice)==(entry.slot==ITEMS[piece].armorType),"matching designs included, other pieces excluded");
            require(!ScreenHousingTextures.validChoice(entry.choice),"armor choice cannot be accepted as block material");
        }
    }
    private static void checkIcon(Minecraft mc,ItemStack stack,ArmorTextures.Entry entry) {
        IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);
        org.apache.commons.lang3.tuple.Pair<? extends IBakedModel,javax.vecmath.Matrix4f> gui=
                model.handlePerspective(ItemCameraTransforms.TransformType.GUI);
        List<BakedQuad> quads=gui.getLeft().getQuads(null,null,0);require(!quads.isEmpty(),"native icon has geometry");
        for(BakedQuad quad:quads) {
            require(quad.getSprite().getIconName().equals(entry.icon),"exact corresponding native icon");
            int[] data=quad.getVertexData();int stride=data.length/4;
            for(int v=0;v<4;v++) {
                javax.vecmath.Point3f point=new javax.vecmath.Point3f(
                        Float.intBitsToFloat(data[v*stride])-.5F,
                        Float.intBitsToFloat(data[v*stride+1])-.5F,
                        Float.intBitsToFloat(data[v*stride+2])-.5F);
                if(gui.getRight()!=null)gui.getRight().transform(point);
                require(point.x+.5F>=1F/16F && point.x+.5F<=15F/16F
                        && point.y+.5F>=1F/16F && point.y+.5F<=15F/16F,"native icon padding after actual GUI transform");
            }
        }
        icons++;
    }
    private static void shot(Minecraft mc,File output,String name) throws java.io.IOException {
        ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_"+name+".png"));
    }
    private static final class Preview extends GuiScreen {
        @Override public boolean doesGuiPauseGame() {return false;}
        @Override public void drawScreen(int x,int y,float partial) {
            drawRect(0,0,width,height,0xFF253441);
            String name=choices(ITEMS[0]).get(Math.min(preview,6)).label.replace(" Helmet","");
            drawCenteredString(fontRenderer,name,width/2,16,0xFFFFFF);
            GuiInventory.drawEntityOnScreen(width/2,height-55,65,0,0,mc.player);
            RenderHelper.enableGUIStandardItemLighting();
            for(int i=0;i<ITEMS.length;i++) {
                int xx=width/2-44+i*24,yy=height-35;
                drawRect(xx-1,yy-1,xx+17,yy+17,0xFF58697A);
                itemRender.renderItemAndEffectIntoGUI(mc.player.getItemStackFromSlot(ITEMS[i].armorType),xx,yy);
            }
            RenderHelper.disableStandardItemLighting();
        }
    }
    private static void require(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);}
}
