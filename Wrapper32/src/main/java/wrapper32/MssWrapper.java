package wrapper32;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

public class MssWrapper implements AutoCloseable {

    private Mss mss;
    private int sampleHandle;
    private Pointer driver;
    private Memory previousMemory;

    public void init(String dllPath) {
        mss = Mss.load(dllPath);
        mss.AIL_startup();
        mss.AIL_set_preference(15, 0);
        mss.AIL_set_preference(33, 1);
        mss.AIL_set_preference(34, 100);
        mss.AIL_HWND();

        PointerByReference p = new PointerByReference();
        mss.AIL_waveOutOpen(p, 0, -1, null);
        driver = p.getValue();
        mss.AIL_digital_configuration(driver, 0, 0, new Memory(4096));
        sampleHandle = mss.AIL_allocate_sample_handle(driver);
        mss.AIL_init_sample(sampleHandle);
    }

    public void play(byte[] data, int offset, int length, int loops) {
        Memory memory = new Memory(length);
        memory.write(0, data, offset, length);
        mss.AIL_set_sample_file(sampleHandle, memory, 0);
        mss.AIL_set_sample_loop_count(sampleHandle, loops);
        mss.AIL_set_sample_volume(sampleHandle, 100);
        mss.AIL_start_sample(sampleHandle);
        mss.AIL_serve();

        if (previousMemory != null) {
            previousMemory.close();
        }
        previousMemory = memory;
    }

    public void stop() {
        mss.AIL_stop_sample(sampleHandle);
    }

    @Override
    public void close() throws Exception {
        mss.AIL_waveOutClose(driver);
        mss.AIL_shutdown();
    }
}
