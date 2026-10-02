package com.fc.v2.model.custom.cycle;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fc.v2.model.auto.TIchCycleTask;

/**
 * 履约考核到期单的**唯一一把工作日尺**（纯算，不碰库）。
 *
 * <p>查单（{@code listRoundDue}）与回写（{@code runRound}）都从这同一份算法过，
 * 各算各的年末两张表必然对不齐，故规矩全收在这里：
 * <ul>
 *   <li>夜里那一段、法定节假日、省里统一停办公的那几日，整段都不算在手里的数；
 *       剩下的按工作日往下数，数到该出手那日还剩几日就是几日。</li>
 *   <li>该出手那一日（due_at）不许挪动，不能为了攒够一批而后推；可商量的只剩提前几日这一格。</li>
 *   <li>压着的那一段（{@link #isPressed}）手里就算有已到点的也不动，等下一轮开口再说。</li>
 *   <li>本轮办妥过的，下一轮不许再当候办捞出来；同一瞬间两条一起到，各归各处理。</li>
 * </ul>
 *
 * <p>法定节假日与省停办日由运维按年注入（见 {@link #of}）；不注入时按 {@link #defaults()}：
 * 夜里 22:00—次日 07:00，无节假日、无停办日。
 *
 * @author fuce
 * @date 2026-10-02
 */
public final class CycleRuler {

    /** 条目情形：候办 */
    public static final int STATUS_WAIT = 0;

    /** 条目情形：已办妥 */
    public static final int STATUS_DONE = 1;

    /** 条目情形：催不动 */
    public static final int STATUS_FAIL = 2;

    /** 默认夜里那一段的起（22:00） */
    public static final LocalTime DEFAULT_NIGHT_FROM = LocalTime.of(22, 0);

    /** 默认夜里那一段的止（次日 07:00） */
    public static final LocalTime DEFAULT_NIGHT_TO = LocalTime.of(7, 0);

    private final LocalTime nightFrom;
    private final LocalTime nightTo;
    private final Set<LocalDate> statutoryHolidays;
    private final Set<LocalDate> provinceClosures;

    private CycleRuler(LocalTime nightFrom, LocalTime nightTo,
                       Set<LocalDate> statutoryHolidays, Set<LocalDate> provinceClosures) {
        this.nightFrom = nightFrom;
        this.nightTo = nightTo;
        this.statutoryHolidays = Collections.unmodifiableSet(
                new LinkedHashSet<>(statutoryHolidays == null ? Collections.emptySet() : statutoryHolidays));
        this.provinceClosures = Collections.unmodifiableSet(
                new LinkedHashSet<>(provinceClosures == null ? Collections.emptySet() : provinceClosures));
    }

    /**
     * 配一把尺。
     *
     * @param nightFrom        夜里那一段的起（含），跨零点绕到次日
     * @param nightTo          夜里那一段的止（不含）
     * @param statutoryHolidays 法定节假日（整日不算）
     * @param provinceClosures 省里统一停办公的那几日（整日不算）
     */
    public static CycleRuler of(LocalTime nightFrom, LocalTime nightTo,
                                Set<LocalDate> statutoryHolidays, Set<LocalDate> provinceClosures) {
        return new CycleRuler(nightFrom, nightTo, statutoryHolidays, provinceClosures);
    }

    /** 默认尺：夜里 22:00—次日 07:00，无节假日、无停办日 */
    public static CycleRuler defaults() {
        return new CycleRuler(DEFAULT_NIGHT_FROM, DEFAULT_NIGHT_TO,
                Collections.emptySet(), Collections.emptySet());
    }

    private static LocalDate dayOf(Date at) {
        return at.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static LocalTime timeOf(Date at) {
        return at.toInstant().atZone(ZoneId.systemDefault()).toLocalTime();
    }

    /** 这一天算不算在手里的数：法定节假日与省停办日整段不算 */
    public boolean isWorkingDay(LocalDate day) {
        return day != null && !statutoryHolidays.contains(day) && !provinceClosures.contains(day);
    }

    /** 这一刻是不是夜里那一段（跨零点绕到次日） */
    public boolean isNight(LocalTime time) {
        if (time == null) {
            return false;
        }
        if (nightFrom.equals(nightTo)) {
            return false;
        }
        if (nightFrom.isBefore(nightTo)) {
            return !time.isBefore(nightFrom) && time.isBefore(nightTo);
        }
        return !time.isBefore(nightFrom) || time.isBefore(nightTo);
    }

    /**
     * 这一刻是不是压着的那一段：夜里、法定节假日、省里统一停办公的那几日，
     * 整段都不算——压着时手里就算有已到点的也不动，等下一轮开口再说。
     */
    public boolean isPressed(Date at) {
        if (at == null) {
            return false;
        }
        return !isWorkingDay(dayOf(at)) || isNight(timeOf(at));
    }

    /**
     * 数到该出手那日还剩几日：自 from 的次日起数到 due 那一日（含），
     * 夜里/节假日/省停办日整段不算，剩下的按工作日往下数；due 不晚于 from 时为 0。
     */
    public long remainingWorkdays(Date from, Date due) {
        if (from == null || due == null) {
            return 0;
        }
        LocalDate f = dayOf(from);
        LocalDate d = dayOf(due);
        if (!d.isAfter(f)) {
            return 0;
        }
        long n = 0;
        for (LocalDate cur = f.plusDays(1); !cur.isAfter(d); cur = cur.plusDays(1)) {
            if (isWorkingDay(cur)) {
                n++;
            }
        }
        return n;
    }

    /** 是不是越过了该出手那一日的止点时刻（due_at 那一刻在档里不许挪动） */
    public boolean isOverdue(Date at, Date dueAt) {
        return at != null && dueAt != null && at.after(dueAt);
    }

    /**
     * 这一行到没到手边：尚未越过该出手那一日的止点，且剩余工作日数已够上提前量
     * （最可提前几日开口；该出手那一日本身不许挪动，不能为了攒够一批而后推）。
     */
    public boolean isWindowOpen(Date at, Date dueAt, int advanceDays) {
        if (at == null || dueAt == null || isOverdue(at, dueAt)) {
            return false;
        }
        return remainingWorkdays(at, dueAt) <= Math.max(0, advanceDays);
    }

    /** 这一行是不是还欠看的：未删除且仍是候办 */
    public static boolean isPendingRow(TIchCycleTask row) {
        return row != null
                && (row.getDelFlag() == null || row.getDelFlag() == 0)
                && row.getStatus() != null && row.getStatus() == STATUS_WAIT;
    }

    /** 这一行可提前几日开口（amount 空着按 0，只许提前不许后推） */
    public static int advanceDaysOf(TIchCycleTask row) {
        if (row == null || row.getAmount() == null) {
            return 0;
        }
        return Math.max(0, row.getAmount().intValue());
    }

    /**
     * 本轮名单（挑单只问一条：该出手那天到没到）。
     *
     * <p>压着的那一段一律不捞；够不上日子的这一轮不碰；到点（含提前量）或已越过
     * 该出手那一日的候办行才进本轮名单。同一瞬间两条一起到，各归各进名单，
     * 既不合成一条也不许漏掉一条；办妥过、催不动过的行不再当候办捞出来。
     */
    public List<TIchCycleTask> pick(List<TIchCycleTask> rows, Date at) {
        if (rows == null || rows.isEmpty() || isPressed(at)) {
            return Collections.emptyList();
        }
        List<TIchCycleTask> picked = new ArrayList<>();
        for (TIchCycleTask row : rows) {
            if (!isPendingRow(row) || row.getDueAt() == null) {
                continue;
            }
            if (isOverdue(at, row.getDueAt())
                    || isWindowOpen(at, row.getDueAt(), advanceDaysOf(row))) {
                picked.add(row);
            }
        }
        return picked;
    }
}
