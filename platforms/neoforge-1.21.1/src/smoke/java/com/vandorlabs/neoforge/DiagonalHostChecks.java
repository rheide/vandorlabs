package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.persistence.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Test-only registered type, no placed diagonal family; local publication/refusal contracts. */
@EventBusSubscriber(modid="vandorlabs")
public final class DiagonalHostChecks {
    private static NativeDiagonalWallBlock block;private static BlockEntityType<NativeWallEntity> type;private static int checks;
    private static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").equals("diagonal-host");}
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError("Diagonal host: "+message);}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!enabled())return;event.register(Registries.BLOCK,helper->{block=new NativeDiagonalWallBlock(BlockBehaviour.Properties.of().dynamicShape(),false);helper.register(ResourceLocation.fromNamespaceAndPath("vandorlabs","diagonal_host_fixture"),block);});event.register(Registries.BLOCK_ENTITY_TYPE,helper->{type=BlockEntityType.Builder.of((pos,state)->new NativeWallEntity(type,pos,state,false),block).build(null);helper.register(ResourceLocation.fromNamespaceAndPath("vandorlabs","diagonal_host_fixture"),type);});}
    }
    private static final class Counting extends NativeWallEntity {
        int dirty,models,refusal;boolean observe;
        Counting(BlockPos pos,BlockState state){super(type,pos,state,false);observe=true;}
        private void projection(){if(observe)require(wallAppearance().walls()==wallSettings()&&wallAppearance().playback()==panelPlayback(),"Complete projection before callback");}
        @Override public void setChanged(){dirty++;projection();super.setChanged();}
        @Override public void requestModelDataUpdate(){models++;projection();super.requestModelDataUpdate();}
        @Override protected boolean stageDiagonalState(BlockState next){if(refusal!=0){setBlockState(next);if(refusal==2)throw new IllegalStateException("fixture refused stage");return false;}return super.stageDiagonalState(next);}
        void reset(){dirty=0;models=0;}
    }
    @SubscribeEvent public static void start(ServerStartedEvent event){if(!enabled())return;
        for(int facing=2;facing<6;facing++)for(boolean inverted:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(int fill=0;fill<4;fill++){
            var state=block.defaultBlockState().setValue(NativeDiagonalWallBlock.FACING,Direction.NORTH);var host=new Counting(new BlockPos(15,15,15),state);host.reset();require(host.configureDiagonal(mode,fill,11,Direction.from3DDataValue(facing),inverted),"Valid local transaction");require(host.wallSettings().fullWidth==(mode!=0)&&host.wallSettings().halfHeight==(mode==2)&&host.wallSettings().fill==fill&&host.wallSettings().join==11,"Mode normalization");require(host.getBlockState().getValue(NativeDiagonalWallBlock.FACING).get3DDataValue()==facing&&host.getBlockState().getValue(NativeDiagonalWallBlock.INVERTED)==inverted&&host.dirty==1&&host.models==1,"Exact staged state one notification");boolean chunk=com.vandorlabs.render.DiagonalChunkPolicy.contained(15,15,15,facing,inverted,mode==2,mode!=0);require(host.wallAppearance().chunkBody()==chunk,"Captured coordinate-only containment");host.reset();var snapshot=host.wallAppearance();require(host.configureDiagonal(mode,fill,11,Direction.from3DDataValue(facing),inverted)&&host.wallAppearance()==snapshot&&host.dirty==0&&host.models==0,"No-op quiescent");require(!host.configureDiagonal(3,fill,11,Direction.NORTH,false)&&host.wallAppearance()==snapshot,"Invalid edit atomic");
            var base=host.appearance();var tiled=new com.vandorlabs.ui.ProgrammableBlockDialog.State(base.housing().mainTexture,1,false,base.housing().faces(),-1);host.configure(new ProgrammableShapeEntity.Appearance(tiled,base.sideTexture(),base.stairs()),host.wallSettings(),host.frameSettings());require(!host.wallAppearance().chunkBody(),"Tiled diagonal body routed to BER");
        }
        var host=new Counting(new BlockPos(-17,31,47),block.defaultBlockState());for(int refusal:new int[]{1,2}){var snapshot=host.wallAppearance();var old=host.getBlockState();host.reset();host.refusal=refusal;boolean failed=false;try{failed=!host.configureDiagonal(2,3,7,Direction.EAST,true);}catch(IllegalStateException expected){failed=refusal==2;}require(failed&&host.wallAppearance()==snapshot&&host.wallSettings()==snapshot.walls()&&host.getBlockState()==old&&host.dirty==0&&host.models==0,"Refused/throwing local stage restores fields state and projection");}host.refusal=0;
        var initial=host.renderBounds();require(initial==host.renderBounds(),"Repeated bounds getter no allocation");var pose=new NativeDiagonalWallGeometry.Pose(Direction.EAST,true,1,3,new NativeDiagonalWallGeometry.Join(true,false,true,false,null,null),Double.NaN,Double.NaN,false,false,new NativeDiagonalWallGeometry.Clip(0,0,0,1,1,1),NativePanelLayout.UNIT);var geometry=NativeDiagonalWallGeometry.build(pose);require(host.publishGeometry(geometry,4),"Late clipped geometry published");var translated=geometry.selectionBounds().move(host.getBlockPos());require(host.renderBounds().equals(translated)&&host.renderBounds()!=initial&&host.renderBounds()==host.renderBounds(),"Exact clipped selection bounds translated once");var stable=host.renderBounds();require(!host.publishGeometry(geometry,5)&&host.renderBounds()==stable,"Equal geometry retains bounds object");
        System.out.println("[vandorlabs][diagonal-host] dedicated PASS checks="+checks+" local staged state/refusal projection chunk routing cached late bounds; placed-state callbacks deferred");event.getServer().halt(false);
    }
}
