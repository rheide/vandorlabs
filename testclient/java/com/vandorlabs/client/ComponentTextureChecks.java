package com.vandorlabs.client;

import java.util.*;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

/** Provider signatures verified against Malisis Doors 7.3.0/Core 6.5.1; no GL context. */
public final class ComponentTextureChecks {
    private static final TextureAtlasSprite LOWER = new Sprite("malisisdoors:blocks/test_lower");
    private static final TextureAtlasSprite UPPER = new Sprite("malisisdoors:blocks/test_upper");
    private static final TextureAtlasSprite MISSING = new Sprite("missingno");
    static void run() {
        ProviderDoor door = new ProviderDoor();
        for (boolean upper : new boolean[]{false,true}) {
            IBlockState state=door.getDefaultState().withProperty(BlockDoor.HALF,
                    upper?BlockDoor.EnumDoorHalf.UPPER:BlockDoor.EnumDoorHalf.LOWER);
            require(ComponentBlockTextures.sprite(state,upper)==(upper?UPPER:LOWER),"component door halves");
            require(ComponentBlockTextures.icon(new StateProvider(),state,upper)==(upper?UPPER:LOWER),"state provider halves");
            require(ComponentBlockTextures.icon(new BigProvider(),state,upper)==LOWER,"big door face instead of inventory icon");
            require(ComponentBlockTextures.icon(new SimpleProvider(),state,upper)==LOWER,"simple provider");
            require(ComponentBlockTextures.icon(new Object(),state,upper)==null,"absent optional API");
            require(ComponentBlockTextures.icon(new BrokenProvider(),state,upper)==null,"provider failure fallback");
        }
        require(!CustomBlockTextures.usable(MISSING) && !CustomBlockTextures.usable(null),"missing sprite rejection");
        require(ComponentBlockTextures.sprite(net.minecraft.init.Blocks.STONE.getDefaultState(),false)==null,"ordinary block fallback");
        System.out.println("PASS: optional component door textures resolve both halves, state/simple/big providers, missing artwork and absent/broken providers (no GL)");
    }
    public static final class ProviderDoor extends BlockDoor {
        public ProviderDoor(){super(Material.WOOD);}
        public Iterable<Object> getComponents(){return Arrays.asList(new Object(),new BrokenProvider(),new MissingProvider(),new DoorProvider());}
    }
    public static final class DoorProvider {
        public TextureAtlasSprite getIcon(boolean upper,boolean flipped,EnumFacing face){require(!flipped && face==EnumFacing.NORTH,"unflipped face");return upper?UPPER:LOWER;}
        public TextureAtlasSprite getIcon(){return MISSING;}
    }
    public static final class StateProvider {
        public TextureAtlasSprite getIcon(IBlockState state,EnumFacing face){return state.getValue(BlockDoor.HALF)==BlockDoor.EnumDoorHalf.UPPER?UPPER:LOWER;}
    }
    public static final class BigProvider {
        public TextureAtlasSprite getDoorIcon(){return LOWER;}
        public TextureAtlasSprite getIcon(){return MISSING;}
    }
    public static final class SimpleProvider {public TextureAtlasSprite getIcon(){return LOWER;}}
    public static final class MissingProvider {public TextureAtlasSprite getIcon(){return MISSING;}}
    public static final class BrokenProvider {public TextureAtlasSprite getIcon(){throw new IllegalStateException("unavailable");}}
    private static final class Sprite extends TextureAtlasSprite {Sprite(String name){super(name);}}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
