-- ich 非物质文化遗产代表性项目与代表性传承人管理服务 -- schema (chen-020)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_020

-- 2026-10-02 起：t_ich_cycle_task 新增 biz_type/target_code/contact/done_at/fail_reason 五列；
-- 办妥要留两个痕迹（去向翻办妥 + 办妥那一刻），催不动缘由顺着写进同一条；
-- 两路送达与逾期撤回另立 t_ich_cycle_log 一笔一记录，撤回一笔盖不住 due_at 与 done_at 两处旧字。
CREATE TABLE IF NOT EXISTS t_ich_cycle_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '履约考核到期单',
  biz_type int DEFAULT NULL COMMENT '事由 0履约催办 1年度考核',
  target_code varchar(64) DEFAULT NULL COMMENT '所拴名录项目代号或传承人代号',
  contact varchar(64) DEFAULT NULL COMMENT '联系人那一栏（手机）',
  due_at datetime DEFAULT NULL COMMENT '该出手那一日的止点时刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '可提前几日开口',
  content varchar(255) DEFAULT NULL COMMENT '事由与所对名录项目或传承人记要',
  status int DEFAULT NULL COMMENT '条目情形 0候办 1已办妥 2催不动',
  done_at datetime DEFAULT NULL COMMENT '办妥的那一刻（只随去向翻办妥一同落笔）',
  fail_reason varchar(255) DEFAULT NULL COMMENT '催不动卡在哪一处缘由',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='履约考核到期单';

-- 到期单办理留痕：两路送达各记各的（手机短信那一路不算替站内那一路交差），
-- 逾期撤回另记一笔，事后查得到它的存在；旧轮痕迹一笔不抹。
CREATE TABLE IF NOT EXISTS t_ich_cycle_log (
  id bigint NOT NULL COMMENT '主键',
  task_id bigint NOT NULL COMMENT '所属到期单 t_ich_cycle_task.id',
  action varchar(16) DEFAULT NULL COMMENT '动作 SITE_MSG站内送达 SMS手机短信送达 WITHDRAW逾期撤回',
  content varchar(255) DEFAULT NULL COMMENT '这一笔的记要',
  action_time datetime DEFAULT NULL COMMENT '落笔时刻',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_cycle_log_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='履约考核到期单办理留痕';

CREATE TABLE IF NOT EXISTS t_ich_grant_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '工坊认定核准单',
  node_no int DEFAULT NULL COMMENT '当前所在档口 0..2（县文旅/市文旅/省文旅）',
  round_no int DEFAULT '0' COMMENT '当下签批轮次 0头一回起 每挪回一格加一（新旧两份分开留档）',
  sign_mode int DEFAULT NULL COMMENT '同档并签办法 0一笔即可 1两笔点齐（仅页面参考，凑齐与否以落名记录回算为准）',
  need_count int DEFAULT NULL COMMENT '本档应落名数（仅页面参考，权威数以三档定法回算为准）',
  sign_count int DEFAULT NULL COMMENT '本档已落名数（仅页面参考，权威数顺着落名记录一笔笔点出）',
  status int DEFAULT NULL COMMENT '核准情形 0在核 1已认下 2已挪回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工坊认定核准单';

-- 2026-10-02 起：t_ich_grant_bill 新增 round_no（挪回轮次）；
-- 落名不再只靠格子里的 sign_count，另立 t_ich_grant_sign 一笔一记录，回算以它为准。
-- 落名一笔一记录：凑齐与否、当前档口、复查总笔数一律顺着这张表点出来，
-- 不由谁往 t_ich_grant_bill 的格子里敲数。挪回只退一格，旧轮落名一笔不抹。
CREATE TABLE IF NOT EXISTS t_ich_grant_sign (
  id bigint NOT NULL COMMENT '主键',
  bill_id bigint NOT NULL COMMENT '所属核准单 t_ich_grant_bill.id',
  node_no int NOT NULL COMMENT '落在哪一档 0县文旅 1市文旅 2省文旅',
  round_no int NOT NULL COMMENT '落名时所属轮次 0头一轮起 挪回后续签轮次加一',
  approver varchar(64) NOT NULL COMMENT '落名同志',
  comment varchar(500) DEFAULT NULL COMMENT '签批意见',
  sign_time datetime DEFAULT NULL COMMENT '落名时刻（新旧两份分开留档）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_bill_round_node_approver (bill_id, round_no, node_no, approver)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工坊认定核准单落名记录';

CREATE TABLE IF NOT EXISTS t_ich_image_card (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '技艺影像卷卷号',
  node_no int DEFAULT NULL COMMENT '本卷版次序',
  site_id int DEFAULT NULL COMMENT '所属名录项目',
  site_no varchar(64) DEFAULT NULL COMMENT '所属项目代号',
  plan_qty decimal(12,2) DEFAULT NULL COMMENT '应归影像件数',
  real_qty decimal(12,2) DEFAULT NULL COMMENT '实归影像件数',
  dev_rate decimal(12,2) DEFAULT NULL COMMENT '两数折出的偏差率',
  content varchar(255) DEFAULT NULL COMMENT '随卷交来的门类要件',
  status int DEFAULT NULL COMMENT '建卷进展 0待核对 1已核对 2已冻住',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技艺影像卷立卷单';

CREATE TABLE IF NOT EXISTS t_ich_item_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '名录项目申报单',
  stage int DEFAULT NULL COMMENT '当前格次 0..3（核验/评议/公示/列入）',
  status int DEFAULT NULL COMMENT '申领会落 0未起 1在办 2已收口',
  content varchar(255) DEFAULT NULL COMMENT '一格一记',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次过口动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='名录项目申报单';

CREATE TABLE IF NOT EXISTS t_ich_pre_line (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '准入门槛线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '线名',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '起分数值',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '过线数值',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '优线数值',
  eff_start datetime DEFAULT NULL COMMENT '启用之日',
  eff_end datetime DEFAULT NULL COMMENT '交棒之日(不含)',
  priority int DEFAULT NULL COMMENT '让位顺位(数值大的先说话)',
  status int DEFAULT NULL COMMENT '线的情形 0在场 1已停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='申报准入门槛线';

CREATE TABLE IF NOT EXISTS t_ich_project (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '名录项目代号',
  site_name varchar(128) DEFAULT NULL COMMENT '项目名称全称',
  site_type varchar(32) DEFAULT NULL COMMENT '门类',
  road_name varchar(128) DEFAULT NULL COMMENT '认定到哪一级(县—市—省)',
  status int DEFAULT NULL COMMENT '底册情形 0在册 1已注销',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='名录项目底册';

CREATE TABLE IF NOT EXISTS t_ich_report_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '履职情况报送批号',
  row_no int DEFAULT NULL COMMENT '原报行次',
  item_code varchar(64) DEFAULT NULL COMMENT '被对上的传承人代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行进账场数',
  hours_num decimal(12,2) DEFAULT NULL COMMENT '本行授徒时数',
  status int DEFAULT NULL COMMENT '行落地情形 0未收 1已收下 2已退回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='履职情况报送核收行';

-- 初始底册数据：两条项目底册，一条在册、一条已注销（注销后不得再挂接新立卷）。
-- t_ich_project 两条种子底册：id=0 在册、id=1 已注销；项目代号与门类按门类口径给值。
INSERT IGNORE INTO t_ich_project (id, site_no, site_name, site_type, road_name, status, del_flag, create_by, create_time)
VALUES (0, 'XM00', '澜川县·苗绣（银苗绣片技艺，在册）', '传统技艺', '澜川县—河湾区—长街上', 0, 0, 'seed', NOW()),
       (1, 'XM01', '澜川县·侗族大歌（歌师已故项目，已注销）', '传统音乐', '澜川县—河湾区—滨河北', 1, 0, 'seed', NOW());

