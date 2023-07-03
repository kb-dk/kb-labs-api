package dk.kb.labsapi;

import com.google.common.collect.Iterators;

import javax.ws.rs.core.StreamingOutput;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipOutputStream;

public class Utils {

    /**
     * Write content from streamingOutput to output stream.
     * @param streamingOutput to write to output stream.
     * @param zos is the ZipOutputStream which data gets streamed to.
     */
    public static void safeStreamWrite(StreamingOutput streamingOutput, ZipOutputStream zos){
        try {
            streamingOutput.write(zos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a non-closing OutputStream, which is used to combine different parts of entries to one entity.
     * @param os is the OutputStream which is not to be closed after use.
     */
    public static OutputStream getNonCloser(OutputStream os) {
        OutputStream nonCloser = new FilterOutputStream(os) {
            @Override
            public void close() throws IOException {
                // Don't care
            }
        };
        return nonCloser;
    }

    /**
     * Lazily partition the input to the given partitionSize.
     * <p>
     * All partitions will have exactly partitionSize elements, except for the last partition which will contain
     * {@code input_size % partitionSize} elements.
     * <p>
     * The implementation is fully streaming and only holds the current partition in memory.
     * <p>
     * The implementation does not support parallelism: If source is parallel, it will be sequentialized.
     * <p>
     * If the end result should be a list of lists, use {@code splitToList(myStream, 87).collect(Collectors.toList())}.
     * @param source any stream.
     * @param partitionSize the maximum size for the partitions.
     * @return the input partitioned into lists, each with partitionSize elements.
     */
    public static <T> Stream<List<T>> splitToLists(Stream<T> source, int partitionSize) {
        return splitToStreams(source, partitionSize).map(stream -> stream.collect(Collectors.toList()));
    }

    /**
     * Lazily partition the input to the given partitionSize.
     * <p>
     * All partitions will have exactly partitionSize elements, except for the last partition which will contain
     * {@code input_size % partitionSize} elements.
     * <p>
     * The implementation is fully streaming and only holds the current partition in memory.
     * <p>
     * The implementation does not support parallelism: If source is parallel, it will be sequentialized.
     * @param source any stream.
     * @param partitionSize the maximum size for the partitions.
     * @return the input partitioned into streams, each with partitionSize elements.
     */
    public static <T> Stream<Stream<T>> splitToStreams(Stream<T> source, int partitionSize) {
        // https://stackoverflow.com/questions/32434592/partition-a-java-8-stream
        final Iterator<T> it = source.iterator();
        final Iterator<Stream<T>> partIt = Iterators.transform(Iterators.partition(it, partitionSize), List::stream);
        final Iterable<Stream<T>> iterable = () -> partIt;

        return StreamSupport.stream(iterable.spliterator(), false);
    }
}
