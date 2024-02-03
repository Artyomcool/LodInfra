package wrapper32;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallFunctionMapper;
import com.sun.jna.win32.StdCallLibrary;

import java.util.HashMap;
import java.util.Map;

public interface Mss extends StdCallLibrary {
    static Mss load(String mssPath) {
        Map<String, Object> options = new HashMap<>();
        options.put(Library.OPTION_FUNCTION_MAPPER, new StdCallFunctionMapper());
        return Native.load(mssPath, Mss.class, options);
    }

    @Structure.FieldOrder({"formatTag","channels", "samplesPerSec", "avgBytesPerSec", "blockAlign"})
    class WaveFormat extends Structure {
        public short formatTag = 1;
        public short channels = 1;
        public int samplesPerSec = 22050;
        public int avgBytesPerSec = 22050;
        public short blockAlign = 1;

        public WaveFormat() {
            super(ALIGN_NONE);
        }
    }

    int AIL_startup();
    int AIL_set_preference(int pref, int val);
    int AIL_HWND();
    /*
    00000000 WAVEFORMAT      struc ; (sizeof=0xE, copyof_459)
00000000                                         ; XREF: .data:waveFormat_69FEE0/r
00000000 wFormatTag = 1     dw ?                    ; XREF: int soundManager::Open(int)+87/w
00000002 nChannels  = 1     dw ?                    ; XREF: int soundManager::Open(int)+94/w
00000004 nSamplesPerSec = 44100  dd ?                    ; XREF: int soundManager::Open(int)+9D/w
00000008 nAvgBytesPerSec = 44100 dd ?                    ; XREF: int soundManager::Open(int)+B1/w
0000000C nBlockAlign = 1    dw ?                    ; XREF: int soundManager::Open(int)+B7/w
                    waveFormat_69FEE0.wFormatTag = 1;
                    waveFormat_69FEE0.nChannels = dword_684B00;
                    waveFormat_69FEE0.nSamplesPerSec = v2;
                    waveFormat_69FEE0.nAvgBytesPerSec = v2 * dword_684B00 * (dword_684AFC / 8);
                    waveFormat_69FEE0.nBlockAlign = dword_684B00 * (dword_684AFC / 8);
     */
    int AIL_waveOutOpen(PointerByReference outDriver, int waveOut /*0*/, int driverId /*-1*/, WaveFormat format);
    void AIL_waveOutClose(Pointer driver);
    void AIL_digital_configuration(Pointer driver, int z1, int z2, Memory out);
    int AIL_allocate_sample_handle(Pointer driver);
    void AIL_init_sample(int sample);
    int AIL_set_sample_file(int sample, Pointer data, int z);
    void AIL_set_sample_loop_count(int sample, int loopCount);
    void AIL_set_sample_volume(int sample, int level);
    void AIL_start_sample(int sample);
    void AIL_stop_sample(int sample);
    void AIL_serve();
    void AIL_shutdown();


}