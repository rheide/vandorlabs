package com.vandorlabs.neoforge.mixin;
import com.vandorlabs.neoforge.fluid.NativeBucketContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
/** The NeoForge ItemStack-sensitive overload is used by vanilla buckets and dispensers. */
@Mixin(BucketItem.class)
public abstract class ContainedFluidBucketMixin {
 @Shadow @Final public net.minecraft.world.level.material.Fluid content;
 @WrapMethod(method="emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z")
 private boolean vandorlabs$explicitBucket(Player player,Level level,BlockPos pos,BlockHitResult hit,ItemStack stack,Operation<Boolean> original){var incoming=stack==null?new net.neoforged.neoforge.fluids.FluidStack(content,1000):net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(stack).orElseGet(()->new net.neoforged.neoforge.fluids.FluidStack(content,1000));return NativeBucketContext.with(level,pos,incoming,()->{Boolean allowed=com.vandorlabs.neoforge.fluid.NativeFluidWorld.bucketTarget(level,pos,incoming);return allowed!=null&&!allowed?false:original.call(player,level,pos,hit,stack);});}
}
