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

    /** 优先级降序；并列按 ruleCode 降序，再并列按启用之日降序（确定性） */
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
            int byCode = cb.compareTo(ca);
            if (byCode != 0) {
                return byCode;
            }
            Date sa = a.getEffStart();
            Date sb = b.getEffStart();
            if (sa == null && sb == null) {
                return 0;
            }
            if (sa == null) {
                return 1;
            }
            if (sb == null) {
                return -1;
            }
            return sb.compareTo(sa);
        }
    };

    @javax.annotation.Resource
    private TIchPreLineMapper ichPreLineMapper;

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）+ 字符串绑定：
     * JDBC 的 serverTimezone 与本机时区不对称，直接把 java.util.Date 作参数会整体偏移，
     * 使边界判定在"切换当日"这类用例上随机错档（实测踩到）。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    public TIchPreLine selectTIchPreLineById(Long id) {
        return this.ichPreLineMapper.selectById(id);
    }

    @Override
    public List<TIchPreLine> listAvailable(Date at) {
        List<TIchPreLine> empty = new java.util.ArrayList<TIchPreLine>();
        if (at == null) {
            return empty;
        }
        // 查单只问一条：这一刻在不在场（未删、未停用、已启用未交棒），
        // 再按让位顺位排开——查单、求值、单条可用、计数全从这一份过，尺度同源
        List<TIchPreLine> all = this.ichPreLineMapper.selectList(
                new QueryWrapper<TIchPreLine>().eq("del_flag", 0));
        List<TIchPreLine> avail = new java.util.ArrayList<TIchPreLine>();
        for (TIchPreLine r : all) {
            if (availableAt(r, at)) {
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
        TIchPreLine rule = findRule(ruleCode, at);
        // 这一刻没有在场版本：按不可用回 0，不许在空规则上解引用
        if (rule == null) {
            return 0;
        }
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        if (input.compareTo(BigDecimal.ZERO) < 0
                || input.compareTo(new BigDecimal("100")) > 0) {
            return 0;
        }
        // 多规则求值取让位顺位最靠前的一条，与列表同一把尺
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
        // 与定位/列表同一份"这一刻在不在场"判定，不许另起一套口径
        return availableAt(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return listAvailable(at).size();
    }

    /**
     * 这条线这一刻在不在场：未删除、未停用、已到启用之日、未到交棒之日（交棒那日不含）。
     * 启用之日空着视为自古在场，交棒之日空着视为一直在场。
     */
    private static boolean availableAt(TIchPreLine r, Date at) {
        if (r == null || at == null) {
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

    private TIchPreLine findRule(String ruleCode, Date at) {
        // 同一代号多个版本：只在这一刻在场的版本里挑，让位顺位最靠前（并列取启用之日最新）的一条
        List<TIchPreLine> hit = this.ichPreLineMapper.selectList(new QueryWrapper<TIchPreLine>()
                .eq("del_flag", 0).eq("rule_code", ruleCode));
        List<TIchPreLine> avail = new java.util.ArrayList<TIchPreLine>();
        for (TIchPreLine r : hit) {
            // 内存里再对一遍代号与在场时刻，口径与 SQL 过滤同源，旁路取数也混不进别条线
            if (ruleCode.equals(r.getRuleCode()) && availableAt(r, at)) {
                avail.add(r);
            }
        }
        if (avail.isEmpty()) {
            return null;
        }
        avail.sort(PRIORITY_THEN_CODE_DESC);
        return avail.get(0);
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

    /** 分档：等于上限取高一档（踩在线上的归入高一线那一档） */
    private int levelOf(TIchPreLine rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) < 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) < 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }
}
