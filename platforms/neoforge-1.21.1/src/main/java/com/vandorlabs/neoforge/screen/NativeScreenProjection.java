package com.vandorlabs.neoforge.screen;
import net.minecraft.nbt.*;
/** Bounded public physical power sample; never used as saved server authority. */
public final class NativeScreenProjection {
 private NativeScreenProjection(){}
 public static int power(CompoundTag tag){int value=tag.getInt("NativeScreenPower");return tag.contains("NativeScreenPower",Tag.TAG_INT)&&value>=0&&value<=15?value:0;}
}
