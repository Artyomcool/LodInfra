package wrapper32;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;

import java.io.InterruptedIOException;
import java.lang.invoke.VarHandle;

public class MemoryChannel {
    private final WinNT.HANDLE fileMapping;
    private final Pointer sharedMemory;

    public MemoryChannel(String name) {
        fileMapping = Kernel32.INSTANCE.OpenFileMapping(Kernel32.FILE_ALL_ACCESS, false, name);
        sharedMemory = Kernel32.INSTANCE.MapViewOfFile(fileMapping, Kernel32.FILE_ALL_ACCESS, 0, 0, 10 * 1024 * 1024);
    }

    public byte[] read() throws InterruptedIOException {
        while (sharedMemory.getInt(0) == 0) {
            try {
                //noinspection BusyWait
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new InterruptedIOException();
            }
        }
        VarHandle.fullFence();
        int size = sharedMemory.getInt(0);
        byte[] result = new byte[size];
        sharedMemory.read(4, result, 0, size);
        VarHandle.fullFence();
        sharedMemory.setInt(0, 0);
        VarHandle.fullFence();
        return result;
    }

    public void write(byte[] data) throws InterruptedIOException {
        while (sharedMemory.getInt(0) != 0) {
            try {
                //noinspection BusyWait
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new InterruptedIOException();
            }
        }
        sharedMemory.write(4, data, 0, data.length);
        VarHandle.fullFence();
        sharedMemory.setInt(0, data.length);
        VarHandle.fullFence();
    }

}
