package com.vandorlabs.neoforge.seating;
import com.vandorlabs.neoforge.canopy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
/** Explicit Pilot upper reservation sharing. Opaque/component-bearing proxies are never replaced. */
public final class NativeSeatBackrests {
    public static boolean supports(String kind){return kind.equals("regular_canopy_glass")||kind.equals("canopy")||kind.equals("long_canopy");}
    private static boolean proxy(Level world,BlockPos pos){if(!NativeSeatBindings.enabled()||!NativeSeatBlock.loaded(world,pos)||!NativeSeatBlock.loaded(world,pos.below()))return false;var state=world.getBlockState(pos);return state.getBlock() instanceof NativeSeatBlock block&&block.kind==NativeSeatKind.PILOT&&state.getValue(NativeSeatBlock.UPPER)&&world.getBlockEntity(pos) instanceof NativeChairEntity upper&&upper.proxyShareable()&&NativeSeatBlock.owner(world,pos)!=null;}
    public static boolean replacing(BlockPlaceContext context,BlockPos pos){return context.getItemInHand().getItem() instanceof BlockItem item&&item.getBlock() instanceof NativeCanopyBlock canopy&&supports(canopy.kind())&&proxy(context.getLevel(),pos);}
    public static boolean shared(NativeCanopyEntity owner,BlockPos pos){return owner.getLevel()!=null&&supports(owner.kind())&&proxy(owner.getLevel(),pos);}
    private NativeSeatBackrests(){}
}
