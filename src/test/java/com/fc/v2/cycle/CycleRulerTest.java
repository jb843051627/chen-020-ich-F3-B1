package com.fc.v2.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.TIchCycleTask;
import com.fc.v2.model.custom.cycle.CycleRuler;

/**
 * 唯一一把工作日尺 CycleRuler 的规矩测试：不启 Spring、不碰库。
 *
 * <p>测试口径：法定节假日 2026-10-01~03，省里统一停办公 2026-10-09，
 * 夜里那一段 22:00—次日 07:00。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class CycleRulerTest {

    private static final Set<LocalDate> HOLIDAYS = new HashSet<>(Arrays.asList(
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3)));
    private static final Set<LocalDate> CLOSURES = new HashSet<>(
            java.util.Collections.singletonList(LocalDate.of(2026, 10, 9)));

    private final CycleRuler ruler = CycleRuler.of(
            LocalTime.of(22, 0), LocalTime.of(7, 0), HOLIDAYS, CLOSURES);

    private static Date dt(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static TIchCycleTask row(long id, String dueAt, Integer status, Integer delFlag) {
        TIchCycleTask r = new TIchCycleTask();
        r.setId(id);
        r.setItemNo("DQ-2026-" + id);
        r.setDueAt(dueAt == null ? null : dt(dueAt));
        r.setAmount(BigDecimal.ZERO);
        r.setStatus(status);
        r.setDelFlag(delFlag);
        return r;
    }

    @Test
    public void 数到该出手那日还剩几日_节假日与停办日整段不算() {
        // 当日即到：0
        assertEquals(0, ruler.remainingWorkdays(dt("2026-10-05 10:00:00"), dt("2026-10-05 18:00:00")));
        // 次日：1
        assertEquals(1, ruler.remainingWorkdays(dt("2026-10-05 10:00:00"), dt("2026-10-06 18:00:00")));
        // 隔三日：3
        assertEquals(3, ruler.remainingWorkdays(dt("2026-10-05 10:00:00"), dt("2026-10-08 18:00:00")));
        // 10-09 是省里停办日，整段不算：06、07、08、10 共 4 日
        assertEquals(4, ruler.remainingWorkdays(dt("2026-10-05 10:00:00"), dt("2026-10-10 18:00:00")));
        // 10-01~03 法定节假日整段不算，只剩 10-04、10-05 两日
        assertEquals(2, ruler.remainingWorkdays(dt("2026-09-30 10:00:00"), dt("2026-10-05 18:00:00")));
        // 该出手那日不晚于手头：0
        assertEquals(0, ruler.remainingWorkdays(dt("2026-10-06 10:00:00"), dt("2026-10-05 18:00:00")));
    }

    @Test
    public void 夜里那一段的起止() {
        assertTrue(ruler.isNight(LocalTime.of(22, 0)));
        assertTrue(ruler.isNight(LocalTime.of(23, 30)));
        assertTrue(ruler.isNight(LocalTime.of(6, 59)));
        assertFalse(ruler.isNight(LocalTime.of(7, 0)));
        assertFalse(ruler.isNight(LocalTime.of(12, 0)));
        assertFalse(ruler.isNight(LocalTime.of(21, 59)));
    }

    @Test
    public void 压着的那一段_夜里节假日省停办日整段压着() {
        // 法定节假日里，白日也压着
        assertTrue(ruler.isPressed(dt("2026-10-01 10:00:00")));
        // 省里统一停办公的那一日
        assertTrue(ruler.isPressed(dt("2026-10-09 10:00:00")));
        // 平常日的夜里那一段
        assertTrue(ruler.isPressed(dt("2026-10-05 23:30:00")));
        assertTrue(ruler.isPressed(dt("2026-10-05 06:30:00")));
        // 平常日的白日不压着
        assertFalse(ruler.isPressed(dt("2026-10-05 10:00:00")));
        assertFalse(ruler.isPressed(dt("2026-10-05 07:00:00")));
    }

    @Test
    public void 越过该出手那一日的止点() {
        assertTrue(ruler.isOverdue(dt("2026-10-05 18:00:01"), dt("2026-10-05 18:00:00")));
        assertFalse(ruler.isOverdue(dt("2026-10-05 18:00:00"), dt("2026-10-05 18:00:00")));
        assertFalse(ruler.isOverdue(dt("2026-10-05 17:59:59"), dt("2026-10-05 18:00:00")));
    }

    @Test
    public void 提前几日开口_够上才开越过不开() {
        Date at = dt("2026-10-05 10:00:00");
        Date due = dt("2026-10-08 18:00:00");
        // 还剩 3 个工作日：提前量 3 够上，2 够不上
        assertTrue(ruler.isWindowOpen(at, due, 3));
        assertFalse(ruler.isWindowOpen(at, due, 2));
        // 已越过止点的一律不算开口
        assertFalse(ruler.isWindowOpen(dt("2026-10-08 18:00:01"), due, 9));
    }

    @Test
    public void 挑单只问该出手那天到没到() {
        List<TIchCycleTask> rows = new ArrayList<>();
        rows.add(row(1, "2026-10-08 18:00:00", 0, 0));   // 到点（amount=0 时未到，见下行分开验）
        rows.add(row(2, "2026-10-20 18:00:00", 0, 0));   // 够不上日子
        rows.add(row(3, "2026-10-04 18:00:00", 0, 0));   // 已越过
        rows.add(row(4, "2026-10-06 18:00:00", 1, 0));   // 上一轮已办妥，不许再捞
        rows.add(row(5, "2026-10-06 18:00:00", 2, 0));   // 已催不动，不再捞
        rows.add(row(6, "2026-10-06 18:00:00", 0, 1));   // 已删除
        rows.add(row(7, null, 0, 0));                    // 没钉日子
        rows.get(0).setAmount(new BigDecimal("5"));      // 1号可提前 5 日：到点
        rows.get(3).setAmount(new BigDecimal("5"));      // 4号即便提前量够，办妥过就不再捞

        List<TIchCycleTask> picked = ruler.pick(rows, dt("2026-10-05 10:00:00"));
        assertEquals(2, picked.size());
        assertTrue(picked.stream().anyMatch(r -> r.getId() == 1L));
        assertTrue(picked.stream().anyMatch(r -> r.getId() == 3L));
    }

    @Test
    public void 压着的那一段手里有已到点的也不动() {
        List<TIchCycleTask> rows = new ArrayList<>();
        rows.add(row(1, "2026-09-30 18:00:00", 0, 0));   // 已越过
        rows.add(row(2, "2026-10-01 18:00:00", 0, 0));   // 当日至
        // 法定节假日里：一条都不捞
        assertTrue(ruler.pick(rows, dt("2026-10-01 10:00:00")).isEmpty());
        // 省停办日里：一条都不捞
        assertTrue(ruler.pick(rows, dt("2026-10-09 10:00:00")).isEmpty());
        // 夜里那一段：一条都不捞
        assertTrue(ruler.pick(rows, dt("2026-10-05 23:30:00")).isEmpty());
    }

    @Test
    public void 同一瞬间两条一起到_各归各进名单() {
        List<TIchCycleTask> rows = new ArrayList<>();
        rows.add(row(1, "2026-10-08 18:00:00", 0, 0));
        rows.add(row(2, "2026-10-08 18:00:00", 0, 0));
        List<TIchCycleTask> picked = ruler.pick(rows, dt("2026-10-08 09:00:00"));
        // 既不合成一条也不许漏掉一条
        assertEquals(2, picked.size());
        assertTrue(picked.stream().anyMatch(r -> r.getId() == 1L));
        assertTrue(picked.stream().anyMatch(r -> r.getId() == 2L));
    }

    @Test
    public void 提前量为零_当日才开口() {
        List<TIchCycleTask> rows = new ArrayList<>();
        rows.add(row(1, "2026-10-05 18:00:00", 0, 0));   // 当日至，剩余 0 够上提前量 0
        rows.add(row(2, "2026-10-06 18:00:00", 0, 0));   // 明日至，剩余 1 够不上
        List<TIchCycleTask> picked = ruler.pick(rows, dt("2026-10-05 10:00:00"));
        assertEquals(1, picked.size());
        assertEquals(1L, picked.get(0).getId().longValue());
    }
}
