package org.marj4n.smooth_fix.benchmark;

/** Constant-memory perimeter cursor; each placement region appears once per band. */
public final class StructureSearchRing {
    private final int last;
    private int ring, offset;
    public record Cell(int x,int z) { }
    public StructureSearchRing(int first,int last){this.ring=first;this.last=last;}
    public boolean hasNext(){return ring<=last;}
    public Cell next(){
        if(!hasNext())throw new java.util.NoSuchElementException();
        if(ring==0){ring++;return new Cell(0,0);}
        int edge=2*ring,side=offset/edge,n=offset%edge;
        Cell cell=switch(side){case 0->new Cell(-ring+n,-ring);case 1->new Cell(ring,-ring+n);case 2->new Cell(ring-n,ring);default->new Cell(-ring,ring-n);};
        if(++offset==8*ring){offset=0;ring++;}return cell;
    }
    public static int nextRadius(int radius,int step,int worldLimit){return (int)Math.min((long)worldLimit,(long)radius+step);}
    public static boolean inBand(long distanceSquared,int inner,int outer){return distanceSquared<=(long)outer*outer && (inner==0 || distanceSquared>(long)inner*inner);}
}
