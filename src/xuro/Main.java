package xuro;

import javax.swing.SwingUtilities;
import java.nio.file.*;

public final class Main {
    public static final String VERSION = "1.0.0";

    public static void main(String[] args) throws Exception {
        Path dir = Config.defaultDir();
        for (int i = 0; i + 1 < args.length; i++) if (args[i].equals("--dir")) dir = Paths.get(args[i + 1]);
        Files.createDirectories(dir);
        Config cfg = Config.load(dir);
        SwingUtilities.invokeLater(() -> new Ui(cfg).show());
    }
}
