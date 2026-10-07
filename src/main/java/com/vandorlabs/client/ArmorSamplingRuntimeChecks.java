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
    private static EnumFacing face() {return example==0 || example==4 || example==5 || example==14 || example==19?EnumFacing.UP:example==2 || example>=7 && example!=12 && example!=13 && example!=14?EnumFacing.NORTH:EnumFacing.EAST;}
    private static EnumHand hand() {return example==6?EnumHand.OFF_HAND:EnumHand.MAIN_HAND;}
    private static Vec3d hit() {EnumFacing face=face();return new Vec3d(pos).addVector(.5+face.getFrontOffsetX()*.5,.5+face.getFrontOffsetY()*.5,.5+face.getFrontOffsetZ()*.5);}
    private static String expected() {
        if(example==19 || example==23)return ScreenHousingTextures.texture(2);
        if(example==20 || example==21)return ScreenHousingTextures.texture(ScreenHousingTextures.lightIndex(2),example==20);
        if(example==22)return "vandorlabs:blocks/sequence_border_off";
        if(example==24)return ScreenHousingTextures.fullTexture(ScreenHousingTextures.screenIndex("engineering_screen_static"));
        if(example==17 || example==18)return ScreenHousingTextures.fullTexture(ScreenHousingTextures.doorIndex(example==17?7:23,1));
        if(example>=7 && example<=9)return ScreenHousingTextures.fullTexture(example==9?2:ScreenHousingTextures.doorIndex(example==8?3:7,1));
        if(example>=10 && example<=14)return example==13?ScreenHousingTextures.texture(2):ScreenHousingTextures.texture(ScreenHousingTextures.lightIndex(2),example!=11);
        if(example==15 || example==16)return ScreenHousingTextures.texture(2);
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
                    if(example>=2 && example<7) {
                        player.world.setBlockState(pos,player.world.getBlockState(pos).withProperty(BlockAnimatedScreenSelector.FACING,
                                example==3 || example==6?EnumFacing.EAST:EnumFacing.NORTH),3);
                        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)player.world.getTileEntity(pos);
                        tile.setHousingTexture(example==5?ScreenHousingTextures.DEFAULT_STORAGE:1);
                        int[] values={-1,-1,-1,-1,-1,-1};values[EnumFacing.NORTH.getIndex()]=2;
                        tile.setFaceTextures(new FaceTextures(example==3 || example==4 || example==6,values));
                    }
                    if(example>=7 && example<=9 || example==17) {
                        net.minecraft.block.Block door=net.minecraft.block.Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_door"));
                        BlockPos lower=example==17?pos.down():pos;
                        player.world.setBlockState(lower.down(),Blocks.STONE.getDefaultState(),3);
                        net.minecraft.block.state.IBlockState state=door.getDefaultState().withProperty(BlockVandorDoor.FACING,EnumFacing.NORTH);
                        player.world.setBlockState(lower,state.withProperty(BlockVandorDoor.HALF,net.minecraft.block.BlockDoor.EnumDoorHalf.LOWER),2);
                        player.world.setBlockState(lower.up(),state.withProperty(BlockVandorDoor.HALF,net.minecraft.block.BlockDoor.EnumDoorHalf.UPPER),2);
                        TileEntitySpaceDoor tile=(TileEntitySpaceDoor)player.world.getTileEntity(lower);
                        tile.configure(example==8?3:7,1,false);tile.setPlacementDepth(0);
                        if(example==9)tile.setFaceTexture(2);
                        player.world.notifyBlockUpdate(lower,state,state,3);
                    }
                    if(example==18) {
                        net.minecraft.block.Block door=net.minecraft.block.Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:large_programmable_door"));
                        BlockPos anchor=pos.east();
                        net.minecraft.block.state.IBlockState state=door.getDefaultState().withProperty(BlockVandorDoor.FACING,EnumFacing.NORTH);
                        for(int x=0;x<3;x++)for(int y=0;y<3;y++) {
                            BlockPos cell=anchor.west(x).up(y);
                            player.world.setBlockState(cell.down(y+1),Blocks.STONE.getDefaultState(),2);
                            player.world.setBlockState(cell,state,2);
                            ((TileEntityLargeProgrammableDoor)player.world.getTileEntity(cell)).assign(anchor);
                        }
                        TileEntitySpaceDoor tile=(TileEntitySpaceDoor)player.world.getTileEntity(anchor);tile.configure(23,1,false);tile.setPlacementDepth(0);
                    }
                    if(example>=10 && example<=14) {
                        player.world.setBlockState(pos,ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,
                                example==12?EnumFacing.EAST:example==14?EnumFacing.UP:EnumFacing.NORTH),3);
                        TileEntityProgrammableLight light=(TileEntityProgrammableLight)player.world.getTileEntity(pos);
                        light.configure(2,15);light.setOn(example!=11);light.setHousingTexture(2);
                    }
                    if(example==15) {
                        player.world.setBlockState(pos,ModBlocks.PROGRAMMABLE_WALL.getDefaultState(),3);
                        ((TileEntityAnimatedScreenSelector)player.world.getTileEntity(pos)).setHousingTexture(2);
                    }
                    if(example==16) {
                        player.world.setBlockState(pos,ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK.getDefaultState(),3);
                        TileEntityProgrammableTrigger trigger=(TileEntityProgrammableTrigger)player.world.getTileEntity(pos);
                        trigger.configure(1,2,0);trigger.configureLevels(false,0,1,1);
                    }
                    if(example==19) {
                        player.world.setBlockState(pos,ModBlocks.PROGRAMMABLE_TRAPDOOR.getDefaultState(),3);
                        ((TileEntityProgrammableTrapdoor)player.world.getTileEntity(pos)).configure(2,0,false,com.vandorlabs.persistence.SpaceDoorData.TRIGGER_DISABLED,0);
                    }
                    if(example==20 || example==21) {
                        player.world.setBlockState(pos,ModBlocks.PROGRAMMABLE_LIGHT_FRAME.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.NORTH),3);
                        TileEntityProgrammableLight light=(TileEntityProgrammableLight)player.world.getTileEntity(pos);light.configure(2,15);light.setOn(example==20);
                    }
                    if(example>=22 && example<=24) {
                        player.world.setBlockState(pos,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.NORTH),3);
                        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)player.world.getTileEntity(pos);
                        tile.setHousingTexture(2);tile.setSelectedScreen("engineering_screen");
                        tile.setDisplayMode(example==22?TileEntityAnimatedScreenSelector.MODE_OFF:TileEntityAnimatedScreenSelector.MODE_STATIC);
                        if(example==23)tile.setSurfaceTexture(0,2);
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
                require(expected().equals(ArmorTextureSampler.resolve(mc.world,pos,face(),hit())),"correct visible face resolved: expected="+expected()+" actual="+ArmorTextureSampler.resolve(mc.world,pos,face(),hit()));
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
                if(++example<25) {stage=0;ticks=0;return;}
                stage=3;return;
            }
            if(stage==3) {
                System.out.println("[vandorlabs][reprolab] armor-world-sampling PASS cases=25 (vanilla top/side, programmable main/rotated overrides/inheritance, storage top, survival Configurizer offhand, NBT, icons, worn skins and packet guards)");
                stage=4;
            }
            if(stage==4)ArmorStandRuntimeChecks.tick(mc);
        } catch(Exception e) {throw new IllegalStateException("Armor sample checks example="+example+" stage="+stage,e);}
    }
    private static float yaw() {return face()==EnumFacing.EAST?90:0;}
    private static float pitch() {return face()==EnumFacing.UP?90:0;}
    private static void require(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);}
}
