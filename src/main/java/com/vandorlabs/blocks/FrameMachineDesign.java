package com.vandorlabs.blocks;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared dimensions and authored attachment anchors for the existing Core frame system. */
public final class FrameMachineDesign {
    public String design,block,model;
    public int width,height,depth;
    public double[] chip_anchor,chip_size;
    public String[] state_models;
    public Surface[] surfaces=new Surface[0];
    public static final class Surface {public String id,label;public double[][] corners;public double[] normal;}
    private static final Map<String,FrameMachineDesign> DESIGNS=load();
    private static Map<String,FrameMachineDesign> load(){
        try(InputStream stream=FrameMachineDesign.class.getResourceAsStream("/assets/vandorlabs/data/frame_machines.json")){
            if(stream==null)throw new IllegalStateException("Missing frame machine catalog");
            Map<String,FrameMachineDesign> designs=new LinkedHashMap<>();
            for(FrameMachineDesign value:new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),FrameMachineDesign[].class)){
                if(value.width<1||value.height<2||value.depth<1||value.width>16||value.height>16||value.depth>16||designs.put(value.design,value)!=null)
                    throw new IllegalStateException("Invalid frame machine design");
            }
            return Collections.unmodifiableMap(designs);
        }catch(IOException error){throw new IllegalStateException(error);}
    }
    public static Collection<FrameMachineDesign> all(){return DESIGNS.values();}
    public static FrameMachineDesign get(String id){FrameMachineDesign result=DESIGNS.get(id);if(result==null)throw new IllegalArgumentException(id);return result;}
    public boolean hasChip(){return chip_anchor!=null;}
}
