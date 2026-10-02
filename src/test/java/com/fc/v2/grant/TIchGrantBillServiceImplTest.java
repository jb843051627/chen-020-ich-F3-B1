package com.fc.v2.grant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fc.v2.mapper.auto.TIchGrantBillMapper;
import com.fc.v2.mapper.auto.TIchGrantSignMapper;
import com.fc.v2.model.auto.TIchGrantBill;
import com.fc.v2.model.auto.TIchGrantSign;
import com.fc.v2.model.custom.grant.GrantBillView;
import com.fc.v2.model.custom.grant.GrantFlowException;
import com.fc.v2.model.custom.grant.GrantLedger;
import com.fc.v2.model.custom.grant.GrantReview;
import com.fc.v2.service.impl.TIchGrantBillServiceImpl;

/**
 * 唯一落名入口 TIchGrantBillServiceImpl 的规矩测试（Mapper 全打桩，不起 Spring、不碰库）。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TIchGrantBillServiceImplTest {

    @Mock
    private TIchGrantBillMapper billMapper;

    @Mock
    private TIchGrantSignMapper signMapper;

    @InjectMocks
    private TIchGrantBillServiceImpl service;

    private TIchGrantBill row;
    private final List<TIchGrantSign> signs = new ArrayList<>();
    private final AtomicInteger idSeq = new AtomicInteger(1);
    private int billUpdateResult = 1;

    @BeforeEach
    public void setUp() {
        row = new TIchGrantBill();
        row.setId(7L);
        row.setBillNo("RD-2026-007");
        row.setNodeNo(0);
        row.setRoundNo(0);
        row.setStatus(0);
        row.setNeedCount(1);
        row.setSignMode(0);
        row.setSignCount(0);
        row.setDelFlag(0);
        signs.clear();
        billUpdateResult = 1;

        when(billMapper.selectById(7L)).thenAnswer(inv -> row);
        when(signMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(signs));
        when(signMapper.insert(any())).thenAnswer(inv -> {
            TIchGrantSign s = inv.getArgument(0);
            s.setId((long) idSeq.getAndIncrement());
            if (s.getDelFlag() == null) {
                s.setDelFlag(0);
            }
            signs.add(s);
            return 1;
        });
        when(billMapper.update(any(), any())).thenAnswer(inv -> {
            if (billUpdateResult == 0) {
                return 0;
            }
            TIchGrantBill patch = inv.getArgument(0);
            if (patch.getNodeNo() != null) {
                row.setNodeNo(patch.getNodeNo());
            }
            if (patch.getRoundNo() != null) {
                row.setRoundNo(patch.getRoundNo());
            }
            if (patch.getNeedCount() != null) {
                row.setNeedCount(patch.getNeedCount());
            }
            if (patch.getSignMode() != null) {
                row.setSignMode(patch.getSignMode());
            }
            if (patch.getSignCount() != null) {
                row.setSignCount(patch.getSignCount());
            }
            if (patch.getStatus() != null) {
                row.setStatus(patch.getStatus());
            }
            if (patch.getRemark() != null) {
                row.setRemark(patch.getRemark());
            }
            return 1;
        });
    }

    @Test
    public void 老查询方法收什么给什么原样不动() {
        assertSame(row, service.selectTIchGrantBillById(7L));
    }

    @Test
    public void 查无此单() {
        GrantFlowException ex = assertThrows(GrantFlowException.class,
                () -> service.view(999L));
        assertEquals(GrantFlowException.Reason.NOT_FOUND, ex.getReason());
    }

    @Test
    public void 县一笔即走市里候签两笔齐再到省省点头锁单() {
        GrantBillView v0 = service.sign(7L, 0, "县同志甲", "县里认");
        assertEquals(1, v0.getCurrentNode());
        assertEquals(1, row.getNodeNo());

        // 市里只落一名，窗口仍旧候签
        GrantBillView v1 = service.sign(7L, 1, "市同志甲", "市里先认");
        assertEquals(1, v1.getCurrentNode());
        assertFalse(v1.getNodes().get(1).isComplete());
        assertEquals(0, row.getStatus());

        // 第二名点齐，进省档
        GrantBillView v2 = service.sign(7L, 1, "市同志乙", "市里再认");
        assertEquals(2, v2.getCurrentNode());

        // 省厅点头：认下、锁单
        GrantBillView v3 = service.sign(7L, 2, "省同志甲", "省厅认下");
        assertEquals(2, v3.getCurrentNode());
        assertTrue(v3.isLocked());
        assertEquals(1, row.getStatus());

        GrantReview r = service.review(7L);
        assertTrue(r.isRecognized());
        assertTrue(r.isConsistent());
        assertEquals(4, r.getTotalSignCount());
    }

    @Test
    public void 省厅认下之后补不进名也退不回() {
        service.sign(7L, 0, "县同志甲", null);
        service.sign(7L, 1, "市同志甲", null);
        service.sign(7L, 1, "市同志乙", null);
        service.sign(7L, 2, "省同志甲", null);

        GrantFlowException ex = assertThrows(GrantFlowException.class,
                () -> service.sign(7L, 2, "省同志乙", "事后补一笔"));
        assertEquals(GrantFlowException.Reason.LOCKED, ex.getReason());
        verify(signMapper, org.mockito.Mockito.never()).insert(org.mockito.ArgumentMatchers
                .argThat(s -> s != null && "省同志乙".equals(s.getApprover())));

        GrantFlowException ex2 = assertThrows(GrantFlowException.class,
                () -> service.sendBack(7L, "省同志甲", "退回"));
        assertEquals(GrantFlowException.Reason.LOCKED, ex2.getReason());
    }

    @Test
    public void 越档直签意向档号与入口相左一概不认() {
        // 单还在县里，屏上却把档号带到省厅
        GrantFlowException ex = assertThrows(GrantFlowException.class,
                () -> service.sign(7L, 2, "省同志甲", "越级先落"));
        assertEquals(GrantFlowException.Reason.NODE_MISMATCH, ex.getReason());
        verify(signMapper, never()).insert(any());
        assertEquals(0, row.getNodeNo());

        // 县齐市未齐时直落省厅，照样不认
        service.sign(7L, 0, "县同志甲", null);
        GrantFlowException ex2 = assertThrows(GrantFlowException.class,
                () -> service.sign(7L, 2, "省同志甲", null));
        assertEquals(GrantFlowException.Reason.NODE_MISMATCH, ex2.getReason());

        // 不传意向档号时按入口当前档收下
        GrantBillView v = service.sign(7L, null, "市同志甲", null);
        assertEquals(1, v.getCurrentNode());
    }

    @Test
    public void 同人同档本轮重复落名被挡() {
        service.sign(7L, 0, "县同志甲", null);
        service.sign(7L, 1, "市同志甲", null);
        int before = signs.size();
        GrantFlowException ex = assertThrows(GrantFlowException.class,
                () -> service.sign(7L, 1, "市同志甲", "再点一笔"));
        assertEquals(GrantFlowException.Reason.ALREADY_SIGNED, ex.getReason());
        assertEquals(before, signs.size());
    }

    @Test
    public void 落名同志为空被挡() {
        GrantFlowException ex = assertThrows(GrantFlowException.class,
                () -> service.sign(7L, 0, "  ", null));
        assertEquals(GrantFlowException.Reason.BAD_APPROVER, ex.getReason());
        verify(signMapper, never()).insert(any());
    }

    @Test
    public void 县里无头可退() {
        GrantFlowException ex = assertThrows(GrantFlowException.class,
                () -> service.sendBack(7L, "县同志甲", "县里自己退"));
        assertEquals(GrantFlowException.Reason.NO_PREV_NODE, ex.getReason());
    }

    @Test
    public void 挪回只退一格轮次加一旧名不抹续签往上续() {
        service.sign(7L, 0, "县同志甲", null);
        service.sign(7L, 1, "市同志甲", null);
        service.sign(7L, 1, "市同志乙", null);
        // 单子已送到省档、省厅尚未落名；省厅这时把单子退回市里
        int archived = signs.size();
        assertEquals(2, row.getNodeNo());

        // 省厅退回：只退一格到市里，轮次 0→1，旧笔一笔不抹
        GrantBillView back = service.sendBack(7L, "省同志甲", "材料不齐");
        assertEquals(1, back.getCurrentNode());
        assertEquals(1, back.getRoundNo());
        assertEquals(2, row.getStatus());
        assertEquals(1, row.getNodeNo());
        assertEquals(archived, signs.size());
        assertFalse(back.getNodes().get(1).isComplete());

        // 新一轮市里两人续，省厅新一轮点头；旧县名沿用，旧市名留档不作数
        service.sign(7L, 1, "市同志丙", null);
        service.sign(7L, 1, "市同志丁", null);
        GrantBillView pass = service.sign(7L, 2, "省同志乙", null);
        assertTrue(pass.isLocked());

        GrantReview r = service.review(7L);
        assertTrue(r.isConsistent());
        assertEquals(archived + 3, r.getTotalSignCount());
        assertEquals(4, r.getEffectiveSignCountSum());
        // 市档留档：旧轮两人 + 新轮两人
        assertEquals(4, r.getNodesReversed().get(1).getAllSigns().size());
    }

    @Test
    public void 市里退县里也是只退一格() {
        service.sign(7L, 0, "县同志甲", null);
        service.sign(7L, 1, "市同志甲", null);
        GrantBillView back = service.sendBack(7L, "市同志乙", "退回县里重签");
        assertEquals(0, back.getCurrentNode());
        assertEquals(1, back.getRoundNo());
        // 旧县名、旧市名留档；新轮县档候签
        assertEquals(2, service.review(7L).getTotalSignCount());
        assertFalse(back.getNodes().get(0).isComplete());
    }

    @Test
    public void 落笔时单据被别人推动则报并发变更() {
        billUpdateResult = 0;
        GrantFlowException ex = assertThrows(GrantFlowException.class,
                () -> service.sign(7L, 0, "县同志甲", null));
        assertEquals(GrantFlowException.Reason.CONCURRENT_CHANGED, ex.getReason());
    }

    @Test
    public void 册子结论与窗口核准同一回算() {
        service.sign(7L, 0, "县同志甲", null);
        GrantLedger before = service.ledger(7L);
        assertFalse(before.isRecognized());
        assertEquals("未认下", before.getApprovedLevelName());

        service.sign(7L, 1, "市同志甲", null);
        service.sign(7L, 1, "市同志乙", null);
        service.sign(7L, 2, "省同志甲", null);
        GrantLedger after = service.ledger(7L);
        assertTrue(after.isRecognized());
        assertTrue(after.isReviewConsistent());
        assertEquals("县文旅—市文旅—省文旅", after.getRoadName());
        assertNotNull(after.getBillNo());
    }
}
