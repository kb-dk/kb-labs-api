package dk.kb.labsapi;

import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.util.zip.ZipOutputStream;

public class Utils {

    /**
     * Write content from streamingOutput to output stream.
     * @param streamingOutput to write to output stream.
     * @param zos is the ZipOutputStream which data gets streamed to.
     */
    public static void safeCsvStreamWrite(StreamingOutput streamingOutput, ZipOutputStream zos){
        try {
            streamingOutput.write(zos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
