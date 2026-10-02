package com.fc.v2.model.custom.grant;

import java.util.List;

/**
 * 一张单此刻的全貌：当前停在第几档、三档逐档四件事、参考列与权威回算对得齐否。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class GrantBillView {

    /** 单据id（字符串下发，保长整精度） */
    private String billId;

    /** 核准单号 */
    private String billNo;

    /** 当前停在第几档（回算得出；前端上送的只当意向） */
    private int currentNode;

    /** 当前停档名 */
    private String currentNodeName;

    /** 当下轮次 0头一回起 */
    private int roundNo;

    /** 0在核 1已认下（此版挪回后仍在核，无单独"已挪回"终态） */
    private int status;

    /** 已被省厅认下、整张锁死 */
    private boolean locked;

    /** 三档自县往省排列（复查时另有倒序） */
    private List<GrantNodeView> nodes;

    /** 当前档格子里的参考已落名数 */
    private Integer displayedSignCount;

    /** 当前档回算作数已落名数 */
    private int actualSignCount;

    /** 格子参考数与落名记录两处对得齐才算（displayedSignCount 为空时不算对得上） */
    private boolean countMatched;

    /** 当前档格子里的参考应落名数 */
    private Integer displayedNeedCount;

    /** 格子应落名数与三档定法两处对得齐才算（displayedNeedCount 为空时不算对得上） */
    private boolean needMatched;

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

    public int getCurrentNode() {
        return currentNode;
    }

    public void setCurrentNode(int currentNode) {
        this.currentNode = currentNode;
    }

    public String getCurrentNodeName() {
        return currentNodeName;
    }

    public void setCurrentNodeName(String currentNodeName) {
        this.currentNodeName = currentNodeName;
    }

    public int getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(int roundNo) {
        this.roundNo = roundNo;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public List<GrantNodeView> getNodes() {
        return nodes;
    }

    public void setNodes(List<GrantNodeView> nodes) {
        this.nodes = nodes;
    }

    public Integer getDisplayedSignCount() {
        return displayedSignCount;
    }

    public void setDisplayedSignCount(Integer displayedSignCount) {
        this.displayedSignCount = displayedSignCount;
    }

    public int getActualSignCount() {
        return actualSignCount;
    }

    public void setActualSignCount(int actualSignCount) {
        this.actualSignCount = actualSignCount;
    }

    public boolean isCountMatched() {
        return countMatched;
    }

    public void setCountMatched(boolean countMatched) {
        this.countMatched = countMatched;
    }

    public Integer getDisplayedNeedCount() {
        return displayedNeedCount;
    }

    public void setDisplayedNeedCount(Integer displayedNeedCount) {
        this.displayedNeedCount = displayedNeedCount;
    }

    public boolean isNeedMatched() {
        return needMatched;
    }

    public void setNeedMatched(boolean needMatched) {
        this.needMatched = needMatched;
    }
}
