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

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper methods for handling parameter parsing and validation.
 */
public class ParamUtil {
    private static final Logger log = LoggerFactory.getLogger(ParamUtil.class);


    private static final Pattern YYYY = Pattern.compile("[0-9]{4,4}");
    private static final Pattern YYYY_MM = Pattern.compile("([0-9]{4,4})-([01]?[0-9])");
    private static final Pattern YYYY_MM_DD = Pattern.compile("([0-9]{4,4})-([01]?[0-9])-([0-3]?[0-9])");
    private static final DateTimeFormatter SOLR_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'00:00:00'Z'", Locale.ROOT);

    /**
     * @param time YYYY or YYYY-MM or blank.
     * @param defaultYear what to return if time is null or blank.
     * @param minYear the earliest allowed year, inclusive.
     * @param maxYear the latest allowed year, inclusive.
     * @param first if true, the timestamp to return is the first in a range (time is 00:00:00), else it is latest
     *              (time is 23:59:59).
     * @return a Solr timestamp.
     */
    public static String parseTimeYearMonth(String time, int defaultYear, int minYear, int maxYear, boolean first) {
        LocalDate date = first ?
                LocalDate.of(defaultYear, 1, 1) :
                LocalDate.of(defaultYear, 12, 31);
        if (time == null || time.isBlank()) {
            return date.format(SOLR_DATE);
        }
        if ("now".equals(time.toLowerCase(Locale.ROOT))) {
            date = date.withYear(Math.min(maxYear, LocalDate.now(SolrTimeline.DA).getYear()));
            return date.format(SOLR_DATE);
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
        LocalDate date = first ?
                LocalDate.of(defaultYear, 1, 1) :
                LocalDate.of(defaultYear, 12, 31);
        if (time == null || time.isBlank()) {
            return date.format(SOLR_DATE);
        }
        if ("now".equals(time.toLowerCase(Locale.ROOT))) {
            LocalDate now = LocalDate.now(SolrTimeline.DA);
            now = now.withYear(Math.min(maxYear, now.getYear()));
            return now.format(SOLR_DATE);
        }

        Matcher matcher;
        if ((matcher = YYYY.matcher(time)).matches()) {
            int year = Math.max(minYear, Math.min(maxYear, Integer.parseInt(matcher.group())));
            return date.withYear(year).format(SOLR_DATE);
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
            date = date.withYear(year)
                    .withDayOfMonth(1)
                    .withMonth(month);
            date = date.with(first ? TemporalAdjusters.firstDayOfMonth() : TemporalAdjusters.lastDayOfMonth());
            return date.format(SOLR_DATE);
        }

        if ((matcher = YYYY_MM_DD.matcher(time)).matches()) {
            int year = Integer.parseInt(matcher.group(1));
            year = Math.max(minYear, Math.min(maxYear, year));
            int month = Integer.parseInt(matcher.group(2).replaceAll("^0", ""));
            if (month == 0) {
                throw new IllegalArgumentException(
                        "The month in '" + time + "' was 0. Month indexes should start with 1");
            }
            if (month > 12) {
                throw new IllegalArgumentException(
                        "The month in '" + time + "' was " + month + " which is not valid under the GregorianCalendar");
            }
            int day = Integer.parseInt(matcher.group(3).replaceAll("^0", ""));
            try {
                date = LocalDate.of(year, month, day);
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("Invalid YYYY-MM-DD: '" + time + "'");
            }
            return date.format(SOLR_DATE);
        }

        throw new IllegalArgumentException("Unsupported datetime format '" + time + "'");
    }

}
