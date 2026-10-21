package com.vandorlabs.client;

import com.vandorlabs.blocks.WaterloggedParts;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Adapt vanilla liquid geometry only at contacts with actual contained fluid volumes. */
public final class ConnectedFluidRenderer extends BlockFluidRenderer {
    private final BlockFluidRenderer delegate;
    private static final ThreadLocal<BufferBuilder> SCRATCH=ThreadLocal.withInitial(()->new BufferBuilder(32768));
    private ConnectedFluidRenderer(BlockFluidRenderer delegate){super(Minecraft.getMinecraft().getBlockColors());this.delegate=delegate;}
    public static final class Installer {
        private boolean installed;
        @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
            if(installed||event.phase!=TickEvent.Phase.END)return;BlockRendererDispatcher dispatcher=Minecraft.getMinecraft().getBlockRendererDispatcher();if(dispatcher==null)return;
            try{for(Field field:BlockRendererDispatcher.class.getDeclaredFields())if(field.getType()==BlockFluidRenderer.class){field.setAccessible(true);BlockFluidRenderer current=(BlockFluidRenderer)field.get(dispatcher);if(!(current instanceof ConnectedFluidRenderer))field.set(dispatcher,new ConnectedFluidRenderer(current));installed=true;return;}}catch(ReflectiveOperationException failure){throw new IllegalStateException("Unable to connect contained fluid rendering",failure);}
        }
    }
    @Override protected void initAtlasSprites(){
        super.initAtlasSprites();
        if(delegate!=null)try{for(Method method:BlockFluidRenderer.class.getDeclaredMethods())if(method.getParameterCount()==0&&method.getReturnType()==void.class){method.setAccessible(true);method.invoke(delegate);return;}}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Unable to reload connected fluid sprites",failure);}
    }
    @Override public boolean renderFluid(IBlockAccess access,IBlockState state,BlockPos pos,BufferBuilder destination){
        Material material=state.getMaterial();if(material!=Material.WATER&&material!=Material.LAVA)return delegate.renderFluid(access,state,pos,destination);
        FluidStack fluid=null;Map<BlockPos,WaterloggedSurfaceMesh.Contact> contacts=null;
        for(EnumFacing face:EnumFacing.values()){
            BlockPos next=pos.offset(face);if(!WaterloggedParts.supports(access.getBlockState(next)))continue;
            TileEntity tile=access.getTileEntity(next);if(tile==null)continue;
            if(fluid==null)fluid=new FluidStack(material==Material.WATER?FluidRegistry.WATER:FluidRegistry.LAVA,1000);
            WaterloggedSurfaceMesh.Contact contact=WaterloggedSurfaceMesh.contact(tile,fluid);
            if(contact!=null&&contact.touches(face.getOpposite())){if(contacts==null)contacts=new HashMap<>();contacts.put(next,contact);}
        }
        if(contacts==null)return delegate.renderFluid(access,state,pos,destination);
        final Map<BlockPos,WaterloggedSurfaceMesh.Contact> joined=contacts;
        Access connected=new Access(access,contacts,material);IBlockState visible=(IBlockState)Proxy.newProxyInstance(IBlockState.class.getClassLoader(),new Class<?>[]{IBlockState.class},(proxy,method,args)->{
            String name=method.getName();if((name.equals("shouldSideBeRendered")||name.equals("func_185894_c"))&&args!=null&&args.length==3&&joined.containsKey(((BlockPos)args[1]).offset((EnumFacing)args[2])))return true;
            try{return method.invoke(state,args);}catch(InvocationTargetException failure){throw failure.getCause();}
        });
        BufferBuilder scratch=SCRATCH.get();scratch.begin(7,DefaultVertexFormats.BLOCK);boolean finished=false;
        try{delegate.renderFluid(connected,visible,pos,scratch);scratch.finishDrawing();finished=true;ByteBuffer bytes=scratch.getByteBuffer();int count=scratch.getVertexCount(),written=0,stride=DefaultVertexFormats.BLOCK.getNextOffset();
            for(int first=0;first<count;first+=4){List<double[]> polygon=new ArrayList<>();for(int vertex=0;vertex<4;vertex++){int at=(first+vertex)*stride;polygon.add(new double[]{bytes.getFloat(at)-pos.getX(),bytes.getFloat(at+4)-pos.getY(),bytes.getFloat(at+8)-pos.getZ(),bytes.getFloat(at+16),bytes.getFloat(at+20),bytes.get(at+12)&255,bytes.get(at+13)&255,bytes.get(at+14)&255,bytes.get(at+15)&255,bytes.getShort(at+24)&65535,bytes.getShort(at+26)&65535,0});}
                EnumFacing boundary=boundary(polygon);WaterloggedSurfaceMesh.Contact contact=boundary==null?null:contacts.get(pos.offset(boundary));List<List<double[]>> pieces;
                if(contact==null)pieces=Collections.singletonList(polygon);else{int axis=boundary.getAxis()==EnumFacing.Axis.X?0:boundary.getAxis()==EnumFacing.Axis.Y?1:2;for(double[] vertex:polygon){vertex[11]=vertex[axis];vertex[axis]=boundary.getAxisDirection()==EnumFacing.AxisDirection.POSITIVE?1:0;}pieces=contact.subtract(polygon,boundary);for(List<double[]> piece:pieces)for(double[] vertex:piece)vertex[axis]=vertex[11];}
                for(List<double[]> piece:pieces)if(piece.size()==4){for(double[] vertex:piece)append(destination,vertex,pos);written+=4;}else for(int i=1;i+1<piece.size();i++){append(destination,piece.get(0),pos);append(destination,piece.get(i),pos);append(destination,piece.get(i+1),pos);append(destination,piece.get(i+1),pos);written+=4;}
            }return written>0;
        }finally{if(!finished)scratch.finishDrawing();scratch.reset();}
    }
    private static EnumFacing boundary(List<double[]> polygon){
        for(int axis=0;axis<3;axis++)for(int side=0;side<2;side++){boolean same=true;for(double[] p:polygon)same&=Math.abs(p[axis]-side)<.002;if(same)return axis==0?(side==0?EnumFacing.WEST:EnumFacing.EAST):axis==1?(side==0?EnumFacing.DOWN:EnumFacing.UP):(side==0?EnumFacing.NORTH:EnumFacing.SOUTH);}
        return null;
    }
    private static void append(BufferBuilder buffer,double[] p,BlockPos pos){buffer.pos(p[0]+pos.getX(),p[1]+pos.getY(),p[2]+pos.getZ()).color((int)Math.round(p[5]),(int)Math.round(p[6]),(int)Math.round(p[7]),(int)Math.round(p[8])).tex(p[3],p[4]).lightmap((int)Math.round(p[9]),(int)Math.round(p[10])).endVertex();}
    private static final class Access implements IBlockAccess {
        final IBlockAccess source;final Map<BlockPos,WaterloggedSurfaceMesh.Contact> contacts;final IBlockState liquid;
        Access(IBlockAccess source,Map<BlockPos,WaterloggedSurfaceMesh.Contact> contacts,Material material){this.source=source;this.contacts=contacts;liquid=(material==Material.WATER?net.minecraft.init.Blocks.WATER:net.minecraft.init.Blocks.LAVA).getDefaultState();}
        public TileEntity getTileEntity(BlockPos p){return source.getTileEntity(p);}public int getCombinedLight(BlockPos p,int minimum){return source.getCombinedLight(p,minimum);}
        public IBlockState getBlockState(BlockPos p){return contacts.containsKey(p)||contacts.containsKey(p.down())?liquid:source.getBlockState(p);}
        public boolean isAirBlock(BlockPos p){return getBlockState(p).getMaterial()==Material.AIR;}public net.minecraft.world.biome.Biome getBiome(BlockPos p){return source.getBiome(p);}public int getStrongPower(BlockPos p,EnumFacing face){return source.getStrongPower(p,face);}public WorldType getWorldType(){return source.getWorldType();}public boolean isSideSolid(BlockPos p,EnumFacing face,boolean fallback){return source.isSideSolid(p,face,fallback);}
    }
}
