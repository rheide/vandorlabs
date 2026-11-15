package com.vandorlabs.surface.core;

import com.vandorlabs.turntable.TurntableGeometry;
import java.util.*;
import java.util.function.Predicate;

/** Bounded loaded-only four-neighbor discovery using each engine's immutable position values. */
public final class SurfaceDiscovery {
    public interface Access<P> {
        boolean loaded(P cell);
        boolean surface(P cell);
        boolean hasTile(P cell);
        TurntableGeometry.Face face(P cell);
        boolean sameArtwork(P seed,P cell);
        P offset(P cell,int x,int y,int z);
    }
    public static <P> Set<P> connected(Access<P> access,P seed,Predicate<P> allowed,boolean matchingArtwork){
        if(!allowed.test(seed))throw new IllegalArgumentException("Surface is outside the selected pad");
        if(!access.loaded(seed)||!access.surface(seed))throw new IllegalArgumentException("Place Programmable Surface blocks in front of the controller");
        if(!access.hasTile(seed))throw new IllegalArgumentException("Surface tile is unavailable");
        TurntableGeometry.Face face=access.face(seed);
        Set<P> visited=new HashSet<P>(),members=new LinkedHashSet<P>();ArrayDeque<P> queue=new ArrayDeque<P>();queue.add(seed);
        while(!queue.isEmpty()){
            P cell=queue.removeFirst();if(!allowed.test(cell)||!visited.add(cell))continue;
            if(!access.loaded(cell))throw new IllegalArgumentException("Load every surface and its boundary before scanning");
            if(!access.surface(cell))continue;
            if(!access.hasTile(cell)){if(matchingArtwork)continue;throw new IllegalArgumentException("Surface tile is unavailable");}
            if(matchingArtwork&&!access.sameArtwork(seed,cell))continue;
            if(access.face(cell)!=face)throw new IllegalArgumentException("Connected surfaces must face the same direction");
            members.add(cell);if(members.size()>SurfaceLayout.MAX_CELLS)throw new IllegalArgumentException("Programmable Surfaces are limited to 32 x 32 blocks");
            queue.add(access.offset(cell,face.ux,face.uy,face.uz));queue.add(access.offset(cell,-face.ux,-face.uy,-face.uz));queue.add(access.offset(cell,face.vx,face.vy,face.vz));queue.add(access.offset(cell,-face.vx,-face.vy,-face.vz));
        }
        return Collections.unmodifiableSet(members);
    }
    private SurfaceDiscovery(){}
}
