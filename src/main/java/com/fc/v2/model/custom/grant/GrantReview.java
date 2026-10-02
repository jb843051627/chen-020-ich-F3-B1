package com.fc.v2.model.custom.grant;

import java.util.Date;
import java.util.List;

/**
 * 事后复查：从省厅倒着数回县里，逐档清点。随便拎出哪一档单独核也站得住。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class GrantReview {

    /** 一笔落名的复查行（哪一日、哪一轮、哪一档、谁落的，新旧两份分开摆） */
    public static class SignRow {
        private int roundNo;
        private int nodeNo;
        private String approver;
        private String comment;
        private Date signTime;

        public SignRow() {
        }

        public SignRow(int roundNo, int nodeNo, String approver, String comment, Date signTime) {
            this.roundNo = roundNo;
            this.nodeNo = nodeNo;
            this.approver = approver;
            this.comment = comment;
            this.signTime = signTime;
        }

        public int getRoundNo() {
            return roundNo;
        }

        public void setRoundNo(int roundNo) {
            this.roundNo = roundNo;
        }

        public int getNodeNo() {
            return nodeNo;
        }

        public void setNodeNo(int nodeNo) {
            this.nodeNo = nodeNo;
        }

        public String getApprover() {
            return approver;
        }

        public void setApprover(String approver) {
            this.approver = approver;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public Date getSignTime() {
            return signTime;
        }

        public void setSignTime(Date signTime) {
            this.signTime = signTime;
        }
    }

    /** 一档的复查清点（作数落名 + 该档全部留档，含被挪回轮次的旧笔） */
    public static class NodeReview {
        private int nodeNo;
        private String nodeName;
        private int requiredCount;
        private int effectiveRound;
        private int effectiveSignedCount;
        private List<String> effectiveApprovers;
        private boolean complete;
        /** 该档全部留档落名，按落名时刻先后，旧轮旧名一笔不抹 */
        private List<SignRow> allSigns;

        public int getNodeNo() {
            return nodeNo;
        }

        public void setNodeNo(int nodeNo) {
            this.nodeNo = nodeNo;
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        public int getRequiredCount() {
            return requiredCount;
        }

        public void setRequiredCount(int requiredCount) {
            this.requiredCount = requiredCount;
        }

        public int getEffectiveRound() {
            return effectiveRound;
        }

        public void setEffectiveRound(int effectiveRound) {
            this.effectiveRound = effectiveRound;
        }

        public int getEffectiveSignedCount() {
            return effectiveSignedCount;
        }

        public void setEffectiveSignedCount(int effectiveSignedCount) {
            this.effectiveSignedCount = effectiveSignedCount;
        }

        public List<String> getEffectiveApprovers() {
            return effectiveApprovers;
        }

        public void setEffectiveApprovers(List<String> effectiveApprovers) {
            this.effectiveApprovers = effectiveApprovers;
        }

        public boolean isComplete() {
            return complete;
        }

        public void setComplete(boolean complete) {
            this.complete = complete;
        }

        public List<SignRow> getAllSigns() {
            return allSigns;
        }

        public void setAllSigns(List<SignRow> allSigns) {
            this.allSigns = allSigns;
        }
    }

    private String billId;
    private String billNo;
    private boolean recognized;
    private int currentNode;
    private int currentRound;

    /** 逐档清点总笔数（全部轮次、不抹旧名）——须与那天窗口那份核准名单合得拢 */
    private int totalSignCount;

    /** 各档作数落名笔数之和，须等于 totalSignCount（留档完整、无悬空笔） */
    private int effectiveSignCountSum;

    /** 省→市→县倒序 */
    private List<NodeReview> nodesReversed;

    /** 总笔数与逐档清点、链条凑齐三处合得拢 */
    private boolean consistent;

    public String getBillId() {
        return billId;
    }

    public void setBillId(String billId) {
        this.billId = billId;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public boolean isRecognized() {
        return recognized;
    }

    public void setRecognized(boolean recognized) {
        this.recognized = recognized;
    }

    public int getCurrentNode() {
        return currentNode;
    }

    public void setCurrentNode(int currentNode) {
        this.currentNode = currentNode;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public int getTotalSignCount() {
        return totalSignCount;
    }

    public void setTotalSignCount(int totalSignCount) {
        this.totalSignCount = totalSignCount;
    }

    public int getEffectiveSignCountSum() {
        return effectiveSignCountSum;
    }

    public void setEffectiveSignCountSum(int effectiveSignCountSum) {
        this.effectiveSignCountSum = effectiveSignCountSum;
    }

    public List<NodeReview> getNodesReversed() {
        return nodesReversed;
    }

    public void setNodesReversed(List<NodeReview> nodesReversed) {
        this.nodesReversed = nodesReversed;
    }

    public boolean isConsistent() {
        return consistent;
    }

    public void setConsistent(boolean consistent) {
        this.consistent = consistent;
    }
}
