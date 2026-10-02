package com.fc.v2.service;

import com.fc.v2.model.auto.TIchCycleTask;

/**
 * 履约考核催办的两路递送口：县文旅局收站内那一条，传承人本人收手机短信息那一条。
 * 两路各递各的，哪一路送到由周期任务单独记哪一路，互不顶差。
 *
 * @author fuce
 * @date 2026-10-02
 */
public interface IchCycleNoticeSender {

    /**
     * 县文旅局收站内那一条。
     *
     * @param row 本轮到点的到期单（事由文本随单走）
     * @return 送到回 true；未送到回 false，该路本轮不记
     */
    boolean sendSiteNotice(TIchCycleTask row);

    /**
     * 传承人本人收手机短信息那一条（递到联系人那一栏的号码）。
     *
     * @param row 本轮到点的到期单
     * @return 送到回 true；未送到回 false，该路本轮不记
     */
    boolean sendSms(TIchCycleTask row);
}
