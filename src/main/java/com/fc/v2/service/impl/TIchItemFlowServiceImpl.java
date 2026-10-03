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
        // 已收口的单子不许再往前推——状态跟着处置走，免得下一轮又办一遍
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        int next = Math.min(st + 1, MAX_STAGE);
        r.setStage(next);
        // 推进与回退同一把步长：都是一格一格走；走到顶格即收口
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
        // 已收口的单子同样不许再退
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        // 只回退一格，与推进同一把步长；不许径直归到起点
        r.setStage(Math.max(st - 1, 0));
        r.setStatus(STATUS_ACTIVE);
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
        // 已办结（收口）不得再改
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
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
        // 已办结（收口）不得删除
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
            return false;
        }
        return this.ichItemFlowMapper.deleteById(id) > 0;
    }

}
