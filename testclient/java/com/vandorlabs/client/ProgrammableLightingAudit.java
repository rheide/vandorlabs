package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableWall;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/** Inspects existing submitted geometry without a GL context or renderer changes. */
public final class ProgrammableLightingAudit {
    private static final TextureAtlasSprite SPRITE = new TextureAtlasSprite("audit") { };

    public static void main(String[] args) throws Exception {
        net.minecraft.init.Bootstrap.register();
        net.minecraft.client.renderer.OpenGlHelper.lastBrightnessX=80;
        net.minecraft.client.renderer.OpenGlHelper.lastBrightnessY=192;
        SPRITE.setIconWidth(16);
        SPRITE.setIconHeight(16);
        SPRITE.initSprite(256, 256, 32, 48, false);
        System.out.println("Geometry inspection only; no shader or visual acceptance claim.");
        System.out.println("case,quads,front_inward,back_inward,top_inward,bottom_inward,normal_attribute,vertex_lightmap");
        BufferBuilder plain = legacyBuffer();
        invoke("panelRect", plain, SPRITE, null, 0, 0, 16, 16);
        rims(plain, null);
        inspect("wall_isolated", plain);

        for (int shape = 0; shape < 4; shape++) {
            PortholeHex.Slice slice = new PortholeHex(1, 1, shape).slice(0, 0);
            BufferBuilder hole = legacyBuffer();
            for (double[] q : slice.frameQuads)
                invoke("panelQuad", hole, SPRITE, null, q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7]);
            for (double[] edge : slice.hexEdges)
                invoke("rimSegment", hole, SPRITE, null, edge[0], edge[1], edge[2], edge[3], 4D);
            rims(hole, null);
            inspect("porthole_shape_" + shape, hole);
        }

        Constructor<?> corner = BlockProgrammableWall.FlatCorner.class.getDeclaredConstructors()[0];
        corner.setAccessible(true);
        Object joined = corner.newInstance(6D, null, Boolean.TRUE, null, true, true);
        BufferBuilder bend = legacyBuffer();
        invoke("renderFlatWall", bend, SPRITE, SPRITE, 6D, joined);
        inspect("wall_corner", bend);

        BlockProgrammableWall wall = new BlockProgrammableWall("audit_wall", BlockProgrammableWall.Shape.PLAIN);
        BlockProgrammableWall porthole = new BlockProgrammableWall("audit_porthole", BlockProgrammableWall.Shape.PORTHOLE);
        if (wall.getDefaultState().useNeighborBrightness() != porthole.getDefaultState().useNeighborBrightness()
                || wall.getDefaultState().getLightOpacity() != porthole.getDefaultState().getLightOpacity())
            throw new IllegalStateException("Wall/porthole light propagation settings differ");
        System.out.println("Wall/porthole propagation: neighborBrightness=" + wall.getDefaultState().useNeighborBrightness()
                + ", lightOpacity=" + wall.getDefaultState().getLightOpacity());
        DiagonalSurfaceChecks.run();
        BufferBuilder trapdoor = new BufferBuilder(4096);
        trapdoor.begin(7, BlockSurfaceFormat.get());
        TEProgrammableTrapdoor.drawLeaf(trapdoor, SPRITE, 0, false, 0, 0, (192 << 16) | 80);
        trapdoor.finishDrawing();
        VertexFormat format = trapdoor.getVertexFormat();
        ByteBuffer data = trapdoor.getByteBuffer();
        for (int vertex = 0; vertex < trapdoor.getVertexCount(); vertex++) {
            int offset = vertex * format.getNextOffset();
            int light = offset + format.getUvOffsetById(1);
            if (data.getShort(light) != 80 || data.getShort(light + 2) != 192)
                throw new IllegalStateException("trapdoor lightmap channels differ from control");
            if (vertex % 4 == 0) {
                double[] a = position(data, offset), b = position(data, offset + format.getNextOffset());
                double[] c = position(data, offset + 2 * format.getNextOffset());
                double[] cross = cross(a, b, c);
                int normal = offset + format.getNormalOffset();
                double dot = cross[0] * data.get(normal) + cross[1] * data.get(normal + 1)
                        + cross[2] * data.get(normal + 2);
                if (dot <= 0) throw new IllegalStateException("trapdoor normal disagrees with winding");
            }
        }
        System.out.println("Trapdoor control: explicit normals agree with winding; block=80, sky=192.");
        surfaces();
        RampRenderChecks.run();
        System.out.println("PASS: programmable surfaces have matching normals/winding and separate live lightmap channels");
    }

    private static BufferBuilder legacyBuffer() {
        BufferBuilder buffer = new BufferBuilder(4096);
        buffer.begin(7, BlockSurfaceFormat.get());
        return buffer;
    }

    private static void rims(BufferBuilder buffer, DiagonalPortholeMesh mesh) throws Exception {
        for (double y : new double[]{0, 16}) invoke("topRim", buffer, SPRITE, mesh, y, 0D, 16D, 4D);
        for (double x : new double[]{0, 16}) invoke("sideRim", buffer, SPRITE, mesh, x, 0D, 16D, 4D);
    }

    private static void invoke(String name, Object... args) throws Exception {
        for (Method method : TEAnimatedScreenSelector.class.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                method.invoke(null, args);
                return;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static void inspect(String label, BufferBuilder buffer) {
        buffer.finishDrawing();
        VertexFormat format = buffer.getVertexFormat();
        ByteBuffer data = buffer.getByteBuffer();
        int[] inward = new int[4];
        for (int i = 0; i < buffer.getVertexCount(); i += 4) {
            int offset = i * format.getNextOffset();
            double[] a = position(data, offset), b = position(data, offset + format.getNextOffset());
            double[] c = position(data, offset + 2 * format.getNextOffset());
            double[] d = position(data, offset + 3 * format.getNextOffset());
            double[] n = cross(a, b, c);
            if (same(a[2], b[2], c[2], d[2])) {
                if (Math.abs(a[2] - 6) < 1E-6 && n[2] > 1E-6) inward[0]++;
                if (Math.abs(a[2] - 10) < 1E-6 && n[2] < -1E-6) inward[1]++;
            }
            if (same(a[1], b[1], c[1], d[1])) {
                if (Math.abs(a[1] - 16) < 1E-6 && n[1] < -1E-6) inward[2]++;
                if (Math.abs(a[1]) < 1E-6 && n[1] > 1E-6) inward[3]++;
            }
        }
        if(inward[0]+inward[1]+inward[2]+inward[3]!=0)
            throw new IllegalStateException(label+": inward boundary face");
        verifyInputs(buffer,label);
        System.out.printf("%s,%d,%d,%d,%d,%d,%s,%s%n", label, buffer.getVertexCount() / 4,
                inward[0], inward[1], inward[2], inward[3], format.hasNormal(), format.hasUvOffset(1));
    }

    private static void verifyInputs(BufferBuilder buffer,String label) {
        VertexFormat format=buffer.getVertexFormat();ByteBuffer data=buffer.getByteBuffer();
        if(!format.hasNormal() || !format.hasUvOffset(1))throw new IllegalStateException(label+": missing shader inputs");
        int stride=format.getNextOffset();
        for(int i=0;i<buffer.getVertexCount();i++) {
            int offset=i*stride,lm=offset+format.getUvOffsetById(1),normal=offset+format.getNormalOffset();
            if(data.getShort(lm)!=80 || data.getShort(lm+2)!=192)throw new IllegalStateException(label+": incorrect lightmap");
            double nx=data.get(normal)/127D,ny=data.get(normal+1)/127D,nz=data.get(normal+2)/127D;
            if(Math.abs(nx*nx+ny*ny+nz*nz-1)>.03)throw new IllegalStateException(label+": invalid unit normal");
            if(i%4==0) {
                double[] a=position(data,offset),b=position(data,offset+stride),c=position(data,offset+2*stride),d=position(data,offset+3*stride);
                double ux=c[0]-a[0],uy=c[1]-a[1],uz=c[2]-a[2],vx=d[0]-b[0],vy=d[1]-b[1],vz=d[2]-b[2];
                if((uy*vz-uz*vy)*nx+(uz*vx-ux*vz)*ny+(ux*vy-uy*vx)*nz<=0)
                    throw new IllegalStateException(label+": normal/winding disagreement");
            }
        }
    }

    private static void surfaces() throws Exception {
        for(int mode=0;mode<3;mode++)for(boolean inverted:new boolean[]{false,true})for(int shape=0;shape<4;shape++) {
            DiagonalPortholeMesh mesh=new DiagonalPortholeMesh(mode,inverted,.25,.75,null);
            PortholeHex.Slice slice=new PortholeHex(1,1,shape).slice(0,0);
            BufferBuilder buffer=legacyBuffer();
            for(double[] q:slice.frameQuads)
                invoke("panelQuad",buffer,SPRITE,mesh,q[0],q[1],q[2],q[3],q[4],q[5],q[6],q[7]);
            rims(buffer,mesh);TEAnimatedScreenSelector.drawPortholeGlassGeometry(buffer,slice,mesh);
            buffer.finishDrawing();verifyInputs(buffer,"diagonal porthole "+mode+"/"+inverted+"/"+shape);
        }
        for(com.vandorlabs.render.ScreenHousingMesh mesh:new com.vandorlabs.render.ScreenHousingMesh[]{
                com.vandorlabs.render.ScreenHousingMesh.cube(),com.vandorlabs.render.ScreenHousingMesh.console(),
                com.vandorlabs.render.ScreenHousingMesh.halfConsole(),com.vandorlabs.render.ScreenHousingMesh.diagonal(false),
                com.vandorlabs.render.ScreenHousingMesh.diagonal(true)})for(boolean ceiling:new boolean[]{false,true}) {
            BufferBuilder buffer=legacyBuffer();invoke("drawWallFaces",buffer,SPRITE,mesh.quads,ceiling);
            buffer.finishDrawing();verifyInputs(buffer,"housing/ceiling="+ceiling);
        }
        for(com.vandorlabs.render.ScreenSurface.Kind kind:com.vandorlabs.render.ScreenSurface.Kind.values())
            for(boolean inverted:new boolean[]{false,true})for(boolean ceiling:new boolean[]{false,true}) {
                BufferBuilder buffer=legacyBuffer();
                invoke("drawScreenQuad",buffer,com.vandorlabs.render.ScreenSurface.quad(kind,inverted),0D,1D,0D,1D,ceiling);
                buffer.finishDrawing();verifyInputs(buffer,"screen "+kind);
            }
        for(boolean full:new boolean[]{false,true})for(boolean small:new boolean[]{false,true})for(int depth=0;depth<3;depth++) {
            BufferBuilder buffer=legacyBuffer();
            TEAnimatedScreenSelector.drawInputQuad(buffer,com.vandorlabs.render.InputSurfaceLayout.ceilingInput(full,small,depth).surface,new double[]{0,1});
            buffer.finishDrawing();verifyInputs(buffer,"ceiling input");
        }
        for(double depth:new double[]{7/16D,9/16D}) {
            BufferBuilder buffer=legacyBuffer();TEProgrammableGlass.pane(buffer,depth,.2F,.85F,.95F,.0513F);
            buffer.finishDrawing();verifyInputs(buffer,"glass");
        }
        com.vandorlabs.ramp.RampGeometry.Box box=new com.vandorlabs.ramp.RampGeometry.Box(0,0,0,1,1,1);
        for(net.minecraft.util.EnumFacing face:net.minecraft.util.EnumFacing.values()) {
            BufferBuilder buffer=legacyBuffer();
            TEControlledRamp.emitFace(buffer,box,face,SPRITE,0,1,0,0,0xFFFFFF);
            buffer.finishDrawing();verifyInputs(buffer,"ramp "+face);
        }
    }

    private static boolean same(double a, double b, double c, double d) {
        return Math.abs(a - b) + Math.abs(a - c) + Math.abs(a - d) < 1E-6;
    }

    private static double[] position(ByteBuffer data, int offset) {
        return new double[]{data.getFloat(offset), data.getFloat(offset + 4), data.getFloat(offset + 8)};
    }

    private static double[] cross(double[] a, double[] b, double[] c) {
        double x = b[0] - a[0], y = b[1] - a[1], z = b[2] - a[2];
        double u = c[0] - a[0], v = c[1] - a[1], w = c[2] - a[2];
        return new double[]{y * w - z * v, z * u - x * w, x * v - y * u};
    }
}
