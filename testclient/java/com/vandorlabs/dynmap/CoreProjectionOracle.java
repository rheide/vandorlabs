package com.vandorlabs.dynmap;

import com.vandorlabs.geometry.CoreMesh;

/** Standalone reference reads pure shared geometry from the exact fixture JAR. */
public final class CoreProjectionOracle {
    public static void main(String[] args){
        StringBuilder json=new StringBuilder("{");boolean firstId=true;
        for(String id:CoreMesh.IDS){if(!firstId)json.append(',');firstId=false;json.append('"').append(id).append("\":[");boolean firstFace=true;
            for(CoreMesh.Face face:CoreMesh.faces(id)){if(!firstFace)json.append(',');firstFace=false;json.append('[');
                for(int vertex=0;vertex<4;vertex++){if(vertex>0)json.append(',');json.append('[');for(int axis=0;axis<3;axis++){if(axis>0)json.append(',');json.append(face.coordinate(vertex,axis));}json.append(']');}json.append(']');
            }json.append(']');
        }System.out.println(json.append('}'));
    }
}
