# 챕터 06 학습 요약 — Spring Data JPA로 영속성 부여

> 제출용 구성: A4 두 쪽. Markdown을 PDF로 변환할 때 본문 10.5–11pt, 여백 약 18mm를 권장한다.
> 아래 수동 페이지 구분을 지원하는 Markdown 변환기를 사용한다.
> 기준: J2-week05 / upstream 4211d41 / Java 21 · Spring Boot 3.2.4 · Gradle 8.6

## 1쪽 — 저장보다 중요한 트랜잭션 경계

### 가져온 코드를 먼저 구분했다

기존 코드는 chapter-03이었다. SurlController가 ArrayList에 데이터를 보관했고 JPA 의존성이 없었다.
직접 새 기능을 구현하는 대신 강의 마지막 참고 커밋의 Java 파일 19개를 가져와 해시까지 대조했다.
조직 저장소 이력·README·프로젝트 이름·Wrapper·포트 8090을 보존하고,
MySQL 8.0은 별도 컨테이너의 13308 포트에 구성했다. 실습 DB와 테스트 DB도 분리했다.

### 변경 감지는 setter의 기능이 아니다

SurlService.increaseCount는 save() 없이 count++만 수행한다. 저장되는 이유는
관리 중인 엔티티를 쓰기 트랜잭션에서 바꾸고 flush 시점에 변경을 비교하기 때문이다.
검증에서는 Article의 제목을 바꾸고 flush/clear 뒤 다시 읽었다.
SQL 조회의 제목이 바뀌었고 Hibernate EntityUpdateCount가 정확히 1 증가했다.

강의 SurlController.go는 조회한 객체를 쓰기 서비스에 넘긴다. 웹 요청 동안 EntityManager를
유지하는 open-in-view=true가 이 동작의 전제다. 이 설정을 끄면 전달된 엔티티가 분리되어
조회수가 저장되지 않을 수 있다. “서비스 메서드에 Transactional이 있다”만으로는 충분하지 않다.
후속 개선 방향은 ID를 받아 같은 쓰기 트랜잭션 안에서 다시 조회하는 것이다.

### this와 프록시를 구분해야 한다

NotProd는 @Lazy로 주입한 self를 통해 work1을 호출한다. 외부 호출이 Spring 프록시를 지나야
@Transactional을 적용할 수 있기 때문이다. this.work1은 프록시를 지나지 않는다.
기존 외부 트랜잭션이 있다면 그 안에서 실행되지만 내부 메서드의 새 전파 설정은 적용되지 않는다.

검증용 TransactionExamples에서 외부 프록시로 writeAndFail을 호출하면 INSERT가 롤백됐다.
반대로 selfCall → this.writeAndFail은 안쪽 ArticleService의 자체 트랜잭션만 커밋되어
예외가 발생한 뒤에도 게시물이 남았다. 검증 후 그 게시물은 제거했다.
실제 초기화 코드의 self 주입은 그대로 보존했고, 새로운 기능으로 이 실험을 넣지는 않았다.

### 예외를 catch해도 commit할 수 없는 경우

MemberService.join은 중복 회원을 조회하고 RuntimeException인 GlobalException을 던진다.
REQUIRED로 참여한 내부 서비스에서 예외가 나오면 공유 트랜잭션이 rollback-only가 된다.
바깥에서 예외를 catch해 정상 반환해도 commit 시 UnexpectedRollbackException이 발생한다.

MySQL에서 “Article 작성 → 중복 회원 가입”의 전체 롤백을 확인했다.
내부 예외를 catch한 경우에도 게시물은 남지 않았다. RsData 실패 값을 반환하는 것과
예외를 던져 롤백시키는 것은 다르다. 결과 코드 자체가 DB 트랜잭션을 제어하지 않는다.

<div style="break-after: page; page-break-after: always;"></div>

## 2쪽 — 매핑·프록시와 실제 검증의 한계

### 날짜 기록과 MappedSuperclass

Demo03Application의 EnableJpaAuditing과 BaseTime의 AuditingEntityListener가 함께 동작한다.
CreatedDate는 저장 이벤트에서, LastModifiedDate는 수정 이벤트에서 날짜를 기록한다.
검증에서는 생성 날짜가 유지되고 제목 변경 후 수정 날짜가 증가했다.
MySQL DATETIME(6)은 마이크로초 단위라 Java 나노초 값을 반올림할 수 있다.
날짜 비교는 DB에서 다시 읽은 값을 기준으로 해야 한다.

BaseEntity와 BaseTime의 MappedSuperclass는 상위 테이블을 만드는 설정이 아니다.
Article·Member·Surl의 각 테이블에 id/create_date/modify_date를 물려준다.
실제 DB에서 공통 컬럼 9개와 상위 테이블이 없는 것을 확인했다.
BaseEntity의 id 기반 equals/hashCode도 저장 전 null ID 객체의 비교에는 주의가 필요하다.

### 작성자 FK와 회원 프록시

Article.author와 Surl.author의 ManyToOne은 author_id를 통해 Member를 참조한다.
MySQL에 두 외래키가 생겼으며 존재하지 않는 회원으로 Article을 저장하면 DB가 거부했다.
원본은 fetch를 생략해 기본 EAGER이고 author의 NOT NULL은 따로 지정하지 않는다.
Surl.author의 JsonIgnore는 응답 JSON에서 회원을 제외할 뿐 관계를 지우지 않는다.

MemberService.getReferenceById의 프록시는 id를 읽을 때 초기화되지 않았고
username을 읽을 때 초기화됐다. Rq가 고정 ID 1L을 반환하는 것은 강의용 회원 선택이다.
로그인 구현이나 실제 인증으로 이해하면 안 된다.

### “완성 코드”에서 보장하지 않는 것

중복 가입은 서비스의 사전 조회로 막지만 username UNIQUE 제약은 없다.
전용 테스트에서 Repository로 동일 username을 넣을 수 있음을 확인하고 롤백했다.
동시 요청에서는 사전 조회만으로 중복을 보장할 수 없다. 조회수 역시 잠금 없이 증가하므로
동시 갱신 유실은 별도 검증·개선 대상이다.

최종 ArticleRepository에는 이전 b60abbe의 조건 조회 메서드가 제거되어 있다.
Containing, And, 정렬 조합은 검증 전용 LearningArticleRepository로 확인했고 원본에 추가하지 않았다.
NotProd는 Article 개수만 검사하므로 게시물은 없고 회원만 남으면 재초기화 시 중복 예외가
발생할 수 있다. 이번 작업에서는 이런 원본 한계를 숨기거나 임의로 고치지 않았다.

### 실제 결과와 재현

- test + bootJar 성공. 강의 컨텍스트 1개와 MySQL 검증 12개, 총 13개 통과.
- CRUD·COUNT·조건 조회, 변경 감지 UPDATE, Auditing, FK, 프록시, 롤백·rollback-only 확인.
- 실제 /add 생성, /all 조회, /g/{id}의 302와 count 0→1 확인.
- 원시 /s 경로의 https://와 query string 보존 확인.
- 서버 재시작 후 Surl ID·URL·count 1 유지, 재이동 후 count 2 확인.
- 미검증: 동시 가입·조회수 갱신, 고부하·운영 배포·인증, MySQL/호스트 재시작.

실행: scripts/run-local.ps1. 테스트: scripts/test-mysql.ps1 --rerun-tasks.
Windows에서는 powershell.exe -NoProfile -ExecutionPolicy Bypass -File 뒤에 위 경로를 지정한다.
전용 DB 비밀번호는 Git 제외된 .local/mysql.env에서 환경변수로 로드한다.
커밋·push는 하지 않았다. 상세 절차와 테스트 매핑은 README와 Chapter06MySqlTests에 남겼다.
