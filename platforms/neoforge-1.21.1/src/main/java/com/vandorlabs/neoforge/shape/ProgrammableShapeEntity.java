package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.ModernHousingTags;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Housing persistence for the upcoming native shapes; registration and fluid lifecycle are separate. */
public class ProgrammableShapeEntity extends ProgrammableBlockEntity {
    public record Appearance(ProgrammableBlockDialog.State housing,int sideTexture,StairState stairs) {
        public boolean valid(){
            if(housing==null||stairs==null||!StairState.valid(stairs.tread,stairs.roll)
                    ||housing.layout<0||housing.layout>2||!ModernFinishCatalog.validChoice(housing.mainTexture)
                    ||sideTexture!=-1&&!ModernFinishCatalog.validChoice(sideTexture))return false;
            for(int i=0;i<6;i++)if(housing.face(i)!=-1&&!ModernFinishCatalog.validChoice(housing.face(i)))return false;
            return true;
        }
    }
    public static final net.neoforged.neoforge.client.model.data.ModelProperty<Appearance> APPEARANCE=new net.neoforged.neoforge.client.model.data.ModelProperty<>();
    private final com.vandorlabs.neoforge.fluid.NativeFluidSlots fluids=new com.vandorlabs.neoforge.fluid.NativeFluidSlots();
    private final boolean stair;
    private StairState stairState=new StairState(8,0);
    private volatile Appearance appearance;
    public ProgrammableShapeEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,boolean stair){
        super(type,pos,state,defaults());this.stair=stair;publishAppearance();
    }
    public final boolean isStair(){return stair;}
    public final StairState stairState(){return stairState;}
    /** Pure storage only; the installed host pipeline must own later world mutations and notifications. */
    public final com.vandorlabs.neoforge.fluid.NativeFluidSlots containedFluids(){return fluids;}
    public final Appearance appearance(){return appearance;}
    private void publishAppearance(){appearance=new Appearance(settings(),sideTexture(),stairState);}
    /** Only the authenticated server handler may invoke live edits. No saved-value normalization here. */
    public boolean applyShape(Appearance next){
        if(next==null||!next.valid())return false;
        if(level!=null&&(level.isClientSide||level.getServer()==null||!level.getServer().isSameThread()))return false;
        int side=next.sideTexture<0?-1:ModernFinishCatalog.storedChoice(next.sideTexture);
        boolean changed=sideTexture()!=side||stairState.tread!=next.stairs.tread||stairState.roll!=next.stairs.roll;
        setSideTexture(side);
        if(stairState.tread!=next.stairs.tread||stairState.roll!=next.stairs.roll)stairState=next.stairs;
        boolean housingChanged=updateSettings(next.housing);
        if(changed||housingChanged){publishAppearance();refresh();}
        return true;
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        stairState=StairState.read(new ModernHousingTags(tag));fluids.read(tag,registries);
        super.loadAdditional(tag,registries);
    }
    @Override public void apply(ProgrammableBlockDialog.State value){applyShape(new Appearance(value,sideTexture(),stairState));}
    @Override protected void housingLoaded(){publishAppearance();}
    @Override public net.neoforged.neoforge.client.model.data.ModelData getModelData(){return super.getModelData().derive().with(APPEARANCE,appearance).build();}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);StairState.write(new ModernHousingTags(tag),stairState.tread,stairState.roll);fluids.write(tag);
    }
}
