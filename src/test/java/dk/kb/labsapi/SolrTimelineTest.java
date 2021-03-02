package dk.kb.labsapi;

import dk.kb.labsapi.config.ServiceConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
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
    @BeforeAll
    static void setupConfig() throws IOException {
        ServiceConfig.initialize("conf/labsapi.yaml");
    }

    @Test
    void testTimeline() {
        SolrTimeline.getInstance().timeline(
                "hest", SolrTimeline.GRANULARITY.decade, "1666", "1700",
                Arrays.asList(SolrTimeline.ELEMENT.values()), SolrTimeline.STRUCTURE.ALL, SolrTimeline.TIMELINE_FORMAT.csv);
    }
}