package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Captures actual root/mate identities supplied by the owning door lifecycle. No slots or registry activation. */
public final class NativeDoorMenu extends AbstractContainerMenu {
    public final NativeProgrammableDoorEntity tile;
    private final NativeProgrammableDoorEntity mate;
    private final NativeDoorLinks links;
    public NativeDoorMenu(MenuType<?> type,int id,NativeProgrammableDoorEntity tile){super(type,id);this.tile=java.util.Objects.requireNonNull(tile);if(!(tile.getBlockState().getBlock() instanceof NativeDoorLinks access))throw new IllegalArgumentException("Door link lifecycle unavailable");links=access;if(!links.configurationLinksLoaded(tile))throw new IllegalArgumentException("Incomplete loaded door links");mate=links.configurationMate(tile);if(mate==tile||mate!=null&&(mate.getLevel()!=tile.getLevel()||mate.large()!=tile.large()))throw new IllegalArgumentException("Invalid linked door editor");}
    @Override public boolean stillValid(Player player){return (!(tile.getLevel() instanceof net.minecraft.server.level.ServerLevel server)||com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.open(server))&&links.configurationAuthorized(player,tile)&&tile.getBlockState().getBlock()==links&&links.configurationLinksLoaded(tile)&&(mate==null||NativeConfigurationAccess.canEditDialog(player,mate))&&links.configurationMate(tile)==mate;}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    public NativeDoorMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos){this(type,id,requireRoot(inventory.player,pos));}
    private static NativeProgrammableDoorEntity requireRoot(Player player,BlockPos pos){var root=DoorConfiguration.root(player.level(),pos);if(root==null)throw new IllegalArgumentException("Missing complete door");return root;}
    public static NativeDoorMenu editing(ServerPlayer player,BlockPos pos,int window){return player.getServer().isSameThread()&&NativeConfigurationAccess.canConfigure(player)&&player.containerMenu instanceof NativeDoorMenu menu&&menu.containerId==window&&menu.tile.getBlockPos().equals(pos)&&menu.stillValid(player)?menu:null;}
    public static boolean applyJoin(ServerPlayer player,BlockPos pos,int window,boolean enabled){
        var menu=editing(player,pos,window);
        return menu!=null&&menu.tile instanceof NativeLargeDoorEntity large&&NativeLargeDoorJoins.change(player,large,enabled);
    }
    public static boolean applyFrames(ServerPlayer player,BlockPos pos,int window,com.vandorlabs.persistence.FrameSettings frames){var menu=editing(player,pos,window);if(menu==null||!NativeProgrammableDoorEntity.validFrames(frames))return false;var targets=targets(player,menu);var staged=new java.util.ArrayList<NativeProgrammableDoorEntity>();for(var door:targets)if(door.stageFrames(frames))staged.add(door);for(var door:staged)door.publish();return true;}
    public static boolean applyGlass(ServerPlayer player,BlockPos pos,int window,NativeDoorGlass glass){var menu=editing(player,pos,window);if(menu==null||glass==null||!(com.vandorlabs.ui.SpaceDoorDialog.glassPage(menu.tile.settings().door().design)||menu.tile.settings().faceTexture()>=0&&com.vandorlabs.neoforge.ModernFinishCatalog.option(menu.tile.settings().faceTexture()).id().equals("utility_door_reactor")))return false;var targets=targets(player,menu);var staged=new java.util.ArrayList<NativeProgrammableDoorEntity>();for(var door:targets)if(door.stageGlass(glass))staged.add(door);for(var door:staged)door.publish();return true;}
    /**
     * The edited door, its paired mate, and every other member of a joined Large Door group the
     * player may edit: a joined group shares one configuration, so an edit to one member keeps
     * the group together instead of leaving that member incompatible with the rest.
     */
    private static java.util.List<NativeProgrammableDoorEntity> targets(ServerPlayer player,NativeDoorMenu menu){
        var targets=new java.util.LinkedHashSet<NativeProgrammableDoorEntity>();targets.add(menu.tile);if(menu.mate!=null)targets.add(menu.mate);
        if(menu.tile instanceof NativeLargeDoorEntity large)for(var member:NativeLargeDoorJoins.group(large).members)
            if(member!=large&&NativeLargeDoorLifecycle.permitted(player.level(),member,player))targets.add(member);
        return java.util.List.copyOf(targets);
    }
    public static boolean apply(ServerPlayer player,BlockPos pos,int window,NativeDoorEdit edit,ChannelList channels){
        NativeDoorMenu menu=editing(player,pos,window);if(menu==null||channels==null)return false;
        var targets=targets(player,menu);for(var door:targets)if(!door.accepts(edit))return false;
        // Every setting is installed before any observer is notified. No inventory/world ownership transfer occurs here.
        var staged=new java.util.ArrayList<NativeProgrammableDoorEntity>();for(var door:targets)if(door.stage(edit,channels))staged.add(door);
        for(var door:staged)door.publish();return true;
    }
}
