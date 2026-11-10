package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Compare real FaceBakery corners and vanilla AO output, without a GL context. */
final class HousingFaceLightingChecks {
    private static final BlockPos POS = new BlockPos(8,100,8);

    static void run() {
        TextureAtlasSprite sprite = new TextureAtlasSprite("face_light_fixture") {};
        sprite.setIconWidth(16); sprite.setIconHeight(16); sprite.initSprite(16,16,0,0,false);
        positionalSamples(sprite);
        smoothCorners(sprite);
    }

    private static IBakedModel model(TextureAtlasSprite sprite, List<BakedQuad> quads) {
        return new IBakedModel() {
            public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long seed) {
                return side == null ? quads : Collections.emptyList();
            }
            public boolean isAmbientOcclusion() { return true; }
            public boolean isGui3d() { return true; }
            public boolean isBuiltInRenderer() { return false; }
            public TextureAtlasSprite getParticleTexture() { return sprite; }
            public ItemCameraTransforms getItemCameraTransforms() { return ItemCameraTransforms.DEFAULT; }
            public ItemOverrideList getOverrides() { return ItemOverrideList.NONE; }
        };
    }

    private static void positionalSamples(TextureAtlasSprite sprite) {
        NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(true) {
            public int getCombinedLight(BlockPos p, int minimum) {
                int level = p.equals(POS.east()) ? 15 : p.equals(POS.north()) ? 8 : p.equals(POS.up()) ? 12 : 6;
                return (Math.max(level,minimum)<<4) | (5<<20);
            }
        };
        int cases = 0;
        for (boolean slab : new boolean[]{false,true}) {
            Block block = slab ? new BlockProgrammableSlab() : new BlockProgrammableBlock();
            ProgrammableHousingModel housing = new ProgrammableHousingModel(model(sprite,Collections.emptyList()),slab,false,name->sprite);
            need(housing.isAmbientOcclusion(),"housing did not opt into smooth lighting");
            for (IBlockState state : block.getBlockState().getValidStates()) {
                world.setBlockState(POS,state,2);
                IBlockState extended = block.getExtendedState(state,world,POS);
                for (EnumFacing face : EnumFacing.values()) {
                    BlockPos sample = POS.offset(face);
                    int expected = Blocks.STONE.getDefaultState().getPackedLightmapCoords(world,sample);
                    int actual = block.getPackedLightmapCoords(extended,world,sample);
                    need(expected==actual,"side light bleeds into visible face: slab="+slab+" sample="+face);
                    cases++;
                }
            }
        }
        System.out.println("housing-positional-lighting PASS samples="+cases+" bright-side negative control");
    }

    private static void smoothCorners(TextureAtlasSprite sprite) {
        // Both components vary asymmetrically; an incorrectly rotated gradient
        // cannot pass because all four corners happen to have the same light.
        NonRenderingChecks.MemoryWorld world = new NonRenderingChecks.MemoryWorld(true) {
            public int getCombinedLight(BlockPos p, int minimum) {
                int x=p.getX()-POS.getX(), y=p.getY()-POS.getY(), z=p.getZ()-POS.getZ();
                int block=2+Math.floorMod(3*x+5*y+7*z,13), sky=1+Math.floorMod(7*x+2*y+3*z,15);
                return (Math.max(block,minimum)<<4) | (sky<<20);
            }
        };
        world.setBlockState(POS.up().west().north(),Blocks.STONE.getDefaultState(),2);
        BlockModelRenderer renderer = new BlockModelRenderer(new BlockColors());
        IBakedModel delegate = model(sprite,Collections.emptyList());
        int faces=0, gradients=0, negativeControls=0, joined=0, formats=0;
        for (int family=0; family<3; family++) {
            boolean slab=family==1, storage=family==2;
            Block block=slab ? new BlockProgrammableSlab()
                    : storage ? new BlockProgrammableStorage() : new BlockProgrammableBlock();
            ProgrammableHousingModel housing=new ProgrammableHousingModel(delegate,slab,storage,name->sprite);
            need(housing.isAmbientOcclusion(),"housing family did not opt into smooth lighting: "+family);
            for (IBlockState state : block.getBlockState().getValidStates()) {
                world.setBlockState(POS,state,2);
                IBlockState extended=block.getExtendedState(state,world,POS);
                for (BakedQuad quad : housing.getQuads(extended,null,0)) {
                    BakedQuad reference=HousingShaderLightingChecks.reference(quad);
                    canonical(reference,quad);
                    int[] expected=lit(renderer,world,extended,reference), actual=lit(renderer,world,extended,quad);
                    need(Arrays.equals(expected,actual),"AO corner payload differs from FaceBakery family="+family+" state="+state+" face="+quad.getFace());
                    if (distinctLight(expected)) gradients++;
                    // An AO-only fix with a cyclically displaced top/bottom
                    // start vertex must fail this same rendered-light oracle.
                    if (quad.getFace().getAxis()==EnumFacing.Axis.Y) {
                        int[] rotated=quad.getVertexData().clone(); int stride=rotated.length/4;
                        System.arraycopy(quad.getVertexData(),stride,rotated,0,3*stride);
                        System.arraycopy(quad.getVertexData(),0,rotated,3*stride,stride);
                        BakedQuad wrong=new BakedQuad(rotated,-1,quad.getFace(),sprite,false,quad.getFormat());
                        if (!Arrays.equals(expected,lit(renderer,world,extended,wrong))) negativeControls++;
                    }
                    for (int layout=0; layout<3; layout++) {
                        HousingFaceLayouts.Plane plane=new HousingFaceLayouts.Plane(new int[]{-1,-2,-1},new int[]{3,4,3});
                        for (BakedQuad mapped : ProgrammableHousingModel.mapJoined(quad,plane,layout,sprite)) {
                            canonical(reference,mapped);
                            need(Arrays.equals(expected,lit(renderer,world,extended,mapped)),"joined UV layout moved an AO corner");
                            joined++;
                        }
                    }
                    faces++;
                }
                VertexFormat expanded=new VertexFormat(DefaultVertexFormats.BLOCK)
                        .addElement(DefaultVertexFormats.NORMAL_3B).addElement(DefaultVertexFormats.PADDING_1B)
                        .addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.GENERIC,4));
                final VertexFormat[] live={expanded};
                ProgrammableHousingModel shaderModel=new ProgrammableHousingModel(delegate,slab,storage,name->sprite,()->live[0]);
                List<BakedQuad> wide=shaderModel.getQuads(extended,null,0);
                for (BakedQuad quad : wide) {
                    canonical(HousingShaderLightingChecks.reference(quad),quad);
                    need(quad.getVertexData().length==4*expanded.getIntegerSize(),"expanded shader records truncated");
                    int normal=expanded.getNormalOffset()/4, stride=expanded.getIntegerSize();
                    int packed=((quad.getFace().getFrontOffsetX()*127)&255)
                            | (((quad.getFace().getFrontOffsetY()*127)&255)<<8)
                            | (((quad.getFace().getFrontOffsetZ()*127)&255)<<16);
                    for(int vertex=0;vertex<4;vertex++)need((quad.getVertexData()[vertex*stride+normal]&0xffffff)==packed,"normal detached from canonical corner");
                    formats++;
                }
                live[0]=DefaultVertexFormats.BLOCK;
                for(BakedQuad quad:shaderModel.getQuads(extended,null,0)) {
                    canonical(HousingShaderLightingChecks.reference(quad),quad);
                    need(quad.getVertexData().length==4*DefaultVertexFormats.BLOCK.getIntegerSize(),"shader format switch retained stale records");
                }
            }
        }
        need(gradients>0 && negativeControls>0,"lighting fixture cannot reject displaced floor corners");
        System.out.println("housing-face-lighting PASS faces="+faces+" gradients="+gradients+" rejected-displaced-floors="+negativeControls
                +" joined="+joined+" expanded-format="+formats+" actual-FaceBakery and vanilla-AO cubes/slab-halves/storage all-facing");
    }

    private static void canonical(BakedQuad reference, BakedQuad actual) {
        int[] a=reference.getVertexData(), b=actual.getVertexData(); int sa=a.length/4,sb=b.length/4;
        need(reference.getFace()==actual.getFace(),"declared face differs from geometric face");
        for(int vertex=0;vertex<4;vertex++)for(int axis=0;axis<3;axis++)
            need(Math.abs(Float.intBitsToFloat(a[vertex*sa+axis])-Float.intBitsToFloat(b[vertex*sb+axis]))<1e-6,
                    "noncanonical AO corner face="+actual.getFace()+" vertex="+vertex+" axis="+axis);
    }

    /** Position, color and light at sorted physical corners; omit artwork UVs. */
    private static int[] lit(BlockModelRenderer renderer, NonRenderingChecks.MemoryWorld world, IBlockState state, BakedQuad quad) {
        BufferBuilder buffer=new BufferBuilder(256); buffer.begin(7,DefaultVertexFormats.BLOCK);
        need(renderer.renderModelSmooth(world,model(quad.getSprite(),Collections.singletonList(quad)),state,POS,buffer,false,0),"empty AO render");
        buffer.finishDrawing();
        return HousingShaderLightingChecks.physicalLight(buffer);
    }

    private static boolean distinctLight(int[] vertices) {
        for(int vertex=1;vertex<4;vertex++)if(vertices[vertex*5+4]!=vertices[4])return true;
        return false;
    }

    private static void need(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
