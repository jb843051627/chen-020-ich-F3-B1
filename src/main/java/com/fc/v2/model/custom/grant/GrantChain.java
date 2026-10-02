package com.fc.v2.model.custom.grant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fc.v2.model.auto.TIchGrantBill;
import com.fc.v2.model.auto.TIchGrantSign;

/**
 * 工坊认定三级链条的**唯一回算口径**（纯算，不碰库）。
 *
 * <p>规矩全在这里，服务层只负责落库与回算：
 * <ul>
 *   <li>三档：0县文旅一笔即可、1市文旅两笔点齐、2省文旅一笔即可（末道点头即认下锁单）。</li>
 *   <li>凑没凑齐顺着落名记录一笔笔点出（同人同档同轮只算一名），单据格子里的数只当参考。</li>
 *   <li>只能一档一档往前推：前一档没齐，后一档的落名按没落算（回算时悬空不计）。</li>
 *   <li>挪回只挪一格、轮次加一：本轮从被退回档重新起签；旧轮落名一笔不抹，新旧分开留档。</li>
 *   <li>当前停在第几档、认不认下，全部由本回算答复，前端递来的档号只当意向。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-02
 */
public final class GrantChain {

    /** 档口数：县文旅/市文旅/省文旅 */
    public static final int NODE_COUNT = 3;

    /** 最末一档（省文旅） */
    public static final int LAST_NODE = NODE_COUNT - 1;

    /** 核准情形：在核 */
    public static final int STATUS_RUNNING = 0;

    /** 核准情形：已认下 */
    public static final int STATUS_PASS = 1;

    /** 档名，档编号即下标 */
    private static final String[] NODE_NAMES = {"县文旅", "市文旅", "省文旅"};

    private GrantChain() {
    }

    public static String nodeName(int nodeNo) {
        return nodeNo >= 0 && nodeNo < NODE_COUNT ? NODE_NAMES[nodeNo] : "未知档";
    }

    /** 这一档按规矩该凑几个人：县一笔、市两笔、省一笔 */
    public static int requiredCount(int nodeNo) {
        if (nodeNo == 1) {
            return 2;
        }
        if (nodeNo == 0 || nodeNo == 2) {
            return 1;
        }
        throw new IllegalArgumentException("档编号越界: " + nodeNo);
    }

    /** 怎么才算凑齐 */
    public static String signRuleName(int nodeNo) {
        return nodeNo == 1 ? "两笔点齐" : "一笔即可";
    }

    /** 三档定法给出的并签办法：0一笔即可 1两笔点齐（与单据参考列 sign_mode 同义） */
    public static int signMode(int nodeNo) {
        return nodeNo == 1 ? 1 : 0;
    }

    /** 链条全部凑齐时的作数落名总笔数：1+2+1 */
    public static int fullRosterCount() {
        int sum = 0;
        for (int n = 0; n < NODE_COUNT; n++) {
            sum += requiredCount(n);
        }
        return sum;
    }

    /**
     * 一档的回算结果。
     */
    public static final class NodeState {
        private final int nodeNo;
        private final int effectiveRound;
        private final boolean complete;
        private final List<String> approvers;

        NodeState(int nodeNo, int effectiveRound, boolean complete, List<String> approvers) {
            this.nodeNo = nodeNo;
            this.effectiveRound = effectiveRound;
            this.complete = complete;
            this.approvers = approvers;
        }

        public int getNodeNo() {
            return nodeNo;
        }

        public int getEffectiveRound() {
            return effectiveRound;
        }

        public boolean isComplete() {
            return complete;
        }

        public List<String> getApprovers() {
            return approvers;
        }

        public int getSignedCount() {
            return approvers.size();
        }
    }

    /**
     * 整张单的回算状态。
     */
    public static final class State {
        private final int currentRound;
        private final int entryNode;
        private final boolean locked;
        private final NodeState[] nodes;
        private final List<TIchGrantSign> allSigns;

        State(int currentRound, int entryNode, boolean locked, NodeState[] nodes,
              List<TIchGrantSign> allSigns) {
            this.currentRound = currentRound;
            this.entryNode = entryNode;
            this.locked = locked;
            this.nodes = nodes;
            this.allSigns = allSigns;
        }

        public int getCurrentRound() {
            return currentRound;
        }

        /** 本轮重新起签的档口（本轮第一笔落在哪一档；一笔未续时取服务层挪回时写下的当前档） */
        public int getEntryNode() {
            return entryNode;
        }

        public boolean isLocked() {
            return locked;
        }

        public NodeState[] getNodes() {
            return nodes;
        }

        public List<TIchGrantSign> getAllSigns() {
            return allSigns;
        }

        public NodeState node(int n) {
            return nodes[n];
        }

        /**
         * 整张单此刻停在第几档：自县往省第一个没凑齐的档；三档齐了即停在省档并已锁。
         * 前档没齐时后档即便有落名也按没落算，故第一个缺口即当前档。
         */
        public int getCurrentNode() {
            for (int n = 0; n < NODE_COUNT; n++) {
                if (!nodes[n].isComplete()) {
                    return n;
                }
            }
            return LAST_NODE;
        }

        /** 链条是否三档齐（省厅点头） */
        public boolean isRecognized() {
            for (int n = 0; n < NODE_COUNT; n++) {
                if (!nodes[n].isComplete()) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 顺着单据与全部落名记录回算链条状态。
     *
     * @param bill  核准单（node_no/status 只作参考与轮次边界推断，凑齐与否不取它的格子数）
     * @param signs 该单全部落名记录（含旧轮，不抹）
     */
    public static State evaluate(TIchGrantBill bill, List<TIchGrantSign> signs) {
        int currentRound = bill.getRoundNo() == null ? 0 : bill.getRoundNo();
        boolean locked = bill.getStatus() != null && bill.getStatus() == STATUS_PASS;

        List<TIchGrantSign> ordered = new ArrayList<>(signs == null ? Collections.emptyList() : signs);
        ordered.sort(Comparator
                .comparing(TIchGrantSign::getSignTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(s -> s.getId() == null ? 0L : s.getId()));

        // 本轮重新起签的入口档：本轮已有续签时，取本轮第一笔落名所在档；
        // 一笔未续（刚挪回）时，以服务层挪回动作写下的 node_no 为准（此刻没有别的路数能改它）。
        int entryNode = LAST_NODE + 1;
        for (TIchGrantSign s : ordered) {
            if (s.getRoundNo() != null && s.getRoundNo() == currentRound
                    && s.getNodeNo() != null && s.getNodeNo() < entryNode) {
                entryNode = s.getNodeNo();
            }
        }
        if (entryNode > LAST_NODE) {
            int ref = bill.getNodeNo() == null ? 0 : bill.getNodeNo();
            entryNode = Math.max(0, Math.min(LAST_NODE, ref));
        }

        // 按 (档, 轮) 点出去重落名人，先后有序
        Map<Integer, Map<Integer, LinkedHashMap<String, String>>> byNodeRound = new LinkedHashMap<>();
        for (int n = 0; n < NODE_COUNT; n++) {
            byNodeRound.put(n, new LinkedHashMap<>());
        }
        for (TIchGrantSign s : ordered) {
            int n = s.getNodeNo() == null ? -1 : s.getNodeNo();
            int r = s.getRoundNo() == null ? 0 : s.getRoundNo();
            if (n < 0 || n >= NODE_COUNT || s.getApprover() == null) {
                continue;
            }
            byNodeRound.get(n).computeIfAbsent(r, k -> new LinkedHashMap<>())
                    .putIfAbsent(s.getApprover().trim(), s.getApprover().trim());
        }

        NodeState[] result = new NodeState[NODE_COUNT];
        int floorRound = 0;
        boolean gateOpen = true;
        for (int n = 0; n < NODE_COUNT; n++) {
            // 硬规矩：前一档没凑齐，后一档就算落了名也按没落算——闸门一关，后面一律空着
            if (!gateOpen) {
                result[n] = new NodeState(n, -1, false, Collections.emptyList());
                continue;
            }
            // 入口档及以后只认本轮；入口档以前各档沿用早先轮次的凑齐结果，一档接一档不可倒退
            if (n >= entryNode) {
                floorRound = Math.max(floorRound, currentRound);
            }
            int effRound = -1;
            List<String> roster = Collections.emptyList();
            Map<Integer, LinkedHashMap<String, String>> roundMap = byNodeRound.get(n);
            List<Integer> rounds = new ArrayList<>(roundMap.keySet());
            Collections.sort(rounds);
            // 作数轮次不得早于前一档凑齐的轮次；最早凑齐的那一轮即该档作数落名
            List<Integer> eligible = new ArrayList<>();
            for (Integer r : rounds) {
                if (r >= floorRound) {
                    eligible.add(r);
                }
            }
            for (Integer r : eligible) {
                if (roundMap.get(r).size() >= requiredCount(n)) {
                    effRound = r;
                    roster = new ArrayList<>(roundMap.get(r).keySet());
                    break;
                }
            }
            // 还没凑齐时，窗口也要看得见"眼下已落几个名"：摆最新一轮作数候选的部分落名
            if (effRound < 0 && !eligible.isEmpty()) {
                roster = new ArrayList<>(roundMap.get(eligible.get(eligible.size() - 1)).keySet());
            }
            result[n] = new NodeState(n, effRound, effRound >= 0, roster);
            if (effRound >= 0) {
                floorRound = effRound;
            } else {
                gateOpen = false;
            }
        }
        return new State(currentRound, entryNode, locked, result,
                Collections.unmodifiableList(ordered));
    }

    /** 一档所见四件事 */
    public static GrantNodeView nodeView(NodeState ns) {
        GrantNodeView v = new GrantNodeView();
        v.setNodeNo(ns.getNodeNo());
        v.setNodeName(nodeName(ns.getNodeNo()));
        v.setRequiredCount(requiredCount(ns.getNodeNo()));
        v.setSignRule(signRuleName(ns.getNodeNo()));
        v.setSignedCount(ns.getSignedCount());
        v.setApprovers(new ArrayList<>(ns.getApprovers()));
        v.setEffectiveRound(ns.getEffectiveRound());
        v.setComplete(ns.isComplete());
        return v;
    }

    /**
     * 一张单此刻全貌：当前停档、三档四件事、格子参考数与落名条数对得齐否。
     */
    public static GrantBillView buildView(TIchGrantBill bill, List<TIchGrantSign> signs) {
        State st = evaluate(bill, signs);
        GrantBillView v = new GrantBillView();
        v.setBillId(String.valueOf(bill.getId()));
        v.setBillNo(bill.getBillNo());
        v.setRoundNo(st.getCurrentRound());
        v.setStatus(bill.getStatus() == null ? STATUS_RUNNING : bill.getStatus());
        v.setLocked(st.isLocked());

        List<GrantNodeView> nodeViews = new ArrayList<>();
        for (int n = 0; n < NODE_COUNT; n++) {
            nodeViews.add(nodeView(st.node(n)));
        }
        v.setNodes(nodeViews);

        int current = st.getCurrentNode();
        v.setCurrentNode(current);
        v.setCurrentNodeName(nodeName(current));

        NodeState cur = st.node(current);
        v.setActualSignCount(cur.getSignedCount());
        v.setDisplayedSignCount(bill.getSignCount());
        v.setDisplayedNeedCount(bill.getNeedCount());
        // 落名条数与档口窗口摆的参考数两处对得齐才算（参考列没填也不算对得上）
        v.setCountMatched(bill.getSignCount() != null && bill.getSignCount() == cur.getSignedCount());
        v.setNeedMatched(bill.getNeedCount() != null
                && bill.getNeedCount() == requiredCount(current));
        return v;
    }

    /**
     * 事后复查：省→市→县倒着数，逐档清点；总笔数与核准名单合得拢，单拎一档也站得住。
     */
    public static GrantReview buildReview(TIchGrantBill bill, List<TIchGrantSign> signs) {
        State st = evaluate(bill, signs);
        GrantReview r = new GrantReview();
        r.setBillId(String.valueOf(bill.getId()));
        r.setBillNo(bill.getBillNo());
        r.setRecognized(st.isRecognized());
        r.setCurrentNode(st.getCurrentNode());
        r.setCurrentRound(st.getCurrentRound());
        r.setTotalSignCount(st.getAllSigns().size());

        List<GrantReview.NodeReview> reversed = new ArrayList<>();
        int effectiveSum = 0;
        boolean eachStands = true;
        for (int n = LAST_NODE; n >= 0; n--) {
            NodeState ns = st.node(n);
            GrantReview.NodeReview nr = new GrantReview.NodeReview();
            nr.setNodeNo(n);
            nr.setNodeName(nodeName(n));
            nr.setRequiredCount(requiredCount(n));
            nr.setEffectiveRound(ns.getEffectiveRound());
            nr.setEffectiveSignedCount(ns.getSignedCount());
            nr.setEffectiveApprovers(new ArrayList<>(ns.getApprovers()));
            nr.setComplete(ns.isComplete());

            List<GrantReview.SignRow> rows = new ArrayList<>();
            for (TIchGrantSign s : st.getAllSigns()) {
                if (s.getNodeNo() != null && s.getNodeNo() == n) {
                    rows.add(new GrantReview.SignRow(
                            s.getRoundNo() == null ? 0 : s.getRoundNo(), n,
                            s.getApprover(), s.getComment(), s.getSignTime()));
                }
            }
            nr.setAllSigns(rows);
            reversed.add(nr);

            if (ns.isComplete()) {
                effectiveSum += ns.getSignedCount();
            }
            // 单拎一档：作数落名必须恰好是该档定法点名的人数，多一笔少一笔都不算站住
            if (!ns.isComplete() || ns.getSignedCount() != requiredCount(n)) {
                eachStands = false;
            }
        }
        r.setNodesReversed(reversed);
        r.setEffectiveSignCountSum(effectiveSum);
        // 认下之后：三档齐、各档单独站得住、作数总数与链条点名总数合得拢
        r.setConsistent(st.isRecognized() && eachStands && effectiveSum == fullRosterCount());
        return r;
    }

    /**
     * 核准结论（册子与窗口共用的一本账）：与复查同一回算，不另起一套手写口径。
     */
    public static GrantLedger buildLedger(TIchGrantBill bill, List<TIchGrantSign> signs) {
        State st = evaluate(bill, signs);
        GrantReview r = buildReview(bill, signs);

        GrantLedger l = new GrantLedger();
        l.setBillNo(bill.getBillNo());
        l.setRecognized(st.isRecognized());
        l.setTotalSignCount(r.getTotalSignCount());
        l.setReviewConsistent(r.isConsistent());
        if (st.isRecognized()) {
            l.setApprovedNode(LAST_NODE);
            l.setApprovedLevelName(nodeName(LAST_NODE));
            l.setRoadName(nodeName(0) + "—" + nodeName(1) + "—" + nodeName(2));
        } else {
            l.setApprovedNode(-1);
            l.setApprovedLevelName("未认下");
            l.setRoadName("");
        }
        return l;
    }
}
