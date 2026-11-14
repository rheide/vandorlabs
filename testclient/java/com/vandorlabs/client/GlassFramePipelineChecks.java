package com.vandorlabs.client;

import com.vandorlabs.ClientProxy;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.tiles.GlassTextures;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;

/** Actual authored frame pixels, real worker publication and retained VBO lifetime. */
public final class GlassFramePipelineChecks {
    private static int checks;
    private static GlassAuthoredAtlas atlas;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message+"; "+GlassFrameRetained.diagnostics()+"; "+GlassPaneRetained.diagnostics()+"; "+GlassPaneTerrain.diagnostics());}
    private static void flush(GlassPanePipelineChecks.Fixture scene){for(int i=0;i<3;i++)scene.flush();}
    @SuppressWarnings("unchecked") private static Map<TileEntityProgrammableGlass,GlassPaneRetained.Group> panes()throws Exception{return (Map<TileEntityProgrammableGlass,GlassPaneRetained.Group>)GlassPanePipelineChecks.field(GlassPaneRetained.class,"OWNERS").get(null);}
    @SuppressWarnings("unchecked") private static Map<TileEntityProgrammableGlass,GlassFrameRetained.Group> frames()throws Exception{return (Map<TileEntityProgrammableGlass,GlassFrameRetained.Group>)GlassPanePipelineChecks.field(GlassFrameRetained.class,"OWNERS").get(null);}
    private static void light(int[] records,VertexFormat format,int sky,int block,String label){
        int stride=format.getIntegerSize(),offset=format.getUvOffsetById(1)/4;
        need(records.length>0,label+" empty records");
        for(int at=offset;at<records.length;at+=stride)need((records[at]&65535)==block&&(records[at]>>>16)==sky,label+" wrong light "+Integer.toHexString(records[at]));
    }
    private static void configure(GlassPanePipelineChecks.Fixture scene,int frame,int tint,int material){
        for(TileEntityProgrammableGlass tile:scene.cells){
            tile.frameAppearance.front=frame;tile.setAppearance(tint,true);tile.setMaterial(material);tile.markDirty();
            scene.world.notifyBlockUpdate(tile.getPos(),scene.world.getBlockState(tile.getPos()),scene.world.getBlockState(tile.getPos()),3);
        }
        scene.queue();flush(scene);
    }
    private static TileEntityProgrammableGlass verify(GlassPanePipelineChecks.Fixture scene,CompiledChunk compiled,int tint,int material)throws Exception{
        int anchors=0,owners=0;TileEntityProgrammableGlass anchor=null;
        for(TileEntityProgrammableGlass tile:scene.cells){
            boolean border=GlassFrameMesh.get(tile).vertexCount()>0;
            need(GlassFrameRetained.handles(tile)==border,"frame ownership differs from actual joined mesh at "+tile.getPos());
            if(border){owners++;light(frames().get(tile).records,frames().get(tile).format,scene.world.light>>>16,240,"emissive frame");}
            if(GlassFrameRetained.anchor(tile)){anchors++;anchor=tile;}
            need(GlassPaneTerrain.rendered(tile)||GlassPaneRetained.handles(tile),"static pane lost both native and retained owner");
            if(GlassPaneRetained.handles(tile)){
                GlassPaneRetained.Group group=panes().get(tile);
                need(group.key.opaque==(tint==GlassTextures.OPAQUE),"opaque/translucent material pass mixed");
                light(group.records,group.format,scene.world.light>>>16,material>=0&&tint==GlassTextures.OPAQUE?240:scene.world.light&65535,"pane");
            }
        }
        need(owners==8&&anchors==1,"3x3 joined border did not collapse to one frame group");
        need(compiled.getTileEntities().size()==1&&compiled.getTileEntities().contains(anchor),"settled uniform pane/frame section retained per-cell admissions: "+compiled.getTileEntities().size());
        long frameDraws=GlassFrameRetained.mainDraws,paneDraws=GlassPaneRetained.mainDraws,legacy=GlassFrameRetained.legacyMainDraws;
        TEProgrammableGlass renderer=new TEProgrammableGlass();renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);
        for(int pass=0;pass<2;pass++){
            ForgeHooksClient.setRenderPass(pass);
            for(TileEntity tile:compiled.getTileEntities())if(tile.shouldRenderInPass(pass)){
                BlockPos pos=tile.getPos();renderer.render((TileEntityProgrammableGlass)tile,pos.getX(),pos.getY(),pos.getZ(),0,-1,1);
            }
            if(pass==0&&tint==GlassTextures.OPAQUE)need(GlassPaneRetained.mainDraws==paneDraws+1,"opaque pane failed pass0 draw");
        }
        need(GlassFrameRetained.mainDraws==frameDraws+1&&GlassFrameRetained.legacyMainDraws==legacy,"frame group leaked to legacy or duplicated draw");
        need(GlassPaneRetained.mainDraws==paneDraws+(GlassPaneRetained.handles(anchor)?1:0),"pane pass duplicated or omitted");
        if(tint==GlassTextures.OPAQUE)need(!anchor.shouldRenderInPass(1),"opaque frame/pane anchor still admitted in pass1");
        need(GL11.glGetError()==0,"render path left GL error");return anchor;
    }
    private static void authoredMatrix()throws Exception{
        for(int frame:atlas.emissiveChoices)for(int kind=0;kind<4;kind++)try(GlassPanePipelineChecks.Fixture scene=new GlassPanePipelineChecks.Fixture()){
            scene.wall(3,3,true,false);int tint=(kind&1)==0?1:GlassTextures.OPAQUE,material=kind<2?-1:atlas.emissiveChoices.get((atlas.emissiveChoices.indexOf(frame)+1)%8);
            configure(scene,frame,tint,material);CompiledChunk compiled=scene.compileAndUpload();flush(scene);flush(scene);verify(scene,compiled,tint,material);
            long frameUploads=GlassFrameRetained.uploads,paneUploads=GlassPaneRetained.uploads;int marks=scene.renderer.marks;
            for(int i=0;i<12;i++)flush(scene);
            need(GlassFrameRetained.uploads==frameUploads&&GlassPaneRetained.uploads==paneUploads&&scene.renderer.marks==marks,"stationary material combination rebuilds/uploads");
        }
    }
    private static void mixedPasses()throws Exception{
        try(GlassPanePipelineChecks.Fixture scene=new GlassPanePipelineChecks.Fixture()){
            scene.wall(3,3,true,false);configure(scene,atlas.emissiveChoices.get(0),1,atlas.emissiveChoices.get(1));
            for(TileEntityProgrammableGlass tile:scene.cells)if(tile.getPos().getX()>0){tile.setWorld(null);tile.setAppearance(GlassTextures.OPAQUE,true);tile.setWorld(scene.world);}
            scene.queue();flush(scene);CompiledChunk compiled=scene.compileAndUpload();flush(scene);flush(scene);
            Set<GlassPaneRetained.Group> groups=Collections.newSetFromMap(new IdentityHashMap<>());Set<TileEntity> anchors=Collections.newSetFromMap(new IdentityHashMap<>());
            for(TileEntityProgrammableGlass tile:scene.cells){GlassPaneRetained.Group group=panes().get(tile);need(group!=null,"mixed pane not retained");groups.add(group);anchors.add(group.anchor);if(GlassFrameRetained.anchor(tile))anchors.add(tile);}
            need(groups.size()==2,"mixed opaque/translucent panes merged draw state or split per-cell");need(compiled.getTileEntities().size()==anchors.size(),"mixed frame and pane anchors not independently admitted");
            TEProgrammableGlass renderer=new TEProgrammableGlass();renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);
            long p=GlassPaneRetained.mainDraws,f=GlassFrameRetained.mainDraws;
            for(int pass=0;pass<2;pass++){
                ForgeHooksClient.setRenderPass(pass);for(TileEntity tile:compiled.getTileEntities())if(tile.shouldRenderInPass(pass)){BlockPos pos=tile.getPos();renderer.render((TileEntityProgrammableGlass)tile,pos.getX(),pos.getY(),pos.getZ(),0,-1,1);}
                need(GlassPaneRetained.mainDraws==p+pass+1,"mixed opaque/translucent pass owns wrong draw count");
            }
            need(GlassFrameRetained.mainDraws==f+1,"mixed pane finishes split frame group");
            for(GlassPaneRetained.Group group:groups)light(group.records,group.format,scene.world.light>>>16,group.key.opaque?240:scene.world.light&65535,"mixed pane");
        }
    }
    private static void dynamicAndFluids()throws Exception{
        int screen=-1;for(int i=0;i<ScreenHousingTextures.BUILTIN_COUNT;i++)if("Screens".equals(ScreenHousingTextures.category(i))){screen=i;break;}
        need(screen>=0,"dynamic glass material missing");
        for(int tint:new int[]{1,GlassTextures.OPAQUE})try(GlassPanePipelineChecks.Fixture scene=new GlassPanePipelineChecks.Fixture()){
            scene.wall(3,3,true,false);configure(scene,atlas.emissiveChoices.get(0),tint,screen);
            for(TileEntityProgrammableGlass tile:scene.cells)tile.glassPlayback.set(2,1);
            scene.queue();flush(scene);CompiledChunk compiled=scene.compileAndUpload();flush(scene);flush(scene);
            int frameAnchors=0;for(TileEntityProgrammableGlass tile:scene.cells){
                need(!GlassPaneTerrain.rendered(tile)&&!GlassPaneRetained.handles(tile),"dynamic glass pane was frozen");
                if(GlassFrameRetained.anchor(tile))frameAnchors++;
            }
            need(frameAnchors==1&&compiled.getTileEntities().size()==9,"dynamic panes rejected frame batching or lost pane admissions");
            final int expectedLight=tint==GlassTextures.OPAQUE?240:scene.world.light&65535;
            final class Capture extends net.minecraft.client.renderer.Tessellator {
                int draws;Capture(){super(4096);}
                @Override public void draw(){net.minecraft.client.renderer.BufferBuilder.State state=getBuffer().getVertexState();light(state.getRawBuffer(),state.getVertexFormat(),scene.world.light>>>16,expectedLight,"dynamic pane");draws++;super.draw();}
            }
            java.lang.reflect.Field singleton=null;for(java.lang.reflect.Field candidate:net.minecraft.client.renderer.Tessellator.class.getDeclaredFields())if(java.lang.reflect.Modifier.isStatic(candidate.getModifiers())&&candidate.getType()==net.minecraft.client.renderer.Tessellator.class)singleton=candidate;
            need(singleton!=null,"Tessellator singleton missing");singleton.setAccessible(true);Object old=singleton.get(null),base=GlassPanePipelineChecks.unsafe.staticFieldBase(singleton);long offset=GlassPanePipelineChecks.unsafe.staticFieldOffset(singleton);Capture capture=new Capture();GlassPanePipelineChecks.unsafe.putObject(base,offset,capture);
            long frames=GlassFrameRetained.mainDraws,legacy=GlassFrameRetained.legacyMainDraws;
            try{
                TEProgrammableGlass renderer=new TEProgrammableGlass();renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);
                for(int pass=0;pass<2;pass++){
                    ForgeHooksClient.setRenderPass(pass);for(TileEntity tile:compiled.getTileEntities())if(tile.shouldRenderInPass(pass)){BlockPos pos=tile.getPos();renderer.render((TileEntityProgrammableGlass)tile,pos.getX(),pos.getY(),pos.getZ(),0,-1,1);}
                }
                need(capture.draws==9,"dynamic pane emitted duplicate or missing draws");need(GlassFrameRetained.mainDraws==frames+1&&GlassFrameRetained.legacyMainDraws==legacy,"dynamic panes restored per-cell frame draws");
            }finally{GlassPanePipelineChecks.unsafe.putObject(base,offset,old);}
        }
        try(GlassPanePipelineChecks.Fixture scene=new GlassPanePipelineChecks.Fixture()){
            scene.wall(3,3,true,false);configure(scene,atlas.emissiveChoices.get(0),1,-1);TileEntityProgrammableGlass wet=scene.cells.get(4);
            net.minecraft.nbt.NBTTagCompound data=new net.minecraft.nbt.NBTTagCompound();data.setBoolean("Waterlogged",true);wet.fluids.read(data);scene.queue();flush(scene);CompiledChunk compiled=scene.compileAndUpload();flush(scene);flush(scene);
            int anchors=0;for(TileEntityProgrammableGlass tile:scene.cells)if(GlassFrameRetained.anchor(tile))anchors++;
            need(anchors==1,"contained fluid split or rejected frame group");need(wet.fluids.any()&&compiled.getTileEntities().contains(wet)&&wet.shouldRenderInPass(1),"frame batching hid independent fluid/pane fallback");
            need(!GlassPaneTerrain.rendered(wet)&&!GlassPaneRetained.handles(wet),"fluid pane incorrectly frozen into static coverage");
        }
    }
    private static void format(VertexFormat format){DefaultVertexFormats.BLOCK.clear();for(VertexFormatElement element:format.getElements())DefaultVertexFormats.BLOCK.addElement(element);}
    private static void nativeHandoff()throws Exception{
        int solid=com.vandorlabs.tiles.ScreenHousingTextures.screenIndex("light_alloy_hull");
        try(GlassPanePipelineChecks.Fixture scene=new GlassPanePipelineChecks.Fixture()){
            scene.wall(3,3,true,false);configure(scene,solid,1,-1);CompiledChunk original=scene.compileAndUpload();flush(scene);flush(scene);
            TileEntityProgrammableGlass border=scene.cells.get(0);
            need(GlassFrameChunks.rendered(border)&&!GlassFrameRetained.handles(border),"authored opaque frame did not use its native chunk");
            need(original.getTileEntities().isEmpty(),"native frame and native pane kept a TESR");
            configure(scene,atlas.emissiveChoices.get(0),1,-1);
            need(!GlassFrameRetained.handles(border),"emissive frame claimed old opaque native coverage before replacement upload");
            for(int i=0;i<8;i++)flush(scene);
            need(!GlassFrameRetained.handles(border),"polling replaced a real native upload fence");
            CompiledChunk replacement=scene.compileAndUpload();flush(scene);flush(scene);verify(scene,replacement,1,-1);
            configure(scene,solid,1,-1);need(!GlassFrameRetained.handles(border),"opaque replacement kept stale emissive VBO");
            scene.compileAndUpload();flush(scene);flush(scene);
            need(GlassFrameChunks.rendered(border)&&!GlassFrameRetained.handles(border),"native frame did not recover after retained edit");
            need(scene.chunk.getCompiledChunk().getTileEntities().isEmpty(),"settled native replacement kept retained anchor admission");
            for(int i=0;i<20;i++)flush(scene);need(!scene.chunk.needsUpdate(),"stationary handoff continuously requested rebuilding");
        }
    }
    private static void lifecycle()throws Exception{
        VertexFormat original=new VertexFormat(DefaultVertexFormats.BLOCK);
        try(GlassPanePipelineChecks.Fixture scene=new GlassPanePipelineChecks.Fixture()){
            scene.wall(3,3,true,false);configure(scene,atlas.emissiveChoices.get(0),GlassTextures.OPAQUE,atlas.emissiveChoices.get(1));CompiledChunk compiled=scene.compileAndUpload();flush(scene);flush(scene);TileEntityProgrammableGlass anchor=verify(scene,compiled,GlassTextures.OPAQUE,atlas.emissiveChoices.get(1));
            long uploads=GlassFrameRetained.uploads;scene.world.light=(200<<16)|48;scene.world.notifyLightSet(anchor.getPos());flush(scene);
            need(GlassFrameRetained.uploads>uploads,"light event did not rebuild frame records");light(frames().get(anchor).records,frames().get(anchor).format,200,240,"relit frame");
            uploads=GlassFrameRetained.uploads;GlassFrameRetained.reload();need(!GlassFrameRetained.handles(anchor),"resource reload kept stale frame owner");flush(scene);need(GlassFrameRetained.uploads>uploads,"resource reload did not replace frame buffer");
            VertexFormat unsupported=new VertexFormat(BlockSurfaceFormat.get()).addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.BYTE,VertexFormatElement.EnumUsage.PADDING,8));format(unsupported);need(!GlassFrameRetained.handles(anchor),"vertex format change retained stale VBO");flush(scene);need(!GlassFrameRetained.handles(anchor),"unsupported format acquired ownership");need(compiled.getTileEntities().size()==scene.cells.size(),"format retirement did not restore fallback admissions");
            format(original);flush(scene);scene.compileAndUpload();flush(scene);flush(scene);need(GlassFrameRetained.handles(anchor),"supported format did not recover");
            BlockPos old=anchor.getPos();anchor.setPos(old.east());need(!GlassFrameRetained.handles(anchor),"relocated tile drew old section VBO");anchor.setPos(old);
            scene.chunk.setPosition(16,0,0);flush(scene);need(!compiled.getTileEntities().isEmpty(),"recycled chunk lost fallback list");scene.chunk.setPosition(0,0,0);scene.compileAndUpload();flush(scene);flush(scene);
            scene.world.tiles.remove(old);scene.world.states.remove(old);scene.world.loadedTileEntityList.remove(anchor);anchor.invalidate();scene.world.notifyBlockUpdate(old,GlassPanePipelineChecks.block.getDefaultState(),net.minecraft.init.Blocks.AIR.getDefaultState(),3);flush(scene);need(!GlassFrameRetained.handles(anchor),"removed frame anchor kept ownership");scene.compileAndUpload();flush(scene);flush(scene);
            need(GL11.glGetError()==0,"lifecycle left GL error");
        }finally{format(original);GlassFrameRetained.flush();GlassPaneRetained.flush();}
        need(GlassFrameRetained.diagnostics().contains("retained-frame-bytes=0"),"unloaded world kept frame VBO bytes");
    }
    public static void main(String[] args)throws Exception{
        GlassPanePipelineChecks.initialize();VandorLabs.proxy=new ClientProxy();atlas=new GlassAuthoredAtlas();
        try{
            if(!"handoff".equals(System.getProperty("vandorlabs.glassPipelineCase"))){authoredMatrix();mixedPasses();dynamicAndFluids();}
            nativeHandoff();
            if(!"handoff".equals(System.getProperty("vandorlabs.glassPipelineCase")))lifecycle();
        }finally{ForgeHooksClient.setRenderPass(-1);}
        need(GL11.glGetError()==0,"pipeline GL error");
        String selected=System.getProperty("vandorlabs.glassPipelineCase","all");
        System.out.println("GlassFramePipeline PASS case="+selected+" checks="+checks+("handoff".equals(selected)?" attached native opaque→retained emissive→native opaque, one real upload per edit, pending fence safety and stationary stability":" actual8 emissive frame pixels/mips × palette/custom × translucent/opaque; independent pass/light/admission, one frame group, stationary reuse, native handoffs, light/reload/format/relocation/recycled-chunk/removal/unload")+"; actualOptiFine=false");
    }
    private GlassFramePipelineChecks(){}
}
