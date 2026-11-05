package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.NativeFlatWallGeometry;
import com.vandorlabs.render.PanelCornerGeometry;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public final class NativeFlatWallGeometryChecks {
    private static void require(boolean ok){if(!ok)throw new AssertionError("flat wall projection");}
    private static double volume(List<AABB> boxes){double n=0;for(AABB b:boxes)n+=b.getXsize()*b.getYsize()*b.getZsize();return n;}
    public static void main(String[] args){
        int count=0;Double[] arms={null,0D,6D,12D};
        for(Direction face:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST})for(int depth=0;depth<3;depth++){
            var straight=NativeFlatWallGeometry.build(face,depth,null);require(Math.abs(volume(straight.boxes())-.25)<1e-12);
            require(straight.side(face==Direction.EAST?1:face==Direction.WEST?0:.5,.5,face==Direction.SOUTH?1:face==Direction.NORTH?0:.5)==0);
            require(straight.side(face==Direction.EAST?0:face==Direction.WEST?1:.5,.5,face==Direction.SOUTH?0:face==Direction.NORTH?1:.5)==1);
            for(Double front:arms)for(Double back:arms)for(int flags=0;flags<4;flags++){
                if(front==null&&back==null)continue;
                var corner=new PanelCornerGeometry.FlatCorner(front,back,front==null?null:(flags&1)!=0,back==null?null:(flags&2)!=0,(flags&1)!=0,(flags&2)!=0);
                var g=NativeFlatWallGeometry.build(face,depth,corner);var shape=NativeFlatWallGeometry.collision(g);
                require(Math.abs(volume(g.boxes())-volume(shape.toAabbs()))<1e-12);
                for(AABB b:g.boxes())require(b.minX>=0&&b.minY==0&&b.minZ>=0&&b.maxX<=1&&b.maxY==1&&b.maxZ<=1&&b.getXsize()>0&&b.getZsize()>0);
                require(shape.bounds().equals(g.bounds()));require(NativeFlatWallGeometry.collision(g)==shape);count++;
            }
        }
        var warm=NativeFlatWallGeometry.build(Direction.NORTH,0,null);for(int i=0;i<50000;i++)NativeFlatWallGeometry.collision(warm);
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();long thread=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(thread);
        for(int i=0;i<20000;i++)NativeFlatWallGeometry.collision(warm);
        long bytes=bean.getThreadAllocatedBytes(thread)-before;require(bytes<1024);
        System.out.println("Native flat wall geometry PASS poses="+count+" exact union volumes, all facing/depth side mappings, stable cached objects; warmed bytes="+bytes);
    }
}
