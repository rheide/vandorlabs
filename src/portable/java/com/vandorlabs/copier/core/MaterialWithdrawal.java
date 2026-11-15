package com.vandorlabs.copier.core;

/** Deterministic, non-mutating resource allocation shared by Source and native copy preflight. */
public final class MaterialWithdrawal {
    public interface Matcher {boolean matches(int need,int slot);}
    public static final class Missing extends IllegalArgumentException {
        private static final long serialVersionUID=1L;
        public final int need;
        public final long count;
        private Missing(int need,long count){super("Missing construction material");this.need=need;this.count=count;}
    }
    public static int[] remaining(long[] needs,int[] available,Matcher matches){
        if(needs.length>32768||available.length>8192)throw new IllegalArgumentException("Construction material bounds");
        int[] result=available.clone();for(int count:result)if(count<0)throw new IllegalArgumentException("Negative material slot");
        for(int n=0;n<needs.length;n++){long missing=needs[n];if(missing<0)throw new IllegalArgumentException("Negative material cost");for(int slot=0;slot<result.length&&missing>0;slot++){if(result[slot]==0||!matches.matches(n,slot))continue;int used=(int)Math.min(missing,result[slot]);result[slot]-=used;missing-=used;}if(missing>0)throw new Missing(n,missing);}
        return result;
    }
    private MaterialWithdrawal(){}
}
