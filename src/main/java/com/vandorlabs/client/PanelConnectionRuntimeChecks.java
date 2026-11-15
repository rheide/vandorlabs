package com.vandorlabs.client;

import com.vandorlabs.blocks.LoadedPlaneConnections;
import com.vandorlabs.blocks.PanelPlane;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.util.HashSet;
import java.util.Set;

/** Traversal safety contracts without asking the world to load a chunk. */
final class PanelConnectionRuntimeChecks {
    private PanelConnectionRuntimeChecks() { }
    static void checkPackedLighting(net.minecraft.entity.player.EntityPlayer player) {
        BlockPos pos=new BlockPos(40,240,40);
        player.world.setBlockState(pos,com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK.getDefaultState(),2);
        try {
            com.vandorlabs.tiles.TileEntityAnimatedScreenSelector tile=
                    (com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)player.world.getTileEntity(pos);
            int light=TEAnimatedScreenSelector.neighborLight(tile);
            require((light >>> 16)!=(light & 65535),"lighting fixture needs unequal sky/block values");
            for(net.minecraft.client.renderer.vertex.VertexFormat format:new net.minecraft.client.renderer.vertex.VertexFormat[]{
                    net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK,BlockSurfaceFormat.get()}) {
                net.minecraft.client.renderer.BufferBuilder buffer=new net.minecraft.client.renderer.BufferBuilder(4096);
                buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,format);
                ProgrammableSolidRenderer.emit(tile,0,0,0,buffer);
                require(buffer.getVertexCount()==24,"cube must emit six quads");buffer.finishDrawing();
                java.nio.ByteBuffer data=buffer.getByteBuffer();int stride=format.getNextOffset(),lm=format.getUvOffsetById(1);
                for(int i=0;i<24;i++) {
                    require((data.getShort(i*stride+lm)&65535)==(light & 65535),"packed block light is wrong");
                    require((data.getShort(i*stride+lm+2)&65535)==(light >>> 16),"packed sky light is wrong");
                    if(i%4==0) {
                        float ax=data.getFloat(i*stride),ay=data.getFloat(i*stride+4),az=data.getFloat(i*stride+8);
                        float bx=data.getFloat((i+1)*stride),by=data.getFloat((i+1)*stride+4),bz=data.getFloat((i+1)*stride+8);
                        float cx=data.getFloat((i+2)*stride),cy=data.getFloat((i+2)*stride+4),cz=data.getFloat((i+2)*stride+8);
                        float nx=(by-ay)*(cz-az)-(bz-az)*(cy-ay),ny=(bz-az)*(cx-ax)-(bx-ax)*(cz-az),nz=(bx-ax)*(cy-ay)-(by-ay)*(cx-ax);
                        require((ax-.5)*nx+(ay-.5)*ny+(az-.5)*nz>0,"batched cube winding points inward");
                        if(format.hasNormal()) {
                            int normal=i*stride+format.getNormalOffset();
                            require(nx*data.get(normal)+ny*data.get(normal+1)+nz*data.get(normal+2)>0,"batched shader normal disagrees with winding");
                        }
                    }
                }
            }
            System.out.println("[vandorlabs][reprolab] batched-lighting-inputs PASS: base and normal-bearing formats");
        } finally {
            player.world.setBlockToAir(pos);
        }
    }
    static void checkBakedHousing(net.minecraft.entity.player.EntityPlayer player) {
        net.minecraft.world.World world = player.world;
        BlockPos pos = new BlockPos(40,240,40), east = pos.east();
        try {
            world.setBlockState(pos,com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),2);
            require(renderedVertices(world,pos)==24,"baked cube is missing faces");
            require(!world.getTileEntity(pos).shouldRenderInPass(0),
                    "baked cube still submits an empty tile renderer");
            world.setBlockState(east,net.minecraft.init.Blocks.STONE.getDefaultState(),2);
            require(renderedVertices(world,pos)==20,"solid neighbor did not hide cube face");
            world.setBlockToAir(east);
            net.minecraft.block.state.IBlockState bottom = com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_SLAB
                    .getDefaultState();
            world.setBlockState(pos,bottom,2);
            require(renderedVertices(world,pos)==24,"baked bottom slab is missing faces");
            require(!world.getTileEntity(pos).shouldRenderInPass(0),
                    "baked slab still submits an empty tile renderer");
            com.vandorlabs.tiles.TileEntityAnimatedScreenSelector slabTile=(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
            slabTile.setHousingTexture(2);slabTile.setSideTexture(5);
            for(net.minecraft.block.BlockSlab.EnumBlockHalf half:net.minecraft.block.BlockSlab.EnumBlockHalf.values()) {
                net.minecraft.block.state.IBlockState slabState=bottom.withProperty(com.vandorlabs.blocks.BlockProgrammableSlab.HALF,half);
                net.minecraft.block.state.IBlockState extended=slabState.getBlock().getExtendedState(slabState,world,pos);
                java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads=net.minecraft.client.Minecraft.getMinecraft()
                        .getBlockRendererDispatcher().getModelForState(slabState).getQuads(extended,null,0);
                require(quads.size()==6,"side texture changed slab geometry");
                for(net.minecraft.client.renderer.block.model.BakedQuad quad:quads) {
                    int[] vertices=quad.getVertexData();int stride=vertices.length/4;
                    boolean horizontal=true;
                    for(int vertex=1;vertex<4;vertex++)horizontal &= vertices[1]==vertices[vertex*stride+1];
                    int choice=horizontal?2:5;
                    require(quad.getSprite().getIconName().equals(com.vandorlabs.tiles.ScreenHousingTextures.texture(choice)),"slab side texture assigned to wrong face");
                }
            }
            slabTile.setSideTexture(-1);
            world.setBlockState(east,bottom,2);
            require(renderedVertices(world,pos)==20,"matching slab halves retained internal face");
            world.setBlockState(east,bottom.withProperty(
                    com.vandorlabs.blocks.BlockProgrammableSlab.HALF,
                    net.minecraft.block.BlockSlab.EnumBlockHalf.TOP),2);
            require(renderedVertices(world,pos)==24,"opposite slab halves lost a visible face");
            world.setBlockState(pos,
                    com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK.getDefaultState(),2);
            require(world.getTileEntity(pos).shouldRenderInPass(0),
                    "programmable trigger lost its tile renderer");
            for (int x=-1;x<=1;x++) for (int z=-1;z<=1;z++)
                world.setBlockState(pos.add(x,0,z),
                        com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),2);
            net.minecraft.client.renderer.BufferBuilder dense = renderedBuffer(world,pos);
            require(dense.getVertexCount()==8,"dense cube should render only exposed top and bottom");
            net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();
            java.nio.ByteBuffer witness=dense.getByteBuffer();int witnessStride=dense.getVertexFormat().getNextOffset(),witnessLight=dense.getVertexFormat().getUvOffsetById(1);
            for(int vertex=0;vertex<dense.getVertexCount();vertex++){
                EnumFacing face=witness.getFloat(vertex*witnessStride+4)<.5F?EnumFacing.DOWN:EnumFacing.UP;int expected=world.getCombinedLight(pos.offset(face),0);
                System.out.println("[vandorlabs][reprolab] dense-housing-light vertex="+vertex+" face="+face+" ao-setting="+mc.gameSettings.ambientOcclusion+" block="+(witness.getShort(vertex*witnessStride+witnessLight)&65535)+" sky="+(witness.getShort(vertex*witnessStride+witnessLight+2)&65535)+" face-block="+(expected&65535)+" face-sky="+(expected>>>16));
            }
            net.minecraft.client.renderer.BufferBuilder smooth=renderedLighting(world,pos,true,false),reference=renderedLighting(world,pos,true,true);
            require(smooth.getVertexCount()==8&&reference.getVertexCount()==8,"AO dense cube face culling changed");
            require(java.util.Arrays.deepEquals(physicalLighting(smooth),physicalLighting(reference)),"dense cube AO differs from actual FaceBakery reference");
            // A direct face sample is the flat-light contract. AO intentionally
            // blends adjacent sky/block samples at each physical corner.
            dense=renderedLighting(world,pos,false,false);
            require(dense.getVertexCount()==8,"flat dense cube face culling changed");
            java.nio.ByteBuffer data=dense.getByteBuffer();
            int stride=dense.getVertexFormat().getNextOffset(),lightOffset=dense.getVertexFormat().getUvOffsetById(1);
            for (int vertex=0;vertex<dense.getVertexCount();vertex++) {
                EnumFacing face=data.getFloat(vertex*stride+4)<.5F?EnumFacing.DOWN:EnumFacing.UP;
                int light=world.getCombinedLight(pos.offset(face),0);
                require((data.getShort(vertex*stride+lightOffset)&65535)==(light&65535),
                        "dense cube block lighting differs from its visible face sample");
                require((data.getShort(vertex*stride+lightOffset+2)&65535)==(light>>>16),
                        "dense cube sky lighting differs from its visible face sample");
            }
        } finally {
            for (int x=-1;x<=1;x++) for (int z=-1;z<=1;z++)
                world.setBlockToAir(pos.add(x,0,z));
        }
    }

    private static net.minecraft.client.renderer.BufferBuilder renderedLighting(net.minecraft.world.World world,BlockPos pos,boolean smooth,boolean reference){
        net.minecraft.client.renderer.BlockRendererDispatcher dispatcher=net.minecraft.client.Minecraft.getMinecraft().getBlockRendererDispatcher();
        net.minecraft.block.state.IBlockState listed=world.getBlockState(pos).getActualState(world,pos),state=listed.getBlock().getExtendedState(listed,world,pos);
        net.minecraft.client.renderer.block.model.IBakedModel model=dispatcher.getModelForState(listed);
        require(model.isAmbientOcclusion(),"registered housing must retain AO");
        if(reference){
            final net.minecraft.client.renderer.block.model.IBakedModel source=model;final java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> faces=new java.util.ArrayList<>();
            for(EnumFacing side:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(net.minecraft.client.renderer.block.model.BakedQuad quad:source.getQuads(state,side,0))faces.add(HousingShaderLightingChecks.reference(quad));
            model=new net.minecraft.client.renderer.block.model.IBakedModel(){
                public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(net.minecraft.block.state.IBlockState s,EnumFacing side,long seed){return side==null?faces:java.util.Collections.emptyList();}
                public boolean isAmbientOcclusion(){return true;}public boolean isGui3d(){return source.isGui3d();}public boolean isBuiltInRenderer(){return false;}
                public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleTexture(){return source.getParticleTexture();}
                public net.minecraft.client.renderer.block.model.ItemCameraTransforms getItemCameraTransforms(){return source.getItemCameraTransforms();}
                public net.minecraft.client.renderer.block.model.ItemOverrideList getOverrides(){return net.minecraft.client.renderer.block.model.ItemOverrideList.NONE;}
            };
        }
        net.minecraft.client.renderer.BufferBuilder buffer=new net.minecraft.client.renderer.BufferBuilder(4096);buffer.begin(7,net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK);buffer.setTranslation(-pos.getX(),-pos.getY(),-pos.getZ());
        net.minecraft.util.BlockRenderLayer old=net.minecraftforge.client.MinecraftForgeClient.getRenderLayer();net.minecraftforge.client.ForgeHooksClient.setRenderLayer(net.minecraft.util.BlockRenderLayer.SOLID);
        try{if(smooth)dispatcher.getBlockModelRenderer().renderModelSmooth(world,model,state,pos,buffer,true,0);else dispatcher.getBlockModelRenderer().renderModelFlat(world,model,state,pos,buffer,true,0);}finally{net.minecraftforge.client.ForgeHooksClient.setRenderLayer(old);}
        buffer.finishDrawing();return buffer;
    }
    private static int[][] physicalLighting(net.minecraft.client.renderer.BufferBuilder buffer){
        net.minecraft.client.renderer.vertex.VertexFormat format=buffer.getVertexFormat();int stride=format.getIntegerSize(),color=format.getColorOffset()/4,light=format.getUvOffsetById(1)/4;int[] raw=buffer.getVertexState().getRawBuffer();int[][] rows=new int[buffer.getVertexCount()][5];
        for(int i=0;i<rows.length;i++){int at=i*stride;rows[i]=new int[]{raw[at],raw[at+1],raw[at+2],raw[at+color],raw[at+light]};}
        java.util.Arrays.sort(rows,(a,b)->{for(int axis=0;axis<3;axis++){int order=Float.compare(Float.intBitsToFloat(a[axis]),Float.intBitsToFloat(b[axis]));if(order!=0)return order;}return 0;});return rows;
    }

    private static int renderedVertices(net.minecraft.world.World world, BlockPos pos) {
        return renderedBuffer(world,pos).getVertexCount();
    }
    private static net.minecraft.client.renderer.BufferBuilder renderedBuffer(
            net.minecraft.world.World world, BlockPos pos) {
        net.minecraft.client.renderer.BufferBuilder buffer = new net.minecraft.client.renderer.BufferBuilder(4096);
        buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,
                net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK);
        buffer.setTranslation(-pos.getX(),-pos.getY(),-pos.getZ());
        net.minecraftforge.client.ForgeHooksClient.setRenderLayer(net.minecraft.util.BlockRenderLayer.SOLID);
        try {
            net.minecraft.client.Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(
                    world.getBlockState(pos),pos,world,buffer);
        } finally {
            net.minecraftforge.client.ForgeHooksClient.setRenderLayer(null);
        }
        buffer.finishDrawing();
        return buffer;
    }
    static void run() {
        BlockPos origin = new BlockPos(-20,100,-20);
        for (EnumFacing facing : EnumFacing.values()) {
            PanelPlane plane = PanelPlane.of(facing);
            Set<BlockPos> expected = new HashSet<>();
            for (int x=0; x<2; x++) for (int y=0; y<3; y++)
                expected.add(origin.offset(plane.right,x).offset(plane.up,y));
            Set<BlockPos> actual = LoadedPlaneConnections.collect(origin, plane,
                    expected::contains, pos -> {
                        require(expected.contains(pos), "eligibility read an unloaded cell");
                        return true;
                    });
            require(actual.equals(expected), "wrong plane or missing connected cell: " + facing);
            BlockPos barrier=origin.offset(plane.up);
            Set<BlockPos> stopped=LoadedPlaneConnections.collect(origin,plane,
                    pos -> pos.equals(origin) || pos.equals(barrier), pos -> !pos.equals(barrier));
            require(stopped.size()==1, "crossed an ineligible cell");
            Set<BlockPos> capped=LoadedPlaneConnections.collect(origin,plane,pos -> true,pos -> true);
            require(capped.size()==LoadedPlaneConnections.LIMIT, "unbounded traversal");
            for (BlockPos pos:capped) require(PanelPlane.axis(pos,facing)==PanelPlane.axis(origin,facing),
                    "traversal escaped plane");
        }
        System.out.println("[vandorlabs][reprolab] panel-connections PASS");
    }
    private static void require(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
