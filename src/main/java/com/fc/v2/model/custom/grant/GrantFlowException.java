package com.fc.v2.model.custom.grant;

/**
 * 认定单流转被入口拒绝时的答复。三桩事——锁单后补名、越档先落、挪回旧名去留——
 * 全由服务层那一个落名入口以这里的缘由答复。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class GrantFlowException extends RuntimeException {

    public enum Reason {
        /** 查无此单 */
        NOT_FOUND,
        /** 省厅已认下，单子锁死，名补不进、退不回 */
        LOCKED,
        /** 意向档号与入口回算出的当前档相左（含越档直签），这笔按没落算 */
        NODE_MISMATCH,
        /** 同档同轮同人已落过一笔，不许重复落名 */
        ALREADY_SIGNED,
        /** 落名同志为空 */
        BAD_APPROVER,
        /** 已在头一档（县文旅），再无紧挨的上一档可退 */
        NO_PREV_NODE,
        /** 落笔瞬间单据已被别人推动，乐观条件没对上 */
        CONCURRENT_CHANGED
    }

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public GrantFlowException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
