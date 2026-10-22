package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
final class VanillaTextureCopyChecks {
    static void run(WorldServer world){
        BlockPos source=new BlockPos(302,40,146),target=source.east();ItemStack tool=new ItemStack(ModItems.DUPLIFIER);int cases=0;
        try{for(Block block:new Block[]{Blocks.STONE,Blocks.PLANKS,Blocks.WOOL,Blocks.LOG,Blocks.BRICK_BLOCK})for(int meta=0;meta<16;meta++){
            world.setBlockState(source,block.getStateFromMeta(meta),2);
            if(ItemDuplifier.copyFrom(world,source,tool)==null)throw new IllegalStateException("vanilla sample missing");
            int expected=CustomBlockMaterials.identifier(block,block.getMetaFromState(world.getBlockState(source)));
            NBTTagCompound values=tool.getSubCompound(ItemDuplifier.SETTINGS_TAG);
            for(Block destination:new Block[]{ModBlocks.PROGRAMMABLE_BLOCK,ModBlocks.PROGRAMMABLE_STAIRS,Block.getBlockFromName("vandorlabs:programmable_glass")}){
                world.setBlockState(target,destination.getDefaultState(),2);
                if(!ItemDuplifier.applyTo(world,target,tool,null))throw new IllegalStateException("vanilla texture not applicable");
                net.minecraft.tileentity.TileEntity tile=world.getTileEntity(target);int actual=tile instanceof TileEntityProgrammableGlass?((TileEntityProgrammableGlass)tile).getMaterial():((TileEntityAnimatedScreenSelector)tile).getHousingTexture();
                if(expected!=actual)throw new IllegalStateException("vanilla texture metadata lost");cases++;
            }
            if(values.getKeySet().stream().anyMatch(key->key.contains("inventory")||key.contains("channel")))throw new IllegalStateException("texture sample includes unrelated state");
        }}finally{world.setBlockToAir(source);world.setBlockToAir(target);}
        System.out.println("[vandorlabs][reprolab] vanilla-texture-copy PASS cases="+cases+" stable block metadata stone planks wool logs brick programmable blocks stairs glass");
    }
}
