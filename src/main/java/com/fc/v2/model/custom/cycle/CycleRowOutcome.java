package com.fc.v2.model.custom.cycle;

/**
 * 本轮里一行的落点：捞出来的这一单到没到位，由周期任务当轮算过再落库，
 * 页面既添不上一单也剔除不掉一单。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class CycleRowOutcome {

    /** 落点：办妥 */
    public static final String OUTCOME_DONE = "DONE";

    /** 落点：催不动（含逾期未送出撤回、项目已销号、联系人空缺） */
    public static final String OUTCOME_FAILED = "FAILED";

    /** 到期单主键 */
    private Long taskId;

    /** 到期单号 */
    private String itemNo;

    /** 落点 {@link #OUTCOME_DONE} / {@link #OUTCOME_FAILED} */
    private String outcome;

    /** 催不动卡在哪一处缘由（办妥时为空） */
    private String reason;

    /** 站内那一路送到没有（县文旅局收的那一条） */
    private boolean siteMsgSent;

    /** 手机短信息那一路送到没有（传承人本人收的那一条） */
    private boolean smsSent;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getItemNo() {
        return itemNo;
    }

    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isSiteMsgSent() {
        return siteMsgSent;
    }

    public void setSiteMsgSent(boolean siteMsgSent) {
        this.siteMsgSent = siteMsgSent;
    }

    public boolean isSmsSent() {
        return smsSent;
    }

    public void setSmsSent(boolean smsSent) {
        this.smsSent = smsSent;
    }
}
