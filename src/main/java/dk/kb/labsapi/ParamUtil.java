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
package dk.kb.labsapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper methods for handling parameter parsing and validation.
 */
public class ParamUtil {
    private static final Logger log = LoggerFactory.getLogger(ParamUtil.class);


    private static final Pattern YYYY = Pattern.compile("[0-9]{4,4}");
    private static final Pattern YYYY_MM = Pattern.compile("([0-9]{4,4})-([01][0-9])");
    private static final Pattern YYYY_MM_DD = Pattern.compile("([0-9]{4,4})-([01][0-9])-([0-3][0-9])");

    /**
     * @param time YYYY or YYYY-MM or blank.
     * @param defaultYear what to return if time is null or blank.
     * @param minYear the earliest allowed year, inclusive.
     * @param maxYear the lates allowed year, inclusive.
     * @param first if true, the timestamp to return is the first in a range (time is 00:00:00), else it is latest
     *              (time is 23:59:59).
     * @return a Solr timestamp.
     */
    public static String parseTimeYearMonth(String time, int defaultYear, int minYear, int maxYear, boolean first) {
        if (time == null || time.isBlank()) {
            // TODO: 12-31T23... when end
            return defaultYear + (first ? "-01-01T00:00:00Z" : "-12-31T23:59:59Z");
        }
        if ("now".equals(time.toLowerCase(Locale.ROOT))) {
            LocalDate now = LocalDate.now(SolrTimeline.DA);
            return now.format(DateTimeFormatter.ofPattern("yyyy-MM-01T00:00:00Z", Locale.ROOT));
        }
        if (YYYY_MM_DD.matcher(time).matches()) {
            throw new IllegalArgumentException(
                    "YYYY-MM-DD pattern detected for '" + time + "' but only YYYY or YYYY-MM is allowed");
        }
        return parseTimeYearMonthDay(time, defaultYear, minYear, maxYear, first);
    }

    /**
     * @param time YYYY or YYYY-MM, YYYY-MM-DD or blank.
     * @param defaultYear what to return if time is null or blank.
     * @param minYear the earliest allowed year, inclusive.
     * @param maxYear the lates allowed year, inclusive.
     * @param first if true, the timestamp to return is the first in a range (time is 00:00:00), else it is latest
     *              (time is 23:59:59).
     * @return a Solr timestamp.
     */
    public static String parseTimeYearMonthDay(String time, int defaultYear, int minYear, int maxYear, boolean first) {
        if (time == null || time.isBlank()) {
            // TODO: 12-31T23... when end
            return defaultYear + (first ? "-01-01T00:00:00Z" : "-12-31T23:59:59Z");
        }
        if ("now".equals(time.toLowerCase(Locale.ROOT))) {
            LocalDate now = LocalDate.now(SolrTimeline.DA);
            return now.format(DateTimeFormatter.ofPattern("yyyy-MM-ddT00:00:00Z", Locale.ROOT));
        }

        LocalDate date;

        Matcher matcher;
        if ((matcher = YYYY.matcher(time)).matches()) {
            int year = Math.max(minYear, Math.min(maxYear, Integer.parseInt(matcher.group())));
            date = LocalDate.of(year, 1, 1);
            if (!first) {
                date = date.withMonth(12);
                date = date.withDayOfMonth(date.lengthOfMonth());
            }
            
            return Math.max(minYear, Math.min(maxYear, year)) + (first ? "-01-01T00:00:00Z" : "-12-31T23:59:59Z");
        }

        if ((matcher = YYYY_MM.matcher(time)).matches()) {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2).replaceAll("^0", ""));
            if (month == 0) {
                throw new IllegalArgumentException(
                        "The month in '" + time + "' was 0. Month indexes should start with 1");
            }
            if (month > 12) {
                throw new IllegalArgumentException(
                        "The month in '" + time + "' was " + month + " which is not valid under the GregorianCalendar");
            }
            year = Math.max(minYear, Math.min(maxYear, year));
            // TODO: Does not seem legal
            return String.format(Locale.ROOT, "%4d-%2d" + (first ? "-01T00:00:00Z" : "-31T23:59:59Z"), year, month);
        }

        if ((matcher = YYYY_MM_DD.matcher(time)).matches()) {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2).replaceAll("^0", ""));
            if (month == 0) {
                throw new IllegalArgumentException(
                        "The month in '" + time + "' was 0. Month indexes should start with 1");
            }
            if (month > 12) {
                throw new IllegalArgumentException(
                        "The month in '" + time + "' was " + month + " which is not valid under the GregorianCalendar");
            }
            year = Math.max(minYear, Math.min(maxYear, year));
            // TODO: Does not seem legal
            return String.format(Locale.ROOT, "%4d-%2d" + (first ? "-01T00:00:00Z" : "-31T23:59:59Z"), year, month);
        }

        throw new IllegalArgumentException("Unsupported datetime format '" + time + "'");
    }
}
