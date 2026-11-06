package com.vandorlabs.neoforge.screen;

import com.google.gson.*;
import com.vandorlabs.screen.ScreenFamily;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Private actual entity/save/prepare/install settings, compared to the released configured item corpus. */
@EventBusSubscriber(modid="vandorlabs")
public final class ScreenItemSettingsChecks {
    private static final NativeScreenBlock[] BLOCKS=new NativeScreenBlock[7];
    private static BlockEntityType<NativeScreenEntity> TYPE;private static int checks;
    private static boolean enabled(){return "screen-item-settings".equals(System.getProperty("vandorlabs.transmitterPhase"));}
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Native Screen item: "+why);}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){
            if(!enabled())return;
            event.register(Registries.BLOCK,helper->{for(var family:ScreenFamily.values()){BLOCKS[family.ordinal()]=NativeScreenBlock.create(family,BlockBehaviour.Properties.of().strength(5,6),()->TYPE);helper.register(ResourceLocation.fromNamespaceAndPath("vandorlabs","screen_item_settings_"+family.ordinal()),BLOCKS[family.ordinal()]);}});
            event.register(Registries.BLOCK_ENTITY_TYPE,helper->{TYPE=BlockEntityType.Builder.<NativeScreenEntity>of((pos,state)->new NativeScreenEntity(TYPE,pos,state,((NativeScreenBlock)state.getBlock()).family(),NativeScreenCatalog.INSTANCE),BLOCKS).build(null);helper.register(ResourceLocation.fromNamespaceAndPath("vandorlabs","screen_item_settings"),TYPE);});
        }
    }
    private static final String[] OWNED={"selectedScreen","redstoneEnabled","displayMode","framed","animationSpeedIndex","inputPanel","secondaryInputPanel","wallPosition","smallInput","housingTexture","HousingTextureVersion","MainTextureLayout","SlabTileSides","RedstoneChannel","RedstoneChannels","RedstoneInverted","PrimarySurfaceTexture","SecondarySurfaceTexture","SurfaceTextureLayout","SurfaceTileSides","CeilingMounted","CeilingPosition","FrameFront","FrameInterior","FrameExterior","DisplayArtworkVersion","DisplaySplitTop","DisplaySplitBottom","FrameTile","DisplayFit","DisplaySplit","FrameBare","RedstonePrimary","RedstoneSecondary"};
    private static void boundaries(net.minecraft.core.HolderLookup.Provider registries)throws Exception{
        var family=ScreenFamily.SCREEN;var state=BLOCKS[0].defaultBlockState();var pos=new BlockPos(8,80,8);
        var source=TagParser.parseTag("{housingTexture:14,HousingTextureVersion:1,ForeignRoot:'retain',NeoForgeData:{Original:'delete'},components:{'minecraft:custom_name':'\"Original\"'}}");
        var owner=new NativeScreenEntity(TYPE,pos,state,family,NativeScreenCatalog.INSTANCE);owner.loadWithComponents(source.copy(),registries);need(!owner.inert()&&owner.components().has(DataComponents.CUSTOM_NAME),"actual current platform fields installed");
        owner.getPersistentData().remove("Original");owner.getPersistentData().putString("Live","current");owner.setComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
        var choices=NativeScreenItemSettings.choices(owner,registries);need(!choices.contains("components")&&!choices.getCompound("NeoForgeData").contains("Original")&&choices.getCompound("NeoForgeData").getString("Live").equals("current"),"inventory captures live platform edits/deletions");
        var prepared=NativeScreenItemSettings.prepare(TYPE,pos,state,family,choices,registries);prepared.snapshot().putString("ForeignRoot","tampered");
        var installed=new NativeScreenEntity(TYPE,pos,state,family,NativeScreenCatalog.INSTANCE);NativeScreenItemSettings.install(installed,prepared);need(installed.saveWithoutMetadata(registries).getString("ForeignRoot").equals("retain")&&installed.getPersistentData().getString("Live").equals("current"),"private prepared snapshot is defensive and installs platform fields");
        var wrong=new NativeScreenEntity(TYPE,pos,BLOCKS[1].defaultBlockState(),ScreenFamily.CONSOLE,NativeScreenCatalog.INSTANCE);var before=wrong.visual();try{NativeScreenItemSettings.install(wrong,prepared);throw new AssertionError("wrong family installed");}catch(IllegalArgumentException expected){}need(wrong.visual()==before,"family refusal before mutation");
        var bad=new CompoundTag();bad.putInt("ScreenChipReader",42);bad.putString("FutureOwner","opaque");var original=bad.copy();try{NativeScreenItemSettings.prepare(TYPE,pos,state,family,bad,registries);throw new AssertionError("unsupported ownership installed");}catch(IllegalArgumentException expected){}need(bad.equals(original),"unsupported preflight leaves original unchanged");
        owner.loadWithComponents(bad.copy(),registries);need(owner.inert(),"unsupported source stays inert");var item=NativeScreenItemSettings.pick(net.minecraft.world.item.Items.PAPER,owner,registries);var recovery=NativeScreenItemSettings.configuration(item);need(recovery.getCompound(NativeScreenItemSettings.RECOVERY).equals(original),"opaque configured pick retains full original owner");
        try{NativeScreenItemSettings.prepare(TYPE,pos,state,family,recovery,registries);throw new AssertionError("opaque recovery installed");}catch(IllegalArgumentException expected){}need(item.getCount()==1,"recovery preflight cannot consume item");
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        if(!enabled())return;var server=event.getServer();
        try(var reader=Files.newBufferedReader(Path.of(System.getProperty("vandorlabs.screenItemReference","../../build/test-artifacts/screen-item-reference.json")))){
            var rows=JsonParser.parseReader(reader).getAsJsonArray();var registries=server.registryAccess();int cases=0;
            for(var entry:rows){var row=entry.getAsJsonObject();var family=ScreenFamily.fromId(row.get("id").getAsString());var block=BLOCKS[family.ordinal()];var state=block.state(family.decode(row.get("meta").getAsInt()));var input=TagParser.parseTag(row.get("input").getAsString());var original=input.copy();
                var prepared=NativeScreenItemSettings.prepare(TYPE,BlockPos.ZERO,state,family,input,registries);need(input.equals(original),"preflight input not mutated");
                var owner=new NativeScreenEntity(TYPE,BlockPos.ZERO,state,family,NativeScreenCatalog.INSTANCE);NativeScreenItemSettings.install(owner,prepared);var actual=NativeScreenItemSettings.choices(owner,registries);
                var expected=NativeScreenArtworkMigration.normalize(TagParser.parseTag(row.get("expected").getAsString()),NativeScreenCatalog.INSTANCE);
                for(String key:OWNED)need(Objects.equals(actual.get(key),expected.get(key)),"source choices case="+cases+" role="+family+" key="+key+" expected="+expected.get(key)+" actual="+actual.get(key));
                for(String key:new String[]{"id","x","y","z","ChannelLevel","ChannelSignal","JoinedSignalLevel","LightGroupPowered","NativeScreenPower","NativeScreenGeometryRevision"})need(!actual.contains(key),"inventory runtime excluded "+key);
                for(String key:new String[]{"RedstonePrimary","RedstoneSecondary"})if(actual.contains(key,Tag.TAG_COMPOUND))for(var rawRow:actual.getCompound(key).getList("Rows",Tag.TAG_COMPOUND))for(String runtime:new String[]{"Levels","Level","LatchedChannels","Active","PulseUntil"})need(!((CompoundTag)rawRow).contains(runtime),"row inventory ownership excluded "+runtime);
                cases++;
            }
            boundaries(registries);need(cases==630,"released corpus coverage");System.out.println("[vandorlabs][screen-item-settings] PASS cases="+cases+" checks="+checks+" inactive normalization foundation; player item transactions remain separate");
        }catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][screen-item-settings] FAIL "+failure);}finally{server.halt(false);}
    }
}
