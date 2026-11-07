package com.vandorlabs.neoforge.screen;

import com.vandorlabs.programmer.JournalText;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Prepared display scalars shared by standalone and embedded readers. Never retains the physical item. */
public final class NativeChipDisplay {
 public final JournalText journal;
 public final boolean master;
 /** Null delegates stock naming to the consuming client's locale. */
 public final String customName;
 private final CompoundTag text;
 private NativeChipDisplay(ItemStack token){text=NativeChipJournal.textData(token);journal=NativeChipJournal.viewText(text);master=NativeChipJournal.master(token);var name=token.get(DataComponents.CUSTOM_NAME);customName=name==null?null:JournalText.bounded(name.getString(),NativeScreenChipView.NAME_LIMIT);}
 public static NativeChipDisplay prepare(ItemStack token){return new NativeChipDisplay(token);}
 public CompoundTag text(){return text==null?null:text.copy();}
}
