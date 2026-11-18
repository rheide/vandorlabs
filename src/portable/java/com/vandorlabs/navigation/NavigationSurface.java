package com.vandorlabs.navigation;

import java.util.Arrays;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Loaded surface observations at one palette byte per two blocks on each axis. */
public final class NavigationSurface {
    public static final int MAX_TILES=16384, TILE_BYTES=64, MAX_SWEEPS=64, MAX_SAMPLES_PER_TICK=64;
    public static final int UNKNOWN=0;
    public static final String STORAGE="vandorlabs_navigation_surface";
    private static final int TILE_RECORD=TILE_BYTES+8;

    private NavigationSurface(){}
    private static final int SENTINEL_PERIOD=160,SURVEY_PERIOD=480;
    public static int period(int radius){return radius==NavigationRange.SENTINEL_RADAR_RADIUS?SENTINEL_PERIOD:SURVEY_PERIOD;}
    public static boolean validRadius(int radius){return radius==128||radius==256;}

    public static final class Atlas {
        private final Map<Long,byte[]> tiles=new HashMap<>();
        private UUID identity=UUID.randomUUID();
        public UUID identity(){return identity;}
        public void setIdentity(UUID identity){this.identity=java.util.Objects.requireNonNull(identity);}
        private long revision,hotKey=Long.MIN_VALUE;
        private byte[] hotTile;
        private byte[] tile(int x,int z){
            long position=key(x,z);
            if(position!=hotKey){hotKey=position;hotTile=tiles.get(position);}
            return hotTile;
        }
        public long revision(){return revision;}
        public int tileCount(){return tiles.size();}
        public int get(int x,int z){
            byte[] tile=tile(x>>4,z>>4);
            return tile==null?UNKNOWN:tile[index(x,z)]&255;
        }
        public boolean put(int x,int z,int color){
            if(color<=0||color>255)return false;
            long key=key(x>>4,z>>4);
            byte[] tile=tile(x>>4,z>>4);
            if(tile==null){
                if(tiles.size()>=MAX_TILES)return false;
                tile=new byte[TILE_BYTES];
                tiles.put(key,tile);hotTile=tile;
            }
            int index=index(x,z);
            if(tile[index]==(byte)color)return false;
            tile[index]=(byte)color;
            revision++;
            return true;
        }
        public byte[] serialize(){
            ByteBuffer data=ByteBuffer.allocate(tiles.size()*TILE_RECORD);
            tiles.forEach((position,pixels)->data.putLong(position).put(pixels));
            return data.array();
        }
        public void restore(byte[] encoded){
            if(encoded.length%TILE_RECORD!=0||encoded.length>TILE_RECORD*MAX_TILES)return;
            ByteBuffer data=ByteBuffer.wrap(encoded);
            int limit=(NavigationMap.LIMIT>>4)+1;
            while(data.hasRemaining()){
                long position=data.getLong();
                byte[] pixels=new byte[TILE_BYTES];
                data.get(pixels);
                if(Math.abs(position>>32)<=limit&&Math.abs((long)(int)position)<=limit&&tiles.size()<MAX_TILES)
                    tiles.putIfAbsent(position,pixels);
            }
            hotKey=Long.MIN_VALUE;hotTile=null;
        }
    }
    public interface Sampler {int sample(int x,int z);}

    /** A controller only receives work from the sector swept on this tick. */
    public static final class Scanner {
        private static final int[][] SENTINEL=sectors(NavigationRange.SENTINEL_RADAR_RADIUS,SENTINEL_PERIOD),
                SURVEY=sectors(NavigationRange.SURVEY_RADAR_RADIUS,SURVEY_PERIOD);
        private static final class Request {
            final long expires;
            final BooleanSupplier available;
            Request(long expires,BooleanSupplier available){this.expires=expires;this.available=available;}
        }
        private final LinkedHashMap<Sweep,Request> sweeps=new LinkedHashMap<>();
        private final Sweep[] pending=new Sweep[MAX_SWEEPS];
        private final int[] sampledX=new int[MAX_SAMPLES_PER_TICK],sampledZ=new int[MAX_SAMPLES_PER_TICK];

        public void request(int x,int z,int radius,long now,BooleanSupplier available){
            if(!validRadius(radius))return;
            Sweep sweep=new Sweep(x,z,radius);
            if(sweeps.size()<MAX_SWEEPS||sweeps.containsKey(sweep))sweeps.put(sweep,new Request(now+60,available));
        }
        public boolean active(){return !sweeps.isEmpty();}
        public int tick(Atlas atlas,long time,int limit,long deadline,Sampler sampler){
            sweeps.values().removeIf(request->request.expires<time);
            if(sweeps.isEmpty()||limit<=0||System.nanoTime()>=deadline)return 0;
            int budget=Math.min(MAX_SAMPLES_PER_TICK,limit),count=0,checked=0;
            // A stable rotation gives each observer turns under contention. Later revolutions visit different pixels.
            sweeps.keySet().toArray(pending);
            int sources=sweeps.size(),first=(int)Math.floorMod(time,sources);
            for(int turn=0;turn<sources&&checked<budget&&System.nanoTime()<deadline;turn++){
                Sweep source=pending[(first+turn)%sources];
                if(!sweeps.get(source).available.getAsBoolean()){sweeps.remove(source);continue;}
                int period=period(source.radius);
                int[] sector=(source.radius==128?SENTINEL:SURVEY)[(int)Math.floorMod(time,period)];
                int allowance=Math.max(1,budget/sources);
                int start=(int)Math.floorMod(Math.floorDiv(time,period)*61,Math.max(1,sector.length));
                for(int i=0;i<sector.length&&i<allowance&&checked<budget&&System.nanoTime()<deadline;i++){
                    int offset=sector[(start+i)%sector.length];
                    int x=(source.x&~1)+(offset>>16),z=(source.z&~1)+(short)offset;
                    checked++;
                    if(NavigationMap.bound(x)!=x||NavigationMap.bound(z)!=z)continue;
                    boolean duplicate=false;
                    for(int k=0;k<count;k++)if(sampledX[k]==x&&sampledZ[k]==z){duplicate=true;break;}
                    if(duplicate)continue;
                    sampledX[count]=x;sampledZ[count++]=z;
                    atlas.put(x,z,sampler.sample(x,z));
                }
            }
            return checked;
        }
    }
    public static final int MAX_AREAS=1024;
    private static final int[] EMPTY_AREAS=new int[0];
    public static int[] validAreas(int[] circles){
        if(circles.length>MAX_AREAS*3||circles.length%3!=0)return EMPTY_AREAS;
        for(int i=0;i<circles.length;i+=3){
            if(NavigationMap.bound(circles[i])!=circles[i]||NavigationMap.bound(circles[i+1])!=circles[i+1]
                    ||!validRadius(circles[i+2]))return EMPTY_AREAS;
        }
        return circles;
    }
    public static int[] mergeAreas(int[] saved,int[] observed){
        if(validAreas(saved)!=saved||validAreas(observed)!=observed||observed.length==0)return saved;
        int[] result=null;
        int end=saved.length;
        boolean changed=false;
        for(int i=0;i<observed.length;i+=3){
            int[] current=result==null?saved:result;
            int x=observed[i],z=observed[i+1],r=observed[i+2];
            boolean contained=false;
            for(int j=0;j<end;j+=3){
                if(encloses(current[j],current[j+1],current[j+2],x,z,r)){contained=true;break;}
            }
            if(contained)continue;
            if(result==null)result=Arrays.copyOf(saved,Math.min(MAX_AREAS*3,saved.length+observed.length));
            int write=0;
            for(int j=0;j<end;j+=3){
                if(encloses(x,z,r,result[j],result[j+1],result[j+2]))continue;
                System.arraycopy(result,j,result,write,3);write+=3;
            }
            if(write<result.length){
                end=write;result[end++]=x;result[end++]=z;result[end++]=r;changed=true;
            }
        }
        return changed?Arrays.copyOf(result,end):saved;
    }
    private static boolean encloses(int x,int z,int radius,int ox,int oz,int otherRadius){
        long gap=radius-otherRadius;
        return gap>=0&&NavigationMap.covers(ox,oz,x,z,(int)gap);
    }

    /** Self-contained chip image. Zero pixels leave the generated terrain visible. */
    public static final class Snapshot {
        public final int x,z,radius;
        private final byte[] pixels;
        private final int hash;
        public Snapshot(int x,int z,int radius,byte[] pixels){
            if(!validRadius(radius)||pixels.length!=radius*radius||NavigationMap.bound(x)!=x||NavigationMap.bound(z)!=z)
                throw new IllegalArgumentException("Surface snapshot bounds");
            this.x=x;this.z=z;this.radius=radius;this.pixels=pixels.clone();
            hash=31*((31*x+z)*31+radius)+Arrays.hashCode(this.pixels);
        }
        public int get(int wx,int wz){
            int col=((wx-(x&~1))+radius)>>1,row=((wz-(z&~1))+radius)>>1;
            return col<0||row<0||col>=radius||row>=radius?UNKNOWN:pixels[row*radius+col]&255;
        }
        public byte[] pixels(){return pixels.clone();}
        public int bytes(){return 64+pixels.length;}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){
            if(this==other)return true;
            if(!(other instanceof Snapshot))return false;
            Snapshot s=(Snapshot)other;
            return hash==s.hash&&x==s.x&&z==s.z&&radius==s.radius&&Arrays.equals(pixels,s.pixels);
        }
    }
    public static Snapshot capture(Atlas atlas,int[] areas,int x,int z){
        if(atlas==null)return null;
        int nearest=-1;
        long best=Long.MAX_VALUE;
        for(int i=0;i<areas.length;i+=3){
            long dx=(long)areas[i]-x,dz=(long)areas[i+1]-z,dist=dx*dx+dz*dz;
            if(dist<best&&dist<(long)areas[i+2]*areas[i+2]){best=dist;nearest=i;}
        }
        if(nearest<0)return null;
        int cx=areas[nearest],cz=areas[nearest+1],r=areas[nearest+2];
        byte[] pixels=new byte[r*r];
        boolean any=false;
        for(int row=0;row<r;row++)for(int col=0;col<r;col++){
            int dx=col*2-r,dz=row*2-r;
            if(dx*dx+dz*dz>=r*r)continue;
            int color=atlas.get((cx&~1)+dx,(cz&~1)+dz);
            pixels[row*r+col]=(byte)color;
            any|=color!=0;
        }
        return any?new Snapshot(cx,cz,r,pixels):null;
    }
    public static final class View {
        public static final View EMPTY=new View(null,EMPTY_AREAS,null);
        private final Atlas atlas;
        private final int[] areas;
        private final NavigationCoverage coverage;
        private final Snapshot chip;
        public View(Atlas atlas,int[] areas,Snapshot chip){
            this.atlas=atlas;this.areas=areas;this.chip=chip;
            coverage=areas.length==0?null:new NavigationCoverage(areas);
        }
        public Snapshot capture(int x,int z){return NavigationSurface.capture(atlas,areas,x,z);}
        public boolean hasAtlas(){return atlas!=null&&coverage!=null;}
        public boolean hasSurface(){return hasAtlas()||chip!=null;}
        public long revision(){return hasAtlas()?atlas.revision():0;}
        public int bytes(){return (chip==null?0:chip.bytes())+areas.length*4;}
        public int color(int x,int z){
            int recent=hasAtlas()&&coverage.contains(x,z)?atlas.get(x,z):UNKNOWN;
            return recent!=UNKNOWN?recent:chip==null?UNKNOWN:chip.get(x,z);
        }
        @Override public int hashCode(){return 31*Arrays.hashCode(areas)+(chip==null?0:chip.hashCode());}
        @Override public boolean equals(Object other){
            if(this==other)return true;
            if(!(other instanceof View))return false;
            View view=(View)other;
            return atlas==view.atlas&&Arrays.equals(areas,view.areas)&&java.util.Objects.equals(chip,view.chip);
        }
    }
    private static final class Sweep {
        final int x,z,radius;
        Sweep(int x,int z,int radius){this.x=x;this.z=z;this.radius=radius;}
        @Override public int hashCode(){return (31*x+z)*31+radius;}
        @Override public boolean equals(Object other){
            if(!(other instanceof Sweep))return false;
            Sweep s=(Sweep)other;return x==s.x&&z==s.z&&radius==s.radius;
        }
    }
    private static int[][] sectors(int radius,int period){
        int[][] sectors=new int[period][];
        int[] counts=new int[period];
        // Every angular sector fits into the square; dynamically grow to retain only its own points.
        for(int x=-radius;x<radius;x+=2)for(int z=-radius;z<radius;z+=2){
            if(x*x+z*z>=radius*radius)continue;
            double radians=Math.atan2(x,-z);
            int phase=Math.min(period-1,(int)((radians<0?radians+2*Math.PI:radians)*period/(2*Math.PI)));
            int[] points=sectors[phase];
            if(points==null)points=new int[128];
            if(counts[phase]==points.length)points=Arrays.copyOf(points,points.length*2);
            points[counts[phase]++]=(x<<16)|(z&65535);
            sectors[phase]=points;
        }
        for(int i=0;i<period;i++)sectors[i]=sectors[i]==null?new int[0]:Arrays.copyOf(sectors[i],counts[i]);
        return sectors;
    }
    private static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    private static int index(int x,int z){return ((z&15)>>1)*8+((x&15)>>1);}
}
