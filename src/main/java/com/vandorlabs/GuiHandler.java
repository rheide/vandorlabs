package com.vandorlabs;

import com.vandorlabs.client.GuiAnimatedScreenSelector;
import com.vandorlabs.client.GuiProgrammableInput;
import com.vandorlabs.client.GuiProgrammableHalfConsole;
import com.vandorlabs.blocks.BlockProgrammableInput;
import com.vandorlabs.blocks.BlockProgrammableHalfConsole;
import com.vandorlabs.blocks.BlockProgrammableFullInput;
import com.vandorlabs.container.ContainerAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.container.ContainerRedstoneChannel;
import com.vandorlabs.client.GuiRedstoneChannel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public class GuiHandler implements IGuiHandler {

    public static final int GUI_ANIMATED_SCREEN_SELECTOR = 0;
    public static final int GUI_RAMP_CONTROLLER = 1;
    public static final int GUI_REDSTONE_CHANNEL = 2;
    public static final int GUI_SPACE_DOOR = 3;
    public static final int GUI_PROGRAMMABLE_GLASS = 4;

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID==GUI_PROGRAMMABLE_GLASS) {
            TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));
            if (tile instanceof com.vandorlabs.tiles.TileEntityProgrammableGlass)
                return new com.vandorlabs.container.ContainerProgrammableGlass((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile);
        }
        if (ID==GUI_SPACE_DOOR) {
            TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));
            if (tile instanceof com.vandorlabs.tiles.TileEntitySpaceDoor)
                return new com.vandorlabs.container.ContainerSpaceDoor((com.vandorlabs.tiles.TileEntitySpaceDoor)tile);
        }
        if (ID == GUI_REDSTONE_CHANNEL) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof RedstoneChannelMember)
                return new ContainerRedstoneChannel((RedstoneChannelMember) te);
        }
        if (ID == GUI_RAMP_CONTROLLER) {
            TileEntity te=world.getTileEntity(new BlockPos(x,y,z));
            if (te instanceof com.vandorlabs.tiles.TileEntityRampController)
                return new com.vandorlabs.container.ContainerRampController((com.vandorlabs.tiles.TileEntityRampController)te);
        }
        if (ID == GUI_ANIMATED_SCREEN_SELECTOR) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof TileEntityAnimatedScreenSelector) {
                return new ContainerAnimatedScreenSelector(player.inventory, (TileEntityAnimatedScreenSelector) te);
            }
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID==GUI_PROGRAMMABLE_GLASS) {
            TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));
            if (tile instanceof com.vandorlabs.tiles.TileEntityProgrammableGlass)
                return new com.vandorlabs.client.GuiProgrammableGlass((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile);
        }
        if (ID==GUI_SPACE_DOOR) {
            TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));
            if (tile instanceof com.vandorlabs.tiles.TileEntitySpaceDoor)
                return new com.vandorlabs.client.GuiSpaceDoor((com.vandorlabs.tiles.TileEntitySpaceDoor)tile);
        }
        if (ID == GUI_REDSTONE_CHANNEL) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof RedstoneChannelMember)
                return new GuiRedstoneChannel((RedstoneChannelMember) te);
        }
        if (ID == GUI_RAMP_CONTROLLER) {
            TileEntity te=world.getTileEntity(new BlockPos(x,y,z));
            if (te instanceof com.vandorlabs.tiles.TileEntityRampController)
                return new com.vandorlabs.client.GuiRampController((com.vandorlabs.tiles.TileEntityRampController)te);
        }
        if (ID == GUI_ANIMATED_SCREEN_SELECTOR) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof TileEntityAnimatedScreenSelector) {
                if (world.getBlockState(new BlockPos(x, y, z)).getBlock()
                        instanceof BlockProgrammableFullInput) {
                    return new GuiAnimatedScreenSelector(player.inventory,
                            (TileEntityAnimatedScreenSelector) te);
                }
                if (world.getBlockState(new BlockPos(x, y, z)).getBlock()
                        instanceof BlockProgrammableInput) {
                    return new GuiProgrammableInput(player.inventory,
                            (TileEntityAnimatedScreenSelector) te);
                }
                if (world.getBlockState(new BlockPos(x, y, z)).getBlock()
                        instanceof BlockProgrammableHalfConsole) {
                    return new GuiProgrammableHalfConsole(player.inventory,
                            (TileEntityAnimatedScreenSelector) te);
                }
                return new GuiAnimatedScreenSelector(player.inventory,
                        (TileEntityAnimatedScreenSelector) te);
            }
        }
        return null;
    }
}
