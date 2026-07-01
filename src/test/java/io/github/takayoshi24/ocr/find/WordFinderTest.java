package io.github.takayoshi24.ocr.find;

import io.github.takayoshi24.ocr.extract.WordOccurrence;
import io.github.takayoshi24.ocr.find.WordFinder.MatchMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class WordFinderTest {

    // Helper to build a minimal WordOccurrence — geometry values are irrelevant for these tests
    private static WordOccurrence occ(String word) {
        return new WordOccurrence(word, 1, 0f, 0f, 10f, 10f);
    }

    // -----------------------------------------------------------------------
    // EXACT mode
    // -----------------------------------------------------------------------

    @Test
    void exact_matchingWordIsFound() {
        WordFinder finder = new WordFinder(MatchMode.EXACT);
        List<RedactionTarget> results = finder.find(List.of(occ("Hello")), List.of("Hello"));

        assertEquals(1, results.size());
        assertEquals("Hello", results.get(0).occurrence().word());
        assertEquals("Hello", results.get(0).matchedPattern());
    }

    @Test
    void exact_nonMatchingWordNotFound() {
        WordFinder finder = new WordFinder(MatchMode.EXACT);
        List<RedactionTarget> results = finder.find(List.of(occ("World")), List.of("Hello"));

        assertTrue(results.isEmpty());
    }

    @Test
    void exact_differentCaseIsNoMatch() {
        WordFinder finder = new WordFinder(MatchMode.EXACT);
        // "hello" (lower-case) should NOT match target "Hello" (title-case) in EXACT mode
        List<RedactionTarget> results = finder.find(List.of(occ("hello")), List.of("Hello"));

        assertTrue(results.isEmpty());
    }

    // -----------------------------------------------------------------------
    // CASE_INSENSITIVE mode
    // -----------------------------------------------------------------------

    @Test
    void caseInsensitive_upperVariantMatches() {
        WordFinder finder = new WordFinder(MatchMode.CASE_INSENSITIVE);
        List<RedactionTarget> results = finder.find(List.of(occ("HELLO")), List.of("hello"));

        assertEquals(1, results.size());
        assertEquals("hello", results.get(0).matchedPattern());
    }

    @Test
    void caseInsensitive_lowerVariantMatches() {
        WordFinder finder = new WordFinder(MatchMode.CASE_INSENSITIVE);
        List<RedactionTarget> results = finder.find(List.of(occ("hello")), List.of("HELLO"));

        assertEquals(1, results.size());
        assertEquals("HELLO", results.get(0).matchedPattern());
    }

    // -----------------------------------------------------------------------
    // REGEX mode
    // -----------------------------------------------------------------------

    @Test
    void regex_numericPatternMatchesNumericWord() {
        WordFinder finder = new WordFinder(MatchMode.REGEX);
        List<RedactionTarget> results = finder.find(List.of(occ("12345")), List.of("\\d+"));

        assertEquals(1, results.size());
        assertEquals("\\d+", results.get(0).matchedPattern());
    }

    @Test
    void regex_nonMatchingWordNotReturned() {
        WordFinder finder = new WordFinder(MatchMode.REGEX);
        List<RedactionTarget> results = finder.find(List.of(occ("abc")), List.of("\\d+"));

        assertTrue(results.isEmpty());
    }

    @Test
    void regex_patternOver200Chars_throwsIllegalArgumentException() {
        WordFinder finder = new WordFinder(MatchMode.REGEX);
        String longPattern = "a".repeat(201);
        assertThrows(IllegalArgumentException.class,
                () -> finder.find(List.of(occ("a")), List.of(longPattern)));
    }

    @Test
    void regex_patternExactly200Chars_isAccepted() {
        WordFinder finder = new WordFinder(MatchMode.REGEX);
        String pattern = "a".repeat(200);
        assertDoesNotThrow(() -> finder.find(List.of(occ("a")), List.of(pattern)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void regex_matchTimesOut_throwsIllegalArgumentExceptionWithTimeoutMessage() throws Exception {
        // Use a mock executor that simulates a TimeoutException so the test is fast
        // and deterministic regardless of JVM regex optimizations.
        Future<Boolean> timedOutFuture = mock(Future.class);
        when(timedOutFuture.get(anyLong(), any(TimeUnit.class))).thenThrow(new TimeoutException());
        ExecutorService mockExecutor = mock(ExecutorService.class);
        when(mockExecutor.submit(any(Callable.class))).thenReturn((Future) timedOutFuture);

        WordFinder finder = new WordFinder(MatchMode.REGEX, 100, mockExecutor);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> finder.find(List.of(occ("hello")), List.of("hello")));
        assertTrue(ex.getMessage().contains("timed out"), "Expected timeout message, got: " + ex.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void regex_matchInterrupted_cancelsFutureAndThrowsIllegalArgumentException() throws Exception {
        Future<Boolean> interruptedFuture = mock(Future.class);
        when(interruptedFuture.get(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
        ExecutorService mockExecutor = mock(ExecutorService.class);
        when(mockExecutor.submit(any(Callable.class))).thenReturn((Future) interruptedFuture);

        WordFinder finder = new WordFinder(MatchMode.REGEX, 100, mockExecutor);
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> finder.find(List.of(occ("hello")), List.of("hello")));
        } finally {
            Thread.interrupted(); // clear the interrupt flag set by the implementation
        }

        verify(interruptedFuture).cancel(true);
    }

    // -----------------------------------------------------------------------
    // Multiple targets
    // -----------------------------------------------------------------------

    @Test
    void multipleTargets_wordMatchingSecondTargetHasCorrectPattern() {
        WordFinder finder = new WordFinder(MatchMode.EXACT);
        List<RedactionTarget> results = finder.find(
                List.of(occ("Bar")),
                List.of("Foo", "Bar", "Baz")
        );

        assertEquals(1, results.size());
        assertEquals("Bar", results.get(0).matchedPattern());
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test
    void emptyOccurrencesList_returnsEmptyResult() {
        WordFinder finder = new WordFinder(MatchMode.EXACT);
        List<RedactionTarget> results = finder.find(List.of(), List.of("Hello"));

        assertTrue(results.isEmpty());
    }

    @Test
    void noTargetMatch_returnsEmptyResult() {
        WordFinder finder = new WordFinder(MatchMode.EXACT);
        List<RedactionTarget> results = finder.find(
                List.of(occ("Hello"), occ("World")),
                List.of("Foo", "Bar")
        );

        assertTrue(results.isEmpty());
    }

    @Test
    void wordMatchesOnlyFirstApplicableTarget_breakAfterFirstMatch() {
        // Both "\\w+" and "Hello" would match "Hello" in REGEX mode.
        // The word should only produce ONE RedactionTarget (from the first matching pattern).
        WordFinder finder = new WordFinder(MatchMode.REGEX);
        List<RedactionTarget> results = finder.find(
                List.of(occ("Hello")),
                List.of("\\w+", "Hello")
        );

        assertEquals(1, results.size());
        // The first pattern wins
        assertEquals("\\w+", results.get(0).matchedPattern());
    }
}
