package dk.kb.labsapi;

import dk.kb.labsapi.config.ServiceConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.StreamingOutput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

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

/*
  IMPORTANT: All this only works with a proper setup and contact to Solr
  TODO: Make this reliant on hostname so that it does not fail while for everyone except Toke
 */
class SolrTimelineTest {
    private static final Logger log = LoggerFactory.getLogger(SolrTimelineTest.class);

    @BeforeAll
    static void setupConfig() throws IOException {
        ServiceConfig.initialize("conf/labsapi*.yaml");
    }

    @Test
    void testTimeline() throws IOException {
        empty(SolrTimeline.getInstance().timeline(
                "hest", SolrTimeline.GRANULARITY.decade, "1666", "1700",
                Arrays.asList(SolrTimeline.ELEMENT.values()), SolrTimeline.STRUCTURE.ALL, SolrTimeline.TIMELINE_FORMAT.csv));
    }

    @Test
    void testTimelineLimited() throws IOException {
        empty(SolrTimeline.getInstance().timeline(
                "hest", SolrTimeline.GRANULARITY.decade, "1666", "1700",
                Collections.singletonList(SolrTimeline.ELEMENT.words), SolrTimeline.STRUCTURE.ALL, SolrTimeline.TIMELINE_FORMAT.json));
    }

    @Test
    void testTimelineMonthShort() throws IOException {
        empty(SolrTimeline.getInstance().timeline(
                "hest", SolrTimeline.GRANULARITY.decade, "1666-1", "1700-1",
                Collections.singletonList(SolrTimeline.ELEMENT.words), SolrTimeline.STRUCTURE.ALL, SolrTimeline.TIMELINE_FORMAT.json));
    }

    @Test
    void testTimelineMonthEarly() throws IOException {
        empty(SolrTimeline.getInstance().timeline(
                "hest", SolrTimeline.GRANULARITY.decade, "1666-01", "1700-01",
                Collections.singletonList(SolrTimeline.ELEMENT.words), SolrTimeline.STRUCTURE.ALL, SolrTimeline.TIMELINE_FORMAT.json));
    }

    @Test
    void testTimelineMonthLate() throws IOException {
        empty(SolrTimeline.getInstance().timeline(
                "hest", SolrTimeline.GRANULARITY.decade, "1666-10", "1700-10",
                Collections.singletonList(SolrTimeline.ELEMENT.words), SolrTimeline.STRUCTURE.ALL, SolrTimeline.TIMELINE_FORMAT.json));
    }

    @Test
    void testTimelineGranularityMonth() throws IOException {
        String response = toString(SolrTimeline.getInstance().timeline(
                "hest", SolrTimeline.GRANULARITY.month, "1800-10", "1801-12",
                Collections.singletonList(
                        SolrTimeline.ELEMENT.words), SolrTimeline.STRUCTURE.ALL, SolrTimeline.TIMELINE_FORMAT.json));

        String firstTimestamp = getFirstTimestamp(response);
        if (firstTimestamp.length() != 7) {
            fail("The first timestamp should be of length 7 (YYYY-MM) but was '" + firstTimestamp + "'");
        }
    }

    @Test
    void testTimelineGranularityYear() throws IOException {
        String response = toString(SolrTimeline.getInstance().timeline(
                "hest", SolrTimeline.GRANULARITY.year, "1800-10", "1801-12",
                Collections.singletonList(
                        SolrTimeline.ELEMENT.words), SolrTimeline.STRUCTURE.ALL, SolrTimeline.TIMELINE_FORMAT.json));

        String firstTimestamp = getFirstTimestamp(response);
        if (firstTimestamp.length() != 4) {
            fail("The first timestamp should be of length 4 (YYYY) but was '" + firstTimestamp + "'");
        }
    }

    private String getFirstTimestamp(String response) throws IOException {
        JSONArray entries = new JSONArray(response).getJSONObject(0).getJSONArray("entries");
        if (entries.isEmpty()) {
            fail("There should be at least 1 entry in the timeline");
        }
        String firstTimestamp = entries.getJSONObject(0).getString("timestamp");
        return firstTimestamp;
    }

    @Test
    void testTimelineStar() throws IOException {
        empty(SolrTimeline.getInstance().timeline(
                "*:*", SolrTimeline.GRANULARITY.year, "1666", "1670",
                Collections.singletonList(SolrTimeline.ELEMENT.pages), SolrTimeline.STRUCTURE.ALL, SolrTimeline.TIMELINE_FORMAT.json));
    }

    @Test
    void testTimelineNoHits() throws IOException {
        empty(SolrTimeline.getInstance().timeline(
                "sdgfsgss", SolrTimeline.GRANULARITY.decade, "1666", "1700",
                Arrays.asList(SolrTimeline.ELEMENT.values()), SolrTimeline.STRUCTURE.ALL, SolrTimeline.TIMELINE_FORMAT.json));
    }

    private void empty(StreamingOutput content) throws IOException {
        log.info("Got " + toString(content));
    }

    private String toString(StreamingOutput content) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        content.write(os);
        return os.toString(StandardCharsets.UTF_8);
    }
}
