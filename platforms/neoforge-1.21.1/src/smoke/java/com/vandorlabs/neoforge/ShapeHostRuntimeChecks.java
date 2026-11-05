package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.neoforge.shape.ProgrammableShapeEntity;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import java.nio.file.*;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Unregistered shape host persistence and atomic model publication, on a real dedicated registry. */
@EventBusSubscriber(modid="vandorlabs")
public final class ShapeHostRuntimeChecks {
    private static int checks;private static final String[] FIELDS={"housingTexture","HousingTextureVersion","SideTexture","MainTextureLayout","SlabTileSides","FaceTexturesEnabled","FaceTextures","StairTread","StairRoll","ContainedFluids"};
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("Native shape host: "+message);}
    public static final class CountingShape extends ProgrammableShapeEntity {
        int dirty,requests;boolean observe;
        CountingShape(boolean stair){super(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),BlockPos.ZERO,VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState(),stair);observe=true;}
        void projection(){if(!observe)return;var value=appearance();require(value!=null&&value.housing()==settings()&&value.sideTexture()==sideTexture()&&value.stairs().tread==stairState().tread&&value.stairs().roll==stairState().roll,"complete immutable appearance precedes observer callback");require(getModelData().get(APPEARANCE)==value,"model data contains published snapshot");}
        @Override public void setChanged(){dirty++;projection();super.setChanged();}
        @Override public void requestModelDataUpdate(){requests++;projection();super.requestModelDataUpdate();}
        void reset(){dirty=0;requests=0;}
    }
    @SubscribeEvent public static void started(ServerStartedEvent event)throws Exception{
        if(!System.getProperty("vandorlabs.transmitterPhase","").equals("shape-host"))return;
        var level=event.getServer().overworld();var registries=level.registryAccess();
        int stairs=replay(registries,System.getProperty("vandorlabs.stairReference"),false),defaults=replay(registries,System.getProperty("vandorlabs.shapeDefaultsReference"),true);transactions(level);components(level);
        System.out.println("[vandorlabs][shape-host] dedicated PASS stairs="+stairs+" defaults="+defaults+" checks="+checks+" actual Forge NBT typed housing immutable projection atomic edits opaque fluids components");event.getServer().halt(false);
    }
    private static int replay(HolderLookup.Provider registries,String reference,boolean defaults)throws Exception{
        require(reference!=null,"actual Forge reference required");var rows=JsonParser.parseString(Files.readString(Path.of(reference))).getAsJsonArray();require(rows.size()==(defaults?72:5929),"exact original independent oracle size");
        for(int index=0;index<rows.size();index++){
            var row=rows.get(index).getAsJsonObject();var host=new CountingShape(!defaults||row.get("stair").getAsBoolean());
            if(defaults)host.loadWithComponents(TagParser.parseTag(row.get("initial").getAsString()),registries);
            host.reset();var input=TagParser.parseTag(row.get("input").getAsString());var before=input.copy();host.loadWithComponents(input,registries);require(input.equals(before),"original oracle input never normalized/mutated row="+index);require(host.requests==1&&host.dirty==0,"load publishes once without dirty/world mutation");
            var actual=host.saveWithoutMetadata(registries);var expected=TagParser.parseTag(row.get("saved").getAsString());
            for(String key:FIELDS)require(java.util.Objects.equals(actual.get(key),expected.get(key)),"actual Forge owned field row="+index+" key="+key+" actual="+actual.get(key)+" expected="+expected.get(key));
            if(defaults){require(actual.getCompound("ForgeCaps").equals(input.getCompound("ForgeCaps"))&&actual.getCompound("FutureShape").equals(input.getCompound("FutureShape")),"original opaque capability/future compounds retained");}
            var snapshot=host.appearance();var copy=actual.copy();host.loadWithComponents(copy,registries);require(snapshot.housing().mainTexture==host.settings().mainTexture&&snapshot.stairs().packed()==host.stairState().packed(),"retained snapshot survives reload");int[] faces=snapshot.housing().faces();faces[0]=999;require(snapshot.housing().face(0)!=999,"appearance owns face array");
        }
        return rows.size();
    }
    private static ProgrammableBlockDialog.State housing(int main,int layout){return new ProgrammableBlockDialog.State(main,layout,false,new int[]{1,2,3,4,5,6},-1);}
    private static void transactions(ServerLevel level)throws Exception{
        var host=new CountingShape(true);host.setLevel(level);var before=host.appearance();host.reset();
        require(host.applyShape(new ProgrammableShapeEntity.Appearance(housing(7,2),9,new StairState(16,2))),"valid mixed edit accepted");require(host.dirty==1&&host.requests==1&&host.appearance()!=before,"one dirty/model refresh after combined edit");var stable=host.appearance();host.reset();require(host.applyShape(stable)&&host.dirty==0&&host.requests==0&&host.appearance()==stable,"no-op edit retains snapshot and sends nothing");
        for(var bad:new ProgrammableShapeEntity.Appearance[]{null,new ProgrammableShapeEntity.Appearance(null,9,new StairState(8,0)),new ProgrammableShapeEntity.Appearance(housing(8,1),-2,new StairState(8,0)),new ProgrammableShapeEntity.Appearance(housing(8,3),9,new StairState(8,0)),new ProgrammableShapeEntity.Appearance(housing(8,1),9,new StairState(3,0)),new ProgrammableShapeEntity.Appearance(housing(8,1),9,new StairState(8,3)),new ProgrammableShapeEntity.Appearance(housing(-2,1),9,new StairState(8,0))}){require(!host.applyShape(bad)&&host.appearance()==stable&&host.dirty==0&&host.requests==0,"invalid mixed edit rejects every field atomically");}
        var attempted=new java.util.concurrent.atomic.AtomicBoolean(true);Thread other=new Thread(()->attempted.set(host.applyShape(new ProgrammableShapeEntity.Appearance(housing(8,1),9,new StairState(8,0)))));other.start();other.join();require(!attempted.get()&&host.appearance()==stable&&host.dirty==0,"off-server-thread edit rejected locally");
        host.apply(housing(10,0));require(host.dirty==1&&host.requests==1&&host.appearance().housing().mainTexture==10,"inherited housing entry also publishes before one refresh");host.reset();var changed=new ProgrammableShapeEntity.Appearance(host.settings(),8,host.stairState());require(host.applyShape(changed)&&host.dirty==1&&host.requests==1,"side-only edit refresh exactly once");
    }
    private static void components(ServerLevel level){
        var registries=level.registryAccess();var host=new CountingShape(true);host.setLevel(level);var input=new CompoundTag();input.putInt("HousingTextureVersion",1);input.putInt("housingTexture",7);input.putInt("SideTexture",9);input.putInt("MainTextureLayout",2);input.putInt("StairTread",16);input.putInt("StairRoll",2);input.putIntArray("FaceTextures",new int[]{1,2,3,4,5,6});var opaque=new CompoundTag();opaque.putString("owner","foreign:shape");opaque.putByteArray("bytes",new byte[]{-1,0,2});input.put("ForgeCaps",opaque);input.put("FutureShape",opaque.copy());var fluids=new CompoundTag();var unknown=new CompoundTag();unknown.putString("FluidName","missing:fluid");unknown.putInt("Amount",1000);unknown.put("Tag",opaque.copy());fluids.put("Side0",unknown);input.put("ContainedFluids",fluids);host.loadWithComponents(input,registries);require(host.containedFluids().occupied(0)&&host.containedFluids().get(0).isEmpty(),"unknown retained fluid is occupied before host pipeline");
        var item=new ItemStack(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get());host.saveToItem(item,registries);var serialized=ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),item).getOrThrow();var restored=ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),serialized).getOrThrow();var data=restored.get(DataComponents.BLOCK_ENTITY_DATA);require(data!=null,"actual configured BLOCK_ENTITY_DATA component encoded");var raw=data.copyTag();var placed=new CountingShape(true);placed.loadWithComponents(raw,registries);placed.applyComponentsFromItemStack(restored);var output=placed.saveWithoutMetadata(registries);require(output.getCompound("ForgeCaps").equals(opaque)&&output.getCompound("FutureShape").equals(opaque)&&output.getCompound("ContainedFluids").equals(fluids),"actual item component/codec roundtrip opaque fields and unresolved fluid");require(placed.stairState().packed()==(16|(2<<5))&&placed.appearance().sideTexture()==9&&Arrays.equals(placed.settings().faces(),new int[]{1,2,3,4,5,6})&&!placed.settings().facesEnabled,"configured component retains saved16 roll2 dormant faces and side");
        output.getCompound("ForgeCaps").putString("owner","mutated");require(placed.saveWithoutMetadata(registries).getCompound("ForgeCaps").equals(opaque),"saving never leaks retained opaque storage");
    }
}
