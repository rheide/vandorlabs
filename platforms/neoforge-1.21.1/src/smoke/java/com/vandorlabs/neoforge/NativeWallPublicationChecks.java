package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.*;
import net.minecraft.core.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Observe actual volume identity/revision when only joined artwork coordinates change. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeWallPublicationChecks {
    private static int checks;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Wall publication: "+why);}
    @SubscribeEvent public static void start(ServerStartedEvent event){if(!System.getProperty("vandorlabs.transmitterPhase","").equals("wall-publication"))return;
        var layout=new NativePanelLayout(1,1,3,2,true,false);
        for(boolean diagonal:new boolean[]{false,true}){
            var block=diagonal?VandorLabsBootstrap.PROGRAMMABLE_DIAGONAL_WALL.get():VandorLabsBootstrap.PROGRAMMABLE_WALL.get();
            var host=new NativeWallEntity(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),new BlockPos(8,8,8),block.defaultBlockState(),false);
            host.refreshFluidGeometry();var original=host.wallAppearance().geometry();var volume=host.fluidVolumeSnapshot();long physical=host.fluidGeometryRevision(),model=host.modelDataRevision();
            need(host.publishGeometry(original.withLayout(layout),1),"new artwork coordinates publish");host.refreshFluidGeometry();
            need(host.wallAppearance().geometry().layout().equals(layout)&&host.modelDataRevision()>model,"new artwork coordinates reach model snapshot");
            need(host.fluidVolumeSnapshot()==volume&&host.fluidGeometryRevision()==physical,"artwork-only update must reuse physical fluid volume");
            NativeWallGeometry changed=diagonal?NativeDiagonalWallGeometry.build(new NativeDiagonalWallGeometry.Pose(Direction.NORTH,false,1,0,null,Double.NaN,Double.NaN,false,false,NativeDiagonalWallGeometry.Clip.NONE,layout)):NativeFlatWallGeometry.straight(Direction.NORTH,1);
            need(host.publishGeometry(changed,2),"physical pose changes");host.refreshFluidGeometry();
            need(host.fluidVolumeSnapshot()!=volume&&host.fluidGeometryRevision()>physical,"physical changes rebuild fluid volume");
            var stable=host.fluidVolumeSnapshot();long revision=host.modelDataRevision();need(!host.publishGeometry(changed,3)&&host.fluidVolumeSnapshot()==stable&&host.modelDataRevision()==revision,"identical snapshots remain quiescent");
        }
        System.out.println("[vandorlabs][wall-publication] dedicated PASS checks="+checks+" actual flat/diagonal cosmetic-only volume identity and independent physical changes");event.getServer().halt(false);
    }
}
