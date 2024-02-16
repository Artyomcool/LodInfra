package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.Resource;
import javafx.scene.layout.VBox;

import java.io.*;
import java.nio.file.Path;
import java.util.Properties;

public class SoundCompareView extends VBox {

    private final SndView localView = new SndView();
    private final SndView remoteView = new SndView();
    private final Properties cfg;
    private Process process;
    private DataInputStream in;
    private DataOutputStream out;
    private String mss32;
    private int sampleHandle;
    private int memoryWithData = -1;

    public SoundCompareView(Properties cfg) {
        this.cfg = cfg;
    }

    public void stop() {
        if (process != null) {
            process.destroy();
        }
    }

    public boolean applySound(Path local, Path remote) {
        Path lod = Resource.pathOfLod(local);
        if (lod != null) {
            if (!lod.getFileName().toString().toLowerCase().endsWith(".snd")) {
                return false;
            }
        } else if (!local.getFileName().toString().toLowerCase().endsWith(".wav")) {
            return false;
        }

        ImgFilesUtils.processFile(local, null, buffer -> {
            try {
                if (process == null) {
                    initProcess();
                }

                int ans;

                out.writeInt(0);
                out.writeUTF(mss32);
                out.writeUTF("_AIL_stop_sample@4");
                out.writeInt(1);
                out.writeInt(3);
                out.writeInt(sampleHandle);
                out.writeInt(0);
                out.flush();

                ans = in.readByte();

                // PLAY SOUND

                if (memoryWithData != -1) {
                    out.writeInt(4);
                    out.writeInt(memoryWithData);
                }

                out.writeInt(1);
                out.writeInt(buffer.remaining());
                out.flush();

                memoryWithData = in.readInt();

                out.writeInt(2);
                out.writeInt(memoryWithData);
                out.writeInt(0);
                out.writeInt(buffer.remaining());
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                out.write(data);

                out.writeInt(0);
                out.writeUTF(mss32);
                out.writeUTF("_AIL_set_sample_file@12");
                out.writeInt(3);
                out.writeInt(3);
                out.writeInt(sampleHandle);
                out.writeInt(-memoryWithData);
                out.writeInt(3);
                out.writeInt(0);
                out.writeInt(3);
                out.flush();

                ans = in.readInt();

                out.writeInt(0);
                out.writeUTF(mss32);
                out.writeUTF("_AIL_set_sample_loop_count@8");
                out.writeInt(2);
                out.writeInt(3);
                out.writeInt(sampleHandle);
                out.writeInt(3);
                out.writeInt(1);
                out.writeInt(0);
                out.flush();

                ans = in.readByte();

                out.writeInt(0);
                out.writeUTF(mss32);
                out.writeUTF("_AIL_set_sample_volume@8");
                out.writeInt(2);
                out.writeInt(3);
                out.writeInt(sampleHandle);
                out.writeInt(3);
                out.writeInt(100);
                out.writeInt(0);
                out.flush();

                ans = in.readByte();

                out.writeInt(0);
                out.writeUTF(mss32);
                out.writeUTF("_AIL_start_sample@4");
                out.writeInt(1);
                out.writeInt(3);
                out.writeInt(sampleHandle);
                out.writeInt(0);
                out.flush();

                ans = in.readByte();

                out.writeInt(0);
                out.writeUTF(mss32);
                out.writeUTF("_AIL_serve@0");
                out.writeInt(0);
                out.writeInt(0);
                out.flush();

                ans = in.readByte();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        return false;
    }

    private void initProcess() throws IOException {
        process = new ProcessBuilder(cfg.getProperty("tools.wrapper32.path"))
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();

        in = new DataInputStream(new BufferedInputStream(process.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(process.getOutputStream()));

        out.writeInt(5);    // show window

        int ans = 0;

        mss32 = Path.of(cfg.getProperty("gameDir"), "MSS32.dll").toString();
        out.writeInt(0);
        out.writeUTF(mss32);
        out.writeUTF("_AIL_startup@0");
        out.writeInt(0);
        out.writeInt(3);
        out.flush();

        ans = in.readInt();

        out.writeInt(0);
        out.writeUTF(mss32);
        out.writeUTF("_AIL_set_preference@8");
        out.writeInt(2);
        out.writeInt(3);
        out.writeInt(15);
        out.writeInt(3);
        out.writeInt(0);
        out.writeInt(3);
        out.flush();

        ans = in.readInt();

        out.writeInt(0);
        out.writeUTF(mss32);
        out.writeUTF("_AIL_set_preference@8");
        out.writeInt(2);
        out.writeInt(3);
        out.writeInt(33);
        out.writeInt(3);
        out.writeInt(1);
        out.writeInt(3);
        out.flush();

        ans = in.readInt();

        out.writeInt(0);
        out.writeUTF(mss32);
        out.writeUTF("_AIL_set_preference@8");
        out.writeInt(2);
        out.writeInt(3);
        out.writeInt(34);
        out.writeInt(3);
        out.writeInt(100);
        out.writeInt(3);
        out.flush();

        ans = in.readInt();

        out.writeInt(0);
        out.writeUTF(mss32);
        out.writeUTF("_AIL_HWND@0");
        out.writeInt(0);
        out.writeInt(3);
        out.flush();

        ans = in.readInt();

        out.writeInt(5);    // hide window

        // PRE-INIT DONE

        out.writeInt(1);
        out.writeInt(4096);
        out.flush();

        int memoryId = in.readInt();

        out.writeInt(0);
        out.writeUTF(mss32);
        out.writeUTF("_AIL_waveOutOpen@16");
        out.writeInt(4);
        out.writeInt(-memoryId);
        out.writeInt(3);
        out.writeInt(0);
        out.writeInt(3);
        out.writeInt(-1);
        out.writeInt(3);
        out.writeInt(0);
        out.writeInt(3);
        out.flush();

        ans = in.readInt();

        out.writeInt(3);
        out.writeInt(memoryId);
        out.writeInt(0);
        out.writeInt(4);
        out.flush();

        int driver = Integer.reverseBytes(in.readInt());

        out.writeInt(0);
        out.writeUTF(mss32);
        out.writeUTF("_AIL_digital_configuration@16");
        out.writeInt(4);
        out.writeInt(3);
        out.writeInt(driver);
        out.writeInt(3);
        out.writeInt(0);
        out.writeInt(3);
        out.writeInt(0);
        out.writeInt(-memoryId);
        out.writeInt(0);
        out.flush();

        ans = in.readByte();

        out.writeInt(0);
        out.writeUTF(mss32);
        out.writeUTF("_AIL_allocate_sample_handle@4");
        out.writeInt(1);
        out.writeInt(3);
        out.writeInt(driver);
        out.writeInt(3);
        out.flush();

        sampleHandle = in.readInt();

        out.writeInt(0);
        out.writeUTF(mss32);
        out.writeUTF("_AIL_init_sample@4");
        out.writeInt(1);
        out.writeInt(3);
        out.writeInt(sampleHandle);
        out.writeInt(0);
        out.flush();

        ans = in.readByte();
    }

}
