package com.vandorlabs.neoforge;

import com.vandorlabs.tiles.FilesystemTextureDiscovery;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Real dedicated process persistence with an independently missing startup catalog. */
@EventBusSubscriber(modid="vandorlabs")
public final class FilesystemRuntimeChecks {
    public static final String SOURCE="Native/portrait.png";
    public static final int CHOICE=FilesystemTextureDiscovery.identifier(SOURCE);
    public static final BlockPos POS=new BlockPos(0,80,0);
    static void require(boolean value,String message){if(!value)throw new IllegalStateException("Native filesystem: "+message);}
    @SubscribeEvent public static void started(ServerStartedEvent event){
        String phase=System.getProperty("vandorlabs.filesystemPhase","");if(!phase.equals("write")&&!phase.equals("read")&&!phase.equals("missing"))return;
        var server=event.getServer();var level=server.overworld();level.getChunkAt(POS);
        require(FilesystemMaterials.catalog().entries.size()>256,"persistent filesystem registry was capped as a transient cache");
        if(phase.equals("write")){
            require(ModernFinishCatalog.renderChoice(CHOICE)==CHOICE,"startup file not registered");
            level.setBlock(POS,VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState(),3);
            var tile=(ProgrammableBlockEntity)level.getBlockEntity(POS);tile.apply(new com.vandorlabs.ui.ProgrammableBlockDialog.State(CHOICE,0,false,new int[]{CHOICE,-1,-1,-1,-1,-1},-1));
            var tag=tile.saveWithoutMetadata(level.registryAccess());tag.putString("UnknownFutureSettings","kept");tile.loadWithComponents(tag,level.registryAccess());tile.setChanged();
        }else{
            var tile=(ProgrammableBlockEntity)level.getBlockEntity(POS);require(tile!=null&&tile.settings().mainTexture==CHOICE&&tile.settings().face(0)==CHOICE,"saved stable filesystem IDs lost");
            CompoundTag saved=tile.saveWithoutMetadata(level.registryAccess());require(saved.getString("UnknownFutureSettings").equals("kept"),"opaque saved field lost");
            require(ModernFinishCatalog.renderChoice(CHOICE)==(phase.equals("missing")?0:CHOICE),"missing peer vs installed identity fallback");
            var item=new net.minecraft.world.item.ItemStack(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get());tile.saveToItem(item,level.registryAccess());require(item.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA).copyTag().getInt(com.vandorlabs.persistence.HousingSettings.MAIN)==CHOICE,"configured item lost unavailable saved ID");
        }
        server.saveEverything(true,true,true);System.out.println("[vandorlabs][filesystem] server-"+phase+" PASS startup catalog >256, stable IDs, disk save/restart, missing-peer fallback and configured item data");server.halt(false);
    }
}
