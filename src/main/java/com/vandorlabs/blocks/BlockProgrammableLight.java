package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** A single light with selectable face art and brightness. */
public final class BlockProgrammableLight extends BlockAnimatedScreenSelector {
    public BlockProgrammableLight() {
        super("programmable_light");
        setLightLevel(1.0F);
    }

    @Override public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityProgrammableLight();
    }

    @Override protected EnumFacing placementFacing(World world, BlockPos pos,
            EnumFacing clickedFace, EntityLivingBase placer) {
        IBlockState support = world.getBlockState(pos.offset(clickedFace.getOpposite()));
        return support.getBlock() == this ? support.getValue(FACING) : clickedFace;
    }

    @Override public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world == null || pos == null ? null : world.getTileEntity(pos);
        return tile instanceof TileEntityProgrammableLight
                && ((TileEntityProgrammableLight) tile).isOn()
                ? ((TileEntityProgrammableLight) tile).getLightLevel() : 0;
    }

    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
            EntityPlayer player, EnumHand hand, EnumFacing side,
            float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return true;
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityProgrammableLight) {
                if (player.isSneaking()) {
                    if (player.capabilities.isCreativeMode) player.openGui(VandorLabs.instance,
                            GuiHandler.GUI_PROGRAMMABLE_LIGHT, world,
                            pos.getX(), pos.getY(), pos.getZ());
                } else {
                    TileEntityProgrammableLight light = (TileEntityProgrammableLight) tile;
                    if (!light.isManual()) return true;
                    boolean next = !light.isOn();
                    for (BlockPos member : ProgrammableLightConnections.members(light, state)) {
                        TileEntity other = world.getTileEntity(member);
                        if (other instanceof TileEntityProgrammableLight)
                            ((TileEntityProgrammableLight) other).setOn(next);
                    }
                }
            }
        }
        return !player.isSneaking() || player.capabilities.isCreativeMode;
    }
}
