create table sms_account (
    id bigint primary key,
    username varchar(100) not null,
    created timestamp(6) not null,
    balance decimal(19, 4) not null,
    credit_limit integer not null,
    daily_quota integer not null,
    transfer_allowed boolean not null
);


create table sms_submission (
    id bigint auto_increment primary key,
    submitted_at timestamp(6) not null,
    available_at timestamp(6) not null,
    description varchar(256),
    deduplication_id integer
);
create index ix_sms_submission_dedup on sms_submission (deduplication_id, id);

create table sms_message (
    id bigint auto_increment primary key,
    submission_id bigint not null,
    submitted_at timestamp(6) not null,
    available_at timestamp(6) not null,
    direction varchar(10) not null,
    origin varchar(32) not null,
    destination varchar(16) not null,
    body text not null,
    encoding varchar(8) not null,
    protocol_id integer not null,
    message_class integer not null,
    number_of_parts integer not null,
    credit_cost decimal(19, 4) not null,
    status_type varchar(12) not null,
    status_subtype varchar(20),
    related_sent_message_id bigint,
    user_supplied_id varchar(20),
    repliable boolean not null,
    routing_group varchar(12) not null,
    delivery_reports varchar(8) not null,
    initial_status_type varchar(12) not null,
    initial_status_subtype varchar(20),
    mail_due timestamp(6),
    mail_lease varchar(36),
    mail_attempts integer not null,
    mail_error varchar(255)
);
alter table sms_message add constraint fk_message_submission foreign key (submission_id) references sms_submission (id);
create index ix_sms_message_mail on sms_message (mail_due, id);
create index ix_sms_message_submission on sms_message (submission_id, id);
create index ix_sms_message_reply on sms_message (destination, repliable, id);
create index ix_sms_message_related on sms_message (related_sent_message_id, id);
create index ix_sms_message_quota on sms_message (direction, submitted_at);

create table sms_webhook (
    id bigint auto_increment primary key,
    name varchar(100) not null,
    url varchar(2048) not null,
    contact_email_address varchar(254),
    trigger_scope varchar(8) not null,
    invoke_option varchar(4) not null,
    active boolean not null,
    on_web_app boolean not null
);
alter table sms_webhook add constraint uk_webhook_name unique (name);

create table sms_webhook_delivery (
    id bigint auto_increment primary key,
    webhook_id bigint not null,
    message_id bigint not null,
    created timestamp(6) not null,
    due timestamp(6),
    lease varchar(36),
    attempt integer not null,
    last_status integer
);
alter table sms_webhook_delivery add constraint fk_webhook_delivery_message foreign key (message_id) references sms_message (id);
create index ix_webhook_delivery_due on sms_webhook_delivery (due, id);
create index ix_webhook_delivery_hook on sms_webhook_delivery (webhook_id, due, id);

create table sms_blocked_number (
    id bigint auto_increment primary key,
    phone_number varchar(16) not null,
    created timestamp(6) not null,
    description varchar(255)
);
alter table sms_blocked_number add constraint uk_blocked_number unique (phone_number);

create table sms_transfer (
    id bigint auto_increment primary key,
    to_username varchar(100) not null,
    to_user_id bigint not null,
    credits decimal(19, 4) not null,
    comment_on_from varchar(100),
    comment_on_to varchar(100),
    created timestamp(6) not null
);


create table sms_attachment (
    id varchar(36) primary key,
    upload_token varchar(36) not null,
    extension varchar(20) not null,
    media_type varchar(120) not null,
    created timestamp(6) not null,
    expires timestamp(6) not null,
    contents longblob
);


create table sms_contact (
    id bigint auto_increment primary key,
    phone_number varchar(16) not null,
    first_name varchar(100),
    surname varchar(100)
);
alter table sms_contact add constraint uk_contact_phone unique (phone_number);

create table sms_group_member (
    id bigint auto_increment primary key,
    group_id varchar(100) not null,
    group_name varchar(100) not null,
    phone_number varchar(16) not null
);
alter table sms_group_member add constraint uk_group_phone unique (group_id, phone_number);
create index ix_group_member_name on sms_group_member (group_name);

insert into sms_account (id, username, created, balance, credit_limit, daily_quota, transfer_allowed)
values (1, 'sandbox', current_timestamp, 100000, 100000, 50000, true),
       (2, 'sandbox-recipient', current_timestamp, 0, 100000, 50000, true);
