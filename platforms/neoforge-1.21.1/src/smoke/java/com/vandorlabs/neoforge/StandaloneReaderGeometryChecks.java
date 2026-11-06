package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.credentials.NativeReaderGeometry;
import com.vandorlabs.persistence.ControlMountSettings;
import java.nio.file.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Private world-free adapters checked against actual released block output. */
@EventBusSubscriber(modid="vandorlabs")
public final class StandaloneReaderGeometryChecks {
    private static Reader card,chip;
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){
            if(!System.getProperty("vandorlabs.transmitterPhase","").equals("standalone-reader-geometry"))return;
            event.register(net.minecraft.core.registries.Registries.BLOCK,helper->{
                card=new Reader(false);chip=new Reader(true);
                helper.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("vandorlabs","reader_geometry_fixture_card"),card);
                helper.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("vandorlabs","reader_geometry_fixture_chip"),chip);
            });
        }
    }
    private static final class Reader extends DirectionalBlock implements NativeMountedControlGeometry.Profile {
        private final boolean chip;
        Reader(boolean chip){super(Properties.of());this.chip=chip;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(NativeMountedSwitchBlock.ON,false).setValue(NativeMountedSwitchBlock.ROTATION,0));}
        @Override protected MapCodec<? extends DirectionalBlock> codec(){return simpleCodec(properties->new Reader(chip));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,NativeMountedSwitchBlock.ON,NativeMountedSwitchBlock.ROTATION);}
        public String mountedFamily(){return chip?"data_chip_slot":"access_card_slot";}
    }
    private static AABB box(JsonArray a){return new AABB(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble(),a.get(3).getAsDouble(),a.get(4).getAsDouble(),a.get(5).getAsDouble());}
    private static void near(AABB actual,AABB expected,String label){double[] a={actual.minX,actual.minY,actual.minZ,actual.maxX,actual.maxY,actual.maxZ},b={expected.minX,expected.minY,expected.minZ,expected.maxX,expected.maxY,expected.maxZ};for(int i=0;i<6;i++)if(Math.abs(a[i]-b[i])>1e-12)throw new AssertionError(label+" axis="+i+" actual="+actual+" expected="+expected);}
    @SubscribeEvent public static void started(ServerStartedEvent event)throws Exception {
        if(!System.getProperty("vandorlabs.transmitterPhase","").equals("standalone-reader-geometry"))return;
        JsonObject root;try(var input=Files.newBufferedReader(Path.of(System.getProperty("vandorlabs.standaloneReaderReference")))){root=JsonParser.parseReader(input).getAsJsonObject();}
        if(!root.get("golden").getAsString().equals("43da29d672c9413bd453a0ca3380bb2be2bfa591297848d99e45388cb630bfe2"))throw new AssertionError("Reader Source golden");
        int cases=0;
        for(var entry:root.getAsJsonArray("geometry")){
            var row=entry.getAsJsonObject();boolean isChip=row.get("chip").getAsBoolean();int mount=row.get("mount").getAsInt(),rotation=row.get("rotation").getAsInt();
            var state=(isChip?chip:card).defaultBlockState().setValue(DirectionalBlock.FACING,Direction.from3DDataValue(row.get("facing").getAsInt())).setValue(NativeMountedSwitchBlock.ON,row.get("on").getAsBoolean());var settings=ControlMountSettings.of(mount%4,mount/4%4,mount/16);
            near(NativeReaderGeometry.outline(state,settings,rotation,isChip).bounds(),box(row.getAsJsonArray("outline")),"outline "+cases);
            var collision=NativeReaderGeometry.collision(state,settings,rotation);
            if(!row.has("collision")||row.get("collision").isJsonNull()){if(!collision.isEmpty())throw new AssertionError("Flat reader collision "+cases);}
            else {
                near(collision.bounds(),box(row.getAsJsonArray("collision")),"collision "+cases);
                // Compare complete occupied regions allowing only cancellation noise from Source world offsets.
                VoxelShape source=Shapes.empty(),expanded=Shapes.empty();for(var part:row.getAsJsonArray("parts")){AABB b=box(part.getAsJsonArray());source=Shapes.or(source,Shapes.create(b));expanded=Shapes.or(expanded,Shapes.create(b.inflate(1e-12)));}
                if(Shapes.joinIsNotEmpty(collision,expanded,BooleanOp.ONLY_FIRST))throw new AssertionError("Native collision adds geometry "+cases);
                VoxelShape nativeExpanded=Shapes.empty();for(AABB b:collision.toAabbs())nativeExpanded=Shapes.or(nativeExpanded,Shapes.create(b.inflate(1e-12)));
                if(Shapes.joinIsNotEmpty(source,nativeExpanded,BooleanOp.ONLY_FIRST))throw new AssertionError("Native collision omits geometry "+cases);
            }
            cases++;
        }
        System.out.println("[vandorlabs][standalone-reader-geometry] dedicated PASS actualSourceCases="+cases);event.getServer().halt(false);
    }
}
