package com.fc.v2.itemflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
 * 申报单推进/回退同一把步长、状态随处置走（Mapper 全打桩）。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TIchItemFlowServiceImplTest {

    @Mock
    private TIchItemFlowMapper mapper;

    @InjectMocks
    private TIchItemFlowServiceImpl service;

    private final List<TIchItemFlow> store = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        when(mapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TIchItemFlow r : store) {
                if (r.getId().equals(id)) {
                    return r;
                }
            }
            return null;
        });
        when(mapper.updateById(any())).thenAnswer(inv -> {
            TIchItemFlow patch = inv.getArgument(0);
            for (TIchItemFlow r : store) {
                if (r.getId().equals(patch.getId())) {
                    if (patch.getStage() != null) {
                        r.setStage(patch.getStage());
                    }
                    if (patch.getStatus() != null) {
                        r.setStatus(patch.getStatus());
                    }
                    if (patch.getContent() != null) {
                        r.setContent(patch.getContent());
                    }
                    if (patch.getLastAction() != null) {
                        r.setLastAction(patch.getLastAction());
                    }
                    return 1;
                }
            }
            return 0;
        });
        when(mapper.deleteById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            store.removeIf(r -> r.getId().equals(id));
            return 1;
        });
    }

    private TIchItemFlow flow(long id, int stage, int status) {
        TIchItemFlow r = new TIchItemFlow();
        r.setId(id);
        r.setStage(stage);
        r.setStatus(status);
        r.setDelFlag(0);
        store.add(r);
        return r;
    }

    @Test
    public void 推进回退都是一格_进退步长同源() {
        TIchItemFlow r = flow(1, 0, 0);
        assertEquals(1, service.advance(1L, "过核验").getStage());
        assertEquals(0, service.rollback(1L, "退回核验").getStage());
        assertSame(r, service.selectTIchItemFlowById(1L));
    }

    @Test
    public void 走到顶格即收口_状态跟着处置走() {
        flow(1, 2, 1);
        TIchItemFlow top = service.advance(1L, "列入");
        assertEquals(3, top.getStage());
        assertEquals(2, top.getStatus());
    }

    @Test
    public void 已收口的单不许再推也不许再退_下一轮不重办() {
        flow(1, 3, 2);
        assertNull(service.advance(1L, "再推"));
        assertNull(service.rollback(1L, "再退"));
    }

    @Test
    public void 已收口的单不许改也不许删() {
        TIchItemFlow r = flow(1, 3, 2);
        assertFalse(service.updateContent(1L, "改内容"));
        assertFalse(service.remove(1L));
        // 没有真删：单子还在
        assertSame(r, service.selectTIchItemFlowById(1L));
        verify(mapper, never()).deleteById(any());
    }

    @Test
    public void 在办单回退只退一格不归零() {
        flow(1, 2, 1);
        assertEquals(1, service.rollback(1L, "退一格").getStage());
    }
}
