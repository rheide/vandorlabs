package com.vandorlabs.client;

/** Dependency-free boundary checks for variable-size render data retention. */
public final class BoundedRenderCacheChecks {
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] ignored)throws Exception{
        BoundedRenderCache<String,String> cache=new BoundedRenderCache<>(3,100);
        cache.put("a","a",30);cache.put("b","b",30);cache.put("c","c",30);cache.get("a");
        cache.put("d","d",40);
        need(cache.get("b")==null&&cache.get("a").equals("a")&&cache.retainedBytes()==100,"weighted LRU discarded the recent entry");
        cache.put("a","updated",10);
        need(cache.retainedBytes()==80&&cache.size()==3&&cache.get("a").equals("updated"),"replacement double-counted retained bytes");
        cache.put("huge","huge",101);
        need(cache.size()==3&&cache.retainedBytes()==80&&cache.get("huge")==null,"oversized admission evicted the useful working set");
        cache.put("empty","empty",0);
        need(cache.size()==3&&cache.get("c")==null&&cache.retainedBytes()==50,"entry bound did not cover zero-sized data");
        cache.clear();need(cache.size()==0&&cache.retainedBytes()==0,"clear retained entries or weights");
        try{cache.put("bad","bad",-1);throw new AssertionError("negative weight accepted");}catch(IllegalArgumentException expected){}
        BoundedRenderCache<Integer,Integer> wide=new BoundedRenderCache<>(2,Long.MAX_VALUE);
        wide.put(1,1,Long.MAX_VALUE-1);wide.put(2,2,2);
        need(wide.size()==1&&wide.retainedBytes()==2&&wide.get(1)==null,"retained-byte arithmetic overflowed");
        java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newFixedThreadPool(4);
        try{
            java.util.List<java.util.concurrent.Future<?>> jobs=new java.util.ArrayList<>();
            for(int worker=0;worker<4;worker++){final int id=worker;jobs.add(workers.submit(()->{for(int i=0;i<4000;i++){cache.put("worker"+id+"/"+i,"value",i%37);cache.get("worker"+id+"/"+(i-1));}}));}
            for(java.util.concurrent.Future<?> job:jobs)job.get();
        }finally{workers.shutdownNow();}
        need(cache.size()<=3&&cache.retainedBytes()<=100&&cache.retainedBytes()>=0,"concurrent updates escaped either bound");
        System.out.println("item-variant-cache retention PASS weighted LRU, replacement, oversize rejection, zero-size entry cap, overflow and concurrent bounds");
    }
}
