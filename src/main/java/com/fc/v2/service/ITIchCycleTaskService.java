package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TIchCycleTask;
import com.fc.v2.model.custom.cycle.CycleRoundReport;

/**
 * 履约考核到期单 Service接口（scheduling-job 形状：周期执行，无增删改查入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITIchCycleTaskService {

    /** 按主键回查条目 */
    TIchCycleTask selectTIchCycleTaskById(Long id);

    /** 该时刻可处理的条目（执行窗口内 + 到期 + 尚未处理） */
    List<TIchCycleTask> listDue(Date at);

    /** 执行一次，返回**成功条数**；单条失败跳过继续 */
    int runOnce(Date at);

    /**
     * 本轮该出手的条目（新捞法：候办 + 到点含提前量或已越过 + 未压着）。
     * 与 {@link #runRound} 共用同一把工作日尺，压着的那一段一律不捞。
     */
    List<TIchCycleTask> listRoundDue(Date at);

    /**
     * 跑一轮：挑单、逐条办理、落库，屏上三个数（还欠看的/这一轮办妥的/催不动的）
     * 同源于这一次点算；一个都没挑着时回"本轮无到期"，是正常收尾不是故障。
     */
    CycleRoundReport runRound(Date at);
}
