-- =============================================================================
-- Orbit MVP — 운영 DB 초기 스키마
-- =============================================================================
-- 대상 DB : Railway PostgreSQL 17
-- Hibernate : 6.6.5.Final (Spring Boot 3.4.2)
-- 작성 기준 : ddl-auto=create 실행 시 Hibernate가 생성한 실제 DDL 기반
--
-- 실행 방법:
--   Railway 대시보드 → PostgreSQL 서비스 → Data 탭 → Query 편집기에 붙여넣기
--   또는: psql "<connection-string>" -f schema-prod.sql
--
-- 주의:
--   - 이 파일은 최초 배포 전 1회 실행한다.
--   - 이후 스키마 변경은 db/migrations/ 하위에 날짜_설명.sql 형식으로 관리한다.
--   - ddl-auto=validate 와 호환된다.
--   - 멱등성 보장: 이미 존재하는 객체는 IF NOT EXISTS로 건너뜀.
-- =============================================================================


-- =============================================================================
-- STEP 1. 시퀀스 생성
-- =============================================================================
-- Hibernate GenerationType.AUTO + Long + PostgreSQL
--   → SequenceStyleGenerator 사용
--   → INSERT 전에 SELECT nextval('...') 를 직접 호출
--   → PK 컬럼에 DEFAULT nextval() 연결 없음 (Hibernate가 자체 관리)
--
-- ⚠️  INCREMENT BY 50 필수
--     Hibernate allocationSize 기본값 = 50
--     INCREMENT BY 값이 다르면 ID 충돌 또는 비정상 점프 발생
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS member_seq
    START WITH 1
    INCREMENT BY 50
    MINVALUE 1
    NO MAXVALUE
    NO CYCLE;

CREATE SEQUENCE IF NOT EXISTS space_seq
    START WITH 1
    INCREMENT BY 50
    MINVALUE 1
    NO MAXVALUE
    NO CYCLE;

CREATE SEQUENCE IF NOT EXISTS space_member_seq
    START WITH 1
    INCREMENT BY 50
    MINVALUE 1
    NO MAXVALUE
    NO CYCLE;

CREATE SEQUENCE IF NOT EXISTS message_seq
    START WITH 1
    INCREMENT BY 50
    MINVALUE 1
    NO MAXVALUE
    NO CYCLE;

CREATE SEQUENCE IF NOT EXISTS discussion_seq
    START WITH 1
    INCREMENT BY 50
    MINVALUE 1
    NO MAXVALUE
    NO CYCLE;

CREATE SEQUENCE IF NOT EXISTS discussion_message_seq
    START WITH 1
    INCREMENT BY 50
    MINVALUE 1
    NO MAXVALUE
    NO CYCLE;


-- =============================================================================
-- STEP 2. 테이블 생성 (FK 의존성 순서)
-- =============================================================================
-- 생성 순서:
--   1. member           (독립 — 참조받는 기준 테이블)
--   2. space            (독립 — 참조받는 기준 테이블)
--   3. space_member     (FK: member, space)
--   4. message          (FK: member, space)
--   5. discussion       (FK: message)
--   6. discussion_message (FK: discussion, member)
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. member
-- -----------------------------------------------------------------------------
-- Entity   : com.chat.entity.Member
-- 의존 FK  : 없음
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS member (
    -- PK: Hibernate가 nextval('member_seq') 호출 후 직접 지정
    member_id           BIGINT          NOT NULL,

    -- 비즈니스 컬럼
    username            VARCHAR(255)    NOT NULL,
    password            VARCHAR(255)    NOT NULL,
    nickname            VARCHAR(255)    NOT NULL,

    -- BaseEntity (Spring Data Auditing)
    created_date        TIMESTAMP(6)    NOT NULL,
    last_modified_date  TIMESTAMP(6)    NOT NULL,

    -- 제약
    CONSTRAINT member_pkey
        PRIMARY KEY (member_id),

    CONSTRAINT uq_member_username
        UNIQUE (username)
);


-- -----------------------------------------------------------------------------
-- 2. space
-- -----------------------------------------------------------------------------
-- Entity   : com.chat.entity.Space
-- 의존 FK  : 없음
-- invite_code: UUID.randomUUID().toString().replace("-","") → 32자 고정
--              VARCHAR(255) 충분
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS space (
    -- PK
    space_id            BIGINT          NOT NULL,

    -- 비즈니스 컬럼
    title               VARCHAR(255)    NOT NULL,
    invite_code         VARCHAR(255)    NOT NULL,

    -- BaseEntity
    created_date        TIMESTAMP(6)    NOT NULL,
    last_modified_date  TIMESTAMP(6)    NOT NULL,

    -- 제약
    CONSTRAINT space_pkey
        PRIMARY KEY (space_id),

    CONSTRAINT uq_space_invite_code
        UNIQUE (invite_code)
);


-- -----------------------------------------------------------------------------
-- 3. space_member
-- -----------------------------------------------------------------------------
-- Entity   : com.chat.entity.SpaceMember
-- 의존 FK  : member(member_id), space(space_id)
--
-- last_read_message_id:
--   커서 기반 읽음 추적 워터마크.
--   신규 가입 시 NULL → 전체 메시지 미읽음 상태.
--   NULL 허용 필수 (NOT NULL 없음).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS space_member (
    -- PK
    space_member_id     BIGINT          NOT NULL,

    -- FK 컬럼
    member_id           BIGINT          NOT NULL,
    space_id            BIGINT          NOT NULL,

    -- 읽음 커서 (nullable)
    last_read_message_id BIGINT,

    -- BaseEntity
    created_date        TIMESTAMP(6)    NOT NULL,
    last_modified_date  TIMESTAMP(6)    NOT NULL,

    -- 제약
    CONSTRAINT space_member_pkey
        PRIMARY KEY (space_member_id),

    -- 한 멤버는 동일 Space에 1번만 참여 가능
    CONSTRAINT uq_space_member
        UNIQUE (member_id, space_id),

    -- FK: ON DELETE NO ACTION (기본값) — 코드에서 수동 삭제 처리
    CONSTRAINT fk_space_member_member
        FOREIGN KEY (member_id)
        REFERENCES member (member_id),

    CONSTRAINT fk_space_member_space
        FOREIGN KEY (space_id)
        REFERENCES space (space_id)
);

-- space_member 인덱스
-- idx_space_member_space_id : Space별 참여 멤버 조회 (SpaceService)
-- idx_space_member_member_id: 멤버별 참여 Space 조회 (SpaceService)
-- uq_space_member 가 (member_id, space_id) 복합 인덱스 역할도 하지만
-- space_id 단독 조회 최적화를 위해 별도 인덱스 필요
CREATE INDEX IF NOT EXISTS idx_space_member_space_id
    ON space_member (space_id);

CREATE INDEX IF NOT EXISTS idx_space_member_member_id
    ON space_member (member_id);


-- -----------------------------------------------------------------------------
-- 4. message
-- -----------------------------------------------------------------------------
-- Entity   : com.chat.entity.Message
-- 의존 FK  : member(member_id), space(space_id)
--
-- content: columnDefinition = "TEXT" → TEXT 타입
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS message (
    -- PK
    message_id          BIGINT          NOT NULL,

    -- FK 컬럼
    member_id           BIGINT          NOT NULL,
    space_id            BIGINT          NOT NULL,

    -- 메시지 본문 (TEXT: 길이 제한 없음)
    content             TEXT            NOT NULL,

    -- BaseEntity
    created_date        TIMESTAMP(6)    NOT NULL,
    last_modified_date  TIMESTAMP(6)    NOT NULL,

    -- 제약
    CONSTRAINT message_pkey
        PRIMARY KEY (message_id),

    CONSTRAINT fk_message_member
        FOREIGN KEY (member_id)
        REFERENCES member (member_id),

    CONSTRAINT fk_message_space
        FOREIGN KEY (space_id)
        REFERENCES space (space_id)
);

-- message 인덱스
-- idx_space_id_message_id:
--   Space별 메시지 페이지네이션 핵심 인덱스
--   WHERE space_id = ? ORDER BY message_id DESC 패턴 최적화
--   message_id DESC 방향 포함 → 역순 정렬 인덱스
CREATE INDEX IF NOT EXISTS idx_space_id_message_id
    ON message (space_id, message_id DESC);


-- -----------------------------------------------------------------------------
-- 5. discussion
-- -----------------------------------------------------------------------------
-- Entity   : com.chat.entity.Discussion
-- 의존 FK  : message(message_id)
--
-- root_message_id:
--   @OneToOne 관계. Message 1개당 Discussion 최대 1개.
--   UNIQUE 제약이 DB 레벨 동시성 가드 역할.
--   DiscussionService.createDiscussion() 주석 참고:
--     "DB UNIQUE(root_message_id) 동시 충돌 시 DataIntegrityViolationException 처리 보강 필요"
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS discussion (
    -- PK
    discussion_id       BIGINT          NOT NULL,

    -- FK 컬럼 (OneToOne → UNIQUE 필수)
    root_message_id     BIGINT          NOT NULL,

    -- BaseEntity
    created_date        TIMESTAMP(6)    NOT NULL,
    last_modified_date  TIMESTAMP(6)    NOT NULL,

    -- 제약
    CONSTRAINT discussion_pkey
        PRIMARY KEY (discussion_id),

    -- Message당 Discussion 1개 보장 (동시성 안전망)
    CONSTRAINT uq_discussion_root_message_id
        UNIQUE (root_message_id),

    CONSTRAINT fk_discussion_root_message
        FOREIGN KEY (root_message_id)
        REFERENCES message (message_id)
);


-- -----------------------------------------------------------------------------
-- 6. discussion_message
-- -----------------------------------------------------------------------------
-- Entity   : com.chat.entity.DiscussionMessage
-- 의존 FK  : discussion(discussion_id), member(member_id)
--
-- content: columnDefinition = "TEXT" → TEXT 타입
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS discussion_message (
    -- PK
    discussion_message_id   BIGINT      NOT NULL,

    -- FK 컬럼
    discussion_id           BIGINT      NOT NULL,
    member_id               BIGINT      NOT NULL,

    -- 메시지 본문 (TEXT)
    content                 TEXT        NOT NULL,

    -- BaseEntity
    created_date            TIMESTAMP(6) NOT NULL,
    last_modified_date      TIMESTAMP(6) NOT NULL,

    -- 제약
    CONSTRAINT discussion_message_pkey
        PRIMARY KEY (discussion_message_id),

    CONSTRAINT fk_dm_discussion
        FOREIGN KEY (discussion_id)
        REFERENCES discussion (discussion_id),

    CONSTRAINT fk_dm_member
        FOREIGN KEY (member_id)
        REFERENCES member (member_id)
);

-- discussion_message 인덱스
-- idx_dm_discussion_id:
--   DiscussionMessageRepository.findByDiscussionIdFetchMember() 최적화
--   WHERE discussion_id = ? ORDER BY discussion_message_id ASC 패턴
--   Hibernate Entity에는 @Index 없으나 성능상 필수
CREATE INDEX IF NOT EXISTS idx_dm_discussion_id
    ON discussion_message (discussion_id);


-- =============================================================================
-- 완료
-- =============================================================================
-- 생성 객체 요약:
--   시퀀스  6개: member_seq, space_seq, space_member_seq,
--                message_seq, discussion_seq, discussion_message_seq
--   테이블  6개: member, space, space_member, message, discussion, discussion_message
--   인덱스  4개: idx_space_member_space_id, idx_space_member_member_id,
--                idx_space_id_message_id, idx_dm_discussion_id
--   UNIQUE  4개: uq_member_username, uq_space_invite_code,
--                uq_space_member, uq_discussion_root_message_id
--   FK      7개: fk_space_member_member, fk_space_member_space,
--                fk_message_member, fk_message_space,
--                fk_discussion_root_message, fk_dm_discussion, fk_dm_member
-- =============================================================================
