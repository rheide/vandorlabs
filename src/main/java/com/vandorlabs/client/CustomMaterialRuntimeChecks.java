package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.blocks.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.init.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Real model/atlas and inventory interaction checks for the common Custom picker. */
final class CustomMaterialRuntimeChecks {
    static void run(net.minecraft.entity.player.EntityPlayer player) {
        Minecraft mc=Minecraft.getMinecraft();
        ItemStack stone=new ItemStack(Blocks.STONE),door=new ItemStack(Items.OAK_DOOR);
        int stoneId=CustomBlockMaterials.choice(stone),doorId=CustomBlockMaterials.choice(door);
        require(CustomBlockMaterials.block(door)==Blocks.OAK_DOOR,"vanilla door item mapping");
        require(CustomBlockTextures.isDoor(doorId),"door classification");
        require(!CustomBlockTextures.sprite(doorId,false).getIconName().equals(CustomBlockTextures.sprite(doorId,true).getIconName()),"upper/lower door artwork");
        require(CustomBlockTextures.texture(stoneId).equals("minecraft:blocks/stone"),"vanilla block artwork");
        ItemStack configured=new ItemStack(ModBlocks.PROGRAMMABLE_BLOCK);NBTTagCompound tag=new NBTTagCompound();tag.setInteger("housingTexture",stoneId);configured.setTagInfo("BlockEntityTag",tag);
        net.minecraft.client.renderer.block.model.IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(configured,player.world,player);
        require(model.getParticleTexture().getIconName().equals("minecraft:blocks/stone"),"custom configured inventory artwork");
        BlockPos pos=new BlockPos(player.posX,248,player.posZ);
        player.world.setBlockState(pos,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState());
        try {
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)player.world.getTileEntity(pos);tile.setHousingTexture(stoneId);
            net.minecraft.block.state.IBlockState state=ModBlocks.PROGRAMMABLE_BLOCK.getExtendedState(player.world.getBlockState(pos),player.world,pos);
            model=mc.getBlockRendererDispatcher().getModelForState(player.world.getBlockState(pos));
            java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads=model.getQuads(state,null,0);require(quads.size()==6,"custom housing geometry");
            for(net.minecraft.client.renderer.block.model.BakedQuad quad:quads)require(quad.getSprite().getIconName().equals("minecraft:blocks/stone"),"custom world housing artwork");
        }finally{player.world.setBlockToAir(pos);}
        ItemStack original=player.inventory.getStackInSlot(0);net.minecraft.inventory.Container container=player.openContainer;
        net.minecraft.client.gui.GuiScreen previous=mc.currentScreen;
        try {
            player.inventory.setInventorySlotContents(0,door.copy());final int[] picked={-1};
            GuiCustomTexture gui=new GuiCustomTexture(new net.minecraft.client.gui.GuiScreen(){},id->picked[0]=id);
            mc.displayGuiScreen(gui);int left=(gui.width-252)/2,top=(gui.height-198)/2;
            gui.mouseClicked(left+47,top+143,0);gui.mouseReleased(left+126,top+45,0);
            require(picked[0]==doorId,"drag into custom sample slot");
            require(player.inventory.getStackInSlot(0).getItem()==door.getItem() && player.inventory.getStackInSlot(0).getCount()==1,"sample consumed inventory item");
            require(player.openContainer==container,"custom picker replaced authorized container");
        }catch(java.io.IOException e){throw new RuntimeException(e);}finally{player.inventory.setInventorySlotContents(0,original);mc.displayGuiScreen(previous);}
        System.out.println("[vandorlabs][reprolab] custom-materials-runtime PASS");
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException("Custom material: "+message);}
}
