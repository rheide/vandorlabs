package com.vandorlabs.items;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Per-item choices for which copied settings may be applied. */
public final class DuplifierApplyOptions {
    public static final String TAG = "DuplifierApplyMask";
    private static final String COUNT_TAG = "DuplifierApplyOptionCount";
    public static final String CONNECTED_TAG = "DuplifierConnected";
    public static final String[] PAGES = {"Common", "Displays", "Blocks", "Doors", "Ramps"};

    public static final class Option {
        public final String key;
        public final String label;
        public final int page;

        private Option(String key, String label, int page) {
            this.key = key;
            this.label = label;
            this.page = page;
        }
    }

    private static Option option(String key, String label, int page) {
        return new Option(key, label, page);
    }

    public static final Option[] OPTIONS = {
            option(ProgrammableSettings.PRIMARY_TEXTURE, "Primary Texture", 0),
            option(ProgrammableSettings.WALL_TEXTURE, "Wall Texture", 0),
            option(ProgrammableSettings.JOIN, "Join", 0),
            option(ProgrammableSettings.CHANNEL, "Redstone Channels", 0),
            option(ProgrammableSettings.TRIGGER, "Redstone Trigger", 0),
            option(ProgrammableSettings.ACTIVE, "Active State", 0),
            option(ProgrammableSettings.PARTICLES, "Particle Stream", 0),
            option(ProgrammableSettings.FRAMED, "Frame", 0),
            option(ProgrammableSettings.GLASS_SHADE, "Glass Shade", 0),
            option(ProgrammableSettings.LIGHT_LEVEL, "Light Level", 0),

            option(ProgrammableSettings.DISPLAY_MODE, "Display Mode", 1),
            option(ProgrammableSettings.ANIMATION_SPEED, "Animation Speed", 1),
            option(ProgrammableSettings.INPUT_PANEL, "Input Panel", 1),
            option(ProgrammableSettings.SECONDARY_INPUT_PANEL, "Second Input Panel", 1),
            option(ProgrammableSettings.SMALL_INPUT, "Small Input", 1),
            option(ProgrammableSettings.WALL_POSITION, "Wall Position", 1),
            option(ProgrammableSettings.TRIGGER_ON_TEXTURE, "Trigger On Texture", 1),

            option(ProgrammableSettings.PROPULSION_SHAPE, "Thruster Shape", 2),
            option(ProgrammableSettings.PORTHOLE_SHAPE, "Porthole Shape", 2),
            option(ProgrammableSettings.GLASS_SIZE, "Glass Size", 2),
            option(ProgrammableSettings.SIDE_TEXTURE,"Side Texture",2),
            option(ProgrammableSettings.SLAB_TILE_SIDES, "Texture Layout", 2),
            option(ProgrammableSettings.CHAIR_STYLE, "Chair Style", 2),
            option(ProgrammableSettings.CHAIR_HEIGHT, "Chair Height", 2),
            option(ProgrammableSettings.SWITCH_ROTATION, "Switch Rotation", 2),

            option(ProgrammableSettings.DOOR_DESIGN, "Door Design", 3),
            option(ProgrammableSettings.DOOR_DETAIL, "Door Detail", 3),
            option(ProgrammableSettings.DOOR_SLIDE_DIRECTION, "Slide Direction", 3),
            option(ProgrammableSettings.DOOR_MIDDLE, "Middle Door", 3),
            option(ProgrammableSettings.DOOR_SLIDING, "Door / Trapdoor Movement", 3),
            option(ProgrammableSettings.DOOR_HINGES, "Door Hinges", 3),
            option(ProgrammableSettings.DOOR_PANEL, "Door Panel", 3),
            option(ProgrammableSettings.DOOR_DEPTH, "Door Depth", 3),

            option(ProgrammableSettings.RAMP_START, "Start Offset", 4),
            option(ProgrammableSettings.RAMP_END, "End Offset", 4),
            option(ProgrammableSettings.RAMP_TREAD_PIXELS, "Tread Size", 4),
            option(ProgrammableSettings.RAMP_SPEED, "Ramp Speed", 4),
            option(ProgrammableSettings.RAMP_LIFT, "Lift Mode", 4),
            option(ProgrammableSettings.RAMP_EXTEND, "Extend Mode", 4),
            option(ProgrammableSettings.RAMP_DIRECTION, "Ramp Direction", 4),
            option(ProgrammableSettings.RAMP_TRAVEL, "Travel Direction", 4),
            option(ProgrammableSettings.FACE_TEXTURES, "Face Overrides", 2),
            option(ProgrammableSettings.DIAGONAL_GEOMETRY, "Diagonal Geometry", 2),
            option(ProgrammableSettings.RAMP_MATCH_TEXTURES, "Match Textures", 4),
            option(ProgrammableSettings.GEAR_SIZE, "Gear Size", 2),
            option(ProgrammableSettings.GEAR_LENGTH, "Gear Extension", 2),
            option(ProgrammableSettings.GEAR_MODE, "Gear Redstone Mode", 2),
            option(ProgrammableSettings.TRAPDOOR_POSITION, "Trapdoor Position", 3),
            option(ProgrammableSettings.TRAPDOOR_COVER, "Next-block Leaf", 3),
            option(ProgrammableSettings.PRIMARY_SURFACE, "Static Surface Texture", 1),
            option(ProgrammableSettings.SECONDARY_SURFACE, "Second Static Texture", 1),
            option(ProgrammableSettings.LIGHT_FACE_TEXTURE, "Light Face Texture", 0),
            option(ProgrammableSettings.DOOR_FACE_TEXTURE, "Door Face Texture", 3),
            option(ProgrammableSettings.DOOR_TILE_TEXTURE, "Door Face Layout", 3),
            option(ProgrammableSettings.TRAPDOOR_TILE_TEXTURE, "Trapdoor Face Layout", 3),
            option(ProgrammableSettings.TRAPDOOR_COVER_FACING, "Trapdoor Hinge Direction", 3),
            option(ProgrammableSettings.REDSTONE_ROWS,"Redstone Screen Items",1),
            option(ProgrammableSettings.RAMP_INTERPOLATION,"Ramp Interpolation",4),
            option(ProgrammableSettings.SIGNAL_SETTINGS,"Signal Levels",0),
            option(DuplifierArmorTextures.KEY,"Armor Texture",0),
            option(ProgrammableSettings.RAMP_ESCALATOR,"Escalator Mode",4),
            option(ProgrammableSettings.GLASS_TINT,"Glass Tint",2),
            option(ProgrammableSettings.GLASS_MATERIAL,"Glass Material / Layout",0),
            option(ProgrammableSettings.BLOCK_CONFIGURATION,"Canopy / Pilot Settings",2),
            option("MoatMode","Moat Mode",4),
            option(ProgrammableSettings.DOOR_SECURITY,"Door Security",3),
            option(ProgrammableSettings.STAIR_TREAD, "Stair step size", 2),
            option(ProgrammableSettings.STAIR_ROLL, "Stair orientation", 2),
            option("FrameAppearance", "Frame Finishes", 0)
    };

    /** Legacy API covers the original first word; option indices are append-only. */
    public static final long ALL = OPTIONS.length>=64?-1L:(1L << OPTIONS.length) - 1L;
    public static final String WORDS_TAG=TAG+"Words";
    private static final int WORD_COUNT=(OPTIONS.length+63)/64;

    private DuplifierApplyOptions() { }

    public static boolean connected(ItemStack tool) {
        NBTTagCompound root = tool.getTagCompound();
        return root != null && root.getBoolean(CONNECTED_TAG);
    }

    public static void setConnected(ItemStack tool, boolean connected) {
        tool.setTagInfo(CONNECTED_TAG, new net.minecraft.nbt.NBTTagByte((byte)(connected ? 1 : 0)));
    }

    public static long[] legacyWords(long low){long[] words=new long[WORD_COUNT];java.util.Arrays.fill(words,-1L);words[0]=low;trim(words);return words;}
    private static void trim(long[] words){int tail=OPTIONS.length%64;if(tail!=0)words[WORD_COUNT-1]&=(1L<<tail)-1;}
    public static boolean valid(long[] words){if(words==null || words.length!=WORD_COUNT)return false;long[] clean=words.clone();trim(clean);return java.util.Arrays.equals(words,clean);}
    /** Original long and option count remain readable; appended options begin enabled. */
    public static long[] maskWords(ItemStack tool){
        NBTTagCompound root=tool.getTagCompound();if(root==null || !root.hasKey(TAG,4))return legacyWords(ALL);
        int known=root.hasKey(COUNT_TAG,3)?root.getInteger(COUNT_TAG):56;known=Math.max(0,Math.min(OPTIONS.length,known));
        long[] words=new long[WORD_COUNT];words[0]=root.getLong(TAG);
        if(root.hasKey(WORDS_TAG,11)){int[] stored=root.getIntArray(WORDS_TAG);for(int i=0;i<WORD_COUNT && i*2+1<stored.length;i++)words[i]=(stored[i*2]&0xffffffffL)|((long)stored[i*2+1]<<32);}
        for(int i=known;i<OPTIONS.length;i++)words[i/64]|=1L<<(i%64);trim(words);return words;
    }
    public static long mask(ItemStack tool){return maskWords(tool)[0];}
    /** Updating an old first-word caller preserves all newer choices. */
    public static void setMask(ItemStack tool,long value){long[] words=maskWords(tool);words[0]=value & ALL;setMask(tool,words);}
    public static void setMask(ItemStack tool,long[] value){
        if(!valid(value))throw new IllegalArgumentException("Invalid Duplifier option mask");
        NBTTagCompound root=tool.getTagCompound();if(root==null)root=new NBTTagCompound();int[] stored=new int[WORD_COUNT*2];
        for(int i=0;i<WORD_COUNT;i++){stored[i*2]=(int)value[i];stored[i*2+1]=(int)(value[i]>>>32);}
        root.setLong(TAG,value[0]);root.setIntArray(WORDS_TAG,stored);root.setInteger(COUNT_TAG,OPTIONS.length);tool.setTagCompound(root);
    }
    public static boolean enabled(long mask,int index){return index>=64 && index<OPTIONS.length || index>=0 && index<64 && (mask&(1L<<index))!=0;}
    public static boolean enabled(long[] words,int index){return index>=0 && index<OPTIONS.length && index/64<words.length && (words[index/64]&(1L<<(index%64)))!=0;}
    public static NBTTagCompound selected(NBTTagCompound captured,long mask){return selected(captured,legacyWords(mask));}

    public static NBTTagCompound selected(NBTTagCompound captured, long[] mask) {
        if (captured == null) return null;
        NBTTagCompound selected = captured.copy();
        for (int i = 0; i < OPTIONS.length; i++)
            if (!enabled(mask, i)) selected.removeTag(OPTIONS[i].key);
        if (!selected.hasKey(ProgrammableSettings.DOOR_SLIDING)) { selected.removeTag(ProgrammableSettings.TRAPDOOR_HINGE_SIDE); selected.removeTag(ProgrammableSettings.TRAPDOOR_ROTATION_FLIPPED); selected.removeTag(ProgrammableSettings.TRAPDOOR_SLIDE_INTO_WALL); selected.removeTag(ProgrammableSettings.TRAPDOOR_SLIDE_OVER_SURFACE); }
        if (!selected.hasKey(ProgrammableSettings.DIAGONAL_GEOMETRY)) selected.removeTag(ProgrammableSettings.DIAGONAL_FULL_WIDTH);
        if(!selected.hasKey(ProgrammableSettings.SLAB_TILE_SIDES))selected.removeTag(ProgrammableSettings.TEXTURE_LAYOUT);
        if(!selected.hasKey(ProgrammableSettings.LIGHT_FACE_TEXTURE))selected.removeTag(ProgrammableSettings.LIGHT_OFF_FACE_TEXTURE);
        if(!selected.hasKey(ProgrammableSettings.DOOR_TILE_TEXTURE))selected.removeTag(ProgrammableSettings.DOOR_TEXTURE_LAYOUT);
        if(!selected.hasKey(ProgrammableSettings.TRAPDOOR_TILE_TEXTURE))selected.removeTag(ProgrammableSettings.TRAPDOOR_TEXTURE_LAYOUT);
        if (!selected.hasKey(ProgrammableSettings.GLASS_MATERIAL)) selected.removeTag(ProgrammableSettings.GLASS_LAYOUT);
        if (!selected.hasKey(ProgrammableSettings.RAMP_START)) selected.removeTag(ProgrammableSettings.RAMP_START_HALF);
        if (!selected.hasKey(ProgrammableSettings.RAMP_END)) selected.removeTag(ProgrammableSettings.RAMP_END_HALF);
        if (!selected.hasKey(ProgrammableSettings.PRIMARY_TEXTURE))
            selected.removeTag(ProgrammableSettings.PRIMARY_KIND);
        return selected;
    }
}
