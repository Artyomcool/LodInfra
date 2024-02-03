package wrapper32;

import javax.swing.*;

public class ConsoleApp {
    public static void main(String[] args) throws Exception {
        JFrame jFrame = new JFrame();
        jFrame.setVisible(true);

        MssWrapper wrapper = new MssWrapper();
        wrapper.init(args[0]);

        jFrame.setVisible(false);

        MemoryChannel memory = new MemoryChannel(args[1]);

        external: while (true) {
            byte[] read = memory.read();
            switch (read[0]) {
                case 0 -> {break external;}
                case 1 -> wrapper.play(read, 2, read.length - 2, read[1]);
                case 2 -> wrapper.stop();
                default -> throw new IllegalStateException("Unexpected value: " + read[0]);
            }
        }

        wrapper.close();
        System.exit(0);
    }
}
