package dev.rift.engine;

import dev.rift.engine.PathMatcher.Match;
import dev.rift.model.Endpoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PathMatcherTest {

    private static final Endpoint SEARCH = new Endpoint("GET", "/api/boards/search");
    private static final Endpoint GET_BOARD = new Endpoint("GET", "/api/boards/{boardID}");
    private static final Endpoint DELETE_BOARD = new Endpoint("DELETE", "/api/boards/{boardID}");
    private static final Endpoint CARDS = new Endpoint("GET", "/api/cards");
    private static final List<Endpoint> ENDPOINTS = List.of(SEARCH, GET_BOARD, DELETE_BOARD, CARDS);

    @Test
    void identicalPathsMatchStrictly() {
        assertEquals(Optional.of(new Match(true, 3)),
                PathMatcher.match("/api/v2/boards", "/api/v2/boards"));
    }

    @Test
    void parameterNamesAreIgnored() {
        assertEquals(Optional.of(new Match(true, 3)),
                PathMatcher.match("/api/v2/boards/{boardId}", "/api/v2/boards/{boardID}"));
    }

    @Test
    void trailingSlashesAreIgnoredOnBothSides() {
        assertEquals(Optional.of(new Match(true, 3)),
                PathMatcher.match("/api/v2/boards/", "/api/v2/boards"));
        assertEquals(Optional.of(new Match(true, 4)),
                PathMatcher.match("/api/v2/boards/{id}/blocks", "/api/v2/boards/{boardID}/blocks/"));
    }

    @Test
    void queryStringIsIgnored() {
        assertEquals(Optional.of(new Match(true, 3)),
                PathMatcher.match("/api/v2/boards?page=2", "/api/v2/boards"));
    }

    @Test
    void differentNumberOfSegmentsDoNotMatch() {
        assertEquals(Optional.empty(),
                PathMatcher.match("/api/v2/boards/{id}", "/api/v2/boards"));
    }

    @Test
    void differentFixedTextDoesNotMatch() {
        assertEquals(Optional.empty(),
                PathMatcher.match("/api/v2/boards", "/api/v2/cards"));
    }

    @Test
    void aFixedValueMatchesABackendParameter() {
        assertEquals(Optional.of(new Match(true, 2)),
                PathMatcher.match("/api/users/123", "/api/users/{id}"));
    }

    @Test
    void aFrontendParameterAgainstBackendFixedTextIsALooseMatch() {
        assertEquals(Optional.of(new Match(false, 2)),
                PathMatcher.match("/api/users/{id}", "/api/users/me"));
    }

    @Test
    void aStrictMatchBeatsALooseOne() {
        assertEquals(List.of(GET_BOARD, DELETE_BOARD),
                PathMatcher.bestMatches("/api/boards/{id}", ENDPOINTS));
    }

    @Test
    void moreIdenticalSegmentsWin() {
        assertEquals(List.of(SEARCH),
                PathMatcher.bestMatches("/api/boards/search", ENDPOINTS));
    }

    @Test
    void noMatchGivesAnEmptyList() {
        assertEquals(List.of(), PathMatcher.bestMatches("/api/projects", ENDPOINTS));
    }

    @Test
    void aLooseCandidateStillMatchesWhenItIsTheOnlyOne() {
        assertEquals(List.of(SEARCH),
                PathMatcher.bestMatches("/api/boards/{x}", List.of(SEARCH, CARDS)));
    }
}