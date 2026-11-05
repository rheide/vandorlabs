package com.vandorlabs.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/** Categorized material rows; saved IDs, filtering, action rows and widgets stay host-owned. */
public final class TextureListLayout {
    private TextureListLayout() { }
    public static List<String> categories(Collection<String> groups){
        Set<String> paths=new HashSet<String>(groups);
        for(String path:new ArrayList<String>(paths))while(path.contains("/")){path=path.substring(0,path.lastIndexOf('/'));paths.add(path);}
        List<String> result=new ArrayList<String>(paths);result.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));return result;
    }
    public static int compareOptions(String a,int aId,String b,int bId){int order=String.CASE_INSENSITIVE_ORDER.compare(a,b);return order==0?Integer.compare(aId,bId):order;}
    /** Children precede direct options. Group lists are already sorted; search uses a different, original input order. */
    public static <T> void appendCategories(List<Integer> rows,List<String> categories,Map<String,List<T>> groups,Set<String> expanded,ToIntFunction<T> choice){appendCategories(rows,categories,groups,expanded,choice,"");}
    private static <T> void appendCategories(List<Integer> rows,List<String> categories,Map<String,List<T>> groups,Set<String> expanded,ToIntFunction<T> choice,String parent){
        for(int index=0;index<categories.size();index++){
            String category=categories.get(index);int slash=category.lastIndexOf('/');String directParent=slash<0?"":category.substring(0,slash);
            if(!directParent.equals(parent))continue;
            rows.add(categoryRow(index));
            if(expanded.contains(category)){appendCategories(rows,categories,groups,expanded,choice,category);for(T option:groups.getOrDefault(category,Collections.emptyList()))rows.add(choice.applyAsInt(option));}
        }
    }
    public static String normalizeQuery(String value){return value.trim().toLowerCase(Locale.ROOT);}
    /** Read current presentation strings when rebuilding, including a host's replaced thumbnail name. */
    public static boolean matches(String query,String label,String category,String texture){
        String text=(label+" "+category+" "+texture).toLowerCase(Locale.ROOT);for(String token:query.split("\\s+"))if(!text.contains(token))return false;return true;
    }
    public static void expandAncestors(Set<String> expanded,String path){expanded.add(path);while(path.contains("/")){path=path.substring(0,path.lastIndexOf('/'));expanded.add(path);}}
    public static boolean parentsExpanded(Set<String> expanded,String path){while(path.contains("/")){path=path.substring(0,path.lastIndexOf('/'));if(!expanded.contains(path))return false;}return true;}
    public static int categoryRow(int categoryIndex){return -200000-categoryIndex;}
    public static int categoryIndex(int row){return -200000-row;}
    public static boolean pinCategory(int row,int visibleRows,boolean emptyQuery){return emptyQuery&&visibleRows>1&&row==0;}
    public static int centeredScroll(int index,int visibleRows,int maximum){return Math.max(0,Math.min(maximum,index-visibleRows/2));}
    public static boolean needsReveal(int index,int scroll,int visibleRows){return index>=0&&(index<=scroll||index>=scroll+visibleRows);}
    public static int indent(String path){return Math.min(48,8*(path.length()-path.replace("/","").length()));}
    public static String categoryLabel(String path){return path.substring(path.lastIndexOf('/')+1);}
}
