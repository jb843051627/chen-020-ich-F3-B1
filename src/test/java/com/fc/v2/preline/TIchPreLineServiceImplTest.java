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
 * 申报准入门槛线跨通道覆盖口径的规矩测试（Mapper 全打桩，不碰库）。
 *
 * <p>盯旧毛病：列表/单规则/取头/是否可用四个通道各判各的，
 * 生效区间、在场情形、让位顺位都没对齐；没生效版本时还拿 null 去解引用。
 * 现在四通道共用 activeAt 与同一排序。
 *
 * @author fuce
 * @date 2026-10-03
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TIchPreLineServiceImplTest {

    @Mock
    private TIchPreLineMapper preLineMapper;

    @InjectMocks
    private TIchPreLineServiceImpl service;

    private final List<TIchPreLine> rows = new ArrayList<>();

    private static Date dt(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    public void setUp() {
        rows.clear();
        when(preLineMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(rows));
        when(preLineMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TIchPreLine r : rows) {
                if (r.getId().equals(id)) {
                    return r;
                }
            }
            return null;
        });
    }

    private TIchPreLine rule(long id, String code, int priority, Integer status,
                             String start, String end) {
        TIchPreLine r = new TIchPreLine();
        r.setId(id);
        r.setRuleCode(code);
        r.setRuleName(code + "号线");
        r.setTh1Max(new BigDecimal("10"));
        r.setTh2Max(new BigDecimal("50"));
        r.setTh3Max(new BigDecimal("90"));
        r.setEffStart(start == null ? null : dt(start));
        r.setEffEnd(end == null ? null : dt(end));
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        rows.add(r);
        return r;
    }

    @Test
    public void 生效区间内含启用日不含交棒日() {
        rule(1, "A", 1, 0, "2026-10-01 00:00:00", "2026-10-09 00:00:00");
        // 启用之日（含）在场
        assertTrue(service.usable(1L, dt("2026-10-01 00:00:00")));
        // 区间中段在场
        assertTrue(service.usable(1L, dt("2026-10-05 10:00:00")));
        // 交棒之日不含，踩到交棒那一刻已失效
        assertFalse(service.usable(1L, dt("2026-10-09 00:00:00")));
        assertFalse(service.usable(1L, dt("2026-10-10 00:00:00")));
        // 没到启用日不在场
        assertFalse(service.usable(1L, dt("2026-09-30 23:59:59")));
    }

    @Test
    public void 停用的线四个通道都不取() {
        rule(1, "A", 1, 1, null, null);
        assertEquals(0, service.countAvailable(dt("2026-10-05 10:00:00")));
        assertFalse(service.usable(1L, dt("2026-10-05 10:00:00")));
        assertEquals(0, service.evaluate("A", new BigDecimal("5"), dt("2026-10-05 10:00:00")));
        assertEquals(0, service.evaluateTop(new BigDecimal("5"), dt("2026-10-05 10:00:00")));
    }

    @Test
    public void 单规则求值只取此刻生效的版本() {
        // 旧版本 9 月交棒，新版本 10 月起生效
        rule(1, "A", 1, 0, "2026-09-01 00:00:00", "2026-10-01 00:00:00");
        rule(2, "A", 1, 0, "2026-10-01 00:00:00", null);
        Date at = dt("2026-10-05 10:00:00");
        assertEquals(1, service.evaluate("A", new BigDecimal("10"), at));
        assertEquals(2, service.evaluate("A", new BigDecimal("50"), at));
        // 9 月里只有旧版本生效
        assertEquals(1, service.evaluate("A", new BigDecimal("5"), dt("2026-09-15 10:00:00")));
    }

    @Test
    public void 该时刻没有生效版本回零不抛异常() {
        rule(1, "A", 1, 0, "2026-11-01 00:00:00", null);
        assertEquals(0, service.evaluate("A", new BigDecimal("5"), dt("2026-10-05 10:00:00")));
        assertEquals(0, service.evaluate("NOT-EXIST", new BigDecimal("5"), dt("2026-10-05 10:00:00")));
    }

    @Test
    public void 取头按让位顺位高者先说话() {
        rule(1, "LOW", 1, 0, null, null);
        rule(2, "HIGH", 9, 0, null, null);
        // 高顺位在前；输入 5 在两线都落一档，这里改用阈值差异验证选中 HIGH
        rows.get(1).setTh1Max(new BigDecimal("3"));
        // 5 对 HIGH（起分 3）落二档，对 LOW（起分 10）落一档；取头应给二档
        assertEquals(2, service.evaluateTop(new BigDecimal("5"), dt("2026-10-05 10:00:00")));
    }

    @Test
    public void 输入越界与参数缺失各通道一致回零() {
        rule(1, "A", 1, 0, null, null);
        Date at = dt("2026-10-05 10:00:00");
        assertEquals(0, service.evaluate("A", new BigDecimal("-1"), at));
        assertEquals(0, service.evaluate("A", new BigDecimal("100.01"), at));
        assertEquals(0, service.evaluate("A", null, at));
        assertEquals(0, service.evaluateTop(new BigDecimal("-1"), at));
        assertEquals(0, service.countAvailable(null));
        assertFalse(service.usable(1L, null));
    }

    @Test
    public void 等于上限取高一档() {
        rule(1, "A", 1, 0, null, null);
        Date at = dt("2026-10-05 10:00:00");
        assertEquals(1, service.evaluate("A", new BigDecimal("10"), at));
        assertEquals(2, service.evaluate("A", new BigDecimal("10.01"), at));
        assertEquals(1, service.countAvailable(at));
    }
}
