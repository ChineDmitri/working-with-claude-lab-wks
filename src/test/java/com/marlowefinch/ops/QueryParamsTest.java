package com.marlowefinch.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The hand-written query parameter checks (TODO-232), without Spring. */
class QueryParamsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void missingDatesDefaultToTheLast30DaysEndingToday() {
        List<String> errors = QueryParams.newErrors();
        DateRange range = QueryParams.dateRange(null, null, CLOCK, errors);
        assertThat(errors).isEmpty();
        assertThat(range).isEqualTo(new DateRange(LocalDate.parse("2026-08-22"), LocalDate.parse("2026-09-21")));
    }

    @Test
    void blankDatesAreTreatedAsMissing() {
        List<String> errors = QueryParams.newErrors();
        DateRange range = QueryParams.dateRange("", " ", CLOCK, errors);
        assertThat(errors).isEmpty();
        assertThat(range.to()).isEqualTo(LocalDate.parse("2026-09-21"));
    }

    @Test
    void nonIsoDatesAreRejected() {
        for (String bad : new String[] {"next-tuesday", "2026-9-1", "20260901", "2026-02-30", "2026-09-01T00:00", "+2026-09-01"}) {
            List<String> errors = QueryParams.newErrors();
            assertThat(QueryParams.dateRange(bad, null, CLOCK, errors)).as(bad).isNull();
            assertThat(errors).as(bad).containsExactly(QueryParams.FROM_NOT_ISO);
        }
    }

    @Test
    void bothBadDatesAreReportedAndOrderIsNotCheckedThen() {
        List<String> errors = QueryParams.newErrors();
        assertThat(QueryParams.dateRange("x", "y", CLOCK, errors)).isNull();
        assertThat(errors).containsExactlyInAnyOrder(QueryParams.FROM_NOT_ISO, QueryParams.TO_NOT_ISO);
    }

    @Test
    void rangeSpanIsMeasuredWithDaysBetween() {
        List<String> ok = QueryParams.newErrors();
        assertThat(QueryParams.dateRange("2025-01-01", "2026-01-02", CLOCK, ok)).isNotNull();
        assertThat(ok).isEmpty();

        List<String> tooLong = QueryParams.newErrors();
        assertThat(QueryParams.dateRange("2025-01-01", "2026-01-03", CLOCK, tooLong)).isNull();
        assertThat(tooLong).containsExactly("date range must span at most 366 days");
    }

    @Test
    void fromAfterToIsRejected() {
        List<String> errors = QueryParams.newErrors();
        assertThat(QueryParams.dateRange("2026-09-02", "2026-09-01", CLOCK, errors)).isNull();
        assertThat(errors).containsExactly("from must be on or before to");
    }

    @Test
    void limitDefaultsWhenMissingAndAcceptsTheBounds() {
        List<String> errors = QueryParams.newErrors();
        assertThat(QueryParams.limit(null, 20, errors)).isEqualTo(20);
        assertThat(QueryParams.limit("", 20, errors)).isEqualTo(20);
        assertThat(QueryParams.limit("1", 20, errors)).isEqualTo(1);
        assertThat(QueryParams.limit("500", 20, errors)).isEqualTo(500);
        assertThat(errors).isEmpty();
    }

    @Test
    void limitOutOfRangeOrNotAnIntegerIsRejected() {
        for (String bad : new String[] {"0", "501", "-1", "2147483648", "abc", "1.5", "1e2", "0x10"}) {
            List<String> errors = QueryParams.newErrors();
            QueryParams.limit(bad, 20, errors);
            assertThat(errors).as(bad).containsExactly("limit must be an integer between 1 and 500");
        }
    }

    @Test
    void throwIfAnyCarriesEveryError() {
        QueryParams.throwIfAny(QueryParams.newErrors());
        assertThatThrownBy(() -> QueryParams.throwIfAny(List.of("a", "b")))
                .isInstanceOf(InvalidParametersException.class)
                .satisfies(e -> assertThat(((InvalidParametersException) e).errors()).containsExactly("a", "b"));
    }

    @Test
    void resolveThrowsWithTheErrors() {
        assertThatThrownBy(() -> DateRange.resolve("next-tuesday", null, CLOCK))
                .isInstanceOf(InvalidParametersException.class)
                .hasMessage("from must be an ISO date (YYYY-MM-DD)");
    }
}
