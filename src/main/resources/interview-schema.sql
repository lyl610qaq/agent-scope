create table if not exists interview_session (
    id uuid primary key,
    user_id text not null,
    direction varchar(32) not null check (direction = 'JAVA_BACKEND'),
    difficulty varchar(16) not null check (difficulty in ('JUNIOR', 'MIDDLE', 'SENIOR')),
    status varchar(40) not null check (status in (
        'QUESTION_GENERATION_PENDING',
        'IN_PROGRESS',
        'SCORING_PENDING',
        'COMPLETED',
        'CANCELLED'
    )),
    main_question_count integer not null check (main_question_count between 0 and 5),
    current_question_id uuid,
    answered_question_count integer not null check (answered_question_count >= 0),
    version bigint not null check (version >= 0),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    completed_at timestamptz
);

create unique index if not exists interview_session_one_active_user_idx
    on interview_session (user_id)
    where status in (
        'QUESTION_GENERATION_PENDING',
        'IN_PROGRESS',
        'SCORING_PENDING'
    );

create table if not exists interview_question (
    id uuid primary key,
    interview_id uuid not null references interview_session(id) on delete cascade,
    type varchar(16) not null check (type in ('MAIN', 'FOLLOW_UP')),
    main_question_number integer not null check (main_question_number between 1 and 5),
    follow_up_number integer not null check (follow_up_number between 0 and 2),
    parent_question_id uuid references interview_question(id),
    text text not null check (length(trim(text)) > 0),
    skill_tags_json text not null,
    evidence_ids_json text not null,
    status varchar(32) not null check (status in ('WAITING_FOR_ANSWER', 'ANSWERED')),
    created_at timestamptz not null,
    answered_at timestamptz,
    unique (interview_id, main_question_number, follow_up_number),
    check (
        (type = 'MAIN' and follow_up_number = 0 and parent_question_id is null)
        or
        (type = 'FOLLOW_UP' and follow_up_number between 1 and 2 and parent_question_id is not null)
    )
);

create table if not exists interview_answer (
    id uuid primary key,
    interview_id uuid not null references interview_session(id) on delete cascade,
    question_id uuid not null references interview_question(id) on delete cascade,
    answer_text text not null check (length(trim(answer_text)) > 0),
    internal_evaluation text not null check (length(trim(internal_evaluation)) > 0),
    ability_tags_json text not null,
    ai_decision varchar(32) not null check (
        ai_decision in ('FOLLOW_UP', 'NEXT_MAIN_QUESTION')
    ),
    decision_reason text not null check (length(trim(decision_reason)) > 0),
    created_at timestamptz not null,
    unique (question_id)
);

create table if not exists interview_report (
    interview_id uuid primary key references interview_session(id) on delete cascade,
    overall_score integer not null check (overall_score between 0 and 100),
    java_fundamentals_score integer not null check (
        java_fundamentals_score between 0 and 100
    ),
    concurrency_score integer not null check (concurrency_score between 0 and 100),
    jvm_score integer not null check (jvm_score between 0 and 100),
    spring_score integer not null check (spring_score between 0 and 100),
    database_score integer not null check (database_score between 0 and 100),
    engineering_score integer not null check (engineering_score between 0 and 100),
    strengths_json text not null,
    weaknesses_json text not null,
    improvement_suggestions_json text not null,
    created_at timestamptz not null
);
