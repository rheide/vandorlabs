package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** Exact vertex oracle for warm lists, mixed face overrides, storage sets and chunk workers. */
final class HousingModelChecks {
    static void run() {
        Map<String,TextureAtlasSprite> atlas=new ConcurrentHashMap<>();
        Function<String,TextureAtlasSprite> sprites=name->atlas.computeIfAbsent(name,key->{
            TextureAtlasSprite sprite=new TextureAtlasSprite(key){};
            sprite.setIconWidth(16);sprite.setIconHeight(16);
            int cell=Math.floorMod(key.hashCode(),128);sprite.initSprite(256,256,(cell%16)*16,(cell/16)*16,false);return sprite;
        });
        IBakedModel delegate=new IBakedModel() {
            public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){return Collections.emptyList();}
            public boolean isAmbientOcclusion(){return false;}public boolean isGui3d(){return true;}public boolean isBuiltInRenderer(){return false;}
            public TextureAtlasSprite getParticleTexture(){return sprites.apply("particle");}
            public ItemCameraTransforms getItemCameraTransforms(){return ItemCameraTransforms.DEFAULT;}
            public ItemOverrideList getOverrides(){return new ItemOverrideList(Collections.emptyList());}
        };
        int cases=0;
        for(int family=0;family<3;family++) {
            Block block=family==1?ModBlocks.PROGRAMMABLE_SLAB:ModBlocks.PROGRAMMABLE_BLOCK;
            ProgrammableHousingModel model=new ProgrammableHousingModel(delegate,family==1,family==2,sprites);
            ReferenceProgrammableHousingModel reference=new ReferenceProgrammableHousingModel(delegate,family==1,family==2,sprites);
            List<IBlockState> inputs=new ArrayList<>();List<List<BakedQuad>> expected=new ArrayList<>();
            for(IBlockState base:block.getBlockState().getValidStates())for(int mode=0;mode<4;mode++)for(int tiled=0;tiled<2;tiled++)for(int visible=0;visible<64;visible++) {
                IExtendedBlockState state=((IExtendedBlockState)base)
                        .withProperty(ProgrammableHousingState.FINISH,mode==0?0:ScreenHousingTextures.DEFAULT_STORAGE)
                        .withProperty(ProgrammableHousingState.SIDE_FINISH,mode==1?3:-1)
                        .withProperty(ProgrammableHousingState.FACES,mode<2?FaceTextures.DEFAULT:new FaceTextures(true,mode==2?new int[]{1,2,3,4,5,6}:new int[]{-1,-1,-1,-1,-1,-1}))
                        .withProperty(ProgrammableHousingState.TILE_SIDES,tiled).withProperty(ProgrammableHousingState.VISIBLE,visible);
                List<BakedQuad> golden=reference.getQuads(state,null,0);
                same(golden,model.getQuads(state,null,0));same(golden,model.getQuads(state,null,42));
                for(EnumFacing side:EnumFacing.values())require(model.getQuads(state,side,0).isEmpty(),"general faces duplicated as side faces");
                inputs.add(state);expected.add(golden);cases++;
            }
            ExecutorService workers=Executors.newFixedThreadPool(4);
            try {
                List<Future<?>> jobs=new ArrayList<>();
                for(int worker=0;worker<4;worker++) {
                    final int seed=worker;
                    jobs.add(workers.submit(()->{Random random=new Random(seed);for(int i=0;i<2000;i++){int at=random.nextInt(inputs.size());same(expected.get(at),model.getQuads(inputs.get(at),null,i));}}));
                }
                for(Future<?> job:jobs)job.get();
            } catch(Exception failure){throw new AssertionError("concurrent housing model",failure);} finally {workers.shutdownNow();}
            for(int choice=0;choice<1500;choice++) {
                IExtendedBlockState state=((IExtendedBlockState)block.getDefaultState()).withProperty(ProgrammableHousingState.FINISH,CustomBlockMaterials.ID_BASE+choice)
                        .withProperty(ProgrammableHousingState.VISIBLE,63);
                same(reference.getQuads(state,null,0),model.getQuads(state,null,0));
            }
            require(model.cachedFaceLists()<=ProgrammableHousingModel.MAX_FACE_LISTS,"unbounded common face lists");
        }
        System.out.println("PASS: "+cases+" exact housing model cases, mixed overrides/storage artwork, concurrent chunk workers and bounded face lists");
    }
    private static void same(List<BakedQuad> expected,List<BakedQuad> actual) {
        require(expected.size()==actual.size(),"housing quad count changed");
        for(int i=0;i<expected.size();i++) {
            BakedQuad a=expected.get(i),b=actual.get(i);
            require(a.getSprite()==b.getSprite() && a.getFace()==b.getFace() && a.getTintIndex()==b.getTintIndex()
                    && a.shouldApplyDiffuseLighting()==b.shouldApplyDiffuseLighting() && a.getFormat().equals(b.getFormat())
                    && Arrays.equals(a.getVertexData(),b.getVertexData()),"housing quad data or texture changed");
        }
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
