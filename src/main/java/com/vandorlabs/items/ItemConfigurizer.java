package com.vandorlabs.items;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockRampController;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityProgrammableChair;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opens the existing configuration menus without the creative-mode gate. */
@Mod.EventBusSubscriber(modid = VandorLabs.MODID)
public final class ItemConfigurizer extends Item {
    public ItemConfigurizer() {
        setRegistryName(VandorLabs.MODID, "configurizer");
        setUnlocalizedName("vandorlabs.configurizer");
        setCreativeTab(VandorLabs.VANDOR_LABS_TAB);
        setMaxStackSize(1);
    }

    @Override public net.minecraft.util.ActionResult<net.minecraft.item.ItemStack> onItemRightClick(World world,EntityPlayer player,EnumHand hand) {
        if(hand==EnumHand.MAIN_HAND&&MovingDeviceConfiguration.openTurntable(player,MovingDeviceConfiguration.pointedTurntable(player)))return new net.minecraft.util.ActionResult<>(net.minecraft.util.EnumActionResult.SUCCESS,player.getHeldItem(hand));
        com.vandorlabs.vehicle.EntityGroundVehicle craft=player.getRidingEntity() instanceof com.vandorlabs.vehicle.EntityGroundVehicle?(com.vandorlabs.vehicle.EntityGroundVehicle)player.getRidingEntity():com.vandorlabs.vehicle.VehicleLookup.pointed(player);
        if(hand==EnumHand.MAIN_HAND && craft!=null) {
            if(!world.isRemote)configureCraft((net.minecraft.entity.player.EntityPlayerMP)player,craft);
            return new net.minecraft.util.ActionResult<>(net.minecraft.util.EnumActionResult.SUCCESS,player.getHeldItem(hand));
        }
        return super.onItemRightClick(world,player,hand);
    }
    private static void configureCraft(net.minecraft.entity.player.EntityPlayerMP player,com.vandorlabs.vehicle.EntityGroundVehicle craft){
        com.vandorlabs.vehicle.VehicleInteraction.Target target=com.vandorlabs.vehicle.VehicleInteraction.pointed(player);
        if(target!=null && target.craft==craft && target.hit.action==com.vandorlabs.vehicle.VehicleSelection.Action.COCKPIT){com.vandorlabs.tiles.TileEntityCockpit cockpit=com.vandorlabs.cockpit.CockpitAssembly.root(craft.view,target.hit.cell.pos);if(cockpit!=null)com.vandorlabs.container.ContainerCockpit.open(player,cockpit);return;}
        if(target!=null && target.craft==craft && target.hit.action==com.vandorlabs.vehicle.VehicleSelection.Action.BOARD){
            BlockPos seat=target.hit.cell.state.getValue(com.vandorlabs.blocks.BlockPilotSeat.UPPER)?target.hit.cell.pos.down():target.hit.cell.pos;com.vandorlabs.vehicle.VehicleService.previewSeat(player,craft,seat);
        }else if(target!=null && target.craft==craft && target.hit.action==com.vandorlabs.vehicle.VehicleSelection.Action.CONTROLLER)com.vandorlabs.vehicle.VehicleService.previewController(player,craft,target.hit.cell.pos);
        else com.vandorlabs.vehicle.VehicleService.previewParking(player,craft);
    }
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGH)
    public static void onMovingEntity(PlayerInteractEvent.EntityInteract event){configureMovingEntity(event);}
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGH)
    public static void onMovingEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event){configureMovingEntity(event);}
    private static void configureMovingEntity(PlayerInteractEvent event){
        if(event.getHand()!=EnumHand.MAIN_HAND||event.getItemStack().getItem()!=ModItems.CONFIGURIZER)return;
        net.minecraft.entity.Entity target=event instanceof PlayerInteractEvent.EntityInteract?((PlayerInteractEvent.EntityInteract)event).getTarget():((PlayerInteractEvent.EntityInteractSpecific)event).getTarget();
        if(target instanceof com.vandorlabs.vehicle.EntityGroundVehicle){
            com.vandorlabs.vehicle.EntityGroundVehicle craft=(com.vandorlabs.vehicle.EntityGroundVehicle)target;
            if(event.getEntityPlayer().getRidingEntity()!=craft&&com.vandorlabs.vehicle.VehicleLookup.pointed(event.getEntityPlayer())!=craft)return;
            event.setCanceled(true);event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);if(!event.getWorld().isRemote)configureCraft((net.minecraft.entity.player.EntityPlayerMP)event.getEntityPlayer(),craft);
        }else if(target instanceof com.vandorlabs.turntable.EntityTurntable&&MovingDeviceConfiguration.openTurntable(event.getEntityPlayer(),MovingDeviceConfiguration.pointedTurntable(event.getEntityPlayer()))){event.setCanceled(true);event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);}
    }
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGH)
    public static void onArmorStand(PlayerInteractEvent.EntityInteractSpecific event) {
        if(event.getHand()!=EnumHand.MAIN_HAND || event.getItemStack().getItem()!=ModItems.CONFIGURIZER
                || !(event.getTarget() instanceof net.minecraft.entity.item.EntityArmorStand))return;
        net.minecraft.entity.item.EntityArmorStand stand=(net.minecraft.entity.item.EntityArmorStand)event.getTarget();
        if(stand.hasMarker() || event.getEntityPlayer().isSpectator())return;
        event.setCanceled(true);event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
        net.minecraft.inventory.EntityEquipmentSlot slot=armorSlot(stand,event.getLocalPos().y);
        if(slot==null || !(stand.getItemStackFromSlot(slot).getItem() instanceof ItemProgrammableArmor))return;
        if(!event.getWorld().isRemote)event.getEntityPlayer().openGui(VandorLabs.instance,
                GuiHandler.GUI_PROGRAMMABLE_ARMOR,event.getWorld(),stand.getEntityId(),slot.getIndex(),1);
    }
    /** Match vanilla armor-stand click regions, including its small stand scale. */
    public static net.minecraft.inventory.EntityEquipmentSlot armorSlot(net.minecraft.entity.item.EntityArmorStand stand,double y) {
        boolean small=stand.isSmall();double height=small?y*2:y;
        net.minecraft.inventory.EntityEquipmentSlot slot;
        if(height>=.1 && height<.1+(small?.8:.45) && stand.hasItemInSlot(net.minecraft.inventory.EntityEquipmentSlot.FEET))slot=net.minecraft.inventory.EntityEquipmentSlot.FEET;
        else if(height>=.9+(small?.3:0) && height<.9+(small?1:.7) && stand.hasItemInSlot(net.minecraft.inventory.EntityEquipmentSlot.CHEST))slot=net.minecraft.inventory.EntityEquipmentSlot.CHEST;
        else if(height>=.4 && height<.4+(small?1:.8) && stand.hasItemInSlot(net.minecraft.inventory.EntityEquipmentSlot.LEGS))slot=net.minecraft.inventory.EntityEquipmentSlot.LEGS;
        else if(height>=1.6 && stand.hasItemInSlot(net.minecraft.inventory.EntityEquipmentSlot.HEAD))slot=net.minecraft.inventory.EntityEquipmentSlot.HEAD;
        else return null;
        return slot;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != EnumHand.MAIN_HAND
                || event.getItemStack().getItem() != ModItems.CONFIGURIZER) return;
        World world = event.getWorld();
        if(MovingDeviceConfiguration.openTurntable(event.getEntityPlayer(),MovingDeviceConfiguration.pointedTurntable(event.getEntityPlayer()))){event.setCanceled(true);event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);return;}
        com.vandorlabs.vehicle.EntityGroundVehicle craft=event.getEntityPlayer().getRidingEntity() instanceof com.vandorlabs.vehicle.EntityGroundVehicle?(com.vandorlabs.vehicle.EntityGroundVehicle)event.getEntityPlayer().getRidingEntity():com.vandorlabs.vehicle.VehicleLookup.pointed(event.getEntityPlayer());
        if(craft!=null){event.setCanceled(true);event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);if(!world.isRemote)configureCraft((net.minecraft.entity.player.EntityPlayerMP)event.getEntityPlayer(),craft);return;}
        BlockPos pos = ProgrammableTarget.settingsPos(world, event.getPos());
        IBlockState state = world.getBlockState(pos);
        com.vandorlabs.tiles.TileEntityCockpit cockpit=com.vandorlabs.cockpit.CockpitAssembly.root(world,pos);
        if(cockpit!=null){event.setCanceled(true);event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);if(!world.isRemote)com.vandorlabs.container.ContainerCockpit.open(event.getEntityPlayer(),cockpit);return;}
        if (state.getBlock() instanceof com.vandorlabs.blocks.BlockPilotSeat) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
            if(event.getEntityPlayer().isSneaking()) {
                if(state.getValue(com.vandorlabs.blocks.BlockPilotSeat.UPPER))pos=pos.down();
                state.getBlock().onBlockActivated(world,pos,world.getBlockState(pos),event.getEntityPlayer(),EnumHand.MAIN_HAND,event.getFace(),.5F,.5F,.5F);return;
            }
            if(!world.isRemote)com.vandorlabs.vehicle.VehicleService.previewAssembly((net.minecraft.entity.player.EntityPlayerMP)event.getEntityPlayer(),pos);
            return;
        }
        if(state.getBlock() instanceof com.vandorlabs.blocks.BlockVehicleController){event.setCanceled(true);event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);if(!world.isRemote)com.vandorlabs.vehicle.VehicleService.previewController((net.minecraft.entity.player.EntityPlayerMP)event.getEntityPlayer(),pos);return;}
        TileEntity tile = world.getTileEntity(pos);
        int gui;
        if(state.getBlock() instanceof com.vandorlabs.blocks.BlockNonWheeledLandingGear){pos=((com.vandorlabs.blocks.BlockNonWheeledLandingGear)state.getBlock()).connectedRoot(world,pos);tile=world.getTileEntity(pos);}
        if(tile instanceof com.vandorlabs.tiles.TileEntityCanopy && ((com.vandorlabs.tiles.TileEntityCanopy)tile).owner().configurable()){tile=((com.vandorlabs.tiles.TileEntityCanopy)tile).owner();pos=tile.getPos();gui=GuiHandler.GUI_LARGE_CANOPY;}
        else if(tile instanceof com.vandorlabs.tiles.TileEntityWirelessRedstone)gui=GuiHandler.GUI_WIRELESS_REDSTONE;
        else if(tile instanceof com.vandorlabs.tiles.TileEntityTokenSlot) gui=GuiHandler.GUI_TOKEN_SLOT;
        else if(tile instanceof com.vandorlabs.tiles.TileEntityTurntableController) gui=GuiHandler.GUI_TURNTABLE;
        else if(tile instanceof com.vandorlabs.gear.GearTileEntity){com.vandorlabs.gear.GearTileEntity root=((com.vandorlabs.gear.GearTileEntity)tile).loadedController();if(root==null)return;gui=GuiHandler.GUI_MODULAR_GEAR;}
        else if (tile instanceof com.vandorlabs.tiles.TileEntityProgrammableTrapdoor) gui = GuiHandler.GUI_PROGRAMMABLE_TRAPDOOR;
        else if (tile instanceof TileEntitySpaceDoor) gui = GuiHandler.GUI_SPACE_DOOR;
        else if (tile instanceof TileEntityProgrammableGlass) gui = GuiHandler.GUI_PROGRAMMABLE_GLASS;
        else if (tile instanceof TileEntityProgrammableLight) gui = GuiHandler.GUI_PROGRAMMABLE_LIGHT;
        else if (tile instanceof com.vandorlabs.tiles.TileEntityLandingGear) gui = GuiHandler.GUI_LANDING_GEAR;
        else if (tile instanceof TileEntityProgrammableChair) gui = GuiHandler.GUI_PROGRAMMABLE_CHAIR;
        else if (state.getBlock() instanceof BlockRampController) gui = GuiHandler.GUI_RAMP_CONTROLLER;
        else if (tile instanceof com.vandorlabs.tiles.TileEntityAutopilot) gui = GuiHandler.GUI_AUTOPILOT;
        else if (tile instanceof com.vandorlabs.tiles.TileEntityHexTank) gui=GuiHandler.GUI_STORAGE_STATUS;
        else if (tile instanceof TileEntityAnimatedScreenSelector)
            gui = GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR;
        else if (tile instanceof RedstoneChannelMember) gui = GuiHandler.GUI_REDSTONE_CHANNEL;
        else return;
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
        if (!world.isRemote) {
            EntityPlayer player = event.getEntityPlayer();
            player.openGui(VandorLabs.instance, gui, world,
                    pos.getX(), pos.getY(), pos.getZ());
        }
    }
}
