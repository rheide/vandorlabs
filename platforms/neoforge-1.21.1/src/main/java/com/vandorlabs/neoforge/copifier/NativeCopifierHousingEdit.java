package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.persistence.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.GlassTextures;
import net.minecraft.nbt.*;

/** Pure appearance plan. This neither installs block NBT nor publishes a world/inventory owner. */
public final class NativeCopifierHousingEdit {
    public static final class Plan {
        private final NativeCopifierHousingCapture.Family family;private final CompoundTag fields;private final boolean applicable;
        private Plan(NativeCopifierHousingCapture.Family family,CompoundTag fields,boolean applicable){this.family=family;this.fields=fields;this.applicable=applicable;}
        public boolean applicable(){return applicable;}
        public CompoundTag fields(){return fields.copy();}
        public CompoundTag capture(){return NativeCopifierHousingCapture.capture(family,fields);}
        public HousingSettings housingSettings(){return HousingSettings.read(new ModernHousingTags(fields),0,family.faces,ModernFinishCatalog::storedChoice,ModernFinishCatalog::validChoice);}
        public ProgrammableShapeEntity.Appearance appearance(){var h=housingSettings();return new ProgrammableShapeEntity.Appearance(new com.vandorlabs.ui.ProgrammableBlockDialog.State(h.mainTexture,h.layout(),h.facesEnabled,h.faces(),-1),h.sideTexture,StairState.read(new ModernHousingTags(fields)));}
        public WallSettings walls(){return WallSettings.read(new ModernHousingTags(fields));}
        public FrameSettings frames(){return FrameSettings.read(new ModernHousingTags(fields),NativeWallEntity::validFrame,NativeWallEntity::validSurface,ModernFinishCatalog::validChoice);}
        public PanelGlassSettings glass(){return PanelGlassSettings.read(new ModernHousingTags(fields),NativeCopifierHousingEdit::material);}
        public PanelPlayback playback(){return PanelPlayback.read(fields);}
        public ChannelList channels(){return ChannelList.of(fields.getIntArray("RedstoneChannels"));}
        public int transmissionRange(){return fields.getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS).getCompound("main").getInt("Range");}
        public boolean localRedstone(){return fields.getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS).getCompound("main").getBoolean("Local");}
        public ChannelList transmittedChannels(){return ChannelList.of(fields.getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS).getCompound("main").getIntArray("Channels"));}
    }
    private static int material(int value){return value==-1||ModernFinishCatalog.validChoice(value)?value:-1;}
    private static int number(CompoundTag tag,String key,int fallback){return tag.contains(key,Tag.TAG_INT)?tag.getInt(key):fallback;}
    private static boolean flag(CompoundTag tag,String key,boolean fallback){return tag.contains(key,Tag.TAG_BYTE)?tag.getBoolean(key):fallback;}
    private static void preflight(CompoundTag values){
        if(!NativeCopiedSettingsValidation.valid(values))throw new IllegalArgumentException("Invalid copied settings");
    }

    private static CompoundTag fields(NativeCopifierHousingCapture.Family family,CompoundTag current){
        // Reconstruct only bounded scalar state; never carry physical items or unknown ownership into a plan.
        var migration=com.vandorlabs.neoforge.redstone.NativeTransmissionMigration.read(current);if(!migration.installable())throw new IllegalArgumentException("Unsupported transmission ownership");var raw=migration.install();var a=new ModernHousingTags(raw);var out=new CompoundTag();
        HousingSettings.read(a,0,family.faces,ModernFinishCatalog::storedChoice,ModernFinishCatalog::validChoice).write(new ModernHousingTags(out));WallSettings w=WallSettings.read(a);WallSettings.write(new ModernHousingTags(out),w.fullWidth,w.halfHeight,w.fill,w.join,w.joinPortholes,w.portholeShape);
        FrameSettings.read(a,NativeWallEntity::validFrame,NativeWallEntity::validSurface,ModernFinishCatalog::validChoice).write(new ModernHousingTags(out));PanelGlassSettings.read(a,NativeCopifierHousingEdit::material).write(new ModernHousingTags(out));PanelPlayback.read(raw).write(out);StairState.read(a).write(new ModernHousingTags(out));
        ChannelList channels=ChannelList.of(Math.max(0,raw.getInt("RedstoneChannel")));if(raw.contains("RedstoneChannels",Tag.TAG_INT_ARRAY))try{channels=ChannelList.of(raw.getIntArray("RedstoneChannels"));}catch(IllegalArgumentException malformed){/* Released scalar fallback. */}out.putInt("RedstoneChannel",channels.first());out.putIntArray("RedstoneChannels",channels.toArray());
        var main=raw.getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS).getCompound("main");var t=new CompoundTag();t.putInt("Range",TransmissionSettings.readRange(main.contains("Range",Tag.TAG_INT),main.getInt("Range"),true));t.putBoolean("Local",TransmissionSettings.readLocal(main.contains("Local",Tag.TAG_BYTE),main.getBoolean("Local"),false));t.putIntArray("Channels",TransmissionSettings.readChannels(main.contains("Channels",Tag.TAG_INT_ARRAY),main.getIntArray("Channels"),ChannelList.EMPTY).toArray());var settings=new CompoundTag();settings.put("main",t);var forge=new CompoundTag();forge.put(TransmissionSettings.SETTINGS,settings);out.put("ForgeData",forge);return out;
    }
    public static Plan prepare(NativeCopifierHousingCapture.Family family,CompoundTag current,CompoundTag values){
        var fields=fields(family,current);if(values==null||values.isEmpty())return new Plan(family,fields,false);preflight(values);boolean applicable=false;
        if(values.contains("wall_texture",Tag.TAG_INT)){fields.putInt(HousingSettings.MAIN,ModernFinishCatalog.storedChoice(values.getInt("wall_texture")));applicable=true;}
        if(family.side&&values.contains("side_texture",Tag.TAG_INT)){int side=values.getInt("side_texture");if(side==-3)throw new IllegalArgumentException("Vehicle surfaces are unavailable");fields.putInt(HousingSettings.SIDE,side<0?-1:ModernFinishCatalog.storedChoice(side));applicable=true;}
        if(values.contains("texture_join_policy",Tag.TAG_COMPOUND)){var policy=values.getCompound("texture_join_policy");if(!com.vandorlabs.surface.TextureJoinSettings.valid(policy.getInt("Direction"),policy.getInt("Parity")))throw new IllegalArgumentException("Invalid texture join policy");new com.vandorlabs.surface.TextureJoinSettings(policy.getInt("Direction"),policy.getInt("Parity")).write(new ModernHousingTags(fields));applicable=true;}
        if(family.faces&&values.contains("face_textures",Tag.TAG_COMPOUND)){var f=values.getCompound("face_textures");boolean enabled=f.getBoolean("enabled");int[] faces=enabled||f.contains("choices",Tag.TAG_INT_ARRAY)?f.getIntArray("choices"):fields.getIntArray(HousingSettings.FACES);int[] clean=new int[6];java.util.Arrays.fill(clean,-1);if(faces.length==6)for(int i=0;i<6;i++)if(faces[i]>=0&&ModernFinishCatalog.validChoice(faces[i]))clean[i]=faces[i];fields.putBoolean(HousingSettings.FACES_ENABLED,enabled);fields.putIntArray(HousingSettings.FACES,clean);applicable=true;}
        if(family.stairs&&(values.contains("stair_tread",Tag.TAG_INT)||values.contains("stair_roll",Tag.TAG_INT))){fields.putInt(StairState.TREAD,number(values,"stair_tread",fields.getInt(StairState.TREAD)));fields.putInt(StairState.ROLL,number(values,"stair_roll",fields.getInt(StairState.ROLL)));applicable=true;}
        if(values.contains("texture_layout",Tag.TAG_INT)){int mode=values.getInt("texture_layout");if(fields.getInt(HousingSettings.LAYOUT)!=mode){fields.putInt(HousingSettings.LAYOUT,mode);fields.putBoolean(HousingSettings.TILE_SIDES,mode==1);}applicable=true;}
        else if(values.contains("slab_tile_sides",Tag.TAG_BYTE)){boolean tile=values.getBoolean("slab_tile_sides");if(fields.getBoolean(HousingSettings.TILE_SIDES)!=tile){fields.putBoolean(HousingSettings.TILE_SIDES,tile);fields.putInt(HousingSettings.LAYOUT,tile?1:0);}applicable=true;}
        if(family.diagonal){
            if(values.contains("diagonal_full_width",Tag.TAG_BYTE)&&!values.contains("diagonal_geometry",Tag.TAG_COMPOUND)){fields.putBoolean(WallSettings.FULL,values.getBoolean("diagonal_full_width"));fields.putBoolean(WallSettings.HALF,false);applicable=true;}
            if(values.contains("diagonal_geometry",Tag.TAG_COMPOUND)){var g=values.getCompound("diagonal_geometry");if(g.contains("join",Tag.TAG_INT)&&g.getInt("join")>=0&&g.getInt("join")<=11)fields.putInt(WallSettings.JOIN,g.getInt("join"));int mode=Math.max(0,Math.min(2,g.getInt("mode")));fields.putBoolean(WallSettings.HALF,mode==2);fields.putBoolean(WallSettings.FULL,mode!=0);fields.putInt(WallSettings.FILL,family==NativeCopifierHousingCapture.Family.DIAGONAL_PORTHOLE?0:WallSettings.fill(g.getInt("fill")));applicable=true;}
        }
        if(family.pane){
            if(values.contains("join",Tag.TAG_BYTE)){fields.putBoolean(WallSettings.PORTHOLES,values.getBoolean("join"));applicable=true;}
            if(values.contains("porthole_shape",Tag.TAG_INT)){int shape=values.getInt("porthole_shape");if(shape>=0&&shape<=3)fields.putInt(WallSettings.SHAPE,shape);applicable=true;}
            if(values.contains("glass_shade",Tag.TAG_INT)){if(GlassTextures.valid(values.getInt("glass_shade")))fields.putInt(PanelGlassSettings.SHADE,values.getInt("glass_shade"));applicable=true;}
            if(values.contains("glass_tint",Tag.TAG_INT)||values.contains("framed",Tag.TAG_BYTE)){int tint=number(values,"glass_tint",fields.getInt(PanelGlassSettings.TINT));if(GlassTextures.validTint(tint)){fields.putInt(PanelGlassSettings.TINT,tint);fields.putBoolean(PanelGlassSettings.FRAMED,flag(values,"framed",fields.getBoolean(PanelGlassSettings.FRAMED)));}applicable=true;}
            if(values.contains("glass_material",Tag.TAG_INT)){int value=values.getInt("glass_material");if(value==-1||ModernFinishCatalog.validChoice(value))fields.putInt(PanelGlassSettings.MATERIAL,value);applicable=true;}
            if(values.contains("glass_layout",Tag.TAG_COMPOUND)){var g=values.getCompound("glass_layout");if(g.contains(GlassPlaybackData.MODE,Tag.TAG_BYTE)){fields.putByte(GlassPlaybackData.MODE,g.getByte(GlassPlaybackData.MODE));fields.putByte(GlassPlaybackData.SPEED,g.contains(GlassPlaybackData.SPEED,Tag.TAG_BYTE)?g.getByte(GlassPlaybackData.SPEED):(byte)1);}boolean stretch=flag(g,"Stretch",fields.getBoolean(PanelGlassSettings.STRETCH)),join=flag(g,"TextureJoin",fields.getBoolean(PanelGlassSettings.TEXTURE_JOIN));if(stretch!=fields.getBoolean(PanelGlassSettings.STRETCH)||join!=fields.getBoolean(PanelGlassSettings.TEXTURE_JOIN)){fields.putBoolean(PanelGlassSettings.STRETCH,stretch);fields.putBoolean(PanelGlassSettings.TEXTURE_JOIN,join);fields.putInt(PanelGlassSettings.LAYOUT,stretch?0:1);}if(g.contains("Mode",Tag.TAG_INT)&&fields.getInt(PanelGlassSettings.LAYOUT)!=g.getInt("Mode")){fields.putInt(PanelGlassSettings.LAYOUT,g.getInt("Mode"));fields.putBoolean(PanelGlassSettings.STRETCH,g.getInt("Mode")!=1);}applicable=true;}
        }
        if(values.contains("redstone_channel")){ChannelList channels=ChannelList.of(fields.getIntArray("RedstoneChannels"));if(values.contains("redstone_channel",Tag.TAG_INT_ARRAY))try{channels=ChannelList.of(values.getIntArray("redstone_channel"));}catch(IllegalArgumentException malformed){/* Preserve released fallback. */}else if(values.contains("redstone_channel",Tag.TAG_INT))channels=ChannelList.of(Math.max(0,values.getInt("redstone_channel")));fields.putInt("RedstoneChannel",channels.first());fields.putIntArray("RedstoneChannels",channels.toArray());applicable=true;}
        if(values.contains("FrameAppearance",Tag.TAG_COMPOUND)){FrameSettings.read(new ModernHousingTags(values.getCompound("FrameAppearance")),NativeWallEntity::validFrame,NativeWallEntity::validSurface,ModernFinishCatalog::validChoice).write(new ModernHousingTags(fields));applicable=true;}
        if(values.contains("Transmission",Tag.TAG_COMPOUND)){var t=values.getCompound("Transmission");var main=new CompoundTag();main.putInt("Range",t.getInt("Range"));main.putBoolean("Local",t.getBoolean("Local"));main.putIntArray("Channels",ChannelList.of(t.getIntArray("Channels")).toArray());fields.getCompound("ForgeData").getCompound(TransmissionSettings.SETTINGS).put("main",main);applicable=true;}
        return new Plan(family,fields,applicable);
    }
    private NativeCopifierHousingEdit(){}
}
