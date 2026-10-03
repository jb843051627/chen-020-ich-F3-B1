package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TIchPreLineMapper;
import com.fc.v2.model.auto.TIchPreLine;
import com.fc.v2.service.ITIchPreLineService;

/**
 * 申报准入门槛线 Service业务层处理（rule-eval 形状）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TIchPreLineServiceImpl implements ITIchPreLineService {

    /** 优先级降序；并列按 ruleCode 降序（确定性） */
    private static final Comparator<TIchPreLine> PRIORITY_THEN_CODE_DESC = new Comparator<TIchPreLine>() {
        @Override
        public int compare(TIchPreLine a, TIchPreLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TIchPreLineMapper ichPreLineMapper;

    /** 分档口径的输入下界（含） */
    private static final BigDecimal INPUT_MIN = BigDecimal.ZERO;

    /** 分档口径的输入上界（含） */
    private static final BigDecimal INPUT_MAX = new BigDecimal("100");

    @Override
    public TIchPreLine selectTIchPreLineById(Long id) {
        return this.ichPreLineMapper.selectById(id);
    }

    @Override
    public List<TIchPreLine> listAvailable(Date at) {
        if (at == null) {
            return new java.util.ArrayList<TIchPreLine>();
        }
        // 覆盖范围只此一处定：未删 + 在场 + 落在 [启用之日, 交棒之日) 内；
        // 单规则求值 / 多规则求头 / 是否可用 / 计数四个通道全从这里过，不再各判各的
        List<TIchPreLine> avail = new java.util.ArrayList<TIchPreLine>();
        for (TIchPreLine r : this.ichPreLineMapper
                .selectList(new QueryWrapper<TIchPreLine>().eq("del_flag", 0))) {
            if (activeAt(r, at)) {
                avail.add(r);
            }
        }
        avail.sort(PRIORITY_THEN_CODE_DESC);
        return avail;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        if (!withinInput(input)) {
            return 0;
        }
        TIchPreLine rule = findRule(ruleCode, at);
        // 该时刻没有生效版本：按不可用回 0，不许拿 null 去解引用中断调用
        return rule == null ? 0 : levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        if (!withinInput(input)) {
            return 0;
        }
        List<TIchPreLine> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        return levelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TIchPreLine r = this.ichPreLineMapper.selectById(id);
        // 与列表/定位同一个覆盖口径：存在且此刻在场生效才算可用
        return r != null && activeAt(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return listAvailable(at).size();
    }

    /**
     * 一条门槛线在 at 时刻是否在场生效——四个通道共用的唯一覆盖谓词。
     * 未删、情形为在场（空值按在场）、已到启用之日（含）、未到交棒之日（不含）。
     */
    private boolean activeAt(TIchPreLine r, Date at) {
        if (r == null) {
            return false;
        }
        if (r.getDelFlag() != null && r.getDelFlag() != 0) {
            return false;
        }
        if (r.getStatus() != null && r.getStatus() != 0) {
            return false;
        }
        if (r.getEffStart() != null && at.before(r.getEffStart())) {
            return false;
        }
        if (r.getEffEnd() != null && !at.before(r.getEffEnd())) {
            return false;
        }
        return true;
    }

    /** 输入是否落在分档口径 [0,100]：单规则与多规则两处同一把尺 */
    private boolean withinInput(BigDecimal input) {
        return input.compareTo(INPUT_MIN) >= 0 && input.compareTo(INPUT_MAX) <= 0;
    }

    private TIchPreLine findRule(String ruleCode, Date at) {
        TIchPreLine hit = null;
        // 同一代号的在场生效版本里，交棒之日最晚（无交棒日视为仍有效、优先）者为准；
        // 覆盖判定与 listAvailable 同源，停用的、过了交棒日的、没到启用日的都不取
        for (TIchPreLine r : listAvailable(at)) {
            if (ruleCode.equals(r.getRuleCode())) {
                if (hit == null) {
                    hit = r;
                } else if (hit.getEffEnd() == null) {
                    // 旧选已无交棒日，仍有效，保持
                } else if (r.getEffEnd() == null || r.getEffEnd().after(hit.getEffEnd())) {
                    hit = r;
                }
            }
        }
        return hit;
    }

    /** 阈值解析：档案为主、明细覆盖；两者皆缺返回 null（调用方按不可用处理），精度统一 2 位 */
    private BigDecimal resolveLimit(TIchPreLine r, int tier) {
        if (tier == 1) {
            return r.getTh1Max();
        }
        if (tier == 2) {
            return r.getTh2Max();
        }
        return r.getTh3Max();
    }

    /** 档案级默认阈值（明细未维护时的回退来源） */
    private BigDecimal defaultThreshold(TIchPreLine r, int tier) {
        return r.getTh1Max();
    }

    /** 分档：等于上限取高一档 */
    private int levelOf(TIchPreLine rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) <= 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) <= 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) <= 0) {
            return 3;
        }
        return 4;
    }
}
