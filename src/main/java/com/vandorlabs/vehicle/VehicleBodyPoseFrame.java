package com.vandorlabs.vehicle;

import com.vandorlabs.physics.RigidTransform;
import io.netty.buffer.ByteBuf;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;

/** Optional full body poses. Owned scalar data only; never a world, tile or mutable matrix alias. */
public final class VehicleBodyPoseFrame {
    public static final int MAGIC=0x47424F31,VERSION=1,MAX_BODIES=127,HEADER_BYTES=23,ENTRY_BYTES=104;
    public static final String NBT_KEY="CouplerBodyPose";
    private static final Set<String> FRAME_KEYS=Collections.unmodifiableSet(new HashSet<>(Arrays.asList("Version","Epoch","Layout","Bodies")));
    private static final Set<String> BODY_KEYS=Collections.unmodifiableSet(new HashSet<>(Arrays.asList("Carrier","Matrix")));
    public static final VehicleBodyPoseFrame NONE=new VehicleBodyPoseFrame();
    public static final class Body {
        public final BlockPos carrier;
        private final double[] values;
        public Body(BlockPos carrier,RigidTransform transform){
            if(carrier==null||transform==null||!carrier(carrier.toLong()))throw new IllegalArgumentException("Invalid general body carrier");
            this.carrier=carrier.toImmutable();values=new double[12];System.arraycopy(transform.r,0,values,0,9);values[9]=transform.x;values[10]=transform.y;values[11]=transform.z;validate(values);
        }
        private Body(long carrier,double[] values){this.carrier=BlockPos.fromLong(carrier);this.values=values;}
        public double rotation(int row,int column){if(row<0||row>2||column<0||column>2)throw new IndexOutOfBoundsException();return values[row*3+column];}
        public double translation(int axis){if(axis<0||axis>2)throw new IndexOutOfBoundsException();return values[9+axis];}
        /** The caller owns this exported matrix, including its public rotation array. */
        public RigidTransform transform(){return new RigidTransform(Arrays.copyOf(values,9),values[9],values[10],values[11]);}
    }
    public final long epoch,layout;
    public final List<Body> bodies;
    private VehicleBodyPoseFrame(){epoch=layout=0;bodies=Collections.emptyList();}
    public VehicleBodyPoseFrame(long epoch,long layout,List<Body> bodies){
        if(epoch<=0||bodies==null||bodies.size()>MAX_BODIES||bodies.isEmpty()!=(layout==0))throw new IllegalArgumentException("Invalid general body generation/layout/count");
        for(int i=0;i<bodies.size();i++){if(bodies.get(i)==null)throw new IllegalArgumentException("Missing general body");for(int j=0;j<i;j++)if(bodies.get(i).carrier.equals(bodies.get(j).carrier))throw new IllegalArgumentException("Duplicate general body carrier");}
        this.epoch=epoch;this.layout=layout;this.bodies=Collections.unmodifiableList(new ArrayList<>(bodies));
    }
    public static VehicleBodyPoseFrame reset(long epoch){return new VehicleBodyPoseFrame(epoch,0,Collections.emptyList());}
    public boolean present(){return epoch!=0;}
    public boolean reset(){return present()&&layout==0;}
    public int encodedBytes(){return present()?HEADER_BYTES+ENTRY_BYTES*bodies.size():0;}
    void write(ByteBuf out){
        if(!present())return;out.writeInt(MAGIC);out.writeByte(VERSION);out.writeLong(epoch);out.writeLong(layout);out.writeShort(bodies.size());
        for(Body body:bodies){out.writeLong(body.carrier.toLong());for(double value:body.values)out.writeDouble(value);}
    }
    /** Magic was consumed. The enclosing packet validates any following known ownership-scope tail. */
    static VehicleBodyPoseFrame read(ByteBuf in){
        if(in.readableBytes()<HEADER_BYTES-4||in.readUnsignedByte()!=VERSION)throw new IllegalArgumentException("Unsupported general body tail");
        long epoch=in.readLong(),layout=in.readLong();int count=in.readUnsignedShort();header(epoch,layout,count);
        if(in.readableBytes()<count*ENTRY_BYTES)throw new IllegalArgumentException("Invalid general body tail length");
        int start=in.readerIndex();
        for(int i=0;i<count;i++){
            int at=start+i*ENTRY_BYTES;long key=in.getLong(at);if(!carrier(key))throw new IllegalArgumentException("Invalid general body carrier");
            for(int j=0;j<i;j++)if(key==in.getLong(start+j*ENTRY_BYTES))throw new IllegalArgumentException("Duplicate general body carrier");
            rotation(in.getDouble(at+8),in.getDouble(at+16),in.getDouble(at+24),in.getDouble(at+32),in.getDouble(at+40),in.getDouble(at+48),in.getDouble(at+56),in.getDouble(at+64),in.getDouble(at+72));
            translation(in.getDouble(at+80));translation(in.getDouble(at+88));translation(in.getDouble(at+96));
        }
        List<Body> bodies=new ArrayList<>(count);
        for(int i=0;i<count;i++){long key=in.readLong();double[] values=new double[12];for(int n=0;n<12;n++)values[n]=in.readDouble();bodies.add(new Body(key,values));}
        return new VehicleBodyPoseFrame(epoch,layout,bodies);
    }
    public NBTTagCompound writeNBT(){
        if(!present())throw new IllegalStateException("An absent general body pose has no saved envelope");
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Version",VERSION);tag.setLong("Epoch",epoch);tag.setLong("Layout",layout);NBTTagList rows=new NBTTagList();
        for(Body body:bodies){NBTTagCompound row=new NBTTagCompound();row.setLong("Carrier",body.carrier.toLong());NBTTagList matrix=new NBTTagList();for(double value:body.values)matrix.appendTag(new NBTTagDouble(value));row.setTag("Matrix",matrix);rows.appendTag(row);}tag.setTag("Bodies",rows);return tag;
    }
    public static VehicleBodyPoseFrame readNBT(NBTTagCompound tag){
        if(tag==null||!tag.getKeySet().equals(FRAME_KEYS)||!tag.hasKey("Version",3)||tag.getInteger("Version")!=VERSION||!tag.hasKey("Epoch",4)||!tag.hasKey("Layout",4)||!tag.hasKey("Bodies",9))throw new IllegalArgumentException("Invalid saved general body envelope");
        NBTTagList rows=(NBTTagList)tag.getTag("Bodies");int count=rows.tagCount();long epoch=tag.getLong("Epoch"),layout=tag.getLong("Layout");header(epoch,layout,count);
        if(rows.getTagType()!=10&&!(count==0&&rows.getTagType()==0))throw new IllegalArgumentException("Invalid saved general body rows");
        for(int i=0;i<count;i++){
            NBTTagCompound row=rows.getCompoundTagAt(i);if(!row.getKeySet().equals(BODY_KEYS)||!row.hasKey("Carrier",4)||!row.hasKey("Matrix",9))throw new IllegalArgumentException("Invalid saved general body row");
            long key=row.getLong("Carrier");if(!carrier(key))throw new IllegalArgumentException("Invalid saved general body carrier");for(int j=0;j<i;j++)if(key==rows.getCompoundTagAt(j).getLong("Carrier"))throw new IllegalArgumentException("Duplicate saved general body carrier");
            NBTTagList matrix=(NBTTagList)row.getTag("Matrix");if(matrix.tagCount()!=12||matrix.getTagType()!=6)throw new IllegalArgumentException("Invalid saved general body matrix");
            rotation(matrix.getDoubleAt(0),matrix.getDoubleAt(1),matrix.getDoubleAt(2),matrix.getDoubleAt(3),matrix.getDoubleAt(4),matrix.getDoubleAt(5),matrix.getDoubleAt(6),matrix.getDoubleAt(7),matrix.getDoubleAt(8));translation(matrix.getDoubleAt(9));translation(matrix.getDoubleAt(10));translation(matrix.getDoubleAt(11));
        }
        List<Body> bodies=new ArrayList<>(count);
        for(int i=0;i<count;i++){NBTTagCompound row=rows.getCompoundTagAt(i);NBTTagList matrix=(NBTTagList)row.getTag("Matrix");double[] values=new double[12];for(int n=0;n<12;n++)values[n]=matrix.getDoubleAt(n);bodies.add(new Body(row.getLong("Carrier"),values));}return new VehicleBodyPoseFrame(epoch,layout,bodies);
    }
    private static void header(long epoch,long layout,int count){if(epoch<=0||count<0||count>MAX_BODIES||(count==0)!=(layout==0))throw new IllegalArgumentException("Invalid general body generation/layout/count");}
    private static boolean carrier(long packed){long x=packed>>38,y=(packed>>26)&4095L,z=packed<<38>>38;return x>=0&&x<64&&y<64&&z>=0&&z<64;}
    private static void validate(double[] v){rotation(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8]);translation(v[9]);translation(v[10]);translation(v[11]);}
    private static void translation(double value){if(!Double.isFinite(value)||Math.abs(value)>65536)throw new IllegalArgumentException("Invalid general body translation");}
    private static void rotation(double a,double b,double c,double d,double e,double f,double g,double h,double i){
        if(!Double.isFinite(a)||!Double.isFinite(b)||!Double.isFinite(c)||!Double.isFinite(d)||!Double.isFinite(e)||!Double.isFinite(f)||!Double.isFinite(g)||!Double.isFinite(h)||!Double.isFinite(i)||Math.abs(a*a+b*b+c*c-1)>1e-7||Math.abs(d*d+e*e+f*f-1)>1e-7||Math.abs(g*g+h*h+i*i-1)>1e-7||Math.abs(a*d+b*e+c*f)>1e-7||Math.abs(a*g+b*h+c*i)>1e-7||Math.abs(d*g+e*h+f*i)>1e-7||Math.abs(a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g)-1)>1e-7)throw new IllegalArgumentException("Invalid general body rotation");
    }
}
