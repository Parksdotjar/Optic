// Optic's own Windows process-audio helper. Built from source, never downloaded.
// WASAPI process loopback: Windows build 20348+. No desktop-wide fallback.
using System;
using System.IO;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Threading;
using System.Collections.Concurrent;

[ComImport, Guid("72A22D78-CDE4-431D-B8CC-843A71199B6D"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface IOperation { [PreserveSig] int GetActivateResult(out int result, [MarshalAs(UnmanagedType.IUnknown)] out object audio); }
[Guid("41D949AB-9862-444A-80F6-C261334DA5EB"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface ICompletion { [PreserveSig] int ActivateCompleted(IOperation operation); }
[Guid("94EA2B94-E9CC-49E0-C0FF-EE64CA8F5B90"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface IAgileObject {}
[ComImport, Guid("1CB9AD4C-DBFA-4c32-B178-C2F568A703B2"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface IAudioClient {
 [PreserveSig] int Initialize(int mode, uint flags, long duration, long period, ref WaveFormat format, IntPtr session);
 [PreserveSig] int GetBufferSize(out uint frames);
 [PreserveSig] int GetStreamLatency(out long latency);
 [PreserveSig] int GetCurrentPadding(out uint padding);
 [PreserveSig] int IsFormatSupported(int mode, IntPtr format, out IntPtr closest);
 [PreserveSig] int GetMixFormat(out IntPtr format);
 [PreserveSig] int GetDevicePeriod(out long normal, out long minimum);
 [PreserveSig] int Start();
 [PreserveSig] int Stop();
 [PreserveSig] int Reset();
 [PreserveSig] int SetEventHandle(IntPtr handle);
 [PreserveSig] int GetService(ref Guid iid, [MarshalAs(UnmanagedType.IUnknown)] out object service);
}
[ComImport, Guid("C8ADBD64-E71E-48a0-A4DE-185C395CD317"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface ICapture {
 [PreserveSig] int GetBuffer(out IntPtr data, out uint frames, out uint flags, out ulong position, out ulong qpc);
 [PreserveSig] int ReleaseBuffer(uint frames);
 [PreserveSig] int GetNextPacketSize(out uint frames);
}
[StructLayout(LayoutKind.Sequential, Pack=2)]
public struct WaveFormat { public ushort tag, channels; public uint rate, bytes; public ushort align, bits, extra; }
[StructLayout(LayoutKind.Explicit, Size=24)]
struct Variant { [FieldOffset(0)] public ushort type; [FieldOffset(8)] public int size; [FieldOffset(16)] public IntPtr data; }
[ComVisible(true), ClassInterface(ClassInterfaceType.None)]
public class Completion : ICompletion, IAgileObject {
 public readonly ManualResetEvent Done = new ManualResetEvent(false);
 public IAudioClient Client; public int Result;
 public int ActivateCompleted(IOperation operation) {
  try { object obj; int hr; Result=operation.GetActivateResult(out hr,out obj); if(Result>=0) Result=hr; if(Result>=0) Client=(IAudioClient)obj; }
  catch(Exception e) { Result=Marshal.GetHRForException(e); }
  finally { Done.Set(); }
  return 0;
 }
}
class Packet { public readonly byte[] Data=new byte[1920]; public int Frames; public long Time; }
class ProcessAudio {
 [DllImport("Mmdevapi.dll", CharSet=CharSet.Unicode)] static extern int ActivateAudioInterfaceAsync(string path, ref Guid iid, ref Variant parameters, ICompletion callback, out IOperation operation);
 static volatile bool stopping;
 static void Check(int hr) { Marshal.ThrowExceptionForHR(hr); }
 [MTAThread] static int Main(string[] args) {
  IAudioClient client=null;
  try {
   uint pid=uint.Parse(args[0]);
   IntPtr parameters=Marshal.AllocHGlobal(12);
   Marshal.WriteInt32(parameters,0,1); Marshal.WriteInt32(parameters,4,(int)pid); Marshal.WriteInt32(parameters,8,0);
   Variant v=new Variant { type=65,size=12,data=parameters };
   var callback=new Completion(); IOperation operation; var iid=typeof(IAudioClient).GUID;
   Check(ActivateAudioInterfaceAsync("VAD\\Process_Loopback",ref iid,ref v,callback,out operation));
   if(!callback.Done.WaitOne(5000)) throw new TimeoutException("Process audio activation timed out");
   Check(callback.Result); client=callback.Client; Marshal.FreeHGlobal(parameters);
   var format=new WaveFormat { tag=1,channels=2,rate=48000,bytes=192000,align=4,bits=16 };
   Check(client.Initialize(0,0x80060000,0,0,ref format,IntPtr.Zero));
   var sampleReady=new AutoResetEvent(false); Check(client.SetEventHandle(sampleReady.SafeWaitHandle.DangerousGetHandle()));
   object service; iid=typeof(ICapture).GUID; Check(client.GetService(ref iid,out service)); var capture=(ICapture)service;
   var free=new ConcurrentQueue<Packet>(); for(int i=0;i<10;i++) free.Enqueue(new Packet());
   var ready=new BlockingCollection<Packet>(10);
   Exception writerFailure=null;
   var output=new BinaryWriter(Console.OpenStandardOutput());
   output.Write(0x4f505433); output.Flush();
   var writer=new Thread(() => { try { foreach(var p in ready.GetConsumingEnumerable()) { output.Write(p.Time); output.Write(p.Frames); output.Write(p.Data,0,p.Frames*4); output.Flush(); free.Enqueue(p); } } catch(Exception e) { writerFailure=e; stopping=true; } }); writer.IsBackground=true; writer.Start();
   new Thread(() => { Console.ReadLine(); stopping=true; }) { IsBackground=true }.Start();
   Check(client.Start()); long dropped=0;
   while(!stopping) {
    uint count; Check(capture.GetNextPacketSize(out count));
    while(count>0 && !stopping) {
     IntPtr data; uint frames,flags; ulong position,qpc;
     Check(capture.GetBuffer(out data,out frames,out flags,out position,out qpc));
     try {
      if((flags&4)!=0) throw new IOException("Audio timestamp unavailable");
      for(int offset=0;offset<(int)frames;offset+=480) {
       int n=Math.Min(480,(int)frames-offset); Packet p;
       if(!free.TryDequeue(out p)) { dropped+=n; continue; }
       p.Frames=n; p.Time=(long)qpc+offset*10000000L/48000;
       if((flags&2)!=0) Array.Clear(p.Data,0,n*4); else Marshal.Copy(IntPtr.Add(data,offset*4),p.Data,0,n*4);
       if(!ready.TryAdd(p)) { dropped+=n; free.Enqueue(p); }
      }
     } finally { Check(capture.ReleaseBuffer(frames)); }
     Check(capture.GetNextPacketSize(out count));
    }
    sampleReady.WaitOne(50);
   }
   Check(client.Stop()); ready.CompleteAdding(); if(!writer.Join(2000)) throw new IOException("Audio output stalled");
   if(writerFailure!=null) throw writerFailure;
   Console.Error.WriteLine("dropped_samples="+dropped); return 0;
  } catch(Exception e) { Console.Error.WriteLine(e.ToString()); return 1; }
  finally { if(client!=null) { try { client.Stop(); } catch {} } }
 }
}


