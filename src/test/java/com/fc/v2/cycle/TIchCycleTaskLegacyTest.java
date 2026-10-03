package com.fc.v2.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.fc.v2.mapper.auto.TIchCycleLogMapper;
import com.fc.v2.mapper.auto.TIchCycleTaskMapper;
import com.fc.v2.mapper.auto.TIchProjectMapper;
import com.fc.v2.model.auto.TIchCycleTask;
import com.fc.v2.service.IchCycleNoticeSender;
import com.fc.v2.service.impl.TIchCycleTaskServiceImpl;

/**
 * 老捞法 listDue/runOnce 的端点与兜底规矩（Mapper 全打桩）。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TIchCycleTaskLegacyTest {

    @Mock
    private TIchCycleTaskMapper taskMapper;
    @Mock
    private TIchCycleLogMapper logMapper;
    @Mock
    private TIchProjectMapper projectMapper;
    @Mock
    private IchCycleNoticeSender noticeSender;

    @InjectMocks
    private TIchCycleTaskServiceImpl service;

    private final List<TIchCycleTask> rows = new ArrayList<>();

    private static Date dt(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TIchCycleTask row(long id, String dueAt, BigDecimal amount) {
        TIchCycleTask r = new TIchCycleTask();
        r.setId(id);
        r.setItemNo("DQ-2026-" + id);
        r.setDueAt(dueAt == null ? null : dt(dueAt));
        r.setAmount(amount);
        r.setStatus(0);
        r.setDelFlag(0);
        rows.add(r);
        return r;
    }

    @BeforeEach
    public void setUp() {
        when(taskMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(rows));
    }

    @Test
    public void 恰好踩在该出手那一刻也算到期_端点不漏() {
        TIchCycleTask onTheDot = row(1, "2026-10-05 10:00:00", BigDecimal.ZERO);
        row(2, "2026-10-06 10:00:00", BigDecimal.ZERO);
        List<TIchCycleTask> due = service.listDue(dt("2026-10-05 10:00:00"));
        assertEquals(1, due.size());
        assertTrue(due.contains(onTheDot));
    }

    @Test
    public void 一条到期都没有_runOnce回零不越界() {
        assertEquals(0, service.runOnce(dt("2026-10-05 10:00:00")));
    }

    @Test
    public void 提前量那一格空着_逐条处置不解引用空值整轮不中断() {
        // amount（可提前几日开口）空着：它只是开窗的尺，不是办没办成的结算依据，
        // 到期这一条仍要计成功，绝不能在 .signum() 上空指针把整轮打断
        row(1, "2026-10-04 10:00:00", null);
        assertEquals(1, service.runOnce(dt("2026-10-05 10:00:00")));
    }

    @Test
    public void 到期即办_成一条计一条_不看提前量正负() {
        row(1, "2026-10-04 10:00:00", new BigDecimal("-5"));
        row(2, "2026-10-03 10:00:00", new BigDecimal("5"));
        assertEquals(2, service.runOnce(dt("2026-10-05 10:00:00")));
    }
}
