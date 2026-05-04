package com.jimuqu.solon.claw.web;

import com.jimuqu.solon.claw.core.model.SessionSearchEntry;
import com.jimuqu.solon.claw.core.model.SessionSearchQuery;
import com.jimuqu.solon.claw.core.service.SessionSearchService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

/** Hermes session/run search endpoint. */
@Controller
public class DashboardSearchController {
    private final SessionSearchService sessionSearchService;

    public DashboardSearchController(SessionSearchService sessionSearchService) {
        this.sessionSearchService = sessionSearchService;
    }

    @Mapping(value = "/api/hermes/search", method = MethodType.GET)
    public Map<String, Object> search(Context context) throws Exception {
        SessionSearchQuery query = new SessionSearchQuery();
        query.setSourceKey(first(context.param("sourceKey"), context.param("source")));
        query.setSessionId(context.param("sessionId"));
        query.setRunId(context.param("runId"));
        query.setToolName(context.param("toolName"));
        query.setChannel(first(context.param("channel"), context.param("platform")));
        query.setQuery(context.param("q"));
        query.setTimeFrom(asLong(context.param("timeFrom")));
        query.setTimeTo(asLong(context.param("timeTo")));
        query.setSummarize(Boolean.parseBoolean(context.param("summarize")));
        query.setLimit(context.paramAsInt("limit", 10));
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (SessionSearchEntry entry : sessionSearchService.search(query)) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("session_id", entry.getSessionId());
            row.put("branch_name", entry.getBranchName());
            row.put("title", entry.getTitle());
            row.put("updated_at", entry.getUpdatedAt());
            row.put("match_preview", entry.getMatchPreview());
            row.put("summary", entry.getSummary());
            row.put("run_id", entry.getRunId());
            row.put("tool_name", entry.getToolName());
            row.put("channel", entry.getChannel());
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("results", rows);
        result.put("tokenizer", "fts5/cjk-ngram-fallback");
        return DashboardResponse.ok(result);
    }

    private String first(String left, String right) {
        return left == null || left.trim().length() == 0 ? right : left;
    }

    private long asLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }
}
