package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Dedicated editor session. The owning runtime must supply its actual command and immutable status. */
public final class NativeRampMenu extends AbstractContainerMenu {
 public record Status(String text,boolean error,boolean pending,boolean moving,long revision){public Status{java.util.Objects.requireNonNull(text);if(text.length()>256||revision<0)throw new IllegalArgumentException("Ramp status bounds");}}
 public record Outcome(NativeRampSettings choices,Status status,boolean admitted,boolean choicesCommitted,boolean actionPending,boolean activationRefused){public Outcome{java.util.Objects.requireNonNull(choices);java.util.Objects.requireNonNull(status);}}
 @FunctionalInterface public interface Commands {Outcome configure(ServerPlayer player,NativeRampSettings choices);}
 public final NativeRampEntity tile;public final Direction scanFacing,horizontalFacing;
 private final Commands commands;private final Supplier<Status> status;
 public NativeRampMenu(MenuType<?> type,int id,NativeRampEntity tile,Direction scanFacing,Direction horizontalFacing,Commands commands,Supplier<Status> status){super(type,id);this.tile=java.util.Objects.requireNonNull(tile);this.scanFacing=java.util.Objects.requireNonNull(scanFacing);this.horizontalFacing=java.util.Objects.requireNonNull(horizontalFacing);if(!horizontalFacing.getAxis().isHorizontal())throw new IllegalArgumentException("Ramp travel fallback");this.commands=commands;this.status=java.util.Objects.requireNonNull(status);}
 public Status status(){return java.util.Objects.requireNonNull(status.get());}
 public NativeRampDraft draft(){return new NativeRampDraft(tile.settings(),scanFacing,horizontalFacing);}
 @Override public boolean stillValid(Player player){return !tile.inert()&&(!(tile.getLevel() instanceof ServerLevel server)||server.getServer().isSameThread()&&NativeRedstoneChannels.open(server))&&NativeConfigurationAccess.canEditDialog(player,tile);}
 @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
 /** Null rejects an unbound or unauthorized session; it never substitutes a successful runtime receipt. */
 public Outcome apply(ServerPlayer player,NativeRampEdit edit){if(!player.getServer().isSameThread()||commands==null||player.containerMenu!=this||containerId!=edit.window()||!tile.getBlockPos().equals(edit.pos())||!stillValid(player))return null;return java.util.Objects.requireNonNull(commands.configure(player,edit.settings()));}
}
