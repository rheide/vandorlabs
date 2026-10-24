package com.vandorlabs.neoforge;

import com.vandorlabs.ui.ProgrammableBlockDialog;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public final class ProgrammableBlockEntity extends BlockEntity {
    public static final ModelProperty<ProgrammableBlockDialog.State> SETTINGS = new ModelProperty<>();
    private CompoundTag retainedSettings = new CompoundTag();
    private ProgrammableBlockDialog.State settings = defaults();
    public static ProgrammableBlockDialog.State defaults() { return new ProgrammableBlockDialog.State(0,1,false,null,-1); }
    public ProgrammableBlockEntity(BlockPos pos, BlockState state) { super(VandorLabsModern.PROGRAMMABLE_ENTITY.get(), pos, state); }
    public ProgrammableBlockDialog.State settings() { return settings; }
    public void apply(ProgrammableBlockDialog.State next) {
        int[] faces = next.faces();
        for (int i=0;i<6;i++) faces[i] = faces[i] < 0 ? -1 : ModernFinishCatalog.storedChoice(faces[i]);
        settings = new ProgrammableBlockDialog.State(ModernFinishCatalog.storedChoice(next.mainTexture),next.layout,next.facesEnabled,faces,-1);
        setChanged();
        if (level != null) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
        }
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);
        retainedSettings=tag.copy();
        for(String key:java.util.List.of("id","x","y","z","components","NeoForgeData","neoforge:attachments"))retainedSettings.remove(key);
        int main = tag.getInt(ProgrammableBlockDialog.MAIN_TEXTURE_KEY);
        main=ProgrammableBlockDialog.savedMainTexture(main,tag.contains("HousingTextureVersion",net.minecraft.nbt.Tag.TAG_INT));
        int[] faces=tag.getIntArray(ProgrammableBlockDialog.FACES_KEY);
        if(faces.length!=6) { faces=new int[6];Arrays.fill(faces,-1); }
        for(int i=0;i<6;i++)faces[i]=faces[i]<0?-1:ModernFinishCatalog.storedChoice(faces[i]);
        int layout=ProgrammableBlockDialog.savedTextureLayout(tag.getInt(ProgrammableBlockDialog.LAYOUT_KEY),tag.contains(ProgrammableBlockDialog.LAYOUT_KEY,net.minecraft.nbt.Tag.TAG_INT),tag.getBoolean("SlabTileSides"));
        settings=new ProgrammableBlockDialog.State(ModernFinishCatalog.storedChoice(main),layout,tag.getBoolean(ProgrammableBlockDialog.FACES_ENABLED_KEY),faces,-1);
        requestModelDataUpdate();
        if(level!=null&&level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        tag.merge(retainedSettings);
        tag.putInt(ProgrammableBlockDialog.MAIN_TEXTURE_KEY,settings.mainTexture);
        tag.putInt("HousingTextureVersion",1);
        tag.putInt(ProgrammableBlockDialog.LAYOUT_KEY,settings.layout);
        tag.putBoolean("SlabTileSides",settings.layout==1);
        tag.putBoolean(ProgrammableBlockDialog.FACES_ENABLED_KEY,settings.facesEnabled);
        tag.putIntArray(ProgrammableBlockDialog.FACES_KEY,settings.faces());
    }
    @Override protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if(level!=null) {
            CompoundTag configuration=saveWithId(level.registryAccess());
            for(String key:java.util.List.of("x","y","z","ChannelLevel","channelSignal","JoinedSignalLevel","LightGroupPowered"))configuration.remove(key);
            components.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(configuration));
        }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public ModelData getModelData() { return ModelData.builder().with(SETTINGS,settings).build(); }
}
