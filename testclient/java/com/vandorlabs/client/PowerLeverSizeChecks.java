package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityRedstoneChannel;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Actual pre-extraction typed save/live/item outputs, with registry-sensitive legacy defaults. */
public final class PowerLeverSizeChecks {
    private static final String GOLDEN="9c69f07698183cbb8293110bd1288954ae872a3d5676eb66026aed82553eabf5";
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static String canonical(NBTBase value){if(!(value instanceof NBTTagCompound))return value.toString();NBTTagCompound tag=(NBTTagCompound)value;StringBuilder result=new StringBuilder("{");for(String key:new TreeSet<>(tag.getKeySet()))result.append(key).append(':').append(canonical(tag.getTag(key))).append(';');return result.append('}').toString();}
    private static NBTTagCompound input(int kind,int value){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("x",0);tag.setInteger("y",100);tag.setInteger("z",0);switch(kind){case 1:tag.setInteger("PowerLeverSize",value);break;case 2:tag.setByte("PowerLeverSize",(byte)value);break;case 3:tag.setShort("PowerLeverSize",(short)value);break;case 4:tag.setLong("PowerLeverSize",value);break;case 5:tag.setString("PowerLeverSize",Integer.toString(value));break;}tag.setInteger("Channel",7);tag.setIntArray("RedstoneChannels",new int[]{3,7});tag.setBoolean("LocalOn",true);tag.setBoolean("ChannelInitialized",true);tag.setIntArray("LatchedChannels",new int[]{3,7});tag.setIntArray("LatchedLevels",new int[]{5,11});tag.setInteger("MountRotation",3);tag.setInteger("ControlMountVersion",2);tag.setInteger("BaseHeight",2);tag.setInteger("BaseTilt",3);tag.setInteger("TiltDirection",1);NBTTagCompound forge=new NBTTagCompound();forge.setString("Unrelated","kept");tag.setTag("ForgeData",forge);return tag;}
    @SuppressWarnings("unchecked") private static void register(Block block)throws Exception{
        net.minecraftforge.registries.ForgeRegistry<Block> blocks=(net.minecraftforge.registries.ForgeRegistry<Block>)net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS;
        net.minecraftforge.registries.ForgeRegistry<Item> items=(net.minecraftforge.registries.ForgeRegistry<Item>)net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS;boolean b=blocks.isLocked(),i=items.isLocked();blocks.unfreeze();items.unfreeze();blocks.register(block);ItemBlock item=new ItemBlock(block);item.setRegistryName(block.getRegistryName());items.register(item);java.lang.reflect.Field field=Item.class.getDeclaredField("BLOCK_TO_ITEM");field.setAccessible(true);((Map<Block,Item>)field.get(null)).put(block,item);if(b)blocks.freeze();if(i)items.freeze();
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityRedstoneChannel.class,new ResourceLocation("vandorlabs","power_size_fixture"));
        BlockIndustrialLever[] blocks={new BlockCompactLever(),new BlockIndustrialLever(),new BlockTwinPowerLever("small_power_lever",false),new BlockTwinPowerLever("large_power_lever",true)};for(Block block:blocks)register(block);
        MessageDigest digest=MessageDigest.getInstance("SHA-256");int cases=0;int[] values={Integer.MIN_VALUE,-2,-1,0,1,2,3,Integer.MAX_VALUE};
        for(BlockIndustrialLever block:blocks)for(int kind=0;kind<6;kind++)for(int value:values){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(0,100,0);world.setBlockState(pos,block.getDefaultState(),2);TileEntityRedstoneChannel tile=(TileEntityRedstoneChannel)world.getTileEntity(pos);NBTTagCompound tag=input(kind,value),before=tag.copy();tile.readFromNBT(tag);require(tag.equals(before),"read mutated input");int loaded=tile.getPowerLeverSize();require(loaded==(kind==1?Math.max(0,Math.min(1,value)):block.defaultSize()),"loaded typed/default size id="+block.getRegistryName()+" kind="+kind+" raw="+value+" loaded="+loaded);
            NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound()),configuration=tile.configuration();require(saved.hasKey("PowerLeverSize",3)==(kind==1),"absent sentinel changed save presence");ItemStack item=new ItemStack(block);item.setTagInfo("RedstoneChannelSettings",configuration.copy());ItemStack restored=new ItemStack(item.writeToNBT(new NBTTagCompound()));require(restored.getSubCompound("RedstoneChannelSettings").equals(configuration),"item NBT roundtrip");
            StringBuilder output=new StringBuilder(block.getRegistryName().toString()).append('/').append(kind).append('/').append(value).append('/').append(loaded).append(canonical(saved)).append(canonical(configuration));
            for(int live:values){tile.setPowerLeverSize(live);require(world.getTileEntity(pos)==tile,"size edit replaced tile");output.append('/').append(live).append(':').append(tile.getPowerLeverSize()).append(':').append(world.dirty).append(':').append(world.renderUpdates);require(tile.getRedstoneChannels().equals(com.vandorlabs.redstone.ChannelList.of(3,7))&&tile.latchedLevel(3)==5&&tile.latchedLevel(7)==11&&tile.getBaseHeight()==2&&tile.getBaseTilt()==3&&tile.getTiltDirection()==1,"size edit altered channels/latch/mount");}
            IBakedModel original=new PowerLeverModel(new Marker(.25f),new Marker(.75f),block.defaultSize());ItemStack raw=new ItemStack(block);raw.setTagInfo("RedstoneChannelSettings",tag.copy());ItemStack untouched=raw.copy();IBakedModel selected=original.getOverrides().handleItemState(original,raw,null,null);require(ItemStack.areItemStacksEqual(raw,untouched),"item override mutated payload");int itemSize=kind==1?value:block.defaultSize();require((selected==original)==(itemSize<0||itemSize>1),"item invalid size did not preserve original model");output.append('/').append(selected==original).append(':').append(selected.getItemCameraTransforms().gui.scale.x);digest.update(output.toString().getBytes(StandardCharsets.UTF_8));cases++;
        }
        StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format("%02x",value&255));require(hash.toString().equals(GOLDEN),"frozen actual outputs changed: "+hash);System.out.println("Power lever actual size PASS cases="+cases+" golden="+hash+" four registry defaults, typed saved vs strict live/item fields, unchanged identity/latch/mount and item payload");
    }
    private static final class Marker implements IBakedModel {
        private final ItemCameraTransforms transforms;
        Marker(float size){ItemTransformVec3f gui=new ItemTransformVec3f(new org.lwjgl.util.vector.Vector3f(),new org.lwjgl.util.vector.Vector3f(),new org.lwjgl.util.vector.Vector3f(size,size,size));ItemTransformVec3f none=ItemTransformVec3f.DEFAULT;transforms=new ItemCameraTransforms(none,none,none,none,none,gui,none,none);}
        public List<BakedQuad> getQuads(net.minecraft.block.state.IBlockState state,EnumFacing side,long seed){return Collections.emptyList();}public boolean isAmbientOcclusion(){return false;}public boolean isGui3d(){return true;}public boolean isBuiltInRenderer(){return false;}public TextureAtlasSprite getParticleTexture(){return null;}public ItemCameraTransforms getItemCameraTransforms(){return transforms;}public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
    }
}
