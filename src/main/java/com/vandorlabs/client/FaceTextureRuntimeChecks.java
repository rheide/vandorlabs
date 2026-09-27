package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.items.ProgrammableSettings;
import com.vandorlabs.tiles.FaceTextures;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** World save and copying contracts, including disabled and inherited faces. */
final class FaceTextureRuntimeChecks {
    static void run(EntityPlayer player) {
        BlockPos source = new BlockPos(52, 250, 32), target = source.east();
        player.world.setBlockState(source, ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(), 3);
        player.world.setBlockState(target, ModBlocks.PROGRAMMABLE_SLAB.getDefaultState(), 3);
        try {
            TileEntityAnimatedScreenSelector a = (TileEntityAnimatedScreenSelector)player.world.getTileEntity(source);
            TileEntityAnimatedScreenSelector b = (TileEntityAnimatedScreenSelector)player.world.getTileEntity(target);
            require(!a.getFaceTextures().enabled, "new block overrides enabled");
            a.setHousingTexture(3);
            a.setFaceTextures(new FaceTextures(true, new int[]{-1,4,5,-1,-1,6}));
            require(a.getFaceTextures().texture(0, a.getHousingTexture()) == 3, "main inheritance");
            a.setHousingTexture(2);
            require(a.getFaceTextures().texture(0, 2) == 2 && a.getFaceTextures().texture(1, 2) == 4, "override changed with main");
            NBTTagCompound saved = a.writeToNBT(new NBTTagCompound());
            saved.setInteger("x", target.getX()); saved.setInteger("y", target.getY()); saved.setInteger("z", target.getZ());
            b.readFromNBT(saved);
            require(b.getFaceTextures().equals(a.getFaceTextures()), "save round trip");
            ProgrammableSettings.apply(player.world, target, ProgrammableSettings.capture(player.world, source));
            require(b.getFaceTextures().equals(a.getFaceTextures()), "enabled copy");
            a.setFaceTextures(new FaceTextures(false, new int[]{1,1,1,1,1,1}));
            NBTTagCompound copied = ProgrammableSettings.capture(player.world, source);
            require(!copied.getCompoundTag(ProgrammableSettings.FACE_TEXTURES).hasKey("choices"), "disabled copy leaked face choices");
            ProgrammableSettings.apply(player.world, target, copied);
            require(!b.getFaceTextures().enabled && b.getFaceTextures().choice(1) == 4, "disabled copy changed face choices");
            saved.removeTag("FaceTexturesEnabled"); saved.removeTag("FaceTextures");
            b.readFromNBT(saved);
            require(!b.getFaceTextures().enabled && b.getFaceTextures().choice(1) == -1, "legacy data default");
            require(new FaceTextures(true, new int[]{999,-2,0,1,2,3}).choice(0) == -1, "invalid texture fallback");
        } finally {
            player.world.setBlockToAir(source); player.world.setBlockToAir(target);
        }
        BlockPos support = source.down();
        for (net.minecraft.block.BlockSlab.EnumBlockHalf half : net.minecraft.block.BlockSlab.EnumBlockHalf.values()) {
            player.world.setBlockState(support, ModBlocks.PROGRAMMABLE_SLAB.getDefaultState()
                    .withProperty(com.vandorlabs.blocks.BlockProgrammableSlab.HALF, half), 3);
            net.minecraft.block.Block door = net.minecraft.block.Block.REGISTRY.getObject(new net.minecraft.util.ResourceLocation("vandorlabs", "programmable_door"));
            require(door.canPlaceBlockAt(player.world, source), "door rejected slab " + half);
            net.minecraft.block.state.IBlockState input = ModBlocks.PROGRAMMABLE_INPUT.getStateForPlacement(
                    player.world, support.north(), net.minecraft.util.EnumFacing.NORTH, .5F,
                    half == net.minecraft.block.BlockSlab.EnumBlockHalf.TOP ? .2F : .8F, .5F, 0, player);
            require(input.getValue(com.vandorlabs.blocks.BlockProgrammableInput.UPPER)
                    == (half == net.minecraft.block.BlockSlab.EnumBlockHalf.TOP), "input slab half mismatch");
        }
        player.world.setBlockToAir(support);
        System.out.println("[vandorlabs][reprolab] face-textures-runtime PASS");
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
