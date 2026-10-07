package com.vandorlabs.client;

import com.vandorlabs.container.ContainerProgrammableArmor;
import com.vandorlabs.items.*;
import com.vandorlabs.network.MessageProgrammableArmor;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.world.GameType;
import java.io.File;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;

/** Real client/server picker packets, equip actions, saved stacks and material rendering. */
final class ProgrammableArmorRuntimeChecks {
    private static final ItemProgrammableArmor[] ITEMS={ModItems.PROGRAMMABLE_HELMET,ModItems.PROGRAMMABLE_CHESTPLATE,
            ModItems.PROGRAMMABLE_LEGGINGS,ModItems.PROGRAMMABLE_BOOTS};
    private static final ItemArmor[] VANILLA={(ItemArmor)Items.DIAMOND_HELMET,(ItemArmor)Items.DIAMOND_CHESTPLATE,
            (ItemArmor)Items.DIAMOND_LEGGINGS,(ItemArmor)Items.DIAMOND_BOOTS};
    private static int stage,piece,ticks;
    private static Future<?> pending;
    private static EntityPlayerMP owner(Minecraft mc) { return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID()); }
    static void tick(Minecraft mc,File output) {
        try {
            if(pending!=null) { if(!pending.isDone())return;pending.get();pending=null; }
            if(stage==12) {RoleArmorRuntimeChecks.tick(mc,output);return;}
            if(stage==0) {
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP player=owner(mc);
                    player.setGameType(GameType.CREATIVE);
                    player.inventory.currentItem=0;
                    ItemStack stack=new ItemStack(ITEMS[piece]);
                    stack.setItemDamage(17);stack.setStackDisplayName("Configured armor");
                    stack.addEnchantment(Enchantments.PROTECTION,2);
                    player.setHeldItem(EnumHand.MAIN_HAND,stack);
                    player.setItemStackToSlot(ITEMS[piece].armorType,ItemStack.EMPTY);
                    player.inventoryContainer.detectAndSendChanges();
                    checkServer(player,stack,piece);
                });stage=1;ticks=0;return;
            }
            if(++ticks<20)return;
            if(stage==1) {
                require(mc.player.getHeldItemMainhand().getItem()==ITEMS[piece],"held piece sync");
                mc.player.setSneaking(true);
                stage=8;ticks=0;return;
            }
            if(stage==8) {
                mc.player.setSneaking(true);
                mc.player.movementInput.sneak=true;
                mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketEntityAction(mc.player,net.minecraft.network.play.client.CPacketEntityAction.Action.START_SNEAKING));
                require(mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"shift right click");
                stage=2;ticks=0;return;
            }
            if(stage==2) {
                if(!(mc.currentScreen instanceof GuiProgrammableArmor)) {
                    final String[] detail={""};
                    mc.getIntegratedServer().addScheduledTask(()->{
                        EntityPlayerMP player=owner(mc);
                        detail[0]=player.openContainer.getClass().getSimpleName()+" sneak="+player.isSneaking()+" held="+player.getHeldItemMainhand()+" equipped="+player.getItemStackFromSlot(ITEMS[piece].armorType);
                    }).get();
                    require(false,"real server-opened picker: client="+mc.currentScreen+" server="+detail[0]);
                }
                GuiProgrammableArmor gui=(GuiProgrammableArmor)mc.currentScreen;
                gui.select(1+piece);
                stage=3;ticks=0;return;
            }
            if(stage==3) {
                require(ItemProgrammableArmor.texture(mc.player.getHeldItemMainhand())==1+piece,"texture packet synchronized");
                GuiProgrammableArmor resized=(GuiProgrammableArmor)mc.currentScreen;
                resized.setWorldAndResolution(mc,resized.width,resized.height);
                java.lang.reflect.Field picker=GuiProgrammableArmor.class.getDeclaredField("textures");picker.setAccessible(true);
                require(((HousingTextureList)picker.get(resized)).selected()==1+piece,"resize retains synchronized selection");
                if(piece==0)shot(mc,output,"armor_picker");
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP player=owner(mc);
                    ItemStack stack=player.getHeldItemMainhand();
                    require(ItemProgrammableArmor.texture(stack)==1+piece,"server material");
                    require(stack.getItemDamage()==17 && stack.isItemEnchanted() && stack.hasDisplayName(),"non-material NBT preserved");
                    require(ItemStack.areItemStacksEqual(stack,new ItemStack(stack.writeToNBT(new NBTTagCompound()))),"save/load complete stack");
                });
                mc.player.closeScreen();mc.player.setSneaking(false);mc.player.movementInput.sneak=false;
                mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketEntityAction(mc.player,net.minecraft.network.play.client.CPacketEntityAction.Action.STOP_SNEAKING));
                stage=4;ticks=0;return;
            }
            if(stage==4) {
                require(mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"ordinary right click equips");
                stage=5;ticks=0;return;
            }
            if(stage==5) {
                ItemStack equipped=mc.player.getItemStackFromSlot(ITEMS[piece].armorType);
                require(equipped.getItem()==ITEMS[piece] && ItemProgrammableArmor.texture(equipped)==1+piece,"equipped appearance sync");
                if(++piece<ITEMS.length) {stage=0;ticks=0;return;}
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP player=owner(mc);player.setGameType(GameType.SURVIVAL);
                    player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));
                    player.setHeldItem(EnumHand.OFF_HAND,new ItemStack(ModItems.PROGRAMMABLE_CHESTPLATE));
                    player.connection.sendPacket(new net.minecraft.network.play.server.SPacketSetSlot(-2,40,player.getHeldItemOffhand()));
                    player.inventoryContainer.detectAndSendChanges();
                });
                stage=9;ticks=0;return;
            }
            if(stage==9) {
                require(mc.player.getHeldItemOffhand().getItem()==ModItems.PROGRAMMABLE_CHESTPLATE,"offhand initial sync");
                mc.player.movementInput.sneak=true;
                mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketEntityAction(mc.player,net.minecraft.network.play.client.CPacketEntityAction.Action.START_SNEAKING));
                require(mc.playerController.processRightClick(mc.player,mc.world,EnumHand.OFF_HAND)==EnumActionResult.SUCCESS,"survival offhand shift click");
                stage=10;ticks=0;return;
            }
            if(stage==10) {
                require(mc.currentScreen instanceof GuiProgrammableArmor,"survival tool opens offhand picker");
                ((GuiProgrammableArmor)mc.currentScreen).select(5);
                stage=11;ticks=0;return;
            }
            if(stage==11) {
                require(mc.currentScreen instanceof GuiProgrammableArmor && ItemProgrammableArmor.texture(mc.player.getHeldItemOffhand())==5,"offhand material sync while dialog remains open");
                mc.player.closeScreen();mc.player.movementInput.sneak=false;
                mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketEntityAction(mc.player,net.minecraft.network.play.client.CPacketEntityAction.Action.STOP_SNEAKING));
                checkWornMasks(mc);
                checkIcons(mc);
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP player=owner(mc);
                    player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
                    player.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);
                    for(ItemProgrammableArmor item:ITEMS) {
                        ItemStack stack=player.getItemStackFromSlot(item.armorType);
                        stack.getTagCompound().removeTag("ench");
                    }
                    player.sendContainerToPlayer(player.inventoryContainer);
                });
                mc.displayGuiScreen(new Preview());
                stage=6;ticks=0;return;
            }
            if(stage==6) {
                shot(mc,output,"armor_worn_and_icons");
                System.out.println("[vandorlabs][reprolab] programmable-armor PASS (four real picker/equip packets, survival offhand sync, diamond stats, recipes, permissions, stale stacks, saved NBT and every material icon)");
                stage=12;
            }
        } catch(Exception e) {throw new IllegalStateException("Armor live checks piece="+piece+" stage="+stage,e);}
    }
    private static void checkServer(EntityPlayerMP player,ItemStack stack,int index) {
        ItemProgrammableArmor item=ITEMS[index];ItemArmor diamond=VANILLA[index];
        require(item.damageReduceAmount==diamond.damageReduceAmount && item.toughness==diamond.toughness
                && item.getMaxDamage()==diamond.getMaxDamage() && item.getItemEnchantability()==diamond.getItemEnchantability(),"diamond statistics");
        require(item.getIsRepairable(stack,new ItemStack(Items.DIAMOND)),"diamond repair");
        require(net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.containsKey(item.getRegistryName()),"crafting recipe registered");
        ContainerProgrammableArmor container=new ContainerProgrammableArmor(player,EnumHand.MAIN_HAND);
        player.openContainer=container;container.windowId=91;
        require(!MessageProgrammableArmor.apply(player,90,1),"wrong window rejected");
        require(!MessageProgrammableArmor.apply(player,91,-1),"invalid material rejected");
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++) {
            int choice=ScreenHousingTextures.choiceAt(i);
            if(ItemProgrammableArmor.validTexture(choice))require(HousingTextureList.generalTexture(i),"server picker parity");
        }
        require(MessageProgrammableArmor.apply(player,91,1),"authorized material");
        ItemStack replacement=stack.copy();player.setHeldItem(EnumHand.MAIN_HAND,replacement);
        require(!MessageProgrammableArmor.apply(player,91,2),"replaced stack rejected");
        player.setHeldItem(EnumHand.MAIN_HAND,stack);
        player.setGameType(GameType.SURVIVAL);
        require(!MessageProgrammableArmor.apply(player,91,2),"survival without tool rejected");
        player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));
        player.setHeldItem(EnumHand.OFF_HAND,stack);
        ContainerProgrammableArmor offhand=new ContainerProgrammableArmor(player,EnumHand.OFF_HAND);
        player.openContainer=offhand;offhand.windowId=92;
        require(MessageProgrammableArmor.apply(player,92,2),"survival Configurizer offhand armor");
        player.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);
        player.setHeldItem(EnumHand.MAIN_HAND,stack);
        player.setGameType(GameType.CREATIVE);
        player.openContainer=player.inventoryContainer;
        ItemProgrammableArmor.setTexture(stack,0);
    }
    private static void checkWornMasks(Minecraft mc) throws java.io.IOException {
        int[] source=new int[32*32];
        for(int y=0;y<32;y++)for(int x=0;x<32;x++)source[y*32+x]=0xFF000000|(x<<16)|(y<<8);
        java.awt.image.BufferedImage mask=new java.awt.image.BufferedImage(64,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<32;y++)for(int x=0;x<64;x++)mask.setRGB(x,y,0xFFFFFFFF);
        // A shorter boot face must center independently from a full-height leg.
        for(int y=20;y<26;y++)for(int x=4;x<8;x++)mask.setRGB(x,y,0);
        java.awt.image.BufferedImage centered=ProgrammableArmorTextures.bake(source,32,32,mask);
        for(int[] point:new int[][]{{12*4,12*4},{24*4,26*4},{6*4,29*4}}) {
            int color=centered.getRGB(point[0],point[1]);
            require(Math.abs(((color>>>16)&255)-16)<=1 && Math.abs(((color>>>8)&255)-16)<=1,
                    "material center aligns with helmet, torso and shortened boot faces");
        }
        require(centered.getRGB(6*4,22*4)>>>24==0,"centering preserves mask holes");
        System.out.println("[vandorlabs][reprolab] armor-material-centering PASS helmet torso boots");
        for(ItemProgrammableArmor item:ITEMS) {
            ItemStack stack=mc.player.getItemStackFromSlot(item.armorType);
            String path=item.getArmorTexture(stack,mc.player,item.armorType,null);
            net.minecraft.client.renderer.texture.ITextureObject texture=mc.getTextureManager().getTexture(new ResourceLocation(path));
            require(texture instanceof net.minecraft.client.renderer.texture.DynamicTexture,"derived worn material loaded");
            int[] pixels=((net.minecraft.client.renderer.texture.DynamicTexture)texture).getTextureData();
            int layer=item.armorType==EntityEquipmentSlot.LEGS?2:1;
            try(java.io.InputStream stream=mc.getResourceManager().getResource(new ResourceLocation("minecraft","textures/models/armor/diamond_layer_"+layer+".png")).getInputStream()) {
                java.awt.image.BufferedImage vanilla=javax.imageio.ImageIO.read(stream);
                int holes=0,solid=0;
                for(int y=0;y<128;y++)for(int x=0;x<256;x++) {
                    int alpha=vanilla.getRGB(x*vanilla.getWidth()/256,y*vanilla.getHeight()/128)>>>24;
                    require((pixels[y*256+x]>>>24)==alpha,"worn armor retains vanilla cutouts including helmet face");
                    if(alpha==0)holes++;else solid++;
                }
                require(holes>0 && solid>0,"vanilla armor mask contains cutouts and protective surfaces");
            }
        }
        System.out.println("[vandorlabs][reprolab] armor-vanilla-masks PASS layers=2 pieces=4");
    }
    private static void checkIcons(Minecraft mc) {
        int checked=0;
        for(ItemProgrammableArmor item:ITEMS)for(int i=0;i<ScreenHousingTextures.IDS.length;i++) {
            int choice=ScreenHousingTextures.choiceAt(i);if(!ItemProgrammableArmor.validTexture(choice))continue;
            ItemStack stack=new ItemStack(item);ItemProgrammableArmor.setTexture(stack,choice);
            IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);
            ItemTransformVec3f gui=model.getItemCameraTransforms().gui;
            require(gui.rotation.x==0 && gui.rotation.y==0 && gui.rotation.z==0,"icon GUI rotation");
            for(BakedQuad quad:model.getQuads(null,null,0)) {
                require(quad.getSprite().getIconName().equals(ScreenHousingTextures.texture(choice)),"icon uses selected sprite");
                int[] data=quad.getVertexData();int stride=data.length/4;
                for(int v=0;v<4;v++)for(int axis=0;axis<2;axis++) {
                    float scale=axis==0?gui.scale.x:gui.scale.y;
                    float position=(Float.intBitsToFloat(data[v*stride+axis])-.5F)*scale+.5F;
                    require(position>=.1F && position<=.9F,"every icon variant padded after GUI transform");
                }
            }
            checked++;
        }
        System.out.println("[vandorlabs][reprolab] armor-icon-variants PASS count="+checked);
    }
    private static void shot(Minecraft mc,File output,String name) throws java.io.IOException {
        ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_"+name+".png"));
    }
    private static final class Preview extends GuiScreen {
        @Override public boolean doesGuiPauseGame() {return false;}
        @Override public void drawScreen(int x,int y,float partial) {
            drawRect(0,0,width,height,0xFF253441);
            drawCenteredString(fontRenderer,"Programmable armor",width/2,16,0xFFFFFF);
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
    private static void require(boolean ok,String message) {if(!ok)throw new IllegalStateException(message);}
}
