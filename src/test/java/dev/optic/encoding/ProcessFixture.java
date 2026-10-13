package dev.optic.encoding;
public final class ProcessFixture {
    public static void main(String[] args) throws Exception {
        if(args[0].equals("sleep")) {
            var pid=java.nio.file.Path.of(args[1]); var temp=pid.resolveSibling(pid.getFileName()+".tmp");
            java.nio.file.Files.writeString(temp,Long.toString(ProcessHandle.current().pid()));
            java.nio.file.Files.move(temp,pid,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            Thread.sleep(60_000);
        }
        else if(args[0].equals("fail")) System.exit(7);
    }
}
