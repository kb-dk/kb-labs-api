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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.MinimalPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.swagger.util.Json;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

import java.io.Writer;

/**
 * Wrapper that handles streamed output of a list of JSON entries, either as a single valid JSON or as
 * JSON Lines (JSONL), which are 1 independent JSON/line.
 *
 * Use the method {@link #writeJSON} and remember to call {@link #close} when finished.
 */
public class JSONStreamWriter extends RuntimeWriter {
    private static Log log = LogFactory.getLog(JSONStreamWriter.class);
    private final ObjectWriter jsonWriter = Json.mapper().writer(new MinimalPrettyPrinter());

    public enum FORMAT { json, jsonl }


    private final FORMAT format;
    private boolean first = true;

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

    public void writeJSON(String jsonStr) {
        if (format == FORMAT.jsonl) {
            jsonStr = jsonStr.replace("\n", " ");
        }

        if (first && format == FORMAT.json) {
            write("{[\n");
            first = false;
        } else {
            write(format == FORMAT.json ? ",\n" : "\n");
        }
        write(jsonStr);
    }

    public void writeJSON(Object o) {
        try {
            writeJSON(jsonWriter.writeValueAsString(o));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JsonProcessingException attempting to write " + o, e);
        }
    }

    @Override
    public void close() {
        if (format == FORMAT.json) {
            write(first ? "]}" : "\n]}");
        }
        super.close();
    }
}
