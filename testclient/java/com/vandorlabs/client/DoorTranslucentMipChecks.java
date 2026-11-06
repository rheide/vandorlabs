package com.vandorlabs.client;

import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Random;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Freeze the actual existing pane sprite's alpha-weighted mip implementation. */
public final class DoorTranslucentMipChecks {
    private static TextureAtlasSprite sprite(String name)throws Exception{Class<?> type=Class.forName("com.vandorlabs.client.SpaceDoorTextures$RectangularSprite");Constructor<?> constructor=type.getDeclaredConstructor(String.class);constructor.setAccessible(true);TextureAtlasSprite sprite=(TextureAtlasSprite)constructor.newInstance(name);sprite.setIconWidth(2);sprite.setIconHeight(2);return sprite;}
    private static int mip(TextureAtlasSprite sprite,int[] pixels){sprite.setFramesTextureData(Collections.singletonList(new int[][]{pixels}));sprite.generateMipmaps(1);return sprite.getFrameTextureData(0)[1][0];}
    public static void main(String[] args)throws Exception{
        TextureAtlasSprite glass=sprite("vandorlabs:blocks/space_doors/medium/observation_glass");
        int[] faint={0x32abcdef,0,0,0};if((mip(glass,faint)>>>24)==0)throw new AssertionError("Actual low-alpha reflection disappeared");
        Random random=new Random(0x44524d495053L);MessageDigest digest=MessageDigest.getInstance("SHA-256");int cases=65536;for(int i=0;i<cases;i++){int[] pixels={random.nextInt(),random.nextInt(),random.nextInt(),random.nextInt()};digest.update(ByteBuffer.allocate(4).putInt(mip(glass,pixels)).array());}
        StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format("%02x",b&255));String expected=System.getProperty("vandorlabs.doorMipGolden","");if(!expected.isEmpty()&&!expected.equals(hash.toString()))throw new AssertionError("Existing pane mip arithmetic changed "+hash);
        System.out.println("Door translucent mips actual PASS cases="+cases+" SHA256="+hash+" actual faint reflection retained; native cutout negative control separate");
    }
}
