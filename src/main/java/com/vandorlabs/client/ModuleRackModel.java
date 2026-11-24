package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockModuleRack;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.client.model.pipeline.LightUtil;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Settled racks are ordinary opaque terrain. All racks share finite immutable
 * component geometry and a bounded cache of typed occupancy/visible/charge combinations per
 * facing. getQuads never reads a world, item inventory, animation clock or NBT.
 */
@Mod.EventBusSubscriber(modid = "vandorlabs", value = Side.CLIENT)
public final class ModuleRackModel implements IBakedModel {
    private static final String[] TEXTURES = { "housing", "module", "autopilot_module", "trim", "dark", "indicators", "contacts", "autonavigation_module" };
    private static final ModuleRackModel[] MODELS = new ModuleRackModel[6];
    private final IBakedModel shell;
    private final int facing;
    private int housingChoice = -1;
    private boolean powerOut;
    private final java.util.LinkedHashMap<Integer, ModuleRackModel> housingModels = new java.util.LinkedHashMap<>(16, .75F, true);

    private MovingMesh navigationBayFrame,navigationBayModule;
    private MovingMesh[][] embeddedSeated;
    private MovingMesh[] embeddedEmpty;
    private MovingMesh[][] embeddedIndicators;
    private final IBakedModel[] modules;
    private final MovingMesh[] moving;
    private final IBakedModel[] autopilotModules;
    private final MovingMesh[] autopilotMoving;
    private final List<BakedQuad>[] autopilotSeated;
    private MovingMesh[] routeMoving;
    private List<BakedQuad>[] routeSeated;
    private IBakedModel[][] batteryModules;
    private MovingMesh[][] batteryMoving;
    private List<BakedQuad>[][] batterySeated;
    private final List<BakedQuad>[] seated, empty;
    private final List<BakedQuad>[][] indicators;
    private final java.util.LinkedHashMap<Integer,List<BakedQuad>> combinations=new java.util.LinkedHashMap<>(16,.75F,true);
    private int cacheBytes;
    private List<BakedQuad>[][] chargeQuads;
    private MovingMesh[][] chargeMeshes;
    private static final int COMBINATION_BYTES = 65536;

    private ModuleRackModel(int facing, IBakedModel shell, IBakedModel[] modules, List<BakedQuad>[] seated,
                            List<BakedQuad>[] empty, List<BakedQuad>[][] indicators, IBakedModel[] autopilotModules, List<BakedQuad>[] autopilotSeated) {
        this.facing = facing; this.shell = shell;
        this.autopilotModules = autopilotModules; this.autopilotSeated = autopilotSeated;
        this.autopilotMoving = new MovingMesh[4];
        for(int slot=0;slot<4;slot++)autopilotMoving[slot]=new MovingMesh(autopilotModules[slot]);
        this.modules = modules;
        this.moving = new MovingMesh[4];
        for (int slot = 0; slot < 4; slot++) moving[slot] = new MovingMesh(modules[slot]);
        this.seated = seated;
        this.empty = empty;
        this.indicators = indicators;
    }

    private ModuleRackModel(ModuleRackModel source, int housingChoice, TextureAtlasSprite sprite, boolean powerOut) {
        this.facing = source.facing; this.housingChoice = housingChoice; this.powerOut = powerOut; shell = new HousingShell(source.shell, sprite, facing, powerOut);
        routeMoving=source.routeMoving;routeSeated=source.routeSeated;
        batteryModules=source.batteryModules;batteryMoving=source.batteryMoving;batterySeated=source.batterySeated;chargeQuads=source.chargeQuads;chargeMeshes=source.chargeMeshes;embeddedSeated=source.embeddedSeated;embeddedEmpty=source.embeddedEmpty;embeddedIndicators=source.embeddedIndicators;
        modules = source.modules; moving = source.moving; autopilotModules=source.autopilotModules; autopilotMoving=source.autopilotMoving; autopilotSeated=source.autopilotSeated; seated = source.seated; empty = source.empty; indicators = source.indicators;
    }
    private synchronized ModuleRackModel housing(int choice, boolean out) {
        int key=(choice+1)*2+(out?1:0);
        ModuleRackModel found = housingModels.get(key);
        if (found != null) return found;
        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(
                choice<0?"vandorlabs:blocks/module_rack/housing":com.vandorlabs.tiles.ScreenHousingTextures.texture(choice));
        found = new ModuleRackModel(this, choice, sprite, out); housingModels.put(key, found);
        // Every entry shares the same fixed geometry and module meshes. Retain at most sixteen shell finishes per facing.
        if (housingModels.size() > 16) housingModels.remove(housingModels.keySet().iterator().next());
        return found;
    }
    private static final class HousingShell implements IBakedModel {
        private final IBakedModel source; private final TextureAtlasSprite sprite;
        private final List<BakedQuad>[] quads;
        @SuppressWarnings("unchecked") HousingShell(IBakedModel source, TextureAtlasSprite sprite, int facing, boolean out) {
            this.source = source; this.sprite = sprite;
            TextureAtlasSprite port=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/resource_ports/power_"+(out?"out":"in")+"_off");
            quads = (List<BakedQuad>[])new List<?>[7];
            for (int face = 0; face < 7; face++) {
                List<BakedQuad> built = new ArrayList<>();
                for (BakedQuad quad : source.getQuads(null, face == 6 ? null : EnumFacing.getFront(face), 0))
                    built.add(housingFace(quad, facing)
                            ? RetexturedItemModel.retextureQuad(quad, quad.getFace().getIndex()==(facing^1)?port:sprite) : quad);
                quads[face] = Collections.unmodifiableList(built);
            }
        }
        public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long seed) { return quads[side == null ? 6 : side.getIndex()]; }
        public boolean isAmbientOcclusion() { return source.isAmbientOcclusion(); }
        public boolean isGui3d() { return source.isGui3d(); }
        public boolean isBuiltInRenderer() { return source.isBuiltInRenderer(); }
        public TextureAtlasSprite getParticleTexture() { return sprite; }
        public ItemCameraTransforms getItemCameraTransforms() { return source.getItemCameraTransforms(); }
        public ItemOverrideList getOverrides() { return source.getOverrides(); }
    }

    private static boolean housingFace(BakedQuad quad, int facing) {
        int[] data = quad.getVertexData(); int stride = data.length / 4;
        if (stride < 3 || data.length % 4 != 0) return false;
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(int vertex=0;vertex<4;vertex++) {
            int offset=vertex*stride; double x=Float.intBitsToFloat(data[offset]),y=Float.intBitsToFloat(data[offset+1]),z=Float.intBitsToFloat(data[offset+2]);
            minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);
        }
        return com.vandorlabs.rack.ModuleRackGeometry.housingFace(facing,quad.getFace().getIndex(),minX,minY,minZ,maxX,maxY,maxZ);
    }

    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String texture : TEXTURES)
            event.getMap().registerSprite(new ResourceLocation("vandorlabs", "blocks/module_rack/" + texture));
        event.getMap().registerSprite(new ResourceLocation("vandorlabs","blocks/resource_ports/power_in_off"));
        event.getMap().registerSprite(new ResourceLocation("vandorlabs","blocks/resource_ports/power_in_on"));
    }

    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        com.vandorlabs.items.ModItems.BATTERY_MODULE.addPropertyOverride(new ResourceLocation("vandorlabs","charge"),(stack,world,entity)->com.vandorlabs.rack.ModuleBatteryData.energy(stack)/(float)com.vandorlabs.rack.ModuleBatteryData.CAPACITY);
        for (EnumFacing facing : EnumFacing.values()) {
            int turns = facing == EnumFacing.WEST ? 1 : facing == EnumFacing.NORTH ? 2 : facing == EnumFacing.EAST ? 3 : 0;
            int pitch = facing == EnumFacing.UP ? 90 : facing == EnumFacing.DOWN ? 270 : 0;
            IBakedModel shell = part("shell", pitch, turns);
            IBakedModel[] modules = new IBakedModel[4], autopilotModules = new IBakedModel[4];
            @SuppressWarnings("unchecked") List<BakedQuad>[][] buttons = (List<BakedQuad>[][])new List<?>[4][2];
            @SuppressWarnings("unchecked") List<BakedQuad>[] seated = (List<BakedQuad>[])new List<?>[4];
            @SuppressWarnings("unchecked") List<BakedQuad>[] empty = (List<BakedQuad>[])new List<?>[4];
            @SuppressWarnings("unchecked") List<BakedQuad>[] autopilotSeated = (List<BakedQuad>[])new List<?>[4];
            for (int slot = 0; slot < 4; slot++) {
                autopilotModules[slot] = part("slot_autopilot_"+slot,pitch,turns);
                autopilotSeated[slot] = all(part("slot_seated_autopilot_"+slot,pitch,turns),false);
                modules[slot] = part("slot_" + slot, pitch, turns);
                seated[slot] = all(part("slot_seated_" + slot, pitch, turns), false);
                empty[slot] = all(part("slot_empty_" + slot, pitch, turns), false);
                buttons[slot][0] = all(part("indicator_" + slot + "_off", pitch, turns), true);
                buttons[slot][1] = all(part("indicator_" + slot + "_on", pitch, turns), true);
            }
            ModuleRackModel model = new ModuleRackModel(facing.getIndex(), shell, modules, seated, empty, buttons, autopilotModules, autopilotSeated);
            model.routeMoving=new MovingMesh[4];
            @SuppressWarnings("unchecked") List<BakedQuad>[] routes=(List<BakedQuad>[])new List<?>[4];
            model.routeSeated=routes;
            for(int slot=0;slot<4;slot++){
                model.routeMoving[slot]=new MovingMesh(part("slot_autonavigation_"+slot,pitch,turns));
                routes[slot]=all(part("slot_seated_autonavigation_"+slot,pitch,turns),false);
            }
            model.batteryModules=new IBakedModel[4][5];model.batteryMoving=new MovingMesh[4][5];model.embeddedSeated=new MovingMesh[4][5];model.embeddedEmpty=new MovingMesh[4];model.embeddedIndicators=new MovingMesh[4][2];
            for(int slot=0;slot<4;slot++){model.embeddedEmpty[slot]=new MovingMesh(empty[slot]);for(int on=0;on<2;on++)model.embeddedIndicators[slot][on]=new MovingMesh(buttons[slot][on]);}
            @SuppressWarnings("unchecked") List<BakedQuad>[][] battery=(List<BakedQuad>[][])new List<?>[4][5];model.batterySeated=battery;
            for(int slot=0;slot<4;slot++)for(int level=0;level<5;level++){
                model.batteryModules[slot][level]=part("slot_battery_"+slot+"_"+level,pitch,turns);
                model.batteryMoving[slot][level]=new MovingMesh(model.batteryModules[slot][level]);
                model.batterySeated[slot][level]=all(part("slot_seated_battery_"+slot+"_"+level,pitch,turns),false);model.embeddedSeated[slot][level]=new MovingMesh(model.batterySeated[slot][level]);
            }
            @SuppressWarnings("unchecked") List<BakedQuad>[][] bars=(List<BakedQuad>[][])new List<?>[4][37];model.chargeQuads=bars;model.chargeMeshes=new MovingMesh[4][37];
            for(int bay=0;bay<4;bay++){List<BakedQuad> full=all(part("battery_bar_"+bay,pitch,turns),false);for(int fill=0;fill<=36;fill++){List<BakedQuad> clipped=new ArrayList<>();if(fill>0)for(BakedQuad quad:full)clipped.add(luminous(new BakedQuad(com.vandorlabs.rack.BatteryChargeBar.clip(quad.getVertexData(),fill),quad.getTintIndex(),quad.getFace(),quad.getSprite(),false,quad.getFormat())));bars[bay][fill]=Collections.unmodifiableList(clipped);model.chargeMeshes[bay][fill]=new MovingMesh(bars[bay][fill]);}}
            model.navigationBayFrame=new MovingMesh(part("navigation_bay_frame",pitch,(turns+2)%4));model.navigationBayModule=new MovingMesh(part("navigation_bay_module",pitch,(turns+2)%4));
            MODELS[facing.getIndex()] = model;
            event.getModelRegistry().putObject(new ModelResourceLocation("vandorlabs:module_rack", "facing=" + facing.getName()), model);
        }
        ModuleRackAnimation.reload();
    }

    private static IBakedModel part(String name, int pitch, int turns) {
        ResourceLocation resource = new ResourceLocation("vandorlabs", "block/module_rack/" + name);
        try {
            IModel source = ModelLoaderRegistry.getModel(resource);
            return source.bake(ModelRotation.getModelRotation(pitch, turns * 90), DefaultVertexFormats.ITEM,
                    texture -> Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(texture.toString()));
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot bake Module Rack component " + resource, failure);
        }
    }

    private static List<BakedQuad> all(IBakedModel model, boolean lit) {
        List<BakedQuad> result = new ArrayList<>();
        for (BakedQuad quad : model.getQuads(null, null, 0)) result.add(lit ? luminous(quad) : quad);
        for (EnumFacing side : EnumFacing.values())
            for (BakedQuad quad : model.getQuads(null, side, 0)) result.add(lit ? luminous(quad) : quad);
        return Collections.unmodifiableList(result);
    }

    /** Forge's terrain lighter preserves an authored minimum lightmap. Use the
     * actual format's semantic elements, including shader additions, rather than
     * assuming a seven-int vertex or injecting a permanent glowing TESR pass.
     */
    private static BakedQuad luminous(BakedQuad source) {
        VertexFormat format = new VertexFormat(DefaultVertexFormats.BLOCK);
        UnpackedBakedQuad.Builder builder = new UnpackedBakedQuad.Builder(format);
        builder.setTexture(source.getSprite());
        builder.setQuadOrientation(source.getFace());
        builder.setQuadTint(source.getTintIndex());
        builder.setApplyDiffuseLighting(false);
        float[] value = new float[4];
        for (int vertex = 0; vertex < 4; vertex++) for (int element = 0; element < format.getElementCount(); element++) {
            VertexFormatElement target = format.getElement(element);
            if (target.getUsage() == VertexFormatElement.EnumUsage.UV && target.getIndex() == 1) {
                // UV1 is a normalized signed-short pair (240 is maximum block/sky light).
                builder.put(element, 240F / 32767F, 240F / 32767F, 0, 1);
                continue;
            }
            int match = -1;
            for (int candidate = 0; candidate < source.getFormat().getElementCount(); candidate++) {
                VertexFormatElement input = source.getFormat().getElement(candidate);
                if (input.getUsage() == target.getUsage() && input.getIndex() == target.getIndex()) { match = candidate; break; }
            }
            if (match >= 0) {
                LightUtil.unpack(source.getVertexData(), value, source.getFormat(), vertex, match);
                builder.put(element, value);
            } else builder.put(element);
        }
        BakedQuad unpacked = builder.build();
        return new BakedQuad(unpacked.getVertexData(), source.getTintIndex(), source.getFace(), source.getSprite(), false, format);
    }

    public static List<BakedQuad> batteryQuads(EnumFacing facing,int slot,int level){
        ModuleRackModel model=MODELS[facing.getIndex()];
        return model==null||slot<0||slot>=4?Collections.emptyList():model.batterySeated[slot][Math.max(0,Math.min(4,level))];
    }

    /** Same cached four-bay geometry and moving cassette as the actual placed Rack. */
    public static void emitEmbedded(BufferBuilder buffer,EnumFacing facing,int bay,int level,boolean occupied,boolean visible,boolean moving,double x,double y,double z,double offset,int light){
        ModuleRackModel model=MODELS[facing.getIndex()];if(model==null||bay<0||bay>=4)return;
        if(!occupied)model.embeddedEmpty[bay].draw(buffer,x,y,z,light);
        model.embeddedIndicators[bay][occupied?1:0].draw(buffer,x,y,z,15728880);
        if(visible)(moving?model.batteryMoving[bay][0]:model.embeddedSeated[bay][0]).draw(buffer,x+facing.getFrontOffsetX()*offset,y+facing.getFrontOffsetY()*offset,z+facing.getFrontOffsetZ()*offset,light);
        if(visible)emitBatteryBar(buffer,facing,bay,level,x+facing.getFrontOffsetX()*offset,y+facing.getFrontOffsetY()*offset,z+facing.getFrontOffsetZ()*offset,15728880);
    }

    public static void emitNavigationBay(BufferBuilder buffer,EnumFacing facing,double x,double y,double z,boolean visible,double offset,int light){ModuleRackModel model=MODELS[facing.getIndex()];if(model==null)return;model.navigationBayFrame.draw(buffer,x,y,z,light);if(visible)model.navigationBayModule.draw(buffer,x+facing.getFrontOffsetX()*offset,y,z+facing.getFrontOffsetZ()*offset,light);}
    static void emitBatteryBar(BufferBuilder buffer,EnumFacing facing,int bay,int fill,double x,double y,double z,int light){ModuleRackModel model=MODELS[facing.getIndex()];if(model!=null)model.chargeMeshes[bay][Math.max(0,Math.min(36,fill))].draw(buffer,x,y,z,15728880);}
    static IBakedModel movingModule(EnumFacing facing, int slot) {
        ModuleRackModel model = MODELS[facing.getIndex()];
        return model == null ? null : model.modules[slot];
    }

    static MovingMesh movingMesh(EnumFacing facing, int slot) { return movingMesh(facing,slot,1); }
    static MovingMesh movingMesh(EnumFacing facing, int slot, int kind) {
        ModuleRackModel model = MODELS[facing.getIndex()];
        return model == null ? null : kind==3?model.batteryMoving[slot][0]:kind==2?model.routeMoving[slot]:kind==4?model.autopilotMoving[slot]:model.moving[slot];
    }

    /** Decode once into format-independent attributes. Animation submits these
     * cached vertices in the current vanilla/shader buffer format without
     * cloning vertex arrays, rebuilding quads or reading item models per frame.
     */
    static final class MovingMesh {
        private final float[] vertices;
        MovingMesh(IBakedModel source) {this(all(source,false));}
        MovingMesh(List<BakedQuad> quads) {
            vertices = new float[quads.size() * 4 * 9];
            float[] value = new float[4];
            int at = 0;
            for (BakedQuad quad : quads) {
                VertexFormat format = quad.getFormat();
                int position = element(format, VertexFormatElement.EnumUsage.POSITION, 0);
                int color = element(format, VertexFormatElement.EnumUsage.COLOR, 0);
                int uv = element(format, VertexFormatElement.EnumUsage.UV, 0);
                float shade = quad.shouldApplyDiffuseLighting() ? LightUtil.diffuseLight(quad.getFace()) : 1;
                for (int vertex = 0; vertex < 4; vertex++) {
                    LightUtil.unpack(quad.getVertexData(), value, format, vertex, position);
                    for (int axis = 0; axis < 3; axis++) vertices[at++] = value[axis];
                    LightUtil.unpack(quad.getVertexData(), value, format, vertex, color);
                    for (int channel = 0; channel < 3; channel++) vertices[at++] = value[channel] * shade;
                    vertices[at++] = value[3];
                    LightUtil.unpack(quad.getVertexData(), value, format, vertex, uv);
                    vertices[at++] = value[0]; vertices[at++] = value[1];
                }
            }
        }
        private static int element(VertexFormat format, VertexFormatElement.EnumUsage usage, int index) {
            for (int e = 0; e < format.getElementCount(); e++)
                if (format.getElement(e).getUsage() == usage && format.getElement(e).getIndex() == index) return e;
            throw new IllegalStateException("Missing Module Rack mesh attribute " + usage);
        }
        void draw(BufferBuilder buffer, double x, double y, double z, int light) {
            int sky = light >>> 16 & 65535, block = light & 65535;
            for (int at = 0; at < vertices.length; at += 9)
                buffer.pos(vertices[at] + x, vertices[at + 1] + y, vertices[at + 2] + z)
                        .color(vertices[at + 3], vertices[at + 4], vertices[at + 5], vertices[at + 6])
                        .tex(vertices[at + 7], vertices[at + 8]).lightmap(sky, block).endVertex();
        }
    }

    @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long seed) {
        if (state instanceof IExtendedBlockState && state.getBlock() instanceof BlockModuleRack) {
            Integer choice = ((IExtendedBlockState)state).getValue(BlockModuleRack.HOUSING);
            Integer output=((IExtendedBlockState)state).getValue(BlockModuleRack.POWER_OUT);
            boolean out=output!=null&&output==1;
            int finish=choice==null?-1:choice;
            if ((finish!=housingChoice||out!=powerOut||shell.getClass()!=HousingShell.class)
                    && (finish<0||com.vandorlabs.tiles.ScreenHousingTextures.validChoice(finish))) return housing(finish,out).getQuads(state, side, seed);
        }
        // Only the housing reaches block boundaries. Leave its cull faces intact.
        if (side != null) return shell.getQuads(null, side, 0);
        int occupied = 0, visible = 0, kinds = 0, charge = 0;
        if (state instanceof IExtendedBlockState && state.getBlock() instanceof BlockModuleRack) {
            IExtendedBlockState extended = (IExtendedBlockState)state;
            Integer occupiedValue = extended.getValue(BlockModuleRack.OCCUPIED_MASK);
            Integer visibleValue = extended.getValue(BlockModuleRack.MODULE_MASK);
            Integer typed = extended.getValue(BlockModuleRack.MODULE_KINDS);
            Integer levels=extended.getValue(BlockModuleRack.BATTERY_LEVELS);charge=levels==null?0:levels;
            kinds = typed == null ? 0 : typed;
            occupied = occupiedValue == null ? 0 : occupiedValue & 15;
            visible = visibleValue == null ? occupied : visibleValue & occupied;
        }
        int key=0, place=1;
        for(int slot=0;slot<4;slot++,place*=42) {
            int bit=1<<slot,kind=com.vandorlabs.rack.ModuleRackPolicy.slotKind(kinds,slot);
            int digit=(occupied&bit)==0?0:(visible&bit)==0?1:kind==3?4+com.vandorlabs.rack.BatteryChargeBar.unpack(charge,slot):kind==2?3:kind==4?41:2;
            key+=digit*place;
        }
        synchronized(combinations){List<BakedQuad> found=combinations.get(key);if(found!=null)return found;
            List<BakedQuad> result=new ArrayList<>(shell.getQuads(null,null,0));
            for(int slot=0;slot<4;slot++){result.addAll(indicators[slot][occupied>>>slot&1]);boolean show=(visible&1<<slot)!=0;int kind=com.vandorlabs.rack.ModuleRackPolicy.slotKind(kinds,slot);result.addAll(show?(kind==3?batterySeated[slot][0]:kind==2?routeSeated[slot]:kind==4?autopilotSeated[slot]:seated[slot]):empty[slot]);if(show&&kind==3)result.addAll(chargeQuads[slot][com.vandorlabs.rack.BatteryChargeBar.unpack(charge,slot)]);}
            found=Collections.unmodifiableList(result);int bytes=32+found.size()*8;while(!combinations.isEmpty()&&(cacheBytes+bytes>COMBINATION_BYTES||combinations.size()>=128)){java.util.Iterator<List<BakedQuad>> values=combinations.values().iterator();List<BakedQuad> old=values.next();cacheBytes-=32+old.size()*8;values.remove();}if(bytes<=COMBINATION_BYTES){combinations.put(key,found);cacheBytes+=bytes;}return found;}
    }
    int cachedCombinations(){synchronized(combinations){return combinations.size();}}

    @Override public boolean isAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return true; }
    @Override public boolean isBuiltInRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleTexture() { return shell.getParticleTexture(); }
    @Override public ItemCameraTransforms getItemCameraTransforms() { return shell.getItemCameraTransforms(); }
    @Override public ItemOverrideList getOverrides() { return shell.getOverrides(); }
}
