package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TIchItemFlowMapper;
import com.fc.v2.model.auto.TIchItemFlow;
import com.fc.v2.service.ITIchItemFlowService;

/**
 * 名录项目申报单 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TIchItemFlowServiceImpl implements ITIchItemFlowService {

    private static final int MAX_STAGE = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TIchItemFlowMapper ichItemFlowMapper;

    @Override
    public TIchItemFlow selectTIchItemFlowById(Long id) {
        return this.ichItemFlowMapper.selectById(id);
    }

    @Override
    public List<TIchItemFlow> selectTIchItemFlowList(QueryWrapper<TIchItemFlow> queryWrapper) {
        return this.ichItemFlowMapper.selectList(queryWrapper);
    }

    @Override
    public TIchItemFlow advance(Long id, String remark) {
        TIchItemFlow r = this.ichItemFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        // 已收口的单子再点推进：同一条目不许反反复复处置——原样放回，一个数都不动
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
            return r;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        int next = Math.min(st + 1, MAX_STAGE);
        r.setStage(next);
        // 状态跟着处置走：推进到末档（列入）即落"已收口"，其余落"在办"
        r.setStatus(next >= MAX_STAGE ? STATUS_TERMINAL : STATUS_ACTIVE);
        r.setLastAction(remark);
        this.ichItemFlowMapper.updateById(r);
        return r;
    }

    @Override
    public TIchItemFlow rollback(Long id, String remark) {
        TIchItemFlow r = this.ichItemFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        // 与推进同一套规矩：只退一格，不一笔砸回头档
        int st = r.getStage() == null ? 0 : r.getStage();
        int prev = Math.max(st - 1, 0);
        r.setStage(prev);
        // 状态跟着处置走：退出收口后重回"在办"；从头未起的保留"未起"
        r.setStatus(prev > 0 ? STATUS_ACTIVE : 0);
        r.setLastAction(remark);
        this.ichItemFlowMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TIchItemFlow r = this.ichItemFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        r.setContent(remark);
        return this.ichItemFlowMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TIchItemFlow r = this.ichItemFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        return this.ichItemFlowMapper.deleteById(id) > 0;
    }

}
