package com.capsule.credit.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.error.CommonErrorCode;
import com.capsule.credit.domain.CreditLedgerEntry;
import com.capsule.credit.dto.CreditTransactionsResponse;
import com.capsule.credit.repository.CreditLedgerRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** api.md 34 크레딧 내역. 먼저 출석 지급 · 소멸을 처리해야 오늘 내역이 보여서 CreditService.current를 거친다 */
@Service
@Transactional
@RequiredArgsConstructor
public class CreditHistoryService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    /** filter → 보여 줄 type. ATTENDANCE는 출석 지급 · 소멸 (화면의 '출석' 탭) */
    private static final Map<String, List<String>> FILTERS = Map.of(
            "ALL",
                    List.of(
                            CreditLedgerEntry.ATTENDANCE,
                            CreditLedgerEntry.INVITE,
                            CreditLedgerEntry.USE,
                            CreditLedgerEntry.EXPIRE),
            "ATTENDANCE", List.of(CreditLedgerEntry.ATTENDANCE, CreditLedgerEntry.EXPIRE),
            "USE", List.of(CreditLedgerEntry.USE),
            "INVITE", List.of(CreditLedgerEntry.INVITE));

    private final CreditService credits;
    private final CreditLedgerRepository ledger;

    public CreditTransactionsResponse page(long userId, String filter, String cursor, Integer size) {
        List<String> types = FILTERS.get(filter == null ? "ALL" : filter);
        int n = size == null ? DEFAULT_SIZE : size;
        if (types == null || n < 1 || n > MAX_SIZE) throw new ApiException(CommonErrorCode.INVALID_REQUEST);
        long before = before(cursor);
        credits.current(userId);
        List<CreditLedgerEntry> rows = ledger.findPage(userId, types, before, PageRequest.of(0, n + 1));
        boolean more = rows.size() > n;
        List<CreditLedgerEntry> shown = more ? rows.subList(0, n) : rows;
        return new CreditTransactionsResponse(
                shown.stream()
                        .map(e -> new CreditTransactionsResponse.Item(
                                e.getId(),
                                e.getType(),
                                e.getTitle(),
                                e.getChange(),
                                e.getBalanceAfter(),
                                e.getCreatedAt()))
                        .toList(),
                more ? String.valueOf(shown.getLast().getId()) : null);
    }

    private static long before(String cursor) {
        if (cursor == null || cursor.isBlank()) return Long.MAX_VALUE;
        try {
            long id = Long.parseLong(cursor);
            if (id < 1) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) {
            throw new ApiException(CommonErrorCode.INVALID_REQUEST);
        }
    }
}
