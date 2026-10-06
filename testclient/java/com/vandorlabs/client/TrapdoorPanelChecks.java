package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.render.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

/** Real saved settings, endpoint geometry, collision, exact selection and loaded-owner routing. */
final class TrapdoorPanelChecks {
    static void run() {
        int checks=0;
        for(boolean diagonal:new boolean[]{false,true})for(int position=0;position<3;position++)
            for(EnumFacing facing:EnumFacing.Plane.HORIZONTAL)for(boolean inverted:new boolean[]{false,true})for(int mode:new int[]{3,4,5,6,7}) {
                NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(8,100,8);
                BlockProgrammableTrapdoor block=diagonal?(BlockProgrammableTrapdoor)ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR:(BlockProgrammableTrapdoor)ModBlocks.PROGRAMMABLE_TRAPDOOR;
                IBlockState state=block.getDefaultState().withProperty(BlockTrapDoor.FACING,facing).withProperty(BlockTrapDoor.HALF,inverted?BlockTrapDoor.DoorHalf.TOP:BlockTrapDoor.DoorHalf.BOTTOM);
                world.setBlockState(pos,state,2);TileEntityProgrammableTrapdoor tile=(TileEntityProgrammableTrapdoor)world.getTileEntity(pos);
                tile.configure(ScreenHousingTextures.DEFAULT_TRAPDOOR,position,true,0,0);tile.setSlideMode(mode);world.setBlockState(pos,state,2);
                double[][] closed=TrapdoorPanelMotion.closed(tile,state);double[] center=new double[3];for(double[] point:closed)for(int i=0;i<3;i++)center[i]+=point[i]/8;
                double[] u=delta(closed[1],closed[0]),v=delta(closed[diagonal && position!=2?2:4],closed[0]),normal=PanelPolyhedron.unit(PanelPolyhedron.cross(u,v));
                Vec3d mid=new Vec3d(center[0]+pos.getX(),center[1]+pos.getY(),center[2]+pos.getZ()),start=mid.addVector(normal[0]*2,normal[1]*2,normal[2]*2),end=mid.addVector(-normal[0]*2,-normal[1]*2,-normal[2]*2);
                require(TrapdoorPanelCollision.trace(tile,state,0,pos,start,end)!=null,"closed panel ray misses seam");
                require(TrapdoorPanelCollision.trace(tile,state,1,pos,start,end)==null,"open panel seals aperture");
                AxisAlignedBB body=new AxisAlignedBB(mid.x-.01,mid.y-.01,mid.z-.01,mid.x+.01,mid.y+.01,mid.z+.01);List<AxisAlignedBB> collisions=new ArrayList<>();
                TrapdoorPanelCollision.add(tile,state,0,pos,body,collisions);require(!collisions.isEmpty(),"closed collision misses leaf");collisions.clear();
                TrapdoorPanelCollision.add(tile,state,1,pos,body,collisions);require(collisions.isEmpty(),"open collision seals aperture");
                double volume=0;for(List<double[][]> faces:tile.panelFaces(state,0))volume+=volume(faces);
                require(Math.abs(volume-volume(PanelPolyhedron.faces(closed,new PanelMotion(tile.panelMotion(state).u,tile.panelMotion(state).v,0,0,1,1,4),0,0)))<1e-7,"split volume lost/overlapped");
                mesh(tile,state,closed,diagonal && position!=2?4:2);
                NBTTagCompound settings=tile.itemSettings();TileEntityProgrammableTrapdoor copy=diagonal?new TileEntityProgrammableDiagonalTrapdoor():new TileEntityProgrammableTrapdoor();copy.readFromNBT(settings);require(copy.getSlideMode()==mode && copy.isSliding(),"picked motion lost");
                NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());copy.readFromNBT(saved);require(copy.getSlideMode()==mode,"saved motion lost");
                NBTTagCompound captured=ProgrammableSettings.capture(world,pos);BlockPos target=pos.east(5);world.setBlockState(target,state,2);TileEntityProgrammableTrapdoor targetTile=(TileEntityProgrammableTrapdoor)world.getTileEntity(target);
                require(ProgrammableSettings.apply(world,target,captured) && targetTile.getSlideMode()==mode,"Duplifier motion lost");
                NBTTagCompound excluded=DuplifierApplyOptions.selected(captured,0);require(!excluded.hasKey(ProgrammableSettings.TRAPDOOR_SLIDE_MODE),"excluded movement retains panel mode");
                io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();new com.vandorlabs.network.MessageProgrammableTrapdoor(pos,ScreenHousingTextures.DEFAULT_TRAPDOOR,position,true,0,0).withSlideMode(mode).toBytes(bytes);
                com.vandorlabs.network.MessageProgrammableTrapdoor packet=new com.vandorlabs.network.MessageProgrammableTrapdoor();packet.fromBytes(bytes);require(net.minecraftforge.fml.relauncher.ReflectionHelper.<Integer,com.vandorlabs.network.MessageProgrammableTrapdoor>getPrivateValue(com.vandorlabs.network.MessageProgrammableTrapdoor.class,packet,"slideMode")==mode,"packet motion lost");bytes.release();
                world.chunkLimit=true;TrapdoorPanelOwners.candidates(world,new AxisAlignedBB(pos).grow(4));world.chunkLimit=false;
                world.setBlockState(pos,state.withProperty(BlockTrapDoor.OPEN,true),2);
                double[] bounds=PanelPolyhedron.bounds(tile.panelFaces(state,1).get(0));
                Vec3d moved=new Vec3d(pos.getX()+(bounds[0]+bounds[3])/2,pos.getY()+(bounds[1]+bounds[4])/2,pos.getZ()+(bounds[2]+bounds[5])/2);
                // Whole leaves provide an interior point that is outside their owner's voxel.
                if(mode==4 || mode==5) {
                    require(!new AxisAlignedBB(pos).contains(moved),"whole slide failed to leave owner voxel");
                    RayTraceResult remote=com.vandorlabs.tiles.OffsetTrapdoorInteractions.trace(world,moved.addVector(normal[0]*.3,normal[1]*.3,normal[2]*.3),moved.addVector(-normal[0]*.3,-normal[1]*.3,-normal[2]*.3));
                    require(remote!=null && remote.getBlockPos().equals(pos),"moved leaf not selectable outside owner");
                    List<AxisAlignedBB> remoteBoxes=new ArrayList<>();com.vandorlabs.tiles.OffsetTrapdoorInteractions.addCollisions(world,new AxisAlignedBB(moved.x-.01,moved.y-.01,moved.z-.01,moved.x+.01,moved.y+.01,moved.z+.01),remoteBoxes);
                    require(!remoteBoxes.isEmpty(),"moved leaf lacks collision outside owner diagonal="+diagonal+" position="+position+" facing="+facing+" inverted="+inverted+" mode="+mode+" indexed="+TrapdoorPanelOwners.candidates(world,new AxisAlignedBB(pos).grow(9))+" state="+world.getBlockState(pos)+" moved="+moved);
                }
                world.setBlockToAir(pos);TrapdoorPanelCollision.bounds(tile,state,1);com.vandorlabs.tiles.OffsetTrapdoorInteractions.addCollisions(world,new AxisAlignedBB(pos).grow(9),new ArrayList<>());
                tile.invalidate();targetTile.invalidate();require(TrapdoorPanelOwners.candidates(world,new AxisAlignedBB(pos).grow(9)).isEmpty(),"invalidated owner retained in index");checks++;
            }
        System.out.println("PASS: trapdoor panel modes "+checks+" flat/sloped/facing/height cases, closed volumes, open ray/body clearance, NBT/items, packets, Duplifier and owner indexing");
    }
    private static void mesh(TileEntityProgrammableTrapdoor tile,IBlockState state,double[][] closed,int thicknessEnd) {
        StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();
        for(int[] face:TrapdoorGeometry.FACES) {
            double[] normal=PanelPolyhedron.unit(PanelPolyhedron.cross(delta(closed[face[1]],closed[face[0]]),delta(closed[face[2]],closed[face[0]])));
            for(int index:face)capture.pos(closed[index][0],closed[index][1],closed[index][2]).color(255,255,255,255)
                    .tex((index&1)==0?.1:.9,(index&(thicknessEnd==4?2:4))==0?.1:.9).normal((float)normal[0],(float)normal[1],(float)normal[2]).endVertex();
        }
        PanelMotion motion=tile.panelMotion(state);StaticSurfaceMesh[] panels=capture.finish().panels(motion,closed,thicknessEnd);
        for(int panel=0;panel<panels.length;panel++) {
            net.minecraft.client.renderer.vertex.VertexFormat format=BlockSurfaceFormat.get();net.minecraft.client.renderer.BufferBuilder buffer=new net.minecraft.client.renderer.BufferBuilder(4096);buffer.begin(7,format);panels[panel].draw(buffer,192,80);buffer.finishDrawing();
            int stride=format.getNextOffset(),uv=format.getUvOffsetById(0);List<double[][]> faces=new ArrayList<>();
            for(int start=0;start<buffer.getVertexCount();start+=4) {
                double[][] face=new double[4][3];
                for(int i=0;i<4;i++) {
                    int offset=(start+i)*stride;for(int axis=0;axis<3;axis++)face[i][axis]=buffer.getByteBuffer().getFloat(offset+axis*4);
                    for(int plane=0;plane<motion.planes();plane++)require(PanelMotion.distance(motion.plane(panel,plane),face[i])>=-1e-5,"render cap crossed cut plane");
                    double u=buffer.getByteBuffer().getFloat(offset+uv),v=buffer.getByteBuffer().getFloat(offset+uv+4);
                    require(Double.isFinite(u) && Double.isFinite(v) && u>=.1-1e-5 && u<=.9+1e-5 && v>=.1-1e-5 && v<=.9+1e-5,"render cap UV escaped source face");
                }
                faces.add(face);
            }
            require(Math.abs(volume(faces)-volume(tile.panelFaces(state,0).get(panel)))<1e-5,"rendered split is not sealed like collision");
        }
    }
    private static double volume(List<double[][]> faces){double result=0;for(double[][] face:faces)for(int i=1;i+1<face.length;i++)result+=PanelPolyhedron.dot(face[0],PanelPolyhedron.cross(face[i],face[i+1]))/6;return Math.abs(result);}
    private static double[] delta(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
