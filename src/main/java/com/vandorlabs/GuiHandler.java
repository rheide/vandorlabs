package com.vandorlabs;

import com.vandorlabs.client.GuiAnimatedScreenSelector;
import com.vandorlabs.client.GuiProgrammableInput;
import com.vandorlabs.client.GuiProgrammableHalfConsole;
import com.vandorlabs.blocks.BlockProgrammableInput;
import com.vandorlabs.blocks.BlockProgrammableHalfConsole;
import com.vandorlabs.blocks.BlockProgrammableFullInput;
import com.vandorlabs.blocks.BlockProgrammableWall;
import com.vandorlabs.blocks.BlockProgrammableBlock;
import com.vandorlabs.blocks.BlockProgrammableTrigger;
import com.vandorlabs.blocks.BlockProgrammableSlab;
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
    public static final int GUI_TRANSMITTER_ITEM=29;
    public static final int GUI_COPY=26;
    public static final int GUI_SURFACE=28;
    public static final int GUI_PROGRAMMER=24,GUI_COUPLER=25,GUI_PRESSURE_PLATE=27;
    public static final int GUI_WIRELESS_REDSTONE=23;
    public static final int GUI_TOKEN_SLOT=22;
    public static final int GUI_STORAGE_STATUS=21;
    public static final int GUI_TURNTABLE=19;
    public static final int GUI_MODULAR_GEAR=20;
    public static final int GUI_COCKPIT=18;
    public static final int GUI_MATTER_INFUSER=17;

    public static final int GUI_PILOT_MODES=16;
    public static final int GUI_AUTOPILOT=15;
    public static final int GUI_LARGE_CANOPY=14;
    public static final int GUI_PROGRAMMABLE_ARMOR = 13;
    public static final int GUI_REDSTONE_SCREEN=11,GUI_REDSTONE_SCREEN_SECONDARY=12;
    public static final int GUI_ANIMATED_SCREEN_SELECTOR = 0;
    public static final int GUI_RAMP_CONTROLLER = 1;
    public static final int GUI_REDSTONE_CHANNEL = 2;
    public static final int GUI_SPACE_DOOR = 3;
    public static final int GUI_PROGRAMMABLE_GLASS = 4;
    public static final int GUI_PROGRAMMABLE_LIGHT = 5;
    public static final int GUI_PROGRAMMABLE_CHAIR = 6;
    public static final int GUI_DUPLIFIER = 7;
    public static final int GUI_LANDING_GEAR = 8;
    public static final int GUI_PROGRAMMABLE_TRAPDOOR = 9;
    public static final int GUI_PROGRAMMABLE_STORAGE = 10;

    private static TileEntity doorTile(World world, BlockPos pos) {
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof com.vandorlabs.blocks.BlockVandorDoor
                && state.getValue(com.vandorlabs.blocks.BlockVandorDoor.HALF)
                == net.minecraft.block.BlockDoor.EnumDoorHalf.UPPER) pos = pos.down();
        return world.getTileEntity(pos);
    }

    private static TileEntity chairTile(World world, BlockPos pos) {
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        if ((state.getBlock() instanceof com.vandorlabs.blocks.BlockBridgeChair
                || state.getBlock() instanceof com.vandorlabs.blocks.BlockConnectedSeat) && state.getValue(com.vandorlabs.blocks.BlockBridgeChair.UPPER)) pos = pos.down();
        return world.getTileEntity(pos);
    }

    @Override
    public Object getServerGuiElement(int ID,EntityPlayer player,World world,int x,int y,int z){Object result=createServerGuiElement(ID,player,world,x,y,z);if(result instanceof net.minecraft.inventory.Container)com.vandorlabs.redstone.InlineTransmitters.attach((net.minecraft.inventory.Container)result,player);return result;}
    private Object createServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if(ID==GUI_TRANSMITTER_ITEM){com.vandorlabs.container.ContainerTransmitterItem c=new com.vandorlabs.container.ContainerTransmitterItem(player,x==0?net.minecraft.util.EnumHand.MAIN_HAND:net.minecraft.util.EnumHand.OFF_HAND);return c.canInteractWith(player)?c:null;}
        if(ID>=1000&&ID<2256){int selector=ID>=2000?ID-2000:ID-1000;if(selector<0||selector>255)return null;com.vandorlabs.redstone.RedstoneChannelMember m=com.vandorlabs.container.ContainerTransmitterSlot.resolve(world,x,y,z,selector);if(m==null)return null;net.minecraft.inventory.Container c=ID>=2000?new com.vandorlabs.container.ContainerTransmitterItem(player,m,selector):new com.vandorlabs.container.ContainerTransmitterSlot(player,m,selector);return c.canInteractWith(player)?c:null;}

        if(ID==GUI_COPY)return com.vandorlabs.copier.CopyControllerContainer.resolve(player,world,x,y,z);
        if(ID==GUI_SURFACE){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));if(!(tile instanceof com.vandorlabs.tiles.TileEntityTurntableSurface))return null;com.vandorlabs.container.ContainerSurface container=new com.vandorlabs.container.ContainerSurface((com.vandorlabs.tiles.TileEntityTurntableSurface)tile);return container.canInteractWith(player)?container:null;}
        if(ID==GUI_PROGRAMMER){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));return tile instanceof com.vandorlabs.programmer.ProgrammerTile&&((com.vandorlabs.programmer.ProgrammerTile)tile).usable(player)?new com.vandorlabs.programmer.ProgrammerContainer(player.inventory,(com.vandorlabs.programmer.ProgrammerTile)tile):null;}
        if(ID==GUI_PRESSURE_PLATE){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));if(!(tile instanceof com.vandorlabs.plate.PlateTile))return null;com.vandorlabs.plate.PlateContainer container=new com.vandorlabs.plate.PlateContainer((com.vandorlabs.plate.PlateTile)tile);return container.canInteractWith(player)?container:null;}
        if(ID==GUI_COUPLER)return com.vandorlabs.coupler.ContainerCoupler.resolve(player,world,x,y,z);
        if(ID==GUI_ANIMATED_SCREEN_SELECTOR&&world.getTileEntity(new BlockPos(x,y,z)) instanceof com.vandorlabs.tiles.TileEntityHexTank)ID=GUI_STORAGE_STATUS;
        if(ID==GUI_STORAGE_STATUS){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));return tile instanceof com.vandorlabs.tiles.TileEntityBattery||tile instanceof com.vandorlabs.tiles.TileEntityHexTank?new com.vandorlabs.container.ContainerStorageStatus(tile):null;}
        if(ID==GUI_WIRELESS_REDSTONE){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));return tile instanceof com.vandorlabs.tiles.TileEntityWirelessRedstone&&((com.vandorlabs.tiles.TileEntityWirelessRedstone)tile).usable(player)?new com.vandorlabs.container.ContainerWirelessRedstone((com.vandorlabs.tiles.TileEntityWirelessRedstone)tile):null;}
        if(ID==GUI_TOKEN_SLOT){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));if(!(tile instanceof com.vandorlabs.tiles.TileEntityTokenSlot)||!((com.vandorlabs.tiles.TileEntityTokenSlot)tile).isUsableByPlayer(player))return null;com.vandorlabs.container.ContainerTokenSlot container=new com.vandorlabs.container.ContainerTokenSlot(player.inventory,(com.vandorlabs.tiles.TileEntityTokenSlot)tile);return container;}
        if(ID==GUI_TURNTABLE)return com.vandorlabs.container.ContainerTurntable.resolve(player,world,x,y,z);
        if(ID==GUI_MODULAR_GEAR)return com.vandorlabs.container.ContainerModularGear.resolve(player,world,x,y,z);
        if(ID==GUI_COCKPIT)return com.vandorlabs.container.ContainerCockpit.resolve(player,world,x,y,z);
        if(ID==GUI_MATTER_INFUSER){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));return tile instanceof com.vandorlabs.tiles.TileEntityMatterInfuser?new com.vandorlabs.container.ContainerMatterInfuser(player.inventory,(com.vandorlabs.tiles.TileEntityMatterInfuser)tile):null;}
        if(ID==GUI_PILOT_MODES){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));if(tile instanceof com.vandorlabs.tiles.TileEntityVehicleController && com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,new BlockPos(x,y,z)))return new ContainerRedstoneChannel((com.vandorlabs.tiles.TileEntityPilotSeat)tile);return null;}
        if(ID==GUI_AUTOPILOT){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));if(tile instanceof com.vandorlabs.tiles.TileEntityAutopilot && com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,new BlockPos(x,y,z)))return new ContainerRedstoneChannel((com.vandorlabs.tiles.TileEntityAutopilot)tile);return null;}
        if(ID==GUI_LARGE_CANOPY){com.vandorlabs.tiles.TileEntityCanopy tile=com.vandorlabs.tiles.TileEntityCanopy.at(world,new BlockPos(x,y,z));if(tile!=null && tile.owner().configurable())return new com.vandorlabs.container.ContainerLargeCanopy(tile);return null;}
        if (ID == GUI_PROGRAMMABLE_ARMOR) {
            com.vandorlabs.container.ContainerProgrammableArmor armor = com.vandorlabs.container.ContainerProgrammableArmor.resolve(player,world,x,y,z);
            if (armor != null && armor.canInteractWith(player)) return armor;
            return null;
        }

        if((ID==GUI_REDSTONE_SCREEN || ID==GUI_REDSTONE_SCREEN_SECONDARY) && com.vandorlabs.items.ConfigurationAccess.canConfigure(player)){
            TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));
            int slot=ID==GUI_REDSTONE_SCREEN?0:1;
            if(tile instanceof TileEntityAnimatedScreenSelector && com.vandorlabs.blocks.RedstoneScreenInteractions.supportsSlot(tile.getBlockType(),slot))return new ContainerAnimatedScreenSelector(player.inventory,(TileEntityAnimatedScreenSelector)tile,slot);
        }
        if (ID == GUI_PROGRAMMABLE_STORAGE) {
            TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
            if (tile instanceof com.vandorlabs.tiles.TileEntityProgrammableStorage
                    && ((com.vandorlabs.tiles.TileEntityProgrammableStorage)tile).isUsableByPlayer(player))
                return new net.minecraft.inventory.ContainerChest(player.inventory, (com.vandorlabs.tiles.TileEntityProgrammableStorage)tile, player);
            return null;
        }
        if (ID == GUI_DUPLIFIER && player.getHeldItemMainhand().getItem()
                == com.vandorlabs.items.ModItems.DUPLIFIER)
            return new com.vandorlabs.container.ContainerDuplifier(player.inventory);
        if(ID==GUI_LANDING_GEAR && com.vandorlabs.items.ConfigurationAccess.canConfigure(player)) {
            TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));
            if(tile instanceof com.vandorlabs.tiles.TileEntityLandingGear && ((com.vandorlabs.tiles.TileEntityLandingGear)tile).isRoot())
                return new ContainerRedstoneChannel((com.vandorlabs.tiles.TileEntityLandingGear)tile);
        }
        if (ID == GUI_PROGRAMMABLE_CHAIR) {
            TileEntity tile = chairTile(world, new BlockPos(x, y, z));
            if (tile instanceof com.vandorlabs.tiles.TileEntityProgrammableChair)
                return new com.vandorlabs.container.ContainerProgrammableChair(
                        (com.vandorlabs.tiles.TileEntityProgrammableChair) tile);
        }
        if (ID == GUI_PROGRAMMABLE_LIGHT) {
            TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
            if (tile instanceof com.vandorlabs.tiles.TileEntityProgrammableLight)
                return new ContainerAnimatedScreenSelector(player.inventory,
                        (com.vandorlabs.tiles.TileEntityProgrammableLight) tile);
        }
        if (ID==GUI_PROGRAMMABLE_GLASS) {
            TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));
            if (tile instanceof com.vandorlabs.tiles.TileEntityProgrammableGlass)
                return new com.vandorlabs.container.ContainerProgrammableGlass((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile);
        }
        if (ID==GUI_PROGRAMMABLE_TRAPDOOR && com.vandorlabs.items.ConfigurationAccess.canConfigure(player)) {
            TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));
            if(tile instanceof com.vandorlabs.tiles.TileEntityProgrammableTrapdoor
                    && ((com.vandorlabs.tiles.TileEntityProgrammableTrapdoor)tile).usable(player))
                return new com.vandorlabs.container.ContainerProgrammableTrapdoor((com.vandorlabs.tiles.TileEntityProgrammableTrapdoor)tile);
        }
        if (ID==GUI_SPACE_DOOR) {
            TileEntity tile=doorTile(world,new BlockPos(x,y,z));
            if (tile instanceof com.vandorlabs.tiles.TileEntitySpaceDoor)
                return new com.vandorlabs.container.ContainerSpaceDoor((com.vandorlabs.tiles.TileEntitySpaceDoor)tile);
        }
        if (ID == GUI_REDSTONE_CHANNEL) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if(te instanceof com.vandorlabs.tiles.TileEntityPilotSeat){com.vandorlabs.vehicle.VehicleService.previewAssembly((net.minecraft.entity.player.EntityPlayerMP)player,new BlockPos(x,y,z));return null;}
            if (te instanceof RedstoneChannelMember)
                return new ContainerRedstoneChannel((RedstoneChannelMember) te);
        }
        if (ID == GUI_RAMP_CONTROLLER) {
            TileEntity te=world.getTileEntity(new BlockPos(x,y,z));
            if (te instanceof com.vandorlabs.tiles.TileEntityRampController && com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,new BlockPos(x,y,z)))
                return new com.vandorlabs.container.ContainerRampController((com.vandorlabs.tiles.TileEntityRampController)te);
        }
        if (ID == GUI_ANIMATED_SCREEN_SELECTOR) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof com.vandorlabs.tiles.TileEntityProgrammableStorage
                    && !com.vandorlabs.items.ConfigurationAccess.canConfigure(player)) return null;
            if (te instanceof TileEntityAnimatedScreenSelector) {
                return new ContainerAnimatedScreenSelector(player.inventory, (TileEntityAnimatedScreenSelector) te);
            }
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID,EntityPlayer player,World world,int x,int y,int z){Object result=createClientGuiElement(ID,player,world,x,y,z);if(result instanceof net.minecraft.client.gui.inventory.GuiContainer)com.vandorlabs.redstone.InlineTransmitters.attach(((net.minecraft.client.gui.inventory.GuiContainer)result).inventorySlots,player);return result;}
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    private Object createClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if(ID==GUI_TRANSMITTER_ITEM)return new com.vandorlabs.client.GuiTransmitterItem(new com.vandorlabs.container.ContainerTransmitterItem(player,x==0?net.minecraft.util.EnumHand.MAIN_HAND:net.minecraft.util.EnumHand.OFF_HAND));
        if(ID>=1000&&ID<2256){int selector=ID>=2000?ID-2000:ID-1000;if(selector<0||selector>255)return null;com.vandorlabs.redstone.RedstoneChannelMember m=com.vandorlabs.container.ContainerTransmitterSlot.resolve(world,x,y,z,selector);if(m==null)return null;return ID>=2000?new com.vandorlabs.client.GuiTransmitterItem(new com.vandorlabs.container.ContainerTransmitterItem(player,m,selector)):new com.vandorlabs.client.GuiTransmitterSlot(new com.vandorlabs.container.ContainerTransmitterSlot(player,m,selector));}

        if(ID==GUI_COPY){com.vandorlabs.copier.CopyControllerContainer container=com.vandorlabs.copier.CopyControllerContainer.resolve(player,world,x,y,z);return container==null?null:new com.vandorlabs.copier.client.GuiCopyController(container);}
        if(ID==GUI_SURFACE){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));return tile instanceof com.vandorlabs.tiles.TileEntityTurntableSurface?new com.vandorlabs.client.GuiProgrammableSurface(new com.vandorlabs.container.ContainerSurface((com.vandorlabs.tiles.TileEntityTurntableSurface)tile)):null;}
        if(ID==GUI_PROGRAMMER){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));return tile instanceof com.vandorlabs.programmer.ProgrammerTile?new com.vandorlabs.programmer.client.GuiProgrammer(new com.vandorlabs.programmer.ProgrammerContainer(player.inventory,(com.vandorlabs.programmer.ProgrammerTile)tile)):null;}
        if(ID==GUI_PRESSURE_PLATE){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));return tile instanceof com.vandorlabs.plate.PlateTile?new com.vandorlabs.client.GuiPressurePlate(new com.vandorlabs.plate.PlateContainer((com.vandorlabs.plate.PlateTile)tile)):null;}
        if(ID==GUI_COUPLER){com.vandorlabs.coupler.ContainerCoupler container=com.vandorlabs.coupler.ContainerCoupler.resolve(player,world,x,y,z);return container==null?null:new com.vandorlabs.client.GuiCoupler(container);}
        if(ID==GUI_ANIMATED_SCREEN_SELECTOR&&world.getTileEntity(new BlockPos(x,y,z)) instanceof com.vandorlabs.tiles.TileEntityHexTank)ID=GUI_STORAGE_STATUS;
        if(ID==GUI_STORAGE_STATUS){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));return tile instanceof com.vandorlabs.tiles.TileEntityBattery||tile instanceof com.vandorlabs.tiles.TileEntityHexTank?new com.vandorlabs.client.GuiStorageStatus(new com.vandorlabs.container.ContainerStorageStatus(tile)):null;}
        if(ID==GUI_WIRELESS_REDSTONE){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));return tile instanceof com.vandorlabs.tiles.TileEntityWirelessRedstone&&((com.vandorlabs.tiles.TileEntityWirelessRedstone)tile).usable(player)?new com.vandorlabs.client.GuiWirelessRedstone(new com.vandorlabs.container.ContainerWirelessRedstone((com.vandorlabs.tiles.TileEntityWirelessRedstone)tile)):null;}
        if(ID==GUI_TOKEN_SLOT){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));if(!(tile instanceof com.vandorlabs.tiles.TileEntityTokenSlot)||!((com.vandorlabs.tiles.TileEntityTokenSlot)tile).isUsableByPlayer(player))return null;com.vandorlabs.container.ContainerTokenSlot container=new com.vandorlabs.container.ContainerTokenSlot(player.inventory,(com.vandorlabs.tiles.TileEntityTokenSlot)tile);return tile instanceof com.vandorlabs.tiles.TileEntitySecuredDisplay?new com.vandorlabs.client.GuiNavigationTable(container):new com.vandorlabs.client.GuiTokenSlot(container);}
        if(ID==GUI_TURNTABLE){com.vandorlabs.container.ContainerTurntable container=com.vandorlabs.container.ContainerTurntable.resolve(player,world,x,y,z);return container==null?null:new com.vandorlabs.client.GuiTurntable(container);}
        if(ID==GUI_MODULAR_GEAR){com.vandorlabs.container.ContainerModularGear container=com.vandorlabs.container.ContainerModularGear.resolve(player,world,x,y,z);return container==null?null:container.track!=null?new com.vandorlabs.client.GuiTrack(container):new com.vandorlabs.client.GuiModularGear(container);}
        if(ID==GUI_COCKPIT){com.vandorlabs.container.ContainerCockpit container=com.vandorlabs.container.ContainerCockpit.resolve(player,world,x,y,z);return container==null?null:new com.vandorlabs.client.GuiCockpit(container);}
        if(ID==GUI_MATTER_INFUSER){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));return tile instanceof com.vandorlabs.tiles.TileEntityMatterInfuser?new com.vandorlabs.client.GuiMatterInfuser(player.inventory,(com.vandorlabs.tiles.TileEntityMatterInfuser)tile):null;}
        if(ID==GUI_PILOT_MODES){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));if(tile instanceof com.vandorlabs.tiles.TileEntityVehicleController && com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,new BlockPos(x,y,z)))return new com.vandorlabs.client.GuiPilotModes((com.vandorlabs.tiles.TileEntityPilotSeat)tile);return null;}
        if(ID==GUI_AUTOPILOT){TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));if(tile instanceof com.vandorlabs.tiles.TileEntityAutopilot && com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,new BlockPos(x,y,z)))return new com.vandorlabs.client.GuiAutopilot((com.vandorlabs.tiles.TileEntityAutopilot)tile);return null;}
        if(ID==GUI_LARGE_CANOPY){com.vandorlabs.tiles.TileEntityCanopy tile=com.vandorlabs.tiles.TileEntityCanopy.at(world,new BlockPos(x,y,z));if(tile!=null && tile.owner().configurable())return new com.vandorlabs.client.GuiLargeCanopy(tile);return null;}
        if (ID == GUI_PROGRAMMABLE_ARMOR) {
            com.vandorlabs.container.ContainerProgrammableArmor armor = com.vandorlabs.container.ContainerProgrammableArmor.resolve(player,world,x,y,z);
            if (armor != null && armor.canInteractWith(player)) return new com.vandorlabs.client.GuiProgrammableArmor(armor);
            return null;
        }

        if((ID==GUI_REDSTONE_SCREEN || ID==GUI_REDSTONE_SCREEN_SECONDARY) && com.vandorlabs.items.ConfigurationAccess.canConfigure(player)){
            TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));
            int slot=ID==GUI_REDSTONE_SCREEN?0:1;
            if(tile instanceof TileEntityAnimatedScreenSelector && com.vandorlabs.blocks.RedstoneScreenInteractions.supportsSlot(tile.getBlockType(),slot))return new com.vandorlabs.client.GuiRedstoneScreen(player.inventory,((TileEntityAnimatedScreenSelector)tile).redstoneScreen(slot));
        }
        if (ID == GUI_PROGRAMMABLE_STORAGE) {
            TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
            if (tile instanceof com.vandorlabs.tiles.TileEntityProgrammableStorage
                    && ((com.vandorlabs.tiles.TileEntityProgrammableStorage)tile).isUsableByPlayer(player))
                return new net.minecraft.client.gui.inventory.GuiChest(player.inventory, (com.vandorlabs.tiles.TileEntityProgrammableStorage)tile);
            return null;
        }
        if (ID == GUI_DUPLIFIER && player.getHeldItemMainhand().getItem()
                == com.vandorlabs.items.ModItems.DUPLIFIER)
            return new com.vandorlabs.client.GuiDuplifier(player.inventory);
        if(ID==GUI_LANDING_GEAR && com.vandorlabs.items.ConfigurationAccess.canConfigure(player)) {
            TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));
            if(tile instanceof com.vandorlabs.tiles.TileEntityLandingGear && ((com.vandorlabs.tiles.TileEntityLandingGear)tile).isRoot())
                return new com.vandorlabs.client.GuiLandingGear((com.vandorlabs.tiles.TileEntityLandingGear)tile);
        }
        if (ID == GUI_PROGRAMMABLE_CHAIR) {
            TileEntity tile = chairTile(world, new BlockPos(x, y, z));
            if (tile instanceof com.vandorlabs.tiles.TileEntityConnectedSeat)
                return new com.vandorlabs.client.GuiConnectedSeat((com.vandorlabs.tiles.TileEntityConnectedSeat)tile);
            if (tile instanceof com.vandorlabs.tiles.TileEntityProgrammableChair)
                return new com.vandorlabs.client.GuiProgrammableChair(
                        (com.vandorlabs.tiles.TileEntityProgrammableChair) tile);
        }
        if (ID == GUI_PROGRAMMABLE_LIGHT) {
            TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
            if (tile instanceof com.vandorlabs.tiles.TileEntityProgrammableLight)
                return new com.vandorlabs.client.GuiProgrammableLight(player.inventory,
                        (com.vandorlabs.tiles.TileEntityProgrammableLight) tile);
        }
        if (ID==GUI_PROGRAMMABLE_GLASS) {
            TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));
            if (tile instanceof com.vandorlabs.tiles.TileEntityProgrammableGlass)
                return new com.vandorlabs.client.GuiProgrammableGlass((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile);
        }
        if (ID==GUI_PROGRAMMABLE_TRAPDOOR && com.vandorlabs.items.ConfigurationAccess.canConfigure(player)) {
            TileEntity tile=world.getTileEntity(new BlockPos(x,y,z));
            if(tile instanceof com.vandorlabs.tiles.TileEntityProgrammableTrapdoor
                    && ((com.vandorlabs.tiles.TileEntityProgrammableTrapdoor)tile).usable(player))
                return new com.vandorlabs.client.GuiProgrammableTrapdoor((com.vandorlabs.tiles.TileEntityProgrammableTrapdoor)tile);
        }
        if (ID==GUI_SPACE_DOOR) {
            TileEntity tile=doorTile(world,new BlockPos(x,y,z));
            if (tile instanceof com.vandorlabs.tiles.TileEntitySpaceDoor)
                return new com.vandorlabs.client.GuiSpaceDoor((com.vandorlabs.tiles.TileEntitySpaceDoor)tile);
        }
        if (ID == GUI_REDSTONE_CHANNEL) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if(te instanceof com.vandorlabs.tiles.TileEntityPilotSeat)return null;
            if (te instanceof RedstoneChannelMember)
                return new GuiRedstoneChannel((RedstoneChannelMember) te);
        }
        if (ID == GUI_RAMP_CONTROLLER) {
            TileEntity te=world.getTileEntity(new BlockPos(x,y,z));
            if (te instanceof com.vandorlabs.tiles.TileEntityRampController && com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,new BlockPos(x,y,z)))
                return new com.vandorlabs.client.GuiRampController((com.vandorlabs.tiles.TileEntityRampController)te);
        }
        if (ID == GUI_ANIMATED_SCREEN_SELECTOR) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof com.vandorlabs.tiles.TileEntityProgrammableStorage
                    && !com.vandorlabs.items.ConfigurationAccess.canConfigure(player)) return null;
            if (te instanceof TileEntityAnimatedScreenSelector) {
                if (world.getBlockState(new BlockPos(x, y, z)).getBlock()
                        instanceof BlockProgrammableTrigger)
                    return new com.vandorlabs.client.GuiProgrammableTrigger(player.inventory,
                            (com.vandorlabs.tiles.TileEntityProgrammableTrigger) te);
                if (world.getBlockState(new BlockPos(x, y, z)).getBlock()
                        instanceof BlockProgrammableWall || world.getBlockState(
                        new BlockPos(x, y, z)).getBlock() instanceof BlockProgrammableBlock
                        || world.getBlockState(new BlockPos(x, y, z)).getBlock()
                        instanceof BlockProgrammableSlab || world.getBlockState(new BlockPos(x,y,z)).getBlock()
                        instanceof com.vandorlabs.blocks.BlockProgrammableStairs || te instanceof com.vandorlabs.tiles.TileEntityHexTank) {
                    return new com.vandorlabs.client.GuiProgrammableWall(player.inventory,
                            (TileEntityAnimatedScreenSelector) te);
                }
                if (world.getBlockState(new BlockPos(x, y, z)).getBlock()
                        instanceof BlockProgrammableFullInput) {
                    return new GuiAnimatedScreenSelector(player.inventory,
                            (TileEntityAnimatedScreenSelector) te);
                }
                if (world.getBlockState(new BlockPos(x, y, z)).getBlock()
                        instanceof BlockProgrammableInput
                        || world.getBlockState(new BlockPos(x,y,z)).getBlock() instanceof com.vandorlabs.blocks.BlockDiagonalHalfConsole) {
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
