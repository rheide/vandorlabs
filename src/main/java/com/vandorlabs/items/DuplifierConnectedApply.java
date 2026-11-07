package com.vandorlabs.items;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Discover the original matching region before applying any copied settings. */
public final class DuplifierConnectedApply {
    public static final int MAX_CELLS = ConnectedCopySelection.MAX_CELLS;

    private DuplifierConnectedApply() { }

    private static boolean editable(World world, BlockPos pos, EntityPlayer player,
            EnumFacing face, ItemStack tool) {
        return world.isBlockLoaded(pos) && world.isBlockModifiable(player, pos)
                && player.canPlayerEdit(pos, face, tool);
    }

    private static NBTTagCompound configuration(World world, BlockPos pos,boolean ignoreDirectionality) {
        NBTTagCompound settings = ProgrammableSettings.capture(world, pos);
        // Disabled face overrides still hold saved choices. Those must match
        // too, even though the normal copy snapshot omits them while disabled.
        TileEntity tile = world.getTileEntity(pos);
        if (settings != null && tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector) {
            com.vandorlabs.tiles.TileEntityAnimatedScreenSelector screen =
                    (com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile;
            settings.setBoolean("placement_ceiling", screen.isCeilingMounted());
            settings.setInteger("placement_ceiling_position", screen.getCeilingPosition(-1));
            settings.setInteger("placement_wall_position", screen.getWallPosition(-1));
        }
        if (settings != null && settings.hasKey(ProgrammableSettings.FACE_TEXTURES)
                && tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)
            settings.getCompoundTag(ProgrammableSettings.FACE_TEXTURES).setIntArray("choices",
                    ((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile)
                            .getFaceTextures().choices());
        if (settings != null && ignoreDirectionality) {
            CopiedDirectionality.strip(settings::removeTag);
        }
        return settings;
    }

    private static boolean matchingState(IBlockState original, IBlockState candidate,boolean ignoreDirectionality) {
        if (original.getBlock() != candidate.getBlock()) return false;
        for (net.minecraft.block.properties.IProperty<?> property : original.getPropertyKeys()) {
            String name = property.getName();
            if (ignoreDirectionality && CopiedDirectionality.ignoreProperty(name,
                    property.getValueClass()==EnumFacing.class,
                    property.getValueClass()==EnumFacing.Axis.class)) continue;
            if (!original.getValue(property).equals(candidate.getValue(property))) return false;
        }
        return true;
    }

    /** Returns applied logical blocks, or -1 when the region exceeds the limit. */
    public static int apply(World world, BlockPos clicked, ItemStack tool,
            EntityPlayer player, EnumFacing face) {
        if (world.isRemote || !editable(world, clicked, player, face, tool)) return 0;
        BlockPos origin = ProgrammableTarget.settingsPos(world, clicked);
        if (!editable(world, origin, player, face, tool)) return 0;
        IBlockState originalState = world.getBlockState(origin);
        boolean ignoreDirectionality=DuplifierApplyOptions.ignoreDirectionality(tool);
        NBTTagCompound originalSettings = configuration(world, origin,ignoreDirectionality);
        if (originalSettings == null) return 0;
        ConnectedCopySelection.Result<BlockPos> selected=ConnectedCopySelection.discover(
                clicked.toImmutable(),origin.toImmutable(),new ConnectedCopySelection.Access<BlockPos>() {
            public BlockPos matchingOwner(BlockPos cell) {
                if(!editable(world,cell,player,face,tool))return null;
                BlockPos root=ProgrammableTarget.settingsPos(world,cell);
                return editable(world,root,player,face,tool)
                        && matchingState(originalState,world.getBlockState(root),ignoreDirectionality)
                        && originalSettings.equals(configuration(world,root,ignoreDirectionality))
                        ?root.toImmutable():null;
            }
            public BlockPos offset(BlockPos cell,int x,int y,int z){return cell.add(x,y,z);}
        });
        if(selected.exceeded)return -1;
        // A selected section can update its linked peers. Snapshot the whole
        // original selection before any callback changes another owner's data.
        for (BlockPos target : selected.owners) DuplifierUndo.before(player,world,target);
        int applied = 0;
        for (BlockPos target : selected.owners) {
            if (editable(world, target, player, face, tool)
                    && world.getBlockState(target).getBlock() == originalState.getBlock()
                    && ItemDuplifier.applyTo(world, target, tool, player)) applied++;
        }
        return applied;
    }
}
