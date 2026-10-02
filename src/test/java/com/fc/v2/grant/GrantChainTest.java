package com.fc.v2.grant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.TIchGrantBill;
import com.fc.v2.model.auto.TIchGrantSign;
import com.fc.v2.model.custom.grant.GrantBillView;
import com.fc.v2.model.custom.grant.GrantChain;
import com.fc.v2.model.custom.grant.GrantLedger;
import com.fc.v2.model.custom.grant.GrantReview;

/**
 * 纯回算口径 GrantChain 的规矩测试：不启 Spring、不碰库。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class GrantChainTest {

    private long seq = 1;

    private TIchGrantBill bill(int node, int round, int status) {
        TIchGrantBill b = new TIchGrantBill();
        b.setId(100L);
        b.setBillNo("RD-2026-001");
        b.setNodeNo(node);
        b.setRoundNo(round);
        b.setStatus(status);
        b.setSignCount(0);
        b.setNeedCount(GrantChain.requiredCount(node));
        return b;
    }

    private TIchGrantSign sign(int node, int round, String approver, String day) {
        TIchGrantSign s = new TIchGrantSign();
        s.setId(seq++);
        s.setBillId(100L);
        s.setNodeNo(node);
        s.setRoundNo(round);
        s.setApprover(approver);
        s.setComment(day + " 的一笔");
        try {
            s.setSignTime(new SimpleDateFormat("yyyy-MM-dd").parse(day));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return s;
    }

    @Test
    public void 三档定法_县一笔市两笔省一笔() {
        assertEquals(1, GrantChain.requiredCount(0));
        assertEquals(2, GrantChain.requiredCount(1));
        assertEquals(1, GrantChain.requiredCount(2));
        assertEquals("一笔即可", GrantChain.signRuleName(0));
        assertEquals("两笔点齐", GrantChain.signRuleName(1));
        assertEquals(4, GrantChain.fullRosterCount());
    }

    @Test
    public void 空单停在县里() {
        GrantChain.State st = GrantChain.evaluate(bill(0, 0, 0), new ArrayList<>());
        assertEquals(0, st.getCurrentNode());
        assertFalse(st.isRecognized());
        assertEquals(0, st.node(0).getSignedCount());
    }

    @Test
    public void 县里头一人落地这一档就往前() {
        List<TIchGrantSign> signs = new ArrayList<>();
        signs.add(sign(0, 0, "县同志甲", "2026-01-01"));
        GrantChain.State st = GrantChain.evaluate(bill(1, 0, 0), signs);
        assertTrue(st.node(0).isComplete());
        assertEquals(1, st.getCurrentNode());
    }

    @Test
    public void 市里只落一名仍旧候签两名点齐才过() {
        List<TIchGrantSign> signs = new ArrayList<>();
        signs.add(sign(0, 0, "县同志甲", "2026-01-01"));
        signs.add(sign(1, 0, "市同志甲", "2026-02-01"));
        GrantChain.State one = GrantChain.evaluate(bill(1, 0, 0), signs);
        assertEquals(1, one.getCurrentNode());
        assertFalse(one.node(1).isComplete());
        assertEquals(1, one.node(1).getSignedCount());

        signs.add(sign(1, 0, "市同志乙", "2026-02-02"));
        GrantChain.State two = GrantChain.evaluate(bill(2, 0, 0), signs);
        assertTrue(two.node(1).isComplete());
        assertEquals(2, two.getCurrentNode());
    }

    @Test
    public void 市里同人两回只算一名() {
        List<TIchGrantSign> signs = new ArrayList<>();
        signs.add(sign(0, 0, "县同志甲", "2026-01-01"));
        signs.add(sign(1, 0, "市同志甲", "2026-02-01"));
        signs.add(sign(1, 0, "市同志甲", "2026-02-03"));
        GrantChain.State st = GrantChain.evaluate(bill(1, 0, 0), signs);
        assertEquals(1, st.node(1).getSignedCount());
        assertFalse(st.node(1).isComplete());
    }

    @Test
    public void 省厅绕过市里径直落名照样不认() {
        List<TIchGrantSign> signs = new ArrayList<>();
        // 县里齐了，市里空着，省厅先落一笔
        signs.add(sign(0, 0, "县同志甲", "2026-01-01"));
        signs.add(sign(2, 0, "省同志甲", "2026-03-01"));
        GrantChain.State st = GrantChain.evaluate(bill(0, 0, 0), signs);
        assertEquals(1, st.getCurrentNode());
        assertEquals(0, st.node(2).getSignedCount());
        assertFalse(st.node(2).isComplete());
        assertFalse(st.isRecognized());
    }

    @Test
    public void 县里没齐市里先落也按没落算() {
        List<TIchGrantSign> signs = new ArrayList<>();
        signs.add(sign(1, 0, "市同志甲", "2026-02-01"));
        signs.add(sign(1, 0, "市同志乙", "2026-02-02"));
        GrantChain.State st = GrantChain.evaluate(bill(0, 0, 0), signs);
        assertEquals(0, st.getCurrentNode());
        assertEquals(0, st.node(1).getSignedCount());
    }

    @Test
    public void 三档齐省厅点头即认下() {
        List<TIchGrantSign> signs = new ArrayList<>();
        signs.add(sign(0, 0, "县同志甲", "2026-01-01"));
        signs.add(sign(1, 0, "市同志甲", "2026-02-01"));
        signs.add(sign(1, 0, "市同志乙", "2026-02-02"));
        signs.add(sign(2, 0, "省同志甲", "2026-03-01"));
        TIchGrantBill b = bill(2, 0, GrantChain.STATUS_PASS);
        GrantChain.State st = GrantChain.evaluate(b, signs);
        assertTrue(st.isRecognized());
        assertEquals(2, st.getCurrentNode());
        GrantReview r = GrantChain.buildReview(b, signs);
        assertTrue(r.isRecognized());
        assertTrue(r.isConsistent());
        assertEquals(4, r.getTotalSignCount());
        // 倒序：省、市、县
        assertEquals(2, r.getNodesReversed().get(0).getNodeNo());
        assertEquals(1, r.getNodesReversed().get(1).getNodeNo());
        assertEquals(0, r.getNodesReversed().get(2).getNodeNo());
    }

    @Test
    public void 挪回只退一格旧名一笔不抹新旧轮分开() {
        // 头一轮市里凑齐、送到省档；省厅没落名便把单子退回市里，旧轮 3 笔留档
        List<TIchGrantSign> signs = new ArrayList<>();
        signs.add(sign(0, 0, "县同志甲", "2026-01-01"));
        signs.add(sign(1, 0, "市同志甲", "2026-02-01"));
        signs.add(sign(1, 0, "市同志乙", "2026-02-02"));

        TIchGrantBill rolled = bill(1, 1, 2);
        GrantChain.State back = GrantChain.evaluate(rolled, signs);
        // 停在市里候签；市档作数看新一轮（空），旧轮两名不作数但留档
        assertEquals(1, back.getCurrentNode());
        assertEquals(1, back.getCurrentRound());
        assertFalse(back.node(1).isComplete());
        assertEquals(0, back.node(1).getSignedCount());
        assertTrue(back.node(0).isComplete());

        // 市里新一轮两人续签，省里新一轮点头
        signs.add(sign(1, 1, "市同志丙", "2026-04-01"));
        signs.add(sign(1, 1, "市同志丁", "2026-04-02"));
        signs.add(sign(2, 1, "省同志甲", "2026-04-03"));
        TIchGrantBill repassed = bill(2, 1, GrantChain.STATUS_PASS);
        GrantChain.State again = GrantChain.evaluate(repassed, signs);
        assertTrue(again.isRecognized());
        assertEquals(0, again.node(0).getEffectiveRound());
        assertEquals(1, again.node(1).getEffectiveRound());
        assertEquals(1, again.node(2).getEffectiveRound());

        GrantReview r = GrantChain.buildReview(repassed, signs);
        assertTrue(r.isConsistent());
        // 留档总笔数 6（旧轮一笔不抹），核准名单作数 4 名
        assertEquals(6, r.getTotalSignCount());
        assertEquals(4, r.getEffectiveSignCountSum());
        // 市档复查：留档新旧四人都在，作数只点新轮两人
        GrantReview.NodeReview city = r.getNodesReversed().get(1);
        assertEquals(4, city.getAllSigns().size());
        assertEquals(2, city.getEffectiveSignedCount());
    }

    @Test
    public void 挪回到县里市里旧名也不抹() {
        List<TIchGrantSign> signs = new ArrayList<>();
        signs.add(sign(0, 0, "县同志甲", "2026-01-01"));
        signs.add(sign(1, 0, "市同志甲", "2026-02-01"));
        // 市里退回县里（只一格），新轮从县续
        TIchGrantBill rolled = bill(0, 1, 2);
        GrantChain.State st = GrantChain.evaluate(rolled, signs);
        assertEquals(0, st.getCurrentNode());
        assertFalse(st.node(0).isComplete());
        // 旧县名、旧市名共两笔都留在档上不抹，只是都不再作数
        GrantReview r = GrantChain.buildReview(rolled, signs);
        assertEquals(2, r.getTotalSignCount());
        assertEquals(0, st.node(1).getSignedCount());
    }

    @Test
    public void 窗口格子参考数被篡改以落名记录为准() {
        List<TIchGrantSign> signs = new ArrayList<>();
        signs.add(sign(0, 0, "县同志甲", "2026-01-01"));
        signs.add(sign(1, 0, "市同志甲", "2026-02-01"));
        TIchGrantBill b = bill(1, 0, 0);
        b.setSignCount(9); // 格子手敲 9，落名只有 1
        GrantBillView v = GrantChain.buildView(b, signs);
        assertEquals(1, v.getActualSignCount());
        assertFalse(v.isCountMatched());
        assertEquals(1, v.getCurrentNode());

        b.setSignCount(1);
        GrantBillView v2 = GrantChain.buildView(b, signs);
        assertTrue(v2.isCountMatched());
    }

    @Test
    public void 单拎一档单独核也站得住() {
        List<TIchGrantSign> signs = new ArrayList<>();
        signs.add(sign(0, 0, "县同志甲", "2026-01-01"));
        TIchGrantBill b = bill(1, 0, 0);
        GrantReview r = GrantChain.buildReview(b, signs);
        GrantReview.NodeReview county = r.getNodesReversed().get(2);
        assertTrue(county.isComplete());
        assertEquals(1, county.getEffectiveSignedCount());
        assertEquals(1, county.getRequiredCount());
        // 整单未认下，整体合不拢，但单拎县档站得住
        assertFalse(r.isConsistent());
        assertFalse(r.isRecognized());
    }

    @Test
    public void 核准结论与册子同一回算() {
        List<TIchGrantSign> signs = new ArrayList<>();
        signs.add(sign(0, 0, "县同志甲", "2026-01-01"));
        TIchGrantBill pending = bill(1, 0, 0);
        GrantLedger l0 = GrantChain.buildLedger(pending, signs);
        assertFalse(l0.isRecognized());
        assertEquals(-1, l0.getApprovedNode());
        assertEquals("未认下", l0.getApprovedLevelName());
        assertEquals("", l0.getRoadName());

        signs.add(sign(1, 0, "市同志甲", "2026-02-01"));
        signs.add(sign(1, 0, "市同志乙", "2026-02-02"));
        signs.add(sign(2, 0, "省同志甲", "2026-03-01"));
        TIchGrantBill done = bill(2, 0, GrantChain.STATUS_PASS);
        GrantLedger l1 = GrantChain.buildLedger(done, signs);
        assertTrue(l1.isRecognized());
        assertTrue(l1.isReviewConsistent());
        assertEquals(2, l1.getApprovedNode());
        assertEquals("省文旅", l1.getApprovedLevelName());
        assertEquals("县文旅—市文旅—省文旅", l1.getRoadName());
    }

    @Test
    public void 视图四件事与停档齐全() {
        List<TIchGrantSign> signs = new ArrayList<>();
        signs.add(sign(0, 0, "县同志甲", "2026-01-01"));
        TIchGrantBill b = bill(1, 0, 0);
        b.setSignCount(0);
        GrantBillView v = GrantChain.buildView(b, signs);
        assertEquals(1, v.getCurrentNode());
        assertEquals("市文旅", v.getCurrentNodeName());
        assertEquals(2, v.getNodes().get(1).getRequiredCount());
        assertEquals("两笔点齐", v.getNodes().get(1).getSignRule());
        assertEquals(0, v.getNodes().get(1).getSignedCount());
        assertFalse(v.getNodes().get(1).isComplete());
    }
}
