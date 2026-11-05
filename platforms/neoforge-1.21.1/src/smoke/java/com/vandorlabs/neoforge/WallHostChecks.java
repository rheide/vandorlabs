package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.persistence.*;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Small dedicated replay of actual wall/frame saves and atomic host projections; no wall registration needed. */
@EventBusSubscriber(modid="vandorlabs")
public final class WallHostChecks {
    private static int checks;
    private static final String[] KEYS={"housingTexture","SideTexture","MainTextureLayout","SlabTileSides","DiagonalFullWidth","DiagonalHalfHeight","DiagonalFill","DiagonalJoin","JoinPortholes","PortholeShape","FrameFront","FrameInterior","FrameExterior","DisplayArtworkVersion","DisplaySplitTop","DisplaySplitBottom","FrameTile","FrameBare","DisplayFit","DisplaySplit"};
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("Native wall host: "+message);}
    public static final class Counting extends NativeWallEntity {
        int dirty,models;boolean observe;
        Counting(boolean porthole){super(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),BlockPos.ZERO,VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState(),porthole);observe=true;}
        private void projection(){if(observe){var snapshot=wallAppearance();require(snapshot!=null&&snapshot.base()==appearance()&&snapshot.walls()==wallSettings()&&snapshot.frames()==frameSettings(),"complete derived snapshot before notification");require(getModelData().get(WALL_APPEARANCE)==snapshot,"model data exposes immutable wall snapshot");}}
        @Override public void setChanged(){dirty++;projection();super.setChanged();}
        @Override public void requestModelDataUpdate(){models++;projection();super.requestModelDataUpdate();}
        void reset(){dirty=0;models=0;}
    }
    @SubscribeEvent public static void start(ServerStartedEvent event)throws Exception{
        if(!System.getProperty("vandorlabs.transmitterPhase","").equals("wall-host"))return;var registries=event.getServer().registryAccess();String reference=System.getProperty("vandorlabs.wallFrameReference","");require(!reference.isEmpty(),"actual Forge reference required");var root=JsonParser.parseString(Files.readString(Path.of(reference))).getAsJsonObject();var rows=root.getAsJsonArray("cases");require(rows.size()==56,"original independent oracle count");
        for(var entry:rows){var row=entry.getAsJsonObject();boolean porthole=row.get("porthole").getAsBoolean();var host=new Counting(porthole);require(host.wallSettings().joinPortholes==porthole&&host.settings().layout==0,"fresh factory-only joined portholes and Stretch wall layout");var input=TagParser.parseTag(row.get("input").getAsString());var before=input.copy();host.reset();host.loadWithComponents(input,registries);require(input.equals(before)&&host.dirty==0&&host.models==1,"local read and immutable original input");var actual=host.saveWithoutMetadata(registries);var expected=TagParser.parseTag(row.get("saved").getAsString());for(String key:KEYS)require(java.util.Objects.equals(actual.get(key),expected.get(key)),"exact Forge wall/frame saved field="+key+" actual="+actual.get(key)+" expected="+expected.get(key));require(actual.getCompound("ForgeCaps").equals(input.getCompound("ForgeCaps"))&&actual.getCompound("FutureWall").equals(input.getCompound("FutureWall")),"opaque unknown source fields retained");var snapshot=host.wallAppearance();host.loadWithComponents(actual,registries);require(snapshot.walls().join==host.wallSettings().join&&snapshot.frames().exterior==host.frameSettings().exterior,"old snapshot survives roundtrip");}
        var host=new Counting(false);var frames=new FrameSettings(-1,7,-2,0,-1,-1,false,false,false,false);var walls=new WallSettings(true,true,3,11,true,3);var base=new ProgrammableShapeEntity.Appearance(new ProgrammableBlockDialog.State(7,2,false,new int[]{1,2,3,4,5,6},-1),9,new StairState(16,2));host.reset();require(host.configure(base,walls,frames)&&host.dirty==1&&host.models==1,"one notification for mixed base wall frame edit, independent half/full flags retained");var stable=host.wallAppearance();host.reset();require(host.configure(host.appearance(),walls,frames)&&host.dirty==0&&host.models==0&&host.wallAppearance()==stable,"no-op edit quiescent");require(!host.configure(base,new WallSettings(false,false,4,0,false,0),frames)&&host.wallAppearance()==stable&&host.dirty==0,"invalid wall rejects whole transaction");require(!host.configure(base,walls,new FrameSettings(7,7,-2,0,-1,-1,false,false,false,false))&&host.wallAppearance()==stable,"non-Frame front rejects before mutation");
        var next=NativeFlatWallGeometry.build(Direction.EAST,2,null);require(host.publishGeometry(next,5)&&host.dirty==0&&host.models==0&&host.wallAppearance().geometry()==next,"derived topology publication sends no NBT or dirty callback");require(!host.publishGeometry(NativeFlatWallGeometry.build(Direction.EAST,2,null),99)&&host.topologyRevision()==5,"structurally equal topology does not queue or revise");host.refreshFluidGeometry();var volume=host.fluidVolumeSnapshot();long revision=host.fluidGeometryRevision();host.refreshFluidGeometry();require(host.fluidVolumeSnapshot()==volume&&host.fluidGeometryRevision()==revision,"immutable two-side geometry reuses volume");require(host.containedFluidSide(.9,.5,.5)==next.side(.9,.5,.5),"partition reads captured geometry only");
        host.publishGeometry(NativeFlatWallGeometry.straight(Direction.NORTH,0),6);host.refreshFluidGeometry();var splitVolume=host.fluidVolumeSnapshot();require(splitVolume.hasSide(0)&&splitVolume.hasSide(1),"both wall fluid slots own physical cells");require(splitVolume.sideAt(.5,.5,.1)==0&&splitVolume.sideAt(.5,.5,.9)==1&&splitVolume.sideAt(.5,.5,.5)==-1,"front/back fluid ownership separated by solid leaf");
        System.out.println("[vandorlabs][wall-host] dedicated PASS cases="+rows.size()+" checks="+checks+" actual Forge typed defaults sparse frames atomic projection derived topology quiescence");event.getServer().halt(false);
    }
}
