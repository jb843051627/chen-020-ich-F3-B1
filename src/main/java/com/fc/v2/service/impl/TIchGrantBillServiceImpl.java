package com.fc.v2.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TIchGrantBillMapper;
import com.fc.v2.mapper.auto.TIchGrantSignMapper;
import com.fc.v2.model.auto.TIchGrantBill;
import com.fc.v2.model.auto.TIchGrantSign;
import com.fc.v2.model.custom.grant.GrantBillView;
import com.fc.v2.model.custom.grant.GrantChain;
import com.fc.v2.model.custom.grant.GrantFlowException;
import com.fc.v2.model.custom.grant.GrantLedger;
import com.fc.v2.model.custom.grant.GrantReview;
import com.fc.v2.service.ITIchGrantBillService;

/**
 * 工坊认定核准单 Service业务层处理（approval-chain 形状：县—市—省三级签批）。
 *
 * <p>认定只发生在 {@link #sign} 这一个落名入口里：前端递上来的档编号一律当意向，
 * 当前停在第几档、后一档收不收，全听 {@link GrantChain} 顺着落名记录的回算。
 * 老的查档次方法 {@link #selectTIchGrantBillById} 收什么给什么，原样不动。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TIchGrantBillServiceImpl implements ITIchGrantBillService {

    /** 核准情形：已挪回（挪回一刻的落档，下一笔落名后仍回到"在核"） */
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TIchGrantBillMapper ichGrantBillMapper;

    @javax.annotation.Resource
    private TIchGrantSignMapper ichGrantSignMapper;

    @Override
    public TIchGrantBill selectTIchGrantBillById(Long id) {
        return this.ichGrantBillMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrantBillView sign(Long id, Integer intendedNode, String approver, String comment) {
        TIchGrantBill bill = requireBill(id);
        String person = approver == null ? null : approver.trim();
        if (person == null || person.isEmpty()) {
            throw new GrantFlowException(GrantFlowException.Reason.BAD_APPROVER, "落名同志不能为空");
        }
        // 省厅点头之后这张单便就此锁上，谁也别想再补一个名
        if (isLocked(bill)) {
            throw new GrantFlowException(GrantFlowException.Reason.LOCKED,
                    "核准单已由省文旅认下锁死，不能再补名");
        }

        List<TIchGrantSign> signs = loadSigns(id);
        GrantChain.State before = GrantChain.evaluate(bill, signs);
        int current = before.getCurrentNode();

        // 屏上带出的档编号与入口答复相左时，一概按入口的来——越档先落那一笔不认
        if (intendedNode != null && intendedNode.intValue() != current) {
            throw new GrantFlowException(GrantFlowException.Reason.NODE_MISMATCH,
                    "意向档口 " + intendedNode + " 与单据当前所在档口 " + current
                            + " 相左，该笔按没落算");
        }
        int round = before.getCurrentRound();
        for (TIchGrantSign s : signs) {
            if (person.equals(s.getApprover())
                    && s.getNodeNo() != null && s.getNodeNo() == current
                    && s.getRoundNo() != null && s.getRoundNo() == round) {
                throw new GrantFlowException(GrantFlowException.Reason.ALREADY_SIGNED,
                        person + " 已在本档本轮落过名，不许重复落名");
            }
        }

        TIchGrantSign row = new TIchGrantSign();
        row.setBillId(id);
        row.setNodeNo(current);
        row.setRoundNo(round);
        row.setApprover(person);
        row.setComment(comment);
        row.setSignTime(new Date());
        row.setDelFlag(0);
        this.ichGrantSignMapper.insert(row);

        // 落笔之后重新回算：这一档凑没凑齐、整张单停到哪一档，不由格子里的数说了算
        signs.add(row);
        GrantChain.State after = GrantChain.evaluate(bill, signs);
        int nextNode = after.getCurrentNode();
        boolean recognized = after.isRecognized();

        TIchGrantBill patch = new TIchGrantBill();
        patch.setNodeNo(nextNode);
        patch.setNeedCount(GrantChain.requiredCount(nextNode));
        patch.setSignMode(GrantChain.signMode(nextNode));
        patch.setSignCount(after.node(nextNode).getSignedCount());
        patch.setStatus(recognized ? GrantChain.STATUS_PASS : GrantChain.STATUS_RUNNING);
        patch.setUpdateTime(new Date());

        UpdateWrapper<TIchGrantBill> uw = new UpdateWrapper<>();
        uw.eq("id", id)
                .eq("node_no", current)
                .eq("round_no", round)
                .ne("status", GrantChain.STATUS_PASS);
        if (this.ichGrantBillMapper.update(patch, uw) == 0) {
            // 落笔瞬间已被别人推动：本次插入与更新同事务回滚，让调用方按最新回算重试
            throw new GrantFlowException(GrantFlowException.Reason.CONCURRENT_CHANGED,
                    "单据刚被别人推动，请按最新档口重新落名");
        }
        bill.setNodeNo(nextNode);
        bill.setNeedCount(patch.getNeedCount());
        bill.setSignMode(patch.getSignMode());
        bill.setSignCount(patch.getSignCount());
        bill.setStatus(patch.getStatus());
        return GrantChain.buildView(bill, signs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrantBillView sendBack(Long id, String operator, String comment) {
        TIchGrantBill bill = requireBill(id);
        if (isLocked(bill)) {
            throw new GrantFlowException(GrantFlowException.Reason.LOCKED,
                    "核准单已由省文旅认下锁死，不能再退回");
        }

        List<TIchGrantSign> signs = loadSigns(id);
        GrantChain.State before = GrantChain.evaluate(bill, signs);
        int current = before.getCurrentNode();
        if (current == 0) {
            throw new GrantFlowException(GrantFlowException.Reason.NO_PREV_NODE,
                    "已在头一档（县文旅），再无紧挨的上一档可退");
        }

        int target = current - 1;
        int newRound = before.getCurrentRound() + 1;

        // 只挪一格、轮次加一；早先各档落名一笔不抹，重新起签从被退回那一档往上续
        TIchGrantBill patch = new TIchGrantBill();
        patch.setNodeNo(target);
        patch.setRoundNo(newRound);
        patch.setNeedCount(GrantChain.requiredCount(target));
        patch.setSignMode(GrantChain.signMode(target));
        patch.setSignCount(0);
        patch.setStatus(STATUS_VETO);
        patch.setRemark(comment);
        patch.setUpdateBy(operator);
        patch.setUpdateTime(new Date());

        UpdateWrapper<TIchGrantBill> uw = new UpdateWrapper<>();
        uw.eq("id", id)
                .eq("node_no", current)
                .eq("round_no", before.getCurrentRound())
                .ne("status", GrantChain.STATUS_PASS);
        if (this.ichGrantBillMapper.update(patch, uw) == 0) {
            throw new GrantFlowException(GrantFlowException.Reason.CONCURRENT_CHANGED,
                    "单据刚被别人推动，请按最新档口重新办理");
        }
        bill.setNodeNo(target);
        bill.setRoundNo(newRound);
        bill.setNeedCount(patch.getNeedCount());
        bill.setSignMode(patch.getSignMode());
        bill.setSignCount(0);
        bill.setStatus(STATUS_VETO);
        bill.setRemark(comment);
        return GrantChain.buildView(bill, signs);
    }

    @Override
    public GrantBillView view(Long id) {
        TIchGrantBill bill = requireBill(id);
        return GrantChain.buildView(bill, loadSigns(id));
    }

    @Override
    public GrantReview review(Long id) {
        TIchGrantBill bill = requireBill(id);
        return GrantChain.buildReview(bill, loadSigns(id));
    }

    @Override
    public GrantLedger ledger(Long id) {
        TIchGrantBill bill = requireBill(id);
        return GrantChain.buildLedger(bill, loadSigns(id));
    }

    private TIchGrantBill requireBill(Long id) {
        TIchGrantBill bill = this.ichGrantBillMapper.selectById(id);
        if (bill == null) {
            throw new GrantFlowException(GrantFlowException.Reason.NOT_FOUND, "查无此核准单: " + id);
        }
        return bill;
    }

    private boolean isLocked(TIchGrantBill bill) {
        return bill.getStatus() != null && bill.getStatus() == GrantChain.STATUS_PASS;
    }

    private List<TIchGrantSign> loadSigns(Long id) {
        return this.ichGrantSignMapper.selectList(new QueryWrapper<TIchGrantSign>()
                .eq("bill_id", id)
                .eq("del_flag", 0)
                .orderByAsc("sign_time")
                .orderByAsc("id"));
    }
}
