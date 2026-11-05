package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import net.minecraft.nbt.NBTTagCompound;
import java.util.Arrays;
import java.util.function.Supplier;

/** Real legacy NBT fixtures for the shared selector and every subclass. */
public final class HousingSettingsChecks {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] ignored){
        net.minecraft.init.Bootstrap.register();
        java.util.List<Supplier<TileEntityAnimatedScreenSelector>> factories=Arrays.asList(
            TileEntityAnimatedScreenSelector::new,TileEntityProgrammableLight::new,
            TileEntityProgrammableStorage::new,TileEntityProgrammableTrigger::new,TileEntityHexTank::new);
        com.vandorlabs.blocks.BlockProgrammableBlock block=new com.vandorlabs.blocks.BlockProgrammableBlock();
        net.minecraftforge.registries.ForgeRegistry<net.minecraft.block.Block> blocks=(net.minecraftforge.registries.ForgeRegistry<net.minecraft.block.Block>)net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS;
        net.minecraftforge.registries.ForgeRegistry<net.minecraft.item.Item> items=(net.minecraftforge.registries.ForgeRegistry<net.minecraft.item.Item>)net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS;
        boolean blocksLocked=blocks.isLocked(),itemsLocked=items.isLocked();blocks.unfreeze();items.unfreeze();
        blocks.register(block);net.minecraft.item.ItemBlock item=new net.minecraft.item.ItemBlock(block);item.setRegistryName(block.getRegistryName());items.register(item);
        try {java.lang.reflect.Field map=net.minecraft.item.Item.class.getDeclaredField("BLOCK_TO_ITEM");map.setAccessible(true);
            @SuppressWarnings("unchecked") java.util.Map<net.minecraft.block.Block,net.minecraft.item.Item> mapping=(java.util.Map<net.minecraft.block.Block,net.minecraft.item.Item>)map.get(null);mapping.put(block,item);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        if(blocksLocked)blocks.freeze();if(itemsLocked)items.freeze();
        NBTTagCompound snapshotInput=new NBTTagCompound();
        snapshotInput.setIntArray("FaceTextures",new int[]{1,2,3,4,5,6});
        com.vandorlabs.persistence.HousingSettings snapshot=com.vandorlabs.persistence.HousingSettings.read(
                new com.vandorlabs.persistence.NbtPrimitiveData(snapshotInput),0,false,
                ScreenHousingTextures::clamp,ScreenHousingTextures::validChoice);
        NBTTagCompound snapshotOutput=new NBTTagCompound();
        snapshot.write(new com.vandorlabs.persistence.NbtPrimitiveData(snapshotOutput));
        snapshotOutput.getIntArray("FaceTextures")[0]=7;
        require(snapshot.faces()[0]==1,"serialized NBT must not alias immutable housing snapshot");
        int count=0;
        for(Supplier<TileEntityAnimatedScreenSelector> factory:factories){
            TileEntityAnimatedScreenSelector tile=factory.get();
            net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(tile.getClass(),
                new net.minecraft.util.ResourceLocation("minecraft:housing_settings_"+count));
            tile.setHousingTexture(15);int previous=tile.getHousingTexture();tile.setSlabTileSides(true);
            int missingExpected=previous;
            if(tile instanceof TileEntityProgrammableStorage){int legacyDefault=ScreenHousingTextures.DEFAULT_STORAGE;
                missingExpected=ScreenHousingTextures.clamp(legacyDefault==14?0:legacyDefault>14?legacyDefault-1:legacyDefault);}
            NBTTagCompound old=new NBTTagCompound();tile.readFromNBT(old);
            require(tile.getHousingTexture()==missingExpected&&tile.isSlabTileSides()&&tile.getMainTextureLayout()==1,"missing values "+tile.getClass());
            old.setString("housingTexture","14");old.setInteger("SlabTileSides",0);tile.readFromNBT(old);
            require(tile.getHousingTexture()==missingExpected&&tile.isSlabTileSides(),"mistyped values");
            old=new NBTTagCompound();old.setInteger("housingTexture",14);old.setBoolean("SlabTileSides",false);
            old.setInteger("SideTexture",7);old.setBoolean("FaceTexturesEnabled",true);old.setIntArray("FaceTextures",new int[]{1,2,3,4,5,6});tile.readFromNBT(old);
            require(tile.getHousingTexture()==ScreenHousingTextures.clamp(0)&&tile.getMainTextureLayout()==0,"old removed grille/layout");
            require(Arrays.equals(tile.getFaceTextures().choices(),new int[]{1,2,3,4,5,6}),"six old faces");
            old.setInteger("housingTexture",15);old.setInteger("MainTextureLayout",99);tile.readFromNBT(old);
            require(tile.getHousingTexture()==ScreenHousingTextures.clamp(14)&&tile.getMainTextureLayout()==0,"old shifted index/invalid layout");
            tile.setSlabTileSides(true);require(tile.getMainTextureLayout()==1,"raw fallback remains driven by slab sides");
            old.setInteger("HousingTextureVersion",1);old.setInteger("MainTextureLayout",2);tile.readFromNBT(old);
            require(tile.getHousingTexture()==ScreenHousingTextures.clamp(15)&&tile.getMainTextureLayout()==2,"versioned layout");
            NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileEntityAnimatedScreenSelector copy=factory.get();copy.readFromNBT(saved);
            require(copy.getHousingTexture()==tile.getHousingTexture()&&copy.getSideTexture()==7&&copy.getMainTextureLayout()==2&&copy.getFaceTextures().equals(tile.getFaceTextures()),"current NBT roundtrip");
            net.minecraft.item.ItemStack drop=block.createConfiguredDrop(tile);
            net.minecraft.item.ItemStack itemCopy=new net.minecraft.item.ItemStack(drop.writeToNBT(new NBTTagCompound()));
            require(!itemCopy.isEmpty()&&itemCopy.getSubCompound("BlockEntityTag")!=null,"configured drop item NBT");
            TileEntityAnimatedScreenSelector placed=factory.get();placed.readFromNBT(itemCopy.getSubCompound("BlockEntityTag"));
            require(placed.getHousingTexture()==tile.getHousingTexture()&&placed.getSideTexture()==7&&placed.getMainTextureLayout()==2&&placed.getFaceTextures().equals(tile.getFaceTextures()),"configured drop/item housing roundtrip");
            count++;
        }
        System.out.println("PASS: legacy housing NBT prior values, unversioned palettes, layout fallback, six faces and configured item drops on "+count+" tile families");
    }
}
