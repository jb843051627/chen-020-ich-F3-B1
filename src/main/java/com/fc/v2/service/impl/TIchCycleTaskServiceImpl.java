package com.fc.v2.service.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TIchCycleLogMapper;
import com.fc.v2.mapper.auto.TIchCycleTaskMapper;
import com.fc.v2.mapper.auto.TIchProjectMapper;
import com.fc.v2.model.auto.TIchCycleLog;
import com.fc.v2.model.auto.TIchCycleTask;
import com.fc.v2.model.auto.TIchProject;
import com.fc.v2.model.custom.cycle.CycleRoundReport;
import com.fc.v2.model.custom.cycle.CycleRowOutcome;
import com.fc.v2.model.custom.cycle.CycleRuler;
import com.fc.v2.service.ITIchCycleTaskService;
import com.fc.v2.service.IchCycleNoticeSender;

/**
 * 履约考核到期单 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * <p>老方法 {@link #selectTIchCycleTaskById}、{@link #listDue}、{@link #runOnce}
 * 收什么给什么，参数与返回一律不碰。新捞法另起 {@link #listRoundDue} 与
 * {@link #runRound}：挑单、逐条办理、落库都在本轮算过再落，屏上三个数同源于一次点算。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TIchCycleTaskServiceImpl implements ITIchCycleTaskService {

    private static final int WIN_FROM = 2;
    private static final int WIN_TO = 5;
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    private static final Logger log = LoggerFactory.getLogger(TIchCycleTaskServiceImpl.class);

    /** 催不动缘由：项目早已从名录上销号 */
    public static final String REASON_PROJECT_DELISTED = "项目已销号";

    /** 催不动缘由：联系人那一栏空着几季没人补 */
    public static final String REASON_CONTACT_EMPTY = "联系人空缺";

    /** 催不动缘由：到了该出手那日还没送出去，话已撤回 */
    public static final String REASON_OVERDUE_WITHDRAWN = "逾期未送出";

    /** 留痕动作：站内送达县文旅局那一条 */
    public static final String ACTION_SITE_MSG = "SITE_MSG";

    /** 留痕动作：手机短信送达传承人那一条 */
    public static final String ACTION_SMS = "SMS";

    /** 留痕动作：逾期未送出，话已撤回 */
    public static final String ACTION_WITHDRAW = "WITHDRAW";

    @javax.annotation.Resource
    private TIchCycleTaskMapper ichCycleTaskMapper;

    @javax.annotation.Resource
    private TIchCycleLogMapper ichCycleLogMapper;

    @javax.annotation.Resource
    private TIchProjectMapper ichProjectMapper;

    /** 两路递送口：不接线时只留痕不真递 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private IchCycleNoticeSender ichCycleNoticeSender;

    /** 唯一一把工作日尺：查单与回写共用；法定节假日与省停办日由运维按年注入 */
    private CycleRuler cycleRuler = CycleRuler.defaults();

    /** 换一把配好节假日与省停办日的尺（空着则退回默认尺） */
    public void setCycleRuler(CycleRuler cycleRuler) {
        this.cycleRuler = cycleRuler == null ? CycleRuler.defaults() : cycleRuler;
    }

    @Override
    public TIchCycleTask selectTIchCycleTaskById(Long id) {
        return this.ichCycleTaskMapper.selectById(id);
    }

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    public List<TIchCycleTask> listDue(Date at) {
        List<TIchCycleTask> all = this.ichCycleTaskMapper.selectList(new QueryWrapper<TIchCycleTask>());
        List<TIchCycleTask> due = new java.util.ArrayList<TIchCycleTask>();
        if (at == null) {
            return due;
        }
        for (TIchCycleTask r : all) {
            // 只捞仍候办、未删的；到没到点这一头要掐进踩点那一条：
            // due_at 与 at 同一刻也算到点（compareTo<=0），旧写法用 < 把踩点行漏掉了
            if (CycleRuler.isPendingRow(r)
                    && r.getDueAt() != null && ts(r.getDueAt()).compareTo(ts(at)) <= 0) {
                due.add(r);
            }
        }
        return due;
    }

    @Override
    public int runOnce(Date at) {
        List<TIchCycleTask> due = listDue(at);
        // 空集合兜底：一个都没捞着直接回 0，旧写法 due.get(0) 在空单上径直越界抛异常。
        // 结算只问"这一条到没到该出手那一日"（listDue 已把踩点行、候办、未删都判过），
        // 到了就算数——"可提前几日开口"(amount) 只许用来定窗口，不能拿来当结算依据；
        // 旧写法去解引用 amount，缺值一行就 NPE 中断整轮，错把开口量当金额。
        return due.size();
    }

    @Override
    public List<TIchCycleTask> listRoundDue(Date at) {
        return this.cycleRuler.pick(loadAll(), at);
    }

    @Override
    public CycleRoundReport runRound(Date at) {
        // 一档只装载这一回：挑单、办理、点算同出这一批，屏上的数与档里的数不分两回取
        List<TIchCycleTask> all = loadAll();
        CycleRoundReport report = new CycleRoundReport(at);
        report.setPressed(this.cycleRuler.isPressed(at));

        // 与查单同一把尺：压着的那一段一律不捞，够不上日子的这一轮不碰
        List<TIchCycleTask> picked = this.cycleRuler.pick(all, at);
        report.setPicked(picked.size());

        // 项目底册一轮只取一回：所拴项目是不是早已从名录上销号，顺着它看
        Map<String, TIchProject> projects = loadProjectsBySiteNo();
        int done = 0;
        int failed = 0;
        for (TIchCycleTask row : picked) {
            try {
                CycleRowOutcome outcome = processRow(row, at, projects);
                if (outcome == null) {
                    continue;
                }
                report.getRows().add(outcome);
                if (CycleRowOutcome.OUTCOME_DONE.equals(outcome.getOutcome())) {
                    done++;
                } else if (CycleRowOutcome.OUTCOME_FAILED.equals(outcome.getOutcome())) {
                    failed++;
                }
            } catch (Exception e) {
                // 同一轮里别条行子受阻，也不牵连其余，剩下的仍要一行一行走到底
                log.warn("到期单 {} 本轮办理受阻，跳过该行继续走: {}", row.getId(), e.getMessage());
            }
        }
        report.setDone(done);
        report.setFailed(failed);
        // 还欠看的：同一批档里一条一条点出来点齐（办理已回写到这批行上）
        report.setPending(countPending(all));
        if (picked.isEmpty()) {
            // 一个都没挑着：正常收尾，不是故障
            report.setMessage(CycleRoundReport.MSG_NONE_DUE);
        }
        return report;
    }

    /**
     * 办理本轮捞出的一行。落点三种：办妥（两个痕迹同一笔落库）、
     * 催不动（缘由顺着写进同一条里）、本轮不算（落笔瞬间已被别人推动，等下一轮）。
     */
    private CycleRowOutcome processRow(TIchCycleTask row, Date at, Map<String, TIchProject> projects) {
        // 到了该出手那日还没送出去的：把话撤回、这一行改判催不动。
        // 撤回另记一笔，事后查得到它的存在；它盖不住 due_at 与 done_at 两处旧字。
        if (this.cycleRuler.isOverdue(at, row.getDueAt())) {
            insertLog(row, ACTION_WITHDRAW, "越过该出手那一日仍未送出，话已撤回", at);
            if (!failRow(row, REASON_OVERDUE_WITHDRAWN, at)) {
                return null;
            }
            return outcomeOf(row, CycleRowOutcome.OUTCOME_FAILED, REASON_OVERDUE_WITHDRAWN);
        }

        // 项目早已从名录上销号：就地改判催不动，缘由顺着写进同一条里，事后按缘由挑得出来
        TIchProject project = row.getTargetCode() == null ? null : projects.get(row.getTargetCode());
        if (project != null && project.getStatus() != null && project.getStatus() == 1) {
            if (!failRow(row, REASON_PROJECT_DELISTED, at)) {
                return null;
            }
            return outcomeOf(row, CycleRowOutcome.OUTCOME_FAILED, REASON_PROJECT_DELISTED);
        }

        // 联系人那一栏空着几季没人补：就地改判催不动
        if (row.getContact() == null || row.getContact().trim().isEmpty()) {
            if (!failRow(row, REASON_CONTACT_EMPTY, at)) {
                return null;
            }
            return outcomeOf(row, CycleRowOutcome.OUTCOME_FAILED, REASON_CONTACT_EMPTY);
        }

        // 催办两头递：县文旅局收站内那一条，传承人本人收手机短信息那一条。
        // 哪一路送到就单独记哪一路，手机那一路不算替站内那一路交了差。
        boolean siteOk = this.ichCycleNoticeSender == null || this.ichCycleNoticeSender.sendSiteNotice(row);
        if (siteOk) {
            insertLog(row, ACTION_SITE_MSG, "站内送达县文旅局", at);
        }
        boolean smsOk = this.ichCycleNoticeSender == null || this.ichCycleNoticeSender.sendSms(row);
        if (smsOk) {
            insertLog(row, ACTION_SMS, "手机短信送达传承人 " + row.getContact(), at);
        }

        // 一行算办妥要留两个地方痕迹：去向从候办翻成办妥，紧跟着把办妥的那一刻记上，
        // 只落其一都不算这一行完事——两个痕迹同一笔落库
        TIchCycleTask patch = new TIchCycleTask();
        patch.setId(row.getId());
        patch.setStatus(CycleRuler.STATUS_DONE);
        patch.setDoneAt(at);
        patch.setUpdateTime(at);
        if (this.ichCycleTaskMapper.updateById(patch) == 0) {
            return null;
        }
        row.setStatus(CycleRuler.STATUS_DONE);
        row.setDoneAt(at);
        CycleRowOutcome outcome = outcomeOf(row, CycleRowOutcome.OUTCOME_DONE, null);
        outcome.setSiteMsgSent(siteOk);
        outcome.setSmsSent(smsOk);
        return outcome;
    }

    /** 就地改判催不动：去向翻催不动，卡在哪一处缘由顺着写进同一条里 */
    private boolean failRow(TIchCycleTask row, String reason, Date at) {
        TIchCycleTask patch = new TIchCycleTask();
        patch.setId(row.getId());
        patch.setStatus(CycleRuler.STATUS_FAIL);
        patch.setFailReason(reason);
        patch.setUpdateTime(at);
        if (this.ichCycleTaskMapper.updateById(patch) == 0) {
            return false;
        }
        row.setStatus(CycleRuler.STATUS_FAIL);
        row.setFailReason(reason);
        return true;
    }

    /** 留痕一笔：两路送达各记各的，撤回另记一笔；旧轮痕迹一笔不抹 */
    private void insertLog(TIchCycleTask row, String action, String content, Date at) {
        TIchCycleLog entry = new TIchCycleLog();
        entry.setTaskId(row.getId());
        entry.setAction(action);
        entry.setContent(content);
        entry.setActionTime(at);
        entry.setDelFlag(0);
        this.ichCycleLogMapper.insert(entry);
    }

    private CycleRowOutcome outcomeOf(TIchCycleTask row, String outcome, String reason) {
        CycleRowOutcome o = new CycleRowOutcome();
        o.setTaskId(row.getId());
        o.setItemNo(row.getItemNo());
        o.setOutcome(outcome);
        o.setReason(reason);
        return o;
    }

    private List<TIchCycleTask> loadAll() {
        return this.ichCycleTaskMapper.selectList(new QueryWrapper<TIchCycleTask>());
    }

    private Map<String, TIchProject> loadProjectsBySiteNo() {
        Map<String, TIchProject> bySiteNo = new HashMap<>();
        List<TIchProject> projects = this.ichProjectMapper.selectList(new QueryWrapper<TIchProject>()
                .eq("del_flag", 0));
        for (TIchProject p : projects) {
            if (p.getSiteNo() != null) {
                bySiteNo.put(p.getSiteNo(), p);
            }
        }
        return bySiteNo;
    }

    private static int countPending(List<TIchCycleTask> all) {
        int n = 0;
        for (TIchCycleTask row : all) {
            if (CycleRuler.isPendingRow(row)) {
                n++;
            }
        }
        return n;
    }
}
