package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.render.DiagonalPanelContact;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Loaded diagonal artwork groups. Only immutable local coordinates survive discovery. */
final class NativeDiagonalLayouts {
    /** Temporary read-only discovery; cached groups retain neither members nor this access. */
    interface Access<M> {
        boolean loaded(BlockPos pos);
        M member(BlockPos pos);
        BlockState state(BlockPos pos);
        BlockState localState(M member);
        com.vandorlabs.persistence.WallSettings settings(M member);
        int mainTexture(M member);
        int textureLayout(M member);
        boolean solid(BlockPos pos,M member);
        default com.vandorlabs.persistence.PanelGlassSettings glass(M member){return com.vandorlabs.persistence.PanelGlassSettings.fresh();}
        default PanelPlayback playback(M member){return PanelPlayback.DEFAULT;}
    }
    private record LevelAccess(Level level) implements Access<NativeWallEntity> {
        public boolean loaded(BlockPos pos){return NativeFluidWorld.loaded(level,pos);}
        public NativeWallEntity member(BlockPos pos){return level.getBlockEntity(pos) instanceof NativeWallEntity host&&!host.isRemoved()?host:null;}
        public BlockState state(BlockPos pos){return level.getBlockState(pos);}
        public BlockState localState(NativeWallEntity host){return host.getBlockState();}
        public com.vandorlabs.persistence.WallSettings settings(NativeWallEntity host){return host.wallSettings();}
        public int mainTexture(NativeWallEntity host){return host.settings().mainTexture;}
        public int textureLayout(NativeWallEntity host){return host.settings().layout;}
        public com.vandorlabs.persistence.PanelGlassSettings glass(NativeWallEntity host){return host.panelGlassSettings();}
        public PanelPlayback playback(NativeWallEntity host){return host.panelPlayback();}
        public boolean solid(BlockPos pos,NativeWallEntity host){return NativeDiagonalWallTopology.solid(level,pos,host.getBlockState());}
    }
    record Capture<M>(Map<BlockPos,M> members,Map<Long,NativePanelLayout> layouts,boolean capped) {}
    private static <M> int mode(Access<M> access,M member){var settings=access.settings(member);return com.vandorlabs.persistence.WallSettings.geometry(settings.halfHeight,settings.fullWidth);}

    static NativePanelLayout layout(NativeWallEntity root){return query(root,NativePanelGroups.Mode.MATERIAL);}
    private static NativeWallLayouts.Group group(NativeWallEntity host,NativePanelGroups.Mode mode){return switch(mode){case MATERIAL->host.textureGroup;case APERTURE->host.apertureGroup;case GLASS->host.glassGroup;};}
    private static void group(NativeWallEntity host,NativePanelGroups.Mode mode,NativeWallLayouts.Group group){switch(mode){case MATERIAL->host.textureGroup=group;case APERTURE->host.apertureGroup=group;case GLASS->host.glassGroup=group;}}
    static NativeDiagonalGlassLayout glass(NativeWallEntity root){
        var geometry=(NativeDiagonalWallGeometry.Geometry)root.wallAppearance().geometry();int mode=geometry.pose().mode();
        if(!root.isGlass()||root.getLevel()==null)return NativeDiagonalGlassLayout.unit(mode);
        var layout=root.getLevel().isClientSide&&root.panelGlassSettings().material>=0?query(root,NativePanelGroups.Mode.GLASS):NativePanelLayout.UNIT;
        return NativeDiagonalGlassLayout.capture(NativeDiagonalWallTopology.access(root.getLevel()),root.getBlockPos(),root.getBlockState(),layout);
    }
    static NativePortholeLayout aperture(NativeWallEntity root){
        Level level=root.getLevel();int shape=root.wallSettings().portholeShape;
        if(level==null||!level.isClientSide)return NativePortholeLayout.unit(shape);
        var access=new LevelAccess(level);boolean solid=access.solid(root.getBlockPos(),root);
        NativePanelLayout pane=query(root,NativePanelGroups.Mode.APERTURE);
        if(solid)return NativePortholeLayout.solidUnit(shape);
        return NativePortholeLayout.contacts(pane,shape,contacts(access,root.getBlockPos(),root),0);
    }
    private static NativePanelLayout query(NativeWallEntity root,NativePanelGroups.Mode queryMode){
        Level level=root.getLevel();if(level==null||!level.isClientSide)return NativePanelLayout.UNIT;
        var old=group(root,queryMode);
        if(old!=null&&old.valid&&!membershipValid(root,old))old.valid=false;
        if(old!=null&&!old.valid&&!old.queued){old.queued=true;for(long packed:old.layouts.keySet()){BlockPos pos=BlockPos.of(packed);if(NativeFluidWorld.loaded(level,pos)&&level.getBlockEntity(pos) instanceof NativeWallEntity member)NativeFluidWorld.queue(member);}}
        if(switch(queryMode){case MATERIAL->root.settings().layout==0;case GLASS->root.panelGlassSettings().material<0||!root.panelGlassSettings().textureJoin;case APERTURE->!root.wallSettings().joinPortholes||new LevelAccess(level).solid(root.getBlockPos(),root);}){group(root,queryMode,null);return NativePanelLayout.UNIT;}
        if(old!=null&&old.valid)return old.layouts.getOrDefault(root.getBlockPos().asLong(),NativePanelLayout.UNIT);
        var captured=capture(new LevelAccess(level),root.getBlockPos(),root,queryMode);
        Map<BlockPos,NativeWallEntity> members=captured.members();Map<Long,NativePanelLayout> layouts=captured.layouts();
        var group=new NativeWallLayouts.Group(queryMode,layouts,captured.capped());
        for(var entry:members.entrySet()){
            NativeWallEntity member=entry.getValue();var previous=group(member,queryMode);
            if(previous!=null&&previous!=group){previous.valid=false;for(long packed:previous.layouts.keySet()){BlockPos pos=BlockPos.of(packed);if(!members.containsKey(pos)&&NativeFluidWorld.loaded(level,pos)&&level.getBlockEntity(pos) instanceof NativeWallEntity excluded)NativeFluidWorld.queue(excluded);}}
            group(member,queryMode,group);if(member!=root)NativeFluidWorld.queue(member);
        }
        return layouts.getOrDefault(root.getBlockPos().asLong(),NativePanelLayout.UNIT);
    }
    private static boolean membershipValid(NativeWallEntity root,NativeWallLayouts.Group group){
        var access=new LevelAccess(root.getLevel());
        for(BlockPos next:candidates(access,root.getBlockPos(),root)){boolean contains=group.layouts.containsKey(next.asLong()),matches=compatible(access,root.getBlockPos(),root,next,group.mode)!=null;if(contains&&!matches||!contains&&matches&&!group.capped)return false;}return true;
    }
    static <M> Capture<M> capture(Access<M> access,BlockPos origin,M root){return capture(access,origin,root,NativePanelGroups.Mode.MATERIAL);}
    static <M> Capture<M> capture(Access<M> access,BlockPos origin,M root,NativePanelGroups.Mode queryMode){
        if(!access.loaded(origin))throw new IllegalArgumentException("Loaded diagonal artwork root required");
        Map<BlockPos,M> members=new LinkedHashMap<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();members.put(origin,root);queue.add(origin);
        while(groupingEnabled(access,origin,root,queryMode)&&!queue.isEmpty()&&members.size()<NativePanelGroups.LIMIT){
            BlockPos current=queue.removeFirst();M member=members.get(current);
            for(BlockPos next:candidates(access,current,member)){
                if(members.containsKey(next))continue;M other=compatible(access,current,member,next,queryMode);if(other==null)continue;members.put(next,other);queue.add(next);if(members.size()>=NativePanelGroups.LIMIT)break;
            }
        }
        BlockPos first=Collections.min(members.keySet());Direction basis=access.localState(members.get(first)).getValue(NativePanelBlock.FACING),right=basis.getClockWise();boolean half=mode(access,root)==2;Direction up=half?basis.getOpposite():Direction.UP;
        boolean capped=members.size()>=NativePanelGroups.LIMIT;
        Map<Long,NativePanelLayout> layouts=NativePanelGroups.partition(members.keySet(),right,up,capped,
                pos->access.localState(members.get(pos)).getValue(NativePanelBlock.FACING).getClockWise()!=right,
                pos->half&&access.localState(members.get(pos)).getValue(NativePanelBlock.FACING).getOpposite()!=up);
        if(capped&&queryMode==NativePanelGroups.Mode.APERTURE){Map<Long,NativePanelLayout> reflected=new HashMap<>();for(var entry:members.entrySet()){var state=access.localState(entry.getValue());Direction face=state.getValue(NativePanelBlock.FACING);reflected.put(entry.getKey().asLong(),new NativePanelLayout(0,0,1,1,face.getClockWise()!=right,half&&face.getOpposite()!=up));}layouts=Map.copyOf(reflected);}
        return new Capture<>(Collections.unmodifiableMap(members),layouts,capped);
    }
    private static <M> boolean groupingEnabled(Access<M> access,BlockPos origin,M root,NativePanelGroups.Mode mode){
        return switch(mode){case MATERIAL->true;case GLASS->access.glass(root).textureJoin;case APERTURE->access.settings(root).joinPortholes&&!access.solid(origin,root);};
    }
    private static <M> List<BlockPos> candidates(Access<M> access,BlockPos pos,M host){
        Direction facing=access.localState(host).getValue(NativePanelBlock.FACING),right=facing.getClockWise();int mode=mode(access,host);
        List<BlockPos> result=new ArrayList<>(8);result.add(pos.relative(right));result.add(pos.relative(right.getOpposite()));Direction row=mode==2?facing.getOpposite():Direction.UP,normal=mode==2?Direction.UP:facing.getOpposite();
        for(boolean positive:new boolean[]{false,true})for(int offset=-1;offset<=1;offset++)result.add(pos.relative(row,positive?1:-1).relative(normal,offset));return result;
    }
    private static <M> M compatible(Access<M> access,BlockPos rootPos,M root,BlockPos next,NativePanelGroups.Mode queryMode){
        if(!access.loaded(next))return null;BlockState a=access.localState(root),b=access.state(next);
        if(a.getBlock()!=b.getBlock())return null;M other=access.member(next);if(other==null)return null;
        if(queryMode==NativePanelGroups.Mode.MATERIAL){if(access.mainTexture(root)!=access.mainTexture(other)||access.textureLayout(root)!=access.textureLayout(other)||access.settings(root).fill!=access.settings(other).fill)return null;}
        else if(queryMode==NativePanelGroups.Mode.GLASS){
            var ar=access.glass(root);var br=access.glass(other);
            if(!br.textureJoin||!sameGlassMaterial(ar.material,br.material)||ar.tint!=br.tint||ar.textureLayout()!=br.textureLayout()||access.settings(root).fill!=access.settings(other).fill)return null;
            if(ar.material>=0&&"Screens".equals(com.vandorlabs.neoforge.ModernFinishCatalog.option(ar.material).category())&&!access.playback(root).equals(access.playback(other)))return null;
        }
        else{var ar=access.settings(root);var br=access.settings(other);if(!ar.joinPortholes||!br.joinPortholes||ar.portholeShape!=br.portholeShape||ar.halfHeight!=br.halfHeight||ar.fullWidth!=br.fullWidth||access.solid(rootPos,root)||access.solid(next,other))return null;}
        Direction af=a.getValue(NativePanelBlock.FACING),bf=b.getValue(NativePanelBlock.FACING);if(af.getAxis()!=bf.getAxis())return null;
        int mode=mode(access,root);if(mode!=mode(access,other))return null;
        BlockPos delta=next.subtract(rootPos);boolean ai=a.getValue(NativeDiagonalWallBlock.INVERTED),bi=b.getValue(NativeDiagonalWallBlock.INVERTED);double normal=NativeDiagonalWallTopology.axis(delta,af.getOpposite());
        if(DiagonalPanelContact.samePlane(mode,ai,bi,bf!=af,delta.getY(),normal))return other;
        int row=NativeDiagonalWallTopology.axis(delta,mode==2?af.getOpposite():Direction.UP);
        return Math.abs(row)==1&&NativeDiagonalWallTopology.axis(delta,af.getClockWise())==0&&DiagonalPanelContact.endsMeet(mode,ai,bi,bf!=af,delta.getY(),normal,row>0)?other:null;
    }
    static <M> int contacts(Access<M> access,BlockPos origin,M root){
        Direction facing=access.localState(root).getValue(NativePanelBlock.FACING),right=facing.getClockWise(),row=mode(access,root)==2?facing.getOpposite():Direction.UP,normal=mode(access,root)==2?Direction.UP:facing.getOpposite();int mask=0;
        if(compatible(access,origin,root,origin.relative(right.getOpposite()),NativePanelGroups.Mode.APERTURE)!=null)mask|=1;
        if(compatible(access,origin,root,origin.relative(right),NativePanelGroups.Mode.APERTURE)!=null)mask|=2;
        for(boolean upper:new boolean[]{false,true})for(int offset=-1;offset<=1;offset++)if(compatible(access,origin,root,origin.relative(row,upper?1:-1).relative(normal,offset),NativePanelGroups.Mode.APERTURE)!=null){mask|=upper?8:4;break;}return mask;
    }
    private static boolean sameGlassMaterial(int a,int b){return a==b||a>=0&&b>=0&&com.vandorlabs.neoforge.ModernFinishCatalog.storedChoice(a)==com.vandorlabs.neoforge.ModernFinishCatalog.storedChoice(b);}
    private NativeDiagonalLayouts(){}
}
