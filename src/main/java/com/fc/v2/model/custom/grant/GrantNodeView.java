package com.fc.v2.model.custom.grant;

import java.util.List;

/**
 * 一档所见四件事：这一档按规矩该凑几个人、怎么才算凑齐、眼下已落了几个名、是否凑齐。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class GrantNodeView {

    /** 档编号 0县文旅 1市文旅 2省文旅 */
    private int nodeNo;

    /** 档名 */
    private String nodeName;

    /** 该档按规矩应落名数 */
    private int requiredCount;

    /** 凑齐办法：一笔即可 / 两笔点齐 */
    private String signRule;

    /** 眼下作数已落名数（顺着落名记录回算，非格子手敲） */
    private int signedCount;

    /** 作数落名同志（按落名先后，去重） */
    private List<String> approvers;

    /** 作数落名所属轮次 */
    private int effectiveRound;

    /** 怎么才算凑齐：已落 ≥ 应落 */
    private boolean complete;

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

    public String getSignRule() {
        return signRule;
    }

    public void setSignRule(String signRule) {
        this.signRule = signRule;
    }

    public int getSignedCount() {
        return signedCount;
    }

    public void setSignedCount(int signedCount) {
        this.signedCount = signedCount;
    }

    public List<String> getApprovers() {
        return approvers;
    }

    public void setApprovers(List<String> approvers) {
        this.approvers = approvers;
    }

    public int getEffectiveRound() {
        return effectiveRound;
    }

    public void setEffectiveRound(int effectiveRound) {
        this.effectiveRound = effectiveRound;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }
}
