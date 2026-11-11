package com.capsule.user.repository;

import com.capsule.user.dto.TermVersion;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** terms, term_versions, user_terms. 읽기와 동의 기록(추가만)뿐이라 엔티티 없이 SQL로 둔다. */
@Repository
@RequiredArgsConstructor
public class TermsRepository {

    private final JdbcClient jdbc;

    /** 약관마다 시행일이 지난 버전 중 가장 높은 것, 표시 순서대로 */
    public List<TermVersion> latestVersions(Instant now) {
        return jdbc.sql("""
                        SELECT term_version_id, code, name, required, content FROM (
                            SELECT DISTINCT ON (t.id)
                                   v.id AS term_version_id, t.code, t.name, t.is_required AS required, v.content,
                                   t.sort_order
                            FROM terms t JOIN term_versions v ON v.term_id = t.id
                            WHERE v.effective_at <= :now
                            ORDER BY t.id, v.version DESC
                        ) latest
                        ORDER BY sort_order, code
                        """)
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .query(TermVersion.class)
                .list();
    }

    public void recordAgreements(long userId, Collection<Long> termVersionIds) {
        for (long termVersionId : termVersionIds) {
            jdbc.sql("INSERT INTO user_terms (user_id, term_version_id, agreed) VALUES (?, ?, true)")
                    .params(userId, termVersionId)
                    .update();
        }
    }
}
