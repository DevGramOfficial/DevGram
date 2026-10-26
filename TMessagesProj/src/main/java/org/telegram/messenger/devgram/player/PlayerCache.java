package org.telegram.messenger.devgram.player;

import java.io.File;
import org.telegram.messenger.FileLoader;
public abstract class PlayerCache {
    public static File directory(String str) {
        File fileCheckDirectory = FileLoader.checkDirectory(4);
        if (fileCheckDirectory == null) {
            return null;
        }
        File file = new File(fileCheckDirectory, str);
        if (file.exists() || file.mkdirs()) {
            return file;
        }
        return null;
    }
}
