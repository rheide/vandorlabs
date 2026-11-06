package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.tiles.*;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Actual released tile decoding and configured-item output, before native extraction. */
public final class TrapdoorSettingsLegacyChecks {
    private static final String GOLDEN="51e3739f7f31e932d88d004ac346c2eae096d0be75a0c8871cd6e74506ac80da";
    public static void main(String[] args)throws Exception {
        net.minecraft.init.Bootstrap.register();JsonArray fixtures=new JsonArray();
        String[] fields={"housingTexture","TrapdoorPosition","TrapdoorTextureLayout","TrapdoorTileTexture","TrapdoorCover","TrapdoorCoverFacing","TrapdoorSliding","TrapdoorSlideIntoWall","TrapdoorSlideOverSurface","TrapdoorTrigger","RedstoneChannel","DiagonalHingeSide","DiagonalRotationFlipped","DiagonalInverted","DiagonalFacing"};
        for(boolean diagonal:new boolean[]{false,true}) {
            add(fixtures,diagonal,new NBTTagCompound());
            for(String field:fields)for(int kind=0;kind<11;kind++){
                NBTTagCompound n=new NBTTagCompound();switch(kind){case 0:n.setInteger(field,-1);break;case 1:n.setInteger(field,0);break;case 2:n.setInteger(field,1);break;case 3:n.setInteger(field,2);break;case 4:n.setInteger(field,3);break;case 5:n.setInteger(field,Integer.MIN_VALUE);break;case 6:n.setInteger(field,Integer.MAX_VALUE);break;case 7:n.setString(field,"1");break;case 8:n.setByte(field,(byte)1);break;case 9:n.setLong(field,1);break;default:n.setDouble(field,1.5);}
                add(fixtures,diagonal,n);
            }
            for(int p=0;p<3;p++)for(int flags=0;flags<128;flags++){
                NBTTagCompound n=new NBTTagCompound();n.setInteger("TrapdoorPosition",p);n.setInteger("housingTexture",ScreenHousingTextures.DEFAULT_TRAPDOOR);n.setBoolean("TrapdoorSliding",(flags&1)!=0);n.setBoolean("TrapdoorCover",(flags&2)!=0);n.setBoolean("TrapdoorSlideIntoWall",(flags&4)!=0);n.setBoolean("TrapdoorSlideOverSurface",(flags&8)!=0);n.setBoolean("TrapdoorTileTexture",(flags&16)!=0);n.setBoolean("DiagonalRotationFlipped",(flags&32)!=0);n.setBoolean("DiagonalInverted",(flags&64)!=0);n.setInteger("TrapdoorTrigger",flags%3);n.setInteger("TrapdoorTextureLayout",flags%4-1);n.setInteger("TrapdoorCoverFacing",flags%4);n.setInteger("DiagonalHingeSide",flags%3);n.setInteger("DiagonalFacing",flags%4);n.setInteger("RedstoneChannel",7);n.setIntArray("RedstoneChannels",flags%2==0?new int[]{9,7,9}:new int[]{-1,7});add(fixtures,diagonal,n);
            }
            for(int link=0;link<4;link++){NBTTagCompound n=new NBTTagCompound();n.setBoolean("TrapdoorCover",true);if(link==0)n.setLong("TrapdoorPartner",42);if(link==1)n.setLong("TrapdoorSquare",42);if(link>=2){NBTTagList members=new NBTTagList();for(int i=0;i<(link==2?1:65);i++)members.appendTag(new NBTTagLong(i));n.setTag("TrapdoorAssembly",members);}add(fixtures,diagonal,n);}
        }
        String json=fixtures.toString();java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");StringBuilder hex=new StringBuilder();for(byte b:hash.digest(json.getBytes(StandardCharsets.UTF_8)))hex.append(String.format("%02x",b&255));if(!hex.toString().equals(GOLDEN))throw new AssertionError("Released Trapdoor settings changed "+hex);
        Path output=Paths.get(args[0]);Files.createDirectories(output.toAbsolutePath().getParent());Files.write(output,json.getBytes(StandardCharsets.UTF_8));System.out.println("Actual Trapdoor settings PASS cases="+fixtures.size()+" sha256="+hex);
    }
    private static void add(JsonArray fixtures,boolean diagonal,NBTTagCompound input){
        TileEntityProgrammableTrapdoor tile=diagonal?new TileEntityProgrammableDiagonalTrapdoor():new TileEntityProgrammableTrapdoor();NBTTagCompound before=input.copy();tile.readFromNBT(input);if(!before.equals(input))throw new AssertionError("Input mutated");JsonObject row=new JsonObject();row.addProperty("diagonal",diagonal);row.addProperty("input",input.toString());row.addProperty("item",tile.itemSettings().toString());row.addProperty("offsetEligible",tile.canOffsetClosedLeaf());row.addProperty("defaultTexture",ScreenHousingTextures.DEFAULT_TRAPDOOR);fixtures.add(row);
    }
}
