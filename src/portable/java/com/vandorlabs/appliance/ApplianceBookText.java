package com.vandorlabs.appliance;
import java.util.*;
/** Conservative vanilla-book pagination; whole recipe entries are admitted together. */
public final class ApplianceBookText {
    public static final int MAX_PAGES=50,COLUMNS=18,LINES=12;
    private ApplianceBookText(){}
    public static List<String> pages(String text){
        List<String> result=new ArrayList<>();StringBuilder page=new StringBuilder();int lines=0;
        for(String paragraph:text.replace("\r","").split("\n",-1)){
            String rest=paragraph;do{int end=Math.min(COLUMNS,rest.length());if(end<rest.length()){int space=rest.lastIndexOf(' ',end);if(space>0)end=space;}String line=rest.substring(0,end);rest=rest.substring(end);if(rest.startsWith(" "))rest=rest.substring(1);if(lines==LINES){result.add(page.toString());page.setLength(0);lines=0;}if(lines++>0)page.append('\n');page.append(line);}while(!rest.isEmpty());
        }
        if(page.length()>0||result.isEmpty())result.add(page.toString());return result;
    }
    public static List<String> recipes(List<String> entries){List<String> pages=new ArrayList<>();for(String entry:entries){List<String> recipe=pages(entry);if(pages.size()+recipe.size()>MAX_PAGES)break;pages.addAll(recipe);}return pages;}
}
