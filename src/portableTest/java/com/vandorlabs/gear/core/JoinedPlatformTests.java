package com.vandorlabs.gear.core;
import java.util.*;
public final class JoinedPlatformTests {
    private static JoinedPlatform.Cell c(int x,int z){return new JoinedPlatform.Cell(x,z);}
    private static void reject(List<JoinedPlatform.Cell> arms,Set<JoinedPlatform.Cell> blocks){try{JoinedPlatform.connected(arms,blocks);}catch(IllegalArgumentException expected){return;}throw new AssertionError("Disconnected platform accepted");}
    public static void main(String[] args){int checks=0;
        for(int width=2;width<=32;width++)for(int depth=2;depth<=32;depth++){
            List<JoinedPlatform.Cell> arms=Arrays.asList(c(0,0),c(width,0),c(0,depth),c(width,depth));Set<JoinedPlatform.Cell> ring=new HashSet<>();for(int x=0;x<=width;x++){ring.add(c(x,0));ring.add(c(x,depth));}for(int z=0;z<=depth;z++){ring.add(c(0,z));ring.add(c(width,z));}
            if(JoinedPlatform.connected(arms,ring).size()!=2*width+2*depth)throw new AssertionError("Hollow rectangle lost cells");checks++;
            Set<JoinedPlatform.Cell> broken=new HashSet<>(ring);broken.remove(c(1,0));broken.remove(c(0,1));reject(arms,broken);checks++;
            Set<JoinedPlatform.Cell> diagonals=new HashSet<>(arms);diagonals.add(c(1,1));reject(arms,diagonals);checks++;
        }
        List<JoinedPlatform.Cell> line=Arrays.asList(c(-16,0),c(16,0));Set<JoinedPlatform.Cell> blocks=new HashSet<>();for(int x=-16;x<=16;x++)blocks.add(c(x,0));if(JoinedPlatform.connected(line,blocks).size()!=33)throw new AssertionError();blocks.remove(c(0,0));reject(line,blocks);checks+=2;
        List<JoinedPlatform.Cell> three=Arrays.asList(c(0,0),c(32,0),c(64,0));Set<JoinedPlatform.Cell> longLine=new HashSet<>();for(int x=0;x<=64;x++)longLine.add(c(x,0));if(JoinedPlatform.connected(three,longLine).size()!=65)throw new AssertionError("Three neighbours at maximum range");checks++;
        System.out.println("joined-platform PASS checks="+checks+" hollow rectangles all corners face-connected broken corners diagonal-only rejected range32 line gaps");
    }
}
