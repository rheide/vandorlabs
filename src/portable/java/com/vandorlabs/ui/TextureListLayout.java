package com.vandorlabs.ui;

import java.util.*;

/** Stable category ordering, expansion and pinned category rows for texture pickers. */
public final class TextureListLayout {
    private TextureListLayout(){}
    public static List<String> categories(Collection<String> leaves){
        Set<String> paths=new HashSet<>(leaves);
        for(String path:new ArrayList<>(paths))while(path.contains("/")){path=path.substring(0,path.lastIndexOf('/'));paths.add(path);}
        List<String> result=new ArrayList<>(paths);
        result.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        return result;
    }
    public static int compareOptions(String firstLabel,int firstId,String secondLabel,int secondId){
        int compared=String.CASE_INSENSITIVE_ORDER.compare(firstLabel,secondLabel);
        return compared!=0?compared:Integer.compare(firstId,secondId);
    }
    public static boolean parentsExpanded(String path,Set<String> expanded){
        while(path.contains("/")){path=path.substring(0,path.lastIndexOf('/'));if(!expanded.contains(path))return false;}
        return true;
    }
    public static List<Integer> rows(List<String> categories,Map<String,List<Integer>> groups,Set<String> expanded,int... trailing){
        List<Integer> result=new ArrayList<>();
        for(int c=0;c<categories.size();c++){
            String category=categories.get(c);
            if(!parentsExpanded(category,expanded))continue;
            result.add(-c-1);
            if(expanded.contains(category))result.addAll(groups.getOrDefault(category,Collections.emptyList()));
        }
        for(int choice:trailing)result.add(choice);
        return result;
    }
    public static int indent(String path){return Math.min(48,8*(path.length()-path.replace("/", "").length()));}
    public static int pinnedChoice(int row,int choice,List<String> categories,String category){return row==0&&choice>=0?-categories.indexOf(category)-1:choice;}
}
