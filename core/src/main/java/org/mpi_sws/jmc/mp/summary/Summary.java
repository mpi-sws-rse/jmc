package org.mpi_sws.jmc.mp.summary;

import java.util.List;
import java.util.Map;

/**
 * One compact summary, the content of one line of a summary file: the statuses and words per
 * visible role, in the format's role order, and the counts.
 * Two complete graphs have the same summary iff these three components are equal; the multiplicity
 * {@code n} is not part of the identity and is supplied when the line is written.
 *
 * @param roles the visible roles in the format's order (ASCII, by bytes)
 * @param statuses role → {@code "done"}, {@code "blocked"} or {@code "err"}
 * @param words role → the observations in program order, each already encoded as
 *     {@code {"s":V}}, {@code {"r":V}} or {@code {"r":null}}
 * @param counts the causal-order counts, event-major, as the format lays them out
 */
public record Summary(
        List<String> roles,
        Map<String, String> statuses,
        Map<String, List<String>> words,
        List<Integer> counts) {

    /**
     * Renders the line.
     *
     * @param n the number of complete graphs with this summary
     * @return one JSON line, no trailing newline
     */
    public String toJson(int n) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"statuses\":{");
        boolean first = true;
        for (String r : roles) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            MpValueEncoder.quote(sb, r);
            sb.append(':');
            MpValueEncoder.quote(sb, statuses.get(r));
        }
        sb.append("},\"words\":{");
        first = true;
        for (String r : roles) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            MpValueEncoder.quote(sb, r);
            sb.append(":[").append(String.join(",", words.get(r))).append(']');
        }
        sb.append("},\"counts\":[");
        first = true;
        for (Integer c : counts) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(c);
        }
        sb.append("],\"n\":").append(n).append('}');
        return sb.toString();
    }
}
