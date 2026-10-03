package com.fc.v2.imagecard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
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

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TIchImageCardMapper;
import com.fc.v2.mapper.auto.TIchProjectMapper;
import com.fc.v2.model.auto.TIchImageCard;
import com.fc.v2.model.auto.TIchProject;
import com.fc.v2.service.impl.TIchImageCardServiceImpl;

/**
 * 影像卷新建/编辑同一套校验、偏差率同源、软删后卷号不可重用、列表只认未删（Mapper 全打桩）。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TIchImageCardServiceImplTest {

    @Mock
    private TIchImageCardMapper baseMapper;
    @Mock
    private TIchProjectMapper projectMapper;

    @InjectMocks
    private TIchImageCardServiceImpl service;

    private final List<TIchImageCard> cards = new ArrayList<>();
    private final List<TIchProject> projects = new ArrayList<>();
    private final AtomicLong idSeq = new AtomicLong(100);

    @BeforeEach
    public void setUp() {
        projects.add(project(10, "XM00", 0));
        projects.add(project(11, "XM01", 1));

        // 底册查询的两个条件：id(Integer) 在前、del_flag 在后，取头一个 Integer 即 siteId
        when(projectMapper.selectOne(any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?> w = inv.getArgument(0);
            w.getSqlSegment();
            // 第一个条件即 id（siteId），按 MPGENVAL 序号取头一个，避开后面的 del_flag=0
            Integer siteId = firstIntParam(w);
            if (siteId == null) {
                return null;
            }
            for (TIchProject p : projects) {
                if (p.getId().intValue() == siteId.intValue() && p.getDelFlag() == 0) {
                    return p;
                }
            }
            return null;
        });

        // 按主键回查（条件里唯一的 Long 即 id）
        when(baseMapper.selectOne(any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?> w = inv.getArgument(0);
            w.getSqlSegment();
            // 第一个条件即主键 id，按序号取头一个 Long
            Long id = firstLongParam(w);
            if (id == null) {
                return null;
            }
            for (TIchImageCard c : cards) {
                if (c.getId().equals(id) && (c.getDelFlag() == null || c.getDelFlag() == 0)) {
                    return c;
                }
            }
            return null;
        });

        // 卷号唯一性：String 参数是卷号，Long 参数是编辑时要排除的本卷 id
        when(baseMapper.selectCount(any())).thenAnswer(inv -> {
            QueryWrapper<TIchImageCard> w = inv.getArgument(0);
            w.getSqlSegment();
            String billNo = null;
            Long excludeId = null;
            for (Object v : w.getParamNameValuePairs().values()) {
                if (v instanceof String) {
                    billNo = (String) v;
                } else if (v instanceof Long) {
                    excludeId = (Long) v;
                }
            }
            int n = 0;
            for (TIchImageCard c : cards) {
                if (c.getDelFlag() != null && c.getDelFlag() == 0
                        && (billNo == null || billNo.equals(c.getBillNo()))
                        && (excludeId == null || !excludeId.equals(c.getId()))) {
                    n++;
                }
            }
            return n;
        });

        when(baseMapper.selectList(any())).thenAnswer(inv -> {
            List<TIchImageCard> out = new ArrayList<>();
            for (TIchImageCard c : cards) {
                if (c.getDelFlag() == null || c.getDelFlag() == 0) {
                    out.add(c);
                }
            }
            return out;
        });

        when(baseMapper.insert(any())).thenAnswer(inv -> {
            TIchImageCard c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(idSeq.getAndIncrement());
            }
            if (c.getDelFlag() == null) {
                c.setDelFlag(0);
            }
            cards.add(c);
            return 1;
        });

        when(baseMapper.update(any(TIchImageCard.class), any(UpdateWrapper.class))).thenAnswer(inv -> {
            TIchImageCard patch = inv.getArgument(0);
            for (TIchImageCard c : cards) {
                if (c.getId().equals(patch.getId()) && (c.getDelFlag() == null || c.getDelFlag() == 0)) {
                    if (patch.getSiteNo() != null) {
                        c.setSiteNo(patch.getSiteNo());
                    }
                    if (patch.getDevRate() != null) {
                        c.setDevRate(patch.getDevRate());
                    }
                    if (patch.getDelFlag() != null) {
                        c.setDelFlag(patch.getDelFlag());
                    }
                    if (patch.getPlanQty() != null) {
                        c.setPlanQty(patch.getPlanQty());
                    }
                    if (patch.getRealQty() != null) {
                        c.setRealQty(patch.getRealQty());
                    }
                    return 1;
                }
            }
            return 0;
        });
    }

    /** 按 MPGENVAL 序号取头一个整型条件值（id/siteId 总在头一个条件） */
    private static Integer firstIntParam(QueryWrapper<?> w) {
        int idx = 1;
        while (true) {
            Object v = w.getParamNameValuePairs().get("MPGENVAL" + idx);
            if (v instanceof Integer) {
                return (Integer) v;
            }
            if (v == null) {
                return null;
            }
            idx++;
        }
    }

    /** 按 MPGENVAL 序号取头一个长整条件值（主键总在头一个条件） */
    private static Long firstLongParam(QueryWrapper<?> w) {
        int idx = 1;
        while (true) {
            Object v = w.getParamNameValuePairs().get("MPGENVAL" + idx);
            if (v instanceof Long) {
                return (Long) v;
            }
            if (v == null) {
                return null;
            }
            idx++;
        }
    }

    private TIchProject project(int id, String siteNo, int status) {
        TIchProject p = new TIchProject();
        p.setId((long) id);
        p.setSiteNo(siteNo);
        p.setStatus(status);
        p.setDelFlag(0);
        return p;
    }

    private TIchImageCard card(String billNo, Integer siteId, String plan, String real) {
        TIchImageCard c = new TIchImageCard();
        c.setBillNo(billNo);
        c.setSiteId(siteId);
        if (plan != null) {
            c.setPlanQty(new BigDecimal(plan));
        }
        if (real != null) {
            c.setRealQty(new BigDecimal(real));
        }
        c.setStatus(0);
        c.setDelFlag(0);
        return c;
    }

    private TIchImageCard insertFirst() {
        TIchImageCard c = card("JUAN-1", 10, "100", "120");
        assertEquals(1, service.insertTIchImageCard(c));
        return c;
    }

    @Test
    public void 新建挂已注销底册被拒_与编辑同一道在册校验() {
        TIchImageCard c = card("JUAN-X", 11, "100", "100");
        assertEquals(0, service.insertTIchImageCard(c));
        verify(baseMapper, never()).insert(any());
    }

    @Test
    public void 新建重号被拒() {
        insertFirst();
        TIchImageCard dup = card("JUAN-1", 10, "100", "100");
        assertEquals(0, service.insertTIchImageCard(dup));
    }

    @Test
    public void 偏差率以应归为基数_新建即算() {
        TIchImageCard c = insertFirst();
        // |120-100|/100*100 = 20.00
        assertEquals(new BigDecimal("20.00"), c.getDevRate());
    }

    @Test
    public void 编辑改数量按同源公式重算_改底册回填项目代号() {
        TIchImageCard c = insertFirst();
        TIchImageCard patch = new TIchImageCard();
        patch.setId(c.getId());
        patch.setPlanQty(new BigDecimal("100"));
        patch.setRealQty(new BigDecimal("150"));
        patch.setSiteId(10);
        assertEquals(1, service.updateTIchImageCard(patch));
        // |150-100|/100*100 = 50.00，与新建同一算法、同一基数
        assertEquals(new BigDecimal("50.00"), c.getDevRate());
        assertEquals("XM00", c.getSiteNo());
    }

    @Test
    public void 删除为软删_卷号留档列表不再见() {
        TIchImageCard c = insertFirst();
        assertEquals(1, service.deleteTIchImageCardById(c.getId()));
        assertEquals(Integer.valueOf(1), c.getDelFlag());
        // 未删列表里不再出现
        assertEquals(0, service.selectTIchImageCardList(new QueryWrapper<TIchImageCard>()).size());
    }

    @Test
    public void 列表尊重调用方条件只补未删口径() {
        TIchImageCard c = insertFirst();
        assertNotNull(c.getId());
        QueryWrapper<TIchImageCard> qw = new QueryWrapper<TIchImageCard>().eq("bill_no", "JUAN-1");
        List<TIchImageCard> list = service.selectTIchImageCardList(qw);
        assertEquals(1, list.size());
        assertEquals("JUAN-1", list.get(0).getBillNo());
    }
}
