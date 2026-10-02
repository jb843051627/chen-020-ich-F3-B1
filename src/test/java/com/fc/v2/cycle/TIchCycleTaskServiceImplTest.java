package com.fc.v2.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
import com.fc.v2.model.auto.TIchCycleLog;
import com.fc.v2.model.auto.TIchCycleTask;
import com.fc.v2.model.auto.TIchProject;
import com.fc.v2.model.custom.cycle.CycleRoundReport;
import com.fc.v2.model.custom.cycle.CycleRowOutcome;
import com.fc.v2.model.custom.cycle.CycleRuler;
import com.fc.v2.service.IchCycleNoticeSender;
import com.fc.v2.service.impl.TIchCycleTaskServiceImpl;

/**
 * 服务层周期任务 TIchCycleTaskServiceImpl 的规矩测试（Mapper 全打桩，不起 Spring、不碰库）。
 *
 * <p>测试口径：法定节假日 2026-10-01~03，省里统一停办公 2026-10-09，
 * 夜里那一段 22:00—次日 07:00；项目底册 XM00 在册、XM01 已注销。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TIchCycleTaskServiceImplTest {

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
    private final List<TIchCycleLog> logs = new ArrayList<>();
    private final List<TIchProject> projects = new ArrayList<>();
    private final AtomicLong logSeq = new AtomicLong(1);

    private static Date dt(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TIchCycleTask row(long id, String dueAt, int advanceDays) {
        TIchCycleTask r = new TIchCycleTask();
        r.setId(id);
        r.setItemNo("DQ-2026-" + id);
        r.setBizType(0);
        r.setTargetCode("XM00");
        r.setContact("1380000000" + id);
        r.setDueAt(dt(dueAt));
        r.setAmount(new BigDecimal(advanceDays));
        r.setContent("履约催办：保护计划该交差了");
        r.setStatus(0);
        r.setDelFlag(0);
        rows.add(r);
        return r;
    }

    private TIchProject project(String siteNo, int status) {
        TIchProject p = new TIchProject();
        p.setSiteNo(siteNo);
        p.setSiteName("澜川县·" + siteNo);
        p.setStatus(status);
        p.setDelFlag(0);
        projects.add(p);
        return p;
    }

    private List<TIchCycleLog> logsOf(long taskId) {
        List<TIchCycleLog> mine = new ArrayList<>();
        for (TIchCycleLog l : logs) {
            if (l.getTaskId() != null && l.getTaskId() == taskId) {
                mine.add(l);
            }
        }
        return mine;
    }

    @BeforeEach
    public void setUp() {
        rows.clear();
        logs.clear();
        projects.clear();
        project("XM00", 0);
        project("XM01", 1);

        when(taskMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(rows));
        when(taskMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TIchCycleTask r : rows) {
                if (r.getId().equals(id)) {
                    return r;
                }
            }
            return null;
        });
        when(taskMapper.updateById(any())).thenAnswer(inv -> {
            TIchCycleTask patch = inv.getArgument(0);
            for (TIchCycleTask r : rows) {
                if (r.getId().equals(patch.getId())) {
                    if (patch.getStatus() != null) {
                        r.setStatus(patch.getStatus());
                    }
                    if (patch.getDoneAt() != null) {
                        r.setDoneAt(patch.getDoneAt());
                    }
                    if (patch.getFailReason() != null) {
                        r.setFailReason(patch.getFailReason());
                    }
                    if (patch.getUpdateTime() != null) {
                        r.setUpdateTime(patch.getUpdateTime());
                    }
                    return 1;
                }
            }
            return 0;
        });
        when(logMapper.insert(any())).thenAnswer(inv -> {
            TIchCycleLog l = inv.getArgument(0);
            l.setId(logSeq.getAndIncrement());
            logs.add(l);
            return 1;
        });
        when(projectMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(projects));
        when(noticeSender.sendSiteNotice(any())).thenReturn(true);
        when(noticeSender.sendSms(any())).thenReturn(true);

        service.setCycleRuler(CycleRuler.of(
                LocalTime.of(22, 0), LocalTime.of(7, 0),
                new HashSet<>(Arrays.asList(
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 2),
                        LocalDate.of(2026, 10, 3))),
                new HashSet<>(java.util.Collections.singletonList(LocalDate.of(2026, 10, 9)))));
    }

    @Test
    public void 老方法收什么给什么原样不动() {
        TIchCycleTask r = row(1, "2026-10-04 18:00:00", 3);
        assertSame(r, service.selectTIchCycleTaskById(1L));
        // 老捞法：只问 due_at 在不在 at 之前，其余一概不问
        List<TIchCycleTask> due = service.listDue(dt("2026-10-05 10:00:00"));
        assertEquals(1, due.size());
        assertEquals(1, service.runOnce(dt("2026-10-05 10:00:00")));
    }

    @Test
    public void 够不上日子的这一轮不碰() {
        TIchCycleTask r = row(1, "2026-10-20 18:00:00", 3);
        CycleRoundReport report = service.runRound(dt("2026-10-05 10:00:00"));
        assertEquals(0, report.getPicked());
        assertEquals(0, report.getDone());
        assertEquals(0, report.getFailed());
        assertEquals(1, report.getPending());
        assertEquals(CycleRoundReport.MSG_NONE_DUE, report.getMessage());
        assertEquals(0, r.getStatus());
        assertTrue(logs.isEmpty());
        verify(noticeSender, never()).sendSiteNotice(any());
        verify(noticeSender, never()).sendSms(any());
    }

    @Test
    public void 到点的一行办妥_两个痕迹同一笔落库() {
        TIchCycleTask r = row(1, "2026-10-08 18:00:00", 5);
        Date at = dt("2026-10-05 10:00:00");
        CycleRoundReport report = service.runRound(at);

        assertEquals(1, report.getPicked());
        assertEquals(1, report.getDone());
        assertEquals(0, report.getFailed());
        assertEquals(0, report.getPending());
        assertNull(report.getMessage());
        // 两个痕迹：去向翻办妥 + 办妥那一刻，只落其一都不算完事
        assertEquals(1, r.getStatus());
        assertEquals(at, r.getDoneAt());
        // 两路各记各的
        List<TIchCycleLog> mine = logsOf(1L);
        assertEquals(2, mine.size());
        assertTrue(mine.stream().anyMatch(l -> TIchCycleTaskServiceImpl.ACTION_SITE_MSG.equals(l.getAction())));
        assertTrue(mine.stream().anyMatch(l -> TIchCycleTaskServiceImpl.ACTION_SMS.equals(l.getAction())));
        CycleRowOutcome outcome = report.getRows().get(0);
        assertEquals(CycleRowOutcome.OUTCOME_DONE, outcome.getOutcome());
        assertTrue(outcome.isSiteMsgSent());
        assertTrue(outcome.isSmsSent());
    }

    @Test
    public void 压着的那一段手里有已到点的也不动() {
        TIchCycleTask r = row(1, "2026-09-30 18:00:00", 3);   // 已越过该出手那一日
        // 法定节假日里：不动，等下一轮开口
        CycleRoundReport report = service.runRound(dt("2026-10-01 10:00:00"));
        assertTrue(report.isPressed());
        assertEquals(0, report.getPicked());
        assertEquals(1, report.getPending());
        assertEquals(CycleRoundReport.MSG_NONE_DUE, report.getMessage());
        assertEquals(0, r.getStatus());
        assertTrue(logs.isEmpty());

        // 省里统一停办公的那一日：照样不动
        CycleRoundReport report2 = service.runRound(dt("2026-10-09 10:00:00"));
        assertTrue(report2.isPressed());
        assertEquals(0, r.getStatus());

        // 夜里那一段：也不动
        CycleRoundReport report3 = service.runRound(dt("2026-10-05 23:30:00"));
        assertTrue(report3.isPressed());
        assertEquals(0, r.getStatus());
        assertTrue(logs.isEmpty());
    }

    @Test
    public void 本轮办妥过的下一轮不许再当候办捞出来() {
        row(1, "2026-10-08 18:00:00", 5);
        CycleRoundReport first = service.runRound(dt("2026-10-05 10:00:00"));
        assertEquals(1, first.getDone());

        CycleRoundReport second = service.runRound(dt("2026-10-06 10:00:00"));
        assertEquals(0, second.getPicked());
        assertEquals(0, second.getDone());
        assertEquals(0, second.getPending());
        assertEquals(CycleRoundReport.MSG_NONE_DUE, second.getMessage());
        // 两路留痕仍只有第一轮那两笔
        assertEquals(2, logsOf(1L).size());
    }

    @Test
    public void 同一瞬间两条一起到_各归各处理() {
        TIchCycleTask a = row(1, "2026-10-08 18:00:00", 5);
        TIchCycleTask b = row(2, "2026-10-08 18:00:00", 5);
        CycleRoundReport report = service.runRound(dt("2026-10-05 10:00:00"));
        // 既不合成一条也不许漏掉一条
        assertEquals(2, report.getPicked());
        assertEquals(2, report.getDone());
        assertEquals(1, a.getStatus());
        assertEquals(1, b.getStatus());
        assertEquals(2, logsOf(1L).size());
        assertEquals(2, logsOf(2L).size());
    }

    @Test
    public void 项目早已从名录上销号_就地改判催不动() {
        TIchCycleTask r = row(1, "2026-10-08 18:00:00", 5);
        r.setTargetCode("XM01");
        CycleRoundReport report = service.runRound(dt("2026-10-05 10:00:00"));

        assertEquals(1, report.getFailed());
        assertEquals(0, report.getDone());
        assertEquals(2, r.getStatus());
        assertEquals(TIchCycleTaskServiceImpl.REASON_PROJECT_DELISTED, r.getFailReason());
        // 这一行不藏：档里照旧列着（不删），只是本轮不再对它动手
        assertEquals(0, r.getDelFlag());
        assertTrue(logs.isEmpty());
        verify(noticeSender, never()).sendSiteNotice(any());
        verify(noticeSender, never()).sendSms(any());
    }

    @Test
    public void 联系人那一栏空着_就地改判催不动() {
        TIchCycleTask r = row(1, "2026-10-08 18:00:00", 5);
        r.setContact("   ");
        CycleRoundReport report = service.runRound(dt("2026-10-05 10:00:00"));

        assertEquals(1, report.getFailed());
        assertEquals(2, r.getStatus());
        assertEquals(TIchCycleTaskServiceImpl.REASON_CONTACT_EMPTY, r.getFailReason());
        assertTrue(logs.isEmpty());
        verify(noticeSender, never()).sendSms(any());
    }

    @Test
    public void 别条行子受阻不牵连其余() {
        TIchCycleTask a = row(1, "2026-10-08 18:00:00", 5);
        TIchCycleTask b = row(2, "2026-10-08 18:00:00", 5);
        when(noticeSender.sendSiteNotice(any())).thenAnswer(inv -> {
            TIchCycleTask row = inv.getArgument(0);
            if (row.getId() == 1L) {
                throw new RuntimeException("站内网关超时");
            }
            return true;
        });

        CycleRoundReport report = service.runRound(dt("2026-10-05 10:00:00"));
        // 一条卡住，剩下的仍要一行一行走到底
        assertEquals(1, report.getDone());
        assertEquals(0, a.getStatus());
        assertEquals(1, b.getStatus());
        assertEquals(1, report.getPending());
    }

    @Test
    public void 到了该出手那日还没送出去_撤回另记一笔不盖旧字() {
        TIchCycleTask r = row(1, "2026-10-04 18:00:00", 3);
        // 上一回真办成的那一刻（旧字，撤回那笔盖不住它）
        r.setDoneAt(dt("2025-10-08 10:00:00"));
        Date dueAt = r.getDueAt();
        Date at = dt("2026-10-05 10:00:00");

        CycleRoundReport report = service.runRound(at);
        assertEquals(1, report.getFailed());
        assertEquals(2, r.getStatus());
        assertEquals(TIchCycleTaskServiceImpl.REASON_OVERDUE_WITHDRAWN, r.getFailReason());
        // 撤回另记一笔，事后查得到它的存在
        List<TIchCycleLog> mine = logsOf(1L);
        assertEquals(1, mine.size());
        assertEquals(TIchCycleTaskServiceImpl.ACTION_WITHDRAW, mine.get(0).getAction());
        assertEquals(at, mine.get(0).getActionTime());
        // 它盖不住两个地方旧字：头回定下的那一日，和上一回真办成的那一刻
        assertEquals(dueAt, r.getDueAt());
        assertEquals(dt("2025-10-08 10:00:00"), r.getDoneAt());
        // 撤回的一行不再递话
        verify(noticeSender, never()).sendSiteNotice(any());
        verify(noticeSender, never()).sendSms(any());
    }

    @Test
    public void 三个数同源于一次点算() {
        row(1, "2026-10-08 18:00:00", 5);                 // 本轮办妥
        TIchCycleTask b = row(2, "2026-10-08 18:00:00", 5); // 本轮催不动（联系人空缺）
        b.setContact(null);
        row(3, "2026-10-20 18:00:00", 3);                 // 够不上日子，还欠看
        TIchCycleTask d = row(4, "2026-10-08 18:00:00", 5); // 上一轮已办妥，不再捞
        d.setStatus(1);
        d.setDoneAt(dt("2026-10-01 09:00:00"));

        CycleRoundReport report = service.runRound(dt("2026-10-05 10:00:00"));
        assertEquals(2, report.getPicked());
        assertEquals(1, report.getDone());
        assertEquals(1, report.getFailed());
        assertEquals(1, report.getPending());
        assertNull(report.getMessage());
        assertEquals(2, report.getRows().size());
    }

    @Test
    public void 两路各记各的_手机那一路不算替站内那一路交差() {
        row(1, "2026-10-08 18:00:00", 5);
        row(2, "2026-10-08 18:00:00", 5);
        // 1号只通站内，2号只通手机
        when(noticeSender.sendSiteNotice(any())).thenAnswer(inv -> ((TIchCycleTask) inv.getArgument(0)).getId() == 1L);
        when(noticeSender.sendSms(any())).thenAnswer(inv -> ((TIchCycleTask) inv.getArgument(0)).getId() == 2L);

        CycleRoundReport report = service.runRound(dt("2026-10-05 10:00:00"));
        assertEquals(2, report.getDone());

        List<TIchCycleLog> one = logsOf(1L);
        assertEquals(1, one.size());
        assertEquals(TIchCycleTaskServiceImpl.ACTION_SITE_MSG, one.get(0).getAction());

        List<TIchCycleLog> two = logsOf(2L);
        assertEquals(1, two.size());
        assertEquals(TIchCycleTaskServiceImpl.ACTION_SMS, two.get(0).getAction());

        CycleRowOutcome o1 = report.getRows().get(0);
        CycleRowOutcome o2 = report.getRows().get(1);
        assertTrue(o1.isSiteMsgSent());
        assertFalse(o1.isSmsSent());
        assertFalse(o2.isSiteMsgSent());
        assertTrue(o2.isSmsSent());
    }

    @Test
    public void 查单与回写共用同一把尺() {
        row(1, "2026-10-08 18:00:00", 5);   // 到点
        row(2, "2026-10-20 18:00:00", 3);   // 够不上
        row(3, "2026-10-04 18:00:00", 3);   // 已越过
        Date at = dt("2026-10-05 10:00:00");

        List<TIchCycleTask> queried = service.listRoundDue(at);
        assertEquals(2, queried.size());

        CycleRoundReport report = service.runRound(at);
        assertEquals(queried.size(), report.getPicked());
        for (CycleRowOutcome o : report.getRows()) {
            assertTrue(queried.stream().anyMatch(r -> r.getId().equals(o.getTaskId())));
        }
    }

    @Test
    public void 一个都没挑着_本轮无到期是正常收尾() {
        CycleRoundReport report = service.runRound(dt("2026-10-05 10:00:00"));
        assertEquals(0, report.getPicked());
        assertEquals(0, report.getDone());
        assertEquals(0, report.getFailed());
        assertEquals(0, report.getPending());
        assertEquals(CycleRoundReport.MSG_NONE_DUE, report.getMessage());
        assertFalse(report.isPressed());
    }
}
