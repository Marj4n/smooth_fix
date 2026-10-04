package org.marj4n.smooth_fix.benchmark;

import com.mojang.datafixers.util.Either;
import net.minecraft.registry.entry.*;
import net.minecraft.server.world.*;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.*;
import net.minecraft.world.gen.chunk.placement.*;
import net.minecraft.world.gen.structure.Structure;
import org.marj4n.smooth_fix.mixin.common.ServerChunkFutureAccess;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Main-thread cursor with one native asynchronous chunk request at a time. */
public final class IncrementalStructureSearch implements AutoCloseable {
    private static final ChunkTicketType<ChunkPos> TICKET=ChunkTicketType.create("smoothfix_structure_search",Comparator.comparingLong(ChunkPos::toLong));
    public record Located(BlockPos position,RegistryEntry<Structure> structure) { }
    private record Group(RandomSpreadStructurePlacement placement,List<RegistryEntry<Structure>> structures) { }
    private record Candidate(ChunkPos position,StructurePlacement placement,List<RegistryEntry<Structure>> structures) { }
    private final ServerWorld world;
    private final BlockPos origin;
    private final StructurePlacementCalculator calculator;
    private final List<Group> groups=new ArrayList<>();
    private final List<Candidate> concentric=new ArrayList<>();
    private final int step,worldLimit;
    private final long started=System.nanoTime();
    private int inner,radius,groupIndex,concentricIndex,ticketRadius;
    private StructureSearchRing ring;
    private Candidate checking;
    private CompletableFuture<Either<Chunk,ChunkHolder.Unloaded>> pending;
    private ChunkPos ticketPos;
    private boolean closed,fullRequest;
    private long regionsChecked,candidatesChecked;
    private Located found;
    private String unavailable;

    public IncrementalStructureSearch(ServerWorld world,RegistryEntryList<Structure> entries,BlockPos origin,int step){
        this.world=world;this.origin=origin;this.step=step;calculator=world.getChunkManager().getStructurePlacementCalculator();
        var border=world.getWorldBorder();double farX=Math.max(Math.abs(border.getBoundWest()-origin.getX()),Math.abs(border.getBoundEast()-origin.getX()));
        double farZ=Math.max(Math.abs(border.getBoundNorth()-origin.getZ()),Math.abs(border.getBoundSouth()-origin.getZ()));
        worldLimit=(int)Math.ceil(Math.hypot(farX,farZ));radius=Math.min(step,worldLimit);
        if(!world.getServer().getSaveProperties().getGeneratorOptions().shouldGenerateStructures()){unavailable="skipped_structure_generation_disabled";return;}
        Map<StructurePlacement,List<RegistryEntry<Structure>>> grouped=new LinkedHashMap<>();
        for(var entry:entries)for(var placement:calculator.getPlacements(entry))grouped.computeIfAbsent(placement,p->new ArrayList<>()).add(entry);
        for(var entry:grouped.entrySet()){
            if(entry.getKey() instanceof RandomSpreadStructurePlacement random)groups.add(new Group(random,entry.getValue()));
            else if(entry.getKey() instanceof ConcentricRingsStructurePlacement circles){var positions=calculator.getPlacementPositions(circles);if(positions!=null)for(var pos:positions)concentric.add(new Candidate(pos,circles,entry.getValue()));}
        }
        concentric.sort(Comparator.comparingLong(c->distanceSquared(c.placement.getLocatePos(c.position))));
        if(groups.isEmpty() && concentric.isEmpty())unavailable=grouped.isEmpty()?"skipped_structure_not_generated_in_dimension":"skipped_unsupported_structure_placement";
    }
    public Located found(){return found;}
    public String unavailable(){return unavailable;}
    public int radius(){return radius;}
    public Map<String,Object> progress(){return Map.of("radiusBlocks",radius,"completedRadiusBlocks",inner,"radiusStepBlocks",step,"regionsChecked",regionsChecked,"candidateChunksChecked",candidatesChecked,"chunkRequestPending",pending!=null,"elapsedMs",(System.nanoTime()-started)/1e6);}
    public void tick(){
        if(closed || unavailable!=null || found!=null)return;
        if(pending!=null){
            if(!pending.isDone())return;
            Chunk chunk=pending.getNow(null).left().orElse(null);pending=null;
            if(chunk==null)throw new IllegalStateException("Structure candidate chunk unavailable at "+checking.position);
            if(fullRequest){releaseTicket();found=new Located(checking.placement.getLocatePos(checking.position),checking.structures.get(0));return;}
            candidatesChecked++;
            for(var entry:checking.structures){StructureStart start=chunk.getStructureStart(entry.value());if(start!=null && start.hasChildren()){
                // Load only the selected destination fully, before teleport/readiness.
                checking=new Candidate(checking.position,checking.placement,List.of(entry));fullRequest=true;releaseTicket();request(ChunkStatus.FULL);return;
            }}
            releaseTicket();checking=null;
        }
        long deadline=System.nanoTime()+1_000_000L;
        for(int examined=0;examined<64 && System.nanoTime()<deadline;examined++){
            Candidate candidate=nextCandidate();if(candidate==null){
                if(groupIndex>=groups.size() && concentricIndex>=concentric.size()){
                    boolean finiteCandidatesExhausted=groups.isEmpty() && (concentric.isEmpty() || distanceSquared(concentric.get(concentric.size()-1).placement.getLocatePos(concentric.get(concentric.size()-1).position))<=(long)radius*radius);
                    if(finiteCandidatesExhausted || radius>=worldLimit){unavailable="skipped_structure_not_found_within_world_border";return;}
                    inner=radius;radius=StructureSearchRing.nextRadius(radius,step,worldLimit);groupIndex=0;concentricIndex=0;ring=null;
                }
                continue;
            }
            BlockPos pos=candidate.placement.getLocatePos(candidate.position);
            if(!world.getWorldBorder().contains(pos) || !StructureSearchRing.inBand(distanceSquared(pos),inner,radius))continue;
            checking=candidate;request(ChunkStatus.STRUCTURE_STARTS);return;
        }
    }
    private Candidate nextCandidate(){
        if(concentricIndex<concentric.size())return concentric.get(concentricIndex++);
        if(groupIndex>=groups.size())return null;
        Group group=groups.get(groupIndex);int spacing=group.placement.getSpacing();
        // A square perimeter's corners are sqrt(2) times farther than its sides.
        if(ring==null){int first=Math.max(0,(int)Math.floor(inner/(16.0*spacing*Math.sqrt(2)))-2),last=(int)Math.ceil(radius/(16.0*spacing))+2;ring=new StructureSearchRing(first,last);}
        if(!ring.hasNext()){groupIndex++;ring=null;return null;}
        StructureSearchRing.Cell cell=ring.next();regionsChecked++;
        int rx=Math.floorDiv(origin.getX()>>4,spacing)+cell.x(),rz=Math.floorDiv(origin.getZ()>>4,spacing)+cell.z();
        ChunkPos pos=group.placement.getStartChunk(calculator.getStructureSeed(),rx*spacing,rz*spacing);
        return new Candidate(pos,group.placement,group.structures);
    }
    private long distanceSquared(BlockPos pos){long dx=(long)pos.getX()-origin.getX(),dz=(long)pos.getZ()-origin.getZ();return dx*dx+dz*dz;}
    private void request(ChunkStatus status){
        ticketPos=checking.position;ticketRadius=33-ChunkLevels.getLevelFromStatus(status);
        world.getChunkManager().addTicket(TICKET,ticketPos,ticketRadius,ticketPos);
        pending=((ServerChunkFutureAccess)world.getChunkManager()).smoothfix$requestChunk(ticketPos.x,ticketPos.z,status,true);
    }
    private void releaseTicket(){if(ticketPos!=null){world.getChunkManager().removeTicket(TICKET,ticketPos,ticketRadius,ticketPos);ticketPos=null;}}
    @Override public void close(){closed=true;releaseTicket();pending=null;checking=null;}
}
