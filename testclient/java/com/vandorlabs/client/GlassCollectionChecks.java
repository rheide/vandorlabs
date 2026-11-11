package com.vandorlabs.client;

import com.vandorlabs.CommonProxy;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.io.InputStream;
import java.lang.invoke.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import sun.misc.Unsafe;

/** Execute the real Forge dispatcher branches with only its GL calls replaced by counters. */
public final class GlassCollectionChecks {
    private static final BlockPos POS=new BlockPos(8,80,8);
    private static final com.sun.management.ThreadMXBean METER=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static BlockProgrammableGlass glass;
    private static int lighting,colors,lightmaps;
    private static Object consumed;
    public static void lighting(){lighting++;}
    public static void color(float r,float g,float b,float a){colors++;}
    public static void lightmap(int unit,float block,float sky){lightmaps++;}
    public static boolean neverFast(TileEntity tile){return false;}
    private static final class World extends NonRenderingChecks.MemoryWorld {
        int lights;
        World(){super(true);}
        @Override public int getCombinedLight(BlockPos pos,int minimum){lights++;return super.getCombinedLight(pos,minimum);}
    }
    private static void need(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static IBlockState state(EnumFacing facing,int depth){return glass.getDefaultState().withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X).withProperty(BlockGlassWall.DEPTH,depth);}
    private static TileEntityProgrammableGlass put(World world,BlockPos pos,EnumFacing facing,int depth,int tint,boolean framed,int shade){
        TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();NBTTagCompound data=new NBTTagCompound();
        data.setInteger("GlassPanelFacing",facing.getHorizontalIndex());data.setInteger("GlassTint",tint);
        data.setBoolean("GlassFramed",framed);data.setInteger("GlassShade",shade);data.setBoolean("GlassJoin",true);
        tile.readFromNBT(data);tile.setWorld(world);tile.setPos(pos);world.states.put(pos,state(facing,depth));world.tiles.put(pos,tile);return tile;
    }
    private static Field field(Class<?> owner,String name)throws Exception{Field f=owner.getDeclaredField(name);f.setAccessible(true);return f;}
    @SuppressWarnings("unchecked") private static Map<TileEntity,?> visible()throws Exception{return (Map<TileEntity,?>)field(GlazingRenderOrder.class,"visible").get(null);}

    private static void eligibility()throws Exception{
        World world=new World();int cases=0;
        try{
            ForgeHooksClient.setRenderPass(0);
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int depth=0;depth<3;depth++)for(int shade=0;shade<19;shade++)for(int tint=0;tint<4;tint++)for(boolean framed:new boolean[]{false,true})for(int shape=0;shape<3;shape++){
                world.clear();GlassSurfaceGroups.invalidate(world);
                TileEntityProgrammableGlass tile=put(world,POS,facing,depth,tint,framed,shade);
                if(shape>0)put(world,POS.offset(facing),facing.rotateY(),depth,tint,framed,shade);
                if(shape==2){put(world,POS.offset(facing.getOpposite()),facing.rotateY(),depth,tint,framed,shade);put(world,POS.offset(facing.rotateY()),facing,depth,tint,framed,shade);}
                boolean corner=PanelJoins.flatCorner(world.states.get(POS),world,POS)!=null;
                need(corner==(shape>0),"corner fixture did not exercise selected shape");
                GlassSurfaceGroups.flatCorner(tile,world.states.get(POS));
                boolean empty=!GlassMaterials.opaque(tile.getMaterial(),tint)&&(!framed||!corner);
                need(tile.hasFastRenderer()==empty,"collection admitted a pane/frame requiring opaque vertices");
                need(tile.shouldRenderInPass(0),"opaque visibility collection removed");
                ForgeHooksClient.setRenderPass(1);need(!tile.hasFastRenderer(),"translucent pane entered shared Forge buffer");
                ForgeHooksClient.setRenderPass(0);cases++;
            }
            TileEntityProgrammableGlass tile=put(world,POS,EnumFacing.NORTH,0,1,true,0);
            NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());data.setBoolean("GlassJoin",false);tile.readFromNBT(data);
            need(tile.hasFastRenderer(),"unjoined stock frame is already in terrain");
            tile.setWorld(null);need(!tile.hasFastRenderer(),"worldless tile entered collection");tile.setWorld(world);
            ForgeHooksClient.setRenderPass(-1);need(!tile.hasFastRenderer(),"item/direct renderer entered collection");ForgeHooksClient.setRenderPass(0);
            VehicleWorld carried=new VehicleWorld(world,VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(POS,state(EnumFacing.NORTH,0),null))));
            tile.setWorld(carried);need(!tile.hasFastRenderer(),"carried tile opted into native collection");tile.setWorld(world);
            // Forge invalidates its metadata cache when the owner block changes.
            world.states.put(POS,state(EnumFacing.EAST,2));tile.updateContainingBlockInfo();
            need(tile.getBlockMetadata()==glass.getMetaFromState(world.states.get(POS)),"native metadata invalidation fixture");
            VandorLabs.proxy=new CommonProxy();need(!tile.hasFastRenderer(),"common/server proxy opted into client rendering");installProxy();
        }finally{GlassSurfaceGroups.unload(world);ForgeHooksClient.setRenderPass(-1);}
        System.out.println("glass-collection eligibility PASS cases="+cases+" all stock colors/tints/depths/facings, corners/T bars, unframed, carried, metadata, direct and shared-server fallback");
    }

    private static void publication()throws Exception{
        World world=new World();TileEntityProgrammableGlass tile=put(world,POS,EnumFacing.NORTH,0,1,true,0);tile.frameAppearance.front=0;
        Class<?> entryClass=Class.forName("com.vandorlabs.client.GlassFrameChunks$Entry");Constructor<?> constructor=entryClass.getDeclaredConstructor(TileEntityProgrammableGlass.class);constructor.setAccessible(true);
        Object entry=constructor.newInstance(tile);tile.frameChunkPublication=entry;
        GlassFrameModel.Mesh mesh=new GlassFrameModel.Mesh(Collections.emptyList(),DefaultVertexFormats.BLOCK,false);tile.frameChunkMesh=mesh;field(entryClass,"mesh").set(entry,mesh);
        Unsafe unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);RenderChunk chunk=(RenderChunk)unsafe.allocateInstance(RenderChunk.class);
        BlockPos.MutableBlockPos section=new BlockPos.MutableBlockPos(0,80,0);field(RenderChunk.class,"position").set(chunk,section);field(entryClass,"chunk").set(entry,chunk);
        ForgeHooksClient.setRenderPass(0);
        try{
            need(!tile.hasFastRenderer(),"unacknowledged selected frame lost fallback");
            field(entryClass,"ready").setBoolean(entry,true);need(tile.hasFastRenderer(),"acknowledged frame retained empty TESR work");
            tile.frameChunkMesh=new Object();need(!tile.hasFastRenderer(),"wrong mesh generation suppressed fallback");tile.frameChunkMesh=mesh;
            section.setPos(16,80,0);need(!tile.hasFastRenderer()&&!field(entryClass,"ready").getBoolean(entry),"recycled chunk suppressed fallback");
            section.setPos(0,80,0);need(!tile.hasFastRenderer(),"recycled chunk accepted without a new acknowledgement");
            field(entryClass,"ready").setBoolean(entry,true);need(tile.hasFastRenderer(),"new exact upload not accepted");
            tile.invalidate();need(!ProgrammableGlassPasses.emptyOpaquePass(tile,null),"invalidated publication survived");
        }finally{GlassSurfaceGroups.unload(world);ForgeHooksClient.setRenderPass(-1);}
        System.out.println("glass-collection publication PASS pending, exact upload, mismatched mesh, recycled chunk and invalidation");
    }

    private static final class Loader extends ClassLoader {
        Loader(){super(GlassCollectionChecks.class.getClassLoader());}
        Class<?> define(byte[] bytes){return defineClass(null,bytes,0,bytes.length);}
    }
    private static final class Dispatcher {
        final Object target;final MethodHandle render;final BufferBuilder buffer;
        Dispatcher(World world,boolean before)throws Exception{
            String source="net/minecraft/client/renderer/tileentity/TileEntityRendererDispatcher",name="com/vandorlabs/client/GlassDispatch"+(before?"Before":"After"),hooks=GlassCollectionChecks.class.getName().replace('.','/');
            ClassNode parsed=new ClassNode();try(InputStream in=GlassCollectionChecks.class.getResourceAsStream("/"+source+".class")){new ClassReader(in).accept(parsed,ClassReader.SKIP_FRAMES);}
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,name,null,"java/lang/Object",null);
            for(FieldNode f:parsed.fields)if(Arrays.asList("world","entityX","entityY","entityZ","staticPlayerX","staticPlayerY","staticPlayerZ","drawingBatch","batchBuffer").contains(f.name))writer.visitField(Opcodes.ACC_PUBLIC|(f.access&Opcodes.ACC_STATIC),f.name,f.desc,null,null).visitEnd();
            String renderer="Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;";
            writer.visitField(Opcodes.ACC_PUBLIC,"renderer",renderer,null,null).visitEnd();
            MethodVisitor init=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);init.visitCode();init.visitVarInsn(Opcodes.ALOAD,0);init.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);init.visitInsn(Opcodes.RETURN);init.visitMaxs(0,0);init.visitEnd();
            MethodVisitor get=writer.visitMethod(Opcodes.ACC_PUBLIC,"getRenderer","(Lnet/minecraft/tileentity/TileEntity;)"+renderer,null,null);get.visitCode();get.visitVarInsn(Opcodes.ALOAD,0);get.visitFieldInsn(Opcodes.GETFIELD,name,"renderer",renderer);get.visitInsn(Opcodes.ARETURN);get.visitMaxs(0,0);get.visitEnd();
            int methods=0;
            for(MethodNode method:parsed.methods)if(method.name.equals("render")&&(method.desc.equals("(Lnet/minecraft/tileentity/TileEntity;FI)V")||method.desc.equals("(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V"))){
                for(AbstractInsnNode node:method.instructions.toArray()){
                    if(node instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)node;if(f.owner.equals(source))f.owner=name;else if(f.owner.equals("net/minecraft/client/renderer/OpenGlHelper")&&f.name.equals("lightmapTexUnit"))method.instructions.set(f,new InsnNode(Opcodes.ICONST_0));}
                    if(node instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)node;
                        if(call.owner.equals(source))call.owner=name;
                        else if(call.owner.equals("net/minecraft/client/renderer/RenderHelper")&&call.name.equals("enableStandardItemLighting")){call.owner=hooks;call.name="lighting";}
                        else if(call.owner.equals("net/minecraft/client/renderer/OpenGlHelper")&&call.name.equals("setLightmapTextureCoords")){call.owner=hooks;call.name="lightmap";}
                        else if(call.owner.equals("net/minecraft/client/renderer/GlStateManager")&&call.name.equals("color"))call.owner=hooks;
                        else if(before&&call.owner.equals("net/minecraft/tileentity/TileEntity")&&call.name.equals("hasFastRenderer")){call.owner=hooks;call.name="neverFast";call.desc="(Lnet/minecraft/tileentity/TileEntity;)Z";call.setOpcode(Opcodes.INVOKESTATIC);}
                    }
                }
                method.accept(writer);methods++;
            }
            need(methods==2,"actual Forge dispatcher methods changed");writer.visitEnd();Class<?> type=new Loader().define(writer.toByteArray());target=type.newInstance();
            type.getField("world").set(target,world);type.getField("drawingBatch").setBoolean(target,true);
            Tessellator batch=new Tessellator(256);buffer=batch.getBuffer();buffer.begin(7,DefaultVertexFormats.BLOCK);buffer.setTranslation(17,19,23);
            buffer.pos(1,2,3).color(4,5,6,7).tex(.25,.75).lightmap(16,240).endVertex();
            type.getField("batchBuffer").set(target,batch);type.getField("renderer").set(target,before?new ReferenceGlassCollectionRenderer():new TEProgrammableGlass());
            render=MethodHandles.lookup().unreflect(type.getMethod("render",TileEntity.class,float.class,int.class)).bindTo(target);
        }
        void render(TileEntity tile)throws Throwable{render.invokeExact(tile,0F,-1);}
    }
    private static void dispatch(int count)throws Throwable{
        World world=new World();TileEntityProgrammableGlass[] tiles=new TileEntityProgrammableGlass[count];
        for(int i=0;i<count;i++)tiles[i]=put(world,new BlockPos(i*4,80,8),EnumFacing.NORTH,0,1,true,i%19);
        Dispatcher[] dispatch={new Dispatcher(world,true),new Dispatcher(world,false)};long[][] cpu=new long[2][11],allocation=new long[2][11];int[] reads=new int[2];
        ForgeHooksClient.setRenderPass(0);
        try{
            int admitted=Math.min(count,GlassSurfaceGroups.MAX_LAYOUTS),overflow=count-admitted;
            // Unknown owners must not trigger discovery from either of Forge's
            // two eligibility calls. The first ordinary render warms the cache.
            for(TileEntityProgrammableGlass tile:tiles){tile.getBlockType();tile.getBlockMetadata();}
            world.stateReads=0;
            for(TileEntityProgrammableGlass tile:tiles)need(!tile.hasFastRenderer(),"cold topology assumed a flat pane");
            need(world.stateReads==0,"cold eligibility queried neighbours");
            for(int i=0;i<count;i++){
                GlassSurfaceGroups.flatCorner(tiles[i],world.states.get(tiles[i].getPos()));
                need(tiles[i].hasFastRenderer()==(i<admitted),"admission/overflow eligibility changed");
            }
            int warm=Math.max(12,(131072+count-1)/count);
            for(int batch=-warm;batch<11;batch++)for(int turn=0;turn<2;turn++){
                int mode=(batch+turn)&1;GlazingRenderOrder.begin(world);world.stateReads=world.lights=lighting=colors=lightmaps=0;
                long bytes=METER.getThreadAllocatedBytes(Thread.currentThread().getId()),start=METER.getCurrentThreadCpuTime();
                for(TileEntityProgrammableGlass tile:tiles)dispatch[mode].render(tile);
                long elapsed=METER.getCurrentThreadCpuTime()-start,used=METER.getThreadAllocatedBytes(Thread.currentThread().getId())-bytes;
                need(visible().size()==count,"visibility collector lost panes or duplicated entries");
                need(lighting==(mode==0?count:overflow)&&world.lights==lighting&&lightmaps==lighting&&colors==lighting,"dispatcher light/GL branch changed");
                if(batch>=0){cpu[mode][batch]=elapsed;allocation[mode][batch]=used;reads[mode]=world.stateReads;}
                need(dispatch[mode].buffer.getVertexCount()==1,"collection modified Forge's vertex buffer");consumed=visible();
            }
            need(reads[0]==count+3*overflow&&reads[1]==4*overflow,"collection repeated neighbour discovery beyond its admission limit");
            need(allocation[1][10]<=allocation[0][10]+4096,"over-budget eligibility added per-pane allocation");
            for(int mode=0;mode<2;mode++){Arrays.sort(cpu[mode]);Arrays.sort(allocation[mode]);System.out.println("glass-collection dispatch "+(mode==0?"delivered-baseline":"current")+" panes="+count+" median-thread-ns="+cpu[mode][5]+" allocated-bytes="+allocation[mode][5]+" state-reads="+reads[mode]+" light-reads="+(mode==0?count:overflow)+" lighting/color/lightmap-calls="+(3*(mode==0?count:overflow)));}
            GlazingRenderOrder.begin(world);new TEProgrammableGlass().renderTileEntityFast(tiles[0],1,2,3,.25F,0,.5F,dispatch[1].buffer);need(visible().isEmpty(),"destroy-stage draw entered sorted collection");
            new TEProgrammableGlass().renderTileEntityFast(tiles[0],1,2,3,.25F,-1,.5F,dispatch[1].buffer);
            Object draw=visible().get(tiles[0]);need(draw!=null&&field(draw.getClass(),"x").getDouble(draw)==1&&field(draw.getClass(),"partial").getFloat(draw)==.25F&&field(draw.getClass(),"alpha").getFloat(draw)==.5F,"collection changed draw parameters");
            int[] raw=dispatch[1].buffer.getVertexState().getRawBuffer();need(Float.intBitsToFloat(raw[0])==18&&Float.intBitsToFloat(raw[1])==21&&Float.intBitsToFloat(raw[2])==26,"shared translation/record was overwritten");
        }finally{GlazingRenderOrder.end();GlassSurfaceGroups.unload(world);ForgeHooksClient.setRenderPass(-1);for(Dispatcher d:dispatch)d.buffer.finishDrawing();}
    }
    private static void installProxy(){VandorLabs.proxy=new CommonProxy(){
        @Override public boolean glassCollectionOnly(TileEntityProgrammableGlass tile){return ProgrammableGlassPasses.collectionOnly(tile);}
        @Override public void glassFrameAppearanceChanged(TileEntityProgrammableGlass tile){GlassSurfaceGroups.changed(tile);}
    };}
    public static void main(String[] args)throws Throwable{
        field(net.minecraftforge.fml.relauncher.CoreModManager.class,"deobfuscatedEnvironment").setBoolean(null,true);
        net.minecraft.init.Bootstrap.register();installProxy();glass=new BlockProgrammableGlass("glass_collection_checks");
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableGlass.class,new net.minecraft.util.ResourceLocation("vandorlabs","glass_collection_checks"));
        METER.setThreadAllocatedMemoryEnabled(true);METER.setThreadCpuTimeEnabled(true);
        eligibility();publication();dispatch(1024);dispatch(8193);dispatch(16384);
        System.out.println("glass-collection PASS exact dispatcher routing and buffer/visibility checks; GL calls counted, not GPU timings or shader-pack acceptance");
    }
}
