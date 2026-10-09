package com.marlowefinch.ops;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Hand-written checks for the query parameters shared by the {@code /api} endpoints (TODO-232).
 *
 * Every check appends a message to an error list instead of failing fast, so one request
 * with several problems gets all of them back at once. {@link #throwIfAny(List)} turns a
 * non-empty list into an {@link InvalidParametersException}, which {@link ApiExceptionHandler}
 * renders as {@code 400 {"errors": [...]}}.
 */
final class QueryParams {

    /**
     * Longest allowed span, measured as {@code ChronoUnit.DAYS.between(from, to)} - the same
     * way {@link DateRange#DEFAULT_DAYS} is counted. So {@code 2025-01-01..2026-01-02} (366) is
     * accepted and {@code 2025-01-01..2026-01-03} (367) is rejected.
     */
    static final int MAX_RANGE_DAYS = 366;
    static final int MIN_LIMIT = 1;
    static final int MAX_LIMIT = 500;

    static final String FROM_NOT_ISO = "from must be an ISO date (YYYY-MM-DD)";
    static final String TO_NOT_ISO = "to must be an ISO date (YYYY-MM-DD)";
    static final String FROM_AFTER_TO = "from must be on or before to";
    static final String RANGE_TOO_LONG = "date range must span at most " + MAX_RANGE_DAYS + " days";
    static final String LIMIT_INVALID = "limit must be an integer between " + MIN_LIMIT + " and " + MAX_LIMIT;

    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");

    private QueryParams() {
    }

    static List<String> newErrors() {
        return new ArrayList<>();
    }

    static void throwIfAny(List<String> errors) {
        if (!errors.isEmpty()) {
            throw new InvalidParametersException(errors);
        }
    }

    /**
     * Resolves {@code from}/{@code to} with the defaults from {@link DateRange}, then checks the
     * order and span of the resolved range. Returns {@code null} when anything was wrong; the
     * problems are in {@code errors}.
     */
    static DateRange dateRange(String from, String to, Clock clock, List<String> errors) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = isMissing(to) ? today : parseDate(to, TO_NOT_ISO, errors);
        LocalDate start = isMissing(from) ? today.minusDays(DateRange.DEFAULT_DAYS) : parseDate(from, FROM_NOT_ISO, errors);
        if (start == null || end == null) {
            return null;
        }
        if (start.isAfter(end)) {
            errors.add(FROM_AFTER_TO);
            return null;
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            errors.add(RANGE_TOO_LONG);
            return null;
        }
        return new DateRange(start, end);
    }

    /** Parses {@code limit}, falling back to {@code defaultLimit} when it is missing or blank. */
    static int limit(String limit, int defaultLimit, List<String> errors) {
        if (isMissing(limit)) {
            return defaultLimit;
        }
        if (INTEGER.matcher(limit).matches()) {
            try {
                int value = Integer.parseInt(limit);
                if (value >= MIN_LIMIT && value <= MAX_LIMIT) {
                    return value;
                }
            } catch (NumberFormatException overflow) {
                // falls through to the error below
            }
        }
        errors.add(LIMIT_INVALID);
        return defaultLimit;
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank();
    }

    private static LocalDate parseDate(String value, String message, List<String> errors) {
        if (ISO_DATE.matcher(value).matches()) {
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException invalidDate) {
                // e.g. 2026-02-30; falls through to the error below
            }
        }
        errors.add(message);
        return null;
    }
}
