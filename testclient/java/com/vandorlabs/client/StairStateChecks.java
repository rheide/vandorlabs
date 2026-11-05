package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.ramp.ControllerPlatform;
import net.minecraft.nbt.*;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.ResourceLocation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Frozen real-NBT saved/default/live stair policy, before portable extraction. */
public final class StairStateChecks {
    private static final String GOLDEN="ace0111a9c5c249973d46c84523a4cbd08673ef6891cf98bcb83768432e0b4d0";
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void field(NBTTagCompound tag,String key,int kind,int value){switch(kind){case 1:tag.setInteger(key,value);break;case 2:tag.setByte(key,(byte)value);break;case 3:tag.setShort(key,(short)value);break;case 4:tag.setLong(key,value);break;case 5:tag.setFloat(key,value+.75F);break;case 6:tag.setString(key,Integer.toString(value));break;}}
    private static NBTTagCompound input(int kind,int tread,int rollKind,int roll){NBTTagCompound tag=new NBTTagCompound();field(tag,"StairTread",kind,tread);field(tag,"StairRoll",rollKind,roll);tag.setInteger("HousingTextureVersion",1);tag.setInteger("housingTexture",7);tag.setInteger("SideTexture",9);tag.setInteger("MainTextureLayout",2);tag.setBoolean("SlabTileSides",true);tag.setBoolean("FaceTexturesEnabled",false);tag.setIntArray("FaceTextures",new int[]{1,2,3,4,5,6});return tag;}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class,new ResourceLocation("vandorlabs","stair_state_fixture"));MessageDigest digest=MessageDigest.getInstance("SHA-256");int cases=0;int[] values={Integer.MIN_VALUE,-1,0,1,2,3,4,8,16,17,Integer.MAX_VALUE};
        for(int kind=0;kind<7;kind++)for(int tread:values)for(int rollKind=0;rollKind<7;rollKind++)for(int roll:values){
            TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setStairGeometry(16,2);NBTTagCompound tag=input(kind,tread,rollKind,roll),before=tag.copy();tile.readFromNBT(tag);require(tag.equals(before),"read mutated input");int expectedTread=kind==1&&ControllerPlatform.validTreadPixels(tread)?tread:8,expectedRoll=Math.max(0,Math.min(2,tag.getInteger("StairRoll")));require(tile.stairTread()==expectedTread&&tile.stairRoll()==expectedRoll&&tile.stairGeometry()==(expectedTread|(expectedRoll<<5)),"typed/default stair read");NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());require(saved.hasKey("StairTread",3)&&saved.hasKey("StairRoll",3),"explicit int writer");TileEntityAnimatedScreenSelector copy=new TileEntityAnimatedScreenSelector();copy.readFromNBT(saved);require(copy.stairGeometry()==tile.stairGeometry(),"save roundtrip");ItemStack item=new ItemStack(Items.STICK);item.setTagInfo("BlockEntityTag",saved);ItemStack restored=new ItemStack(item.writeToNBT(new NBTTagCompound()));TileEntityAnimatedScreenSelector placed=new TileEntityAnimatedScreenSelector();placed.readFromNBT(restored.getSubCompound("BlockEntityTag"));require(placed.stairGeometry()==tile.stairGeometry()&&placed.getSideTexture()==tile.getSideTexture()&&placed.getFaceTextures().equals(tile.getFaceTextures()),"configured item roundtrip and dormant faces");
            StringBuilder output=new StringBuilder().append(kind).append('/').append(tread).append('/').append(rollKind).append('/').append(roll).append(':').append(tile.stairGeometry());
            for(int next:values)for(int pose:new int[]{-1,0,1,2,3}){int previous=tile.stairGeometry();tile.setStairGeometry(next,pose);require(tile.stairGeometry()==(ControllerPlatform.validTreadPixels(next)&&pose>=0&&pose<=2?(next|(pose<<5)):previous),"invalid live edit must preserve previous");require(tile.getHousingTexture()==copy.getHousingTexture()&&tile.getSideTexture()==copy.getSideTexture()&&tile.getMainTextureLayout()==copy.getMainTextureLayout()&&tile.getFaceTextures().equals(copy.getFaceTextures()),"stair edit changed housing");output.append('/').append(tile.stairGeometry());}digest.update(output.toString().getBytes(StandardCharsets.UTF_8));cases++;
        }
        StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format("%02x",value&255));require(GOLDEN.equals(hash.toString()),"frozen stair policy changed "+hash);System.out.println("Stair actual-NBT policy PASS cases="+cases+" SHA256="+hash+" typed presence/numeric roll, saved16px/roll2, invalid live edits, item/dormant-face roundtrip");
    }
}
