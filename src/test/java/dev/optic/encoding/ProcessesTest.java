package dev.optic.encoding;

import dev.optic.recording.Cancellation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ProcessesTest {
    @TempDir Path directory;
    private List<String> command(String mode,Path pid) throws Exception {
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        String classes=Path.of(ProcessFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        return List.of(java,"-cp",classes,ProcessFixture.class.getName(),mode,pid.toString());
    }
    @Test void nonzeroExitIsNotSuccess() throws Exception { assertFalse(Processes.run(command("fail",directory.resolve("unused")),null,3)); }
    @Test void cancellingBlockedProcessReturnsAndKillsIt() throws Exception {
        var cancellation=new Cancellation(); Path pid=directory.resolve("pid"); var command=command("sleep",pid);
        try(var executor=Executors.newSingleThreadExecutor()) {
            var result=executor.submit(() -> Processes.run(command,null,30,cancellation));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            String processText="";
            while(System.nanoTime()<deadline) {
                if(Files.exists(pid)) processText=Files.readString(pid).trim();
                if(!processText.isEmpty()) break;
                Thread.sleep(10);
            }
            assertFalse(processText.isEmpty()); long processId=Long.parseLong(processText);
            cancellation.cancel();
            assertInstanceOf(CancellationException.class,assertThrows(ExecutionException.class,() -> result.get(2,TimeUnit.SECONDS)).getCause());
            var handle=ProcessHandle.of(processId); if(handle.isPresent()) handle.get().onExit().get(2,TimeUnit.SECONDS);
            assertFalse(ProcessHandle.of(processId).map(ProcessHandle::isAlive).orElse(false));
        }
    }
}
