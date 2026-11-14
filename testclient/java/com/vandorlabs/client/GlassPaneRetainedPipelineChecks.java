package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;

/** Real retained-manager ownership, admissions and GL-resource lifetime. */
public final class GlassPaneRetainedPipelineChecks {
    private static int checks;
    private static void need(boolean result,String message){checks++;if(!result)throw new AssertionError(message+"; "+GlassPaneRetained.diagnostics());}
    private static void format(VertexFormat value){DefaultVertexFormats.BLOCK.clear();for(VertexFormatElement element:value.getElements())DefaultVertexFormats.BLOCK.addElement(element);}
    private static void flush(GlassPanePipelineChecks.Fixture scene){
        scene.flush();GlassPaneRetained.flush();scene.flush();
    }
    private static TileEntityProgrammableGlass verify(GlassPanePipelineChecks.Fixture scene,CompiledChunk compiled,int count){
        int anchors=0;TileEntityProgrammableGlass selected=null;
        for(TileEntityProgrammableGlass tile:scene.cells)if(!tile.isInvalid()){
            need(GlassPaneRetained.handles(tile),"real manager omitted an installed pane");
            if(GlassPaneRetained.anchor(tile)){anchors++;selected=tile;}
        }
        need(anchors==1,"coplanar section was not one retained group");
        need(compiled.getTileEntities().size()==count,"compiled list retained per-cell dispatcher admissions: "+compiled.getTileEntities().size());
        need(compiled.getTileEntities().contains(selected),"retained anchor lost dispatcher admission");
        return selected;
    }
    public static void main(String[] args)throws Exception{
        // Controlled fallback mode applies only to this isolated JVM.
        System.setProperty("vandorlabs.staticGlassRenderer","groups");
        GlassPanePipelineChecks.initialize();VertexFormat original=new VertexFormat(DefaultVertexFormats.BLOCK);
        try(GlassPanePipelineChecks.Fixture scene=new GlassPanePipelineChecks.Fixture()){
            scene.wall(16,16,true,true);scene.queue();flush(scene);
            CompiledChunk compiled=scene.compileAndUpload();flush(scene);flush(scene);
            TileEntityProgrammableGlass anchor=verify(scene,compiled,1);
            need(GlassPaneRetained.bounds(anchor).maxX==16&&GlassPaneRetained.bounds(anchor).maxY==16,"anchor bounds do not cover joined section");
            long main=GlassPaneRetained.mainDraws,shadow=GlassPaneRetained.shadowDraws,uploads=GlassPaneRetained.uploads;
            ForgeHooksClient.setRenderPass(0);
            need(GlassPaneRetained.draw(anchor,0,0,0),"opaque pass did not handle retained owner");
            need(main==GlassPaneRetained.mainDraws&&shadow==GlassPaneRetained.shadowDraws,"opaque pass drew translucent pane");
            ForgeHooksClient.setRenderPass(1);
            for(TileEntityProgrammableGlass tile:scene.cells)need(GlassPaneRetained.draw(tile,0,0,0),"retained member escaped into per-cell fallback");
            need(GlassPaneRetained.mainDraws==main+1,"retained members generated more than one draw");
            for(int i=0;i<20;i++)flush(scene);
            need(GlassPaneRetained.uploads==uploads,"stationary group reuploaded");
            VertexFormat ordinary=new VertexFormat(BlockSurfaceFormat.get());
            need(BlockMeshVbo.supportsRetained(ordinary),"ordinary normal32 VBO rejected");
            VertexFormat bad=new VertexFormat(ordinary).addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.BYTE,VertexFormatElement.EnumUsage.PADDING,8));
            VertexFormat extended=new VertexFormat(ordinary).addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.BYTE,VertexFormatElement.EnumUsage.PADDING,24));
            need(!BlockMeshVbo.supportsRetained(bad),"arbitrary40 format accepted");
            need(!BlockMeshVbo.supportsRetained(extended),"56-byte shader layout accepted without actual shader hooks");
            format(bad);
            need(!GlassPaneRetained.handles(anchor),"old-format buffer remained owned after format change");
            flush(scene);
            need(compiled.getTileEntities().size()==scene.cells.size(),"format retirement did not restore every fallback admission");
            need(!GlassPaneRetained.handles(anchor),"unsupported format rebuilt a retained owner");
            format(original);flush(scene);compiled=scene.compileAndUpload();flush(scene);flush(scene);
            anchor=verify(scene,compiled,1);
            need(GlassPaneRetained.uploads>uploads,"restored compatible format did not rebuild VBO");
            TileEntityProgrammableGlass removed=scene.cells.get(20);BlockPos pos=removed.getPos();
            scene.world.tiles.remove(pos);scene.world.states.remove(pos);scene.world.loadedTileEntityList.remove(removed);removed.invalidate();
            GlassPaneRetained.changed(removed);scene.world.notifyBlockUpdate(pos,GlassPanePipelineChecks.block.getDefaultState(),net.minecraft.init.Blocks.AIR.getDefaultState(),3);flush(scene);
            need(!GlassPaneRetained.handles(removed),"removed tile retained group ownership");
            // Native frame publication settles through a genuine replacement upload.
            scene.compileAndUpload();flush(scene);flush(scene);verify(scene,scene.chunk.getCompiledChunk(),1);
            need(GL11.glGetError()==0,"retained lifecycle left a GL error");
            System.out.println("GlassPaneRetainedPipeline PASS checks="+checks+" real manager/worker/VBO/admissions, emissive frames, opaque no-op, one anchor, stationary reuse, incompatible format retirement/recovery, removal; actualOptiFine=false");
        }finally{format(original);ForgeHooksClient.setRenderPass(-1);GlassPaneRetained.flush();}
    }
    private GlassPaneRetainedPipelineChecks(){}
}
