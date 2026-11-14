package com.vandorlabs.client;

import com.vandorlabs.ClientProxy;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockProgrammableWall;
import com.vandorlabs.tiles.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;

/** Actual section compilation, VBO ownership, independent passes and safe tile-list handoff. */
public final class PortholeRetainedPipelineChecks {
    private static int checks,cases;
    private static String fixture;
    private static final TEAnimatedScreenSelector RENDERER=new TEAnimatedScreenSelector();
    private static void need(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message+" ["+fixture+"]; "+PortholeRetained.diagnostics());}
    @SuppressWarnings("unchecked") private static Map<TileEntityAnimatedScreenSelector,PortholeRetained.Owner> owners()throws Exception{
        return (Map<TileEntityAnimatedScreenSelector,PortholeRetained.Owner>)GlassPanePipelineChecks.field(PortholeRetained.class,"OWNERS").get(null);
    }
    private static Set<PortholeRetained.Group> groups(Collection<TileEntityAnimatedScreenSelector> tiles)throws Exception{
        Set<PortholeRetained.Group> groups=Collections.newSetFromMap(new IdentityHashMap<>());
        Map<TileEntityAnimatedScreenSelector,PortholeRetained.Owner> owners=owners();
        for(TileEntityAnimatedScreenSelector tile:tiles){PortholeRetained.Owner owner=owners.get(tile);if(owner==null)continue;if(owner.housing!=null)groups.add(owner.housing);if(owner.pane!=null)groups.add(owner.pane);}return groups;
    }
    private static final class Scene implements AutoCloseable {
        final GlassPanePipelineChecks.Fixture fixture=new GlassPanePipelineChecks.Fixture();
        final List<TileEntityAnimatedScreenSelector> tiles=new ArrayList<>();
        final BlockProgrammableWall block=new BlockProgrammableWall("porthole_pipeline",BlockProgrammableWall.Shape.PORTHOLE);
        final IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,EnumFacing.NORTH);
        Scene(int width,int height,boolean hole,int material,int tint,int playback,int rim)throws Exception{
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(!hole||x!=width/2||y!=height/2){
                TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setJoinPortholes(true);
                tile.setGlassShade(19);tile.setGlassMaterial(material);tile.setGlassAppearance(tint,false);tile.glassPlayback.set(playback,1);
                tile.frameAppearance.interior=rim;tile.frameAppearance.exterior=rim;
                BlockPos pos=new BlockPos(x,y,1);tile.setPos(pos);tile.setWorld(fixture.world);
                fixture.world.states.put(pos,state);fixture.world.tiles.put(pos,tile);fixture.world.loadedTileEntityList.add(tile);tiles.add(tile);
                PortholeRetained.changed(tile);
            }
        }
        void flush(){fixture.flush();PortholeRetained.flush();}
        CompiledChunk settle()throws Exception{flush();CompiledChunk compiled=fixture.compileAndUpload();flush();flush();return compiled;}
        public void close()throws Exception{fixture.close();PortholeRetained.flush();}
    }
    private static void verify(Scene scene,CompiledChunk compiled,boolean dynamic)throws Exception{
        int expected=0,anchors=0;
        for(TileEntityAnimatedScreenSelector tile:scene.tiles)if(!tile.isInvalid()){
            need(PortholeRetained.handles(tile)&&PortholeRetained.handlesHousing(tile),"installed porthole failed retained ownership");
            PortholeRetained.Owner owner=owners().get(tile);boolean retained=owner.anchor(),required=retained||!owner.staticPane||tile.fluids.any();
            if(retained){anchors++;need(PortholeRetained.bounds(tile)!=null,"retained anchor has no visibility bounds");}
            need(compiled.getTileEntities().contains(tile)==required,"tile admissions do not match independent housing/pane/dynamic/fluid work");
            if(required)expected++;
            need(owner.staticPane!=dynamic||owner.emptyPane,"animated/static pane classification incorrect");
        }
        need(compiled.getTileEntities().size()==expected,"compiled list contains unexpected residual portholes");
        need(anchors>0,"stationary section lost all renderer anchors");
    }
    private static void render(Scene scene,CompiledChunk compiled){
        GlazingRenderOrder.end();
        for(int pass=0;pass<=1;pass++){
            ForgeHooksClient.setRenderPass(pass);
            for(TileEntity raw:new ArrayList<>(compiled.getTileEntities()))if(raw instanceof TileEntityAnimatedScreenSelector&&raw.shouldRenderInPass(pass)){
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)raw;BlockPos pos=tile.getPos();
                RENDERER.render(tile,pos.getX(),pos.getY(),pos.getZ(),0,-1,1);
            }
        }
    }
    private static void packedLight(Scene scene,int expected)throws Exception{
        for(PortholeRetained.Group group:groups(scene.tiles)){
            int stride=group.format.getIntegerSize(),light=group.format.getUvOffsetById(1)/4;
            for(int i=light;i<group.records.length;i+=stride)need(group.records[i]==expected,"housing/pane lost independent legacy world light");
        }
    }
    private static void staticMaterials(GlassAuthoredAtlas atlas)throws Exception{
        int custom=ScreenHousingTextures.screenIndex("light_alloy_hull");need(custom>=0,"custom fixture missing");
        for(int material:new int[]{-1,custom})for(int tint:new int[]{1,GlassTextures.OPAQUE})for(int rim:new int[]{-1,atlas.emissiveChoices.get(0)}){
            fixture="material="+material+" tint="+tint+" rim="+rim;
            try(Scene scene=new Scene(4,4,true,material,tint,1,rim)){
                CompiledChunk compiled=scene.settle();verify(scene,compiled,false);
                Set<PortholeRetained.Group> groups=groups(scene.tiles);int housing=0,pane=0;
                for(PortholeRetained.Group group:groups)if(group.key.kind==0)housing++;else pane++;
                need(housing==1&&pane==1,"one coplanar section did not become independent housing/pane groups");
                long h=PortholeRetained.housingDraws,p=PortholeRetained.paneDraws,legacyH=PortholeRetained.legacyHousingDraws,legacyP=PortholeRetained.legacyPaneDraws;
                render(scene,compiled);
                need(PortholeRetained.housingDraws==h+1&&PortholeRetained.paneDraws==p+1,"static section submitted per-owner housing or pane draws");
                need(PortholeRetained.legacyHousingDraws==legacyH&&PortholeRetained.legacyPaneDraws==legacyP,"static retained section escaped into legacy submissions");
                packedLight(scene,scene.fixture.world.light);
                long uploads=PortholeRetained.uploads;for(int i=0;i<20;i++)scene.flush();need(PortholeRetained.uploads==uploads,"stationary portholes repeatedly uploaded");
                need(GL11.glGetError()==0,"static porthole GL failure");cases++;
            }
        }
    }
    private static int screen(){for(int i=0;i<ScreenHousingTextures.BUILTIN_COUNT;i++)if("Screens".equals(ScreenHousingTextures.category(i)))return i;throw new AssertionError("Screens fixture missing");}
    private static void dynamicAndFluids(GlassAuthoredAtlas atlas)throws Exception{
        for(int tint:new int[]{1,GlassTextures.OPAQUE})for(int mode:new int[]{0,2}){
            fixture="dynamic tint="+tint+" mode="+mode;
            try(Scene scene=new Scene(3,3,false,screen(),tint,mode,atlas.emissiveChoices.get(0))){
                CompiledChunk compiled=scene.settle();verify(scene,compiled,true);
                long h=PortholeRetained.housingDraws,legacyH=PortholeRetained.legacyHousingDraws,legacyP=PortholeRetained.legacyPaneDraws;
                render(scene,compiled);
                need(PortholeRetained.housingDraws==h+1,"dynamic pane prevented shared housing draw");
                need(PortholeRetained.legacyHousingDraws==legacyH,"dynamic pane drew housing a second time");
                need(PortholeRetained.legacyPaneDraws>legacyP,"dynamic pane was frozen or omitted");
                if(mode==2){
                    double[] uv=new double[4];net.minecraft.util.ResourceLocation texture=TEAnimatedScreenSelector.resolveDisplay(scene.tiles.get(0),screen(),true,true,1,uv);
                    double span=uv[1]-uv[0];int frames=span>0?(int)Math.round(1/span):0;
                    need(frames>1&&span<1,"animation loader silently fell back to one static frame");
                    need(!net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE.equals(texture)
                            &&GlassPanePipelineChecks.mc.getTextureManager().getTexture(texture)!=null,"dynamic glazing did not load its real animation texture");
                    System.out.println("Porthole dynamic texture PASS frames="+frames+" texture="+texture+" tint="+tint);
                }
                cases++;
            }
        }
        fixture="fluid admission";
        try(Scene scene=new Scene(4,4,false,-1,1,1,-1)){
            CompiledChunk compiled=scene.settle();TileEntityAnimatedScreenSelector wet=null;
            for(TileEntityAnimatedScreenSelector tile:scene.tiles)if(!compiled.getTileEntities().contains(tile)){wet=tile;break;}
            need(wet!=null,"fluid case has no covered non-anchor");
            NBTTagCompound fluid=new NBTTagCompound();fluid.setBoolean("Waterlogged",true);wet.fluids.read(fluid);scene.flush();
            verify(scene,compiled,false);need(wet.fluids.any()&&compiled.getTileEntities().contains(wet)&&wet.shouldRenderInPass(1),"retention hid independent contained fluid");
            ForgeHooksClient.setRenderPass(1);need(PortholeRetained.draw(wet,wet.getPos().getX(),wet.getPos().getY(),wet.getPos().getZ()),"fluid owner lost retained glass coverage");
            wet.fluids.read(new NBTTagCompound());scene.flush();verify(scene,compiled,false);need(!compiled.getTileEntities().contains(wet),"dry non-anchor kept stale fluid admission");cases++;
        }
    }
    private static void format(VertexFormat value){DefaultVertexFormats.BLOCK.clear();for(VertexFormatElement element:value.getElements())DefaultVertexFormats.BLOCK.addElement(element);}
    private static void shadowPolicy()throws Exception{
        java.lang.reflect.Field shadow;
        try{shadow=Class.forName("net.optifine.shaders.Shaders").getField("isShadowPass");}
        catch(ClassNotFoundException unavailable){System.out.println("PortholeRetainedPipeline shadow-policy NOT RUN: shader pass hook absent");return;}
        fixture="dynamic shadow policy framesOnly="+GlassPanePolicy.framesOnlyShadows();
        try(Scene scene=new Scene(3,3,false,screen(),1,0,-1)){
            CompiledChunk compiled=scene.settle();verify(scene,compiled,true);shadow.setBoolean(null,true);ForgeHooksClient.setRenderPass(1);
            long legacy=PortholeRetained.legacyPaneDraws;
            for(TileEntityAnimatedScreenSelector tile:scene.tiles){
                boolean handled=PortholeRetained.draw(tile,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ());
                need(handled==GlassPanePolicy.framesOnlyShadows()||owners().get(tile).emptyPane,"dynamic shadow omit policy disagrees with static pane policy");
                if(!handled)RENDERER.render(tile,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ(),0,-1,1);
            }
            need(GlassPanePolicy.framesOnlyShadows()?PortholeRetained.legacyPaneDraws==legacy:PortholeRetained.legacyPaneDraws>legacy,"dynamic shadow pane ignored configured shadow policy");cases++;
        }finally{shadow.setBoolean(null,false);ForgeHooksClient.setRenderPass(-1);}
    }
    private static void lifetime()throws Exception{
        fixture="stationary 16x16 lifetime";VertexFormat original=new VertexFormat(DefaultVertexFormats.BLOCK);
        try(Scene scene=new Scene(16,16,true,-1,1,1,-1)){
            CompiledChunk compiled=scene.settle();verify(scene,compiled,false);need(compiled.getTileEntities().size()<=2,"large joined aperture kept per-cell dispatcher admission");
            TileEntityAnimatedScreenSelector promoted=null;
            for(TileEntityAnimatedScreenSelector tile:scene.tiles)if(!compiled.getTileEntities().contains(tile)&&!owners().get(tile).emptyPane){promoted=tile;break;}
            need(promoted!=null,"anchor change fixture has no suppressed pane member");
            promoted.setGlassAppearance(GlassTextures.OPAQUE,false);scene.flush();
            need(scene.fixture.chunk.getCompiledChunk()==compiled,"appearance edit unexpectedly replaced compiled identity");
            need(PortholeRetained.anchor(promoted)&&compiled.getTileEntities().contains(promoted),"formerly suppressed owner failed promotion on same compiled chunk");verify(scene,compiled,false);
            promoted.setGlassAppearance(1,false);scene.flush();verify(scene,compiled,false);
            long uploads=PortholeRetained.uploads;scene.fixture.world.light=(96<<16)|80;scene.fixture.world.notifyLightSet(scene.tiles.get(0).getPos());scene.flush();
            need(PortholeRetained.uploads>uploads,"light change did not rebuild retained lightmap");packedLight(scene,scene.fixture.world.light);
            uploads=PortholeRetained.uploads;PortholeRetained.reload();scene.flush();need(PortholeRetained.uploads>uploads,"resource reload kept stale retained buffer");verify(scene,compiled,false);
            VertexFormat incompatible=new VertexFormat(BlockSurfaceFormat.get()).addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.BYTE,VertexFormatElement.EnumUsage.PADDING,8));
            format(incompatible);scene.flush();need(compiled.getTileEntities().size()==scene.tiles.size(),"unsupported format lost legacy admissions");
            for(TileEntityAnimatedScreenSelector tile:scene.tiles)need(!PortholeRetained.handles(tile),"unsupported format retained a porthole owner");
            format(original);scene.flush();verify(scene,compiled,false);
            scene.fixture.chunk.setPosition(32,0,0);scene.flush();need(compiled.getTileEntities().size()==scene.tiles.size(),"recycled render chunk kept old pruned members");
            for(TileEntityAnimatedScreenSelector tile:scene.tiles)need(!PortholeRetained.handles(tile),"off-section chunk still admits retained group");
            scene.fixture.chunk.setPosition(0,0,0);compiled=scene.fixture.compileAndUpload();scene.flush();verify(scene,compiled,false);
            TileEntityAnimatedScreenSelector removed=scene.tiles.get(0);BlockPos pos=removed.getPos();scene.fixture.world.states.remove(pos);scene.fixture.world.tiles.remove(pos);scene.fixture.world.loadedTileEntityList.remove(removed);removed.invalidate();
            scene.fixture.world.notifyBlockUpdate(pos,scene.state,net.minecraft.init.Blocks.AIR.getDefaultState(),3);scene.flush();
            need(!PortholeRetained.handles(removed),"removed anchor kept retained ownership");
            compiled=scene.fixture.compileAndUpload();scene.flush();verify(scene,compiled,false);
            need(GL11.glGetError()==0,"lifetime check left GL error");cases++;
        }finally{format(original);ForgeHooksClient.setRenderPass(-1);PortholeRetained.flush();}
        need(owners().isEmpty()&&PortholeRetained.diagnostics().contains("porthole-bytes=0"),"world unload retained owners or VBO bytes");
    }
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
        if(args.length>0&&"--frames-only".equals(args[0]))System.setProperty("vandorlabs.glassPaneShadows","frames");
        GlassPanePipelineChecks.initialize();GlassAuthoredAtlas atlas=new GlassAuthoredAtlas();VandorLabs.proxy=new ClientProxy();
        Map<Class<? extends TileEntity>,Object> renderers=(Map<Class<? extends TileEntity>,Object>)GlassPanePipelineChecks.field(TileEntityRendererDispatcher.class,"renderers").get(TileEntityRendererDispatcher.instance);
        RENDERER.setRendererDispatcher(TileEntityRendererDispatcher.instance);renderers.put(TileEntityAnimatedScreenSelector.class,RENDERER);
        if("shadow".equals(System.getProperty("vandorlabs.glassPipelineCase")))shadowPolicy();
        else{staticMaterials(atlas);dynamicAndFluids(atlas);shadowPolicy();lifetime();}
        System.out.println("PortholeRetainedPipeline PASS cases="+cases+" checks="+checks+" real RenderChunk/VBO/admissions, palette/custom x transparent/opaque x plain/emissive, dynamic pane only fallback, fluids, same-chunk anchor promotion, light/reload/format/recycle/removal/unload; "+PortholeRetained.diagnostics());
    }
    private PortholeRetainedPipelineChecks(){}
}
