package com.fc.v2.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fc.v2.model.auto.TIchCycleTask;
import com.fc.v2.service.IchCycleNoticeSender;

/**
 * 两路递送的默认落法：只打日志回真。站内网关与手机短信网关接进来时，
 * 另起一个 {@link IchCycleNoticeSender} 实现顶掉本默认即可，周期任务不用动。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Component
public class LoggingIchCycleNoticeSender implements IchCycleNoticeSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingIchCycleNoticeSender.class);

    @Override
    public boolean sendSiteNotice(TIchCycleTask row) {
        log.info("履约考核催办站内送达县文旅局：单号={}，事由={}", row.getItemNo(), row.getContent());
        return true;
    }

    @Override
    public boolean sendSms(TIchCycleTask row) {
        log.info("履约考核催办手机短信送达传承人：单号={}，联系人={}", row.getItemNo(), row.getContact());
        return true;
    }
}
