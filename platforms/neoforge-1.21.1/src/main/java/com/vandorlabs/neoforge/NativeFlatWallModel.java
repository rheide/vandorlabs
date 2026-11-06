package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.render.PanelDepthPolicy;
import com.vandorlabs.tiles.MaterialChoicePolicy;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Current plain wall body and canonical item; all worker inputs are immutable per-bake snapshots. */
final class NativeFlatWallModel extends BakedModelWrapper<BakedModel> {
    private static final class Layers {
        static final ChunkRenderTypeSet VALUE=ChunkRenderTypeSet.of(RenderType.cutout());
        static final List<RenderType> ITEM=List.of(net.minecraft.client.renderer.Sheets.translucentItemSheet());
    }
    private record Key(Object geometry,int main,int interior,int exterior,int mode){}
    private record ApertureKey(Object geometry,com.vandorlabs.neoforge.shape.NativePortholeLayout aperture,int main,int interior,int exterior,int mode){}
    private record PaneKey(Object geometry,com.vandorlabs.neoforge.shape.NativePortholeLayout aperture,int material,int shade,int tint,int layout,boolean standalone){}
    private record GlassKey(NativeDiagonalWallGeometry.Pose pose,NativeDiagonalGlassLayout layout,int material,int shade,int tint,int mode,boolean standalone){}
    private record GlassFrameKey(NativeDiagonalWallGeometry.Pose pose,int mask,int material,int shade,int front,int interior,int exterior){}
    private record ItemKey(BakedModel source,int main,boolean custom,Direction side){}
    static final class Bake {
        final Map<Integer,ProgrammableModel.MaterialSnapshot> surfaces;
        final Map<Integer,NativePanelQuads.Material> interiors;
        final NativePanelQuads.Material originalMetal,originalGlassRail;
        final Map<Integer,NativePanelQuads.Material> glassRails;
        private final NativePanelQuads.Material[][] palette=new NativePanelQuads.Material[com.vandorlabs.tiles.GlassTextures.IDS.length][4];
        private final com.vandorlabs.neoforge.client.NativeMaterialPlayback.Material[] playback;
        // Snapshot keys contain only immutable scalar settings and <=3 flat AABBs, never worlds/tiles.
        // Identity/FIFO avoids Guava's 24-byte access-queue node on each weighted-cache hit.
        private record SnapshotEntry(NativeWallEntity.WallAppearance key,int cost){}
        private final java.util.IdentityHashMap<NativeWallEntity.WallAppearance,PaneProjection> playbackPanes=new java.util.IdentityHashMap<>();
        private final java.util.ArrayDeque<SnapshotEntry> playbackOrder=new java.util.ArrayDeque<>();
        private int playbackBytes;
        final com.google.common.cache.Cache<Object,List<BakedQuad>> quads=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(16*1024*1024)
                .weigher((Object key,List<BakedQuad> values)->keyCost(key)+values.stream().mapToInt(quad->96+quad.getVertices().length*4).sum()).build();
        Bake(Map<Integer,ProgrammableModel.MaterialSnapshot> surfaces,Function<Material,TextureAtlasSprite> textures){
            this.surfaces=surfaces;Map<Integer,NativePanelQuads.Material> mapped=new HashMap<>();
            for(var entry:surfaces.entrySet()){
                ModernFinishCatalog.Frame frame=entry.getKey()<ModernFinishCatalog.ENTRIES.size()?ModernFinishCatalog.ENTRIES.get(entry.getKey()).frame():null;
                List<ProgrammableModel.MaterialSnapshot> edges=new ArrayList<>();if(frame!=null&&frame.border()&&frame.rail()!=null)for(int i=0;i<4;i++)edges.add(new ProgrammableModel.MaterialSnapshot(textures.apply(new Material(TextureAtlas.LOCATION_BLOCKS,ResourceLocation.parse(frame.rail()+(i==0?"":"_edge_"+i)))),1,1));
                mapped.put(entry.getKey(),new NativePanelQuads.Material(entry.getValue(),edges));
            }
            playback=new com.vandorlabs.neoforge.client.NativeMaterialPlayback.Material[ModernFinishCatalog.ENTRIES.size()];
            for(int i=0;i<playback.length;i++)playback[i]=com.vandorlabs.neoforge.client.NativeMaterialPlayback.capture(ModernFinishCatalog.ENTRIES.get(i));
            for(int shade=0;shade<palette.length;shade++)for(int tint=0;tint<4;tint++){
                String name=com.vandorlabs.tiles.GlassTextures.texture(shade,1,tint).substring("vandorlabs:blocks/programmable_glass/low/".length());
                var sprite=textures.apply(new Material(TextureAtlas.LOCATION_BLOCKS,ResourceLocation.fromNamespaceAndPath("vandorlabs","glass-palette/"+name)));
                palette[shade][tint]=new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(sprite,1,1));
            }
            Map<Integer,NativePanelQuads.Material> rails=new HashMap<>();for(var entry:mapped.entrySet()){
                var frame=entry.getKey()<ModernFinishCatalog.ENTRIES.size()?ModernFinishCatalog.ENTRIES.get(entry.getKey()).frame():null;var material=entry.getValue();
                rails.put(entry.getKey(),new NativePanelQuads.Material(material.edges().isEmpty()?material.surface():material.edges().getFirst(),material.edges(),frame!=null&&frame.authoredRail()));
            }
            glassRails=Map.copyOf(rails);originalGlassRail=new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(textures.apply(new Material(TextureAtlas.LOCATION_BLOCKS,ResourceLocation.parse("vandorlabs:frame-default/double_frame_metal"))),1,1));
            interiors=Map.copyOf(mapped);originalMetal=new NativePanelQuads.Material(new ProgrammableModel.MaterialSnapshot(textures.apply(new Material(TextureAtlas.LOCATION_BLOCKS,ResourceLocation.parse("vandorlabs:frame-default/metal_side"))),1,1));
        }
        BakedModel wrap(BakedModel original,boolean item){return wrap(original,item,false);}
        BakedModel wrap(BakedModel original,boolean item,boolean porthole){return wrap(original,item,porthole?NativePanelKind.PORTHOLE:NativePanelKind.WALL);}
        BakedModel wrap(BakedModel original,boolean item,NativePanelKind kind){return new NativeFlatWallModel(original,this,item,kind,null);}
        com.vandorlabs.neoforge.client.NativeMaterialPlayback.Material playback(int material){int index=ModernFinishCatalog.storedChoice(material);return material<0||index>=playback.length?null:playback[index];}
        static final class PaneProjection {
            private final List<BakedQuad> quads;private final com.vandorlabs.neoforge.client.NativeMaterialPlayback.Material material;private final ProgrammableModel.MaterialSnapshot source;private final boolean opaque;
            private volatile RenderType[] renderTypes;
            PaneProjection(List<BakedQuad> quads,com.vandorlabs.neoforge.client.NativeMaterialPlayback.Material material,ProgrammableModel.MaterialSnapshot source,boolean opaque){this.quads=quads;this.material=material;this.source=source;this.opaque=opaque;}
            List<BakedQuad> quads(){return quads;}com.vandorlabs.neoforge.client.NativeMaterialPlayback.Material material(){return material;}ProgrammableModel.MaterialSnapshot source(){return source;}
            boolean opaque(){return opaque;}
            RenderType renderType(int mode){var found=renderTypes;if(found==null){synchronized(this){found=renderTypes;if(found==null){boolean opaque=opaque();if(material==null){var atlas=NativePanelPlaybackRenderer.renderType(TextureAtlas.LOCATION_BLOCKS,opaque);found=new RenderType[]{atlas,atlas,atlas};}else found=new RenderType[]{NativePanelPlaybackRenderer.renderType(material.texture(0),opaque),NativePanelPlaybackRenderer.renderType(material.texture(1),opaque),NativePanelPlaybackRenderer.renderType(material.texture(2),opaque)};renderTypes=found;}}}return found[mode];}
        }
        synchronized PaneProjection paneProjection(NativeWallEntity.WallAppearance appearance){
            var found=playbackPanes.get(appearance);if(found!=null)return found;var display=playback(appearance.glass().material);
            var geometry=appearance.geometry();
            found=new PaneProjection(appearance.kind()==NativePanelKind.GLASS?glassPane(appearance,display!=null):pane(appearance,geometry,display!=null),display,surfaces.get(0),com.vandorlabs.render.GlassRenderPolicy.opaque(appearance.glass().tint));
            int cost=3072;for(var quad:found.quads())cost+=96+quad.getVertices().length*4;
            while(playbackBytes+cost>4*1024*1024&&!playbackOrder.isEmpty()){var oldest=playbackOrder.removeFirst();playbackPanes.remove(oldest.key);playbackBytes-=oldest.cost;}
            if(cost<=4*1024*1024){playbackPanes.put(appearance,found);playbackOrder.addLast(new SnapshotEntry(appearance,cost));playbackBytes+=cost;}return found;
        }
        private final java.util.IdentityHashMap<NativeWallEntity.WallAppearance,List<BakedQuad>> bodies=new java.util.IdentityHashMap<>();
        private final java.util.ArrayDeque<SnapshotEntry> bodyOrder=new java.util.ArrayDeque<>();
        private int bodyBytes;
        synchronized List<BakedQuad> bodyProjection(NativeWallEntity.WallAppearance appearance){
            var found=bodies.get(appearance);if(found!=null)return found;
            var geometry=appearance.geometry();found=appearance.kind()==NativePanelKind.GLASS?glassFrame(appearance):appearance.kind()==NativePanelKind.PORTHOLE?aperture(appearance,geometry):body(geometry,appearance.base().housing().mainTexture,appearance.frames().interior,appearance.frames().exteriorChoice(),appearance.base().housing().layout);
            // Precharge both possible lazy analytic surface tables as well as boxes and snapshot settings.
            int cost=65536+96*(geometry.boxes().size()+geometry.fluidBoxes().size());for(var quad:found)cost+=96+quad.getVertices().length*4;
            while(bodyBytes+cost>4*1024*1024&&!bodyOrder.isEmpty()){var oldest=bodyOrder.removeFirst();bodies.remove(oldest.key);bodyBytes-=oldest.cost;}
            if(cost<=4*1024*1024){bodies.put(appearance,found);bodyOrder.addLast(new SnapshotEntry(appearance,cost));bodyBytes+=cost;}return found;
        }
        private NativePanelQuads.Material material(int index){return index<0?originalMetal:interiors.get(index);}
        List<BakedQuad> aperture(NativeWallEntity.WallAppearance appearance,NativeWallGeometry geometry){
            int main=index(appearance.base().housing().mainTexture),fallback=appearance.base().sideTexture()<0?-1:index(appearance.base().sideTexture());
            int interior=appearance.frames().interior<0?fallback:index(appearance.frames().interior),exterior=appearance.frames().exteriorChoice()<0?fallback:index(appearance.frames().exteriorChoice()),mode=appearance.base().housing().layout;
            var aperture=appearance.aperture();ApertureKey key=new ApertureKey(descriptor(geometry),aperture,main,interior,exterior,mode);var found=quads.getIfPresent(key);if(found!=null)return found;
            var wall=new NativePanelQuads.Material(surfaces.get(main));
            if(geometry instanceof NativeDiagonalWallGeometry.Geometry diagonal){
                var pose=diagonal.pose();
                if(aperture.solid())found=body(geometry,main,exterior,exterior,mode);
                else{
                    var projection=projection(pose);
                    found=NativePanelQuads.diagonalAperture(pose.facing(),projection,NativePanelSlice.get(aperture),wall,material(interior),material(exterior),mode,pose.layout(),aperture.hideLeft(),aperture.hideRight(),aperture.hideLower(),aperture.hideUpper(),4);
                    if(projection.corner!=null){var combined=new ArrayList<BakedQuad>(found);combined.addAll(NativePanelQuads.diagonalArms(pose,wall,material(exterior),mode,pose.layout()));found=List.copyOf(combined);}
                }
            }else{
                var flat=(NativeFlatWallGeometry.Geometry)geometry;var owner=NativeFlatBarOwner.from(flat);
                found=owner==null&&flat.join()!=null?NativePanelQuads.bentBody(flat,aperture,wall,material(interior),material(exterior),mode):owner==null?NativePanelQuads.aperture(flat.facing(),flat.fullDepth()?0:PanelDepthPolicy.start(flat.depth()),flat.fullDepth()?4:1,NativePanelSlice.get(aperture),wall,material(interior),material(exterior),mode,flat.layout(),aperture.hideLeft(),aperture.hideRight(),aperture.hideLower(),aperture.hideUpper(),flat.fullDepth()?16:4):NativePanelQuads.barBody(owner,flat,aperture,wall,material(interior),material(exterior));
            }
            quads.put(key,found);return found;
        }
        private static Object descriptor(NativeWallGeometry geometry){return geometry instanceof NativeDiagonalWallGeometry.Geometry diagonal?diagonal.pose():geometry;}
        private static com.vandorlabs.render.DiagonalPortholeProjection projection(NativeDiagonalWallGeometry.Pose pose){return new com.vandorlabs.render.DiagonalPortholeProjection(pose.mode(),pose.inverted(),pose.lowerEnd(),pose.upperEnd(),pose.clip().scaled(16),pose.mode()==1&&pose.join()!=null?pose.join().corner():null);}
        List<BakedQuad> pane(NativeWallEntity.WallAppearance appearance,NativeWallGeometry geometry,boolean standalone){
            if(appearance.aperture().solid())return List.of();
            var glass=appearance.glass();var display=playback(glass.material);int visual=glass.material<0?-1:standalone?ModernFinishCatalog.storedChoice(glass.material):index(glass.material);
            PaneKey key=new PaneKey(descriptor(geometry),appearance.aperture(),visual,glass.shade,glass.tint,glass.material<0?0:glass.textureLayout(),standalone);var found=quads.getIfPresent(key);if(found!=null)return found;
            NativePanelQuads.Material material=glass.material<0?palette[glass.shade][glass.tint]:new NativePanelQuads.Material(surfaces.get(standalone?0:visual));
            var aperture=appearance.aperture();var layout=glass.material<0?NativePanelLayout.UNIT:aperture.pane();
            float opacity=com.vandorlabs.render.GlassRenderPolicy.opacity(glass.material,glass.tint);
            int mode=glass.material<0?0:glass.textureLayout();double bx=glass.material<0?0:(2+aperture.extraBorder())/16D,by=glass.material<0?0:(4+aperture.extraBorder())/16D,aspect=standalone&&display!=null?display.aspect():0;
            if(geometry instanceof NativeDiagonalWallGeometry.Geometry diagonal){var pose=diagonal.pose();found=NativePanelQuads.diagonalPane(pose.facing(),projection(pose),NativePanelSlice.get(aperture),material,mode,layout,bx,by,opacity,aspect);}
            else{var flat=(NativeFlatWallGeometry.Geometry)geometry;var owner=NativeFlatBarOwner.from(flat);
                found=owner==null&&flat.join()!=null?NativePanelQuads.bentPane(flat,aperture,material,mode,layout,bx,by,opacity,aspect):owner==null?NativePanelQuads.pane(flat.facing(),flat.fullDepth()?0:PanelDepthPolicy.start(flat.depth()),flat.fullDepth()?4:1,NativePanelSlice.get(aperture),material,mode,layout,bx,by,opacity,aspect):NativePanelQuads.barPane(owner,aperture,material,mode,layout,bx,by,opacity,aspect);
            }
            quads.put(key,found);return found;
        }

        private NativePanelQuads.Material glassMaterial(NativeWallEntity.WallAppearance appearance,boolean standalone){var glass=appearance.glass();return glass.material<0?palette[glass.shade][glass.tint]:new NativePanelQuads.Material(surfaces.get(standalone?0:index(glass.material)));}
        List<BakedQuad> glassPane(NativeWallEntity.WallAppearance appearance,boolean standalone){
            var pose=((NativeDiagonalWallGeometry.Geometry)appearance.geometry()).pose();var glass=appearance.glass();var descriptor=appearance.diagonalGlass();int visual=glass.material<0?-1:standalone?ModernFinishCatalog.storedChoice(glass.material):index(glass.material),mode=glass.material<0?0:glass.textureLayout();
            var key=new GlassKey(pose,descriptor,visual,glass.shade,glass.tint,mode,standalone);var found=quads.getIfPresent(key);if(found!=null)return found;var display=playback(glass.material);
            found=NativePanelQuads.diagonalGlassBody(pose,glassMaterial(appearance,standalone),mode,glass.material<0?NativePanelLayout.UNIT:descriptor.group(),descriptor.hideMask(),com.vandorlabs.render.GlassRenderPolicy.opacity(glass.material,glass.tint),standalone&&display!=null?display.aspect():0);quads.put(key,found);return found;
        }
        List<BakedQuad> glassFrame(NativeWallEntity.WallAppearance appearance){
            if(!appearance.glass().framed)return List.of();var pose=((NativeDiagonalWallGeometry.Geometry)appearance.geometry()).pose();var frames=appearance.frames();int front=frames.front<0?-1:index(frames.front),inner=frames.interior<0?-1:index(frames.interior),outer=frames.exteriorChoice()<0?-1:index(frames.exteriorChoice());
            var key=new GlassFrameKey(pose,appearance.diagonalGlass().hideMask(),appearance.glass().material<0?-1:index(appearance.glass().material),appearance.glass().shade,front,inner,outer);var found=quads.getIfPresent(key);if(found!=null)return found;
            found=NativePanelQuads.diagonalGlassFrame(pose,glassMaterial(appearance,false),front<0?originalGlassRail:glassRails.get(front),material(inner),material(outer),appearance.diagonalGlass().hideMask());quads.put(key,found);return found;
        }
        List<BakedQuad> body(NativeWallGeometry geometry,int main,int interior,int mode){return body(geometry,main,interior,interior,mode);}
        List<BakedQuad> body(NativeWallGeometry geometry,int main,int interior,int exterior,int mode){
            var owner=geometry instanceof NativeFlatWallGeometry.Geometry flat?NativeFlatBarOwner.from(flat):null;
            int body=index(main),edge=interior<0?-1:index(interior),outer=owner==null?edge:exterior<0?-1:index(exterior);
            // Diagonal descriptors do not retain collision lists or lazy fluid tables in mesh keys.
            Object descriptor=geometry instanceof NativeDiagonalWallGeometry.Geometry diagonal?diagonal.pose():geometry;
            Key key=new Key(descriptor,body,edge,outer,owner==null?mode:0);var found=quads.getIfPresent(key);if(found!=null)return found;
            var wall=new NativePanelQuads.Material(surfaces.get(body));var metal=edge<0?originalMetal:interiors.get(edge);List<BakedQuad> result;
            if(geometry instanceof NativeFlatWallGeometry.Geometry flat){var join=flat.join();result=owner==null?NativePanelQuads.flat(flat.facing(),PanelDepthPolicy.start(flat.depth()),join==null?null:join.corner(),wall,metal,mode,flat.layout()):NativePanelQuads.barBody(owner,flat,null,wall,metal,material(outer));}
            else if(geometry instanceof NativeDiagonalWallGeometry.Geometry diagonal){var pose=diagonal.pose();boolean half=pose.mode()==2;var join=pose.join();
                result=NativePanelQuads.diagonal(pose.facing(),pose.inverted(),join==null?null:join.corner(),pose.mode()==1?16:8,pose.fill(),half&&pose.inverted()?-8:0,half&&pose.inverted()?8:16,half?(pose.inverted()?8:0):Double.NaN,
                        pose.lowerEnd()*16,pose.upperEnd()*16,pose.hideLower(),pose.hideUpper(),pose.clip().scaled(16),wall,metal,mode,pose.layout());
            }else throw new IllegalArgumentException("Unknown panel geometry");
            quads.put(key,result);return result;
        }
        private static int keyCost(Object key){Object descriptor=key instanceof Key value?value.geometry:key instanceof ApertureKey value?value.geometry:key instanceof PaneKey value?value.geometry:key instanceof GlassKey value?value.pose:key instanceof GlassFrameKey value?value.pose:null;return descriptor instanceof NativeDiagonalWallGeometry.Pose pose?1024+pose.clip().size()*8:384;}
        int index(int choice){return ProgrammableModel.renderIndex(surfaces,choice);}
    }
    private static final class Defaults {
        static final NativeWallEntity.WallAppearance[] VALUES=new NativeWallEntity.WallAppearance[36];
        static NativeWallEntity.WallAppearance diagonal(NativeWallGeometry geometry){var base=VALUES[6];return new NativeWallEntity.WallAppearance(base.base(),base.walls(),base.frames(),base.glass(),geometry,NativePortholeLayout.unit(0),PanelPlayback.DEFAULT,true,NativePanelKind.PORTHOLE);}
        static {
            var base=new ProgrammableShapeEntity.Appearance(new com.vandorlabs.ui.ProgrammableBlockDialog.State(0,0,false,new int[]{-1,-1,-1,-1,-1,-1},-1),-1,new com.vandorlabs.persistence.StairState(8,0));
            var walls=new com.vandorlabs.persistence.WallSettings(false,false,0,0,true,0);var frames=new com.vandorlabs.persistence.FrameSettings(-1,-1,-2,0,-1,-1,false,false,false,false);var glass=com.vandorlabs.persistence.PanelGlassSettings.fresh();
            for(Direction face:Direction.Plane.HORIZONTAL)for(int depth=0;depth<3;depth++)for(int full=0;full<2;full++){
                var geometry=full==0?NativeFlatWallGeometry.straight(face,depth):NativeFlatWallGeometry.full(face,depth);
                VALUES[full*18+face.get3DDataValue()*3+depth]=new NativeWallEntity.WallAppearance(base,walls,frames,glass,geometry,NativePortholeLayout.unit(0,full));
            }
        }
    }
    private final Bake bake;private final boolean item;private final NativePanelKind kind;private final ItemChoice choice;
    private NativeFlatWallModel(BakedModel original,Bake bake,boolean item,NativePanelKind kind,ItemChoice choice){super(original);this.bake=bake;this.item=item;this.kind=kind;this.choice=choice;}
    @Override public ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level,net.minecraft.core.BlockPos pos,BlockState state,ModelData data){return data;}
    @Override public TextureAtlasSprite getParticleIcon(){return getParticleIcon(ModelData.EMPTY);}
    @Override public TextureAtlasSprite getParticleIcon(ModelData data){var appearance=data.get(NativeWallEntity.WALL_APPEARANCE);if(kind==NativePanelKind.GLASS){if(!item&&appearance!=null)return bake.glassMaterial(appearance,false).surface().sprite();if(!item||choice==null||!choice.custom)return originalModel.getParticleIcon(data);}int main=item?(choice==null?0:choice.main):appearance==null?0:appearance.base().housing().mainTexture;return bake.surfaces.get(bake.index(main)).sprite();}
    @Override public boolean useAmbientOcclusion(){return false;}
    @Override public net.neoforged.neoforge.common.util.TriState useAmbientOcclusion(BlockState state,ModelData data,RenderType layer){return net.neoforged.neoforge.common.util.TriState.FALSE;}
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data){return Layers.VALUE;}
    @Override public List<RenderType> getRenderTypes(net.minecraft.world.item.ItemStack stack,boolean fabulous){return item&&kind!=NativePanelKind.WALL?Layers.ITEM:super.getRenderTypes(stack,fabulous);}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){
        if(item)return itemQuads(state,side,random,data,layer);if(side!=null)return List.of();var appearance=data.get(NativeWallEntity.WALL_APPEARANCE);
        if(state.getBlock() instanceof NativeDiagonalWallBlock block){
            if(block.isGlass())return List.of();
            NativeWallGeometry geometry=appearance!=null&&appearance.geometry() instanceof NativeDiagonalWallGeometry.Geometry?appearance.geometry():NativeDiagonalWallGeometry.build(NativeDiagonalWallTopology.initial(state,0));
            if(appearance!=null&&!appearance.chunkBody())return List.of();
            if(kind==NativePanelKind.PORTHOLE){var snapshot=appearance==null?Defaults.diagonal(geometry):appearance;return bake.aperture(snapshot,geometry);}
            return bake.body(geometry,appearance==null?0:appearance.base().housing().mainTexture,appearance==null?-1:appearance.frames().interior,appearance==null?-1:appearance.frames().exteriorChoice(),appearance==null?0:appearance.base().housing().layout);
        }
        Direction facing=state.getValue(NativeFlatWallBlock.FACING);int depth=state.getValue(NativeFlatWallBlock.DEPTH);boolean full=state.getBlock() instanceof NativeFlatWallBlock block&&block.isFullDepth();Object published=appearance==null?null:appearance.geometry();NativeFlatWallGeometry.Geometry geometry=published instanceof NativeFlatWallGeometry.Geometry value?value:null;if(geometry==null||geometry.facing()!=facing||geometry.depth()!=depth||geometry.fullDepth()!=full)geometry=full?NativeFlatWallGeometry.full(facing,depth):NativeFlatWallGeometry.straight(facing,depth);
        if(kind==NativePanelKind.PORTHOLE)return bake.aperture(appearance==null?Defaults.VALUES[(full?18:0)+facing.get3DDataValue()*3+depth]:appearance,geometry);
        return bake.body(geometry,appearance==null?0:appearance.base().housing().mainTexture,appearance==null?-1:appearance.frames().interior,appearance==null?-1:appearance.frames().exteriorChoice(),appearance==null?0:appearance.base().housing().layout);
    }
    List<BakedQuad> worldQuads(NativeWallGeometry geometry,int main,int interior,int mode){return bake.body(geometry,main,interior,mode);}
    private List<BakedQuad> itemQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){
        var source=originalModel.getQuads(state,side,random,data,layer);if(choice==null||bake.index(choice.main)==0&&!choice.custom)return source;
        ItemKey key=new ItemKey(originalModel,bake.index(choice.main),choice.custom,side);boolean cache=NativeAuthoredControlBake.isStatic(originalModel);var found=cache?bake.quads.getIfPresent(key):null;if(found!=null)return found;
        var target=bake.surfaces.get(bake.index(choice.main));var original=bake.surfaces.get(0).sprite();List<BakedQuad> result=new ArrayList<>(source.size());for(var quad:source){if(!choice.custom&&quad.getSprite()!=original){result.add(quad);continue;}int[] packed=quad.getVertices().clone();int stride=packed.length/4;var old=quad.getSprite();for(int i=0;i<4;i++){float u=(Float.intBitsToFloat(packed[i*stride+4])-old.getU0())/(old.getU1()-old.getU0())*16,v=(Float.intBitsToFloat(packed[i*stride+5])-old.getV0())/(old.getV1()-old.getV0())*16;packed[i*stride+4]=Float.floatToRawIntBits(NativeHousingQuads.atlas(target,u,true));packed[i*stride+5]=Float.floatToRawIntBits(NativeHousingQuads.atlas(target,v,false));}result.add(new BakedQuad(packed,quad.getTintIndex(),quad.getDirection(),target.sprite(),quad.isShade(),quad.hasAmbientOcclusion()));}found=List.copyOf(result);if(cache)bake.quads.put(key,found);return found;
    }
    // Legacy item mesh selection requires an exact IntTag for builtins; its later custom-material
    // override accepts all numeric tags and recolors caps too. Other wall settings remain dormant.
    private record ItemChoice(int main,boolean custom){}
    private static final com.mojang.serialization.MapDecoder<ItemChoice> ITEM=new com.mojang.serialization.MapDecoder.Implementation<>(){
        @Override public <T> java.util.stream.Stream<T> keys(com.mojang.serialization.DynamicOps<T> ops){return java.util.stream.Stream.of("housingTexture","BlockEntityTag").map(ops::createString);}
        @Override public <T> com.mojang.serialization.DataResult<ItemChoice> decode(com.mojang.serialization.DynamicOps<T> ops,com.mojang.serialization.MapLike<T> input){if(input.get("BlockEntityTag")!=null){var nested=ops.getMap(input.get("BlockEntityTag")).result();if(nested.isPresent())input=nested.get();}Object value=input.get("housingTexture");int raw=value instanceof NumericTag tag?tag.getAsInt():0;boolean custom=raw>=MaterialChoicePolicy.CUSTOM_BASE;return com.mojang.serialization.DataResult.success(new ItemChoice(custom||value instanceof IntTag?raw:0,custom));}
    };
    private final com.google.common.cache.Cache<net.minecraft.world.item.component.CustomData,BakedModel> items=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(128).build();
    BakedModel resolveItem(net.minecraft.world.item.component.CustomData data){var found=items.getIfPresent(data);if(found==null){found=new NativeFlatWallModel(originalModel,bake,true,kind,data.read(ITEM).getOrThrow());items.put(data,found);}return found;}
    private final ItemOverrides overrides=new ItemOverrides(){@Override public BakedModel resolve(BakedModel original,net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel world,net.minecraft.world.entity.LivingEntity entity,int seed){var custom=stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);if(custom==null)custom=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);return custom==null?NativeFlatWallModel.this:resolveItem(custom);}};
    @Override public ItemOverrides getOverrides(){return item?overrides:originalModel.getOverrides();}
    @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){getTransforms().getTransform(context).apply(left,pose);return this;}
    private final List<BakedModel> passes=List.of(this);
    @Override public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack,boolean fabulous){return passes;}
}
