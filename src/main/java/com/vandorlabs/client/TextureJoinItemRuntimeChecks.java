package com.vandorlabs.client;

import com.vandorlabs.surface.*;
import com.vandorlabs.items.ProgrammableSettings;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Configured picks and copied-item crafting must retain join policy through real placement hooks. */
final class TextureJoinItemRuntimeChecks {
    static void run(EntityPlayerMP player,BlockPos pos){
        World world=player.world;int cases=0;
        try{
            for(String id:new String[]{"programmable_block","programmable_wall","programmable_porthole_wall","programmable_diagonal_wall","programmable_diagonal_porthole","programmable_diagonal_glass","programmable_glass"}){
                Block block=Block.getBlockFromName("vandorlabs:"+id);
                TileEntity original=block.createTileEntity(world,block.getDefaultState());net.minecraft.nbt.NBTTagCompound explicit=original.writeToNBT(new net.minecraft.nbt.NBTTagCompound());
                TextureJoinSettings.DEFAULT.write(new com.vandorlabs.persistence.NbtPrimitiveData(explicit),true);original.readFromNBT(explicit);
                net.minecraft.nbt.NBTTagCompound repeated=original.writeToNBT(new net.minecraft.nbt.NBTTagCompound());need(repeated.hasKey(TextureJoinSettings.DIRECTION_TAG,3)&&repeated.hasKey(TextureJoinSettings.PARITY_TAG,3),id+" explicit default field presence");
                for(int direction=2;direction<6;direction++)for(int parity=0;parity<3;parity++){
                    world.setBlockToAir(pos);world.setBlockState(pos,block.getDefaultState(),3);
                    TileEntity tile=world.getTileEntity(pos);TextureJoinSettings expected=new TextureJoinSettings(direction,parity);((TextureJoinOwner)tile).setTextureJoin(expected);
                    ItemStack picked=block.getPickBlock(world.getBlockState(pos),null,world,pos,player);
                    net.minecraft.nbt.NBTTagCompound captured=ProgrammableSettings.capture(world,pos);
                    place(world,pos,block,picked,player,expected,id+" configured pick");
                    ItemStack crafted=ProgrammableSettings.applyToItem(new ItemStack(block),captured);
                    need(!crafted.isEmpty(),id+" copied-settings crafting");
                    place(world,pos,block,crafted,player,expected,id+" copied item");cases++;
                }
            }
        }finally{world.setBlockToAir(pos);}
        System.out.println("[vandorlabs][reprolab] texture-join-items PASS cases="+cases+" seven families configured picks/copied crafting/actual placement direction and neighbour filter");
    }
    private static void place(World world,BlockPos pos,Block block,ItemStack stack,EntityPlayerMP player,TextureJoinSettings expected,String stage){
        world.setBlockToAir(pos);need(stack.getItem() instanceof ItemBlock,stage+" block item");
        need(((ItemBlock)stack.getItem()).placeBlockAt(stack,player,world,pos,EnumFacing.UP,.5f,.5f,.5f,block.getDefaultState()),stage+" placement accepted");
        TileEntity installed=world.getTileEntity(pos);need(installed instanceof TextureJoinOwner&&((TextureJoinOwner)installed).textureJoin().equals(expected),stage+" policy retained expected="+expected.direction+"/"+expected.parity);
    }
    private static void need(boolean value,String stage){if(!value)throw new IllegalStateException("Texture join item: "+stage);}
    private TextureJoinItemRuntimeChecks(){}
}
