package com.marlowefinch.ops;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * A closed date range for the query endpoints.
 *
 * Both bounds default to "the last 30 days ending today" ({@code today - 30 .. today}).
 * {@link #resolve} validates the request parameters (TODO-232): {@code from}/{@code to}, when
 * present, must be ISO dates ({@code YYYY-MM-DD}); after defaults are applied, {@code from}
 * must be on or before {@code to}, and {@code ChronoUnit.DAYS.between(from, to)} must be at
 * most {@value QueryParams#MAX_RANGE_DAYS} (i.e. up to 367 calendar days counted inclusively,
 * matching how the 30-day default is counted). Violations throw
 * {@link InvalidParametersException}. The canonical constructor itself does not validate.
 */
public record DateRange(LocalDate from, LocalDate to) {

    public static final int DEFAULT_DAYS = 30;

    public static DateRange resolve(String from, String to, Clock clock) {
        List<String> errors = QueryParams.newErrors();
        DateRange range = QueryParams.dateRange(from, to, clock, errors);
        QueryParams.throwIfAny(errors);
        return range;
    }
}
