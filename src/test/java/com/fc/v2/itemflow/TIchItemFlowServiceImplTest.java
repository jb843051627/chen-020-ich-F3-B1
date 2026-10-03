package com.fc.v2.itemflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.fc.v2.mapper.auto.TIchItemFlowMapper;
import com.fc.v2.model.auto.TIchItemFlow;
import com.fc.v2.service.impl.TIchItemFlowServiceImpl;

/**
 * 名录项目申报单状态机的规矩测试（Mapper 全打桩，不碰库）。
 *
 * <p>盯三处旧毛病：推进一次跳两格、到顶不置收口、已收口还被反反复复改；
 * 回退原来一笔砸回头档，现在与推进对称只退一格。
 *
 * @author fuce
 * @date 2026-10-03
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TIchItemFlowServiceImplTest {

    @Mock
    private TIchItemFlowMapper itemFlowMapper;

    @InjectMocks
    private TIchItemFlowServiceImpl service;

    private final List<TIchItemFlow> rows = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        rows.clear();
        when(itemFlowMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TIchItemFlow r : rows) {
                if (r.getId().equals(id)) {
                    return r;
                }
            }
            return null;
        });
        when(itemFlowMapper.updateById(any())).thenAnswer(inv -> 1);
    }

    private TIchItemFlow row(long id, Integer stage, Integer status) {
        TIchItemFlow r = new TIchItemFlow();
        r.setId(id);
        r.setBizNo("SB-2026-" + id);
        r.setStage(stage);
        r.setStatus(status);
        r.setDelFlag(0);
        rows.add(r);
        return r;
    }

    @Test
    public void 推进一次只走一格不跳格() {
        TIchItemFlow r = row(1, 0, 0);
        service.advance(1L, "核验过");
        assertEquals(1, r.getStage());
        assertEquals(1, r.getStatus());
    }

    @Test
    public void 中途推进仍是只走一格() {
        TIchItemFlow r = row(1, 1, 1);
        service.advance(1L, "评议过");
        // 旧写法 +2 会从 1 直接跳到 3
        assertEquals(2, r.getStage());
        assertEquals(1, r.getStatus());
    }

    @Test
    public void 推进到末档即落已收口状态跟着处置走() {
        TIchItemFlow r = row(1, 2, 1);
        service.advance(1L, "列入");
        assertEquals(3, r.getStage());
        assertEquals(2, r.getStatus());
    }

    @Test
    public void 已收口的单子再推进原样放回不反复处置() {
        TIchItemFlow r = row(1, 3, 2);
        TIchItemFlow out = service.advance(1L, "又点一下");
        assertEquals(3, out.getStage());
        assertEquals(2, out.getStatus());
        // 收口后再推不写库
        verify(itemFlowMapper, never()).updateById(any());
    }

    @Test
    public void 回退只退一格不砸回头档() {
        TIchItemFlow r = row(1, 2, 1);
        service.rollback(1L, "退一档");
        assertEquals(1, r.getStage());
        assertEquals(1, r.getStatus());
    }

    @Test
    public void 退回头档后状态回未起() {
        TIchItemFlow r = row(1, 1, 1);
        service.rollback(1L, "退到头");
        assertEquals(0, r.getStage());
        assertEquals(0, r.getStatus());
    }

    @Test
    public void 查无此单() {
        assertNull(service.advance(999L, "x"));
        assertNull(service.rollback(999L, "x"));
    }
}
