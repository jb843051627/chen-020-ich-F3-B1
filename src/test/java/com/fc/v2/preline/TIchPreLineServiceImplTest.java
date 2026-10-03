package com.fc.v2.preline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fc.v2.mapper.auto.TIchPreLineMapper;
import com.fc.v2.model.auto.TIchPreLine;
import com.fc.v2.service.impl.TIchPreLineServiceImpl;

/**
 * 准入门槛线五个入口"这一刻在不在场"同一把尺（Mapper 全打桩，不起 Spring、不碰库）。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TIchPreLineServiceImplTest {

    @Mock
    private TIchPreLineMapper mapper;

    @InjectMocks
    private TIchPreLineServiceImpl service;

    private final List<TIchPreLine> store = new ArrayList<>();

    private static Date dt(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TIchPreLine rule(long id, String code, int priority, String start, String end, int status) {
        TIchPreLine r = new TIchPreLine();
        r.setId(id);
        r.setRuleCode(code);
        r.setPriority(priority);
        r.setEffStart(start == null ? null : dt(start));
        r.setEffEnd(end == null ? null : dt(end));
        r.setStatus(status);
        r.setTh1Max(new BigDecimal("60"));
        r.setTh2Max(new BigDecimal("80"));
        r.setTh3Max(new BigDecimal("90"));
        r.setDelFlag(0);
        store.add(r);
        return r;
    }

    @BeforeEach
    public void setUp() {
        when(mapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(store));
        when(mapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TIchPreLine r : store) {
                if (r.getId().equals(id)) {
                    return r;
                }
            }
            return null;
        });
    }

    @Test
    public void 未到启用之日的线这一刻不在场_五口同源() {
        rule(1, "A", 10, "2026-11-01 00:00:00", null, 0);
        Date at = dt("2026-10-02 10:00:00");
        assertTrue(service.listAvailable(at).isEmpty());
        assertFalse(service.usable(1L, at));
        assertEquals(0, service.countAvailable(at));
        assertEquals(0, service.evaluate("A", new BigDecimal("50"), at));
        assertEquals(0, service.evaluateTop(new BigDecimal("50"), at));
    }

    @Test
    public void 已交棒的线这一刻不在场_交棒那日不含() {
        rule(1, "A", 10, "2026-01-01 00:00:00", "2026-10-01 00:00:00", 0);
        Date atEdge = dt("2026-10-01 00:00:00");
        assertTrue(service.listAvailable(atEdge).isEmpty());
        assertFalse(service.usable(1L, atEdge));
        assertTrue(service.listAvailable(dt("2026-09-30 10:00:00")).size() == 1);
    }

    @Test
    public void 已停用的线不在场() {
        rule(1, "A", 10, null, null, 1);
        Date at = dt("2026-10-02 10:00:00");
        assertTrue(service.listAvailable(at).isEmpty());
        assertFalse(service.usable(1L, at));
        assertEquals(0, service.countAvailable(at));
        assertEquals(0, service.evaluate("A", new BigDecimal("50"), at));
    }

    @Test
    public void 多版本同代号_取这一刻在场且顺位最前的一条判档() {
        rule(1, "A", 5, null, null, 0);
        rule(2, "A", 9, null, null, 0);
        Date at = dt("2026-10-02 10:00:00");
        // 顺位 9 的一条排最前
        List<TIchPreLine> avail = service.listAvailable(at);
        assertEquals(2, avail.size());
        assertEquals(2L, avail.get(0).getId().longValue());
        assertEquals(1, service.evaluate("A", new BigDecimal("50"), at));
        assertEquals(1, service.evaluateTop(new BigDecimal("50"), at));
        assertTrue(service.usable(1L, at));
        assertEquals(2, service.countAvailable(at));
    }

    @Test
    public void 分档等于上限取高一档() {
        rule(1, "A", 5, null, null, 0);
        Date at = dt("2026-10-02 10:00:00");
        assertEquals(2, service.evaluate("A", new BigDecimal("60"), at));
        assertEquals(3, service.evaluateTop(new BigDecimal("80"), at));
        assertEquals(4, service.evaluateTop(new BigDecimal("95"), at));
    }
}
