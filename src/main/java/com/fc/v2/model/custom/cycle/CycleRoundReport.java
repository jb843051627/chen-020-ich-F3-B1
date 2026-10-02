package com.fc.v2.model.custom.cycle;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 一轮跑完的战报：还欠看的、这一轮办妥的、催不动的，三个数同源于一次点算，
 * 由服务层那个周期任务当轮算过再落库——屏上落的就是这一份，不许分成两回去取。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class CycleRoundReport {

    /** 一个都没挑着时的回话：正常收尾，不是故障 */
    public static final String MSG_NONE_DUE = "本轮无到期";

    /** 本轮下手的时刻 */
    private Date at;

    /** 这一刻是不是压着的那一段（压着时手里有已到点的也不动，等下一轮开口） */
    private boolean pressed;

    /** 本轮捞出单数 */
    private int picked;

    /** 还欠看的（跑完这一轮档里仍是候办的行数） */
    private int pending;

    /** 这一轮办妥的 */
    private int done;

    /** 这一轮催不动的 */
    private int failed;

    /** 一个都没挑着时落"本轮无到期"，否则为空 */
    private String message;

    /** 本轮捞出各行的落点，一行一条 */
    private List<CycleRowOutcome> rows = new ArrayList<>();

    public CycleRoundReport() {
    }

    public CycleRoundReport(Date at) {
        this.at = at;
    }

    public Date getAt() {
        return at;
    }

    public void setAt(Date at) {
        this.at = at;
    }

    public boolean isPressed() {
        return pressed;
    }

    public void setPressed(boolean pressed) {
        this.pressed = pressed;
    }

    public int getPicked() {
        return picked;
    }

    public void setPicked(int picked) {
        this.picked = picked;
    }

    public int getPending() {
        return pending;
    }

    public void setPending(int pending) {
        this.pending = pending;
    }

    public int getDone() {
        return done;
    }

    public void setDone(int done) {
        this.done = done;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<CycleRowOutcome> getRows() {
        return rows;
    }

    public void setRows(List<CycleRowOutcome> rows) {
        this.rows = rows;
    }
}
