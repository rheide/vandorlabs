package com.vandorlabs.neoforge.door;

import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.render.TextureLayoutMode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Two cells in one chunk are staged before publication. A surviving lower half remains the sole item owner. */
final class NativeDoorPlacement {
    private record Scope(Level level,BlockPos root){}
    private static final ThreadLocal<Scope> ACTIVE=new ThreadLocal<>();
    static boolean mutating(Level level,BlockPos pos){Scope scope=ACTIVE.get();return scope!=null&&scope.level==level&&(scope.root.equals(pos)||scope.root.above().equals(pos));}
    static NativeDoorSettings choices(NativeDoorSettings previous,CompoundTag configured,NativeDoorSettings neighbor,int clickedDepth){
        SpaceDoorData data=neighbor==null?previous.door():neighbor.door();if(data.direction==3||data.design>=21&&data.design!=29)data=previous.door();var frames=previous.frames();var glass=previous.glass();int face=previous.faceTexture(),layout=previous.rawLayout(),panel=previous.rawPanelMode(),mode=previous.securityMode(),allowed=previous.securityAllowed();boolean tile=previous.tileTexture();
        if(configured!=null){var saved=NativeDoorSettings.read(configured);var candidate=saved.door();frames=saved.frames();glass=saved.glass();
            int selected=configured.contains("DoorTextureLayout",Tag.TAG_INT)?configured.getInt("DoorTextureLayout"):configured.getBoolean("DoorTileTexture")?1:0;if(!TextureLayoutMode.valid(selected))throw new IllegalArgumentException("Invalid configured door texture layout");layout=selected;tile=selected==1;
            face=configured.contains("DoorFaceTexture",Tag.TAG_INT)?configured.getInt("DoorFaceTexture")<0?-1:com.vandorlabs.neoforge.ModernFinishCatalog.storedChoice(configured.getInt("DoorFaceTexture")):-1;
            if(candidate.direction!=3&&(candidate.design<21||candidate.design==29))data=candidate;
            panel=configured.contains("DoorPanelMode",Tag.TAG_INT)?saved.rawPanelMode():data.panel?1:0;mode=saved.securityMode();allowed=saved.securityAllowed();
        }
        if(clickedDepth!=0&&panel==2)panel=1;
        return new NativeDoorSettings(new SpaceDoorData(data.design,data.detail,data.framed,data.direction,clickedDepth==0,data.sliding,data.hinges,data.trigger,data.panel),face,layout,tile,clickedDepth,panel,mode,allowed,frames,glass);
    }
    static boolean install(BlockPlaceContext context,NativeProgrammableDoorBlock block,BlockState state,CompoundTag configured,int depth,Runnable transferOwnership){return install(context,block,state,configured,null,depth,transferOwnership);}
    static boolean install(BlockPlaceContext context,NativeProgrammableDoorBlock block,BlockState state,CompoundTag configured,CompoundTag recovery,int depth,Runnable transferOwnership){
        Level level=context.getLevel();BlockPos root=context.getClickedPos();BlockState[] old={level.getBlockState(root),level.getBlockState(root.above())};NativeProgrammableDoorEntity[] installed=new NativeProgrammableDoorEntity[2];Scope previous=ACTIVE.get();ACTIVE.set(new Scope(level,root));boolean success=false;Throwable original=null;NativeDoorSettings prepared=null;ChannelList preparedChannels=ChannelList.EMPTY;
        try{
            var prior=NativeDoorSettings.fresh(false,block.slidingModel());BlockPos mate=NativeDoorLifecycle.mateForPlacement(level,root,block,state);var neighbor=mate!=null&&level.getBlockEntity(mate) instanceof NativeProgrammableDoorEntity host?host.placementSettings():null;
            var settings=choices(prior,configured,neighbor,depth);prepared=settings;ChannelList channels=ChannelList.EMPTY;if(configured!=null){ChannelList fallback=ChannelList.of(Math.max(0,configured.getInt("SpaceDoorChannel")));try{channels=configured.contains("RedstoneChannels",Tag.TAG_INT_ARRAY)?ChannelList.of(configured.getIntArray("RedstoneChannels")):fallback;}catch(IllegalArgumentException malformed){channels=fallback;}}
            preparedChannels=channels;for(int i=0;i<2;i++){BlockPos pos=i==0?root:root.above();BlockState next=state.setValue(NativeProgrammableDoorBlock.HALF,i==0?DoubleBlockHalf.LOWER:DoubleBlockHalf.UPPER);if(!block.stagePart(level,pos,next)||level.getBlockState(pos)!=next||!(level.getBlockEntity(pos) instanceof NativeProgrammableDoorEntity host))return false;installed[i]=host;if(i==0){host.installPlacement(settings,channels);if(recovery!=null&&!host.restoreOpaqueCredentialRecovery(recovery,level.registryAccess()))throw new IllegalStateException("Opaque reader recovery changed during placement");}}
            if(level.getBlockEntity(root)!=installed[0]||level.getBlockEntity(root.above())!=installed[1])return false;
            transferOwnership.run();success=true;
        }catch(RuntimeException|Error failure){original=failure;throw failure;}finally{
            Throwable rollback=null;
            try{
                if(!success){for(int i=1;i>=0;i--){BlockPos pos=i==0?root:root.above();try{if(level.getBlockState(pos).is(block)&&(installed[i]==null||level.getBlockEntity(pos)==installed[i])){if(!block.restorePart(level,pos,old[i])&&level.getBlockState(pos)!=old[i])throw new IllegalStateException("Refused door placement rollback at "+pos);}}catch(RuntimeException|Error failure){if(rollback==null)rollback=failure;else rollback.addSuppressed(failure);}}
                    if(level.getBlockState(root).is(block)&&level.getBlockEntity(root) instanceof NativeProgrammableDoorEntity owner&&(owner==installed[0]||installed[0]==null&&level.getBlockState(root)==state)){if(installed[0]==null&&prepared!=null){owner.installPlacement(prepared,preparedChannels);if(recovery!=null&&!owner.restoreOpaqueCredentialRecovery(recovery,level.registryAccess()))throw new IllegalStateException("Opaque reader recovery refused on retained root");}owner.retainPlacementRecovery();}
                }
            }finally{if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);}
            if(rollback!=null){if(original!=null)original.addSuppressed(rollback);else throw new IllegalStateException("Door placement rollback failed; surviving root retains ownership",rollback);}
        }
        if(success){for(BlockPos pos:new BlockPos[]{root,root.above()}){level.sendBlockUpdated(pos,level.getBlockState(pos),level.getBlockState(pos),Block.UPDATE_CLIENTS);level.updateNeighborsAt(pos,block);}installed[0].publish();}
        return success;
    }
}
