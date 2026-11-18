package dev.rift.engine;

import dev.rift.model.Endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Decides whether a URL found in the frontend refers to an endpoint declared by the backend.
 *
 * <p>Paths are compared segment by segment. A segment is either fixed text or a parameter
 * (anything containing "{"). Parameter names never matter: {boardId} and {boardID} are the same.
 */
public final class PathMatcher {

    private PathMatcher() {
    }

    /**
     * How well a call matches an endpoint.
     *
     * @param strict      false when a frontend parameter had to be accepted where the backend has
     *                    fixed text (the parameter might hold that text, but we cannot tell)
     * @param literalHits number of segments that matched as identical fixed text
     */
    public record Match(boolean strict, int literalHits) implements Comparable<Match> {
        @Override
        public int compareTo(Match other) {
            if (strict != other.strict) {
                return strict ? 1 : -1;
            }
            return Integer.compare(literalHits, other.literalHits);
        }
    }

    public static Optional<Match> match(String callPath, String endpointPath) {
        List<String> call = segments(callPath);
        List<String> endpoint = segments(endpointPath);
        if (call.size() != endpoint.size()) {
            return Optional.empty();
        }

        boolean strict = true;
        int literalHits = 0;
        for (int i = 0; i < call.size(); i++) {
            String callSegment = call.get(i);
            String endpointSegment = endpoint.get(i);

            if (isParameter(endpointSegment)) {
                continue; // the backend accepts any value here
            }
            if (isParameter(callSegment)) {
                strict = false;
                continue;
            }
            if (!callSegment.equals(endpointSegment)) {
                return Optional.empty();
            }
            literalHits++;
        }
        return Optional.of(new Match(strict, literalHits));
    }

    /**
     * Every endpoint that matches the call equally well, ignoring the HTTP method.
     * A strict match beats a loose one, and more identical fixed segments beat fewer, so
     * a call to /boards/{id} is not credited to /boards/search.
     */
    public static List<Endpoint> bestMatches(String callPath, List<Endpoint> endpoints) {
        Match best = null;
        List<Endpoint> result = new ArrayList<>();

        for (Endpoint endpoint : endpoints) {
            Optional<Match> match = match(callPath, endpoint.pathTemplate());
            if (match.isEmpty()) {
                continue;
            }
            int comparison = best == null ? 1 : match.get().compareTo(best);
            if (comparison > 0) {
                best = match.get();
                result.clear();
                result.add(endpoint);
            } else if (comparison == 0) {
                result.add(endpoint);
            }
        }
        return result;
    }

    /** Drops the query string and fragment, then splits on "/" ignoring empty parts. */
    static List<String> segments(String path) {
        String clean = path;
        int cut = indexOfAny(clean, '?', '#');
        if (cut >= 0) {
            clean = clean.substring(0, cut);
        }

        List<String> segments = new ArrayList<>();
        for (String segment : clean.split("/")) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments;
    }

    private static boolean isParameter(String segment) {
        return segment.contains("{");
    }

    private static int indexOfAny(String text, char first, char second) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == first || c == second) {
                return i;
            }
        }
        return -1;
    }
}