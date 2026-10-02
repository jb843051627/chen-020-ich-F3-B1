package com.fc.v2.model.custom.grant;

/**
 * 核准结论与工坊册子共用的一本账：两处都由 GrantChain 同一回算给出，
 * 不许一处取数、一处手写两套装法。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class GrantLedger {

    /** 核准单号 */
    private String billNo;

    /** 是否认下（省厅点头且链条合得拢才算） */
    private boolean recognized;

    /** 认到哪一档 0县 1市 2省；未认下为 -1 */
    private int approvedNode;

    /** 认到哪一级名目（县文旅/市文旅/省文旅；未认下为"未认下"） */
    private String approvedLevelName;

    /** 册子上该写的认定级别路名（认到省即"县—市—省"，未认下为空） */
    private String roadName;

    /** 逐档清点总笔数（全部轮次留档） */
    private int totalSignCount;

    /** 复查是否合得拢（总笔数与逐档清点一致） */
    private boolean reviewConsistent;

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

    public int getApprovedNode() {
        return approvedNode;
    }

    public void setApprovedNode(int approvedNode) {
        this.approvedNode = approvedNode;
    }

    public String getApprovedLevelName() {
        return approvedLevelName;
    }

    public void setApprovedLevelName(String approvedLevelName) {
        this.approvedLevelName = approvedLevelName;
    }

    public String getRoadName() {
        return roadName;
    }

    public void setRoadName(String roadName) {
        this.roadName = roadName;
    }

    public int getTotalSignCount() {
        return totalSignCount;
    }

    public void setTotalSignCount(int totalSignCount) {
        this.totalSignCount = totalSignCount;
    }

    public boolean isReviewConsistent() {
        return reviewConsistent;
    }

    public void setReviewConsistent(boolean reviewConsistent) {
        this.reviewConsistent = reviewConsistent;
    }
}
