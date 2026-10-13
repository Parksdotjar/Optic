package dev.optic.diagnostics;

import java.util.*;
import java.nio.file.*;
import java.io.IOException;

/** Offline only. Exact quantiles from bounded traces, never called by capture. */
public final class BenchmarkSummary {
    public record Stats(int frames,double seconds,double averageFps,double low1Fps,double p50Ms,double p95Ms,double p99Ms,long stutters) {}
    public static Stats summarize(long[] intervals) {
        if(intervals.length<2 || intervals.length>240_000) throw new IllegalArgumentException("Trace must contain 2..240000 intervals");
        long[] sorted=intervals.clone(); double total=0; long stutters=0;
        for(long ns:sorted) { if(ns<=0 || ns>60_000_000_000L) throw new IllegalArgumentException("Invalid frame interval"); total+=ns; if(ns>50_000_000) stutters++; }
        Arrays.sort(sorted); int slow=Math.max(1,(int)Math.ceil(sorted.length*.01)); double slowTotal=0;
        for(int i=sorted.length-slow;i<sorted.length;i++) slowTotal+=sorted[i];
        return new Stats(sorted.length,total/1e9,sorted.length*1e9/total,slow*1e9/slowTotal,quantile(sorted,.5),quantile(sorted,.95),quantile(sorted,.99),stutters);
    }
    private static double quantile(long[] a,double q) { return a[Math.max(0,(int)Math.ceil(a.length*q)-1)]/1e6; }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]); Files.createDirectories(root);
        var csv=new StringBuilder("trial,mode,seconds,frames,average_fps,low_1_percent_fps,p50_ms,p95_ms,p99_ms,stutters_over_50ms,eligible\n");
        Map<String,Integer> eligible=new TreeMap<>();
        try(var directories=Files.list(root)) {
            for(Path dir:directories.filter(Files::isDirectory).sorted().limit(10000).toList()) {
                Path frames=dir.resolve("frames.csv"),environment=dir.resolve("environment.txt");
                if(!Files.isRegularFile(frames) || !Files.isRegularFile(environment)) continue;
                if(Files.size(frames)>12*1024*1024 || Files.size(environment)>65536) throw new IOException("Oversized trace: "+dir.getFileName());
                Properties env=new Properties(); try(var r=Files.newBufferedReader(environment)) { env.load(r); }
                String mode=env.getProperty("mode","");
                if(!Set.of("disabled","idle","record","obs").contains(mode)) throw new IOException("Unknown mode in "+dir.getFileName());
                long[] data=new long[240000]; int count=0;
                try(var r=Files.newBufferedReader(frames)) {
                    if(!"frame,ns".equals(r.readLine())) throw new IOException("Expected frame,ns CSV header");
                    for(String line;(line=r.readLine())!=null;) {
                        String[] pair=line.split(",");
                        if(pair.length!=2 || count==data.length || Integer.parseInt(pair[0])!=count) throw new IOException("Incomplete/out-of-order trace");
                        data[count++]=Long.parseLong(pair[1]);
                    }
                }
                Stats s=summarize(Arrays.copyOf(data,count));
                boolean valid=s.seconds>=120 && "false".equals(env.getProperty("unfocused")) && "false".equals(env.getProperty("overflow")) && Integer.toString(count).equals(env.getProperty("samples"));
                if(valid) eligible.merge(mode,1,Integer::sum);
                csv.append(String.format(Locale.ROOT,"%s,%s,%.3f,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%s%n",dir.getFileName().toString().replaceAll("[^A-Za-z0-9_-]","_"),mode,s.seconds,s.frames,s.averageFps,s.low1Fps,s.p50Ms,s.p95Ms,s.p99Ms,s.stutters,valid));
            }
        }
        Path output=root.resolve("summary.csv"); Files.writeString(output,csv);
        for(String mode:List.of("disabled","idle","record","obs")) System.out.println(mode+": "+eligible.getOrDefault(mode,0)+" eligible runs (minimum 3; matching workload/quality still requires review)");
        System.out.println("Summary: "+output+". Eligibility is not an OBS performance claim.");
    }
    private BenchmarkSummary() {}
}
