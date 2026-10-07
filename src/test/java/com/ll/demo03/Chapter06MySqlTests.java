package com.ll.demo03;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ll.demo03.domain.article.article.entity.Article;
import com.ll.demo03.domain.article.article.repository.ArticleRepository;
import com.ll.demo03.domain.article.article.service.ArticleService;
import com.ll.demo03.domain.member.member.entity.Member;
import com.ll.demo03.domain.member.member.repository.MemberRepository;
import com.ll.demo03.domain.member.member.service.MemberService;
import com.ll.demo03.domain.surl.surl.entity.Surl;
import com.ll.demo03.domain.surl.surl.repository.SurlRepository;
import com.ll.demo03.domain.surl.surl.service.SurlService;
import com.ll.demo03.global.exceptions.GlobalException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Chapter06MySqlTests {
    @Autowired ArticleService articleService;
    @Autowired ArticleRepository articleRepository;
    @Autowired MemberService memberService;
    @Autowired MemberRepository memberRepository;
    @Autowired SurlService surlService;
    @Autowired SurlRepository surlRepository;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TransactionExamples examples;

    @BeforeAll
    void requireDedicatedTestDatabase() {
        assertThat(jdbc.queryForObject("select database()", String.class))
                .as("Use scripts/test-mysql.ps1; never run verification on another project database")
                .isEqualTo("j2_week05_test");
    }

    @Test
    @Transactional
    void articleCrudCountAndConditionalQueries() {
        Member author = memberRepository.findByUsername("user1").orElseThrow();
        long before = articleService.count();
        Article first = articleService.write(author, "JPA needle A", "body A").getData();
        Article second = articleService.write(author, "JPA needle B", "body B").getData();
        assertThat(articleService.count()).isEqualTo(before + 2);
        assertThat(articleService.findById(first.getId()).orElseThrow().getBody()).isEqualTo("body A");
        // These derived query methods appeared in b60abbe and were removed from the final repository.
        // Keep production source at 4211d41; construct a learning repository only in this test.
        LearningArticleRepository queries = new JpaRepositoryFactory(entityManager)
                .getRepository(LearningArticleRepository.class);
        assertThat(queries.findByTitleContaining("JPA needle")).hasSize(2);
        assertThat(queries.findByTitleAndBody("JPA needle A", "body A")).hasSize(1);
        assertThat(queries.findByIdInOrderByTitleDescIdAsc(List.of(first.getId(), second.getId())))
                .extracting(Article::getId).containsExactly(second.getId(), first.getId());
        articleService.delete(first);
        assertThat(articleService.findById(first.getId())).isEmpty();
        assertThat(articleService.count()).isEqualTo(before + 1);
    }

    @Test
    @Transactional
    void dirtyCheckingExecutesUpdateAndAuditingRecordsDates() throws Exception {
        Member author = memberRepository.findByUsername("user1").orElseThrow();
        Article article = articleService.write(author, "before update", "audit body").getData();
        entityManager.flush();
        Long id = article.getId();
        assertThat(article.getCreateDate()).isNotNull();
        assertThat(article.getModifyDate()).isNotNull();
        entityManager.clear();
        Thread.sleep(30);
        Article managed = articleService.findById(id).orElseThrow();
        // Compare values read from MySQL, which rounds Java nanoseconds to DATETIME(6).
        var created = managed.getCreateDate();
        var modified = managed.getModifyDate();
        long updatesBefore = entityManagerFactory.unwrap(SessionFactory.class)
                .getStatistics().getEntityUpdateCount();
        managed.setTitle("after dirty checking");
        entityManager.flush(); // Deliberately no repository.save().
        assertThat(entityManagerFactory.unwrap(SessionFactory.class).getStatistics().getEntityUpdateCount())
                .isEqualTo(updatesBefore + 1);
        entityManager.clear();
        Article reloaded = articleService.findById(id).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("after dirty checking");
        assertThat(reloaded.getCreateDate()).isEqualTo(created);
        assertThat(reloaded.getModifyDate()).isAfter(modified);
        assertThat(jdbc.queryForObject("select title from article where id = ?", String.class, id))
                .isEqualTo("after dirty checking");
    }

    @Test
    @Transactional
    void mappedSuperclassColumnsAndAuthorForeignKeysExist() {
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.key_column_usage
                where constraint_schema = database() and referenced_table_name = 'member'
                and table_name in ('article', 'surl') and column_name = 'author_id'
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = database() and table_name in ('base_entity', 'base_time')
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = database() and table_name in ('article', 'member', 'surl')
                and column_name in ('id', 'create_date', 'modify_date')
                """, Integer.class)).isEqualTo(9);
        Article article = articleService.findAll().get(0);
        assertThat(article.getAuthor().getUsername()).isIn("user1", "user2");
    }

    @Test
    @Transactional
    void memberReferenceDefersQueryUntilAPropertyIsRead() {
        entityManager.clear();
        Member reference = memberService.getReferenceById(1L);
        assertThat(Hibernate.isInitialized(reference)).isFalse();
        assertThat(reference.getId()).isEqualTo(1L);
        assertThat(Hibernate.isInitialized(reference)).isFalse();
        assertThat(reference.getUsername()).isEqualTo("user1");
        assertThat(Hibernate.isInitialized(reference)).isTrue();
    }

    @Test
    @Transactional
    void sampleMembersAndDuplicateDomainException() {
        assertThat(memberRepository.findByUsername("user1")).isPresent();
        assertThat(memberRepository.findByUsername("user2")).isPresent();
        long before = memberRepository.count();
        assertThatThrownBy(() -> memberService.join("user1", "1234", "duplicate"))
                .isInstanceOf(GlobalException.class)
                .satisfies(error -> assertThat(((GlobalException) error).getRsData().getResultCode())
                        .isEqualTo("400-1"));
        assertThat(memberRepository.count()).isEqualTo(before);
    }

    @Test
    @Transactional
    void lectureSchemaDoesNotEnforceUsernameUniqueness() {
        Member duplicate = memberRepository.saveAndFlush(Member.builder()
                .username("user1").password("1234").nickname("duplicate").build());
        assertThat(duplicate.getId()).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from member where username = 'user1'", Long.class))
                .isEqualTo(2L);
        // This deliberately documents the lecture limitation. The test rolls back the duplicate.
    }

    @Test
    @Transactional
    void databaseForeignKeyRejectsUnknownAuthor() {
        Member missingAuthor = memberService.getReferenceById(Long.MAX_VALUE);
        assertThatThrownBy(() -> articleService.write(missingAuthor, "invalid author", "body"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void propagatedRuntimeExceptionRollsBackArticleInsert() {
        long before = articleService.count();
        assertThatThrownBy(() -> examples.writeThenDuplicate("rollback-" + UUID.randomUUID(), false))
                .isInstanceOf(GlobalException.class);
        assertThat(articleService.count()).isEqualTo(before);
    }

    @Test
    void catchingInnerExceptionStillLeavesRollbackOnly() {
        long before = articleService.count();
        assertThatThrownBy(() -> examples.writeThenDuplicate("caught-" + UUID.randomUUID(), true))
                .isInstanceOf(UnexpectedRollbackException.class);
        assertThat(articleService.count()).isEqualTo(before);
    }

    @Test
    void thisCallBypassesTransactionProxyButExternalCallRollsBack() {
        String externalTitle = "external-" + UUID.randomUUID();
        String selfTitle = "self-" + UUID.randomUUID();
        try {
            assertThatThrownBy(() -> examples.writeAndFail(externalTitle)).isInstanceOf(GlobalException.class);
            assertThat(articleService.findAll()).noneMatch(article -> article.getTitle().equals(externalTitle));
            assertThatThrownBy(() -> examples.selfCall(selfTitle)).isInstanceOf(GlobalException.class);
            assertThat(articleService.findAll()).anyMatch(article -> article.getTitle().equals(selfTitle));
        } finally {
            articleService.findAll().stream().filter(article -> article.getTitle().equals(selfTitle))
                    .forEach(articleService::delete);
        }
    }

    @Test
    void surlHttpResponseRedirectAndCountPersistWithoutTestTransaction() throws Exception {
        Long id = null;
        try {
            String content = mvc.perform(get("/add").param("body", "http verification")
                            .param("url", "https://example.com/chapter06?x=1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.resultCode").value("200-1"))
                    .andExpect(jsonPath("$.data.count").value(0))
                    .andExpect(jsonPath("$.data.author").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            JsonNode data = mapper.readTree(content).get("data");
            id = data.get("id").longValue();
            assertThat(surlRepository.findById(id).orElseThrow().getCreateDate()).isNotNull();
            mvc.perform(get("/g/{id}", id)).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("https://example.com/chapter06?x=1"));
            assertThat(jdbc.queryForObject("select count from surl where id = ?", Long.class, id)).isEqualTo(1L);
            mvc.perform(get("/all")).andExpect(status().isOk());
        } finally {
            if (id != null) surlRepository.deleteById(id);
        }
    }

    @Test
    void embeddedSurlPathPreservesQueryString() throws Exception {
        Long id = null;
        try {
            String content = mvc.perform(get("/s/path-check/https://example.com/a/b?x=1&y=2")
                            .with(request -> {
                                // MockMvc's URI builder normalizes double slashes; supply the raw request URI.
                                request.setRequestURI("/s/path-check/https://example.com/a/b");
                                return request;
                            }))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.url").value("https://example.com/a/b?x=1&y=2"))
                    .andReturn().getResponse().getContentAsString();
            id = mapper.readTree(content).get("data").get("id").longValue();
        } finally {
            if (id != null) surlRepository.deleteById(id);
        }
    }

    interface LearningArticleRepository extends JpaRepository<Article, Long> {
        List<Article> findByIdInOrderByTitleDescIdAsc(List<Long> ids);
        List<Article> findByTitleContaining(String title);
        List<Article> findByTitleAndBody(String title, String body);
    }

    @TestConfiguration
    static class ExamplesConfig {
        @Bean
        TransactionExamples transactionExamples(ArticleService articles, MemberService members) {
            return new TransactionExamples(articles, members);
        }
    }

    static class TransactionExamples {
        private final ArticleService articles;
        private final MemberService members;

        TransactionExamples(ArticleService articles, MemberService members) {
            this.articles = articles;
            this.members = members;
        }

        @Transactional
        public void writeThenDuplicate(String title, boolean catchFailure) {
            Member author = members.getReferenceById(1L);
            articles.write(author, title, "transaction verification");
            try {
                members.join("user1", "1234", "duplicate");
            } catch (GlobalException exception) {
                if (!catchFailure) throw exception;
            }
        }

        public void selfCall(String title) {
            this.writeAndFail(title); // No proxy crossing: ArticleService commits its own transaction.
        }

        @Transactional
        public void writeAndFail(String title) {
            Member author = members.getReferenceById(1L);
            articles.write(author, title, "self invocation verification");
            throw new GlobalException("400-9", "Intentional verification rollback");
        }
    }
}
