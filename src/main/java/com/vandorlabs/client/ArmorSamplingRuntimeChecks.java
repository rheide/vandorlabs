package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.network.MessageSampleArmorTexture;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;
import java.util.concurrent.Future;

/** Real block-use events and sample packets, including rotated programmable and storage faces. */
final class ArmorSamplingRuntimeChecks {
    private static final ItemProgrammableArmor[] ITEMS={ModItems.PROGRAMMABLE_HELMET,ModItems.PROGRAMMABLE_CHESTPLATE,
            ModItems.PROGRAMMABLE_LEGGINGS,ModItems.PROGRAMMABLE_BOOTS};
    private static int stage,example,ticks;
    private static BlockPos pos;
    private static Future<?> pending;
    private static EntityPlayerMP owner(Minecraft mc) {return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static EnumFacing face() {return example==0 || example==4 || example==5?EnumFacing.UP:example==2?EnumFacing.NORTH:EnumFacing.EAST;}
    private static EnumHand hand() {return example==6?EnumHand.OFF_HAND:EnumHand.MAIN_HAND;}
    private static Vec3d hit() {EnumFacing face=face();return new Vec3d(pos).addVector(.5+face.getFrontOffsetX()*.5,.5+face.getFrontOffsetY()*.5,.5+face.getFrontOffsetZ()*.5);}
    private static String expected() {
        if(example==0)return "minecraft:blocks/log_oak_top";
        if(example==1)return "minecraft:blocks/log_oak";
        if(example==5)return ScreenHousingTextures.storageTexture(ScreenHousingTextures.DEFAULT_STORAGE,EnumFacing.UP);
        return ScreenHousingTextures.texture(example==3 || example==6?2:1);
    }
    static void tick(Minecraft mc) {
        try {
            if(pending!=null) {if(!pending.isDone())return;pending.get();pending=null;}
            if(stage==0) {
                mc.displayGuiScreen(null);
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP player=owner(mc);
                    player.setGameType(example==2?GameType.CREATIVE:GameType.SURVIVAL);
                    player.setNoGravity(true);player.inventory.currentItem=0;
                    if(pos==null)pos=player.getPosition().up(6);
                    for(EnumFacing direction:EnumFacing.values())player.world.setBlockToAir(pos.offset(direction));
                    player.world.setBlockState(pos,(example<2?Blocks.LOG:example==5?ModBlocks.PROGRAMMABLE_STORAGE:ModBlocks.PROGRAMMABLE_BLOCK).getDefaultState(),3);
                    if(example>=2) {
                        player.world.setBlockState(pos,player.world.getBlockState(pos).withProperty(BlockAnimatedScreenSelector.FACING,
                                example==3 || example==6?EnumFacing.EAST:EnumFacing.NORTH),3);
                        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)player.world.getTileEntity(pos);
                        tile.setHousingTexture(example==5?ScreenHousingTextures.DEFAULT_STORAGE:1);
                        int[] values={-1,-1,-1,-1,-1,-1};values[EnumFacing.NORTH.getIndex()]=2;
                        tile.setFaceTextures(new FaceTextures(example==3 || example==4 || example==6,values));
                    }
                    ItemStack armor=new ItemStack(ITEMS[example%4]);armor.setItemDamage(19);armor.setStackDisplayName("Sample check");
                    armor.addEnchantment(net.minecraft.init.Enchantments.PROTECTION,2);
                    player.setHeldItem(EnumHand.MAIN_HAND,example==6?new ItemStack(ModItems.CONFIGURIZER):armor);
                    player.setHeldItem(EnumHand.OFF_HAND,example==6?armor:ItemStack.EMPTY);
                    player.setItemStackToSlot(ITEMS[example%4].armorType,ItemStack.EMPTY);
                    Vec3d eye=hit().addVector(face().getFrontOffsetX()*2,face().getFrontOffsetY()*2,face().getFrontOffsetZ()*2);
                    player.connection.setPlayerLocation(eye.x,eye.y-player.getEyeHeight(),eye.z,yaw(),pitch());
                    player.sendContainerToPlayer(player.inventoryContainer);
                });stage=1;ticks=0;return;
            }
            if(++ticks<25)return;
            if(stage==1) {
                mc.player.rotationYaw=yaw();mc.player.rotationPitch=pitch();
                mc.player.movementInput.sneak=true;
                mc.player.connection.sendPacket(new CPacketEntityAction(mc.player,CPacketEntityAction.Action.START_SNEAKING));
                require(expected().equals(ArmorTextureSampler.resolve(mc.world,pos,face(),hit())),"correct visible face resolved");
                require(mc.playerController.processRightClickBlock(mc.player,mc.world,pos,face(),hit(),
                        example==6?EnumHand.MAIN_HAND:hand())==EnumActionResult.SUCCESS,"real Shift block-use gesture consumed");
                stage=2;ticks=0;return;
            }
            if(stage==2) {
                ItemStack armor=mc.player.getHeldItem(hand());
                require(expected().equals(ItemProgrammableArmor.sample(armor)),"sample packet and held stack synchronized");
                require(armor.getItemDamage()==19 && armor.isItemEnchanted() && "Sample check".equals(armor.getDisplayName()),"sample preserves damage, enchantment and name");
                require(mc.currentScreen==null,"sample does not open block or armor dialog");
                require(mc.player.getItemStackFromSlot(ITEMS[example%4].armorType).isEmpty(),"sample does not equip armor");
                require(expected().equals(ItemProgrammableArmor.sample(new ItemStack(armor.writeToNBT(new NBTTagCompound())))),"sample persists in saved stack");
                net.minecraft.client.renderer.block.model.IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(armor,mc.world,mc.player);
                for(net.minecraft.client.renderer.block.model.BakedQuad quad:model.getQuads(null,null,0))
                    require(expected().equals(quad.getSprite().getIconName()),"sampled artwork on inventory icon");
                String worn=ITEMS[example%4].getArmorTexture(armor,mc.player,ITEMS[example%4].armorType,null);
                require(mc.getTextureManager().getTexture(new ResourceLocation(worn)) instanceof net.minecraft.client.renderer.texture.DynamicTexture,"sampled worn skin generated");
                final EnumHand sampledHand=hand();
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP player=owner(mc);ItemStack stack=player.getHeldItem(sampledHand);
                    require(!MessageSampleArmorTexture.apply(player,pos.add(1000,0,0),sampledHand,stack,"minecraft:blocks/stone"),"out-of-reach target rejected");
                    require(!MessageSampleArmorTexture.apply(player,pos,sampledHand,new ItemStack(ModItems.PROGRAMMABLE_BOOTS),"minecraft:blocks/stone"),"stale stack rejected");
                    require(!MessageSampleArmorTexture.apply(player,pos,sampledHand,stack,"minecraft:../invalid"),"invalid sprite rejected");
                    player.setSneaking(false);
                    require(!MessageSampleArmorTexture.apply(player,pos,sampledHand,stack,"minecraft:blocks/stone"),"non-sneaking packet rejected");
                    player.setSneaking(true);
                    ItemProgrammableArmor.setTexture(stack,ArmorTextures.defaultChoice(((ItemProgrammableArmor)stack.getItem()).armorType));
                    require(ItemProgrammableArmor.sample(stack)==null,"picker selection clears sample");
                    player.sendContainerToPlayer(player.inventoryContainer);
                });
                if(++example<7) {stage=0;ticks=0;return;}
                stage=3;return;
            }
            if(stage==3) {
                System.out.println("[vandorlabs][reprolab] armor-world-sampling PASS cases=7 (vanilla top/side, programmable main/rotated overrides/inheritance, storage top, survival Configurizer offhand, NBT, icons, worn skins and packet guards)");
                stage=4;mc.shutdown();
            }
        } catch(Exception e) {throw new IllegalStateException("Armor sample checks example="+example+" stage="+stage,e);}
    }
    private static float yaw() {return face()==EnumFacing.EAST?90:0;}
    private static float pitch() {return face()==EnumFacing.UP?90:0;}
    private static void require(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);}
}
