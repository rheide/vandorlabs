package com.vandorlabs.neoforge.programmer;

import com.vandorlabs.programmer.ProgrammerPolicy;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.neoforge.screen.NativeChipJournal;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/** Pure stack transformations; the future host must transfer its existing slots atomically. */
public final class NativeProgrammerRules {
 private NativeProgrammerRules(){}
 public static int kind(ItemStack stack){return stack.isEmpty()?ProgrammerPolicy.EMPTY:stack.is(NativeCredentialItems.ACCESS_CARD.get())?ProgrammerPolicy.CARD:stack.is(NativeCredentialItems.DATA_CHIP.get())?ProgrammerPolicy.CHIP:ProgrammerPolicy.OTHER;}
 public static int company(ItemStack master){return ProgrammerPolicy.company(kind(master),NativeCredentialItems.company(master),NativeChipJournal.master(master));}
 public static boolean validSlot(int slot,ItemStack stack){return !NativeChipJournal.vehicle(stack)&&ProgrammerPolicy.validSlot(slot,kind(stack),NativeCredentialItems.company(stack),NativeChipJournal.master(stack));}
 public static String problem(ItemStack master,ItemStack input,ItemStack output,NativeProgrammerDraft draft){if(!draft.available()||NativeChipJournal.vehicle(master)||NativeChipJournal.vehicle(input)||NativeChipJournal.vehicle(output))return "Vehicle chip programming is unavailable";return ProgrammerPolicy.problem(kind(input),NativeCredentialItems.company(input),input.getCount(),!output.isEmpty(),!master.isEmpty(),company(master),draft.journal());}
 public static String copyProblem(ItemStack source,ItemStack target){if(NativeChipJournal.vehicle(source)||NativeChipJournal.vehicle(target))return "Vehicle chip copying is unavailable";return ProgrammerPolicy.copyProblem(kind(source),NativeCredentialItems.company(source),source.getCount(),kind(target),NativeCredentialItems.company(target),target.getCount());}
 public static ItemStack programmedCopy(ItemStack master,ItemStack input,NativeProgrammerDraft draft){String problem=problem(master,input,ItemStack.EMPTY,draft);if(!problem.isEmpty())throw new IllegalArgumentException(problem);var result=NativeCredentialItems.ownedCopy(input);result.setCount(1);int company=company(master);if(company>0)result.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(company));if(kind(result)==ProgrammerPolicy.CHIP)NativeChipJournal.write(result,draft.journal(),draft.title(),draft.entries());return result;}
 public static ItemStack committedCopy(ItemStack source,ItemStack target){String problem=copyProblem(source,target);if(!problem.isEmpty())throw new IllegalArgumentException(problem);var result=NativeCredentialItems.ownedCopy(source);result.setCount(1);return result;}
}
