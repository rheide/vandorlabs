package com.vandorlabs.tiles;

/** Shared menu choices and atlas names for programmable housing surfaces. */
public final class ScreenHousingTextures {
    public static final String[] IDS;
    private static final String[] TEXTURES;
    private static final java.util.Map<Integer,Integer> REDIRECTS=new java.util.HashMap<>();
    private static final String[] SQUARE_TEXTURES, UNLIT_TEXTURES, STORAGE_TOP, STORAGE_SIDE;
    private static final java.util.List<com.google.gson.JsonObject> EXTRAS=new java.util.ArrayList<>();
    private static final com.google.gson.JsonObject MENU;
    public static final int LEGACY_COUNT=HousingFinishCatalog.legacyCount();
    public static final int BUILTIN_COUNT;
    public static final int DEFAULT_TRAPDOOR;
    public static final int DEFAULT_STORAGE;
    private static final java.util.Map<Integer,Integer> FILE_CHOICES=new java.util.HashMap<>();
    static {
        try(java.io.InputStream stream=ScreenHousingTextures.class.getResourceAsStream("/assets/vandorlabs/data/texture_menu.json")) {
            if(stream==null)throw new IllegalStateException("Texture menu metadata missing");
            MENU=new com.google.gson.JsonParser().parse(new java.io.InputStreamReader(stream,"UTF-8")).getAsJsonObject();
        } catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}
        try(java.io.InputStream stream=ScreenHousingTextures.class.getResourceAsStream("/assets/vandorlabs/data/unified_textures.json")) {
            if(stream==null)throw new IllegalStateException("Shared texture catalog missing");
            for(com.google.gson.JsonElement e:new com.google.gson.JsonParser().parse(new java.io.InputStreamReader(stream,"UTF-8")).getAsJsonArray())EXTRAS.add(e.getAsJsonObject());
        } catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}
        BUILTIN_COUNT=HousingFinishCatalog.legacyCount()+EXTRAS.size();
        EXTRAS.addAll(FilesystemTextures.entries());
        IDS=new String[HousingFinishCatalog.legacyCount()+EXTRAS.size()];TEXTURES=new String[IDS.length];
        for(int i=0;i<HousingFinishCatalog.legacyCount();i++){IDS[i]=HousingFinishCatalog.id(i);TEXTURES[i]="vandorlabs:blocks/"+HousingFinishCatalog.texture(i);}
        for(int i=0;i<EXTRAS.size();i++) {
            com.google.gson.JsonObject entry=EXTRAS.get(i);int index=i+HousingFinishCatalog.legacyCount();
            IDS[index]=entry.get("id").getAsString();
            if(entry.has("key"))FILE_CHOICES.put(entry.get("key").getAsInt(),index);
            TEXTURES[index]="vandorlabs:blocks/"+(entry.has("rectangular")?"unified/"+IDS[index]:entry.get("source").getAsString());
        }
        for(int i=0;i<EXTRAS.size();i++) {
            com.google.gson.JsonObject e=EXTRAS.get(i);
            if(e.has("alias")) {
                String alias=e.get("alias").getAsString();int target=-1;
                for(int j=0;j<IDS.length;j++)if(alias.equals(IDS[j])){target=j;break;}
                if(target<0)throw new IllegalStateException("Missing texture alias: "+alias);
                REDIRECTS.put(LEGACY_COUNT+i,target);
            }
            if(e.has("detail") && e.get("detail").getAsInt()>0){
                for(int j=0;j<EXTRAS.size();j++){
                    com.google.gson.JsonObject low=EXTRAS.get(j);
                    if(!low.has("detail") || low.get("detail").getAsInt()!=0)continue;
                    boolean same=e.has("design") && low.has("design") && e.get("design").equals(low.get("design"))
                            || e.has("textureFamily") && low.has("textureFamily") && e.get("textureFamily").equals(low.get("textureFamily"));
                    if(same){REDIRECTS.put(LEGACY_COUNT+i,LEGACY_COUNT+j);break;}
                }
            }
        }
        DEFAULT_STORAGE=screenIndex("storage/cabinet_front");
        DEFAULT_TRAPDOOR=textureIndex(screenIndex("imported/trapdoors/cyan_lit_armored_sci_fi_hatch_4"));
        if(DEFAULT_TRAPDOOR==0)throw new IllegalStateException("Default trapdoor material missing");
        // Keep saved choice numbers while omitting retired artwork from the runtime JAR.
        for(int i=0;i<BUILTIN_COUNT;i++)if(!visible(i))TEXTURES[i]=TEXTURES[0];
        SQUARE_TEXTURES=new String[IDS.length];UNLIT_TEXTURES=new String[IDS.length];
        STORAGE_TOP=new String[IDS.length];STORAGE_SIDE=new String[IDS.length];
        // The catalog is fixed after bootstrap. Retain names, never atlas sprites,
        // so resource reloads and dynamic Custom artwork still resolve normally.
        for(int i=0;i<IDS.length;i++) {
            com.google.gson.JsonObject e=entry(i),metadata=menu(i);
            SQUARE_TEXTURES[i]=e!=null && e.has("design") && !"Double Doors".equals(e.get("category").getAsString()) && visible(i)?TEXTURES[i]+"_half":TEXTURES[i];
            if(visible(i) && metadata!=null && metadata.has("unlit"))UNLIT_TEXTURES[i]="vandorlabs:blocks/"+metadata.get("unlit").getAsString();
            if(e!=null && e.has("top"))STORAGE_TOP[i]="vandorlabs:blocks/"+e.get("top").getAsString();
            if(e!=null && e.has("side"))STORAGE_SIDE[i]="vandorlabs:blocks/"+e.get("side").getAsString();
        }
    }
    public static com.google.gson.JsonObject entry(int choice){int index=localIndex(choice)-LEGACY_COUNT;return index>=0 && index<EXTRAS.size()?EXTRAS.get(index):null;}
    /** Menu metadata changes presentation without moving persisted numeric choices. */
    private static com.google.gson.JsonObject menu(int choice) {
        int index=localIndex(choice);
        return index<LEGACY_COUNT && MENU.has(IDS[index])?MENU.getAsJsonObject(IDS[index]):entry(choice);
    }
    public static String category(int choice){com.google.gson.JsonObject e=menu(choice);return e!=null && e.has("category")?e.get("category").getAsString():"Materials";}
    public static String label(int choice){com.google.gson.JsonObject e=menu(choice);return e==null || !e.has("label")?null:e.has("detail")?e.get("label").getAsString().replaceFirst(" (Small|Medium|Large)$",""):e.get("label").getAsString();}
    public static boolean visible(int choice){com.google.gson.JsonObject e=menu(choice);return e==null || !e.has("hidden") || !e.get("hidden").getAsBoolean();}
    public static int screenIndex(String source){for(int i=0;i<EXTRAS.size();i++)if(source.equals(EXTRAS.get(i).get("source").getAsString()))return LEGACY_COUNT+i;return 0;}
    /** Storage sets use their front as the ordinary shared material thumbnail. */
    public static String storageTexture(int choice, net.minecraft.util.EnumFacing localFace) {
        if(localFace==net.minecraft.util.EnumFacing.NORTH)return texture(choice);
        String selected=(localFace.getAxis()==net.minecraft.util.EnumFacing.Axis.Y?STORAGE_TOP:STORAGE_SIDE)[textureIndex(choice)];
        return selected==null?texture(choice):selected;
    }
    public static boolean isDoor(int choice){if(CustomBlockMaterials.isCustom(choice))return com.vandorlabs.VandorLabs.proxy!=null && com.vandorlabs.VandorLabs.proxy.customDoor(choice);com.google.gson.JsonObject e=entry(choice);return e!=null && e.has("design");}
    public static int doorIndex(int design,int detail){
        detail=Math.max(0,Math.min(detail,1));
        for(int i=0;i<EXTRAS.size();i++) {
            com.google.gson.JsonObject e=EXTRAS.get(i);
            if(e.has("design") && e.get("design").getAsInt()==design && e.get("detail").getAsInt()==detail)return LEGACY_COUNT+i;
        }
        return LEGACY_COUNT+6+2*3+1;
    }
    public static int sizedTextureIndex(String family,int detail) {
        for(int i=0;i<EXTRAS.size();i++) {
            com.google.gson.JsonObject e=EXTRAS.get(i);
            if(e.has("textureFamily") && family.equals(e.get("textureFamily").getAsString()) && e.get("detail").getAsInt()==Math.min(detail,1))return LEGACY_COUNT+i;
        }
        return 0;
    }
    /** Static Off artwork remains available as a material, but not in light menus. */
    public static boolean lightOnly(int choice){com.google.gson.JsonObject e=menu(choice);return e!=null&&e.has("lightOnly")&&e.get("lightOnly").getAsBoolean();}
    public static boolean isLightOff(int choice){com.google.gson.JsonObject e=menu(choice);return e!=null && ("Lights".equals(category(choice))||lightOnly(choice)) && (e.has("off") && e.get("off").getAsBoolean() || e.has("id") && e.get("id").getAsString().endsWith("_off"));}
    public static int lightIndex(int style){return LEGACY_COUNT+Math.max(0,Math.min(5,style));}
    public static String texture(int choice,boolean lit){
        String unlit=lit?null:UNLIT_TEXTURES[textureIndex(choice)];
        return unlit==null?texture(choice):unlit;
    }

    public static final int INDUSTRIAL_BLOCK = 9;

    private ScreenHousingTextures() { }

    public static boolean validChoice(int choice){return choice>=0 && (choice<BUILTIN_COUNT || choice>=CustomBlockMaterials.ID_BASE);}
    public static int choiceAt(int index){com.google.gson.JsonObject e=entry(index);return e!=null && e.has("key")?e.get("key").getAsInt():index;}
    public static int localIndex(int choice){return CustomBlockMaterials.isCustom(choice)?0:choice>=FilesystemTextures.ID_BASE?FILE_CHOICES.getOrDefault(choice,0):choice>=0 && choice<IDS.length?choice:0;}

    /** Artwork lookups accept catalog positions as well as stable external choice IDs. */
    private static int textureIndex(int choice){return localIndex(REDIRECTS.getOrDefault(choice,choice));}
    public static int canonicalChoice(int choice){return textureIndex(choice);}

    public static int clamp(int choice) {
        return validChoice(choice) ? REDIRECTS.getOrDefault(choice,choice) : 0;
    }

    public static int cycle(int choice, int direction) {
        return choiceAt(Math.floorMod(textureIndex(choice) + direction, IDS.length));
    }

    /** Full artwork for door leaves and thumbnails; ordinary blocks use one square half. */
    public static String fullTexture(int choice) {
        if(CustomBlockMaterials.isCustom(choice) && com.vandorlabs.VandorLabs.proxy!=null)return com.vandorlabs.VandorLabs.proxy.customTexture(choice);
        return TEXTURES[textureIndex(choice)];
    }
    public static String texture(int choice) {
        if(CustomBlockMaterials.isCustom(choice) && com.vandorlabs.VandorLabs.proxy!=null)return com.vandorlabs.VandorLabs.proxy.customTexture(choice);
        return SQUARE_TEXTURES[textureIndex(choice)];
    }
}
