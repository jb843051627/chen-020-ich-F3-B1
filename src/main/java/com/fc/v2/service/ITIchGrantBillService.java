package com.fc.v2.service;

import com.fc.v2.model.auto.TIchGrantBill;
import com.fc.v2.model.custom.grant.GrantBillView;
import com.fc.v2.model.custom.grant.GrantLedger;
import com.fc.v2.model.custom.grant.GrantReview;

/**
 * 工坊认定核准单 Service接口（approval-chain 形状：县—市—省三级签批，无增删改查入口）。
 *
 * <p>两条路数分开：{@link #selectTIchGrantBillById(Long)} 是老的查档次方法，收什么给什么，
 * 照旧维持不动；推进的事一律走新的落名入口 {@link #sign}，不混进同一个方法里。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITIchGrantBillService {

    /**
     * 老方法维持不动：按主键回查单据原貌，收什么给什么（不回算、不推进）。
     */
    TIchGrantBill selectTIchGrantBillById(Long id);

    /**
     * 唯一的落名入口：认定只在这里发生。
     *
     * @param id           核准单id
     * @param intendedNode 前端屏上带出的档编号，一律当意向；与入口回算相左按入口的来
     *                     （越档先落、页面停在旧档，都在这里被挡住）
     * @param approver     落名同志
     * @param comment      签批意见
     * @return 落名后入口回算出的权威全貌（当前档以它为准）
     */
    GrantBillView sign(Long id, Integer intendedNode, String approver, String comment);

    /**
     * 往回挪只挪一格：退回紧挨的上一档，轮次加一，早先各档落名一笔不抹。
     *
     * @param operator 经手退件人
     * @param comment  退件缘由（留档）
     * @return 挪回后入口回算出的权威全貌
     */
    GrantBillView sendBack(Long id, String operator, String comment);

    /** 一张单此刻全貌：三档各一档四件事、停在第几档、参考数对得齐否 */
    GrantBillView view(Long id);

    /** 事后复查：省→县倒序逐档清点，总笔数与核准名单合得拢 */
    GrantReview review(Long id);

    /** 核准结论（与工坊册子同一回算，不许两套装法） */
    GrantLedger ledger(Long id);
}
