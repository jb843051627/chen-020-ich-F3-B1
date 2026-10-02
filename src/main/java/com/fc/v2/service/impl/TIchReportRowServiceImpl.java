package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TIchReportRowMapper;
import com.fc.v2.model.auto.TIchReportRow;
import com.fc.v2.service.ITIchReportRowService;

/**
 * 履职情况报送核收行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TIchReportRowServiceImpl implements ITIchReportRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;
    /** 单行明细码长度上限 */
    private static final int ITEM_CODE_MAX = 64;
    /** 单行数量上限（超出视为录入错误，防止脏数据拉爆统计） */
    private static final java.math.BigDecimal QTY_MAX = new java.math.BigDecimal("999999");

    /** 批次处理留痕（单机内存缓冲，供运维查"这批发过没有"） */
    private final java.util.Map<String, String> batchStats =
            new java.util.concurrent.ConcurrentHashMap<String, String>();

    @javax.annotation.Resource
    private TIchReportRowMapper ichReportRowMapper;

    @Override
    public TIchReportRow selectTIchReportRowById(Long id) {
        return this.ichReportRowMapper.selectById(id);
    }

    /**
     * 整批提交。口径与接口契约一致：
     * 空批次返回 0；超过单批上限返回 -1 且整批不入库；其余逐行校验，
     * 合法行入库、非法行记入失败明细（保留原始行号），返回成功行数。
     * 整批一个事务，中途异常全量回滚，不留半批。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TIchReportRow> rows) {
        // 空批次兜底：契约约定返回 0，不得抛异常中断
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // 单批行数上限：超限整批拒收，契约约定返回 -1
        if (rows.size() > MAX_ROWS) {
            return -1;
        }
        // 一报一个批号，进门就定死：以入参为准，入参缺失才回明细上带的号
        String no = isBlank(batchNo) ? firstBatchNo(rows) : batchNo.trim();
        if (isBlank(no)) {
            // 两头都拿不出批号：无法归档也无法幂等，整批不收
            return 0;
        }
        // 重放判定：该批已落过明细（成功或失败都算），重发不得重复入库，
        // 直接回报库里已有的成功行数，前后两次提交拿到同一个结果
        if (countBatch(no) > 0) {
            noteAfterCommit(no, "重复提交已忽略：批次已落库，未重复入账");
            return countBatchOk(no);
        }
        int ok = 0;
        int fail = 0;
        java.util.Set<Integer> usedRowNos = new java.util.HashSet<Integer>();
        java.util.Set<String> okCodes = new java.util.HashSet<String>();
        for (int i = 0; i < rows.size(); i++) {
            TIchReportRow r = rows.get(i);
            if (r == null) {
                // 空行兜底：跳过，不占行号、不计成功
                continue;
            }
            // 明细码归一：校验、批内去重、落库三处用同一份去空白值，尺度同源
            if (r.getItemCode() != null) {
                r.setItemCode(r.getItemCode().trim());
            }
            // 原始行号归一：调用方给的 Excel 行号优先保留，缺失/冲突才补未占用序号
            int rowNo = normalizeRowNo(r, usedRowNos, i + 1);
            String reason = invalidReason(r);
            if (reason == null && !okCodes.add(r.getItemCode())) {
                // 批内去重：同一码只收第一行，后续重复行记失败明细，不重复落库
                reason = "批内明细码重复：" + r.getItemCode();
            }
            if (reason == null) {
                markOk(r, no, rowNo);
                ok++;
            } else {
                markFail(r, no, rowNo, reason);
                fail++;
            }
        }
        noteAfterCommit(no, "提交完成：成功 " + ok + " 行，失败 " + fail + " 行");
        return ok;
    }

    @Override
    public List<TIchReportRow> listErrors(String batchNo) {
        if (isBlank(batchNo)) {
            return new java.util.ArrayList<TIchReportRow>();
        }
        // 只取该批次的失败明细（成功行从不进失败清单），口径与 submitBatch 的落库口径一致
        List<TIchReportRow> errs = this.ichReportRowMapper.selectList(new QueryWrapper<TIchReportRow>()
                .eq("batch_no", batchNo)
                .eq("status", STATUS_FAIL)
                .eq("del_flag", 0));
        if (errs == null || errs.isEmpty()) {
            return new java.util.ArrayList<TIchReportRow>();
        }
        // 按原始行号升序：运维要按 Excel 行序逐行修，乱序会漏改
        errs.sort(new java.util.Comparator<TIchReportRow>() {
            @Override
            public int compare(TIchReportRow a, TIchReportRow b) {
                int ra = a.getRowNo() == null ? 0 : a.getRowNo().intValue();
                int rb = b.getRowNo() == null ? 0 : b.getRowNo().intValue();
                return ra - rb;
            }
        });
        return errs;
    }
    /** 批次是否已落过明细（成功或失败都算），用于重放判定 */
    private int countBatch(String batchNo) {
        List<TIchReportRow> exist = this.ichReportRowMapper.selectList(
                new QueryWrapper<TIchReportRow>().eq("batch_no", batchNo));
        return exist == null ? 0 : exist.size();
    }

    /** 该批次已落库的成功行数：重放时回报用，成功/失败的划分口径与 listErrors 一致 */
    private int countBatchOk(String batchNo) {
        List<TIchReportRow> exist = this.ichReportRowMapper.selectList(
                new QueryWrapper<TIchReportRow>()
                        .eq("batch_no", batchNo)
                        .eq("status", STATUS_OK)
                        .eq("del_flag", 0));
        return exist == null ? 0 : exist.size();
    }

    /** 入参批号缺失时，回明细行上带的批号（兼容老调用方把号只放在明细上） */
    private static String firstBatchNo(List<TIchReportRow> rows) {
        for (TIchReportRow r : rows) {
            if (r != null && !isBlank(r.getBatchNo())) {
                return r.getBatchNo().trim();
            }
        }
        return null;
    }

    /** 空串判定：批次号/明细码的纯空白串按"缺失"处理 */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 单行校验：返回 null 表示合法；否则返回失败原因（原因写入失败明细备注，便于运维定位） */
    private String invalidReason(TIchReportRow r) {
        if (isBlank(r.getItemCode())) {
            return "明细码为空";
        }
        if (r.getItemCode().trim().length() > ITEM_CODE_MAX) {
            return "明细码超过 " + ITEM_CODE_MAX + " 个字符";
        }
        if (r.getQty() == null) {
            return "数量缺失";
        }
        if (r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return "数量必须大于 0";
        }
        if (r.getQty().compareTo(QTY_MAX) > 0) {
            return "数量超过单行上限 " + QTY_MAX.toPlainString();
        }
        return null;
    }

    /** 原始行号归一：优先保留调用方给的 Excel 行号，缺失/冲突时才补一个未占用序号 */
    private static int normalizeRowNo(TIchReportRow r, java.util.Set<Integer> used, int fallback) {
        Integer given = r.getRowNo();
        if (given != null && given.intValue() > 0 && used.add(given)) {
            return given.intValue();
        }
        int candidate = fallback > 0 ? fallback : 1;
        while (!used.add(Integer.valueOf(candidate))) {
            candidate++;
        }
        return candidate;
    }

    /** 失败行落库：状态置失败 + 原因写进备注，原始行号原样保留 */
    private void markFail(TIchReportRow r, String batchNo, int rowNo, String reason) {
        r.setBatchNo(batchNo);
        r.setRowNo(Integer.valueOf(rowNo));
        r.setStatus(STATUS_FAIL);
        r.setRemark(reason);
        stamp(r);
        this.ichReportRowMapper.insert(r);
    }

    /** 成功行落库：状态置成功，并清掉失败备注（同一实体可能被复用，避免残留上次原因） */
    private void markOk(TIchReportRow r, String batchNo, int rowNo) {
        r.setBatchNo(batchNo);
        r.setRowNo(Integer.valueOf(rowNo));
        r.setStatus(STATUS_OK);
        r.setRemark(null);
        stamp(r);
        this.ichReportRowMapper.insert(r);
    }

    /** 写前盖章：软删标记缺省补 0，更新时间统一由服务端生成 */
    private static void stamp(TIchReportRow r) {
        if (r.getDelFlag() == null) {
            r.setDelFlag(0);
        }
        r.setUpdateTime(new java.util.Date());
    }

    /** 落库留痕联动：库事务落定后才写内存留痕，回滚/超时不会出现"库里没有、痕里说有" */
    private void noteAfterCommit(final String batchNo, final String what) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    note(batchNo, what);
                }
            });
        } else {
            note(batchNo, what);
        }
    }

    /** 整批处理留痕：按批次记录最近一次处理结论（内存缓冲，超过 200 批整体清理） */
    private void note(String batchNo, String what) {
        if (batchStats.size() > 200) {
            batchStats.clear();
        }
        batchStats.put(batchNo, what);
    }

}
