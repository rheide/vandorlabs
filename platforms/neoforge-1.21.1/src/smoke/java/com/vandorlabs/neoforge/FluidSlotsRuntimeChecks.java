package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.fluid.NativeFluidSlots;
import java.nio.file.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/** Dedicated real-registry storage/codec checks. No block, world or gameplay adapter is installed. */
@EventBusSubscriber(modid="vandorlabs")
public final class FluidSlotsRuntimeChecks {
    private static int checks;private static Fluid probe;
    private static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").equals("fluid-slots");}
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("Native fluid slots: "+message);}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){
            if(enabled())event.register(Registries.FLUID,helper->{probe=new WaterFluid.Source();helper.register(ResourceLocation.fromNamespaceAndPath("vandorlabs","fluid_slots_probe"),probe);});
        }
    }
    @SubscribeEvent public static void start(ServerStartedEvent event)throws Exception{
        if(!enabled())return;var registries=event.getServer().registryAccess();
        int legacy=reference(registries);modern(registries);isolation(registries);
        System.out.println("[vandorlabs][fluid-slots] dedicated PASS legacy="+legacy+" checks="+checks+" actual codec water lava registered probe opaque invalid data snapshot copies");event.getServer().halt(false);
    }
    private static int reference(HolderLookup.Provider registries)throws Exception{
        String path=System.getProperty("vandorlabs.fluidSlotsReference","");require(!path.isEmpty(),"actual Forge reference required");
        var root=NbtIo.readCompressed(Path.of(path),NbtAccounter.unlimitedHeap());var rows=root.getList("Cases",10);require(rows.size()>90,"nonempty independent Forge cases");
        for(int i=0;i<rows.size();i++){
            var row=rows.getCompound(i);var input=row.getCompound("Input");var expected=row.getCompound("Expected");var slots=new NativeFluidSlots();slots.read(input,registries);var actual=new CompoundTag();slots.write(actual);require(actual.equals(expected),"Forge raw save exact row="+i);
            long revision=slots.revision();var snapshot=slots.snapshot();slots.read(input,registries);require(slots.revision()==revision,"equal raw read does not revise row="+i);
            for(int side=0;side<2;side++){
                require(slots.occupied(side)==row.getBoolean("Occupied"+side),"Forge occupancy row="+i+" side="+side);var fluid=slots.get(side);String name=row.getString("Fluid"+side);int amount=row.getInt("Amount"+side);
                if(!name.isEmpty()&&amount>0){require(!fluid.isEmpty()&&BuiltInRegistries.FLUID.getKey(fluid.getFluid()).equals(ResourceLocation.parse(name))&&fluid.getAmount()==amount,"Forge positive fluid resolution row="+i);var data=fluid.get(DataComponents.CUSTOM_DATA);require((data!=null)==row.getBoolean("HasTag"+side),"legacy Tag presence row="+i);if(data!=null)require(data.copyTag().equals(row.getCompound("Tag"+side)),"legacy Tag contents row="+i);}
                else require(fluid.isEmpty(),"unknown/nonpositive Forge data remains opaque occupied row="+i);
            }
            input.putString("ContainedFluids","mutated input");actual.getCompound(NativeFluidSlots.KEY).putString("Side0","mutated output");var retained=new CompoundTag();snapshot.write(retained);require(retained.equals(expected),"retained snapshot owns read/write data row="+i);
        }
        return rows.size();
    }
    private static CompoundTag opaque(){var tag=new CompoundTag();tag.putString("unicode","水µ");tag.putLongArray("bits",new long[]{Long.MIN_VALUE,Long.MAX_VALUE});var nested=new CompoundTag();nested.putInt("v",23);tag.put("nested",nested);return tag;}
    private static CompoundTag encoded(FluidStack fluid,HolderLookup.Provider registries){return (CompoundTag)FluidStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),fluid).getOrThrow();}
    private static CompoundTag root(CompoundTag side){var data=new CompoundTag();data.put("Side0",side);var root=new CompoundTag();root.put(NativeFluidSlots.KEY,data);return root;}
    private static void modern(HolderLookup.Provider registries){
        for(var fluid:new Fluid[]{Fluids.WATER,Fluids.LAVA,probe})for(int amount:new int[]{1,1000,Integer.MAX_VALUE}){
            var stack=new FluidStack(fluid,amount);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(opaque()));stack.set(DataComponents.BUCKET_ENTITY_DATA,CustomData.of(opaque()));stack.set(DataComponents.CUSTOM_MODEL_DATA,new net.minecraft.world.item.component.CustomModelData(42));var raw=encoded(stack,registries);raw.put("UnknownModern",opaque());
            var slots=new NativeFluidSlots();slots.read(root(raw),registries);require(FluidStack.matches(stack,slots.get(0)),"real native CODEC includes all component values");var out=new CompoundTag();slots.write(out);require(out.getCompound(NativeFluidSlots.KEY).getCompound("Side0").equals(raw),"modern unknown fields retained");
            var saved=slots.snapshot();long revision=slots.revision();slots.set(1,stack,registries);require(slots.revision()==revision+1&&FluidStack.matches(stack,slots.get(1)),"set second side encodes modern data");slots.set(1,stack,registries);require(slots.revision()==revision+1,"identical set does not revise");slots.set(0,null,registries);require(!slots.occupied(0)&&slots.occupied(1)&&saved.side(0).occupied()&&!saved.side(1).occupied(),"old immutable snapshot retains both original sides");
            out=new CompoundTag();slots.write(out);var restored=new NativeFluidSlots();restored.read(out,registries);require(FluidStack.matches(stack,restored.get(1)),"modern set write reload full components");
        }
        for(String id:new String[]{"missing:fluid","BAD ID!","minecraft:empty","minecraft:water"})for(int amount:new int[]{-1,0,1}){
            var raw=new CompoundTag();raw.putString("id",id);raw.putInt("amount",amount);raw.put("unknown",opaque());if(id.equals("minecraft:water")&&amount==1)raw.putString("components","malformed");var slots=new NativeFluidSlots();slots.read(root(raw),registries);require(slots.occupied(0)&&slots.get(0).isEmpty(),"malformed modern CODEC occupied raw retained id="+id+" amount="+amount);var out=new CompoundTag();slots.write(out);require(out.equals(root(raw)),"malformed native round trip is lossless");
        }
    }
    private static void isolation(HolderLookup.Provider registries){
        var slots=new NativeFluidSlots();var empty=slots.get(0);require(empty!=FluidStack.EMPTY,"never expose mutable global EMPTY");empty.setAmount(1000);empty.set(DataComponents.CUSTOM_DATA,CustomData.of(opaque()));require(slots.get(0).isEmpty()&&slots.get(0).get(DataComponents.CUSTOM_DATA)==null&&FluidStack.EMPTY.get(DataComponents.CUSTOM_DATA)==null,"empty return mutation isolated");
        var source=new FluidStack(Fluids.WATER,1000);source.set(DataComponents.CUSTOM_DATA,CustomData.of(opaque()));source.set(DataComponents.BUCKET_ENTITY_DATA,CustomData.of(opaque()));slots.set(0,source,registries);var initial=slots.raw(0);source.setAmount(1);source.get(DataComponents.CUSTOM_DATA).getUnsafe().putString("mutated","source");require(slots.get(0).getAmount()==1000&&slots.raw(0).equals(initial)&&!slots.get(0).get(DataComponents.CUSTOM_DATA).copyTag().contains("mutated"),"set owns stack/components");
        var returned=slots.snapshot().side(0).fluid();returned.setAmount(2);returned.get(DataComponents.BUCKET_ENTITY_DATA).getUnsafe().putString("mutated","bucket");returned.get(DataComponents.CUSTOM_DATA).getUnsafe().putString("mutated","returned");var raw=slots.raw(0);raw.putString("mutated","raw");require(slots.raw(0).equals(initial)&&slots.get(0).getAmount()==1000&&!slots.get(0).get(DataComponents.CUSTOM_DATA).copyTag().contains("mutated"),"snapshot stack and raw access own copies");require(!slots.get(0).get(DataComponents.BUCKET_ENTITY_DATA).copyTag().contains("mutated"),"all opaque CustomData component types copied");
        long revision=slots.revision();for(int side:new int[]{-1,2}){boolean failed=false;try{slots.set(side,source,registries);}catch(IllegalArgumentException expected){failed=true;}require(failed&&slots.revision()==revision,"invalid side atomic rejection");}
    }
}
