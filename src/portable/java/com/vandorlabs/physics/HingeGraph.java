package com.vandorlabs.physics;
import java.util.*;
/** Bridge-only articulated tree. Parallel links and closed loops are rigid constraints. */
public final class HingeGraph {
    public static final class Edge {
        public final int a,b;public final boolean articulated;
        public Edge(int a,int b,boolean articulated){this.a=a;this.b=b;this.articulated=articulated;}
        int other(int v){return v==a?b:a;}
    }
    public static final class Joint {
        public final int edge,parent,child;
        Joint(int edge,int parent,int child){this.edge=edge;this.parent=parent;this.child=child;}
    }
    public final int[] body;public final int bodies;public final List<Joint> joints;
    public HingeGraph(int nodes,int root,List<Edge> edges){
        if(nodes<1||root<0||root>=nodes)throw new IllegalArgumentException("Invalid hinge graph");
        List<List<Integer>> adjacent=new ArrayList<>();for(int i=0;i<nodes;i++)adjacent.add(new ArrayList<>());
        for(int i=0;i<edges.size();i++){Edge e=edges.get(i);if(e.a<0||e.b<0||e.a>=nodes||e.b>=nodes)throw new IllegalArgumentException("Invalid hinge endpoint");adjacent.get(e.a).add(i);adjacent.get(e.b).add(i);}
        int[] seen=new int[nodes],low=new int[nodes],parent=new int[nodes],parentEdge=new int[nodes],cursor=new int[nodes];Arrays.fill(parent,-1);Arrays.fill(parentEdge,-1);boolean[] bridge=new boolean[edges.size()];int clock=0;
        ArrayDeque<Integer> stack=new ArrayDeque<>();
        for(int start=0;start<nodes;start++)if(seen[start]==0){
            seen[start]=low[start]=++clock;stack.push(start);
            while(!stack.isEmpty()){
                int v=stack.peek();if(cursor[v]<adjacent.get(v).size()){
                    int ei=adjacent.get(v).get(cursor[v]++);if(ei==parentEdge[v])continue;int w=edges.get(ei).other(v);
                    if(seen[w]==0){parent[w]=v;parentEdge[w]=ei;seen[w]=low[w]=++clock;stack.push(w);}else low[v]=Math.min(low[v],seen[w]);
                }else{stack.pop();if(parent[v]>=0){bridge[parentEdge[v]]=low[v]>seen[parent[v]];low[parent[v]]=Math.min(low[parent[v]],low[v]);}}
            }
        }
        int[] union=new int[nodes];for(int i=0;i<nodes;i++)union[i]=i;
        for(int i=0;i<edges.size();i++)if(!bridge[i]||!edges.get(i).articulated)join(union,edges.get(i).a,edges.get(i).b);
        // Disconnected decorative/owned cargo has no independently movable hitch.
        boolean[] reachable=new boolean[nodes];stack.push(root);reachable[root]=true;while(!stack.isEmpty()){int v=stack.pop();for(int ei:adjacent.get(v)){int w=edges.get(ei).other(v);if(!reachable[w]){reachable[w]=true;stack.push(w);}}}
        for(int i=0;i<nodes;i++)if(!reachable[i])join(union,root,i);
        Map<Integer,Integer> ids=new LinkedHashMap<>();ids.put(find(union,root),0);body=new int[nodes];for(int i=0;i<nodes;i++)body[i]=ids.computeIfAbsent(find(union,i),k->ids.size());bodies=ids.size();
        List<List<Integer>> bodyEdges=new ArrayList<>();for(int i=0;i<bodies;i++)bodyEdges.add(new ArrayList<>());
        for(int i=0;i<edges.size();i++){Edge e=edges.get(i);if(body[e.a]!=body[e.b]){bodyEdges.get(body[e.a]).add(i);bodyEdges.get(body[e.b]).add(i);}}
        List<Joint> result=new ArrayList<>();boolean[] visited=new boolean[bodies];stack.add(0);visited[0]=true;
        while(!stack.isEmpty()){int p=stack.removeFirst();for(int ei:bodyEdges.get(p)){Edge e=edges.get(ei);int child=body[e.a]==p?body[e.b]:body[e.a];if(visited[child])continue;visited[child]=true;result.add(new Joint(ei,p,child));stack.addLast(child);}}
        joints=Collections.unmodifiableList(result);
    }
    private static int find(int[] p,int v){int root=v;while(p[root]!=root)root=p[root];while(p[v]!=v){int next=p[v];p[v]=root;v=next;}return root;}
    private static void join(int[] p,int a,int b){p[find(p,b)]=find(p,a);}
}
