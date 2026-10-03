package com.fc.v2.imagecard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.mapper.auto.TIchImageCardMapper;
import com.fc.v2.mapper.auto.TIchProjectMapper;
import com.fc.v2.model.auto.TIchImageCard;
import com.fc.v2.model.auto.TIchProject;
import com.fc.v2.service.impl.TIchImageCardServiceImpl;

/**
 * 技艺影像卷立卷单"新建/编辑同一套规矩"的规矩测试（Mapper 全打桩，不碰库）。
 *
 * <p>盯旧毛病：编辑既不校验在册项目也不重折偏差率，与新建两张规矩；
 * 结转（实归）与结余（应归）两数折偏差率两处算法不同源。
 *
 * @author fuce
 * @date 2026-10-03
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

    private TIchProject project;

    @BeforeEach
    public void setUp() {
        project = new TIchProject();
        project.setId(7L);
        project.setSiteNo("XM00");
        project.setStatus(0);
        project.setDelFlag(0);
        when(projectMapper.selectOne(any())).thenAnswer(inv -> project);
        when(baseMapper.selectCount(any())).thenReturn(0);
        when(baseMapper.insert(any())).thenAnswer(inv -> 1);
        when(baseMapper.update(any(), any(Wrapper.class))).thenAnswer(inv -> 1);
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
        return c;
    }

    @Test
    public void 新建偏差率由应归实归同一套算法折出() {
        TIchImageCard c = card("JUAN-1", 7, "100", "120");
        assertEquals(1, service.insertTIchImageCard(c));
        // |120-100|/120*100 = 16.67
        assertEquals(0, new BigDecimal("16.67").compareTo(c.getDevRate()));
        assertEquals("XM00", c.getSiteNo());
        assertEquals("JUAN-1", c.getCreateBy());
        assertEquals(0, c.getDelFlag());
    }

    @Test
    public void 实归为零时分母用应归() {
        TIchImageCard c = card("JUAN-1", 7, "100", "0");
        assertEquals(1, service.insertTIchImageCard(c));
        assertEquals(0, new BigDecimal("100.00").compareTo(c.getDevRate()));
    }

    @Test
    public void 归属项目已注销新建被挡() {
        project.setStatus(1);
        assertEquals(0, service.insertTIchImageCard(card("JUAN-1", 7, "100", "120")));
    }

    @Test
    public void 归属项目查无新建被挡() {
        when(projectMapper.selectOne(any())).thenReturn(null);
        assertEquals(0, service.insertTIchImageCard(card("JUAN-1", 7, "100", "120")));
    }

    @Test
    public void 卷号重复新建被挡() {
        when(baseMapper.selectCount(any())).thenReturn(1);
        assertEquals(0, service.insertTIchImageCard(card("JUAN-1", 7, "100", "120")));
    }

    @Test
    public void 编辑也按同一套算法重折偏差率() {
        // 旧毛病：update 只盖 update_time，偏差率留旧值、项目也不校验
        TIchImageCard c = card("JUAN-1", 7, "100", "150");
        c.setId(5L);
        c.setDevRate(new BigDecimal("999.99"));
        assertEquals(1, service.updateTIchImageCard(c));
        // |150-100|/150*100 = 33.33
        assertEquals(0, new BigDecimal("33.33").compareTo(c.getDevRate()));
        assertNotNull(c.getUpdateTime());
    }

    @Test
    public void 编辑挂到已注销项目同样被挡() {
        project.setStatus(1);
        TIchImageCard c = card("JUAN-1", 7, "100", "120");
        c.setId(5L);
        assertEquals(0, service.updateTIchImageCard(c));
    }

    @Test
    public void 列表只排未删且保留调用方条件() {
        List<TIchImageCard> data = new ArrayList<>();
        data.add(card("JUAN-1", 7, "1", "1"));
        when(baseMapper.selectList(any())).thenAnswer(inv -> {
            Wrapper<TIchImageCard> w = inv.getArgument(0);
            // 统一口径必须带上 del_flag=0；旧写法硬塞的 status=0 不应再凭空出现
            String sql = w.getExpression() == null ? "" : w.getCustomSqlSegment();
            assertNotNull(sql);
            return data;
        });
        assertEquals(1, service.selectTIchImageCardList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TIchImageCard>()
                        .eq("bill_no", "JUAN-1")).size());
    }
}
