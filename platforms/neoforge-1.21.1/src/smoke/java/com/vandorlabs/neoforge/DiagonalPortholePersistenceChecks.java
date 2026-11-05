package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Inactive-family persistence proof using the actual test-registered diagonal block and host. */
final class DiagonalPortholePersistenceChecks {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError("Diagonal porthole: "+message);}
    static void run(BlockEntityType<NativeWallEntity> type,NativeDiagonalWallBlock block,NativeDiagonalWallItem item,RegistryAccess registries){
        for(Direction face:Direction.Plane.HORIZONTAL)for(boolean inverted:new boolean[]{false,true}){
            var state=block.defaultBlockState().setValue(NativeDiagonalWallBlock.FACING,face).setValue(NativeDiagonalWallBlock.INVERTED,inverted);
            var host=new NativeWallEntity(type,BlockPos.ZERO,state,true);
            require(host.isPorthole()&&host.wallSettings().joinPortholes,"fresh porthole factory joins");
            require(host.panelGlassSettings().shade==2&&host.panelGlassSettings().textureJoin&&host.panelPlayback().equals(PanelPlayback.DEFAULT),"current panel defaults");
            var input=new CompoundTag();input.putBoolean("DiagonalHalfHeight",true);input.putBoolean("DiagonalFullWidth",false);
            input.putInt("PortholeShape",3);input.putInt("GlassShade",1);input.putInt("GlassTint",2);
            input.putByte("GlassPlaybackMode",(byte)2);input.putByte("GlassPlaybackSpeed",(byte)0);
            var foreign=new CompoundTag();foreign.putString("owner","foreign:opaque");input.put("ForgeCaps",foreign);input.put("FuturePanel",foreign.copy());
            var before=input.copy();host.loadWithComponents(input,registries);
            require(input.equals(before)&&!host.wallSettings().joinPortholes,"missing saved join false; input untouched");
            require(host.wallSettings().halfHeight&&!host.wallSettings().fullWidth,"saved raw half/full flags not normalized");
            var saved=host.saveWithoutMetadata(registries);
            require(saved.getCompound("ForgeCaps").equals(foreign)&&saved.getCompound("FuturePanel").equals(foreign),"opaque capabilities and unknown data retained");
            var component=net.minecraft.world.item.component.CustomData.of(host.saveWithId(registries));saved.getCompound("FuturePanel").putString("owner","mutated");
            var stack=new net.minecraft.world.item.ItemStack(item);stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,component);
            var ops=registries.createSerializationContext(NbtOps.INSTANCE);
            var encoded=net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops,stack).getOrThrow();
            var decoded=net.minecraft.world.item.ItemStack.CODEC.parse(ops,encoded).getOrThrow();
            require(decoded.is(item)&&decoded.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA).equals(component),"actual configured item codec roundtrip");
            require(host.panelPlayback().equals(new PanelPlayback(2,0)),"strict byte playback retained");
            var restored=new NativeWallEntity(type,BlockPos.ZERO,state,true);restored.loadWithComponents(component.copyTag(),registries);
            require(restored.saveWithoutMetadata(registries).getCompound("FuturePanel").equals(foreign),"configured data component owns its NBT");
            require(restored.wallAppearance().glass()==restored.panelGlassSettings()&&restored.wallAppearance().playback()==restored.panelPlayback(),"complete immutable porthole projection");
            var stable=restored.wallAppearance();require(!restored.configurePanelGlass(restored.panelGlassSettings(),restored.panelPlayback(),false,4)&&restored.wallAppearance()==stable,"invalid shape atomically refused");
        }
        System.out.println("[vandorlabs][diagonal-porthole-persistence] dedicated PASS checks="+checks+" factory/load defaults raw flags opaque configured component; placement deferred");
    }
}
