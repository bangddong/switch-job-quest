package com.devquest.migration

/**
 * 앱 기동과 분리된 DB 마이그레이션 실행 모드. `java -jar app.jar <mode>` 의 첫 프로그램 인자.
 */
enum class MigrationMode(val arg: String) {
    /** validate 후 migrate. repair 는 하지 않는다 — 체크섬 불일치는 시끄럽게 실패한다. */
    MIGRATE("migrate"),

    /** 적용된 이력이 클래스패스와 일치하는지만 검사한다. 아직 적용 안 된(pending) 마이그레이션은 허용. */
    VALIDATE("validate"),

    /** 사람이 명시적으로 치는 수동 모드. 자동 경로(release_command, 기동)에서는 절대 쓰지 않는다. */
    REPAIR("repair"),
    ;

    companion object {
        /**
         * 마이그레이션 모드면 해당 [MigrationMode], 일반 기동이면 null.
         * 옵션(`--`/`-` 시작)이 아닌 알 수 없는 맨 단어가 첫 인자면 예외로 거부한다 —
         * `release_command = "migrat"` 같은 오타가 조용히 웹 서버를 띄워 release 가 타임아웃까지 매달리는 것을 막는다.
         */
        fun parse(args: Array<String>): MigrationMode? {
            val first = args.firstOrNull() ?: return null
            if (first.startsWith("-")) return null
            return entries.firstOrNull { it.arg == first }
                ?: throw IllegalArgumentException(
                    "알 수 없는 실행 모드 '$first' — 허용: ${entries.joinToString { it.arg }}",
                )
        }
    }
}
