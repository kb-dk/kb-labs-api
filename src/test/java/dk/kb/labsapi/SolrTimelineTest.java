package dk.kb.labsapi;

import dk.kb.labsapi.config.ServiceConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.StreamingOutput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
class SolrTimelineTest {
    private static final Logger log = LoggerFactory.getLogger(SolrTimelineTest.class);

    @BeforeAll
    static void setupConfig() throws IOException {
        ServiceConfig.initialize("conf/labsapi.yaml");
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

    private void empty(StreamingOutput hest) throws IOException {
        OutputStream os = new ByteArrayOutputStream();
        hest.write(os);
        log.info("Got " + os.toString());
    }
}