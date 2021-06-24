/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package dk.kb;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.MinimalPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.swagger.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Writer;

/**
 * Wrapper that handles streamed output of a list of JSON entries, either as a single valid JSON or as
 * JSON Lines (JSONL, which is 1 independent JSON/line).
 *
 * Use the method {@link #writeJSON} and remember to call {@link #close} when finished.
 */
public class JSONStreamWriter extends RuntimeWriter {
    private static final Logger log = LoggerFactory.getLogger(JSONStreamWriter.class);
    private final ObjectWriter jsonWriter;
    {
        ObjectMapper mapper = Json.mapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        jsonWriter = mapper.writer(new MinimalPrettyPrinter());
    }

    public enum FORMAT { json, jsonl }

    private final FORMAT format;
    private boolean first = true;

    /**
     * Wrap the given inner Writer in the JSONStreamWriter. Calls to {@link #writeJSON} writes directly to inner,
     * so the JSONStreamWriter holds no cached data. The inner {@link Writer#flush()} is not called.
     * @param inner  the Writer to send te result to.
     * @param format Valid JSON or JSON Lines.
     */
    public JSONStreamWriter(Writer inner, FORMAT format) {
        super(inner);
        this.format = format;
        if (inner == null) {
            throw new IllegalArgumentException("inner Writer was null, but must be defined");
        }
        if (format == null) {
            throw new IllegalArgumentException("format was null, but must be defined");
        }
    }

    /**
     * Write a JSON expression that has already been serialized to String.
     * It is the responsibility of the caller to ensure that jsonStr is valid standalone JSON.
     * If {@link #format} is {@link FORMAT#jsonl}, newlines in jsonStr will be replaced by spaces.
     * @param jsonStr a valid JSON.
     */
    public void writeJSON(String jsonStr) {
        if (format == FORMAT.jsonl) {
            jsonStr = jsonStr.replace("\n", " ");
        }

        if (first && format == FORMAT.json) {
            write("[\n");
            first = false;
        } else {
            write(format == FORMAT.json ? ",\n" : "\n");
        }
        write(jsonStr);
    }

    /**
     * Use {@link #jsonWriter} to serialize the given object to String JSON and write the result, ensuring the
     * invariants of {@link #format} holds.
     * @param object
     */
    public void writeJSON(Object object) {
        try {
            writeJSON(jsonWriter.writeValueAsString(object));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JsonProcessingException attempting to write " + object, e);
        }
    }

    /**
     * Finishes the JSON stream by writing closing statements.
     */
    @Override
    public void close() {
        if (format == FORMAT.json) {
            write(first ? "]" : "\n]");
        }
        super.close();
    }
}
