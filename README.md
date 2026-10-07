# J2-week05
멋사 PBL 스터디 5주차 레포지토리

## chapter-03 초기 준비 기록

- 주제: Spring Data JPA로 영속성 부여
- 조직 저장소(origin): https://github.com/dku-likelion-backend-study-minsu/J2-week05.git
- 강의 원본(upstream): https://github.com/jhs512/demo03-2024.git
- 시작 브랜치: `chapter-03`
- 실제 가져온 강의 커밋: `8a69dcb62fcb1be0868de52645dddb28ebff967a`
- 초기 상태: chapter-03 준비 완료, 챕터 06 구현 전 (현재 상태는 아래 적용 기록 참조)
- 조직 저장소의 기존 `main` 이력, README와 .gitignore 내용을 보존했다.
- 강의 구성: Java 21, Spring Boot 3.2.4, Gradle Wrapper 8.6, 패키지 `com.ll.demo03`
- 프로젝트 이름만 `J2-week05`로 변경했으며, 강의 소스와 Wrapper는 유지했다.

## IntelliJ에서 열기

1. **File → Open**에서 `C:\J2-week05` 폴더를 선택하고 **New Window**로 독립 프로젝트를 연다.
2. **File → Project Structure → Project → SDK**에서 Java 21을 선택한다.
   목록에 없으면 **Add SDK → JDK**에서 설치된 JDK 21 폴더를 지정한다.
   이 PC의 사용 가능한 경로: `C:\Users\vosej\.jdks\ms-21.0.11`.
3. **Settings → Build, Execution, Deployment → Build Tools → Gradle**에서
   Gradle 배포 방식은 프로젝트의 **Wrapper**, **Gradle JVM**은 같은 Java 21로 설정한다.
4. Gradle 프로젝트를 동기화한다. 기존 스터디 프로젝트의 `.idea` 설정은 변경하지 않는다.

## Windows PowerShell 명령

`J2-week05`에서 프로젝트에 포함된 Wrapper를 사용한다.

```powershell
Set-Location 'C:\J2-week05'
$env:JAVA_HOME = 'C:\Users\vosej\.jdks\ms-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

java -version
.\gradlew.bat --version
.\gradlew.bat test --no-daemon
```

애플리케이션 실행 명령(필요할 때 별도로 실행):

```powershell
.\gradlew.bat bootRun
```

강의 시작 코드의 서버 포트는 `8090`이다.
이번 준비 작업에서는 애플리케이션·MySQL·Docker 실행과 DB 생성을 진행하지 않는다.
현재 테스트는 강의에서 제공한 Spring 애플리케이션 컨텍스트 로딩 테스트이며,
다음 단계의 JPA 기능을 검증하는 테스트는 아니다.

## 다음 구현 항목

- Article CRUD
- Service 계층
- Auditing
- 트랜잭션
- Member 관계
- Surl 엔티티화

챕터 06의 JPA 구현과 커밋·push는 다음 작업으로 남겨둔다.

## 챕터 06 적용 및 새로 확인한 내용

현재 작업 폴더는 `C:\dku-likelion-backend-study2\J2-week05`이다.
위 내용은 chapter-03 초기 준비 기록이며, 현재는 강의 마지막 참고 커밋
[`4211d41f66a707b1111bcc2b7fe8cce8cd0e95f9`](https://github.com/jhs512/demo03-2024/commit/4211d41f66a707b1111bcc2b7fe8cce8cd0e95f9)의 실습 Java 코드로 전환했다.
Java 파일 19개가 원본과 SHA-256까지 일치한다. 직접 작성하던 별도 구현은 적용하지 않았다.
프로젝트 이름, 포트 8090, Java 21, Boot 3.2.4, Wrapper 8.6, 조직 저장소의 기존 이력을 유지했다.
강의의 배포 설정(.github, Dockerfile, fly.toml)과 하드코딩된 DB 비밀번호는 가져오지 않았다.

### 변경 감지의 전제: 같은 영속성 컨텍스트와 쓰기 트랜잭션

`domain/surl/surl/service/SurlService.increaseCount(Surl)`은 `save()` 없이
`surl.increaseCount()`만 호출한다. JPA가 관리 중인 엔티티의 스냅샷과 현재 값을 비교해
flush 시 UPDATE한다. 아무 객체나 setter로 바꾸면 저장되는 것은 아니다.

강의의 `SurlController.go()`는 조회한 객체를 다른 서비스 호출에 전달한다.
이 구조는 웹 요청 동안 EntityManager를 유지하는 **Open EntityManager in View**에 의존한다.
실행 설정에서 `spring.jpa.open-in-view=true`를 명시해 원본 동작을 유지했다.
이 설정을 끄고 같은 코드를 사용하면 서비스로 넘긴 객체가 detached 상태여서
조회수 변경이 DB에 반영되지 않을 수 있다. 나중에 개선한다면 ID를 받아 쓰기 트랜잭션 안에서
다시 조회하는 방법을 검토한다. 이번 작업에서는 원본을 수정하지 않았다.

`Chapter06MySqlTests.dirtyCheckingExecutesUpdateAndAuditingRecordsDates()`는
관리 중인 Article의 제목을 바꾸고 `flush()/clear()` 후 다시 읽어 UPDATE를 확인한다.
Hibernate 통계의 EntityUpdateCount가 정확히 1 증가했고 SQL로도 변경된 제목을 확인했다.

### this 호출, 트랜잭션 프록시, rollback-only

`global/initData/NotProd`가 `@Lazy`로 주입한 `self.work1()`을 호출하는 이유는
트랜잭션 프록시를 통과하기 위해서다. `this.work1()`처럼 객체 내부에서 직접 호출하면
호출된 메서드의 `@Transactional`을 새로 적용하지 않는다.
이미 외부 트랜잭션이 있다면 그 트랜잭션은 유지되지만, 내부 메서드의 별도 전파 설정은 적용되지 않는다.
이 자기 주입은 강의 코드 그대로다. 다른 Bean으로 초기화 작업을 나누는 것도 후속 개선 방법이다.

`MemberService.join()`의 중복 회원 `GlobalException`은 RuntimeException이다.
기본 REQUIRED 전파로 바깥 트랜잭션에 참여하던 내부 서비스가 이 예외를 던지면
공유 트랜잭션이 rollback-only가 된다. 바깥에서 catch해도 이 표시가 취소되지 않는다.
바깥에서 정상 반환하여 commit을 시도하면 `UnexpectedRollbackException`이 발생한다.
검증용 TransactionExamples에서 예외 전파, 내부 예외를 catch한 경우,
외부 프록시 호출과 this 호출의 저장 결과를 각각 비교했다.

### Auditing과 MySQL 날짜 정밀도

`Demo03Application.@EnableJpaAuditing`과 `BaseTime.@EntityListeners(AuditingEntityListener.class)`를
함께 사용한다. `@CreatedDate`와 `@LastModifiedDate`는 JPA 저장·수정 이벤트에서 기록된다.
DB가 임의로 날짜를 채우는 방식이 아니다. 생성과 변경 날짜가 모두 기록되고,
제목 변경 시 생성 날짜는 유지되며 수정 날짜가 증가하는 것을 확인했다.

MySQL의 DATETIME(6)은 마이크로초 정밀도다. 저장 직후 Java 객체의 나노초 값과
DB에서 다시 읽은 값을 그대로 equals로 비교하면 반올림 차이 때문에 검증이 실패할 수 있다.
테스트에서는 DB에서 읽은 날짜를 기준으로 변경 전후를 비교했다.
`BaseTime.setModified()`는 원본에 있지만 현재 서비스에서는 호출하지 않는다.

### MappedSuperclass, 작성자 관계, 회원 프록시

`BaseEntity`의 id와 `BaseTime`의 날짜를 Article·Member·Surl 테이블에 각각 매핑한다.
`@MappedSuperclass`는 상위 클래스 자체의 테이블을 만들거나 다형성 조회 대상을 만드는 설정이 아니다.
MySQL에서 각 테이블의 공통 컬럼 9개와 상위 클래스 테이블이 없는 것을 확인했다.

Article.author와 Surl.author는 `@ManyToOne`이다. DB에 `author_id → member.id`
외래키가 생기며, 존재하지 않는 회원 ID로 Article을 저장하면 MySQL이 거부한다.
원본은 fetch를 지정하지 않아 기본 EAGER이고, author의 NOT NULL 제약은 따로 지정하지 않는다.
`Surl.author.@JsonIgnore`는 JSON 노출을 막는 설정이며 DB 관계를 없애는 설정은 아니다.

`MemberService.getReferenceById(1L)`은 회원 프록시를 반환한다.
테스트에서 id만 읽을 때는 초기화되지 않고, username을 읽을 때 초기화되는 것을 확인했다.
`Rq.getMember()`의 1L은 강의용 고정 회원이다. 실제 로그인·인증 기능이 아니다.
회원이 삭제되거나 초기 ID가 달라지면 이 가정도 깨진다.

### 완성 실습 코드와 실서비스 보장은 다르다

- 중복 회원은 `MemberService.join()`의 사전 조회로 막는다. **username UNIQUE 제약은 없다.**
  Repository로 직접 같은 username을 넣을 수 있음을 전용 테스트의 롤백 트랜잭션에서 확인했다.
  동시 가입 경쟁을 DB가 차단하는 구현은 아니다.
- `NotProd.work1()`은 Article 개수만 검사한다. 코드를 보면 게시물을 모두 지우고 회원만 남겼을 때
  재시작 초기화가 중복 회원 예외로 실패할 수 있다. 이 초기화 문제는 수정하지 않았다.
- 원본의 조회수는 일반 읽기 후 증가 방식이다. 동시 요청의 갱신 유실과 고부하 동작은 검증하지 않았다.
- `BaseEntity`의 id 기반 equals/hashCode는 저장 전 null ID 객체를 컬렉션 키로 사용할 때 주의해야 한다.
- `RsData`는 반환 결과 표현이고 예외가 아니다. `GlobalException`에 결과 코드가 있어도
  HTTP 상태를 매핑하는 전역 예외 핸들러는 원본에 없다.
- 최종 `ArticleRepository`에는 이전 `b60abbe`의 조건 조회 메서드가 빠져 있다.
  조건 조회 학습은 검증 테스트의 LearningArticleRepository에만 재현했고 실습 소스는 그대로 두었다.
- `/add`와 `/s/{body}/**`의 응답은 최종 강의 기준 `RsData<Surl>`이며 엔티티는 `data`에 있다.
  `/all`은 목록, `/g/{id}`는 302 이동이다. chapter-03의 생성 응답 구조와는 다르다.

### 실제 검증 기록 — 2026-10-07

| 항목 | 결과 |
|---|---|
| Java / Gradle JVM / Wrapper | Microsoft Java 21.0.11 / Java 21.0.11 / Gradle 8.6 |
| `test bootJar` | BUILD SUCCESSFUL, 테스트 13개 통과(강의 컨텍스트 1 + 검증 12) |
| Article CRUD·COUNT·조건 조회 | 전용 MySQL에서 통과 |
| 변경 감지 / Auditing / 작성자 FK / 프록시 | UPDATE 1회, 날짜 기록·변경, FK 2개, 프록시 지연 초기화 확인 |
| 예외 롤백 / rollback-only / this 호출 | MySQL 기반 테스트 통과 |
| 실제 HTTP 생성 / 목록 / 이동 / 조회수 | 생성 성공, 302 Location 확인, 조회수 0 → 1 |
| URL 내부 `https://`와 query string | curl `--path-as-is` 실제 요청으로 보존 확인 |
| 서버 재시작 후 영속성 | Surl id=1·URL·조회수 1 유지, 재이동 후 2 증가 확인 |
| 미검증 | 동시 가입·동시 조회수 갱신, 고부하, 운영 배포·인증, MySQL 컨테이너/호스트 재시작 |
| 원본 한계 확인 | username UNIQUE 부재를 검증했으며 이를 성공적인 중복 방지로 기록하지 않았다 |

MySQL 8.0 전용 컨테이너: `j2-week05-mysql-20261007`, `127.0.0.1:13308`.
실습 DB `j2_week05`와 테스트 DB `j2_week05_test`를 분리했다.
전용 볼륨은 `j2-week05-mysql-data-20261007`이며 기존 MySQL 3306 및 다른 컨테이너·볼륨은 변경하지 않았다.
비밀번호는 Git에서 제외되는 `.local/mysql.env`에만 저장하고 스크립트가 환경변수로 로드한다.
원본의 `/secretKey` 예제에는 DB 비밀번호 대신 의미 없는 학습용 placeholder만 연결했다.

검증 후 이번에 시작한 애플리케이션 프로세스는 종료했다. 전용 MySQL은 실행 상태로 남겼다.
Git 브랜치·origin·upstream·기존 HEAD는 유지했고 커밋·push하지 않았다.
초기 소스·문서·설정 백업은
`C:\Users\vosej\AppData\Local\JetBrains\IntelliJIdea2026.1\aia\agents\week05-chapter06-1791356821513\backup`에 보존했다.

### 다시 실행할 명령

IntelliJ에서 현재 폴더를 열고, Project SDK와 Gradle JVM을 기존 Java 21로 설정한다.
IDE에서 Demo03Application을 직접 실행하려면 Run/Debug Configuration에도 DB_URL,
DB_USERNAME, DB_PASSWORD 환경변수가 필요하다. 기존 .idea 설정은 자동으로 수정하지 않았다.
비밀번호는 .local/mysql.env에서 확인하며 문서나 공유되는 실행 설정에 복사하지 않는다.
Windows 기본 PowerShell 실행 정책 때문에 스크립트는 아래처럼 **실행 프로세스에만**
Bypass를 적용한다. 시스템 실행 정책을 변경하지 않는다.

```powershell
Set-Location 'C:\dku-likelion-backend-study2\J2-week05'

# 이미 준비된 전용 MySQL 시작(볼륨 유지)
docker compose -p j2-week05 up -d mysql

# 환경변수 로드 + j2_week05_test에서 전체 MySQL 테스트 재실행
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-mysql.ps1 --rerun-tasks

# JAR 빌드
.\gradlew.bat bootJar --no-daemon

# 전용 실습 DB로 애플리케이션 실행(8090)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-local.ps1
```

다른 PC에서는 Docker가 실행 중인지 확인한 뒤, 컨테이너·볼륨·13308 포트가 비어 있는 상태에서
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-local-mysql.ps1`로
새 비밀번호와 독립 DB를 준비한다. 이 스크립트는 기존 파일·컨테이너·볼륨을 대체하지 않는다.
테스트 리포트: `build/reports/tests/test/index.html`. JAR: `build/libs/J2-week05-0.0.1-SNAPSHOT.jar`.

핵심 학습 요약: [docs/study-summary.md](docs/study-summary.md).
공식 참고: [트랜잭션 프록시](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html),
[rollback-only와 REQUIRED](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html),
[Auditing](https://docs.spring.io/spring-data/jpa/reference/auditing.html).
