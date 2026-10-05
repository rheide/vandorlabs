package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.blocks.BlockProgrammableGlass;
import net.minecraft.nbt.NBTTagCompound;

/** Saved old High choices remain functional and the two tier menus preserve designs. */
final class TextureTierChecks {
    static void run() {
        for(int design=0;design<29;design++) {
            int large=ScreenHousingTextures.doorIndex(design,1);
            require(ScreenHousingTextures.doorIndex(design,2)==large,"old High lookup did not map to Large");
            TileEntityLargeProgrammableDoor tile=new TileEntityLargeProgrammableDoor();
            tile.configure(design,2,true);require(tile.getDetail()==1,"old High configure");
            NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());tag.setInteger("SpaceDetail",2);tile.readFromNBT(tag);
            require(tile.getDetail()==1 && tile.getDesign()==design,"old High save lost design");
        }
        int hatchFamilies=0,legacyAliases=0;
        for(int choice=ScreenHousingTextures.LEGACY_COUNT;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++) {
            com.google.gson.JsonObject e=ScreenHousingTextures.entry(choice);
            if(e.has("alias")) {
                int target=ScreenHousingTextures.clamp(choice);require(target!=choice,"legacy alias unchanged");
                require(ScreenHousingTextures.texture(choice).equals(ScreenHousingTextures.texture(target)),"legacy material square texture lost");
                require(ScreenHousingTextures.fullTexture(choice).equals(ScreenHousingTextures.fullTexture(target)),"legacy face artwork lost");
                require(!ScreenHousingTextures.visible(choice),"old High still visible");legacyAliases++;
            }
            if(e.has("textureFamily") && e.get("detail").getAsInt()==1) {
                String family=e.get("textureFamily").getAsString();int small=ScreenHousingTextures.sizedTextureIndex(family,0);
                require(small!=choice && small>0,"missing Small hatch");
                require(HousingTextureList.doorSizeChoice(choice,0)==small && HousingTextureList.doorSizeChoice(small,1)==choice,"hatch size selection changed design");
                require(HousingTextureList.doorDetail(choice)==1 && HousingTextureList.doorDetail(small)==0,"hatch selected size detection");hatchFamilies++;
            }
        }
        require(hatchFamilies==8 && legacyAliases==29,"tier family/alias count");
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableGlass.class,new net.minecraft.util.ResourceLocation("minecraft:glass_tier_check"));
        TileEntityProgrammableGlass glass=new TileEntityProgrammableGlass();glass.setShade(1);glass.setJoin(false);glass.setSize(2);
        require(glass.getSize()==1,"legacy glass setter");
        NBTTagCompound old=glass.writeToNBT(new NBTTagCompound());old.setInteger("GlassSize",2);glass.readFromNBT(old);
        require(glass.getSize()==1 && glass.getShade()==1 && !glass.isJoin(),"legacy glass save migration changed shade/join");
        BlockProgrammableGlass block=new BlockProgrammableGlass("glass_tier_check");
        require(block.getStateFromMeta(4).getValue(BlockProgrammableGlass.SIZE)==1 && block.getStateFromMeta(5).getValue(BlockProgrammableGlass.SIZE)==1,"legacy block metadata migration");
        System.out.println("PASS: 29 legacy door texture aliases, eight Small/Large hatch families, door NBT and Programmable Glass metadata/NBT preserve designs, tint and joining");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
