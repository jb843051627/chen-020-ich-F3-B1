package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TIchImageCardMapper;
import com.fc.v2.mapper.auto.TIchProjectMapper;
import com.fc.v2.model.auto.TIchImageCard;
import com.fc.v2.model.auto.TIchProject;
import com.fc.v2.service.ITIchImageCardService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 技艺影像卷立卷单Service业务层处理
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TIchImageCardServiceImpl extends ServiceImpl<TIchImageCardMapper, TIchImageCard> implements ITIchImageCardService {

    @Autowired
    private TIchProjectMapper ichProjectMapper;

    @Override
    public TIchImageCard selectTIchImageCardById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TIchImageCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TIchImageCard> selectTIchImageCardList(Wrapper<TIchImageCard> queryWrapper) {
        // 覆盖范围与查单、新增、编辑同一把尺：只认未删的，条件与分页全听调用方，
        // 不在此另塞状态、不另起一页，免得台账这头与那头对不拢
        QueryWrapper<TIchImageCard> wrapper = queryWrapper instanceof QueryWrapper
                ? (QueryWrapper<TIchImageCard>) queryWrapper
                : new QueryWrapper<TIchImageCard>();
        wrapper.eq("del_flag", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTIchImageCard(TIchImageCard record) {
        if (record == null) {
            return 0;
        }
        // 新建与编辑两处同一套校验：底册在不在册、卷号重不重，一处规矩两处都走
        TIchProject refArch = resolveArchive(record.getSiteId());
        if (refArch == null) {
            return 0;
        }
        if (billNoDuplicated(record.getBillNo(), null)) {
            return 0;
        }
        record.setSiteNo(refArch.getSiteNo());
        record.setDevRate(calcDevRate(record.getPlanQty(), record.getRealQty()));
        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTIchImageCard(TIchImageCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        TIchImageCard exist = selectTIchImageCardById(record.getId());
        if (exist == null) {
            return 0;
        }
        // 编辑同样走卷号唯一这道关（把自己排除），与新建一处规矩
        if (billNoDuplicated(record.getBillNo(), record.getId())) {
            return 0;
        }
        // 改拴底册时，与新建同一套在册校验并回填项目代号；没改底册就沿用原代号，
        // 绝不能这里改了 site_id、site_no 还照旧
        if (record.getSiteId() != null) {
            TIchProject refArch = resolveArchive(record.getSiteId());
            if (refArch == null) {
                return 0;
            }
            record.setSiteNo(refArch.getSiteNo());
        }
        // 结转（应归）与结余（实归）两数折偏差率：新建、编辑同一份算法，以应归件数为基数。
        // 编辑时两数都带齐了才重算；只改别的格子就不动原偏差率，免得被误清成 0
        if (record.getPlanQty() != null && record.getRealQty() != null) {
            record.setDevRate(calcDevRate(record.getPlanQty(), record.getRealQty()));
        }
        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TIchImageCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTIchImageCardByIds(String ids) {
        // 软删：卷号仍留档，不会被同一卷反反复复重新立卷
        Long[] idArr = ConvertUtil.toLongArray(ids);
        int n = 0;
        for (Long id : idArr) {
            n += softDelete(id);
        }
        return n;
    }

    @Override
    public int deleteTIchImageCardById(Long id) {
        return softDelete(id);
    }

    /** 底册在册且未注销才作数；通过则回该底册，否则回 null */
    private TIchProject resolveArchive(Integer siteId) {
        if (siteId == null) {
            return null;
        }
        TIchProject refArch = ichProjectMapper.selectOne(new QueryWrapper<TIchProject>()
                .eq("id", siteId).eq("del_flag", 0));
        if (refArch == null) {
            return null;
        }
        if (refArch.getStatus() != null && refArch.getStatus() == 1) {
            return null;
        }
        return refArch;
    }

    /** 卷号在未删卷里是否已被占用（excludeId 为编辑时的本卷自己） */
    private boolean billNoDuplicated(String billNo, Long excludeId) {
        if (StringUtils.isEmpty(billNo)) {
            return false;
        }
        QueryWrapper<TIchImageCard> qw = new QueryWrapper<TIchImageCard>()
                .eq("bill_no", billNo).eq("del_flag", 0);
        if (excludeId != null) {
            qw.ne("id", excludeId);
        }
        Integer dupCnt = this.baseMapper.selectCount(qw);
        return dupCnt != null && dupCnt > 0;
    }

    /**
     * 偏差率：|实归 - 应归| / 应归 × 100，保留 2 位。
     * 以应归件数（planQty）为唯一基数——新建与编辑都从这一个算法出，两数才合得拢。
     * 任一数缺失或应归为 0 时无法折算，记 0。
     */
    private static BigDecimal calcDevRate(BigDecimal planQty, BigDecimal realQty) {
        if (planQty == null || realQty == null || planQty.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return realQty.subtract(planQty).abs()
                .multiply(new BigDecimal("100"))
                .divide(planQty, 2, RoundingMode.HALF_UP);
    }

    private int softDelete(Long id) {
        if (id == null) {
            return 0;
        }
        TIchImageCard patch = new TIchImageCard();
        patch.setId(id);
        patch.setDelFlag(1);
        patch.setUpdateTime(new Date());
        return this.baseMapper.update(patch, new UpdateWrapper<TIchImageCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }
}
