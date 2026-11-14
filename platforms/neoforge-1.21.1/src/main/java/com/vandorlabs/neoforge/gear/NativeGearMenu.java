package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** A selected installed member is the session identity; its controller may change after joining. */
public final class NativeGearMenu extends AbstractContainerMenu {
    public NativeGearEntity tile;
    private final BlockPos selected;private final java.util.UUID identity;
    public NativeGearMenu(MenuType<?> type,int id,NativeGearEntity member){super(type,id);selected=member.getBlockPos().immutable();identity=member.identity();tile=java.util.Objects.requireNonNull(member.controllerEntity());}
    public NativeGearMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos){this(type,id,require(inventory.player,pos));}
    public NativeGearMenu(MenuType<?> type,int id,Inventory inventory,BlockPos pos,java.util.UUID identity){this(type,id,inventory,pos);if(!this.identity.equals(identity))throw new IllegalArgumentException("Arm session member replaced");}
    private static NativeGearEntity require(Player player,BlockPos pos){var member=NativeGearFormation.at(player.level(),pos);if(member==null||member.controllerEntity()==null)throw new IllegalArgumentException("Missing arm member");return member;}
    public BlockPos selectedPosition(){return selected;}
    public java.util.UUID selectedIdentity(){return identity;}
    public boolean resolve(Player player){return resolve(player,false);}
    private boolean resolve(Player player,boolean keepSession){var member=NativeGearFormation.at(player.level(),selected);if(member==null||!identity.equals(member.identity())||member.inert()||!player.level().mayInteract(player,selected)||!player.mayUseItemAt(selected,net.minecraft.core.Direction.UP,player.getMainHandItem()))return false;var root=member.controllerEntity();if(root==null||!(keepSession?NativeGearAccess.session(player,root):NativeGearAccess.menu(player,root)))return false;tile=root;return true;}
    /** A result may precede the chunk update; keep the pending reply until its exact root arrives. */
    public boolean rebind(Player player,BlockPos controller,java.util.UUID controllerIdentity){var root=NativeGearFormation.at(player.level(),controller);if(root==null||!root.identity().equals(controllerIdentity)||!root.controller())return false;var member=NativeGearFormation.at(player.level(),selected);if(member==null||!identity.equals(member.identity())||member.controllerEntity()!=root)return false;tile=root;return true;}
    @Override public boolean stillValid(Player player){return resolve(player,true);}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    public boolean apply(ServerPlayer actor,int window,BlockPos pos,NativeGearSettings settings,boolean target){if(!actor.getServer().isSameThread()||actor.containerMenu!=this||containerId!=window||!selected.equals(pos)||!NativeConfigurationAccess.canConfigure(actor)||!resolve(actor))return false;tile.authorize(actor);return tile.configure(settings,target);}
}
