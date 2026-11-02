package org.marj4n.smooth_fix.benchmark;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Bounded, ordered report fragments. Gameplay state and small control packets are unchanged. */
public final class BenchmarkReportTransfer {
 public static final int PART_ACTION=8, PART_CHARS=16000, MAX_PARTS=128, MAX_RAW_BYTES=8*1024*1024;
 private BenchmarkReportTransfer(){}
 public static List<String> encode(String json)throws IOException{
  byte[] raw=json.getBytes(StandardCharsets.UTF_8);
  if(raw.length>MAX_RAW_BYTES)throw new IOException("Benchmark report exceeds raw byte bound; local report retained");
  var bytes=new ByteArrayOutputStream();try(var gzip=new GZIPOutputStream(bytes)){gzip.write(raw);}
  String base64=Base64.getEncoder().encodeToString(bytes.toByteArray());
  int count=(base64.length()+PART_CHARS-1)/PART_CHARS;
  if(count>MAX_PARTS)throw new IOException("Benchmark report exceeds transfer bound; local report retained");
  List<String> out=new ArrayList<>(count);
  for(int i=0;i<count;i++)out.add(i+"/"+count+":"+base64.substring(i*PART_CHARS,Math.min(base64.length(),(i+1)*PART_CHARS)));
  return out;
 }
 public static final class Assembly {
  private int phase=-1,count,next;private long started;
  private final StringBuilder data=new StringBuilder();
  public void clear(){phase=-1;count=next=0;data.setLength(0);data.trimToSize();}
  public String accept(int phase,String frame)throws IOException{
   try{
    int slash=frame.indexOf('/'),colon=frame.indexOf(':');
    if(slash<1||slash>3||colon<=slash+1||colon>7)throw new IOException("Invalid report fragment header");
    int index=Integer.parseInt(frame.substring(0,slash)),total=Integer.parseInt(frame.substring(slash+1,colon));
    String content=frame.substring(colon+1);
    if(total<1||total>MAX_PARTS||index<0||index>=total||content.isEmpty()||content.length()>PART_CHARS)throw new IOException("Invalid report fragment bounds");
    if(next==0){if(index!=0)throw new IOException("Report must begin at fragment zero");this.phase=phase;count=total;started=System.nanoTime();}
    if(phase!=this.phase||total!=count||index!=next||System.nanoTime()-started>30_000_000_000L)throw new IOException("Out-of-order, stale or interleaved report");
    data.append(content);next++;
    if(next<count)return null;
    byte[] compressed=Base64.getDecoder().decode(data.toString());var raw=new ByteArrayOutputStream();
    try(var gzip=new GZIPInputStream(new ByteArrayInputStream(compressed))){
     byte[] buffer=new byte[8192];int n;
     while((n=gzip.read(buffer))!=-1){if(raw.size()+n>MAX_RAW_BYTES)throw new IOException("Expanded report exceeds byte bound");raw.write(buffer,0,n);}
    }
    String value=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(raw.toByteArray())).toString();clear();return value;
   }catch(IOException|IllegalArgumentException failure){clear();if(failure instanceof IOException io)throw io;throw new IOException("Invalid benchmark report encoding",failure);}
  }
 }
}
